// 시나리오 4. 가이드 재방문 러시 (합격 가이드 재독회)
//   글 상세 조회 → 이미 만든 가이드 다시 열기(캐시)
//   확인할 점: 캐시 응답 지연, OpenAI 호출이 일어나지 않는지(cached=true)
//
// 데이터 흐름
//   GET  /api/posts/{id}        8000(gateway) -> 8082(content) -> 8085(bookmark) ②
//   POST /api/posts/{id}/guide  8000(gateway) -> 8084(guide) -> 8081(user) ④ profileVersion 조회 -> guide DB 캐시
//                               (캐시 적중이면 8082(content) ⑤, OpenAI, Kafka C 모두 없음)
//
// 가이드는 (사용자, 글, profileVersion, promptVersion)이 같을 때만 캐시가 맞는다.
// 그래서 setup()의 고정 계정(k6-guide-N@recu.test)만 쓰고, 이 계정들의 프로필은 고치지 않는다.
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import * as api from '../lib/api.js';
import { ERROR_RATE, GUIDE_CACHED_P95_MS, P95_MS, VUS, stages } from '../lib/config.js';
import { pick, think, userFor } from '../lib/util.js';

export const NAME = 'guide';

const guideCachedDuration = new Trend('guide_cached_duration', true);
// cached=false로 온 횟수 = OpenAI를 부른 횟수. 0이어야 한다
const guideCacheMiss = new Counter('guide_cache_miss');

export function scenario(startTime) {
  return {
    executor: 'ramping-vus',
    startTime,
    startVUs: 0,
    stages: stages(VUS.guide, VUS.guidePeak),
    exec: `${NAME}Run`, // main.js에서 내보내는 함수 이름
    gracefulRampDown: '5s',
  };
}

export const thresholds = {
  [`http_req_duration{scenario:${NAME}}`]: [`p(95)<${P95_MS}`],
  [`http_req_failed{scenario:${NAME}}`]: [`rate<${ERROR_RATE}`],
  [`http_reqs{scenario:${NAME}}`]: ['count>0'],
  guide_cached_duration: [`p(95)<${GUIDE_CACHED_P95_MS}`],
  guide_cache_miss: ['count==0'],
};

export function run(data) {
  const user = userFor(data.guide.users);
  const postId = pick(data.guide.postIds);

  check(api.postDetail(user.token, postId), { '글 상세 200': (res) => res.status === 200 });
  sleep(think(0.5, 1.5));

  // 캐시가 없으면 OpenAI까지 가므로 오래 기다리지 않는다. 캐시 응답은 빨라야 한다
  const res = api.guide(user.token, postId, '10s');
  const cached = res.status === 200 && res.json('cached') === true;
  if (res.status === 200 && !cached) guideCacheMiss.add(1);
  if (cached) guideCachedDuration.add(res.timings.duration);
  check(res, {
    '가이드 200': (x) => x.status === 200,
    '캐시 적중(cached=true)': () => cached,
  });
  sleep(think(1, 3));
}

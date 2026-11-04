// 시나리오 3. 프로필 업데이트 파도 (이직 결심의 밤)
//   글·기업 조회 → 프로필 수정(관심 직무·보유/희망 기술 변경) → 바뀐 프로필로 기업 목록 다시 보기
//   확인할 점: 프로필 수정 p95와 이벤트 적체 여부
//
// 데이터 흐름
//   GET /api/companies/{id}/posts 8000(gateway) -> 8082(content) -> 8081(user) ①, -> 8085(bookmark) ②
//   GET /api/posts/{id}           8000(gateway) -> 8082(content) -> 8085(bookmark) ②
//   PUT /api/profile              8000(gateway) -> 8081(user) -> 8082(content) ③
//                                 8081(user) => 9092(Kafka B recu.profile.updated) => 8082(content), 8085(bookmark)
//   GET /api/companies            8000(gateway) -> 8082(content) -> 8081(user) ① (새 profileVersion 반영 확인)
//
// 이벤트 적체(consumer lag)는 HTTP로 보이지 않는다. 실행 중에 README의 kafka-consumer-groups 명령으로 본다.
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import * as api from '../lib/api.js';
import { ERROR_RATE, P95_MS, VUS, stages } from '../lib/config.js';
import { randomProfile } from '../lib/data.js';
import { kafkaB } from '../lib/metrics.js';
import { pick, think, userFor } from '../lib/util.js';

export const NAME = 'profile';

const profileUpdateDuration = new Trend('profile_update_duration', true);

export function scenario(startTime) {
  return {
    executor: 'ramping-vus',
    startTime,
    startVUs: 0,
    stages: stages(VUS.profile, VUS.profilePeak),
    exec: `${NAME}Run`, // main.js에서 내보내는 함수 이름
    gracefulRampDown: '5s',
  };
}

export const thresholds = {
  [`http_req_duration{scenario:${NAME}}`]: [`p(95)<${P95_MS}`],
  [`http_req_failed{scenario:${NAME}}`]: [`rate<${ERROR_RATE}`],
  [`http_reqs{scenario:${NAME}}`]: ['count>0'],
  profile_update_duration: [`p(95)<${P95_MS}`],
};

export function run(data) {
  const user = userFor(data.users);

  // 1) 둘러보기: 기업별 글 목록 → 글 상세
  check(api.companyPosts(user.token, pick(data.catalog.companyIds), true), {
    '기업별 글 목록 200': (res) => res.status === 200,
  });
  sleep(think(0.5, 1.5));
  check(api.postDetail(user.token, pick(data.catalog.postIds)), { '글 상세 200': (res) => res.status === 200 });
  sleep(think(0.5, 1.5));

  // 2) 프로필 수정: 직무·경력·보유/희망 기술을 모두 새로 뽑는다 → Kafka B
  const res = api.updateProfile(user.token, randomProfile(data.techIds));
  profileUpdateDuration.add(res.timings.duration);
  if (check(res, { '프로필 수정 200': (x) => x.status === 200 })) kafkaB.add(1);

  // 3) 바뀐 프로필로 기업 목록 다시 보기 (① preference가 새 값을 돌려준다)
  check(api.companies(user.token), { '기업 목록 200': (x) => x.status === 200 });
  sleep(think(1, 2));
}

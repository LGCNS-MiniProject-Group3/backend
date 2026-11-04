// 시나리오 2. 북마크 쓸어담기 (저장 버튼 연타)
//   글 조회 50%, 북마크 추가·삭제 40%, 프로필 수정 10%
//   확인할 점: 쓰기 응답 지연, 중복 요청 오류, Kafka B 이벤트 처리량
//
// 데이터 흐름
//   GET    /api/posts/{id}      8000(gateway) -> 8082(content) -> 8085(bookmark) ②
//   POST   /api/bookmarks/{id}  8000(gateway) -> 8085(bookmark) -> 8082(content) ⑦
//   DELETE /api/bookmarks/{id}  8000(gateway) -> 8085(bookmark)
//   PUT    /api/profile         8000(gateway) -> 8081(user) -> 8082(content) ③
//                               8081(user) => 9092(Kafka B recu.profile.updated) => 8082(content), 8085(bookmark)
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import * as api from '../lib/api.js';
import { ERROR_RATE, P95_MS, VUS, stages } from '../lib/config.js';
import { randomProfile } from '../lib/data.js';
import { kafkaB } from '../lib/metrics.js';
import { pick, think, userFor } from '../lib/util.js';

export const NAME = 'bookmark';

// 연타 두 번째 요청이 409로 막힌 횟수 (정상 동작)
const duplicateConflict = new Counter('bookmark_duplicate_409');
// 연타 두 번째 요청이 409가 아닌 값(500, 201 등)으로 온 비율. 중복 처리 버그를 잡는다
const duplicateWrong = new Rate('bookmark_duplicate_wrong');
// 추가·삭제 응답 시간
const writeDuration = new Trend('bookmark_write_duration', true);

export function scenario(startTime) {
  return {
    executor: 'ramping-vus',
    startTime,
    startVUs: 0,
    stages: stages(VUS.bookmark, VUS.bookmarkPeak),
    exec: `${NAME}Run`, // main.js에서 내보내는 함수 이름
    gracefulRampDown: '5s',
  };
}

export const thresholds = {
  [`http_req_duration{scenario:${NAME}}`]: [`p(95)<${P95_MS}`],
  [`http_req_failed{scenario:${NAME}}`]: [`rate<${ERROR_RATE}`],
  [`http_reqs{scenario:${NAME}}`]: ['count>0'],
  bookmark_write_duration: [`p(95)<${P95_MS}`],
  bookmark_duplicate_wrong: ['rate==0'],
};

export function run(data) {
  const user = userFor(data.users);
  const postId = pick(data.catalog.postIds);
  const r = Math.random();

  if (r < 0.5) {
    // 글 조회 50%
    check(api.postDetail(user.token, postId), { '글 상세 200': (res) => res.status === 200 });
  } else if (r < 0.9) {
    // 북마크 추가·삭제 40%
    const add = api.addBookmark(user.token, postId);
    writeDuration.add(add.timings.duration);
    check(add, { '북마크 추가 201': (res) => res.status === 201 });

    // 30%는 저장 버튼을 한 번 더 누른다 → 409 DUPLICATE가 와야 정상
    if (add.status === 201 && Math.random() < 0.3) {
      const again = api.addBookmark(user.token, postId, [201, 409]);
      writeDuration.add(again.timings.duration);
      const ok = again.status === 409;
      if (ok) duplicateConflict.add(1);
      duplicateWrong.add(!ok);
      check(again, { '중복 추가 409': () => ok });
    }

    sleep(think(0.2, 0.6));
    const del = api.deleteBookmark(user.token, postId, add.status === 201 ? [204] : [204, 404]);
    writeDuration.add(del.timings.duration);
    check(del, { '북마크 삭제 204': (res) => res.status === 204 || add.status !== 201 });
  } else {
    // 프로필 수정 10% → Kafka B
    const res = api.updateProfile(user.token, randomProfile(data.techIds));
    if (check(res, { '프로필 수정 200': (x) => x.status === 200 })) kafkaB.add(1);
  }
  sleep(think(0.3, 1));
}

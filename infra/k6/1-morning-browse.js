// 시나리오 1. 출근길 추천 탐색 (월요일 9시 대시)
//   목록 조회 70% (기술 태그·기업·기업별 글 목록), 글 상세 30%
//   확인할 점: 조회 API p95와 오류율
//
// 데이터 흐름
//   GET /api/tech-tags            8000(gateway) -> 8082(content)
//   GET /api/companies            8000(gateway) -> 8082(content) -> 8081(user) ①
//   GET /api/companies/{id}/posts 8000(gateway) -> 8082(content) -> 8081(user) ①, -> 8085(bookmark) ②
//   GET /api/posts/{id}           8000(gateway) -> 8082(content) -> 8085(bookmark) ②
import { check, sleep } from 'k6';
import * as api from '../lib/api.js';
import { ERROR_RATE, P95_MS, VUS, stages } from '../lib/config.js';
import { pick, think, userFor } from '../lib/util.js';

export const NAME = 'browse';

export function scenario(startTime) {
  return {
    executor: 'ramping-vus',
    startTime,
    startVUs: 0,
    stages: stages(VUS.browse, VUS.browsePeak),
    exec: `${NAME}Run`, // main.js에서 내보내는 함수 이름
    gracefulRampDown: '5s',
  };
}

export const thresholds = {
  [`http_req_duration{scenario:${NAME}}`]: [`p(95)<${P95_MS}`],
  [`http_req_failed{scenario:${NAME}}`]: [`rate<${ERROR_RATE}`],
  [`http_reqs{scenario:${NAME}}`]: ['count>0'],
};

export function run(data) {
  const { token } = userFor(data.browseUsers);
  const r = Math.random();

  if (r < 0.7) {
    // 목록 조회 70%: 세 목록을 고르게
    const which = Math.floor(Math.random() * 3);
    if (which === 0) {
      check(api.techTags(), { '기술 태그 200': (res) => res.status === 200 });
    } else if (which === 1) {
      check(api.companies(token), { '기업 목록 200': (res) => res.status === 200 });
    } else {
      const res = api.companyPosts(token, pick(data.catalog.companyIds), Math.random() < 0.5);
      check(res, { '기업별 글 목록 200': (x) => x.status === 200 });
    }
  } else {
    // 글 상세 30%
    check(api.postDetail(token, pick(data.catalog.postIds)), { '글 상세 200': (res) => res.status === 200 });
  }
  sleep(think(0.5, 1.5));
}

// 부하 테스트 공통 설정.
//
// 포트 (docs/design.md 9장 "포트")
//   8000 gateway   ─ k6가 부르는 유일한 주소. 프론트와 같은 입구
//   8081 user      8082 content   8083 collector   8084 guide   8085 bookmark
//   9092 Kafka     3306 MariaDB
//
// 데이터 흐름 표기
//   ->   HTTP (Gateway 라우팅 또는 Feign 내부 API ①~⑦)
//   =>   Kafka 메시지 (A~C)

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8000';

export const PORT = {
  gateway: 8000,
  user: 8081,
  content: 8082,
  collector: 8083,
  guide: 8084,
  bookmark: 8085,
  kafka: 9092,
};

// 요청마다 붙는 데이터 흐름. k6 태그(flow)로도 남아서 결과 JSON에서 경로별로 볼 수 있다.
export const FLOW = {
  signup: '8000(gateway) -> 8081(user)',
  login: '8000(gateway) -> 8081(user)',
  profileCreate:
    '8000(gateway) -> 8081(user) -> 8082(content) ③ GET /internal/tech-tags | 8081(user) => 9092(Kafka B recu.profile.updated) => 8082(content), 8085(bookmark)',
  profileUpdate:
    '8000(gateway) -> 8081(user) -> 8082(content) ③ GET /internal/tech-tags | 8081(user) => 9092(Kafka B recu.profile.updated) => 8082(content), 8085(bookmark)',
  techTags: '8000(gateway) -> 8082(content)',
  companies: '8000(gateway) -> 8082(content) -> 8081(user) ① GET /internal/users/{id}/preference',
  companyPosts:
    '8000(gateway) -> 8082(content) -> 8081(user) ① preference, -> 8085(bookmark) ② POST /internal/bookmarks/lookup',
  postDetail: '8000(gateway) -> 8082(content) -> 8085(bookmark) ② POST /internal/bookmarks/lookup',
  bookmarkAdd: '8000(gateway) -> 8085(bookmark) -> 8082(content) ⑦ POST /internal/posts/summaries',
  bookmarkDelete: '8000(gateway) -> 8085(bookmark)',
  guideCached: '8000(gateway) -> 8084(guide) -> 8081(user) ④ GET /internal/users/{id}/profile (캐시 적중: ⑤·OpenAI 없음)',
  guideMiss:
    '8000(gateway) -> 8084(guide) -> 8081(user) ④, -> 8082(content) ⑤ guide-input, -> OpenAI | 8084(guide) => 9092(Kafka C recu.guide.created) => 8082(content), 8085(bookmark)',
};

// 단계: 워밍업 → 일정 부하 → 최대 부하 → 정리. 시간과 VU 수는 환경 변수로 바꾼다.
export const WARMUP = __ENV.WARMUP || '30s';
export const STEADY = __ENV.STEADY || '1m';
export const PEAK = __ENV.PEAK || '30s';
export const COOLDOWN = __ENV.COOLDOWN || '10s';

export function stages(vus, peakVus) {
  return [
    { duration: WARMUP, target: Math.max(1, Math.ceil(vus / 3)) }, // 워밍업
    { duration: STEADY, target: vus }, // 일정 부하
    { duration: PEAK, target: peakVus }, // 최대 부하
    { duration: COOLDOWN, target: 0 }, // 정리
  ];
}

// 시나리오 하나가 끝날 때까지 걸리는 시간(초). 순서대로 돌릴 때 startTime 계산에 쓴다.
export function totalSeconds() {
  return [WARMUP, STEADY, PEAK, COOLDOWN].map(toSeconds).reduce((a, b) => a + b, 0);
}

function toSeconds(d) {
  const m = /^(\d+)(ms|s|m|h)$/.exec(d);
  if (!m) throw new Error(`시간 형식이 잘못됐어요: ${d} (예: 30s, 1m)`);
  const n = Number(m[1]);
  return { ms: n / 1000, s: n, m: n * 60, h: n * 3600 }[m[2]];
}

export const VUS = {
  browse: Number(__ENV.BROWSE_VUS || 20),
  browsePeak: Number(__ENV.BROWSE_PEAK_VUS || 50),
  bookmark: Number(__ENV.BOOKMARK_VUS || 10),
  bookmarkPeak: Number(__ENV.BOOKMARK_PEAK_VUS || 30),
  profile: Number(__ENV.PROFILE_VUS || 5),
  profilePeak: Number(__ENV.PROFILE_PEAK_VUS || 15),
  guide: Number(__ENV.GUIDE_VUS || 10),
  guidePeak: Number(__ENV.GUIDE_PEAK_VUS || 30),
};

// 기준치. 숫자는 예시(p95 < 500ms, 오류율 < 1%)에서 시작해 3번과 함께 맞춘다.
export const P95_MS = Number(__ENV.P95_MS || 500);
export const GUIDE_CACHED_P95_MS = Number(__ENV.GUIDE_CACHED_P95_MS || 300);
export const ERROR_RATE = Number(__ENV.ERROR_RATE || 0.01);

// 가이드 캐시 시나리오용 고정 계정. 매번 새로 만들면 profileVersion이 달라져 캐시가 안 맞는다.
export const GUIDE_USERS = Number(__ENV.GUIDE_USERS || 5);
export const GUIDE_POSTS = Number(__ENV.GUIDE_POSTS || 2);
// 캐시가 없을 때만 setup()에서 가이드를 한 번 만든다(OpenAI 호출). false면 만들지 않는다.
export const GUIDE_WARMUP = (__ENV.GUIDE_WARMUP || 'true') === 'true';
export const LOAD_TEST_PASSWORD = __ENV.LOAD_TEST_PASSWORD || 'k6-load-test-pw';

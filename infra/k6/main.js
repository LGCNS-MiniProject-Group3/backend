// ReCu 부하 테스트 진입점. 저장소 루트에서:
//
//   k6 run infra/k6/main.js
//
// 모든 요청은 Gateway(8000) 하나로 들어가고, Gateway가 서비스로 나눠 보낸다.
//
//   k6 ──> 8000 gateway ─┬─> 8081 user ──────> 8082 content (③)
//                        │       └──=> 9092 Kafka B ──=> 8082 content, 8085 bookmark
//                        ├─> 8082 content ─┬─> 8081 user (①)
//                        │                 └─> 8085 bookmark (②)
//                        ├─> 8085 bookmark ──> 8082 content (⑦)
//                        └─> 8084 guide ─────> 8081 user (④)  [캐시 없을 때만 -> 8082 content ⑤ -> OpenAI, => Kafka C]
//
// 환경 변수
//   BASE_URL   Gateway 주소 (기본 http://localhost:8000)
//   SCENARIOS  돌릴 시나리오. 쉼표로 구분 (기본 browse,bookmark,profile)
//              guide(시나리오 4)는 OpenAI 비용이 들 수 있어 기본에서 뺐다. 팀과 정한 경우에만 명시한다
//   MODE       sequential(기본, 하나씩 차례로) | parallel(한꺼번에)
//   WARMUP / STEADY / PEAK / COOLDOWN, *_VUS / *_PEAK_VUS, P95_MS, ERROR_RATE … lib/config.js 참고
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import * as browse from './scenarios/1-morning-browse.js';
import * as bookmark from './scenarios/2-bookmark-sweep.js';
import * as profile from './scenarios/3-profile-wave.js';
import * as guide from './scenarios/4-guide-revisit.js';
import { BASE_URL, VUS, totalSeconds } from './lib/config.js';
import { loadCatalog, loadTechTagIds, prepareGuideUsers, prepareUsers } from './lib/data.js';

const ALL = { browse, bookmark, profile, guide };
const LABEL = {
  browse: '1. 출근길 추천 탐색',
  bookmark: '2. 북마크 쓸어담기',
  profile: '3. 프로필 업데이트 파도',
  guide: '4. 가이드 재방문 러시',
};

const selected = (__ENV.SCENARIOS || 'browse,bookmark,profile')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);
for (const s of selected) {
  if (!ALL[s]) throw new Error(`알 수 없는 시나리오: ${s} (browse, bookmark, profile, guide 중에서)`);
}
const sequential = (__ENV.MODE || 'sequential') !== 'parallel';

const scenarios = {};
let thresholds = {};
selected.forEach((s, i) => {
  // 차례로 돌릴 때는 앞 시나리오가 끝난 뒤(+5초) 시작해서 서로의 지표가 섞이지 않게 한다
  const start = sequential ? `${i * (totalSeconds() + 5)}s` : '0s';
  scenarios[s] = ALL[s].scenario(start);
  thresholds = { ...thresholds, ...ALL[s].thresholds };
});

export const options = {
  scenarios,
  thresholds,
  setupTimeout: __ENV.SETUP_TIMEOUT || '5m',
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 시나리오가 부르는 함수 (options.scenarios[*].exec)
export const browseRun = browse.run;
export const bookmarkRun = bookmark.run;
export const profileRun = profile.run;
export const guideRun = guide.run;

// VU 번호(exec.vu.idInTest)는 테스트 전체에서 1부터 매겨지고, 시나리오가 바뀌어도 이어서 늘어날 수 있다.
// 선택한 시나리오의 최대 VU를 모두 더한 만큼 계정을 만들면, 어떤 VU 번호든 자기 계정이 따로 있다.
// (예전에는 시나리오별 풀에서 __VU % 풀크기로 골라 두 VU가 같은 계정을 쓸 수 있었다 → 북마크 409가 오류율에 섞임)
const PEAK_OF = { browse: VUS.browsePeak, bookmark: VUS.bookmarkPeak, profile: VUS.profilePeak };
const USER_POOL = selected.reduce((sum, s) => sum + (PEAK_OF[s] || 0), 0);

// 가상 사용자 준비: 가입 -> 로그인 -> 프로필 등록, 토큰을 VU에 나눠 준다
export function setup() {
  console.log(`[setup] Gateway ${BASE_URL}, 시나리오 ${selected.join(', ')} (${sequential ? '차례로' : '한꺼번에'})`);
  if (selected.includes('guide')) {
    console.warn('[setup] 시나리오 4(guide) 포함: 캐시가 없으면 OpenAI가 불려 비용이 들어요. 팀과 정한 경우에만 실행하세요.');
  }

  // 8000(gateway) -> 8082(content)
  const techIds = loadTechTagIds();
  const data = { techIds, users: [], guide: { users: [], postIds: [] } };

  // 1~3 시나리오가 함께 쓰는 계정 풀. VU 번호 하나에 계정 하나
  data.users = prepareUsers('load', Math.max(1, USER_POOL), techIds);
  console.log(`[setup] 계정 ${data.users.length}개 준비 (VU 번호마다 1개)`);

  // 목록용 기업·글 번호
  data.catalog = loadCatalog(data.users[0].token);

  if (selected.includes('guide')) data.guide = prepareGuideUsers(techIds, data.catalog.postIds);

  console.log(`[setup] 기업 ${data.catalog.companyIds.length}곳, 글 ${data.catalog.postIds.length}편 준비 완료`);
  return data;
}

// 결과 요약: 시나리오별 요청 수 · p95 · 오류율 + k6 기본 요약
export function handleSummary(data) {
  const m = data.metrics;
  const ms = (v) => (v === undefined ? '-' : `${v.toFixed(0)}ms`);
  const pct = (v) => (v === undefined ? '-' : `${(v * 100).toFixed(2)}%`);
  const val = (name, key) => (m[name] ? m[name].values[key] : undefined);

  const lines = ['', '══════ ReCu 부하 테스트 요약 ══════', ''];
  lines.push(pad('시나리오', 26) + pad('요청 수', 10) + pad('p95', 10) + pad('오류율', 10));
  for (const s of selected) {
    lines.push(
      pad(LABEL[s], 26) +
        pad(String(val(`http_reqs{scenario:${s}}`, 'count') ?? '-'), 10) +
        pad(ms(val(`http_req_duration{scenario:${s}}`, 'p(95)')), 10) +
        pad(pct(val(`http_req_failed{scenario:${s}}`, 'rate')), 10),
    );
  }

  lines.push('');
  if (selected.includes('bookmark')) {
    lines.push(`북마크 쓰기 p95            ${ms(val('bookmark_write_duration', 'p(95)'))}`);
    lines.push(`중복 클릭 409 (정상)        ${val('bookmark_duplicate_409', 'count') ?? 0}건`);
    lines.push(`중복 클릭 잘못된 응답       ${pct(val('bookmark_duplicate_wrong', 'rate'))}`);
  }
  if (selected.includes('bookmark') || selected.includes('profile')) {
    const n = val('kafka_b_published', 'count') ?? 0;
    const r = val('kafka_b_published', 'rate');
    lines.push(`Kafka B 발행 (프로필 수정)  ${n}건, ${r === undefined ? '-' : r.toFixed(2)}건/초`);
  }
  if (selected.includes('profile')) {
    lines.push(`프로필 수정 p95            ${ms(val('profile_update_duration', 'p(95)'))}`);
  }
  if (selected.includes('guide')) {
    lines.push(`가이드 캐시 응답 p95        ${ms(val('guide_cached_duration', 'p(95)'))}`);
    lines.push(`가이드 캐시 미스(OpenAI)    ${val('guide_cache_miss', 'count') ?? 0}건 (0이어야 함)`);
  }
  lines.push('');

  const out = {
    stdout: textSummary(data, { indent: ' ', enableColors: true }) + lines.join('\n') + '\n',
  };
  if (__ENV.SUMMARY_JSON) out[__ENV.SUMMARY_JSON] = JSON.stringify(data, null, 2);
  return out;
}

function pad(s, n) {
  // 한글은 터미널에서 두 칸을 차지한다
  const width = [...s].reduce((w, ch) => w + (/[ㄱ-힣]/.test(ch) ? 2 : 1), 0);
  return s + ' '.repeat(Math.max(1, n - width));
}

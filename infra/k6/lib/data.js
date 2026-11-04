// 테스트 데이터 준비. setup()에서 한 번만 부른다.
//   가입 -> 로그인 -> 프로필 등록 순서로 가상 사용자를 만들고 토큰을 나눠 준다.
import { check, fail } from 'k6';
import * as api from './api.js';
import { GUIDE_POSTS, GUIDE_USERS, GUIDE_WARMUP, LOAD_TEST_PASSWORD } from './config.js';

// api-spec: jobFields는 Backend Frontend Data Infra Mobile 중 1개 이상
export const JOB_FIELDS = ['Backend', 'Frontend', 'Data', 'Infra', 'Mobile'];

// 8000(gateway) -> 8082(content): 프로필에 넣을 기술 번호
export function loadTechTagIds() {
  const res = api.techTags();
  if (res.status !== 200) fail(`GET /api/tech-tags 실패: ${res.status} ${res.body}`);
  const ids = res.json('techTags').map((t) => t.techTagId);
  if (ids.length < 6) fail('기술 태그가 6개 미만이에요. infra/db 초기 데이터를 확인하세요.');
  return ids;
}

// api-spec 400 조건을 피한다: wantSkillIds 1~5개, 보유와 관심이 겹치지 않음, careerYears >= 0
export function randomProfile(techIds) {
  const shuffled = [...techIds].sort(() => Math.random() - 0.5);
  const haveCount = 1 + Math.floor(Math.random() * 3);
  const wantCount = 1 + Math.floor(Math.random() * 5);
  const fields = [...JOB_FIELDS].sort(() => Math.random() - 0.5).slice(0, 1 + Math.floor(Math.random() * 2));
  return {
    jobFields: fields,
    careerYears: Math.floor(Math.random() * 11),
    haveSkillIds: shuffled.slice(0, haveCount),
    wantSkillIds: shuffled.slice(haveCount, haveCount + wantCount),
  };
}

// 가입(8000->8081) -> 로그인(8000->8081) -> 프로필 등록(8000->8081->8082 ③, =>Kafka B)
export function prepareUser(email, name, techIds, profile) {
  api.signup(email, LOAD_TEST_PASSWORD, name);
  const { res, token } = api.login(email, LOAD_TEST_PASSWORD);
  if (!token) fail(`로그인 실패 ${email}: ${res.status} ${res.body}`);
  const body = profile || randomProfile(techIds);
  const p = api.createProfile(token, body);
  check(p, { '프로필 등록 201/409': (r) => r.status === 201 || r.status === 409 });
  return { email, token, profile: body };
}

// 실행마다 새 계정. 이메일에 실행 시각을 넣어 겹치지 않게 한다
export function prepareUsers(prefix, count, techIds) {
  const runId = Date.now();
  const users = [];
  for (let i = 0; i < count; i++) {
    users.push(prepareUser(`k6-${prefix}-${runId}-${i}@recu.test`, `k6-${prefix}-${i}`, techIds));
  }
  return users;
}

// 기업 목록(8000->8082->8081 ①) -> 기업별 글 목록(8000->8082->8081 ①, ->8085 ②)
export function loadCatalog(token) {
  const c = api.companies(token);
  if (c.status !== 200) fail(`GET /api/companies 실패: ${c.status} ${c.body}`);
  const companyIds = c.json('companies').map((x) => x.companyId);
  const postIds = [];
  for (const id of companyIds) {
    const p = api.companyPosts(token, id, false);
    if (p.status === 200) postIds.push(...p.json('posts').map((x) => x.postId));
  }
  const unique = [...new Set(postIds)].sort((a, b) => a - b);
  if (unique.length === 0) fail('글이 없어요. infra/db/03-content-data.sql이 들어갔는지 확인하세요.');
  return { companyIds, postIds: unique };
}

// 가이드 캐시 시나리오용 고정 계정.
// 계정·프로필이 그대로여야 profileVersion이 같아서 이전 실행에서 만든 가이드를 다시 쓴다.
// 이 계정들은 다른 시나리오(특히 프로필 수정)에 절대 섞지 않는다.
export function prepareGuideUsers(techIds, postIds) {
  const guidePostIds = postIds.slice(0, GUIDE_POSTS);
  const fixedProfile = {
    jobFields: ['Backend'],
    careerYears: 3,
    haveSkillIds: [techIds[0]],
    wantSkillIds: [techIds[1], techIds[2]],
  };
  const users = [];
  let warmed = 0;
  for (let i = 0; i < GUIDE_USERS; i++) {
    const u = prepareUser(`k6-guide-${i}@recu.test`, `k6-guide-${i}`, techIds, fixedProfile);
    for (const postId of guidePostIds) {
      if (!GUIDE_WARMUP) continue;
      // 8000(gateway) -> 8084(guide) -> 8081(user) ④ [캐시 없으면 -> 8082(content) ⑤ -> OpenAI, => Kafka C]
      const g = api.guide(u.token, postId);
      if (g.status !== 200) fail(`가이드 준비 실패 user=${i} post=${postId}: ${g.status} ${g.body}`);
      if (!g.json('cached')) warmed++;
    }
    users.push(u);
  }
  console.log(`[guide] 고정 계정 ${users.length}명, 글 ${guidePostIds.join(',')}, 이번에 새로 만든 가이드 ${warmed}개(OpenAI 호출)`);
  return { users, postIds: guidePostIds };
}

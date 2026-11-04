// Gateway(8000)로 보내는 요청 모음. 함수마다 서비스 사이 데이터 흐름을 적었다 (lib/config.js의 FLOW).
import http from 'k6/http';
import { BASE_URL, FLOW } from './config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function params(token, name, flow, extra = {}) {
  const headers = { ...JSON_HEADERS };
  if (token) headers.Authorization = token; // "Bearer ..." 그대로. Gateway가 검사 후 X-User-Id를 붙인다
  return { headers, tags: { name, flow }, ...extra };
}

// ── user ───────────────────────────────────────────────

// 8000(gateway) -> 8081(user)
export function signup(email, password, name) {
  return http.post(
    `${BASE_URL}/api/auth/signup`,
    JSON.stringify({ email, password, name }),
    params(null, 'POST /api/auth/signup', FLOW.signup, {
      responseCallback: http.expectedStatuses(201, 409), // 409: 이미 가입(고정 계정 재사용)
    }),
  );
}

// 8000(gateway) -> 8081(user). Access 토큰은 응답 헤더 Authorization에 온다
export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    params(null, 'POST /api/auth/login', FLOW.login),
  );
  return { res, token: res.headers.Authorization || res.headers.authorization || null };
}

// 8000(gateway) -> 8081(user) -> 8082(content) ③ 기술 번호 검증
// 8081(user) => 9092(Kafka B recu.profile.updated) => 8082(content), 8085(bookmark)
export function createProfile(token, body) {
  return http.post(
    `${BASE_URL}/api/profile`,
    JSON.stringify(body),
    params(token, 'POST /api/profile', FLOW.profileCreate, {
      responseCallback: http.expectedStatuses(201, 409), // 409: 이미 등록된 프로필
    }),
  );
}

// 8000(gateway) -> 8081(user) -> 8082(content) ③
// 8081(user) => 9092(Kafka B recu.profile.updated) => 8082(content), 8085(bookmark): user_guide_flag 삭제
export function updateProfile(token, body) {
  return http.put(
    `${BASE_URL}/api/profile`,
    JSON.stringify(body),
    params(token, 'PUT /api/profile', FLOW.profileUpdate),
  );
}

// ── content ────────────────────────────────────────────

// 8000(gateway) -> 8082(content)
export function techTags() {
  return http.get(`${BASE_URL}/api/tech-tags`, params(null, 'GET /api/tech-tags', FLOW.techTags));
}

// 8000(gateway) -> 8082(content) -> 8081(user) ① 선호(직무·관심 기술) 조회
export function companies(token) {
  return http.get(`${BASE_URL}/api/companies`, params(token, 'GET /api/companies', FLOW.companies));
}

// 8000(gateway) -> 8082(content) -> 8081(user) ① 선호
//                                -> 8085(bookmark) ② 저장 여부(bookmarked) 일괄 조회
export function companyPosts(token, companyId, onlyMySkills = false) {
  return http.get(
    `${BASE_URL}/api/companies/${companyId}/posts?onlyMySkills=${onlyMySkills}`,
    params(token, 'GET /api/companies/{id}/posts', FLOW.companyPosts),
  );
}

// 8000(gateway) -> 8082(content) -> 8085(bookmark) ② 저장 여부
export function postDetail(token, postId) {
  return http.get(
    `${BASE_URL}/api/posts/${postId}`,
    params(token, 'GET /api/posts/{id}', FLOW.postDetail),
  );
}

// ── bookmark ───────────────────────────────────────────

// 8000(gateway) -> 8085(bookmark) -> 8082(content) ⑦ 글 존재 확인·카드 정보
// expected: 중복 클릭을 일부러 보낼 때는 409를 정상으로 센다
export function addBookmark(token, postId, expected = [201]) {
  return http.post(
    `${BASE_URL}/api/bookmarks/${postId}`,
    null,
    params(token, 'POST /api/bookmarks/{id}', FLOW.bookmarkAdd, {
      responseCallback: http.expectedStatuses(...expected),
    }),
  );
}

// 8000(gateway) -> 8085(bookmark)
export function deleteBookmark(token, postId, expected = [204]) {
  return http.del(
    `${BASE_URL}/api/bookmarks/${postId}`,
    null,
    params(token, 'DELETE /api/bookmarks/{id}', FLOW.bookmarkDelete, {
      responseCallback: http.expectedStatuses(...expected),
    }),
  );
}

// ── guide ──────────────────────────────────────────────

// 캐시 적중: 8000(gateway) -> 8084(guide) -> 8081(user) ④ 프로필(profileVersion) 조회 -> guide DB
// 캐시 없음: 위에 더해 -> 8082(content) ⑤ guide-input -> OpenAI, 8084(guide) => 9092(Kafka C) => 8082, 8085
export function guide(token, postId, timeout = '60s') {
  return http.post(
    `${BASE_URL}/api/posts/${postId}/guide`,
    null,
    params(token, 'POST /api/posts/{id}/guide', FLOW.guideCached, { timeout }),
  );
}

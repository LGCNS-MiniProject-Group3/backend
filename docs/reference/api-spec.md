> **원본 사본.** 1차 미니프로젝트 8조 산출물(2026-09-29 제출본)을 고치지 않고 옮겼어요. MSA에서 지켜야 할 원본 동작을 확인할 때 봐요. MSA의 스펙 기준은 [design.md](../design.md)이고, 둘이 다르면 design.md를 따라요. 이 파일은 고치지 않아요.

# API 명세서

LG CNS AM Inspire Camp 6기 · 미니 프로젝트 1 · 8조

| | |
|---|---|
| 작성일 | 2026-09-26 (9/29 수정) |
| 작성자 | 신해원 (팀장) |
| 문서 상태 | 제출본 · 2026-09-29 배포본 기준 |

> 예시의 숫자는 9/21~9/22 로컬 DB 에서 센 값이고, 설명 속 현황 숫자는 9/29 배포 DB 기준이다. 추정치는 쓰지 않았다.

---

## 1. 개요

| 항목 | 내용 |
|:---:|---|
| 기본 주소 | 배포 `https://backend-production-fe2f.up.railway.app`<br>로컬 `http://localhost:8000` |
| 형식 | 요청과 응답 모두 JSON, UTF-8 |
| 인증 | 로그인 뒤 받은 access 토큰을 `Authorization: Bearer {accessToken}` 헤더에 담는다 |
| 토큰 없이 부르는 API | 로그인, 토큰 재발급, 기술 목록, 기업 상세, 파일 적재(`/api/admin/collect/import`, 임시로 열어 둠) |
| 날짜 | 날짜만 필요한 값은 `2026-03-18`, 시각은 `2026-09-21T14:04:00+09:00`(한국 시간) |
| 관련 문서 | 요구사항 명세서(FR 번호, 화면 ID), ERD · 테이블 명세서 |

화면과 서버를 동시에 만들어서 명세를 먼저 확정했다. 화면은 이 문서의 응답 예시로 먼저 만들고, 서버가 완성되면 호출부만 바꾼다.

---

## 2. 오류 응답 규격

모든 API 는 오류가 나면 같은 모양으로 응답한다.

```json
{
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "field": "wantSkillIds"
}
```

| 항목 | 내용 |
|:---:|---|
| code | 오류 종류.<br>아래 표의 13가지 |
| message | 서버의 기본 문구.<br>화면은 이 문구 대신 code 별 문구를 보여 준다 |
| field | 입력값 오류일 때 걸린 항목 이름.<br>나머지는 null |

| 상태 | code | 언제 |
|:---:|:---:|---|
| 400 | INVALID_INPUT | 입력값이 규칙에 맞지 않을 때.<br>field 에 항목 이름 |
| 400 | PROFILE_REQUIRED | 기본정보를 저장하지 않고 기업 목록을 부르거나, 경력 년수 없이 AI 가이드를 부를 때 |
| 401 | UNAUTHORIZED | 토큰이 없거나 서명이 틀릴 때 |
| 401 | TOKEN_EXPIRED | access 토큰이 만료됐을 때 |
| 401 | INVALID_GOOGLE_TOKEN | Google 토큰으로 사용자 정보를 받지 못할 때 |
| 403 | INVALID_REFRESH_TOKEN | refresh 토큰이 없거나 만료·폐기됐을 때 |
| 403 | FORBIDDEN | 관리자 전용 API 를 일반 사용자가 부를 때 |
| 404 | NOT_FOUND | 없는 기업·글·북마크 번호 |
| 409 | ALREADY_BOOKMARKED | 이미 저장한 글을 다시 저장할 때 |
| 409 | PROFILE_ALREADY_EXISTS | 프로필이 있는 사용자가 최초 저장을 다시 보낼 때 |
| 500 | INTERNAL_ERROR | 그 밖의 모든 오류.<br>내부 정보는 응답에 싣지 않는다 |
| 501 | NOT_IMPLEMENTED | 아직 만들지 않은 경로 |
| 502 | AI_UNAVAILABLE | Gemini 호출 실패, 시간 초과, 응답 형식 오류 |

---

## 3. API 목록

| # | 메서드 | 경로 | 용도 | 인증 | 화면 | 요구사항 |
|:---:|:---:|---|---|:---:|:---:|:---:|
| 1 | POST | `/api/auth/google` | 로그인 | 불필요 | UI‑01 | FR‑01 |
| 2 | POST | `/api/auth/refresh` | access 토큰 재발급 | refresh 토큰 | 공통 | FR‑01 |
| 3 | POST | `/api/auth/logout` | 로그아웃 | 필요 | 공통 | FR‑01 |
| 4 | GET | `/api/tech-tags` | 기술 목록 | 불필요 | UI‑02<br>UI‑07 | FR‑02 |
| 5 | POST | `/api/profile` | 프로필 최초 저장 | 필요 | UI‑02 | FR‑02 |
| 6 | GET | `/api/profile` | 프로필 조회 | 필요 | UI‑07 | FR‑02 |
| 7 | PUT | `/api/profile` | 프로필 수정 | 필요 | UI‑07 | FR‑08 |
| 8 | GET | `/api/companies` | 기업 목록 | 필요 | UI‑03 | FR‑04 |
| 9 | GET | `/api/companies/{id}` | 기업 상세 | 불필요 | UI‑04 | FR‑05 |
| 10 | GET | `/api/companies/{id}/posts` | 기업의 글 목록 | 필요 | UI‑04 | FR‑05 |
| 11 | GET | `/api/posts/{id}` | 글 원문과 구간 목차 | 필요 | UI‑05 | FR‑06 |
| 12 | POST | `/api/posts/{id}/guide` | AI 읽기 가이드 | 필요 | UI‑05 | FR‑06 |
| 13 | POST | `/api/bookmarks/{postId}` | 글 저장 | 필요 | UI‑04<br>UI‑05 | FR‑07 |
| 14 | GET | `/api/bookmarks` | 저장한 글 목록 | 필요 | UI‑06 | FR‑07 |
| 15 | DELETE | `/api/bookmarks/{postId}` | 저장 해제 | 필요 | UI‑05<br>UI‑06 | FR‑07 |
| 16 | POST | `/api/admin/collect` · `/api/admin/collect/import` | 글 수집과 적재 | 관리자<br>(파일 적재는 불필요) | 없음 | FR‑03<br>FR‑09 |

---

## 4. API 상세

### 4.1 로그인 `POST /api/auth/google`

Google 에서 받은 access 토큰으로 사용자를 확인하고 우리 서비스의 토큰 두 개를 발급한다. 처음 로그인하면 사용자를 만든다.

#### 요청 바디

```json
{
  "googleAccessToken": "ya29.a0ARrdaM-8kYQ3n1xVQ7pLmT2cRfZ9wB6sN4hJ0vD"
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| googleAccessToken | string | Google 로그인에서 받은 토큰.<br>우리 서비스의 access 토큰과 다른 값이라 이름을 구분했다 |

#### 서버 처리

| 순서 | 내용 |
|:---:|---|
| 1 | 받은 토큰이 우리 앱에 발급된 것인지 확인한다(client id 대조) |
| 2 | Google 사용자 정보를 조회해 계정 고유값으로 사용자를 찾고, 없으면 만든다 |
| 3 | access 토큰과 refresh 토큰을 발급해 응답 헤더에 싣는다.<br>refresh 토큰은 DB 에도 저장한다 |

#### 응답 200

| 헤더 | 값 |
|:---:|---|
| Authorization | `Bearer {accessToken}` |
| Refresh-Token | `{refreshToken}` |

```json
{"userId": 12, "name": "신해원", "profileCompleted": false}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| userId | number | 사용자 번호 |
| name | string | Google 계정 이름 |
| profileCompleted | boolean | 기본정보를 입력했는지.<br>false 면 화면이 기본정보 입력(UI‑02)으로 보낸다 |

#### 오류

401 `INVALID_GOOGLE_TOKEN`

### 4.2 토큰 재발급 `POST /api/auth/refresh`

access 토큰이 만료되면 화면의 공통 요청 처리기가 자동으로 부른다.

#### 요청 헤더

| 헤더 | 설명 |
|:---:|---|
| Refresh-Token | 로그인 때 받은 refresh 토큰 |

#### 응답 200

바디 없음. 새 access 토큰을 `Authorization` 응답 헤더에 싣는다.

#### 오류

403 `INVALID_REFRESH_TOKEN`

### 4.3 로그아웃 `POST /api/auth/logout`

왼쪽 메뉴의 로그아웃. 저장된 refresh 토큰을 지운다.

#### 요청 바디

```json
{"refreshToken": "eyJhbGciOiJIUzI1NiJ9..."}
```

#### 응답 204

바디 없음

#### 오류

400 `INVALID_INPUT` (바디에 `refreshToken` 이 없을 때), 401 `UNAUTHORIZED`

### 4.4 기술 목록 `GET /api/tech-tags`

기본정보 입력과 마이페이지의 기술 선택지. 보유 기술과 관심 기술이 같은 목록을 쓴다.

#### 응답 200

```json
{
  "techTags": [
    {"techTagId": 85, "name": "Spring", "field": "Backend"},
    {"techTagId": 2, "name": "React", "field": "Frontend"}
  ]
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| techTags[].techTagId | number | 기술 번호.<br>프로필 저장 때 이 값을 보낸다 |
| techTags[].name | string | 기술 이름 |
| techTags[].field | string | 계열.<br>화면이 이 값으로 칩을 묶는다 |

20종을 아래 순서 그대로 내려준다. 9/22 적재한 기술 사전 541종 중 427종은 글 한 편에만 붙어 있어, 골랐을 때 추천 글이 나오는 기술로 줄였다.

| 계열 | 기술 |
|:---:|---|
| Backend | Spring, Spring Boot, MySQL, Kotlin, SSE |
| Frontend | React, JavaScript, Vite |
| Data | LLM, RAG, MCP, Claude Code, Machine Learning, Airflow |
| Infra | Kafka, Kubernetes, Redis, AWS |
| Mobile | Android, Flutter |

#### 오류

없음

### 4.5 프로필 저장 · 조회 · 수정 `POST` · `GET` · `PUT /api/profile`

세 API 는 같은 모양의 프로필을 돌려준다. 화면이 한 가지 모양만 다루면 된다.

#### 요청 바디 (POST · PUT)

```json
{
  "jobFields": ["Backend", "Data"],
  "careerYears": 0,
  "haveSkillIds": [85, 24],
  "wantSkillIds": [88, 16, 12]
}
```

| 항목 | 형식 | 규칙 |
|:---:|:---:|---|
| jobFields | string 배열 | 희망 직무.<br>`Backend` `Frontend` `Data` `Infra` `Mobile` 중 1개 이상 |
| careerYears | number | 경력 년수. 0 은 신입, null 은 미입력.<br>음수 불가 |
| haveSkillIds | number 배열 | 보유 기술 번호. 0개 이상 |
| wantSkillIds | number 배열 | 관심 기술 번호. 1개 이상 5개 이하.<br>추천의 기준 |

#### 응답 (POST 201 · GET 200 · PUT 200)

```json
{
  "userId": 1,
  "name": "신해원",
  "jobFields": ["Backend", "Data"],
  "careerYears": 0,
  "haveSkills": [
    {"techTagId": 85, "name": "Spring"},
    {"techTagId": 24, "name": "MySQL"}
  ],
  "wantSkills": [
    {"techTagId": 88, "name": "Kafka"},
    {"techTagId": 16, "name": "Redis"},
    {"techTagId": 12, "name": "LLM"}
  ],
  "profileVersion": 1
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| haveSkills · wantSkills | 객체 배열 | 기술 번호와 이름.<br>화면이 이름을 바로 그릴 수 있게 함께 보낸다 |
| profileVersion | number | 최초 저장 시 1, 이후 저장할 때마다 1 오른다.<br>값이 같아도 오른다.<br>오르면 기존 AI 가이드는 다시 만든다 |

#### 오류

| 상태 | code | 언제 |
|:---:|:---:|---|
| 400 | INVALID_INPUT | jobFields 가 비었거나 5종 밖의 값, careerYears 음수, wantSkillIds 0개 또는 6개 이상, 없는 기술 번호, 같은 기술을 보유와 관심에 함께 보냄.<br>마지막 경우 field 는 `wantSkillIds` |
| 401 | UNAUTHORIZED | 토큰 없음 |
| 409 | PROFILE_ALREADY_EXISTS | POST 만.<br>이미 프로필이 있는 사용자.<br>수정은 PUT 을 쓴다 |

### 4.6 기업 목록 `GET /api/companies`

내 관심 기술과 겹치는 정도로 기업 8곳을 정렬한다.

#### 응답 200

```json
{
  "companies": [
    {
      "companyId": 5,
      "name": "올리브영",
      "summary": "(한 줄 소개)",
      "logoUrl": "(로고 주소)",
      "matchedSkillCount": 2,
      "totalSkillCount": 2,
      "matchedPostCount": 8,
      "totalPostCount": 41,
      "matchedSkills": [
        {"name": "Kafka", "postCount": 5},
        {"name": "Redis", "postCount": 4}
      ],
      "recommended": true
    }
  ]
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| logoUrl | string, null | 공식 로고 주소.<br>없으면 null |
| matchedSkillCount | number | 내 관심 기술 중 이 기업 글에 나온 기술 수 |
| totalSkillCount | number | 내가 고른 관심 기술 수 |
| matchedPostCount | number | 내 관심 기술이 붙은 글 수 |
| totalPostCount | number | 이 기업의 기술 글 수.<br>기술 글이 아닌 글은 세지 않는다 |
| matchedSkills | 객체 배열 | 겹친 기술과 이 기업에서 그 기술이 붙은 글 수 |
| recommended | boolean | 정렬 1위 한 곳만 true |

#### 정렬

`matchedSkillCount` 내림차순, 같으면 `matchedPostCount` 내림차순. 순서는 관심 기술로만 정하고 희망 직무는 쓰지 않는다. `matchedSkillCount` 가 0 인 기업도 목록에서 빼지 않고 화면이 흐리게 그린다. 기업이 8곳뿐이라 빼면 화면이 비어 보인다.

#### 오류

400 `PROFILE_REQUIRED`(기본정보를 한 번도 저장하지 않은 계정. 화면은 02 기본정보 입력으로 보낸다), 401 `UNAUTHORIZED`

### 4.7 기업 상세 `GET /api/companies/{id}`

기업 상세 화면 상단의 소개와 집계.

#### 응답 200

```json
{
  "companyId": 5,
  "name": "올리브영",
  "logoUrl": "(로고 주소)",
  "summary": "(한 줄 소개)",
  "mainBusiness": "(주요 사업)",
  "sourceUrl": "https://oliveyoung.tech",
  "checkedAt": "2026-09-21",
  "stats": {
    "postCount": 41,
    "firstPublishedAt": "(첫 글 날짜)",
    "lastPublishedAt": "(마지막 글 날짜)",
    "topCategories": [
      {"name": "Backend", "count": 19},
      {"name": "Frontend", "count": 5}
    ],
    "topSkills": [{"name": "Kafka", "count": 5}, {"name": "Redis", "count": 4}]
  }
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| summary · mainBusiness · sourceUrl · checkedAt | string, null | 직접 작성하는 소개 4가지.<br>비어 있으면 null 이고 화면이 그 칸을 그리지 않는다 |
| stats.postCount | number | 기술 글 수 |
| stats.firstPublishedAt · lastPublishedAt | string | 수집 범위의 첫 글과 마지막 글 날짜 |
| stats.topCategories | 객체 배열 | 많이 쓴 계열 5개 |
| stats.topSkills | 객체 배열 | 많이 나온 기술 5개 |

집계는 모두 기술 글만 센다. 기업 목록의 `totalPostCount`, 글 목록의 `filter.totalCount` 와 같은 기준이라 세 화면의 숫자가 같다.

#### 오류

404 `NOT_FOUND`

### 4.8 기업의 글 목록 `GET /api/companies/{id}/posts`

기업 상세 화면의 글 카드. 희망 직무로 거른 뒤 관심 기술이 겹치는 순으로 정렬한다. 페이지 나누기 없이 한 번에 내린다. 가장 많은 기업이 68편(토스 · LY)이라 나눌 필요가 없다.

#### 요청 쿼리

| 이름 | 형식 | 기본값 | 설명 |
|:---:|:---:|:---:|---|
| onlyMySkills | boolean | true | true 면 관심 기술이 하나라도 걸린 글만.<br>false 면 이 기업의 기술 글 전체를 최신순으로 |

#### 고르는 순서

| 순서 | 조건 |
|:---:|---|
| 1 | 기술 글이 아닌 글은 뺀다 |
| 2 | 글의 계열이 내 희망 직무 중 하나와 같아야 한다 |
| 3 | 계열이 `General`(AI 도구·조직 이야기)인 글은 관심 기술이 걸릴 때만 넣는다 |
| 4 | onlyMySkills 가 true 면 관심 기술이 하나라도 걸린 글만 남긴다 |

2~4 는 onlyMySkills 가 true 일 때 적용한다. false 면 1 만 거치고 최신순으로 내린다.

#### 정렬

겹친 관심 기술 수 내림차순, 같으면 발행일 최신순.

#### 관심 기술 매칭

글의 핵심 기술과 부분 일치로 본다(단어 경계 기준). 정확히 같을 때만 인정하면 관심 기술 `LLM` 이 글의 `LLM 에이전트` 를 놓친다.

#### 응답 200

```json
{
  "filter": {"onlyMySkills": true, "matchedCount": 8, "totalCount": 41},
  "posts": [
    {
      "postId": 128,
      "title": "올영매장은 MSA 환경에서 흩어진 도메인 데이터를 어떻게 연동했을까?",
      "publishedAt": "2026-03-18",
      "categories": ["Backend"],
      "level": "중급",
      "summary": "(한 줄 요지)",
      "skills": ["Redis", "Kafka", "API Integration"],
      "matchedSkills": ["Kafka", "Redis"],
      "charCount": 8520,
      "sectionCount": 12,
      "originalUrl": "https://oliveyoung.tech/(글 주소)",
      "bookmarked": false,
      "hasGuide": false
    }
  ]
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| filter.matchedCount | number | 관심 기술이 걸린 글 수 |
| filter.totalCount | number | 이 기업의 기술 글 수 |
| posts[].categories | string 배열 | 글의 계열 |
| posts[].level | string | 난이도.<br>입문, 중급, 고급 |
| posts[].summary | string | 수집할 때 AI 가 뽑은 한 줄 요지 |
| posts[].skills · matchedSkills | string 배열 | 글의 핵심 기술과, 그중 내 관심 기술과 겹친 것 |
| posts[].sectionCount | number | 구간 수. 0 이면 읽기 가이드 대상이 아니다 |
| posts[].bookmarked | boolean | 내가 저장한 글인지 |
| posts[].hasGuide | boolean | 지금 프로필 버전으로 만든 가이드가 있는지 |

관심 기술에 맞는 글이 한 편도 없으면 빈 목록을 그대로 내린다. 서버가 다른 글로 채우지 않고, 화면이 「전체 글 보기」로 안내한다.

#### 오류

401 `UNAUTHORIZED`, 404 `NOT_FOUND`

### 4.9 글 원문 `GET /api/posts/{id}`

글 읽기 화면 왼쪽. 원문 HTML 과 구간 목차를 내린다.

#### 응답 200

```json
{
  "postId": 128,
  "title": "(글 제목)",
  "publishedAt": "2026-03-18",
  "companyName": "올리브영",
  "categories": ["Backend"],
  "skills": ["Redis", "Kafka", "API Integration"],
  "charCount": 8520,
  "originalUrl": "https://oliveyoung.tech/(글 주소)",
  "contentHtml": "<h2 id=\"section-1\">(구간 1 제목)</h2><p>...</p> ...",
  "sections": [
    {"seq": 1, "heading": "(구간 1 제목)", "charCount": 474},
    {"seq": 2, "heading": "(구간 2 제목)", "charCount": 679}
  ],
  "bookmarked": false
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| contentHtml | string | 원문 HTML.<br>구간마다 `id="section-{seq}"` 표시가 박혀 있어 목차를 누르면 그 위치로 이동한다 |
| sections | 객체 배열 | 구간 목차.<br>seq 는 1부터 |
| bookmarked | boolean | 내가 저장한 글인지 |

#### 오류

401 `UNAUTHORIZED`, 404 `NOT_FOUND`

### 4.10 AI 읽기 가이드 `POST /api/posts/{id}/guide`

글 읽기 화면 오른쪽. 서비스에서 사용자가 기다리는 동안 AI 를 부르는 유일한 API 다.

#### 재사용

사용자, 글, 프로필 버전, 지시문 버전이 모두 같으면 저장해 둔 결과를 AI 호출 없이 돌려준다. 프로필을 수정하면 버전이 올라 새로 만든다.

#### AI 에 넣는 것

글 제목, 계열, 난이도, 번호를 붙인 구간 전체, 사용자의 경력 년수 · 보유 기술 · 관심 기술 · 희망 직무. 글 하나가 평균 9,569자(배포 기술 글 기준)라 나누지 않고 한 번에 넣는다. 경력 년수는 설명 수준을 맞추는 데만 쓰고 목록을 거르는 데는 쓰지 않는다.

#### 응답 200

```json
{
  "guideId": 913,
  "cached": true,
  "basis": {
    "careerYears": 0,
    "haveSkills": ["Spring"],
    "wantSkills": ["Kafka", "Redis"]
  },
  "focusSections": [2, 3, 6],
  "sectionBadges": [
    {"seq": 1, "badge": "LIGHT"},
    {"seq": 2, "badge": "KEY"},
    {"seq": 3, "badge": "KEY"},
    {"seq": 4, "badge": "SKIP"},
    {"seq": 5, "badge": "REF"},
    {"seq": 6, "badge": "KEY"}
  ],
  "section1Text": "(이 글에서 볼 부분)",
  "section2Text": "(내 수준에서 읽는 순서)",
  "section3Text": "(이어서 공부할 것)"
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| cached | boolean | true 면 저장해 둔 결과, false 면 이번에 새로 만든 결과 |
| basis | 객체 | 가이드를 만든 근거가 된 프로필 |
| focusSections | number 배열 | 먼저 읽을 구간 번호.<br>`KEY` 를 받은 구간과 같다 |
| sectionBadges | 객체 배열 | 구간마다 등급 하나.<br>`KEY` 먼저 읽기, `LIGHT` 훑어보기, `REF` 참고, `SKIP` 건너뛰기.<br>개수는 구간 수와 같다 |
| section1Text ~ section3Text | string | 이 글에서 볼 부분, 내 수준에서 읽는 순서, 이어서 공부할 것.<br>각 2~3문장 |

소제목이 없어 구간을 나눌 수 없는 기술 글 27편(배포 기준)은 가이드 대상이 아니다. 이 글들은 글 목록에서 `sectionCount` 가 0 으로 내려온다.

#### 오류

| 상태 | code | 언제 |
|:---:|:---:|---|
| 400 | PROFILE_REQUIRED | 경력 년수가 비어 프로필이 없는 경우 |
| 401 | UNAUTHORIZED | 토큰 없음 |
| 404 | NOT_FOUND | 없는 글 번호 |
| 502 | AI_UNAVAILABLE | Gemini 호출 실패, 또는 응답이 형식에 맞지 않음.<br>화면은 원문을 그대로 두고 가이드 영역만 비운다 |

### 4.11 글 저장 `POST /api/bookmarks/{postId}`

#### 응답 201

```json
{
  "bookmarkId": 55,
  "postId": 128,
  "createdAt": "2026-09-21T14:04:00+09:00"
}
```

#### 오류

401 `UNAUTHORIZED`, 404 `NOT_FOUND`(없는 글), 409 `ALREADY_BOOKMARKED`(이미 저장한 글)

### 4.12 저장한 글 목록 `GET /api/bookmarks`

최근 저장 순.

#### 응답 200

```json
{
  "bookmarks": [
    {
      "bookmarkId": 55,
      "postId": 128,
      "companyId": 5,
      "title": "(글 제목)",
      "companyName": "올리브영",
      "categories": ["Backend"],
      "summary": "(AI가 수집 때 뽑은 한 줄 요지)",
      "skills": ["Redis", "Kafka", "API Integration"],
      "publishedAt": "2026-03-18",
      "savedAt": "2026-09-21T14:04:00+09:00",
      "hasGuide": true
    }
  ],
  "count": 1
}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| companyId | number | 카드를 누르면 `/{companyId}/{postId}` 로 이동할 때 쓴다 |
| categories | string 배열 | 글의 계열 |
| summary | string | 한 줄 요지. 04 글 목록과 같은 값 |
| skills | string 배열 | 글의 핵심 기술. 04 글 목록과 같은 값 · 같은 순서 |
| savedAt | string | 저장 시각.<br>이 값 기준 최신순 |
| hasGuide | boolean | 지금 프로필 버전으로 만든 가이드가 있는지 |

#### 오류

401 `UNAUTHORIZED`

### 4.13 저장 해제 `DELETE /api/bookmarks/{postId}`

#### 응답 204

바디 없음

#### 오류

401 `UNAUTHORIZED`, 404 `NOT_FOUND`(저장하지 않은 글)

### 4.14 글 수집과 적재 `POST /api/admin/collect` · `POST /api/admin/collect/import`

관리자 전용이라 부르는 화면이 없다. 글을 DB 에 넣는 입구는 둘이고 저장 처리는 하나다. 두 입구 모두 글 주소로 중복을 걸러 여러 번 불러도 행이 늘지 않는다.

| 입구 | 하는 일 |
|:---:|---|
| 웹 수집 `/api/admin/collect` | 기업 블로그에서 글을 받아 구간으로 나누고, AI 로 분류한 뒤 저장한다 |
| 파일 적재 `/api/admin/collect/import` | 수집 단계에서 만들어 둔 파일을 읽어 저장한다 |

AI 분류는 글마다 이 시점에 한 번만 부른다. 사용자가 서비스를 쓰는 동안에는 분류용 AI 를 부르지 않는다. 받는 값은 기술 글 여부, 계열, 난이도, 핵심 기술 1~3개, 한 줄 요지다.

#### 웹 수집 요청 바디

```json
{"companyId": 1}
```

| 항목 | 형식 | 설명 |
|:---:|:---:|---|
| companyId | number | 수집할 기업.<br>바디를 생략하면 전체 기업을 수집한다 |

#### 웹 수집 응답 202

```json
{
  "jobId": "collect-20260919-001",
  "startedAt": "2026-09-19T03:00:00+09:00"
}
```

#### 파일 적재 응답 200

요청 바디 없음. 파일 위치는 서버 설정(`COLLECT_DATA_DIR`)에서 읽는다. 아래 예시는 9/22 로컬 적재 결과다.

```json
{
  "companyCount": 8,
  "techTagCount": 541,
  "newPosts": 402,
  "skipped": 0,
  "sectionCount": 4753,
  "tagCount": 899
}
```

#### 오류

| 상태 | code | 언제 |
|:---:|:---:|---|
| 401 | UNAUTHORIZED | 토큰 없음 |
| 403 | FORBIDDEN | 사용자 권한이 `ADMIN` 이 아님 |
| 404 | NOT_FOUND | 없는 companyId 를 넣었을 때 |

위 오류는 웹 수집에만 해당한다. 파일 적재는 로그인 없이 열어 두었다.

# 역할 분담

설계 문서([design.md](design.md))를 기준으로 7명이 비슷한 양을 맡도록 나눴어요. 2026-10-30 팀 회의에서 정한 역할 분담 V1에 이후 결정(ECR 배포, V1·V2 단계)을 반영했어요.

①~⑦은 동기 통신(내부 API), A~C는 비동기 통신(Kafka 메시지) 번호예요. 뜻은 맨 아래 부록 A · B에 있어요.

"원래 코드"는 원본 저장소(mini8-backend)에서의 경로예요. 1번이 초기 분리 커밋으로 각 서비스 폴더에 옮겨 두면, 각자 그 코드에서 시작해요. 경로 표기는 [README.md](README.md#경로-표기)에 있어요.

## 한눈에 보기

| # | 역할 | 맡는 것 | 원래 코드 |
| --- | --- | --- | --- |
| 1 | Gateway · 인프라 | Gateway · Eureka · Config · 공통 모듈(웹) | `commons/config` · `filter` · `token` · `exception` · `handler` |
| 2 | user · bookmark 서비스 | user 서비스 + bookmark 서비스 | `features/user` · `features/bookmark` |
| 3 | content 서비스 (조회 API) | content 조회 API + 다른 서비스용 내부 API 제공 | `features/company` · `post` · `tech`, `CompanyPostQueryRepository` |
| 4 | content 서비스 (메시지 수신) | Kafka 메시지 수신: 글 저장 · 가이드 표시 저장 | `collect/service/PostIngestService` (저장 부분) |
| 5 | collector 서비스 · 로컬 환경 | collector 서비스 + 로컬 docker-compose | `features/collect`, `resources/prompts/classify-v1.txt` |
| 6 | guide 서비스 · Kafka 공통 | guide 서비스 + Kafka 공통 설정 (토픽 · 메시지 DTO · 발행/수신 설정) | `features/guide`, `resources/prompts/guide-v1.txt` |
| 7 | CI/CD · 테스트 | GitHub Actions CI/CD + 통합 테스트 | `Dockerfile` (단일 서버용), workflow는 새로 만듦 |

나눈 기준:

- **큰 일 + 작은 일**: 작업이 큰 것과 작은 것을 한 사람에게 묶었어요 (guide + Kafka 공통, user(중) + bookmark(소)).
- **content는 둘로**: 코드량과 다른 서비스와 엮인 곳이 가장 많아서 조회 API와 메시지 수신으로 나눴어요.
- **시기가 다른 일 묶기**: CI는 초반, CD는 중반, 테스트는 후반이라 7번이 한꺼번에 몰리지 않아요.

## 범위

| 단계 | 하는 것 |
| --- | --- |
| V1 | 서비스 5개 분리, 동기 통신(OpenFeign), 비동기 통신(Kafka 발행·수신), GitHub Actions CI/CD (ECR + EC2 + RDS, 필수 10번), 선택 1 Spring Cloud Bus · 2 설정 암호화 · 4 클라우드 실배포 |
| V2 | Kafka 이벤트 동기화 처리 (재시도 · DLT · 중복 무시 · 순서 뒤바뀜, 선택 10번). V1 통합 테스트 통과 후 시작 |
| 안 함 | Saga, outbox |

V2 작업의 담당은 V1이 끝난 뒤 정해요. 수신 공통 설정은 6번, 수신 핸들러는 4번이 맡는 것을 기본으로 해요.

## 역할별 할 일

### 1. Gateway · 인프라

- 초기 분리 커밋: 원본 코드를 5개 서비스 폴더로 나눠 옮겨요. 서비스 사이의 참조를 Feign·Kafka로 바꾸는 일은 각 담당자가 해요.
- JWT 검사를 Gateway로 옮기고, 검사가 끝나면 `X-User-Id`, `X-User-Role` 헤더를 붙여 넘겨요.
- 클라이언트가 직접 보낸 `X-User-*` 헤더는 지우고, `/api/admin/**`는 ADMIN만 통과시켜요.
- 라우팅 순서를 정해요. `/api/posts/{id}/guide`를 `/api/posts/{id}`보다 먼저 매칭해요.
- Eureka(서비스 등록), Config(공통 설정 · JWT 키)를 띄워요.
- private 설정 저장소를 만들고, 비밀 값을 `{cipher}`로 암호화해 넣고, 팀원에게 저장소 읽기 권한과 `ENCRYPT_KEY`를 전달해요.
- Spring Cloud Bus(Kafka)를 연결해 `/actuator/busrefresh`로 설정을 한 번에 갱신하게 해요.
- 공통 모듈: 헤더에서 회원 번호를 읽는 도구, 오류 응답 형식 `{code, message, field}`, Feign 타임아웃 설정

**완료 기준** 프론트가 `VITE_API_BASE_URL`만 Gateway 주소로 바꾸면 모든 화면이 동작해요.
**함께 맞출 사람** 전원 (헤더 · 오류 형식 사용), 7번 (배포 설정)

### 2. user · bookmark 서비스

- user: 구글 로그인, 토큰 발급 · 재발급 · 로그아웃, 프로필 등록 · 조회 · 수정을 옮겨요.
- user: 내부 API ①④를 제공하고, ③을 호출하고, 프로필이 바뀌면 B `recu.profile.updated`를 발행해요.
- bookmark: 추가 · 목록 · 삭제를 옮기고, ⑦을 호출하고, ②(lookup)를 제공해요.
- bookmark: 4번이 만든 가이드 표시 수신 코드를 적용해요.

**완료 기준** 프로필을 수정하면 메시지가 발행되고, 북마크 목록에 글 정보와 가이드 배지가 표시돼요.
**함께 맞출 사람** 3번 (③⑦ 제공), 4번 (가이드 표시 수신), 6번 (Kafka 공통 모듈)

### 3. content 서비스 (조회 API)

- 기업 목록 · 상세 · 기업별 글 목록, 글 원문, 기술 태그 API를 옮겨요.
- 다른 서비스 테이블을 직접 조회하던 코드를 바꿔요: user 데이터는 ①, bookmark 데이터는 ②, 가이드 배지는 `user_guide_flag` 조회로.
- 다른 서비스가 쓰는 내부 API ③⑤⑥⑦을 제공해요. 첫날 stub(가짜 응답)부터 열어요.
- bookmark 서비스가 응답하지 않으면 "저장 안 됨"으로 보여주는 fallback을 만들어요.

**완료 기준** content 코드에 다른 서비스 테이블을 직접 조회하는 부분이 남아 있지 않아요.
**함께 맞출 사람** 2번 (①② 제공), 4번 (`user_guide_flag`), 2 · 5 · 6번 (내부 API 사용)

### 4. content 서비스 (메시지 수신)

- A `recu.post.collected`를 받아 저장해요. 기존 `PostIngestService`의 저장 로직(글 · 구간 · 분류어 · 태그, 처음 보는 기술 추가, 같은 URL 건너뛰기)을 옮겨요.
- 기업 8곳의 기본 데이터를 content에서 준비해요.
- C `recu.guide.created`를 받으면 `user_guide_flag`에 (사용자, 글)을 추가하고, B `recu.profile.updated`를 받으면 그 사용자의 줄을 지워요.
- 이 수신 코드를 content와 bookmark에 똑같이 적용해요 (bookmark 쪽은 2번과 함께).

**완료 기준** 수집한 글이 content DB에 저장되고, 가이드를 만들면 표시가 붙고 프로필을 고치면 지워져요.
**함께 맞출 사람** 5번 (글 메시지 내용), 6번 (수신 공통 모듈), 2번 (bookmark 적용)

### 5. collector 서비스 · 로컬 환경

- `features/collect`를 옮기고, DB 직접 저장을 A `recu.post.collected` 발행으로 바꿔요 (글 1편당 메시지 1개).
- 내부 API ⑥(수집 대상 기업 목록, 저장된 글 URL)을 호출해요.
- `collect_job` 테이블을 만들고, 파일 적재(import)도 같은 메시지로 발행해요. 수집 API 두 개는 ADMIN 전용으로 바꿔요.
- 수집 작업 조회 API(`GET /api/admin/collect/jobs`, `/jobs/{jobId}`)를 새로 만들어요.
- 로컬 `docker-compose`: MariaDB(서비스별 DB 5개), Kafka, 덤프 데이터 적재. `infra/db/`의 DB·계정 SQL과 덤프 데이터 파일을 만들고, 각 서비스 담당이 테이블 생성 SQL을 넣을 자리를 정해요. Config Server가 설정 저장소 토큰과 `ENCRYPT_KEY`를 `.env`로 받게 해요

**완료 기준** 팀원 누구나 명령 한 줄로 전체를 실행할 수 있고, 관리자 수집이 끝까지 동작해요.
**함께 맞출 사람** 3번 (⑥ 제공), 4번 (메시지 수신), 6번 (발행 공통 모듈)

### 6. guide 서비스 · Kafka 공통

- guide: `GuideService`를 옮기고, ④⑤ 호출과 Gemini를 연결하고, 새 가이드를 저장하면 C `recu.guide.created`를 발행해요.
- Kafka 공통 설정: 토픽 3개 생성, 메시지 봉투 · DTO 3개 (필드 이름 확정)
- 발행(`KafkaTemplate`) · 수신(`@KafkaListener`) 설정을 공통으로 만들어서 다른 담당자가 가져다 쓰게 해요.

**완료 기준** Kafka 메시지를 보내고 받는 모든 서비스가 같은 공통 설정을 써요.
**함께 맞출 사람** 전원 (공통 모듈 사용), 2번 (④ 제공), 3번 (⑤ 제공)

### 7. CI/CD · 테스트

- 초반: `ci.yml` (PR마다 테스트 · 빌드), CI를 통과해야 병합되도록 브랜치 보호 설정
- 중반: 서비스별 Dockerfile, ECR 저장소, `cd.yml` (변경된 서비스만 배포), EC2 배포 서버와 `.env`(`ENCRYPT_KEY` · 설정 저장소 토큰) · GitHub Secrets · OIDC 역할 준비, RDS(MariaDB 1대) 생성 후 5번의 SQL로 DB 5개 · 계정 생성
- 후반: [통합 테스트 체크리스트](#통합-테스트-체크리스트-7번이-확인)로 동기 · 비동기 흐름을 끝까지 확인하고 결과를 공유해요.

**완료 기준** main에 병합하면 자동 배포되고, 통합 테스트 체크리스트 6개가 모두 통과해요.
**함께 맞출 사람** 전원 (Dockerfile · 테스트), 1번 (설정 저장소 · 비밀 값), 4 · 5 · 6번 (비동기 흐름 확인)

## 진행 순서

| 역할 | 1단계: 약속 정하기 (처음 1~2일) | 2단계: 서비스 분리 + 동기 통신 | 3단계: Kafka 연동 + 배포 | 4단계: 통합 테스트 |
| --- | --- | --- | --- | --- |
| 1 Gateway · 인프라 | Gateway · Eureka 골격, 헤더 규칙 공유 | Config, 공통 모듈, 라우팅 · 권한 | 배포 환경에서 Gateway 설정 점검 | 각자 맡은 기능 통합 테스트, 버그 수정, 프론트 연결 확인 |
| 2 user · bookmark | 내부 API ①②④ 스펙 확정 · stub 공개 | user · bookmark 코드 이전, Feign ③⑦ 연결 | B 발행, bookmark에 가이드 표시 수신 적용 | 〃 |
| 3 content 조회 | 내부 API ③⑤⑥⑦ 스펙 확정 · stub 공개 | 조회 API 이전, Feign ①② + fallback | `user_guide_flag`로 가이드 배지 표시 | 〃 |
| 4 content 수신 | `user_guide_flag` 테이블 확정 | 글 저장 로직 이전, 기업 기본 데이터 | 메시지 3개 수신 (글 저장 · 가이드 표시) | 〃 |
| 5 collector · 로컬 | docker-compose (MariaDB · Kafka) | collector 이전, ⑥ 호출, collect_job · 작업 조회 API | A 발행 (웹 수집 · import) | 〃 |
| 6 guide · Kafka | 메시지 DTO 3개 · 토픽 확정 | guide 이전, ④⑤ + Gemini, Kafka 공통 설정 | C 발행, 발행 · 수신 공통 설정 | 〃 |
| 7 CI/CD · 테스트 | ci.yml, 브랜치 보호 | Dockerfile 템플릿, ECR | cd.yml, EC2 배포 서버, Secrets | 통합 테스트 체크리스트 실행 |

여기까지가 V1이에요. V2(Kafka 동기화 처리)는 V1 통합 테스트가 통과한 뒤 시작해요.

## 협업 규칙

- **내부 API는 제공하는 쪽이 만들고, 첫날 stub(가짜 응답)부터 공개해요.** 3번이 제공하는 API가 가장 많아서 특히 먼저 열어야 다른 사람이 막히지 않아요.
- **메시지 DTO는 6번의 공통 설정만 써요.** 각자 만들면 필드 이름이 어긋나요.
- **`feat/*` → `develop` PR은 CI를 통과해야 병합해요.** `main` · `develop`에 직접 push하지 않아요. 자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있어요.
- **다른 서비스의 테이블은 읽지 않아요.** 필요한 값은 내부 API로 묻거나, Kafka로 받은 메시지를 저장해서 써요.
- **막히면 "함께 맞출 사람"에게 먼저 물어봐요.** 스펙을 바꿔야 하면 "스펙 변경" 이슈를 열고 [design.md](design.md)를 먼저 고쳐요.

## 통합 테스트 체크리스트 (7번이 확인)

| # | 흐름 | 이렇게 해 봐요 | 이렇게 나오면 성공 | 통신 |
| --- | --- | --- | --- | --- |
| 1 | 로그인 → 기업 목록 | 구글 로그인 후 프로필 등록, 기업 목록 열기 | 관심 기술 글이 많은 기업부터 보여요 | 동기 ① |
| 2 | 글 원문 · 북마크 | 기업의 글 목록 → 글 원문 → 북마크 추가 · 삭제 | 저장 표시가 바로 바뀌고, 북마크 목록에 글 정보가 보여요 | 동기 ② ⑦ |
| 3 | AI 가이드 | 글 원문에서 AI 가이드 만들기 | 가이드가 보이고, 글 목록 · 북마크 목록에 "가이드 있음"이 붙어요 | 동기 ④ ⑤ · 비동기 C |
| 4 | 프로필 수정 | 관심 기술을 바꿔서 프로필 저장 | 기업 순서가 바뀌고, 기존 "가이드 있음" 표시가 사라져요 | 동기 ③ · 비동기 B |
| 5 | 관리자 수집 | ADMIN 계정으로 수집 시작 | 새 글이 기업의 글 목록에 나타나요 | 동기 ⑥ · 비동기 A |
| 6 | 권한 확인 | 일반 계정으로 관리자 API 호출, 토큰 없이 로그인 필요 API 호출 | 403 · 401 오류가 정해진 형식으로 와요 | Gateway |

V2 검증 시나리오는 [design.md 6장](design.md#증빙-시나리오)에 있어요.

## 부록 A. 내부 API 번호표 (①~⑦)

서비스끼리만 쓰는 길이에요. Gateway에 연결하지 않아서 바깥에서는 부를 수 없어요. 경로 이름은 1단계에서 확정해요.

| # | 호출 → 제공 | 내부 API | 받는 값 | 제공 담당 |
| --- | --- | --- | --- | --- |
| ① | content → user | `GET /internal/users/{userId}/preference` | 직무 · 관심 기술 · 프로필 버전 | 2번 |
| ② | content → bookmark | `POST /internal/bookmarks/lookup` | 저장한 글 번호들 | 2번 |
| ③ | user → content | `GET /internal/tech-tags?ids=…` | 기술 번호 · 이름 | 3번 |
| ④ | guide → user | `GET /internal/users/{userId}/profile` | 경력 · 직무 · 보유/관심 기술 · 버전 | 2번 |
| ⑤ | guide → content | `GET /internal/posts/{postId}/guide-input` | 제목 · 계열 · 난이도 · 구간 | 3번 |
| ⑥ | collector → content | `GET /internal/companies`<br>`GET /internal/posts/urls?companyId=…` | 수집 대상 기업 목록, 이미 저장된 글 URL | 3번 |
| ⑦ | bookmark → content | `POST /internal/posts/summaries` | 제목 · 기업 · 계열 · 요약 · 기술 · 작성일 | 3번 |

## 부록 B. Kafka 메시지 3개 (A · B · C)

| # | 토픽 | 발행 | 수신 | 키 | 수신 쪽이 하는 일 |
| --- | --- | --- | --- | --- | --- |
| A | `recu.post.collected` | collector (5번) | content (4번) | url | 글 · 구간 · 태그 · 분류어 저장 |
| B | `recu.profile.updated` | user (2번) | content · bookmark | userId | 그 사용자의 "가이드 있음" 표시 삭제 |
| C | `recu.guide.created` | guide (6번) | content · bookmark | userId | "가이드 있음" 표시 추가 |

보낸 서비스는 답을 기다리지 않고 바로 자기 일을 해요. 이름 규칙은 `프로젝트.무엇이.어떻게됐다`(과거형)예요.

## 부록 C. 서비스별 테이블

| 서비스 | 원래 있던 테이블 | 새로 생기는 테이블 | 담당 |
| --- | --- | --- | --- |
| user | app_user · user_job_field · user_skill | — | 2번 |
| content | company · blog_post · blog_post_section · blog_post_category · blog_post_tag · tech_tag | user_guide_flag | 3 · 4번 |
| collector | — | collect_job | 5번 |
| guide | ai_guide | — | 6번 |
| bookmark | bookmark | user_guide_flag | 2 · 4번 |

다른 서비스의 회원 번호 · 글 번호는 외래 키 없이 숫자만 저장해요. 테이블 정의는 [design.md 8장](design.md#8-db-분리)에 있어요.

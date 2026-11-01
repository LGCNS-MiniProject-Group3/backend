# 문서

ReCu MSA 백엔드의 문서는 모두 이 폴더에 있어요.

| 문서 | 내용 | 누가 고치나 |
| --- | --- | --- |
| [design.md](design.md) | 설계 최종본: 서비스 경계, 내부 API ①~⑦, Kafka 메시지 A~C, DB, CI/CD. **스펙의 기준** | 스펙 변경 이슈 합의 후 누구나 |
| [roles.md](roles.md) | 7명 역할 분담, 담당별 할 일, 진행 순서, 통합 테스트 체크리스트 | 팀 합의 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 이슈 → 브랜치 → 커밋·push → PR → 리뷰 → 병합 6단계, 커밋·브랜치 이름 규칙 | 팀 합의 |
| [code-convention.md](code-convention.md) | 저장소·패키지 구조, 이름, API·오류·Feign·Kafka·DB·로그·테스트 규칙 | 팀 합의 |
| [ai-guidelines.md](ai-guidelines.md) | AI 도구를 쓸 때 지킬 것. AI는 초안만, 실행은 사람이 | 팀 합의 |
| [reference/api-spec.md](reference/api-spec.md) | 원본 외부 API 17개의 요청·응답·오류 (8조 제출본 사본) | 고치지 않음 |
| [reference/erd.md](reference/erd.md) | 원본 테이블 11개, 관계, 제약 (8조 제출본 사본) | 고치지 않음 |

## 어디에 무엇을 남기나

| 정보 | 둘 곳 |
| --- | --- |
| 작업 방식, 코드 규칙 | `docs/` |
| 원본 스펙 (바뀌지 않음) | `docs/reference/` |
| MSA 설계 (스펙 변경 이슈로만 바뀜) | `docs/design.md` |
| 누가 무엇을 하고 어디까지 했나 | GitHub 이슈 · 마일스톤 · PR. **문서로 따로 만들지 않아요** |
| 개별 작업의 맥락 | 이슈 본문. AI에게는 이슈 번호로 알려 줘요 |

## 처음 온 사람이 읽는 순서

1. [roles.md](roles.md)에서 내 역할과 "함께 맞출 사람"
2. [design.md](design.md)에서 내 서비스가 나오는 장
3. [CONTRIBUTING.md](CONTRIBUTING.md)의 "처음 한 번 할 일"
4. [code-convention.md](code-convention.md)
5. AI 도구를 쓴다면 [ai-guidelines.md](ai-guidelines.md)

## 원본 코드

문서에서 **"원본"**은 1차 미니프로젝트 8조의 저장소를 말해요. 둘 다 공개 저장소라 따로 보관하지 않아요.

| 저장소 | 주소 | 브랜치 |
| --- | --- | --- |
| 원본 백엔드 | https://github.com/LGCNS-AM06-8/mini8-backend | `develop` |
| 원본 프론트 | https://github.com/LGCNS-AM06-8/mini8-frontend | `develop` |

- **백엔드 코드는 받을 필요 없어요.** 1번이 원본 코드를 서비스별 폴더로 나눠 옮긴 초기 분리 커밋에서 시작해요. 원래 모습이나 커밋 이력이 궁금하면 위 주소에서 웹으로 봐요. API 명세와 ERD는 [reference/](reference/api-spec.md)에 사본이 있어요.
- **프론트는 화면으로 확인할 때 받아요.** 통합 테스트와 시연에서 필요해요. API 주소(`VITE_API_BASE_URL`)만 Gateway로 바꿔 실행해요.
- 받을 때는 **이 저장소 밖**에 받아요. 안에 받으면 원본이 커밋될 수 있어요. 원본은 읽기만 하고, 고치거나 push 하지 않아요.

### 경로 표기

문서에 원본 경로를 짧게 적을 때는 아래를 기준으로 해요.

| 문서에 적힌 모양 | 원본 저장소의 실제 위치 |
| --- | --- |
| `features/…`, `commons/…`, `database/…` | `src/main/java/com/mini8/backend/` 아래 |
| `resources/…` | `src/main/resources/` 아래 |
| `mini8-content-dump.sql`, `Dockerfile`, `build.gradle` | 저장소 루트 |

이 저장소의 경로는 저장소 루트 기준으로 적어요. 예) `gateway/`, `infra/db/`

### 원본에서 이 저장소로 가져올 파일

코드 말고도 가져와야 하는 파일이에요. 초기 분리 커밋에 들어 있지 않으면, 담당자가 이슈를 열고 PR로 올려요.

| 원본 파일 | 이 저장소 위치 | 담당 |
| --- | --- | --- |
| `mini8-content-dump.sql` | `infra/db/` | 5번 |
| `resources/prompts/classify-v1.txt` | `collector/src/main/resources/prompts/` | 5번 |
| `resources/prompts/guide-v1.txt` | `guide/src/main/resources/prompts/` | 6번 |
| `build.gradle`의 Spotless 설정 | 루트 `build.gradle` | 1번 |
| `.env.example` | 루트 `.env.example` (서비스별 변수로 다시 정리) | 1번 |
| `Dockerfile` | 서비스별 `Dockerfile`의 바탕 | 7번 |

## 문서 밖에 있는 규칙 파일

도구가 정해진 위치에서만 읽기 때문에 `docs/` 밖에 둔 파일이에요. 내용은 위 문서를 따르고, 고칠 때는 문서와 함께 고쳐요.

| 파일 | 읽는 도구 | 내용 |
| --- | --- | --- |
| `AGENTS.md` (루트) | Codex, Cursor 등 AI 도구 | 반드시 지킬 것 7개와 이 폴더 안내 |
| `CLAUDE.md` (루트) | Claude Code | `AGENTS.md`와 `ai-guidelines.md`를 불러옴 |
| `.claude/settings.json` | Claude Code | git·GitHub 쓰기 명령 차단 |
| `.githooks/` | git | 코드 모양, 커밋 메시지, push 대상·브랜치 이름 검사 |
| `.gitattributes` | git | 줄바꿈을 LF로 통일 (Windows에서도 훅·`gradlew` 실행) |
| `.github/ISSUE_TEMPLATE/`, `.github/pull_request_template.md` | GitHub | 이슈·PR 템플릿 |

# AGENTS.md

AI 도구가 이 저장소에서 일할 때 자동으로 읽는 파일이에요. 도구가 루트에서만 찾기 때문에 여기 두고, 자세한 규칙은 `docs/`에 있어요. **작업을 시작하기 전에 아래 문서 중 필요한 것을 먼저 읽어요.**

| 문서 | 언제 읽나 |
| --- | --- |
| [docs/ai-guidelines.md](docs/ai-guidelines.md) | 항상. AI가 할 수 있는 것과 없는 것 |
| [docs/design.md](docs/design.md) | 서비스 경계, 내부 API, Kafka 메시지, DB를 다룰 때. **스펙의 기준** |
| [docs/code-convention.md](docs/code-convention.md) | 코드를 쓸 때 |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | 이슈·커밋 메시지·PR 초안을 쓸 때 |
| [docs/roles.md](docs/roles.md) | 담당자와 담당 폴더를 확인할 때 |
| [docs/reference/](docs/reference/api-spec.md) | 원본 외부 API와 테이블을 확인할 때. 고치지 않는 사본 |

## 반드시 지킬 것

1. **git과 GitHub에 흔적을 남기는 동작은 하지 않아요.** 이슈 생성, 브랜치 생성·전환, `git add`·`commit`·`push`·`merge`·`rebase`·`reset`, PR 생성, 리뷰 게시, 승인, 병합은 사람이 해요. AI는 그 내용의 초안만 써요. 읽기 명령(`git status`, `git diff`, `git log`)은 써도 돼요.
2. **담당 서비스 폴더 밖을 고치지 않아요.** 필요하면 먼저 사람에게 물어요.
3. **다른 서비스의 테이블을 읽거나, 다른 서비스 엔티티를 참조하지 않아요.** 내부 API(`/internal/**`)나 Kafka 메시지로 받아요.
4. **스펙을 추측하지 않아요.** `docs/design.md`와 `docs/reference/`에 없는 필드·경로·토픽은 만들지 말고 확인할 것으로 남겨요.
5. **비밀 값(`.env`, `*.pem`, 키)을 읽거나 쓰지 않아요.**
6. **V1 작업 중에 V2 기능을 넣지 않아요.** 지금 범위는 이슈의 마일스톤으로 확인해요.
7. **실행 확인 없이 완료라고 하지 않아요.** 바꾼 모듈은 `./gradlew :<모듈>:check`로 확인하고 결과를 그대로 알려요.

## 프로젝트 요약

- 1차 미니프로젝트 8조의 ReCu(기술 블로그 읽기 안내) 모놀리스를 5개 서비스로 나눠요. 문서의 "원본"은 그 저장소([LGCNS-AM06-8/mini8-backend](https://github.com/LGCNS-AM06-8/mini8-backend))예요. 원본 코드는 초기 분리 커밋으로 각 서비스 폴더에 옮겨져 있으니, 이 저장소의 코드를 기준으로 고쳐요. 원래 모습을 확인해야 하는데 읽을 수 있는 곳에 없으면 사람에게 요청하고, 원본은 **읽기만** 해요. 원본 코드를 추측해서 쓰지 않아요.
- Java 17, Spring Boot 3.4.5, Spring Cloud 2024.0.x, Kafka, MariaDB, Docker Compose, GitHub Actions → AWS ECR → EC2.
- V1: 서비스 분리, Feign, Kafka 발행·수신, CI/CD. V2: Kafka 동기화 처리(재시도·DLT·멱등성·순서).

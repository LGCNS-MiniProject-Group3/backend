# 코드 규칙

서비스 다섯 개를 일곱 명이 나눠 만들기 때문에, 어느 서비스를 열어도 같은 자리에 같은 이름이 있도록 맞춰요. 원본 저장소([mini8-backend](https://github.com/LGCNS-AM06-8/mini8-backend))의 규칙을 이어 쓰고, MSA에서 새로 생긴 것만 더했어요. "원본"이 무엇인지는 [README.md](README.md#원본-코드)에 있어요.

> **기본값 표시.** `(1번 확정)`, `(6번 확정)`이 붙은 항목은 그 담당이 골격을 만들면서 최종 결정해요. 바뀌면 이 문서를 먼저 고쳐요.

## 저장소 구조

Gradle 멀티 프로젝트 하나로 묶어요. (1번 확정)

```
(저장소 루트)/
├─ settings.gradle            # 모든 모듈 include
├─ build.gradle               # 공통 플러그인·버전 (Spotless, Java 17, Spring Cloud BOM)
├─ common/
│  ├─ common-web/             # 헤더 리졸버, 오류 응답, Feign 설정 (1번)
│  └─ common-kafka/           # 토픽 이름, 메시지 봉투·DTO, 발행·수신 설정 (6번)
├─ gateway/                  # :8000
├─ discovery/                # :8761 Eureka
├─ config/                   # :8888 Config Server
├─ user/                     # :8081
├─ content/                  # :8082
├─ collector/                # :8083
├─ guide/                    # :8084
├─ bookmark/                 # :8085
├─ infra/
│  ├─ compose.yaml            # 로컬 전체 실행 (5번)
│  ├─ compose.prod.yaml       # EC2 실행 (7번)
│  └─ db/                     # DB·계정 생성, 서비스별 테이블 생성 SQL, 원본 덤프. 로컬 컨테이너와 RDS 공용 (5번)
├─ .github/workflows/         # CI/CD (7번)
└─ docs/
```

- 업무 서비스는 `implementation project(':common:common-web')`로 공통 모듈을 가져다 써요. Kafka를 쓰는 서비스만 `common-kafka`를 더해요.
- 모듈 사이 의존은 `서비스 → common` 한 방향만 있어요. 서비스끼리 서로 의존하지 않아요. 다른 서비스의 DTO가 필요하면 내 서비스에 Feign 응답용 DTO를 따로 만들어요.

## 패키지 구조

패키지 루트는 `com.recu.<서비스>`예요. 원본의 `features/<도메인>/ctrl · service · repository · domain` 구조를 그대로 써요.

```
com.recu.content/
├─ ContentApplication.java
├─ config/                     # 이 서비스만의 설정 (Feign 활성화, Swagger 등)
├─ client/                     # 다른 서비스를 부르는 Feign 클라이언트
│  ├─ UserClient.java
│  └─ dto/
├─ messaging/                  # Kafka 발행·수신
│  └─ GuideCreatedListener.java
├─ database/
│  └─ entity/                  # JPA 엔티티는 여기에만
└─ features/
   └─ company/
      ├─ ctrl/                 # CompanyController, InternalCompanyController
      ├─ service/
      ├─ repository/
      └─ domain/dto/
```

- 엔티티는 `database/entity/` 한 곳에만 둬요. `features/*/domain/entity`를 만들지 않아요.
- 내부 API 컨트롤러는 같은 도메인의 `ctrl/`에 두고 이름 앞에 `Internal`을 붙여요.

## 이름

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 모듈 (폴더) | 짧은 이름 하나. 커밋 범위·브랜치·라벨·ECR 저장소와 같은 이름 | `content`, `discovery` |
| `spring.application.name` | 모듈 이름과 같게 | `content` |
| ECR 저장소 | `recu-<모듈>` | `recu-content` |
| 컨트롤러 | `<도메인>Controller` | `BookmarkController` |
| 내부 API 컨트롤러 | `Internal<도메인>Controller` | `InternalPostController` |
| 서비스 | `<도메인>Service` | `BookmarkService` |
| 리포지토리 | `<엔티티>Repository` | `BlogPostRepository` |
| 엔티티 | `<테이블>Entity` (원본 유지) | `BlogPostEntity`, `UserGuideFlagEntity` |
| 요청·응답 DTO | `<무엇>RequestDTO`, `<무엇>ResponseDTO` (원본 유지) | `ProfileRequestDTO` |
| Feign 클라이언트 | `<상대 서비스>Client` | `UserClient`, `ContentClient` |
| Kafka 메시지 DTO | `<무엇이><어떻게됐다>Event` (6번 확정) | `PostCollectedEvent`, `GuideCreatedEvent` |
| Kafka 수신 클래스 | `<메시지>Listener` | `ProfileUpdatedListener` |
| Kafka 발행 클래스 | `<메시지>Publisher` | `GuideCreatedPublisher` |
| 오류 코드 enum | `<서비스>ErrorCode` | `ContentErrorCode` |
| 상수·토픽 이름 | `UPPER_SNAKE_CASE` | `TOPIC_GUIDE_CREATED` |

## API

### 외부 API (`/api/**`)

- **경로, 메서드, 요청·응답 필드는 원본([reference/api-spec.md](reference/api-spec.md))과 똑같이 유지해요.** 프론트는 Gateway 주소만 바꿔요. 바꿔야 하면 스펙 변경 이슈를 열어요.
- 신규 외부 API는 수집 작업 조회 2개뿐이에요([design.md 7장](design.md#수집-작업-조회-신규-외부-api)). 이것도 스펙은 design.md가 기준이에요.
- 회원 번호는 `X-User-Id` 헤더에서 `common-web`의 도구로 받아요. 원본의 `@AuthenticationPrincipal Long userId`를 바꿔요. (1번 확정)

```java
@PostMapping("/{postId}")
public ResponseEntity<BookmarkResponseDTO> addBookmark(
    @CurrentUserId Long userId, @PathVariable Long postId) {
  return ResponseEntity.status(HttpStatus.CREATED)
      .body(bookmarkService.addBookmark(userId, postId));
}
```

- 서비스는 JWT를 다시 검사하지 않아요. 권한은 Gateway가 이미 확인했어요.
- Swagger 어노테이션(`@Operation`, `@ApiResponses`)은 원본처럼 달아요.

### 내부 API (`/internal/**`)

- [design.md 5장](design.md#5-동기-통신-openfeign)의 ①~⑦만 만들어요. 새로 만들려면 스펙 변경 이슈를 열어요.
- 목록이 필요하면 id를 여러 개 받는 일괄 조회로 만들어요. 반복문 안에서 Feign을 부르지 않아요.
- 응답은 꼭 필요한 필드만 담아요. 엔티티를 그대로 돌려주지 않아요.
- 제공하는 쪽이 첫날 고정 값을 돌려주는 stub부터 열어요.

### 오류 응답

- 형식은 `{ "code", "message", "field" }` 하나예요. 스택 트레이스를 응답에 싣지 않아요.
- 서비스마다 `<서비스>ErrorCode` enum을 만들고 `common-web`의 `ErrorCode` 인터페이스를 구현해요. 던질 때는 `BusinessException`을 써요. (1번 확정)
- `code`는 `UPPER_SNAKE_CASE`이고, 원본에 있던 코드는 이름을 바꾸지 않아요.
- 여러 서비스가 같이 쓰는 코드(아래 표의 `ALREADY_BOOKMARKED` 외 전부)는 `common-web`의 `CommonErrorCode`에 있어요. 서비스에만 있는 코드(`ALREADY_BOOKMARKED`, `PROFILE_REQUIRED` 등)만 `<서비스>ErrorCode`에 둬요.

| 상황 | HTTP | code 예 |
| --- | --- | --- |
| 입력값이 틀림 | 400 | `INVALID_INPUT` |
| 토큰 없음·만료 (Gateway) | 401 | `UNAUTHORIZED` |
| 권한 없음 (Gateway) | 403 | `FORBIDDEN` |
| 대상 없음 | 404 | `NOT_FOUND` |
| 이미 있음 | 409 | `ALREADY_BOOKMARKED` (bookmark) |
| 다른 서비스 호출 실패 | 502 | `UPSTREAM_ERROR` |

## Feign

```java
@FeignClient(name = "user", path = "/internal/users")
public interface UserClient {
  @GetMapping("/{userId}/preference")
  UserPreferenceResponseDTO getPreference(@PathVariable Long userId);
}
```

- `name`은 상대의 `spring.application.name`이에요. URL을 직접 적지 않아요 (Eureka로 찾음).
- 타임아웃은 `common-web`의 기본값을 써요. 서비스마다 바꾸지 않아요. (1번 확정)
- 실패하면 `UPSTREAM_ERROR`(502)로 바꿔 돌려줘요. fallback은 설계에서 정한 곳(② bookmark 조회)에만 둬요.

## Kafka

- 토픽 이름, 메시지 DTO, `KafkaTemplate`·`@KafkaListener` 설정은 `common-kafka`의 것만 써요. 서비스에서 같은 DTO를 새로 만들지 않아요. (6번 확정)
- 메시지는 공통 봉투(`eventId`, `eventType`, `occurredAt`, `payload`)에 담아요.
- 키는 [design.md 6장](design.md#6-비동기-통신-kafka)대로 써요. A는 url, B·C는 userId예요.
- 발행은 DB 트랜잭션이 커밋된 뒤에 해요(`@TransactionalEventListener(phase = AFTER_COMMIT)` 등). 저장이 롤백됐는데 메시지만 나가는 일을 막아요.
- 수신 핸들러는 같은 메시지를 두 번 받아도 결과가 같게 써요 (UNIQUE 키로 건너뛰기). 재시도·DLT는 V2에서 `common-kafka`에 넣어요.
- `groupId`는 `<모듈 이름>`으로 해요. 예) `content`

## DB · JPA

- 내 서비스 스키마의 테이블만 다뤄요.
- 다른 서비스의 행은 `Long userId`, `Long blogPostId`처럼 값으로만 가져요. `@ManyToOne`으로 묶지 않아요.
- 연관 관계는 기본 `LAZY`로 해요. 목록 조회에서 N+1이 생기면 fetch join이나 일괄 조회로 고쳐요.
- 조회만 하는 서비스 메서드에는 `@Transactional(readOnly = true)`를 달아요.
- 테이블은 `infra/db/`의 SQL로 만들고, JPA는 확인만 해요. `ddl-auto`는 local · dev · prod 모두 `validate`, 테스트(H2)만 `create-drop`이에요. 엔티티와 SQL이 다르면 서비스가 뜨지 않아서 바로 알 수 있어요.

### 테이블 생성 SQL

| 파일 | 내용 | 쓰는 사람 |
| --- | --- | --- |
| `infra/db/01-databases.sql` | DB 5개, 서비스별 계정과 권한 | 5번 |
| `infra/db/02-<모듈>-schema.sql` | 그 서비스의 `CREATE TABLE` | 각 서비스 담당 |
| `infra/db/03-content-data.sql` | 원본 덤프의 데이터(INSERT)만 | 5번 |

- 엔티티를 바꾸면 **같은 PR에서** 자기 `02-<모듈>-schema.sql`도 고쳐요. 리뷰어는 둘이 맞는지 봐요.
- 서비스 계정에는 데이터 권한(SELECT, INSERT, UPDATE, DELETE)만 줘요. 테이블을 만들거나 지울 권한은 주지 않아요.
- 로컬에서 SQL을 다시 적용하려면 `docker compose down -v`로 DB 볼륨을 지우고 다시 띄워요.
- RDS에 이미 만든 테이블을 바꿔야 하면, PR 본문 메모에 `ALTER TABLE` 문을 적고 7번이 배포 전에 RDS에 실행해요.

## 설정

- 각 서비스의 `application.yml`에는 `spring.application.name`과 Config Server 주소만 둬요. 나머지는 private 설정 저장소의 `<모듈>.yml`에 둬요([design.md 9장](design.md#config-설정-저장소)).
- 프로필은 `local`(IDE에서 서비스 실행), `dev`(compose로 전체 스택), `prod`(EC2) 세 개예요.
- 설정 변경을 바로 다시 읽어야 하는 빈에만 `@RefreshScope`를 달아요. 갱신은 Spring Cloud Bus(`/actuator/busrefresh`)로 해요.
- 비밀 값은 설정 저장소에 `{cipher}`로만 넣어요. 코드와 이 저장소의 설정 파일에는 비밀 값을 쓰지 않아요.
- 실행 환경 변수 파일은 `.env` 하나만 써요. `.env.local` 같은 다른 이름을 만들지 않아요.
- `.env.example`에는 실행 환경 변수(`ENCRYPT_KEY`, 설정 저장소 토큰, 로컬 DB root 비밀번호)의 이름만 적고 값은 비워 둬요.

## 로그

- `System.out.println`을 쓰지 않아요. Lombok의 `@Slf4j`를 써요.
- 토큰, 비밀번호, API 키, 이메일 같은 개인 정보는 로그에 남기지 않아요.
- 레벨: 정상 흐름의 주요 지점은 `info`, 예상한 실패(404, 409)는 `warn` 이하, 예상하지 못한 실패는 `error`와 예외를 함께 남겨요.
- Kafka 수신 로그에는 토픽, 키, `eventId`를 남겨요. 메시지 본문 전체(특히 글 HTML)는 남기지 않아요.

## 코드 모양

- Spotless(google-java-format 1.17.0)로 맞춰요. 들여쓰기는 2칸이에요. (원본 유지)
- 커밋하면 `pre-commit` 훅이 검사해요. 고치려면 `./gradlew spotlessApply`.
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j`를 써요. 엔티티에는 `@Setter`와 `@Data`를 쓰지 않아요.
- 의존성 주입은 생성자 주입(`@RequiredArgsConstructor` + `private final`)만 써요.
- 주석은 "왜"를 적어요. 코드를 그대로 읽어 주는 주석은 달지 않아요.

## 테스트

- `./gradlew :<모듈>:test`가 MariaDB, Kafka 없이 돌아가야 해요. DB는 H2(원본과 같음), Kafka는 `@EmbeddedKafka`를 써요.
- 최소한 아래는 테스트를 둬요.

| 대상 | 테스트 | 담당 |
| --- | --- | --- |
| 내가 제공하는 내부 API | `@WebMvcTest`로 경로·응답 필드 확인 | 제공하는 사람 |
| 원본에서 다른 서비스 테이블을 읽던 곳 | Feign 클라이언트를 mock해서 결과가 원본과 같은지 확인 | 옮기는 사람 |
| Kafka 수신 핸들러 | 같은 메시지 두 번 → 결과 한 번 | 받는 사람 |
| Gateway 필터 | 토큰 없음 401, 일반 사용자의 `/api/admin/**` 403, 클라이언트가 보낸 `X-User-Id` 제거 | 1번 |

- 테스트 이름은 한국어로 써도 돼요. `@DisplayName("이미 저장한 글을 다시 저장하면 409")`
- 외부 서비스(Gemini, Google, 블로그 사이트)를 실제로 부르는 테스트는 기본 실행에서 빼요.

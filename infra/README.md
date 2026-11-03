# infra

로컬에서 백엔드를 띄우는 Docker Compose 설정이에요 (5번).

| 실행 방법 | 프로필 | compose로 띄우는 것 | 앱 실행 |
| --- | --- | --- | --- |
| **local** | `local` | MariaDB · Kafka | IDE(bootRun)에서 직접 |
| **dev** | `dev` | 전체 스택 (아래 10개) | compose가 이미지로 |

서비스 이름 `discovery` · `mariadb` · `kafka`는 설정 저장소 `application-dev.yml`의 호스트 이름과 같아야 해요.

## 준비

1. 저장소 루트(`backend/`)에서 `.env.example`을 `.env`로 복사해요.
2. `.env`에 값을 채워요. 값은 이 README나 이슈·PR에 적지 않아요.

| 변수 | local | dev | 받는 곳 |
| --- | --- | --- | --- |
| `MARIADB_ROOT_PASSWORD` | 필수 | 필수 | 각자 정함 |
| `ENCRYPT_KEY` | IDE 실행 시 | 필수 | 1번 |
| `CONFIG_REPO_TOKEN` | IDE 실행 시 | 필수 | 1번 |
| `OPENAI_API_KEY` | 선택 | 선택 | 1번. 비우면 AI 호출만 실패 |

dev는 `SPRING_PROFILES_ACTIVE`, `CONFIG_URL`을 compose가 정해요. `.env`의 값(`local`, 빈 값)은 쓰지 않아요.

## 기동

모든 명령은 `backend/` 루트에서 실행해요.

### local: 인프라만

```bash
docker compose -f infra/compose.yaml --env-file .env up -d mariadb kafka
```

서비스 이름을 빼고 `up -d`만 하면 전체 스택이 떠요.

### dev: 전체 스택

```bash
# 1. 이미지를 하나씩 빌드해요 (아래 "알려진 제한" 1번)
for m in discovery config user content collector guide bookmark gateway; do
  docker compose -f infra/compose.yaml --env-file .env build "$m" || break
done

# 2. 띄워요
docker compose -f infra/compose.yaml --env-file .env up -d
```

처음 빌드는 오래 걸려요. 그다음부터는 Gradle 캐시 덕분에 빨라요.
코드를 바꾼 서비스만 다시 빌드하려면 `build <서비스>` 후 `up -d <서비스>` 하면 돼요.

기동 순서는 `depends_on`으로 정해져 있어요. 앞 단계가 healthy가 되어야 다음이 떠요.

```
mariadb · kafka · discovery → config → user · content · collector · guide · bookmark → gateway
```

## 확인

| 구성 요소 | 주소 (호스트에서) | 컨테이너 안에서 |
| --- | --- | --- |
| Gateway | `http://127.0.0.1:8000` | `gateway:8000` |
| Eureka 대시보드 | `http://127.0.0.1:8761` | `discovery:8761` |
| Config | `http://127.0.0.1:8888` | `config:8888` |
| user · content · collector · guide · bookmark | `127.0.0.1:8081` ~ `8085` | `<서비스>:<포트>` |
| MariaDB | `127.0.0.1:3306` | `mariadb:3306` |
| Kafka | `127.0.0.1:9092` | `kafka:9092` |

서비스 포트(8081~8085)는 디버깅용이에요. 실제 요청은 Gateway(8000)로 보내요.

```bash
# 전부 healthy인지
docker compose -f infra/compose.yaml --env-file .env ps

# 한 서비스 로그
docker compose -f infra/compose.yaml --env-file .env logs <서비스> --tail 100

# Gateway 경유 (로그인 없이 되는 API)
curl -i http://127.0.0.1:8000/api/tech-tags
```

Eureka 대시보드에 GATEWAY · USER · CONTENT · COLLECTOR · GUIDE · BOOKMARK가 하나씩 UP이면 정상이에요.

## 중지 · SQL 다시 적용

```bash
# 중지 (데이터 유지)
docker compose -f infra/compose.yaml --env-file .env down

# DB 볼륨까지 지우고 init SQL 다시 적용
docker compose -f infra/compose.yaml --env-file .env down -v
docker compose -f infra/compose.yaml --env-file .env up -d
```

`infra/db/`의 SQL은 MariaDB 컨테이너가 **처음** 만들어질 때만 적용돼요. SQL을 고친 뒤에는 `down -v` 후 다시 `up` 해요.

## 알려진 제한

1. **이미지를 한꺼번에 빌드하면 실패해요.** `up --build`처럼 8개를 동시에 빌드하면 Gradle 캐시 잠금(`Timeout waiting to lock journal cache`)으로 일부가 실패해요. 위의 순서대로 하나씩 빌드해요. Dockerfile 수정(`sharing=locked`)은 7번에게 요청했어요.
2. **IDE로 띄운 서비스는 먼저 꺼요.** dev와 IDE 실행이 섞이면 같은 서비스가 Eureka에 두 번 등록돼 요청이 엉뚱한 쪽으로 가요. 포트(8761 · 8888 · 808x)도 겹쳐요.
3. **dev에서 파일 적재(`/api/admin/collect/import`)는 아직 안 돼요.** `COLLECT_DATA_DIR`의 경로가 컨테이너 안에 없어서예요. 웹 수집은 돼요.
4. **자원이 넉넉해야 해요.** Docker Desktop 메모리 6GB 이상, 디스크 여유 15GB 이상을 권장해요. 서비스마다 힙은 256MB(`JAVA_TOOL_OPTIONS`)예요.

## db/ 파일

| 파일 | 담당 | 내용 |
| --- | --- | --- |
| `01-databases.sql` | 5번 | DB 5개 · 서비스별 계정 · 권한 |
| `02-user-schema.sql` | 2번 (초안은 infra) | `app_user`, `user_job_field`, `user_skill` |
| `02-content-schema.sql` | 3·4번 (초안은 infra) | `company`, `blog_post`, …, `user_guide_flag` |
| `02-collector-schema.sql` | 5번 | `collect_job` |
| `02-guide-schema.sql` | 6번 (초안은 infra) | `ai_guide` |
| `02-bookmark-schema.sql` | 2·4번 (초안은 infra) | `bookmark`, `user_guide_flag` |
| `03-content-data.sql` | 5번 | 원본 덤프의 INSERT |

엔티티를 바꾸면 같은 PR에서 해당 `02-*-schema.sql`도 함께 고쳐요. 스키마 SQL을 고친 뒤에는 `down -v` 후 다시 `up` 해야 테이블이 다시 만들어져요.

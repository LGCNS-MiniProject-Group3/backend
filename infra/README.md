# infra

로컬에서 백엔드를 띄우는 Docker Compose 설정이에요 (5번).

| 실행 방법 | 프로필 | compose로 띄우는 것 | 앱 실행 |
| --- | --- | --- | --- |
| **local** | `local` | MariaDB · Kafka | IDE(bootRun)에서 직접 |
| **dev** | `dev` | 전체 스택 + 모니터링 (14개) | compose가 이미지로 |

서비스 이름 `discovery` · `mariadb` · `kafka`는 설정 저장소 `application-dev.yml`의 호스트 이름과 같아야 해요.

## 준비

1. 저장소 루트(`backend/`)에서 `.env.example`을 `.env`로 복사해요.
2. `.env`에 값을 채워요. 값은 이 README나 이슈·PR에 적지 않아요.

| 변수 | local | dev | 받는 곳 |
| --- | --- | --- | --- |
| `MARIADB_ROOT_PASSWORD` | 필수 | 필수 | 각자 정함 |
| `CONFIG_REPO_URI` | IDE 실행 시 | 필수 | 1번 |
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
docker compose -f infra/compose.yaml --env-file .env up -d --build
```

처음 빌드는 오래 걸려요. 그다음부터는 Gradle 캐시 덕분에 빨라요.
코드를 바꾼 서비스만 다시 빌드하려면 `up -d --build <서비스>` 하면 돼요.

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

## 모니터링 (V2 · #96 · #107)

dev 전체 스택 기동 명령에 Prometheus·Grafana·Zipkin·kafka-exporter가 포함돼요.
기존 IDE 서버는 먼저 종료해 포트 충돌과 Eureka 중복 등록을 피해주세요.
이번 작업으로 DB를 초기화할 필요는 없어요.

```bash
docker compose -f infra/compose.yaml --env-file .env up -d --build
```

| 도구 | 브라우저 주소 | 컨테이너 안 주소 |
| --- | --- | --- |
| Prometheus | http://127.0.0.1:9090 | http://prometheus:9090 |
| Grafana | http://127.0.0.1:3000 | http://grafana:3000 |
| Zipkin | http://127.0.0.1:9411 | http://zipkin:9411 |
| kafka-exporter | http://127.0.0.1:9308/metrics | http://kafka-exporter:9308 |

포트는 모두 내 컴퓨터(`127.0.0.1`)에만 열어요. EC2 반영은 #98에서 따로 정해요.
Grafana 최초 로그인은 공식 이미지 기본 계정 `admin` / `admin`이며, 처음 로그인하면 비밀번호를 변경해요.
변경한 비밀번호는 `grafana_data` 볼륨에 유지돼요. 실제 비밀번호는 Git에 올리지 않아요.
Prometheus 수집 데이터는 `prometheus_data` 볼륨에 최대 3일 보관하고, Zipkin은 메모리에 저장하므로 재시작하면 trace가 사라져요.
`down`은 DB·지표·Grafana 볼륨을 유지하지만, `down -v`는 이 데이터까지 모두 지워요.

### 수집·연결 확인

같은 compose 네트워크에 이름이 `zipkin`인 임시 컨테이너를 띄웠다면, 먼저 `docker stop zipkin`으로 중지해 이름 충돌을 피해주세요.

1. #94(라이브러리)와 #95(설정)가 반영된 서비스 이미지로 실행해요.
2. `http://127.0.0.1:9090/targets`에서 서비스 8개와 kafka-exporter, 모두 9개가 UP인지 확인해요.
   업무 서비스 5개와 Gateway는 Eureka에서 자동으로 찾고, Discovery·Config·kafka-exporter는 고정 주소를 사용해요.
   kafka-exporter는 `job="kafka-exporter"`로 따로 수집해서 서비스 상태(`up{job=~"recu-.*"}`)에는 섞이지 않아요. Kafka 연결은 `kafka_brokers`가 1이면 정상이에요.
   Eureka 등록·수집에는 약간의 시간이 걸려요. 등록 전에는 6개 서비스가 목록에 없을 수 있어요.
3. Gateway로 API를 호출한 뒤 Grafana의 Explore에서 Prometheus를 선택하고 아래 지표를 조회해요.
   요청이 없으면 HTTP 지표가 아직 없을 수 있어요.

   ```promql
   http_server_requests_seconds_count
   ```

   수집 상태는 `up{job=~"recu-.*"}`의 `instance`로 구분해요. HTTP 등 앱 지표의 서비스 구분 라벨 `application`은 #95 설정으로 앱이 붙이며, Prometheus는 중복으로 붙이지 않아요.
4. Zipkin 화면에서 호출한 서비스의 trace를 검색해요. #95의 dev Zipkin 전송 주소는
   `http://zipkin:9411/api/v2/spans`여야 해요. 화면만 열리는 것과 실제 trace가 쌓이는 것은 별도 확인이에요.

#94·#95가 아직 없으면 컨테이너와 화면은 떠도 지표 경로가 404이거나 trace가 없을 수 있어요.
이 상태는 전체 연동 완료가 아니에요. 이 설정은 dev 전체 compose용이며 IDE(local) 서버 수집용은 아니에요.

### 4번·2번 담당자에게 전달할 설정

- Grafana 데이터소스는 기동 시 자동 등록돼요. 이름/UID는 `Prometheus` / `prometheus`, `Zipkin` / `zipkin`이에요.
- 대시보드 JSON은 `infra/monitoring/grafana/provisioning/dashboards/json/`에 넣어요.
  Grafana의 `ReCu` 폴더에 자동 반영돼요. 대시보드 내용 자체는 4번 담당 범위예요.
  기본 대시보드는 `recu-overview.json`(ReCu 전체 상태)이에요. 칸 구성은 관측 가이드 1장(P1~P25)을 따라요.
  등록된 대시보드는 화면에서 저장되지 않아요(`allowUiUpdates: false`). 고칠 때는 JSON 파일을 고쳐요.
  화면에서 만든 대시보드를 공유하려면 JSON으로 내보내 이 폴더에 저장해요.
- k6 remote-write 수신 주소는 호스트 실행 시 `http://127.0.0.1:9090/api/v1/write`,
  같은 compose 네트워크 안에서는 `http://prometheus:9090/api/v1/write`예요.

### 설정 검사·로그

비밀 설정을 출력하지 않고 compose 구문과 Prometheus 설정을 검사할 수 있어요.

```bash
docker compose -f infra/compose.yaml --env-file .env config --quiet
docker compose -f infra/compose.yaml --env-file .env run --rm --no-deps --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose -f infra/compose.yaml --env-file .env logs --tail 100 prometheus grafana zipkin
```

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

1. **IDE로 띄운 서비스는 먼저 꺼요.** dev와 IDE 실행이 섞이면 같은 서비스가 Eureka에 두 번 등록돼 요청이 엉뚱한 쪽으로 가요. 포트(8761 · 8888 · 808x)도 겹쳐요.
2. **dev에서 파일 적재(`/api/admin/collect/import`)는 아직 안 돼요.** `COLLECT_DATA_DIR`의 경로가 컨테이너 안에 없어서예요. 웹 수집은 돼요.
3. **자원이 넉넉해야 해요.** Docker Desktop 메모리 6GB 이상, 디스크 여유 15GB 이상을 권장해요. 서비스마다 힙은 256MB(`JAVA_TOOL_OPTIONS`)예요.

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

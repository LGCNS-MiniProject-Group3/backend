# infra

로컬 MariaDB · Kafka (5번).

앱·Gateway는 포함하지 않습니다. `local` 프로필에서는 IDE로 서비스를 띄우고, 여기 인프라만 compose로 올립니다.

서비스 이름 `mariadb`, `kafka`는 설정 저장소의 `application-dev.yml`과 같아야 합니다.

## 준비

1. 저장소 루트(`backend/`)에서 `.env.example`을 `.env`로 복사합니다.
2. `.env`에 필요한 값을 채웁니다. (MARIADB_ROOT_PASSWORD 값은 팀 채널 등 비공개로 공유합니다. 이 README에는 적지 않습니다.)

## 기동

```bash
# backend 루트에서
docker compose -f infra/compose.yaml --env-file .env up -d
```

## 중지 · SQL 다시 적용

```bash
# 데이터 유지
docker compose -f infra/compose.yaml --env-file .env down

# 볼륨 삭제 후 init SQL 재실행
docker compose -f infra/compose.yaml down -v
docker compose -f infra/compose.yaml --env-file .env up -d
```

`infra/db/`의 SQL은 MariaDB 컨테이너가 **처음** 만들어질 때만 적용됩니다. SQL을 고친 뒤에는 `down -v` 후 다시 `up` 하세요.

## 확인

| 구성 요소 | 주소 |
| --- | --- |
| MariaDB | `localhost:3306` |
| Kafka | `localhost:9092` |

```bash
docker compose -f infra/compose.yaml ps
docker compose -f infra/compose.yaml logs mariadb --tail 50
docker compose -f infra/compose.yaml logs kafka --tail 50
```

## db/ 파일

| 파일 | 담당 |
| --- | --- |
| `01-databases.sql` | 5번 (DB·계정·권한) |
| `02-user-schema.sql` | 2번 |
| `02-content-schema.sql` | 3·4번 |
| `02-collector-schema.sql` | 5번 |
| `02-guide-schema.sql` | 6번 |
| `02-bookmark-schema.sql` | 2·4번 |
| `03-content-data.sql` | 5번 (덤프 INSERT, 후속) |

엔티티를 바꾸면 같은 PR에서 해당 `02-*-schema.sql`도 함께 수정합니다.

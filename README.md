LG CNS AM Inspire 6기, mini Project 2, 3조 Backend Github Repo입니다.

## 문서

설계, 역할 분담, 협업 규칙, 코드 규칙, AI 사용 규칙은 모두 [docs/](docs/README.md)에 있어요.

## 처음 한 번

```bash
git config core.hooksPath .githooks
```
       |
## rerecu v0

## 1. Gradle 및 프로젝트 설정 관리

본 프로젝트는 **Gradle 멀티 프로젝트(Multi-Project) 구조**를 사용한다.

최상위 Gradle에서 공통 빌드 환경과 버전을 관리하고, 각 마이크로서비스는 개별 `build.gradle`을 통해 필요한 의존성과 기능을 관리한다.


폴더 구조는 msa-toy 에 맞춰서 해주세요 

### 1.1 Gradle 관리 구조

```text
recu-msa/
│
├── settings.gradle          # 전체 프로젝트 및 하위 모듈 등록
├── build.gradle             # 전체 공통 빌드 설정
├── gradlew                  # Linux/macOS Gradle 실행 파일
├── gradlew.bat              # Windows Gradle 실행 파일
│
├── services/
│   ├── api-gateway/
│   │   └── build.gradle     # Gateway 전용 의존성
│   │
│   ├── discovery-service/
│   │   └── build.gradle     # Eureka 전용 의존성
│   │
│   ├── config-service/
│   │   └── build.gradle     # Config Server 전용 의존성
│   │
│   ├── user-service/
│   │   └── build.gradle     # 사용자 서비스 전용 의존성
│   │
│   ├── content-service/
│   │   └── build.gradle     # 콘텐츠 서비스 전용 의존성
│   │
│   ├── guide-service/
│   │   └── build.gradle     # AI 가이드 서비스 전용 의존성
│   │
│   └── bookmark-service/
│       └── build.gradle     # 북마크 서비스 전용 의존성
│
└── frontend/
    └── package.json         # 프론트엔드 패키지 관리
```


## 실행 포트 구성 (Port Configuration)

각 서비스는 포트 충돌을 방지하기 위해 고유 포트를 사용한다.

### 1. 서비스 포트

| 구분 | 서비스 | 포트 | 설명 |
|---|---|---:|---|
| Gateway | API Gateway | `8080` | 외부 클라이언트 요청 진입점 및 서비스 라우팅 |
| User | User Service | `8081` | 로그인, JWT, 사용자 프로필 관리 |
| Content | Content Service | `8082` | 기업, 기술 태그, 게시글 조회 및 관리 |
| Guide | Guide Service | `8083` | AI 읽기 가이드 생성 및 관리 |
| Bookmark | Bookmark Service | `8084` | 북마크 등록, 조회, 삭제 |
| Infrastructure | Eureka Server | `8761` | 마이크로서비스 등록 및 검색 |
| Infrastructure | Config Server | `8888` | 중앙 집중식 설정 관리 |
| Collector | Collector | 별도 포트 없음 | Content Service 내부 모듈로 실행 |

### 2. 포트 구성 규칙

- 외부 클라이언트는 기본적으로 API Gateway(`8080`)를 통해 서비스에 접근한다.
- 각 도메인 서비스는 독립적인 포트를 사용한다.
- 서비스 간 통신은 Eureka를 통한 서비스 검색 및 OpenFeign을 활용한다.
- Eureka Server는 `8761`, Config Server는 `8888`을 사용한다.
- Collector는 Content Service 내부 모듈이므로 별도 포트를 할당하지 않는다.

### 3. 서비스 접근 구조

```text
Client / Frontend
       |
       v
API Gateway : 8080
       |
       +---- User Service     : 8082
       |
       +---- Content Service  : 8084
       |
       +---- Guide Service    : 8088
       |
       +---- Bookmark Service : 8090

Infrastructure
       |
       +---- Eureka Server    : 8761
       |
       +---- Config Server    : 8888
```

### 4. 로컬 개발 환경

로컬 환경에서는 각 서비스를 개별 실행할 수 있으며, API 테스트는 Gateway 포트인 `8080`을 기준으로 진행한다.

예시:

```http
http://localhost:8080/api/auth/google
http://localhost:8080/api/profile
http://localhost:8080/api/companies
http://localhost:8080/api/bookmarks
```

> 포트 번호는 프로젝트의 기본 개발 설정이며, Docker 및 AWS 배포 환경에서는 별도의 포트 매핑과 환경 설정을 적용할 수 있다.
### 1.2 루트 Gradle과 서비스별 Gradle 역할

| 구분 | 관리 대상 | 주요 역할 |
|---|---|---|
| Root `settings.gradle` | 전체 프로젝트 | 하위 서비스 모듈 등록 및 프로젝트 이름 설정 |
| Root `build.gradle` | 공통 빌드 환경 | Java 버전, 공통 플러그인, Spring Boot 및 Spring Cloud 버전 관리 |
| Service `build.gradle` | 개별 서비스 | 서비스별 라이브러리, 의존성, 빌드 설정 관리 |
| `gradlew`, `gradlew.bat` | Gradle 실행 | 프로젝트에서 지정한 Gradle 버전으로 빌드 실행 |
| `gradle-wrapper.properties` | Gradle 버전 | 사용할 Gradle 배포 버전 지정 |
| `gradle-wrapper.jar` | Gradle 실행 | Gradle Wrapper 실행에 필요한 파일 |

### 1.3 공통 기술 및 설정

루트 Gradle에서 공통으로 관리할 항목이다.

- Java 17
- Spring Boot 버전
- Spring Cloud 버전
- 공통 Gradle 플러그인
- Maven Central 저장소
- 프로젝트 그룹 및 버전

### 1.4 서비스별 의존성 관리

각 서비스는 담당 기능에 필요한 의존성만 선언한다.

| 서비스 | 주요 의존성 및 기능 |
|---|---|
| API Gateway | Spring Cloud Gateway, Eureka Client, Config Client |
| Discovery Service | Eureka Server, Actuator |
| Config Service | Spring Cloud Config Server, Actuator |
| User Service | Spring Web, Spring Data JPA, Spring Security, JWT, MariaDB |
| Content Service | Spring Web, Spring Data JPA, OpenFeign, Spring Kafka, MariaDB |
| Guide Service | Spring Web, Spring Data JPA, OpenFeign, Spring Kafka, Gemini API 연동 |
| Bookmark Service | Spring Web, Spring Data JPA, OpenFeign, Spring Security, MariaDB |

※ 서비스별 의존성은 실제 구현 기능에 따라 추가하거나 제외한다.

### 1.5 Gradle 실행 방법

프로젝트 최상위 폴더에서 명령어를 실행한다.

| 작업 | 명령어 |
|---|---|
| 전체 프로젝트 빌드 | `./gradlew build` |
| 전체 테스트 | `./gradlew test` |
| 전체 프로젝트 정리 | `./gradlew clean` |
| 특정 서비스 빌드 | `./gradlew :services:user-service:build` |
| 특정 서비스 실행 | `./gradlew :services:user-service:bootRun` |

Windows 환경에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용한다.

---

## 2. 프로젝트 공통 설정 및 협업 파일

프로젝트 실행, 환경 설정, 코드 관리 및 팀 협업에 필요한 파일이다.

| 파일 | 역할 |
|---|---|
| `README.md` | 프로젝트 소개, 기술 스택, 실행 방법 및 문서 안내 |
| `.gitignore` | GitHub에 업로드하지 않을 파일 및 폴더 지정 |
| `.env.example` | 환경변수 구성 예시 제공 |
| `CONTRIBUTING.md` | 브랜치, 커밋, Pull Request 및 코드 리뷰 규칙 |
| `AGENTS.md` | AI 코딩 도구가 참고할 프로젝트 구조 및 개발 지침 |
| `Dockerfile` | 애플리케이션 Docker 이미지 생성 방법 정의 |
| `settings.gradle` | Gradle 하위 프로젝트 등록 |
| `build.gradle` | Gradle 빌드 및 의존성 설정 |

### 2.1 환경변수 관리

DB 접속 정보, JWT Secret, Gemini API Key 등 민감한 정보는 코드에 직접 작성하지 않는다.

- `.env.example`: 환경변수 이름과 예시만 관리
- `.env`: 개발 환경에서 실제 값 관리
- 실제 비밀번호 및 API Key는 GitHub에 업로드하지 않는다.

### 2.2 Docker 관리

각 마이크로서비스는 개별 Docker 이미지로 빌드할 수 있도록 구성한다.

- 서비스별 `Dockerfile` 관리
- 서비스별 이미지 빌드 및 실행
- Docker Compose를 통한 로컬 통합 실행
- 환경변수를 통한 서비스 설정 관리

### 2.3 협업 규칙

- `main`: 최종 배포 및 안정화된 코드 관리
- `develop`: 팀 공통 개발 및 통합 브랜치
- `feature/*`: 담당 기능별 개발 브랜치
- 기능 개발 후 Pull Request를 통해 `develop`에 병합
- 검증 완료 후 `main`에 반영


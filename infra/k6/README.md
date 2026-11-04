# k6 부하 테스트

Gateway(8000) 하나로 요청을 보내고, 기본으로 시나리오 1~3을 차례로 돌려요. 결과는 시나리오별 **요청 수 · p95 · 오류율**로 요약돼요.

> **비용 없이 돌리려면 `SCENARIOS`에 `guide`를 넣지 않아요.** 기본값(`browse,bookmark,profile`)이 그 상태라 `k6 run infra/k6/main.js`는 OpenAI를 부르지 않아요.
> `GUIDE_WARMUP=false`는 setup의 가이드 생성만 끌 뿐이라, 시나리오 4를 돌리면 캐시가 없을 때 부하 단계에서 OpenAI가 불려요. 시나리오 4는 OpenAI 비용이 드니 팀과 정한 경우에만 `SCENARIOS=guide`로 명시해서 실행해요. 자세한 건 아래 [가이드와 OpenAI 비용](#가이드와-openai-비용)을 봐요.

## 데이터 흐름

```
k6 ──> 8000 gateway ─┬─> 8081 user ──────> 8082 content (③ 기술 번호 검증)
                     │       └──=> 9092 Kafka B recu.profile.updated ──=> 8082 content, 8085 bookmark
                     ├─> 8082 content ─┬─> 8081 user (① 선호)
                     │                 └─> 8085 bookmark (② 저장 여부)
                     ├─> 8085 bookmark ──> 8082 content (⑦ 글 요약)
                     └─> 8084 guide ─────> 8081 user (④ 프로필)
                                           [캐시 없을 때만 -> 8082 content ⑤ -> OpenAI, => Kafka C]
```

`->`는 HTTP(Gateway 라우팅·Feign), `=>`는 Kafka 메시지예요. 요청마다 흐름이 `flow` 태그로 붙어서 `SUMMARY_JSON` 결과에서도 볼 수 있어요.

## 시나리오

| # | 시나리오 | 이름 | 요청과 비율 | 확인할 점 |
| --- | --- | --- | --- | --- |
| 1 | 출근길 추천 탐색 | `browse` | 태그·기업·기업별 글 목록 70%, 글 상세 30% | 조회 API p95, 오류율 |
| 2 | 북마크 쓸어담기 | `bookmark` | 글 조회 50%, 북마크 추가·삭제 40%(30%는 연타), 프로필 수정 10% | 쓰기 p95, 연타 시 409, Kafka B 발행 수 |
| 3 | 프로필 업데이트 파도 | `profile` | 글·기업 조회 → 프로필 수정 → 기업 목록 다시 보기 | 프로필 수정 p95, 이벤트 적체 |
| 4 (선택) | 가이드 재방문 러시 | `guide` | 글 상세 → 이미 만든 가이드 다시 열기 | 캐시 응답 p95, OpenAI 호출 0건. **기본 실행에서 제외** |

각 시나리오는 `워밍업(30s) → 일정 부하(1m) → 최대 부하(30s) → 정리(10s)` 단계로 돌아요.

## 실행

전체 스택을 띄운 뒤(`infra/README.md`의 dev) 저장소 루트에서 실행해요.

```bash
k6 run infra/k6/main.js
```

자주 쓰는 옵션이에요.

```bash
k6 run -e SCENARIOS=browse,bookmark infra/k6/main.js
```

```bash
k6 run -e WARMUP=10s -e STEADY=20s -e PEAK=10s infra/k6/main.js
```

회차별 결과를 파일로 남겨요. 보고서 표를 만들 때 써요. `infra/k6/results/`는 커밋되지 않아요(`.gitignore`).

```bash
k6 run -e SUMMARY_JSON=infra/k6/results/run-01.json infra/k6/main.js
```

```bash
k6 run -e BASE_URL=http://<EC2 주소>:8000 -e SUMMARY_JSON=infra/k6/results/run-01.json infra/k6/main.js
```

| 변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8000` | Gateway 주소 |
| `SCENARIOS` | `browse,bookmark,profile` | 돌릴 시나리오. `guide`는 명시할 때만 |
| `MODE` | `sequential` | `parallel`이면 고른 시나리오를 한꺼번에 |
| `WARMUP` `STEADY` `PEAK` `COOLDOWN` | `30s` `1m` `30s` `10s` | 단계별 시간 |
| `BROWSE_VUS` / `BROWSE_PEAK_VUS` | 20 / 50 | 일정 부하 / 최대 부하 VU. `BOOKMARK_*` 10/30, `PROFILE_*` 5/15, `GUIDE_*` 10/30 |
| `P95_MS` | 500 | 시나리오별 p95 기준치 |
| `ERROR_RATE` | 0.01 | 시나리오별 오류율 기준치 |
| `GUIDE_CACHED_P95_MS` | 300 | 가이드 캐시 응답 p95 기준치 |
| `GUIDE_USERS` / `GUIDE_POSTS` | 5 / 2 | 가이드 시나리오 고정 계정 수 / 글 수 |
| `GUIDE_WARMUP` | `false` | `true`면 setup에서 가이드 캐시를 만듦 (OpenAI 비용). `false`여도 시나리오 4의 부하 단계 요청은 나가므로 **비용을 막는 설정이 아님** |
| `SUMMARY_JSON` | (없음) | 결과 전체를 JSON 파일로 저장. `infra/k6/results/` 아래를 권장 |

## 테스트 데이터

`setup()`이 시작할 때 한 번 준비해요.

1. `GET /api/tech-tags`로 기술 번호를 읽어요.
2. 고른 시나리오의 최대 VU를 **모두 더한 수**만큼 `가입 → 로그인 → 프로필 등록`을 해요(기본 50 + 30 + 15 = 95명). 이메일은 `k6-load-<실행시각>-<n>@recu.test`라 실행마다 새 계정이에요.
    - VU 번호 하나에 계정 하나예요. k6의 VU 번호는 시나리오가 바뀌어도 이어서 늘어날 수 있어서, 시나리오별 최대 VU만큼만 만들면 두 VU가 같은 계정을 쓰게 돼요. 그러면 같은 글을 동시에 저장해 생긴 409가 오류율에 섞여요.
3. 기업 목록과 기업별 글 목록에서 글 번호를 모아요.
4. 시나리오 4를 고른 경우에만 **고정 계정** `k6-guide-<n>@recu.test`를 준비해요.

### 가이드와 OpenAI 비용

가이드 캐시는 (사용자, 글, profileVersion, promptVersion)이 같을 때만 맞아요. 그래서 가이드 시나리오는 프로필을 고치지 않는 고정 계정만 써요.

**비용 없는 실행은 시나리오 4를 빼는 것뿐이에요.** 기본 실행이 그 상태예요.

```bash
k6 run infra/k6/main.js
```

`GUIDE_WARMUP=false`로는 비용을 막을 수 없어요. 이 값은 setup의 가이드 생성만 끄고, 시나리오 4의 부하 단계에서는 VU마다 `POST /api/posts/{id}/guide`를 계속 보내요. 캐시가 없으면 그 요청이 OpenAI를 불러요.

#### 시나리오 4를 돌리기로 팀과 정한 경우

OpenAI 비용이 들어요. 아래 순서를 지켜요.

1. 처음 한 번만 캐시를 만들어요. 가이드 10개(`GUIDE_USERS × GUIDE_POSTS`)를 만들면서 OpenAI를 불러요. 부하는 거의 없게 짧게 돌려요.

   ```bash
   k6 run -e SCENARIOS=guide -e GUIDE_WARMUP=true -e WARMUP=1s -e STEADY=1s -e PEAK=1s -e COOLDOWN=1s infra/k6/main.js
   ```

2. 그다음 부하를 걸어요. 같은 계정·글이라 캐시가 남아 있으면 모두 캐시로 답해요.

   ```bash
   k6 run -e SCENARIOS=guide infra/k6/main.js
   ```

- 부하 단계에서 `cached=false`가 오면 테스트를 멈춰요. 다만 멈추기 전에 이미 보낸 요청(최대 동시 VU 수만큼)은 OpenAI를 부를 수 있어요. 그래서 이 장치는 비용을 줄이는 안전장치일 뿐, 비용을 0으로 만들지는 않아요.
- `promptVersion`이 바뀌거나 DB 볼륨을 지우면(`down -v`) 캐시가 다시 비어요. 1번부터 다시 해요.

## 오류율이 튈 때 먼저 볼 것

- **409가 섞였는지**: 계정은 VU 번호마다 따로 있어서 VU끼리 같은 계정을 쓰지 않아요. 그래도 409가 보이면 `*_PEAK_VUS`를 바꿨는지, 이전 실행에서 지우지 못한 북마크가 남았는지 확인해요.
- **502가 섞였는지**: Feign 타임아웃이에요. content·user 로그를 같이 봐요.

## 이벤트 적체 확인 (시나리오 2·3)

Kafka B가 얼마나 쌓였는지는 HTTP로 보이지 않아요. 부하를 거는 동안 다른 터미널에서 consumer lag을 봐요.

```bash
docker exec recu-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --all-groups
```

`recu.profile.updated`의 `LAG`이 부하 중에 계속 늘어나기만 하면 적체예요. 끝난 뒤 0으로 돌아오는지도 봐요. k6 요약의 `Kafka B 발행` 건수·초당 건수와 비교해요.

## 결과 읽기

마지막에 k6 기본 요약 다음으로 이런 표가 나와요.

```
══════ ReCu 부하 테스트 요약 ══════

시나리오                  요청 수   p95       오류율
1. 출근길 추천 탐색       …         …         …
2. 북마크 쓸어담기        …         …         …
…
북마크 쓰기 p95            …
중복 클릭 409 (정상)        …건
Kafka B 발행 (프로필 수정)  …건, …건/초
가이드 캐시 미스(OpenAI)    0건 (0이어야 함)
```

기준치를 넘으면 k6가 종료 코드 99로 끝나요.

## 파일

| 파일 | 내용 |
| --- | --- |
| `main.js` | 진입점. 시나리오 조합, setup, 결과 요약 |
| `scenarios/1-morning-browse.js` ~ `4-guide-revisit.js` | 시나리오별 요청 비율·기준치 |
| `results/` | `SUMMARY_JSON` 결과 저장 위치. `.gitkeep`만 커밋 |
| `lib/api.js` | Gateway 요청과 요청별 데이터 흐름 |
| `lib/config.js` | 포트, 흐름 표기, 단계, 기준치 |
| `lib/data.js` | 가입·로그인·프로필 준비, 글 번호 수집 |

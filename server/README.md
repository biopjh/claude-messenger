# Messenger (Spring Boot + MyBatis + PostgreSQL)

카카오톡 클론 메신저 — 회원/친구/1:1·그룹 채팅(WebSocket 실시간) / 파일·이미지 첨부 / 프로필 편집까지.

## 기술 스택

- Java 17 / Spring Boot 3.2 / Gradle
- MyBatis 3 + PostgreSQL 15+ (Flyway 9)
- Spring Security + JWT (JJWT 0.12)
- Spring WebSocket + STOMP (실시간 채팅)
- Thymeleaf + 일반 JS (SockJS, stomp.js)

## 기능

- 회원가입 / 로그인 / 토큰 리프레시
- 프로필 편집 (닉네임·상태메시지·프로필 이미지)
- 친구 검색·요청·수락·삭제
- 1:1 채팅방 (idempotent 생성)
- 그룹 채팅방 만들기·초대·나가기·시스템 메시지
- WebSocket 실시간 송수신 / 읽음 처리 / 안 읽은 수
- 파일·이미지 첨부 (드래그앤드롭, 라이트박스)
- 모든 화면에 동그란 아바타 (이미지 또는 이니셜 폴백)
- 메시지 답장 / 5분 이내 수정 / 소프트 삭제
- 이모지 리액션 (👍 ❤️ 😂 😮 😢 🔥)
- 메시지 본문 부분일치 검색 (pg_trgm)
- **다중 인스턴스 STOMP 스케일아웃 (Redis Pub/Sub)** — 두 인스턴스 뒤에 있는 사용자도 실시간 메시지 받음

---

## 1. 로컬 실행 (IntelliJ + 시스템 PostgreSQL)

### 사전 준비

1. **Java 17** 설치 (`java -version` 확인)
2. **PostgreSQL** 실행 중
   - 기본 가정: DB=`springboot`, User=`biopjh`, Password=`8919`
   - 위 값과 다르면 `src/main/resources/application.yml` 수정
3. **IntelliJ IDEA** — 본 폴더를 열면 Gradle 동기화가 자동 시작 (Wrapper도 자동 생성)

### 실행

IntelliJ: `MessengerApplication` 우클릭 → Run.

또는 커맨드라인:

```bash
brew install gradle
gradle wrapper
./gradlew bootRun
```

서버가 뜨면 http://localhost:8080 접속.

---

## 2. 로컬 실행 (Docker Compose, Postgres 포함)

시스템에 Postgres가 없거나 격리된 환경에서 돌리고 싶을 때.

```bash
# 빌드 + 기동 (앱 + Postgres + Redis)
docker compose up -d --build

# 로그 보기
docker compose logs -f app

# 정지
docker compose down
```

- 호스트 5433 포트로 Postgres가 노출됩니다 (시스템 Postgres 와 충돌 방지). DBeaver 같은 툴로 접속 가능.
- 업로드 파일은 `uploads` 도커 볼륨에 영속화.
- 환경변수는 `docker-compose.yml` 에서 관리합니다. JWT_SECRET 은 반드시 교체할 것.

---

## 3. Fly.io 배포

가장 작고 빠르게 외부에 띄우는 방법. 무료 한도가 줄었지만 Hobby 플랜으로 최소 비용 운영 가능.

### 사전 준비

1. [fly.io](https://fly.io) 가입 + 결제수단 등록
2. `flyctl` CLI 설치
   ```bash
   curl -L https://fly.io/install.sh | sh
   fly auth login
   ```

### 단계별 명령

```bash
# 0) 프로젝트 폴더에서

# 1) 앱 생성 (이름은 fly가 추천하는 대로 두거나 직접 지정)
fly launch --no-deploy --copy-config --name messenger-yourname

# 2) PostgreSQL 만들기 (Fly Managed Postgres)
fly postgres create
#   생성 끝나면 "Connection string" 출력됨. 그걸로 attach:
fly postgres attach <postgres-app-name>
#   → SPRING_DATASOURCE_URL/USERNAME/PASSWORD 가 자동으로 secrets 에 추가됨
#   (참고: attach 가 만든 ENV 이름은 DATABASE_URL 형식인 경우가 있음.
#    그 경우 fly secrets set 으로 SPRING_DATASOURCE_* 로 다시 매핑)

# 3) 업로드 파일을 위한 영속 볼륨
fly volumes create messenger_uploads --size 1 --region nrt

# 4) 비밀 값 등록 (32자 이상 무작위 문자열로)
fly secrets set JWT_SECRET="$(openssl rand -base64 48)"

# 5) 배포!
fly deploy

# 6) 열기
fly open
```

### Fly에서 디버깅

```bash
fly logs                         # 실시간 로그
fly ssh console                  # 컨테이너 안으로 들어가기
fly status                       # 인스턴스 상태
fly secrets list                 # 등록된 환경변수
```

### 업데이트 시

코드 수정 → `git push` → `fly deploy` 한 줄이면 끝.

---

## 4. Render 배포 (대안)

Fly가 부담스러우면 Render. PostgreSQL은 90일 무료 제공.

대략적인 흐름:

1. Render 대시보드 → New → **Web Service** → GitHub 저장소 연결
2. **Environment**: Docker. **Branch**: main. **Plan**: Free
3. 환경변수 추가 (Settings → Environment):
   - `SPRING_PROFILES_ACTIVE=prod`
   - `JWT_SECRET=...`
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://...` (Render Postgres URL)
   - `SPRING_DATASOURCE_USERNAME=...`
   - `SPRING_DATASOURCE_PASSWORD=...`
   - `FILE_UPLOAD_DIR=/var/data/uploads` (Render Disk 마운트 경로)
4. **Disks** 탭에서 1GB 디스크를 `/var/data` 에 마운트 (유료 플랜에서만 가능 — Free 에서는 업로드가 휘발성)

---

## 4-B. 다중 인스턴스 스케일아웃 (Redis Pub/Sub) 테스트

서버 한 인스턴스만으로는 STOMP 메시지가 그 인스턴스에 연결된 클라이언트에만 도달합니다.
**두 인스턴스를 띄우고 둘 다 같은 Redis 를 바라보게 하면**, 인스턴스 A 에서 보낸 메시지가
인스턴스 B 에 연결된 사용자에게도 실시간으로 전달됩니다.

### 1) Redis 띄우기

가장 쉬운 방법:
```bash
brew install redis && brew services start redis
# 또는
docker run -d -p 6379:6379 redis:7-alpine
```

확인:
```bash
redis-cli ping   # → PONG
```

### 2) Spring Boot 두 인스턴스 띄우기 (같은 DB / 같은 Redis 공유)

```bash
# 터미널 1 — 8080 포트
cd server
MESSENGER_REDIS_ENABLED=true ./gradlew bootRun

# 터미널 2 — 8081 포트
MESSENGER_REDIS_ENABLED=true SERVER_PORT=8081 ./gradlew bootRun
```

`[MessageBroker] instanceId=...` 로그가 두 줄, 서로 다른 ID 로 찍히면 OK.

### 3) 클라이언트 두 개를 서로 다른 인스턴스에 붙이기

```bash
# A 사용자 — 8080 인스턴스 사용
MESSENGER_SERVER_URL=http://localhost:8080 npm --prefix client run start:1

# B 사용자 — 8081 인스턴스 사용
MESSENGER_SERVER_URL=http://localhost:8081 npm --prefix client run start:2
```

A 가 B 에게 메시지를 보내면 흐름은:
```
A → 인스턴스(8080) → 로컬 broker (A 본인에게 echo)
                  → Redis Pub/Sub (messenger:broadcast 채널)
                                 → 인스턴스(8081) 가 수신
                                 → 인스턴스(8081) 의 로컬 broker
                                 → B 의 STOMP 세션
```

자기 자신이 발행한 메시지는 `instanceId` 비교로 echo 차단되어 중복 전달되지 않습니다.

### 4) docker-compose 로 두 인스턴스 띄우기

`server/docker-compose.yml` 에 `app2` 서비스 블록이 주석 처리되어 있습니다.
주석을 풀고 `docker compose up -d --build` 하면 8080 (app) + 8081 (app2) 동시 실행 + Redis 공유.

### 한계

- HTTP/REST 호출은 인스턴스 간 sticky session 이 필요 (또는 stateless 면 무관). 우리는 stateless JWT 라 무관.
- WebSocket 핸드셰이크 자체는 한 인스턴스에 고정 — 한 사용자의 STOMP 세션은 한 인스턴스에 머무름. 그 사용자가 보낸 메시지가 같은 방의 다른 인스턴스 사용자에게도 가도록 하는 게 Redis Pub/Sub 의 역할.
- Pub/Sub 은 메시지 영속성이 없음(브로커 재시작 시 미전달 메시지 유실). 하지만 우리 메시지는 Postgres 에 영속화되므로 클라이언트가 재접속하면 history 로 복구 가능.

---

## 5. 운영 환경 변수 한눈에 보기

| 변수 | 필수 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | 운영에서는 `prod` |
| `SPRING_DATASOURCE_URL` | ✅ | `jdbc:postgresql://host:port/db` |
| `SPRING_DATASOURCE_USERNAME` | ✅ | DB user |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | DB password |
| `JWT_SECRET` | ✅ | **32자 이상**의 랜덤 비밀 키 |
| `JWT_ACCESS_TTL` | ❌ | 액세스 토큰 만료(초). 기본 3600 |
| `JWT_REFRESH_TTL` | ❌ | 리프레시 토큰 만료(초). 기본 1209600(14일) |
| `FILE_UPLOAD_DIR` | ❌ | 업로드 저장 경로. 기본 `/app/var/uploads` |
| `SERVER_PORT` | ❌ | 기본 8080 |
| `DB_POOL_MAX` | ❌ | HikariCP 최대 풀 크기. 기본 20 |
| `MESSENGER_REDIS_ENABLED` | ❌ | Redis Pub/Sub 사용 여부. dev 기본 false, prod 기본 true |
| `SPRING_DATA_REDIS_HOST` | prod ✅ | Redis 호스트. 기본 localhost |
| `SPRING_DATA_REDIS_PORT` | ❌ | 기본 6379 |

---

## 6. 디렉터리 구조

```
src/main/java/com/example/messenger/
├─ MessengerApplication.java
├─ config/{SecurityConfig, WebSocketConfig}.java
├─ common/{exception, response}/
├─ auth/{controller, service, jwt, dto}/
├─ user/{controller, service, mapper, domain, dto}/
├─ friend/{controller, service, mapper, domain, dto}/
├─ chatroom/{controller, service, mapper, domain, dto}/
├─ message/{controller(REST + STOMP), service, mapper, domain, dto}/
├─ file/{controller, service, dto}/
└─ web/PageController.java

src/main/resources/
├─ application.yml              # 로컬 dev 기본값
├─ application-prod.yml         # SPRING_PROFILES_ACTIVE=prod 일 때 덮어쓰기
├─ db/migration/V1__init.sql    # 모든 테이블 한 번에
├─ mapper/{user,friend,chatroom,message}/*.xml
├─ static/{css, js/avatar.js, js/chat-list.js, js/chat-room.js, js/auth.js}
└─ templates/{login,signup,home,chat-list,chat-room}.html

Dockerfile
docker-compose.yml
fly.toml
```

---

## 7. 동작 확인 시나리오

1. http://localhost:8080/signup 에서 두 계정 가입 (예: A=`a@a.com`, B=`b@b.com`)
2. A 로그인 → 검색창에 `b@` 입력 → "친구 요청"
3. 시크릿창에서 B 로그인 → "받은 친구 요청"에서 수락
4. 친구 목록의 B 옆 "1:1 채팅" 클릭 → 채팅방 진입
5. 메시지 송신 / 📎로 사진 첨부 / 드래그앤드롭으로 파일 첨부
6. 다시 채팅 목록으로 돌아가 "＋ 그룹채팅 만들기" → 멤버 선택 → 만들기
7. 그룹방에서 ＋(초대) / ⎋(나가기) / 👥(멤버 패널) 동작 확인
8. 좌상단 "내 정보" → 프로필 이미지 변경 → 저장 → 다시 채팅창에 들어가면 본인 아바타 갱신

---

## 자주 만나는 문제

- **Flyway: relation "users" already exists** — 기존 DB에 같은 테이블 충돌. 빈 DB로 옮기거나 `baseline-on-migrate`(이미 켜둠)에 맡기되, 같은 이름 테이블이 미리 있으면 여전히 충돌.
- **401 Unauthorized** — Authorization 헤더 누락/만료. 다시 로그인.
- **`password authentication failed`** — `application.yml`의 username/password 또는 PostgreSQL `pg_hba.conf` 확인.
- **JWT secret 길이 오류** — 32자 이상이어야 함. 운영은 `openssl rand -base64 48` 권장.
- **WebSocket 401 즉시 끊김** — STOMP CONNECT 헤더에 `Authorization: Bearer ...` 누락. `chat-room.js` 의 `stomp.connect({ Authorization: 'Bearer '+token }, ...)` 확인.
- **Fly.io 헬스체크 실패** — 첫 부팅 시 Flyway가 마이그레이션 돌리는 동안 헬스체크가 timeout 될 수 있음. `fly.toml` 의 `grace_period`를 60s로 늘리거나, Postgres 가 미리 떠 있는지 확인.
- **Fly 자동 슬립과 WebSocket** — `fly.toml`의 `auto_stop_machines = true` + `min_machines_running = 0` 설정은 트래픽이 없으면 머신을 잠재웁니다. 잠든 동안 STOMP 세션이 끊깁니다(클라가 재요청을 보내면 콜드 스타트로 깨어남, 약 20~30초). 항상 깨어 있어야 한다면 `min_machines_running = 1` 로 변경하세요.

자세한 설계는 동봉된 `카카오톡-클론-메신저-개발가이드.docx`의 6~17장을 참고하세요.

# Messenger Desktop Client

Electron 기반 PC 데스크톱 클라이언트. 같은 머신에 떠 있는 서버를 윈도우에 띄우고,
시스템 트레이·OS 네이티브 알림·토큰 안전 저장 등 데스크톱 특화 기능까지 갖춥니다.
electron-builder 로 macOS/Windows/Linux 설치파일을 한 번에 만들 수 있습니다.

## 사전 준비

1. Node.js 18 이상 설치 (`node -v` 확인)
2. 서버를 먼저 띄워두기

```bash
# 다른 터미널에서
cd ../server
./gradlew bootRun
```

## 첫 실행

```bash
cd client
npm install        # 최초 1회 — electron 다운로드
npm start          # Electron 윈도우가 뜨고 localhost:8080 을 로드
```

서버가 외부(예: Fly.io)에 떠 있다면:

```bash
MESSENGER_SERVER_URL=https://my-messenger.fly.dev npm start
```

## 개발 편의

```bash
npm run dev        # DevTools 자동 분리(detach) 모드로 실행
npm run lint:syntax  # main/preload.js 의 Node 문법 빠른 점검
```

## 구조

```
client/
├── package.json
├── src/
│   ├── main/
│   │   ├── index.js     # Electron main process: 윈도우, 메뉴, 외부 링크 라우팅
│   │   └── preload.js   # window.messengerNative 화이트리스트 API
│   └── renderer/        # (다음 세션에서 채움)
└── assets/              # 아이콘 등 (다음 세션)
```

## 동작 원리 (지금 단계)

```
┌──────────────────┐     loadURL("http://localhost:8080")    ┌──────────────┐
│  Electron Window │ ───────────────────────────────────────▶│ Spring Boot  │
│  (Chromium)      │  ◀── HTML/CSS/JS, REST, WebSocket ──── │ (server/)    │
└──────────────────┘                                          └──────────────┘
```

서버가 이미 모든 화면(login/signup/chat-list/chat-room/me)을 Thymeleaf 로 서빙하므로,
데스크톱 앱은 그 페이지를 그대로 보여줄 뿐입니다. 따라서 채팅·실시간·파일첨부 등
모든 기능이 그대로 동작합니다.

## 데스크톱 특화 기능 (현재 단계까지 포함된 것)

- **시스템 트레이** — 트레이 아이콘 클릭으로 창 토글, 우클릭 메뉴(열기/서버 URL 변경/종료).
- **창 닫기 → 백그라운드 유지** — X 버튼은 트레이로 숨김. 트레이 메뉴 → 종료로만 실제 quit.
- **OS 네이티브 알림** — 새 메시지 도착 시. 창이 포커스 되어 있을 때는 알림 없음(이미 보고 있는 거니까).
- **토큰 안전 저장** — `safeStorage` 로 OS Keychain(macOS), Credential Vault(Windows), libsecret(Linux)에 암호화 저장.
- **서버 URL 설정 화면** — 첫 실행 또는 메뉴 → 설정에서 변경. `app.getPath('userData')/config.json` 에 영속화.
- **작업표시줄 unread 배지** — macOS 도크 / Linux Unity 에서 카운트. 모든 OS에서 윈도우 타이틀에 `(N)` 표시.
- **단일 인스턴스** — 두 번 실행하면 첫 인스턴스 창이 떠오름.
- **외부 링크 라우팅** — 서버 외 도메인은 OS 기본 브라우저로 자동 열기.

## 환경변수

| 변수 | 설명 |
|---|---|
| `MESSENGER_SERVER_URL` | 설정 파일을 무시하고 강제로 사용할 서버 URL |
| `MESSENGER_DEVTOOLS=1` | DevTools 자동 분리 모드로 실행 |
| `MESSENGER_PROFILE=name` | 다중 인스턴스 모드 — 별도 userData / 단일 락 우회 |

## 빌드 / 설치파일 만들기

설정은 `../installer/electron-builder.yml`, 산출물은 `../installer/dist/` 에 떨어집니다.

```bash
npm run pack          # 패키지만 (.app 디렉터리 — 설치파일 없이 빠른 검증)
npm run dist          # 현재 OS용 설치파일 만들기
npm run dist:mac      # ../installer/dist/Messenger-0.1.0-{arm64,x64}.dmg
npm run dist:win      # ../installer/dist/Messenger-0.1.0-Setup.exe (NSIS)
npm run dist:linux    # ../installer/dist/Messenger-0.1.0.AppImage
```

자세한 사항·코드 사이닝·크로스 플랫폼 빌드 한계는 `../installer/README.md` 참고.

## 다중 인스턴스 (테스트용)

같은 머신에서 두 명을 동시에 띄우려면 — `--profile` 플래그가 userData 격리 + 단일 인스턴스 락 우회를 한 번에 처리합니다.

```bash
# 터미널 1
npm run start:1
# 터미널 2 (다른 창)
npm run start:2
```

각 윈도우 타이틀에 `(user1)`, `(user2)` 가 붙고, 서로 다른 계정으로 로그인 가능.

## 자동 업데이트 (electron-updater + GitHub Releases)

설치된 사용자가 매번 수동으로 새 버전을 받지 않아도, 백그라운드에서 새 버전을 감지하고
다운로드한 뒤 다음 종료 시 자동 설치됩니다. **dev 모드에서는 동작하지 않고**, 패키징된
설치본(`.dmg`/`.exe`/`.AppImage`)에서만 작동합니다.

### 동작 방식

1. 앱 시작 30초 후, 그리고 6시간마다 `latest.yml`/`latest-mac.yml`/`latest-linux.yml` 을
   GitHub Releases 에서 확인.
2. 새 버전이 있으면 자동 다운로드 (백그라운드).
3. 다운로드 완료되면 "지금 재시작하고 설치 / 나중에" 다이얼로그 표시.
4. "나중에" 선택 → 다음 종료 시 자동 설치.

메뉴 → 도움말 → **업데이트 확인…** 에서 강제로 즉시 체크 가능.

### 한 번만 해두는 사전 작업

1. GitHub 저장소에 코드 push (`origin` 이 있어야 함)
2. `installer/electron-builder.yml` 의 `publish.owner` / `publish.repo` 를 본인 정보로 수정

### 새 버전 릴리스하기 (한 줄 명령으로 시작)

```bash
# 1) client/package.json 의 version 을 0.2.0 등으로 올림
npm --prefix client version 0.2.0 --no-git-tag-version
git add client/package.json
git commit -m "chore: bump version to 0.2.0"

# 2) 태그 푸시 — GitHub Actions 가 트리거됨
git tag v0.2.0
git push origin main --tags
```

3 분 정도면 GitHub Actions 가 macOS/Windows/Linux 세 OS 인스톨러 + `latest*.yml` 을
**Releases v0.2.0** 으로 자동 업로드. 이미 설치된 모든 사용자는 다음 자동 체크 때 업데이트
다이얼로그를 보게 됩니다.

### 로컬에서 수동 발행 (Action 안 쓸 때)

```bash
# 본인 GitHub Personal Access Token (repo write 권한 필요) 을 환경변수로
export GH_TOKEN=ghp_xxxxxxxxxxxx
cd client
npm run release            # 현재 OS만. mac/win/linux 강제는 -- --mac 등으로
```

### 코드 사이닝 (운영 시)

자동 업데이트는 macOS 에서 코드 사이닝이 없으면 일부 단계가 스킵될 수 있습니다.
GitHub Actions secrets 에 다음을 등록하면 자동으로 사인/공증됩니다:

- `CSC_LINK` (.p12 인증서 파일을 base64 로 인코딩한 값)
- `CSC_KEY_PASSWORD`
- macOS 공증(notarization) 추가: `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`

학습/내부 배포는 사인 없이도 동작합니다.

### 디버깅

- 자동 업데이트 로그는 `electron-log` 가 다음 위치에 남깁니다:
  - macOS: `~/Library/Logs/Messenger/main.log`
  - Windows: `%USERPROFILE%\AppData\Roaming\Messenger\logs\main.log`
  - Linux: `~/.config/Messenger/logs/main.log`
- 무엇이 잡혔는지 `[updater]` 로 시작하는 줄을 보면 됨.

## 다음 세션 (선택)

- 메시지 수정·삭제·인용 기능
- 메시지 풀텍스트 검색
- macOS 트레이 아이콘 template image (다크/라이트 자동 적응)
- 보안 강화 (HttpOnly 쿠키, CSRF, rate limiting)

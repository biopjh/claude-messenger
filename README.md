# Claude Messenger

카카오톡 클론 메신저 학습 프로젝트. 한 저장소 안에 **세 부분**으로 구성됩니다.

```
claude-messenger/
├── server/      Spring Boot + MyBatis + PostgreSQL  (REST + WebSocket)
├── client/      Electron 기반 PC 데스크톱 클라이언트
├── installer/   설치파일 빌드 자료 (electron-builder)
└── docs/        설계 문서 (개발 가이드 docx)
```

## 빠른 시작 (한 머신에서 풀스택 띄우기)

**1) 서버 띄우기**
```bash
cd server
./gradlew bootRun
# → http://localhost:8080 (PostgreSQL 가 이미 실행 중이어야 함)
```

또는 Postgres 까지 통째로 도커로:
```bash
cd server
docker compose up -d --build
```

**2) 클라이언트 띄우기 (다른 터미널)**
```bash
cd client
npm install     # 최초 1회
npm start       # Electron 윈도우가 뜨면서 localhost:8080 을 로드
```

웹 브라우저에서 그대로 쓰던 것과 동일하게 회원가입 → 로그인 → 채팅이 동작합니다.

## 각 폴더 안내

- **server/** — 본 프로젝트의 핵심. Spring Boot 백엔드 + Thymeleaf 프론트.
  자세한 실행 / Docker / Fly.io 배포는 `server/README.md`.
- **client/** — Electron 데스크톱 앱. 현재(세션 1) 단계는 서버 URL 을 윈도우에 그대로
  띄우는 thin client. 자세한 사항은 `client/README.md`.
- **installer/** — 다음 세션에서 .exe / .dmg / .AppImage 설치파일을 만들 자리.
- **docs/** — 메신저 전체 설계와 단계별 학습 가이드 (DOCX).

## 학습 단계 요약

| 단계 | 결과물 |
|---|---|
| 1 | 회원가입·로그인·JWT 인증 |
| 2 | 친구·1:1 채팅·WebSocket 실시간 |
| 3 | 그룹채팅·시스템 메시지·읽음 처리 |
| 4 | 파일·이미지 첨부 (드래그앤드롭, 라이트박스) |
| 5 | 프로필 편집·아바타 |
| 6 | Docker / 운영 프로파일 / Fly.io 배포 |
| 7 (현재) | **PC 클라이언트 스캐폴딩 (Electron)** |
| 8 (예정) | 데스크톱 특화 기능 (트레이·알림·안전 토큰 저장) |
| 9 (예정) | 설치파일 빌드 (electron-builder, NSIS/DMG/AppImage) |
| 10 (선택) | 자동 업데이트 (electron-updater + GitHub Releases) |

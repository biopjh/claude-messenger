# Installer

데스크톱 클라이언트의 설치파일을 만드는 자리. 빌드 자체는 `client/`에서 npm 스크립트로
실행하고, 산출물이 여기 `dist/` 로 떨어집니다.

## 디렉터리

```
installer/
├── electron-builder.yml      ← 모든 OS 공통 설정 (macOS/Windows/Linux 한 파일)
├── build/                    ← 빌드에 들어갈 자원
│   ├── icon.png              ← 1024×1024 마스터 아이콘 (macOS .icns 자동 생성용)
│   └── icon.ico              ← Windows 멀티 사이즈 아이콘 (16/24/32/48/64/128/256)
└── dist/                     ← 산출물 (.dmg / .exe / .AppImage). git 제외
```

## 빌드

`client/` 폴더에서:

```bash
cd ../client
npm install                 # 처음 1회 — electron + electron-builder 다운로드

# 1) 패키지만 (설치파일 없이 .app 디렉터리 — 빠른 검증용)
npm run pack

# 2) 현재 OS용 설치파일
npm run dist                # mac에서 실행하면 .dmg, win에서 실행하면 .exe ...

# 3) 특정 OS 강제
npm run dist:mac            # → ../installer/dist/Messenger-0.1.0-arm64.dmg (또는 x64)
npm run dist:win            # → ../installer/dist/Messenger-0.1.0-Setup.exe
npm run dist:linux          # → ../installer/dist/Messenger-0.1.0.AppImage
```

산출물:
- macOS: `Messenger-0.1.0-arm64.dmg`, `Messenger-0.1.0-x64.dmg` (Apple Silicon + Intel)
- Windows: `Messenger-0.1.0-Setup.exe` (NSIS 인스톨러, 사용자 위치/단축키 선택 가능)
- Linux: `Messenger-0.1.0.AppImage` (실행권한 부여 후 더블클릭으로 실행)

## 크로스 플랫폼 빌드 한계

- macOS 위에서 `dist:win`, `dist:linux` 도 동작합니다 (electron-builder가 prebuilt
  바이너리를 내려받음).
- Windows 코드 사이닝(EV/OV 인증서)이나 macOS 공증(notarization)은 해당 OS + 인증서가
  필요합니다.
- 가장 깔끔한 방식은 GitHub Actions 매트릭스(macOS / windows-latest / ubuntu-latest)에서
  각 OS 빌드를 분담하고 산출물을 GitHub Releases 로 모으는 것입니다 — 다음 세션에
  추가 예정.

## 코드 사이닝 메모

학습/내부 배포는 사이닝 없이도 동작합니다. 단:

- **macOS**: 첫 실행 시 "확인되지 않은 개발자" 경고 → 우클릭 → 열기 한 번이면 이후 OK.
- **Windows**: SmartScreen 경고 → "추가 정보" → "실행" 클릭하면 통과.
- **운영 배포**시에는:
  - macOS: Apple Developer($99/년) → `identity` 채우고 `notarize` 활성화
  - Windows: EV/OV Code Signing 인증서 → `signtoolOptions` 설정

자세한 설정은 `electron-builder.yml` 의 주석 참고.

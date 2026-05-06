// electron-builder afterPack 훅.
// .app 디렉터리가 만들어진 직후 (DMG 로 패키징되기 전) 호출된다.
//
// 목적: macOS Apple Silicon 에서 코드 사이닝이 전혀 없는 바이너리는
// "손상되었기 때문에 열 수 없습니다" 라는 거짓 에러를 띄운다(macOS 11+ 정책).
// `codesign --sign -` 로 ad-hoc 서명을 한 번만 박아주면 이 거짓 에러가 사라지고
// 표준 "확인되지 않은 개발자" 경고로 떨어진다 (우클릭 → 열기로 통과 가능).
//
// 실제 Apple Developer ID 가 있을 때는 electron-builder 가 자동 사인하므로
// 이 훅은 그 경우 아무 일도 안 한다 (CSC_LINK 가 있으면 스킵).

const { execFileSync } = require('node:child_process');
const path = require('node:path');

exports.default = async function afterPack(context) {
  const { electronPlatformName, appOutDir, packager } = context;

  // macOS 빌드에서만 동작
  if (electronPlatformName !== 'darwin') return;

  // 진짜 사이닝이 활성화되어 있으면 스킵
  if (process.env.CSC_LINK || process.env.CSC_IDENTITY_AUTO_DISCOVERY === 'true') {
    console.log('[afterPack] real code signing detected — skip ad-hoc sign');
    return;
  }

  const appName = packager.appInfo.productFilename + '.app';
  const appPath = path.join(appOutDir, appName);

  console.log(`[afterPack] ad-hoc signing → ${appPath}`);
  try {
    execFileSync('codesign', [
      '--force',
      '--deep',
      '--sign', '-',                // "-" 가 ad-hoc 서명
      '--options', 'runtime',
      appPath,
    ], { stdio: 'inherit' });
    console.log('[afterPack] ad-hoc sign OK');
  } catch (e) {
    console.error('[afterPack] ad-hoc sign FAILED:', e.message);
    // 실패해도 빌드 자체는 계속 — 사용자가 xattr 로 우회 가능
  }
};

// electron-builder afterPack 훅 — macOS ad-hoc 서명.
//
// 단순 `codesign --deep --sign -` 는 Electron Framework 같은 중첩 번들에 새 ad-hoc
// Team ID 를 제대로 전파하지 못해서, 실행 시 dyld 가 "different Team IDs" 로 로드 거부함.
// 따라서 안쪽(deepest)부터 바깥쪽으로 하나씩 명시적으로 서명한다.
//
// 진짜 Apple Developer ID 가 있을 때는 electron-builder 가 이미 사인하므로 이 훅은
// 아무 일도 안 한다 (CSC_LINK 가 set 되어 있을 때 스킵).

const { execFileSync } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');

exports.default = async function afterPack(context) {
  const { electronPlatformName, appOutDir, packager } = context;
  if (electronPlatformName !== 'darwin') return;
  if (process.env.CSC_LINK || process.env.CSC_IDENTITY_AUTO_DISCOVERY === 'true') {
    console.log('[afterPack] real code signing detected — skip ad-hoc');
    return;
  }

  const appName = packager.appInfo.productFilename + '.app';
  const appPath = path.join(appOutDir, appName);
  console.log(`[afterPack] ad-hoc signing (deepest-first) → ${appPath}`);

  // 1) appPath 안에서 서명 대상이 될 모든 항목을 모은다 (재귀).
  //    - *.framework  (디렉터리 번들)
  //    - *.app        (Helper 앱 등 디렉터리 번들. 단, 최상위 main app 은 제외)
  //    - *.dylib      (파일)
  const targets = [];
  (function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        const isBundle = entry.name.endsWith('.framework') || entry.name.endsWith('.app');
        // 번들 내부도 더 들어가서 중첩 framework / 헬퍼 / dylib 까지 수집
        walk(p);
        if (isBundle) targets.push(p);
      } else if (entry.isFile() && entry.name.endsWith('.dylib')) {
        targets.push(p);
      }
    }
  })(appPath);

  // 2) 깊은 경로일수록 안쪽에 있는 것 — 경로 길이 내림차순 정렬로 deepest-first 보장
  targets.sort((a, b) => b.length - a.length);

  // 3) 각 항목 ad-hoc 서명
  let ok = 0, fail = 0;
  for (const t of targets) {
    try {
      execFileSync('codesign', [
        '--force',
        '--sign', '-',                  // ad-hoc
        '--timestamp=none',
        t,
      ], { stdio: 'pipe' });
      ok++;
    } catch (e) {
      fail++;
      console.error(`  ✗ ${path.relative(appPath, t)}: ${e.message.split('\n')[0]}`);
    }
  }
  console.log(`  signed ${ok} nested items (${fail} failed)`);

  // 4) 마지막으로 main .app 서명
  try {
    execFileSync('codesign', [
      '--force',
      '--sign', '-',
      '--timestamp=none',
      appPath,
    ], { stdio: 'inherit' });
    console.log(`[afterPack] ad-hoc sign of main app: OK`);
  } catch (e) {
    console.error('[afterPack] main app sign FAILED:', e.message);
  }

  // 5) 검증 — 실패한 게 있으면 로그에 남기고 빌드는 계속
  try {
    execFileSync('codesign', [
      '--verify',
      '--verbose=2',
      '--deep',
      appPath,
    ], { stdio: 'inherit' });
    console.log('[afterPack] codesign --verify: OK');
  } catch (e) {
    console.warn('[afterPack] codesign --verify reported issues — DMG 는 만들어지지만 실행 시 문제 가능');
  }
};

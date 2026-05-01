// Electron main 프로세스.
//   - BrowserWindow / Tray / Menu
//   - 설정파일 (config.json) — serverUrl 영속화
//   - 토큰 안전 저장 (safeStorage 로 OS Keychain 암호화 + 디스크에 보관)
//   - 자동 업데이트 (electron-updater + GitHub Releases)
//   - IPC 핸들러:
//        token:get/save/clear     (auth.js 가 사용)
//        notify:show              (새 메시지 OS 네이티브 알림)
//        badge:set                (작업표시줄/도크 unread 배지)
//        setup:save-url/get-url   (첫 실행 또는 메뉴에서 서버 URL 변경)

const {
  app, BrowserWindow, Menu, Tray, ipcMain, shell,
  Notification, nativeImage, safeStorage, dialog,
} = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const log = require('electron-log/main');
const { autoUpdater } = require('electron-updater');

// 파일 로그 (운영 시 ~/Library/Logs/Messenger/main.log 등에 기록 — 업데이트 디버깅에 필수)
log.transports.file.level = 'info';
log.transports.console.level = 'debug';
autoUpdater.logger = log;
autoUpdater.autoDownload = true;
autoUpdater.autoInstallOnAppQuit = true;

// ─────────────────────────────── 상수 ───────────────────────────────

const ICON_PATH = path.join(__dirname, '..', '..', 'assets', 'tray.png');
const SETUP_HTML = path.join(__dirname, '..', 'renderer', 'setup.html');
const userDataDir = () => app.getPath('userData');
const CONFIG_FILE = () => path.join(userDataDir(), 'config.json');
const TOKEN_FILE = () => path.join(userDataDir(), 'tokens.dat');

const WANTS_DEVTOOLS =
  process.argv.includes('--devtools') || process.env.MESSENGER_DEVTOOLS === '1';

// 다중 인스턴스 테스트용 프로파일 분기.
//   electron . --profile=user1
//   MESSENGER_PROFILE=user1 electron .
// 프로파일을 지정하면 (1) userData 디렉터리를 분리해 토큰/설정을 격리하고
// (2) 단일 인스턴스 락을 건너뛴다. 같은 머신에서 두 사용자를 동시에 띄울 때 사용.
const profileArg = process.argv.find(a => a.startsWith('--profile='));
const PROFILE = (profileArg ? profileArg.split('=')[1] : '') || process.env.MESSENGER_PROFILE || '';
if (PROFILE) {
  const base = app.getPath('userData');
  app.setPath('userData', base + '-' + PROFILE);
}
const TITLE_BASE = PROFILE ? `Messenger (${PROFILE})` : 'Messenger';

// Windows 토스트 알림은 AppUserModelID 가 설정되어야 동작한다.
// 패키징 후에는 electron-builder 가 appId 를 기반으로 자동 설정하지만,
// dev 모드(`npm start`)에서는 우리가 직접 같은 값을 박아준다.
// (electron-builder.yml 의 appId 와 반드시 일치할 것)
if (process.platform === 'win32') {
  app.setAppUserModelId('com.example.messenger');
}

// ─────────────────────────────── 상태 ───────────────────────────────

/** @type {BrowserWindow | null} */
let mainWindow = null;
/** @type {Tray | null} */
let tray = null;
let isQuitting = false;

let serverUrl = null;             // 현재 사용 중인 서버 URL (env > config 파일)
let cachedTokens = { access: null, refresh: null };

// ─────────────────────── 설정 파일 (serverUrl) ──────────────────────

function loadConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_FILE(), 'utf8'));
  } catch {
    return {};
  }
}
function saveConfig(c) {
  try {
    fs.mkdirSync(path.dirname(CONFIG_FILE()), { recursive: true });
    fs.writeFileSync(CONFIG_FILE(), JSON.stringify(c, null, 2));
  } catch (e) {
    console.error('saveConfig failed', e);
  }
}

// ─────────────────────── 토큰 안전 저장 ─────────────────────────────

function loadTokensFromDisk() {
  try {
    if (!fs.existsSync(TOKEN_FILE())) return { access: null, refresh: null };
    if (!safeStorage.isEncryptionAvailable()) {
      console.warn('safeStorage unavailable — tokens not loaded');
      return { access: null, refresh: null };
    }
    const enc = fs.readFileSync(TOKEN_FILE());
    const json = safeStorage.decryptString(enc);
    const obj = JSON.parse(json);
    return { access: obj.access || null, refresh: obj.refresh || null };
  } catch (e) {
    console.error('loadTokensFromDisk failed', e);
    return { access: null, refresh: null };
  }
}

function saveTokensToDisk(tokens) {
  try {
    if (!safeStorage.isEncryptionAvailable()) {
      console.warn('safeStorage unavailable — tokens kept in memory only');
      return;
    }
    fs.mkdirSync(path.dirname(TOKEN_FILE()), { recursive: true });
    const enc = safeStorage.encryptString(JSON.stringify(tokens));
    fs.writeFileSync(TOKEN_FILE(), enc);
  } catch (e) {
    console.error('saveTokensToDisk failed', e);
  }
}

function clearTokensFromDisk() {
  try { fs.existsSync(TOKEN_FILE()) && fs.unlinkSync(TOKEN_FILE()); }
  catch (e) { console.error('clearTokensFromDisk failed', e); }
}

// ───────────────────────────── 트레이 ──────────────────────────────

function createTray() {
  if (tray) return;
  const icon = nativeImage.createFromPath(ICON_PATH);
  // macOS 에서는 메뉴바 아이콘 — 추후 template image 로 바꾸면 다크/라이트 자동 적응
  tray = new Tray(icon.isEmpty() ? nativeImage.createEmpty() : icon);
  tray.setToolTip('Messenger');

  const ctx = Menu.buildFromTemplate([
    { label: '열기',           click: () => showMainWindow() },
    { label: '서버 URL 변경…', click: () => promptChangeServerUrl() },
    { type: 'separator' },
    { label: '종료', click: () => { isQuitting = true; app.quit(); } },
  ]);
  tray.setContextMenu(ctx);
  tray.on('click', () => showMainWindow());
}

function showMainWindow() {
  if (!mainWindow) {
    createMainWindow();
    return;
  }
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

// ─────────────────────────── 메인 윈도우 ─────────────────────────────

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1024,
    height: 720,
    minWidth: 380,
    minHeight: 480,
    title: TITLE_BASE,
    autoHideMenuBar: true,
    backgroundColor: '#f3f4f6',
    icon: ICON_PATH,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  // 외부 도메인 링크는 OS 기본 브라우저로
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (serverUrl && url.startsWith(serverUrl)) return { action: 'allow' };
    shell.openExternal(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (serverUrl && !url.startsWith(serverUrl)) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  // 알림 권한은 자동 승인 (자기 앱이므로)
  mainWindow.webContents.session.setPermissionRequestHandler((wc, perm, cb) => {
    cb(perm === 'notifications' || perm === 'media');
  });

  // 창 닫기 → 종료가 아니라 트레이로 숨김 (실제 종료는 메뉴/단축키)
  mainWindow.on('close', (e) => {
    if (!isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
  mainWindow.on('closed', () => { mainWindow = null; });

  if (WANTS_DEVTOOLS) {
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  }

  loadServerOrSetup();
}

function loadServerOrSetup() {
  if (!mainWindow) return;
  if (serverUrl) {
    mainWindow.loadURL(serverUrl).catch((err) => {
      console.error('loadURL failed', serverUrl, err);
      mainWindow && mainWindow.loadFile(SETUP_HTML, { query: { error: 'unreachable', url: serverUrl } });
    });
  } else {
    mainWindow.loadFile(SETUP_HTML).catch((e) => console.error('loadFile setup failed', e));
  }
}

async function promptChangeServerUrl() {
  // 메뉴/트레이에서 호출. setup 페이지로 이동
  if (!mainWindow) createMainWindow();
  else mainWindow.loadFile(SETUP_HTML, { query: { current: serverUrl || '' } });
  showMainWindow();
}

// ───────────────────────── IPC 핸들러 ───────────────────────────────

function registerIpc() {
  // ── 토큰
  ipcMain.handle('token:get',   async () => ({ ...cachedTokens }));
  ipcMain.handle('token:save',  async (_e, t) => {
    cachedTokens = { access: t?.access || null, refresh: t?.refresh || null };
    saveTokensToDisk(cachedTokens);
    return true;
  });
  ipcMain.handle('token:clear', async () => {
    cachedTokens = { access: null, refresh: null };
    clearTokensFromDisk();
    return true;
  });

  // ── 알림
  ipcMain.handle('notify:show', async (_e, { title, body }) => {
    const supported = Notification.isSupported();
    console.log('[notify:show]', { title, body, supported });
    if (!supported) return false;
    const n = new Notification({
      title: String(title || 'Messenger'),
      body:  String(body  || ''),
      icon:  ICON_PATH,
      silent: false,
    });
    n.on('show',   () => console.log('[notify:show] displayed'));
    n.on('failed', (e, err) => console.error('[notify:show] failed:', err));
    n.on('click',  () => showMainWindow());
    n.show();
    return true;
  });

  // ── 배지 (작업표시줄/도크 unread 카운트)
  ipcMain.handle('badge:set', async (_e, count) => {
    const n = Math.max(0, Math.floor(Number(count) || 0));
    // macOS 도크, Linux Unity launcher 에서 동작. Windows 에서는 무시됨.
    try { app.setBadgeCount(n); } catch (_) {}
    // 모든 OS 공통 — 윈도우 타이틀에 (N) 붙이기. 프로파일 모드면 그것도 같이 표시.
    if (mainWindow) {
      mainWindow.setTitle(n > 0 ? `(${n}) ${TITLE_BASE}` : TITLE_BASE);
    }
    return true;
  });

  // ── 설정 (서버 URL)
  ipcMain.handle('setup:get-url', async () => serverUrl || '');
  ipcMain.handle('setup:save-url', async (_e, url) => {
    const trimmed = String(url || '').trim().replace(/\/+$/, '');
    if (!/^https?:\/\//i.test(trimmed)) {
      throw new Error('http:// 또는 https:// 로 시작하는 URL을 입력하세요.');
    }
    serverUrl = trimmed;
    saveConfig({ serverUrl });
    loadServerOrSetup();
    return serverUrl;
  });

  ipcMain.handle('window:show', async () => { showMainWindow(); return true; });
}

// ──────────────────────── 메뉴바 (단축키용) ──────────────────────────

function buildAppMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    ...(isMac
      ? [{
          label: app.name,
          submenu: [
            { role: 'about' },
            { type: 'separator' },
            { label: '서버 URL 변경…', click: promptChangeServerUrl },
            { type: 'separator' },
            { role: 'hide' }, { role: 'hideOthers' }, { role: 'unhide' },
            { type: 'separator' },
            { label: '종료', accelerator: 'Cmd+Q',
              click: () => { isQuitting = true; app.quit(); } },
          ],
        }]
      : []),
    {
      label: '편집',
      submenu: [
        { role: 'undo' }, { role: 'redo' }, { type: 'separator' },
        { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' },
      ],
    },
    {
      label: '보기',
      submenu: [
        { role: 'reload' }, { role: 'forceReload' },
        { role: 'toggleDevTools' }, { type: 'separator' },
        { role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' },
      ],
    },
    {
      label: '도움말',
      submenu: [
        { label: `버전 ${app.getVersion()}`, enabled: false },
        { label: '업데이트 확인…',
          click: () => checkForUpdates({ silent: false }),
        },
      ],
    },
    {
      label: '설정',
      submenu: [
        { label: '서버 URL 변경…', click: promptChangeServerUrl },
        { label: '테스트 알림 보내기',
          click: () => {
            if (!Notification.isSupported()) {
              dialog.showMessageBox(mainWindow || undefined, {
                type: 'warning',
                message: 'Notification.isSupported() === false',
                detail: '이 OS에서는 데스크톱 알림이 지원되지 않습니다.',
              });
              return;
            }
            const n = new Notification({
              title: TITLE_BASE + ' — 테스트 알림',
              body: '이 알림이 보인다면 권한 설정이 정상입니다.',
              icon: ICON_PATH,
            });
            n.on('show',   () => console.log('[notify test] displayed'));
            n.on('failed', (_e, err) => console.error('[notify test] failed:', err));
            n.show();
          },
        },
        { label: '저장된 로그인 지우기',
          click: async () => {
            const { response } = await dialog.showMessageBox(mainWindow || undefined, {
              type: 'warning',
              buttons: ['취소', '지우기'],
              defaultId: 0, cancelId: 0,
              message: '저장된 로그인 토큰을 지울까요?',
              detail: '다음 실행 시 다시 로그인해야 합니다.',
            });
            if (response === 1) {
              cachedTokens = { access: null, refresh: null };
              clearTokensFromDisk();
              if (mainWindow) mainWindow.webContents.reload();
            }
          },
        },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// ───────────────────────── 자동 업데이트 ─────────────────────────────
// electron-updater 가 publish 설정(electron-builder.yml → GitHub Releases)을 읽어
// 새 버전(`latest.yml`/`latest-mac.yml`/`latest-linux.yml`)을 주기적으로 확인한다.
// dev 모드(`npm start`)에서는 app.isPackaged === false 라 자동 체크는 스킵하지만,
// 메뉴 → "업데이트 확인" 으로 강제 호출은 가능 (실패해도 친절히 안내).

let updateCheckInterval = null;

function bindAutoUpdaterEvents() {
  autoUpdater.on('checking-for-update', () => log.info('[updater] checking…'));
  autoUpdater.on('update-not-available', (info) => log.info('[updater] up to date:', info?.version));
  autoUpdater.on('update-available', (info) => {
    log.info('[updater] update available:', info?.version);
    // 다운로드는 자동(autoDownload=true) — 백그라운드로 진행됨
  });
  autoUpdater.on('download-progress', (p) => {
    log.info(`[updater] downloading ${p.percent.toFixed(1)}%  (${(p.bytesPerSecond/1024).toFixed(0)} KB/s)`);
  });
  autoUpdater.on('error', (err) => {
    log.error('[updater] error:', err && (err.stack || err.message || err));
  });
  autoUpdater.on('update-downloaded', async (info) => {
    log.info('[updater] downloaded:', info?.version);
    const win = mainWindow || BrowserWindow.getAllWindows()[0];
    const { response } = await dialog.showMessageBox(win, {
      type: 'info',
      buttons: ['지금 재시작하고 설치', '나중에'],
      defaultId: 0,
      cancelId: 1,
      message: '새 버전 다운로드 완료',
      detail: `버전 ${info?.version || ''} 가 준비되었습니다.\n` +
              `지금 설치하시겠어요? 다음에 종료할 때 자동으로 설치됩니다.`,
    });
    if (response === 0) {
      isQuitting = true;
      autoUpdater.quitAndInstall();
    }
  });
}

/** silent=true: 결과를 dialog 로 알리지 않음(자동 체크). silent=false: 사용자 클릭(메뉴) */
async function checkForUpdates({ silent = true } = {}) {
  // dev 모드에서는 publishing 정보가 없을 수 있어 그냥 메시지만
  if (!app.isPackaged) {
    if (!silent) {
      dialog.showMessageBox(mainWindow || undefined, {
        type: 'info',
        message: '개발 모드에서는 자동 업데이트가 동작하지 않습니다.',
        detail: '패키징된 설치본(npm run dist)에서만 작동합니다.',
      });
    }
    log.info('[updater] dev mode — skipping');
    return;
  }
  try {
    const result = await autoUpdater.checkForUpdates();
    if (!silent) {
      const v = result?.updateInfo?.version;
      if (!v || v === app.getVersion()) {
        dialog.showMessageBox(mainWindow || undefined, {
          type: 'info',
          message: '최신 버전을 사용 중입니다.',
          detail: `현재 버전: ${app.getVersion()}`,
        });
      }
    }
  } catch (e) {
    log.error('[updater] checkForUpdates failed:', e);
    if (!silent) {
      dialog.showMessageBox(mainWindow || undefined, {
        type: 'warning',
        message: '업데이트 확인에 실패했습니다.',
        detail: String(e?.message || e),
      });
    }
  }
}

function startUpdateChecker() {
  if (!app.isPackaged) return;             // dev 모드 자동 체크 안 함
  bindAutoUpdaterEvents();
  // 시작 30초 후 첫 체크 (기동 직후 부하 회피)
  setTimeout(() => checkForUpdates({ silent: true }), 30 * 1000);
  // 이후 6시간마다
  updateCheckInterval = setInterval(() => checkForUpdates({ silent: true }), 6 * 60 * 60 * 1000);
}

// ───────────────────────── 단일 인스턴스 ────────────────────────────
// 프로파일 모드에서는 락을 걸지 않아 같은 앱을 여러 번 띄울 수 있게 한다.

const gotLock = PROFILE ? true : app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else if (!PROFILE) {
  app.on('second-instance', () => showMainWindow());
}

// ───────────────────────── 라이프사이클 ─────────────────────────────

app.whenReady().then(() => {
  // 0) 진단 로그 — 알림이 안 뜰 때 원인 파악용
  console.log('[startup]', {
    platform: process.platform,
    electron: process.versions.electron,
    notificationsSupported: Notification.isSupported(),
    appUserModelId: process.platform === 'win32' ? 'com.example.messenger' : '(N/A)',
    safeStorageAvailable: safeStorage.isEncryptionAvailable(),
    profile: PROFILE || '(none)',
  });

  // 1) 설정 / 토큰 부트스트랩
  const cfg = loadConfig();
  serverUrl = process.env.MESSENGER_SERVER_URL
              || cfg.serverUrl
              || null;
  cachedTokens = loadTokensFromDisk();

  // 2) IPC 등록
  registerIpc();

  // 3) 메뉴/트레이/창
  buildAppMenu();
  createTray();
  createMainWindow();

  // 4) 자동 업데이트 (패키징 시에만 활성)
  startUpdateChecker();
});

app.on('window-all-closed', () => {
  // 트레이 백그라운드 동작이 기본. 창은 보통 hide() 되므로 이 이벤트가 잘 발화되지 않지만,
  // 외부 종료(태스크매니저 등)로 창이 사라진 경우 isQuitting 이 true 일 때만 실제 종료한다.
  if (isQuitting && process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  else showMainWindow();
});

app.on('before-quit', () => { isQuitting = true; });

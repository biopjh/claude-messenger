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
const toastMgr = require('./toast');

// 파일 로그 (운영 시 ~/Library/Logs/Messenger/main.log 등에 기록 — 업데이트 디버깅에 필수)
log.transports.file.level = 'info';
log.transports.console.level = 'debug';
autoUpdater.logger = log;
autoUpdater.autoDownload = true;
autoUpdater.autoInstallOnAppQuit = true;

// ─────────────────────────────── 상수 ───────────────────────────────

const ICON_PATH    = path.join(__dirname, '..', '..', 'assets', 'tray.png');
const SETUP_HTML   = path.join(__dirname, '..', 'renderer', 'setup.html');
const SETTINGS_HTML = path.join(__dirname, '..', 'renderer', 'settings.html');
const userDataDir = () => app.getPath('userData');
const CONFIG_FILE = () => path.join(userDataDir(), 'config.json');
const TOKEN_FILE = () => path.join(userDataDir(), 'tokens.dat');

/** 알림 설정 기본값. 사용자가 settings 화면에서 바꾸기 전까지 이 값. */
const DEFAULT_NOTIFICATION_SETTINGS = Object.freeze({
  enabled: true,
  style: 'both',          // 'os' | 'toast' | 'both'
  sound: true,
  toastDurationMs: 5000,
});

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
let notificationSettings = { ...DEFAULT_NOTIFICATION_SETTINGS };
/** @type {BrowserWindow | null} */
let settingsWindow = null;

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

/** 현재 메모리 상태(serverUrl + notificationSettings) 를 통째로 디스크에 보관. */
function persistConfig() {
  saveConfig({
    serverUrl: serverUrl || null,
    notifications: { ...notificationSettings },
  });
}

/** 외부 입력으로 들어온 설정을 안전한 범위로 정규화. */
function sanitizeSettings(s) {
  const styles = ['os', 'toast', 'both'];
  return {
    enabled: !!s.enabled,
    style: styles.includes(s.style) ? s.style : 'both',
    sound: !!s.sound,
    toastDurationMs: Math.max(2000, Math.min(30000, Math.floor(Number(s.toastDurationMs) || 5000))),
  };
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
    // Windows/Linux 에서는 메뉴바를 항상 표시. (Mac 은 OS 상단 메뉴바라 영향 없음)
    autoHideMenuBar: process.platform === 'darwin',
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

// ───────────────────────── 알림 디스패치 ─────────────────────────────

function showOsNotification({ title, body, sound }) {
  if (!Notification.isSupported()) return false;
  const n = new Notification({
    title: String(title || 'Messenger'),
    body:  String(body  || ''),
    icon:  ICON_PATH,
    silent: !sound,
  });
  n.on('click', () => showMainWindow());
  n.show();
  return true;
}

/** 알림 한 건을 사용자 설정에 따라 OS / 토스트 / 둘 다 / 안 함 으로 분기. */
function dispatchNotification({ title, body, roomId }) {
  const s = notificationSettings;
  if (!s.enabled) return false;

  if (s.style === 'os' || s.style === 'both') {
    showOsNotification({ title, body, sound: s.sound });
  }
  if (s.style === 'toast' || s.style === 'both') {
    toastMgr.showToast(
      { title, body, roomId, durationMs: s.toastDurationMs },
      {
        iconPath: ICON_PATH,
        onClick: (clickedRoomId) => {
          showMainWindow();
          if (clickedRoomId && mainWindow && serverUrl) {
            const target = `${serverUrl.replace(/\/+$/, '')}/rooms/${clickedRoomId}`;
            mainWindow.loadURL(target).catch(() => {});
          }
        },
      }
    );
  }
  return true;
}

// ───────────────────────── 설정 윈도우 ─────────────────────────────

function openSettingsWindow() {
  if (settingsWindow && !settingsWindow.isDestroyed()) {
    settingsWindow.show();
    settingsWindow.focus();
    return;
  }
  settingsWindow = new BrowserWindow({
    width: 540,
    height: 660,
    parent: mainWindow || undefined,
    modal: false,
    resizable: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    title: '알림 설정',
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
  settingsWindow.loadFile(SETTINGS_HTML).catch((e) => log.error('settings loadFile', e));
  settingsWindow.on('closed', () => { settingsWindow = null; });
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

  // ── 알림 (설정에 따라 OS 네이티브 / 커스텀 토스트 / 둘 다 분기)
  ipcMain.handle('notify:show', async (_e, payload) => {
    return dispatchNotification(payload);
  });
  ipcMain.handle('notify:test', async () => {
    return dispatchNotification({
      title: 'Messenger 테스트',
      body:  '이 알림이 보이면 설정이 정상입니다.',
      roomId: null,
    });
  });

  // ── 토스트가 자기 자신을 닫거나 클릭됐을 때
  ipcMain.handle('toast:close', (e) => {
    toastMgr.dispatchClose(BrowserWindow.fromWebContents(e.sender));
  });
  ipcMain.handle('toast:click', async (e) => {
    const senderWin = BrowserWindow.fromWebContents(e.sender);
    toastMgr.dispatchClick(senderWin);   // 내부에서 onClick 호출
  });

  // ── 설정 (알림)
  ipcMain.handle('settings:get', async () => ({ ...notificationSettings }));
  ipcMain.handle('settings:save', async (_e, partial) => {
    notificationSettings = sanitizeSettings({ ...notificationSettings, ...(partial || {}) });
    persistConfig();
    return { ...notificationSettings };
  });
  ipcMain.handle('settings:close', async (e) => {
    const win = BrowserWindow.fromWebContents(e.sender);
    if (win && !win.isDestroyed()) win.close();
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
    persistConfig();
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
        { label: '알림 설정…',     click: openSettingsWindow },
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
// 사용자가 메뉴 → "업데이트 확인" 을 누르면 작은 진행 창이 떠서 검사·다운로드·설치
// 진행률을 실시간으로 보여준다. 동시에 메인 창의 작업표시줄(Windows)/도크(macOS)
// 아이콘에도 진행 게이지가 표시된다.

const UPDATE_HTML = path.join(__dirname, '..', 'renderer', 'update.html');
let updateCheckInterval = null;
/** @type {BrowserWindow | null} */
let updateWindow = null;

/**
 * 업데이트 진행 상태. autoUpdater 이벤트 핸들러가 업데이트하며,
 * 변경 시 진행 창과 main window 작업표시줄 게이지로 동시에 반영된다.
 */
const UpdateState = {
  current: {
    phase: 'idle',          // idle | checking | up-to-date | available | downloading | downloaded | error | dev
    version: app.getVersion(),
    percent: 0,
    bytesPerSecond: 0,
    message: '',
  },
  set(patch) {
    this.current = { ...this.current, ...patch };
    // 진행 창에 푸시
    if (updateWindow && !updateWindow.isDestroyed()) {
      updateWindow.webContents.send('update:status', this.current);
    }
    // 작업표시줄/도크 게이지 (Windows/Linux/macOS 공통)
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (this.current.phase === 'downloading') {
        mainWindow.setProgressBar(Math.max(0.001, Math.min(1, this.current.percent / 100)));
      } else {
        mainWindow.setProgressBar(-1);   // 게이지 제거
      }
    }
  },
};

function openUpdateWindow() {
  if (updateWindow && !updateWindow.isDestroyed()) {
    updateWindow.show();
    updateWindow.focus();
    return;
  }
  updateWindow = new BrowserWindow({
    width: 460,
    height: 300,
    parent: mainWindow || undefined,
    modal: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    resizable: false,
    title: '업데이트',
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
  updateWindow.loadFile(UPDATE_HTML).catch((e) => log.error('update window loadFile failed', e));
  updateWindow.on('closed', () => { updateWindow = null; });
}

function bindAutoUpdaterEvents() {
  autoUpdater.on('checking-for-update', () => {
    log.info('[updater] checking…');
    UpdateState.set({ phase: 'checking', message: '' });
  });
  autoUpdater.on('update-not-available', (info) => {
    log.info('[updater] up to date:', info?.version);
    UpdateState.set({ phase: 'up-to-date', version: app.getVersion() });
  });
  autoUpdater.on('update-available', (info) => {
    log.info('[updater] update available:', info?.version);
    UpdateState.set({ phase: 'available', version: info?.version, percent: 0 });
  });
  autoUpdater.on('download-progress', (p) => {
    const pct = Math.round(p.percent || 0);
    log.info(`[updater] downloading ${pct}%  (${(p.bytesPerSecond/1024).toFixed(0)} KB/s)`);
    UpdateState.set({
      phase: 'downloading',
      percent: pct,
      bytesPerSecond: p.bytesPerSecond || 0,
    });
  });
  autoUpdater.on('error', (err) => {
    log.error('[updater] error:', err && (err.stack || err.message || err));
    UpdateState.set({ phase: 'error', message: String((err && (err.message || err)) || '알 수 없는 오류') });
  });
  autoUpdater.on('update-downloaded', async (info) => {
    log.info('[updater] downloaded:', info?.version);
    UpdateState.set({ phase: 'downloaded', version: info?.version, percent: 100 });

    // 진행 창이 열려 있으면 거기서 버튼으로 처리. 닫혀 있으면 dialog 폴백.
    if (updateWindow && !updateWindow.isDestroyed()) return;

    const win = mainWindow || BrowserWindow.getAllWindows()[0];
    const { response } = await dialog.showMessageBox(win, {
      type: 'info',
      buttons: ['지금 재시작하고 설치', '나중에'],
      defaultId: 0, cancelId: 1,
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

/** silent=true: 진행 창 안 띄움(자동 체크). silent=false: 메뉴 → 진행 창 표시 */
async function checkForUpdates({ silent = true } = {}) {
  if (!silent) openUpdateWindow();

  if (!app.isPackaged) {
    log.info('[updater] dev mode — skipping');
    UpdateState.set({
      phase: 'dev',
      message: '개발 모드에서는 자동 업데이트가 동작하지 않습니다.\n패키징된 설치본(.dmg/.exe/.AppImage)에서만 작동합니다.',
    });
    return;
  }

  // 진행 창에 즉시 "확인 중" 상태를 보여줌
  if (!silent) UpdateState.set({ phase: 'checking', message: '' });

  try {
    await autoUpdater.checkForUpdates();
    // 결과는 이벤트 핸들러가 이어서 phase 를 업데이트함
  } catch (e) {
    log.error('[updater] checkForUpdates failed:', e);
    UpdateState.set({ phase: 'error', message: String(e?.message || e) });
  }
}

function registerUpdateIpc() {
  ipcMain.handle('update:get-state', () => UpdateState.current);
  ipcMain.handle('update:install',   () => {
    isQuitting = true;
    autoUpdater.quitAndInstall();
    return true;
  });
  ipcMain.handle('update:close',     () => {
    if (updateWindow && !updateWindow.isDestroyed()) updateWindow.close();
    return true;
  });
}

function startUpdateChecker() {
  registerUpdateIpc();           // dev 모드에서도 IPC 핸들러는 등록 (창 열어도 동작하도록)
  bindAutoUpdaterEvents();
  if (!app.isPackaged) return;   // dev 모드는 자동 체크 스킵
  setTimeout(() => checkForUpdates({ silent: true }), 30 * 1000);
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
  notificationSettings = sanitizeSettings({ ...DEFAULT_NOTIFICATION_SETTINGS, ...(cfg.notifications || {}) });
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

app.on('before-quit', () => {
  isQuitting = true;
  toastMgr.closeAll();
});

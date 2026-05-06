// 커스텀 토스트 알림 매니저.
// 트레이 근처(우하단)에 작은 프레임리스 윈도우를 띄워서 발신자 + 미리보기를 보여준다.
// 5초(설정값) 후 자동 페이드아웃, 빠르게 여러 개 오면 위로 스택, 최대 5개까지.

const { BrowserWindow, screen } = require('electron');
const path = require('node:path');

const W = 360;
const H = 100;
const MARGIN = 16;
const GAP = 8;
const MAX_TOASTS = 5;

const TOAST_HTML = path.join(__dirname, '..', 'renderer', 'toast.html');

/** 활성 토스트 윈도우 목록 (위로 스택). */
const toasts = [];

/** macOS 는 메뉴바가 위쪽이라 우상단 / 그 외는 우하단 트레이 근처. */
function computeBottomLeft(stackIndex) {
  const display = screen.getPrimaryDisplay();
  const wa = display.workArea;
  const x = wa.x + wa.width - W - MARGIN;
  if (process.platform === 'darwin') {
    // macOS: 우상단부터 아래로 쌓임
    const y = wa.y + MARGIN + stackIndex * (H + GAP);
    return { x, y };
  } else {
    // Windows/Linux: 우하단부터 위로 쌓임
    const y = wa.y + wa.height - H - MARGIN - stackIndex * (H + GAP);
    return { x, y };
  }
}

function repositionAll() {
  toasts.forEach((entry, i) => {
    if (entry.win.isDestroyed()) return;
    const { x, y } = computeBottomLeft(i);
    entry.win.setBounds({ x, y, width: W, height: H });
  });
}

/**
 * 토스트 띄우기.
 *  payload: { title, body, roomId, durationMs, iconPath }
 *  onClick: (roomId) => void  — 클릭 시 main 프로세스가 메인 창 활성화 + 채팅방 이동
 */
function showToast(payload, { iconPath, onClick }) {
  // 5개 초과 시 가장 오래된 것 닫기
  while (toasts.length >= MAX_TOASTS) {
    const oldest = toasts.shift();
    if (!oldest.win.isDestroyed()) oldest.win.close();
  }

  const stackIndex = toasts.length;
  const { x, y } = computeBottomLeft(stackIndex);

  const win = new BrowserWindow({
    width: W,
    height: H,
    x, y,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    resizable: false,
    movable: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    show: false,
    hasShadow: false,
    icon: iconPath,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  // 토스트는 dock/taskbar에서 안 보이게 + 가능한 한 포커스 안 뺏기게
  win.setAlwaysOnTop(true, 'floating');
  if (process.platform === 'darwin') {
    win.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  }

  const params = new URLSearchParams({
    title:       String(payload.title || ''),
    body:        String(payload.body  || ''),
    roomId:      String(payload.roomId || ''),
    durationMs:  String(payload.durationMs || 5000),
  });
  win.loadFile(TOAST_HTML, { search: params.toString() });
  win.once('ready-to-show', () => {
    if (!win.isDestroyed()) win.showInactive();   // 포커스 안 빼앗기
  });

  const entry = { win, roomId: payload.roomId, onClick };
  toasts.push(entry);

  win.on('closed', () => {
    const idx = toasts.indexOf(entry);
    if (idx >= 0) toasts.splice(idx, 1);
    repositionAll();
  });
}

/** 모든 토스트 닫기. */
function closeAll() {
  while (toasts.length) {
    const e = toasts.shift();
    if (!e.win.isDestroyed()) e.win.close();
  }
}

/** preload 가 toast 윈도우에서 호출하는 클릭 콜백 라우팅. */
function dispatchClick(senderWin) {
  const entry = toasts.find((e) => e.win === senderWin);
  if (!entry) return;
  if (typeof entry.onClick === 'function') entry.onClick(entry.roomId);
  if (!entry.win.isDestroyed()) entry.win.close();
}

/** 토스트가 자기 자신을 닫고 싶을 때 (페이드아웃 끝) */
function dispatchClose(senderWin) {
  if (senderWin && !senderWin.isDestroyed()) senderWin.close();
}

module.exports = { showToast, closeAll, dispatchClick, dispatchClose };

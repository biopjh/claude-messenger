// Preload 스크립트.
// contextIsolation: true 환경에서 렌더러(웹 페이지) 에 노출할 API 만 화이트리스트로 공개한다.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('messengerNative', {
  /** 데스크톱 앱 안에서 동작 중인지 식별. 웹 브라우저에서는 undefined. */
  isDesktop: true,
  platform: process.platform,
  versions: {
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
  },

  /** 토큰 안전 저장 (OS Keychain 암호화) */
  token: {
    get:   () => ipcRenderer.invoke('token:get'),
    save:  (data) => ipcRenderer.invoke('token:save', data),
    clear: () => ipcRenderer.invoke('token:clear'),
  },

  /** OS 네이티브 알림 */
  notify: {
    show: (payload) => ipcRenderer.invoke('notify:show', payload),
  },

  /** 작업표시줄/도크 unread 배지 + 윈도우 타이틀 */
  badge: {
    set: (count) => ipcRenderer.invoke('badge:set', count),
  },

  /** 첫 실행 / 메뉴에서 사용하는 서버 URL 설정 */
  setup: {
    getUrl:  () => ipcRenderer.invoke('setup:get-url'),
    saveUrl: (url) => ipcRenderer.invoke('setup:save-url', url),
  },

  /** 윈도우 표시 (트레이/알림 클릭 등) */
  window: {
    show: () => ipcRenderer.invoke('window:show'),
  },

  /** 자동 업데이트 진행 UI 가 사용 */
  update: {
    /** 현재 업데이트 상태(phase/version/percent 등) 즉시 조회 */
    getState: () => ipcRenderer.invoke('update:get-state'),
    /** 다운로드 완료 후 호출 — quitAndInstall */
    install:  () => ipcRenderer.invoke('update:install'),
    /** 업데이트 창 닫기 */
    close:    () => ipcRenderer.invoke('update:close'),
    /** 업데이트 상태가 바뀔 때마다 호출. cb(state) */
    onStatus: (cb) => ipcRenderer.on('update:status', (_e, payload) => cb(payload)),
  },
});

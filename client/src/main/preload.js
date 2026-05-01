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
});

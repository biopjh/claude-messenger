// 토큰 저장/조회 추상화.
//   - Electron 데스크톱 (window.messengerNative.token): OS Keychain 으로 암호화 저장
//   - 웹 브라우저: localStorage (학습용 폴백, XSS 노출 위험 있음 — 운영은 HttpOnly 쿠키 권장)
//
// 동기 API(getAccess/getRefresh) 호환을 위해 메모리 캐시를 둔다.
// 페이지 진입 시 반드시 await Auth.bootstrap() 한 뒤에 getAccess() 호출.
window.Auth = (function () {
  const desktopAvailable = !!(window.messengerNative && window.messengerNative.token);
  const cache = { access: null, refresh: null };
  let bootstrapped = false;

  async function bootstrap() {
    if (bootstrapped) return;
    if (desktopAvailable) {
      try {
        const t = await window.messengerNative.token.get();
        cache.access  = t?.access  || null;
        cache.refresh = t?.refresh || null;
      } catch (e) {
        console.error('Auth.bootstrap (desktop) failed', e);
      }
    } else {
      cache.access  = localStorage.getItem('accessToken');
      cache.refresh = localStorage.getItem('refreshToken');
    }
    bootstrapped = true;
  }

  function saveTokens(data) {
    if (!data) return;
    cache.access  = data.accessToken  || cache.access;
    cache.refresh = data.refreshToken || cache.refresh;
    if (desktopAvailable) {
      window.messengerNative.token.save({
        access:  cache.access,
        refresh: cache.refresh,
      }).catch((e) => console.error('token save failed', e));
    } else {
      if (cache.access)  localStorage.setItem('accessToken',  cache.access);
      if (cache.refresh) localStorage.setItem('refreshToken', cache.refresh);
    }
    bootstrapped = true;
  }

  function clear() {
    cache.access = null;
    cache.refresh = null;
    if (desktopAvailable) {
      window.messengerNative.token.clear().catch(() => {});
    } else {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    }
  }

  function getAccess()  { return cache.access; }
  function getRefresh() { return cache.refresh; }

  function authHeader() {
    const t = cache.access;
    return t ? { 'Authorization': 'Bearer ' + t } : {};
  }

  return { bootstrap, saveTokens, clear, getAccess, getRefresh, authHeader };
})();

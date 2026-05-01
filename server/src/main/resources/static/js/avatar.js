// 공유 아바타 헬퍼.
// renderAvatarHTML(name, url, sizeClass)  → string
//   - url 이 있으면 <img>
//   - 없으면 이름 첫 글자 + 이름 해시 기반 배경색
window.Avatar = (function () {
  // 12색 팔레트 (가독성 좋은 톤)
  const COLORS = [
    '#EF4444', '#F59E0B', '#10B981', '#3B82F6',
    '#8B5CF6', '#EC4899', '#14B8A6', '#F97316',
    '#6366F1', '#84CC16', '#06B6D4', '#A855F7'
  ];

  function colorOf(name) {
    const s = String(name || '?');
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
    return COLORS[Math.abs(h) % COLORS.length];
  }

  function initialOf(name) {
    const s = String(name || '?').trim();
    if (!s) return '?';
    // 이모지·다국어 안전: 첫 코드포인트
    const cp = Array.from(s)[0];
    return cp.toUpperCase();
  }

  function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  /** sizeClass: 'avatar--sm' | 'avatar--md' | 'avatar--lg' | undefined */
  function renderAvatarHTML(name, url, sizeClass) {
    const cls = 'avatar' + (sizeClass ? ' ' + sizeClass : '');
    if (url) {
      return `<span class="${cls}"><img src="${escapeHtml(url)}" alt=""></span>`;
    }
    const bg = colorOf(name);
    const ini = initialOf(name);
    return `<span class="${cls}" style="background:${bg}">${escapeHtml(ini)}</span>`;
  }

  return { renderAvatarHTML };
})();

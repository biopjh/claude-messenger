// 채팅 목록 페이지: 친구 검색·요청·수락·목록·채팅방 목록
(async function () {
  await Auth.bootstrap();
  const token = Auth.getAccess();
  if (!token) { location.href = '/login'; return; }

  const $ = (sel) => document.querySelector(sel);
  const api = async (path, opts = {}) => {
    const res = await fetch(path, {
      ...opts,
      headers: {
        'Content-Type': 'application/json',
        ...Auth.authHeader(),
        ...(opts.headers || {}),
      },
    });
    const json = await res.json().catch(() => ({}));
    if (res.status === 401) { Auth.clear(); location.href = '/login'; throw new Error('unauth'); }
    if (!json.success) throw new Error(json.error?.message || 'API error');
    return json.data;
  };

  // ---------------- me ----------------
  api('/api/users/me').then((me) => {
    window.__ME__ = me;
    $('#userBadge').innerHTML =
      Avatar.renderAvatarHTML(me.nickname, me.profileImageUrl, 'avatar--sm') +
      ` <span class="muted">${escape(me.nickname)}</span>`;
  }).catch(() => {});

  // ---------------- search ----------------
  $('#searchBtn').addEventListener('click', doSearch);
  $('#searchInput').addEventListener('keydown', (e) => {
    if (e.isComposing || e.keyCode === 229) return;       // 한국어 IME 조합 중 무시
    if (e.key === 'Enter') doSearch();
  });

  async function doSearch() {
    const q = $('#searchInput').value.trim();
    const ul = $('#searchResults');
    ul.innerHTML = '';
    if (!q) return;
    try {
      const users = await api('/api/users/search?q=' + encodeURIComponent(q));
      if (!users.length) { ul.innerHTML = '<li class="list-empty">검색 결과 없음</li>'; return; }
      users.forEach((u) => {
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML = `
          ${Avatar.renderAvatarHTML(u.nickname, u.profileImageUrl, 'avatar--md')}
          <div class="list-item__main">
            <strong>${escape(u.nickname)}</strong>
            <span class="muted">${escape(u.email)}</span>
          </div>
          <div class="list-item__actions">
            <button class="primary" data-action="add" data-id="${u.id}">친구 요청</button>
          </div>`;
        ul.appendChild(li);
      });
    } catch (e) { alert(e.message); }
  }

  $('#searchResults').addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-action=add]'); if (!btn) return;
    btn.disabled = true;
    try {
      await api('/api/friends/request/' + btn.dataset.id, { method: 'POST' });
      btn.textContent = '요청 보냄';
    } catch (err) { alert(err.message); btn.disabled = false; }
  });

  // ---------------- incoming requests ----------------
  $('#reloadIncoming').addEventListener('click', loadIncoming);
  async function loadIncoming() {
    const ul = $('#incomingList'); ul.innerHTML = '<li class="list-empty">불러오는 중…</li>';
    try {
      const items = await api('/api/friends/requests');
      if (!items.length) { ul.innerHTML = '<li class="list-empty">받은 요청이 없습니다.</li>'; return; }
      ul.innerHTML = '';
      items.forEach((it) => {
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML = `
          ${Avatar.renderAvatarHTML(it.nickname, it.profileImageUrl, 'avatar--md')}
          <div class="list-item__main">
            <strong>${escape(it.nickname)}</strong>
            <span class="muted">${escape(it.email)}</span>
          </div>
          <div class="list-item__actions">
            <button class="primary"  data-action="accept"  data-fid="${it.friendshipId}">수락</button>
            <button class="ghost"    data-action="reject"  data-fid="${it.friendshipId}">거절</button>
          </div>`;
        ul.appendChild(li);
      });
    } catch (e) { ul.innerHTML = `<li class="list-empty">${e.message}</li>`; }
  }
  $('#incomingList').addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-fid]'); if (!btn) return;
    btn.disabled = true;
    const fid = btn.dataset.fid;
    try {
      if (btn.dataset.action === 'accept') {
        await api('/api/friends/' + fid + '/accept', { method: 'POST' });
      } else {
        await api('/api/friends/' + fid, { method: 'DELETE' });
      }
      await loadIncoming();
      await loadFriends();
    } catch (err) { alert(err.message); btn.disabled = false; }
  });

  // ---------------- friends ----------------
  $('#reloadFriends').addEventListener('click', loadFriends);
  async function loadFriends() {
    const ul = $('#friendsList'); ul.innerHTML = '<li class="list-empty">불러오는 중…</li>';
    try {
      const items = await api('/api/friends');
      if (!items.length) { ul.innerHTML = '<li class="list-empty">친구가 아직 없어요.</li>'; return; }
      ul.innerHTML = '';
      items.forEach((it) => {
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML = `
          ${Avatar.renderAvatarHTML(it.nickname, it.profileImageUrl, 'avatar--md')}
          <div class="list-item__main">
            <strong>${escape(it.nickname)}</strong>
            <span class="muted">${escape(it.email)}</span>
            <span class="muted">${escape(it.statusMessage || '')}</span>
          </div>
          <div class="list-item__actions">
            <button class="primary" data-action="chat" data-uid="${it.userId}">1:1 채팅</button>
            <button class="ghost"   data-action="del"  data-fid="${it.friendshipId}">삭제</button>
          </div>`;
        ul.appendChild(li);
      });
    } catch (e) { ul.innerHTML = `<li class="list-empty">${e.message}</li>`; }
  }
  $('#friendsList').addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-action]'); if (!btn) return;
    btn.disabled = true;
    try {
      if (btn.dataset.action === 'chat') {
        const data = await api('/api/chat-rooms/direct/' + btn.dataset.uid, { method: 'POST' });
        location.href = '/rooms/' + data.roomId;
      } else if (btn.dataset.action === 'del') {
        if (!confirm('이 친구를 삭제할까요?')) { btn.disabled = false; return; }
        await api('/api/friends/' + btn.dataset.fid, { method: 'DELETE' });
        await loadFriends();
      }
    } catch (err) { alert(err.message); btn.disabled = false; }
  });

  // ---------------- rooms ----------------
  $('#reloadRooms').addEventListener('click', loadRooms);
  async function loadRooms() {
    const ul = $('#roomsList'); ul.innerHTML = '<li class="list-empty">불러오는 중…</li>';
    try {
      const items = await api('/api/chat-rooms');
      // 데스크톱: 작업표시줄/도크 unread 배지 갱신
      if (window.messengerNative?.isDesktop) {
        const total = (items || []).reduce((s, it) => s + (Number(it.unreadCount) || 0), 0);
        window.messengerNative.badge.set(total).catch(() => {});
      }
      if (!items.length) { ul.innerHTML = '<li class="list-empty">아직 채팅방이 없어요.</li>'; return; }
      ul.innerHTML = '';
      items.forEach((it) => {
        const li = document.createElement('li');
        li.className = 'list-item room-item';
        li.dataset.roomId = it.roomId;
        const time = it.lastMessageAt ? new Date(it.lastMessageAt).toLocaleString() : '';
        // GROUP 은 표시용 group 아이콘(👥), DIRECT 는 상대 프로필
        const avatar = it.roomType === 'GROUP'
          ? `<span class="avatar avatar--md avatar--group">👥</span>`
          : Avatar.renderAvatarHTML(it.displayTitle || '?', it.displayProfileImageUrl, 'avatar--md');
        li.innerHTML = `
          ${avatar}
          <div class="list-item__main">
            <strong>${escape(it.displayTitle || '(제목 없음)')}</strong>
            <span class="muted">${escape(it.lastMessagePreview || '아직 메시지가 없어요.')}</span>
          </div>
          <div class="list-item__actions">
            <span class="muted small">${time}</span>
            ${it.unreadCount > 0 ? `<span class="badge">${it.unreadCount}</span>` : ''}
          </div>`;
        li.addEventListener('click', () => location.href = '/rooms/' + it.roomId);
        ul.appendChild(li);
      });
    } catch (e) { ul.innerHTML = `<li class="list-empty">${e.message}</li>`; }
  }

  // ---------------- group create modal ----------------
  const $modal = $('#groupModal');
  $('#newGroupBtn').addEventListener('click', openGroupModal);
  $modal.addEventListener('click', (e) => {
    if (e.target.dataset.close !== undefined) closeGroupModal();
  });
  $('#groupCreateBtn').addEventListener('click', createGroup);

  async function openGroupModal() {
    $('#groupTitleInput').value = '';
    $('#groupModalMsg').textContent = '';
    const ul = $('#groupFriendList');
    ul.innerHTML = '<li class="list-empty">불러오는 중…</li>';
    $modal.classList.remove('hidden');
    $modal.setAttribute('aria-hidden', 'false');
    try {
      const friends = await api('/api/friends');
      if (!friends.length) { ul.innerHTML = '<li class="list-empty">친구를 먼저 추가하세요.</li>'; return; }
      ul.innerHTML = '';
      friends.forEach((f) => {
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML = `
          <label class="checklist__row">
            <input type="checkbox" value="${f.userId}">
            ${Avatar.renderAvatarHTML(f.nickname, f.profileImageUrl, 'avatar--sm')}
            <span class="list-item__main">
              <strong>${escape(f.nickname)}</strong>
              <span class="muted">${escape(f.email)}</span>
            </span>
          </label>`;
        ul.appendChild(li);
      });
    } catch (e) { ul.innerHTML = `<li class="list-empty">${e.message}</li>`; }
  }
  function closeGroupModal() {
    $modal.classList.add('hidden');
    $modal.setAttribute('aria-hidden', 'true');
  }
  async function createGroup() {
    const title = $('#groupTitleInput').value.trim();
    const ids = Array.from(document.querySelectorAll('#groupFriendList input[type=checkbox]:checked'))
      .map((c) => Number(c.value));
    const msg = $('#groupModalMsg');
    msg.className = 'msg';
    if (!title) { msg.className = 'msg error'; msg.textContent = '제목을 입력하세요.'; return; }
    if (!ids.length) { msg.className = 'msg error'; msg.textContent = '친구를 1명 이상 선택하세요.'; return; }
    $('#groupCreateBtn').disabled = true;
    try {
      const data = await api('/api/chat-rooms/group', {
        method: 'POST',
        body: JSON.stringify({ title, memberIds: ids }),
      });
      closeGroupModal();
      location.href = '/rooms/' + data.roomId;
    } catch (e) {
      msg.className = 'msg error';
      msg.textContent = e.message;
      $('#groupCreateBtn').disabled = false;
    }
  }

  // 초기 로드
  loadIncoming();
  loadFriends();
  loadRooms();

  // 로그아웃
  $('#logoutBtn').addEventListener('click', () => { Auth.clear(); location.href = '/login'; });

  // 실시간 알림 (새 메시지 수신 시 방 목록 갱신)
  const sock = new SockJS('/ws-chat');
  const stomp = Stomp.over(sock);
  stomp.debug = null;
  stomp.connect({ Authorization: 'Bearer ' + token }, () => {
    stomp.subscribe('/user/queue/notifications', (frame) => {
      try {
        const noti = JSON.parse(frame.body);
        if (noti.kind === 'NEW_MESSAGE') {
          loadRooms();
          // 데스크톱: OS 네이티브 알림 (창 포커스 없을 때만)
          if (window.messengerNative?.isDesktop && !document.hasFocus()) {
            const m = noti.message || {};
            const title = m.senderNickname || '새 메시지';
            const body =
              m.type === 'TEXT'  ? (m.content || '') :
              m.type === 'IMAGE' ? '📷 이미지' :
              m.type === 'FILE'  ? ('📎 ' + (m.content || '파일')) :
              m.type === 'SYSTEM'? (m.content || '') :
              (m.content || '새 메시지가 도착했어요.');
            window.messengerNative.notify.show({ title, body }).catch(() => {});
          }
        }
      } catch (e) {}
    });
  });

  function escape(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }
})();

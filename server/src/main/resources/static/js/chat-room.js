// 한 채팅방 화면: 과거 메시지 + 실시간 STOMP 송수신
(async function () {
  await Auth.bootstrap();
  const token = Auth.getAccess();
  if (!token) { location.href = '/login'; return; }

  const roomId = window.__ROOM_ID__;
  const $ = (sel) => document.querySelector(sel);
  let me = null;
  let detail = null;       // { roomId, roomType, title, members[] }
  let oldestId = null;     // 더 보기 cursor
  let stomp = null;
  let lastReadMessageId = 0;

  const api = async (path, opts = {}) => {
    const res = await fetch(path, {
      ...opts,
      headers: { 'Content-Type': 'application/json', ...Auth.authHeader(), ...(opts.headers || {}) },
    });
    const json = await res.json().catch(() => ({}));
    if (res.status === 401) { Auth.clear(); location.href = '/login'; throw new Error('unauth'); }
    if (!json.success) throw new Error(json.error?.message || 'API error');
    return json.data;
  };

  // 1) 내 정보 + 방 상세 + 과거 메시지 로드
  Promise.all([
    api('/api/users/me'),
    api('/api/chat-rooms/' + roomId),
  ]).then(async ([_me, _detail]) => {
    me = _me;
    detail = _detail;
    renderHeader();
    await loadOlder(true);
    connectStomp();
  }).catch((e) => alert(e.message));

  function renderHeader() {
    $('#roomTitle').textContent = detail.title || '채팅방';
    if (detail.roomType === 'GROUP') {
      $('#roomMemberCount').textContent = '참여자 ' + detail.members.length + '명';
      $('#inviteBtn').classList.remove('hidden');
      $('#leaveBtn').classList.remove('hidden');
    } else {
      $('#roomMemberCount').textContent = '';
    }
  }

  async function loadOlder(initial) {
    const params = new URLSearchParams();
    if (oldestId != null) params.set('cursorId', oldestId);
    params.set('size', '30');
    const list = await api('/api/chat-rooms/' + roomId + '/messages?' + params.toString());
    if (!list.length) return;
    // API 는 최신 → 오래된 순(DESC). 화면에선 오래된 → 최신 순으로 prepend.
    const ordered = [...list].reverse();
    const wrap = $('#messages');
    const wasAtBottom = isAtBottom();
    const beforeHeight = wrap.scrollHeight;

    if (initial) wrap.innerHTML = '';
    const frag = document.createDocumentFragment();
    ordered.forEach((m) => frag.appendChild(renderMessage(m)));

    if (initial) {
      wrap.appendChild(frag);
      scrollToBottom();
    } else {
      wrap.prepend(frag);
      // 스크롤 위치 보존: 추가된 만큼 내려준다
      wrap.scrollTop = wrap.scrollHeight - beforeHeight;
    }

    oldestId = list[list.length - 1].id; // DESC 의 마지막 = 가장 오래된 id
    const maxId = Math.max(...list.map((m) => m.id));
    if (maxId > lastReadMessageId) {
      lastReadMessageId = maxId;
      sendRead();
    }
    if (initial && wasAtBottom) scrollToBottom();
  }

  $('#messages').addEventListener('scroll', () => {
    if ($('#messages').scrollTop < 60 && oldestId != null) {
      loadOlder(false).catch(() => {});
    }
  });

  function connectStomp() {
    const sock = new SockJS('/ws-chat');
    stomp = Stomp.over(sock);
    stomp.debug = null;
    stomp.connect(
      { Authorization: 'Bearer ' + token },
      () => {
        $('#connState').textContent = '연결됨';
        $('#connState').classList.add('ok');
        stomp.subscribe('/topic/rooms/' + roomId, (frame) => {
          const m = JSON.parse(frame.body);
          appendMessage(m);
          if (m.id > lastReadMessageId) {
            lastReadMessageId = m.id;
            sendRead();
          }
          // 시스템 메시지(초대/나가기)는 멤버가 바뀐 신호 → 멤버 목록 다시 받기
          if (m.type === 'SYSTEM') refreshDetail();

          // 데스크톱: 본인 메시지가 아니고 창 포커스 없을 때 OS 알림
          if (
            window.messengerNative?.isDesktop &&
            m.type !== 'SYSTEM' &&
            me && m.senderId !== me.id &&
            !document.hasFocus()
          ) {
            const prefix = (detail?.roomType === 'GROUP') ? `[${detail.title}] ` : '';
            const title = prefix + (m.senderNickname || '새 메시지');
            const body =
              m.type === 'TEXT'  ? (m.content || '') :
              m.type === 'IMAGE' ? '📷 이미지' :
              m.type === 'FILE'  ? ('📎 ' + (m.content || '파일')) :
              (m.content || '');
            window.messengerNative.notify.show({ title, body }).catch(() => {});
          }
        });
      },
      (err) => {
        $('#connState').textContent = '연결 실패';
        $('#connState').classList.remove('ok');
        console.error(err);
      }
    );
  }

  async function refreshDetail() {
    try {
      detail = await api('/api/chat-rooms/' + roomId);
      renderHeader();
    } catch (e) { /* 방에서 추방되었을 가능성 — 무시 */ }
  }

  function sendRead() {
    if (!stomp || !stomp.connected) return;
    stomp.send('/app/chat.read', {}, JSON.stringify({
      roomId, lastReadMessageId,
    }));
  }

  // 전송
  $('#sendForm').addEventListener('submit', (e) => {
    e.preventDefault();
    submit();
  });
  $('#msgInput').addEventListener('keydown', (e) => {
    // 한국어 IME 조합 중 keydown 은 무시. (Chromium 은 조합 중 Enter 시 keydown 을 두 번 발화 →
    //  isComposing 체크가 없으면 마지막 글자가 한 번 더 전송됨)
    if (e.isComposing || e.keyCode === 229) return;
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  });

  function submit() {
    const text = $('#msgInput').value.trim();
    if (!text) return;
    if (!stomp || !stomp.connected) { alert('아직 연결 중입니다. 잠시 후 다시 시도하세요.'); return; }
    stomp.send('/app/chat.send', {}, JSON.stringify({
      roomId, type: 'TEXT', content: text,
    }));
    $('#msgInput').value = '';
  }

  // ------------------- 첨부 파일 -------------------
  $('#attachBtn').addEventListener('click', () => $('#fileInput').click());
  $('#fileInput').addEventListener('change', (e) => {
    const f = e.target.files?.[0];
    if (f) uploadAndSend(f);
    e.target.value = '';   // 같은 파일 재선택 가능하게
  });

  // 드래그&드롭
  const $shell = document.querySelector('.chat-shell');
  const $overlay = $('#dropOverlay');
  ['dragenter', 'dragover'].forEach((ev) => $shell.addEventListener(ev, (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    $overlay.classList.remove('hidden');
  }));
  ['dragleave', 'drop'].forEach((ev) => $shell.addEventListener(ev, (e) => {
    if (ev === 'dragleave' && e.target !== $overlay) return;
    $overlay.classList.add('hidden');
  }));
  $shell.addEventListener('drop', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (f) uploadAndSend(f);
  });
  function hasFiles(e) {
    return e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');
  }

  async function uploadAndSend(file) {
    if (!stomp || !stomp.connected) { alert('아직 연결 중입니다. 잠시 후 다시 시도하세요.'); return; }
    const status = $('#uploadStatus');
    status.classList.remove('hidden');
    status.textContent = `업로드 중… ${file.name} (${humanSize(file.size)})`;
    try {
      const fd = new FormData();
      fd.append('file', file);
      const res = await fetch('/api/files', {
        method: 'POST',
        headers: { ...Auth.authHeader() },  // Content-Type 은 브라우저가 boundary 와 함께 자동 설정
        body: fd,
      });
      const json = await res.json().catch(() => ({}));
      if (res.status === 401) { Auth.clear(); location.href = '/login'; return; }
      if (!json.success) throw new Error(json.error?.message || '업로드 실패');
      const up = json.data;

      stomp.send('/app/chat.send', {}, JSON.stringify({
        roomId,
        type: up.kind,                       // "IMAGE" or "FILE"
        content: up.originalFileName,        // 표시용 원본 파일명
        attachmentUrl: up.url,
        attachmentMimeType: up.mimeType,
        attachmentSizeBytes: up.sizeBytes,
      }));
    } catch (e) {
      alert(e.message);
    } finally {
      status.classList.add('hidden');
    }
  }

  function humanSize(n) {
    if (n < 1024) return n + 'B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + 'KB';
    if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB';
    return (n / 1024 / 1024 / 1024).toFixed(2) + 'GB';
  }

  // ------------------- 라이트박스 (이미지 클릭 확대) -------------------
  const $lightbox = $('#lightbox');
  const $lightboxImg = $('#lightboxImg');
  $lightbox.addEventListener('click', () => $lightbox.classList.add('hidden'));

  function renderMessage(m) {
    const li = document.createElement('div');
    if (m.type === 'SYSTEM') {
      li.className = 'msg-system';
      li.textContent = m.content;
      return li;
    }
    const mine = me && m.senderId === me.id;
    li.className = 'msg-row ' + (mine ? 'msg-row--mine' : 'msg-row--other');
    li.dataset.id = m.id;

    const avatar = mine
      ? ''
      : Avatar.renderAvatarHTML(m.senderNickname, m.senderProfileImageUrl, 'avatar--sm');

    const sender = mine ? '' : `<div class="msg__sender">${escape(m.senderNickname || '')}</div>`;
    let body;
    if (m.type === 'IMAGE' && m.attachmentUrl) {
      body = `<a class="msg__image-wrap" data-image="${escape(m.attachmentUrl)}">
                <img class="msg__image" src="${escape(m.attachmentUrl)}" alt="${escape(m.content)}">
              </a>`;
    } else if (m.type === 'FILE' && m.attachmentUrl) {
      body = `<a class="msg__file" href="${escape(m.attachmentUrl)}" download="${escape(m.content)}">
                <span class="msg__file-icon">📄</span>
                <span class="msg__file-meta">
                  <span class="msg__file-name">${escape(m.content)}</span>
                  <span class="msg__file-size">${humanSize(m.attachmentSizeBytes || 0)}</span>
                </span>
              </a>`;
    } else {
      body = `<div class="msg__bubble">${escape(m.content)}</div>`;
    }

    const inner = `
      <div class="msg ${mine ? 'msg--mine' : 'msg--other'}">
        ${sender}${body}
        <div class="msg__time">${formatTime(m.createdAt)}</div>
      </div>`;
    li.innerHTML = mine ? inner : `${avatar}${inner}`;

    // 이미지 클릭 → 라이트박스
    const imgWrap = li.querySelector('.msg__image-wrap');
    if (imgWrap) {
      imgWrap.addEventListener('click', (e) => {
        e.preventDefault();
        $lightboxImg.src = imgWrap.dataset.image;
        $lightbox.classList.remove('hidden');
      });
    }
    return li;
  }

  function appendMessage(m) {
    const wrap = $('#messages');
    const wasAtBottom = isAtBottom();
    wrap.appendChild(renderMessage(m));
    if (wasAtBottom) scrollToBottom();
  }

  function isAtBottom() {
    const w = $('#messages');
    return w.scrollHeight - w.scrollTop - w.clientHeight < 80;
  }
  function scrollToBottom() {
    const w = $('#messages'); w.scrollTop = w.scrollHeight;
  }
  function formatTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  function escape(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  // ------------------- 멤버 패널 -------------------
  const $drawer = $('#membersPanel');
  $('#membersBtn').addEventListener('click', openMembers);
  $drawer.addEventListener('click', (e) => {
    if (e.target.dataset.closeDrawer !== undefined) closeMembers();
  });
  function openMembers() {
    const ul = $('#membersList'); ul.innerHTML = '';
    (detail?.members ?? []).forEach((u) => {
      const li = document.createElement('li');
      li.className = 'list-item';
      const tag = u.id === me.id ? ' (나)' : '';
      li.innerHTML = `
        ${Avatar.renderAvatarHTML(u.nickname, u.profileImageUrl, 'avatar--md')}
        <div class="list-item__main">
          <strong>${escape(u.nickname)}${tag}</strong>
          <span class="muted">${escape(u.email)}</span>
        </div>`;
      ul.appendChild(li);
    });
    $drawer.classList.remove('hidden');
  }
  function closeMembers() { $drawer.classList.add('hidden'); }

  // ------------------- 초대 모달 -------------------
  const $inviteModal = $('#inviteModal');
  $('#inviteBtn').addEventListener('click', openInvite);
  $inviteModal.addEventListener('click', (e) => {
    if (e.target.dataset.close !== undefined) closeInvite();
  });
  $('#inviteSubmitBtn').addEventListener('click', submitInvite);

  async function openInvite() {
    const ul = $('#inviteFriendList');
    $('#inviteModalMsg').textContent = '';
    ul.innerHTML = '<li class="list-empty">불러오는 중…</li>';
    $inviteModal.classList.remove('hidden');
    try {
      const friends = await api('/api/friends');
      const memberIds = new Set((detail?.members ?? []).map((m) => m.id));
      const candidates = friends.filter((f) => !memberIds.has(f.userId));
      if (!candidates.length) { ul.innerHTML = '<li class="list-empty">초대할 수 있는 친구가 없습니다.</li>'; return; }
      ul.innerHTML = '';
      candidates.forEach((f) => {
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
  function closeInvite() { $inviteModal.classList.add('hidden'); }

  async function submitInvite() {
    const ids = Array.from(document.querySelectorAll('#inviteFriendList input[type=checkbox]:checked'))
      .map((c) => Number(c.value));
    const msg = $('#inviteModalMsg');
    msg.className = 'msg';
    if (!ids.length) { msg.className = 'msg error'; msg.textContent = '초대할 친구를 선택하세요.'; return; }
    $('#inviteSubmitBtn').disabled = true;
    try {
      await api('/api/chat-rooms/' + roomId + '/invite', {
        method: 'POST',
        body: JSON.stringify({ userIds: ids }),
      });
      closeInvite();
      // 시스템 메시지가 곧 STOMP 로 와서 detail 도 새로고침 됨
    } catch (e) {
      msg.className = 'msg error';
      msg.textContent = e.message;
    } finally {
      $('#inviteSubmitBtn').disabled = false;
    }
  }

  // ------------------- 나가기 -------------------
  $('#leaveBtn').addEventListener('click', async () => {
    if (!confirm('이 그룹채팅에서 나가시겠습니까? 다시 들어오려면 다시 초대받아야 합니다.')) return;
    try {
      await api('/api/chat-rooms/' + roomId + '/leave', { method: 'POST' });
      location.href = '/home';
    } catch (e) { alert(e.message); }
  });
})();

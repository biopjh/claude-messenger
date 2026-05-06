// 한 채팅방 화면: 과거 메시지 + 실시간 STOMP 송수신 + 답장/수정/삭제
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

  /** id → message 캐시 (답장 미리보기/수정 시 원본 참조용) */
  const messageById = new Map();

  /** 입력바 모드: { kind: 'normal' } | { kind: 'reply', targetId, sender, preview } | { kind: 'edit', targetId } */
  let inputMode = { kind: 'normal' };

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
      wrap.scrollTop = wrap.scrollHeight - beforeHeight;
    }

    oldestId = list[list.length - 1].id;
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
          const wrap = $('#messages');
          const existing = wrap.querySelector(`[data-id="${m.id}"]`);
          applyMessage(m);

          // 수정/삭제(이미 화면에 있던 메시지가 갱신된 경우)는 알림/읽음 처리 스킵
          if (existing) return;

          if (m.id > lastReadMessageId) {
            lastReadMessageId = m.id;
            sendRead();
          }
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
    stomp.send('/app/chat.read', {}, JSON.stringify({ roomId, lastReadMessageId }));
  }

  // ───────────── 입력바 모드 (normal / reply / edit) ─────────────

  function previewOfMessage(m) {
    if (m.deletedAt) return '삭제된 메시지';
    if (m.type === 'IMAGE') return '📷 이미지';
    if (m.type === 'FILE')  return '📎 ' + (m.content || '파일');
    return m.content || '';
  }

  function enterReplyMode(m) {
    inputMode = {
      kind: 'reply',
      targetId: m.id,
      sender: m.senderNickname || '',
      preview: previewOfMessage(m).slice(0, 80),
    };
    renderInputBanner();
    $('#msgInput').focus();
  }

  function enterEditMode(m) {
    inputMode = { kind: 'edit', targetId: m.id };
    $('#msgInput').value = m.content || '';
    renderInputBanner();
    $('#msgInput').focus();
  }

  function exitInputMode() {
    inputMode = { kind: 'normal' };
    renderInputBanner();
  }

  function renderInputBanner() {
    let banner = $('#inputBanner');
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'inputBanner';
      banner.className = 'input-banner hidden';
      const form = $('#sendForm');
      form.parentElement.insertBefore(banner, form);
    }
    if (inputMode.kind === 'normal') {
      banner.classList.add('hidden');
      banner.innerHTML = '';
      return;
    }
    banner.classList.remove('hidden');
    if (inputMode.kind === 'reply') {
      banner.innerHTML = `
        <div class="input-banner__icon">↩</div>
        <div class="input-banner__body">
          <div class="input-banner__title">${escape(inputMode.sender)} 에게 답장</div>
          <div class="input-banner__preview">${escape(inputMode.preview)}</div>
        </div>
        <button class="input-banner__close" type="button" aria-label="취소">✕</button>`;
    } else if (inputMode.kind === 'edit') {
      banner.innerHTML = `
        <div class="input-banner__icon">✎</div>
        <div class="input-banner__body">
          <div class="input-banner__title">메시지 수정 중</div>
          <div class="input-banner__preview muted">텍스트 메시지만, 5분 이내</div>
        </div>
        <button class="input-banner__close" type="button" aria-label="취소">✕</button>`;
    }
    banner.querySelector('.input-banner__close').addEventListener('click', () => {
      if (inputMode.kind === 'edit') $('#msgInput').value = '';
      exitInputMode();
    });
  }

  // ───────────── 메시지 액션 (답장/수정/삭제/리액션) ─────────────

  /** 클라이언트가 노출할 emoji picker — 서버 화이트리스트와 동일해야 함 */
  const REACTION_EMOJIS = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

  let actionMenuEl = null;
  function openActionMenu(m, anchorEl) {
    closeActionMenu();
    const mine = me && m.senderId === me.id;
    const canEdit   = mine && m.type === 'TEXT' && !m.deletedAt;
    const canDelete = mine && !m.deletedAt;
    const canReply  = !m.deletedAt && m.type !== 'SYSTEM';
    const canReact  = !m.deletedAt && m.type !== 'SYSTEM';
    if (!canEdit && !canDelete && !canReply && !canReact) return;

    const menu = document.createElement('div');
    menu.className = 'action-menu';
    let html = '';
    if (canReact) {
      html += `<div class="action-menu__picker">` +
        REACTION_EMOJIS.map(e =>
          `<button class="emoji-btn" data-react="${e}" type="button" title="${e}">${e}</button>`
        ).join('') +
      `</div>`;
    }
    if (canReply)  html += `<button data-act="reply"  type="button">답장</button>`;
    if (canEdit)   html += `<button data-act="edit"   type="button">수정</button>`;
    if (canDelete) html += `<button data-act="delete" type="button">삭제</button>`;
    menu.innerHTML = html;
    document.body.appendChild(menu);

    const rect = anchorEl.getBoundingClientRect();
    const menuW = 220;
    const left = Math.min(rect.left, window.innerWidth - menuW - 8);
    menu.style.top = `${rect.bottom + 4}px`;
    menu.style.left = `${left}px`;

    menu.addEventListener('click', (e) => {
      const reactBtn = e.target.closest('button[data-react]');
      if (reactBtn) {
        closeActionMenu();
        toggleReaction(m.id, reactBtn.dataset.react);
        return;
      }
      const btn = e.target.closest('button[data-act]');
      if (!btn) return;
      const act = btn.dataset.act;
      closeActionMenu();
      if (act === 'reply')  enterReplyMode(m);
      if (act === 'edit')   enterEditMode(m);
      if (act === 'delete') deleteMessageAction(m);
    });

    setTimeout(() => {
      document.addEventListener('click', closeActionMenu, { once: true });
    }, 0);
    actionMenuEl = menu;
  }
  function closeActionMenu() {
    if (actionMenuEl && actionMenuEl.parentNode) actionMenuEl.parentNode.removeChild(actionMenuEl);
    actionMenuEl = null;
  }

  async function deleteMessageAction(m) {
    if (!confirm('이 메시지를 삭제하시겠습니까?')) return;
    try {
      await api('/api/messages/' + m.id, { method: 'DELETE' });
    } catch (e) {
      alert(e.message);
    }
  }

  async function toggleReaction(messageId, emoji) {
    try {
      await api('/api/messages/' + messageId + '/reactions', {
        method: 'POST',
        body: JSON.stringify({ emoji }),
      });
      // STOMP 가 broadcast 해서 자동 갱신
    } catch (e) {
      alert(e.message);
    }
  }

  // ───────────── 전송 (3 가지 모드) ─────────────

  $('#sendForm').addEventListener('submit', (e) => {
    e.preventDefault();
    submit();
  });
  $('#msgInput').addEventListener('keydown', (e) => {
    // 한국어 IME 조합 중 keydown 은 무시
    if (e.isComposing || e.keyCode === 229) return;
    if (e.key === 'Escape' && inputMode.kind !== 'normal') {
      e.preventDefault();
      if (inputMode.kind === 'edit') $('#msgInput').value = '';
      exitInputMode();
      return;
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  });

  async function submit() {
    const text = $('#msgInput').value.trim();
    if (!text) return;

    // ── 수정 모드: REST PATCH
    if (inputMode.kind === 'edit') {
      const id = inputMode.targetId;
      try {
        await api('/api/messages/' + id, {
          method: 'PATCH',
          body: JSON.stringify({ content: text }),
        });
        $('#msgInput').value = '';
        exitInputMode();
        // STOMP 가 broadcast 해서 자동으로 화면 갱신
      } catch (e) {
        alert(e.message);
      }
      return;
    }

    // ── 일반 / 답장 모드: STOMP send
    if (!stomp || !stomp.connected) { alert('아직 연결 중입니다. 잠시 후 다시 시도하세요.'); return; }
    const replyTo = (inputMode.kind === 'reply') ? inputMode.targetId : null;
    stomp.send('/app/chat.send', {}, JSON.stringify({
      roomId,
      type: 'TEXT',
      content: text,
      replyToMessageId: replyTo,
    }));
    $('#msgInput').value = '';
    exitInputMode();
  }

  // ───────────── 첨부 파일 ─────────────

  $('#attachBtn').addEventListener('click', () => $('#fileInput').click());
  $('#fileInput').addEventListener('change', (e) => {
    const f = e.target.files?.[0];
    if (f) uploadAndSend(f);
    e.target.value = '';
  });

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
    if (!stomp || !stomp.connected) { alert('아직 연결 중입니다.'); return; }
    if (inputMode.kind === 'edit') {
      alert('수정 모드에서는 첨부를 보낼 수 없습니다.');
      return;
    }
    const status = $('#uploadStatus');
    status.classList.remove('hidden');
    status.textContent = `업로드 중… ${file.name} (${humanSize(file.size)})`;
    try {
      const fd = new FormData();
      fd.append('file', file);
      const res = await fetch('/api/files', {
        method: 'POST',
        headers: { ...Auth.authHeader() },
        body: fd,
      });
      const json = await res.json().catch(() => ({}));
      if (res.status === 401) { Auth.clear(); location.href = '/login'; return; }
      if (!json.success) throw new Error(json.error?.message || '업로드 실패');
      const up = json.data;

      const replyTo = (inputMode.kind === 'reply') ? inputMode.targetId : null;
      stomp.send('/app/chat.send', {}, JSON.stringify({
        roomId,
        type: up.kind,
        content: up.originalFileName,
        attachmentUrl: up.url,
        attachmentMimeType: up.mimeType,
        attachmentSizeBytes: up.sizeBytes,
        replyToMessageId: replyTo,
      }));
      exitInputMode();
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

  // ───────────── 라이트박스 ─────────────
  const $lightbox = $('#lightbox');
  const $lightboxImg = $('#lightboxImg');
  $lightbox.addEventListener('click', () => $lightbox.classList.add('hidden'));

  // ───────────── 메시지 렌더 ─────────────

  function renderMessage(m) {
    messageById.set(m.id, m);
    const li = document.createElement('div');

    if (m.type === 'SYSTEM') {
      li.className = 'msg-system';
      li.dataset.id = m.id;
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

    // 답장 인용 박스
    let replyQuote = '';
    if (m.replyToMessageId != null) {
      if (m.replyToContent != null || m.replyToType != null) {
        const quoteText =
          m.replyToType === 'IMAGE' ? '📷 이미지' :
          m.replyToType === 'FILE'  ? '📎 ' + (m.replyToContent || '파일') :
          (m.replyToContent || '');
        replyQuote = `
          <div class="msg__quote" data-jump-to="${m.replyToMessageId}">
            <div class="msg__quote-sender">${escape(m.replyToSenderNickname || '')}</div>
            <div class="msg__quote-text">${escape(quoteText)}</div>
          </div>`;
      } else {
        replyQuote = `<div class="msg__quote msg__quote--deleted">삭제된 메시지</div>`;
      }
    }

    // 본문
    let body;
    if (m.deletedAt) {
      body = `<div class="msg__bubble msg__bubble--deleted">삭제된 메시지입니다</div>`;
    } else if (m.type === 'IMAGE' && m.attachmentUrl) {
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

    const editedTag = (m.editedAt && !m.deletedAt) ? ' <span class="msg__edited">(편집됨)</span>' : '';
    const timeRow = `<div class="msg__time">${formatTime(m.createdAt)}${editedTag}</div>`;

    // ⋯ 액션 버튼 (삭제된 메시지 / SYSTEM 은 표시 안 함)
    const showActions = !m.deletedAt && m.type !== 'SYSTEM';
    const actionBtn = showActions
      ? `<button class="msg__menu-btn" type="button" aria-label="메시지 메뉴">⋯</button>`
      : '';

    // 리액션 chips — 삭제된 메시지에는 표시 안 함
    let reactionsRow = '';
    if (!m.deletedAt && Array.isArray(m.reactions) && m.reactions.length > 0) {
      reactionsRow = `<div class="reactions">` +
        m.reactions.map((r) => {
          const count = (r.userIds || []).length;
          const reactedByMe = me && (r.userIds || []).includes(me.id);
          const tip = (r.userNicknames || []).join(', ');
          return `<button class="reaction ${reactedByMe ? 'reaction--mine' : ''}"
                          data-emoji="${escape(r.emoji)}"
                          title="${escape(tip)}"
                          type="button">
                    <span class="reaction__emoji">${escape(r.emoji)}</span>
                    <span class="reaction__count">${count}</span>
                  </button>`;
        }).join('') +
      `</div>`;
    }

    const inner = `
      <div class="msg ${mine ? 'msg--mine' : 'msg--other'}">
        ${sender}
        ${replyQuote}
        ${body}
        ${timeRow}
        ${reactionsRow}
        ${actionBtn}
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

    // ⋯ 클릭 → 액션 메뉴
    const menuBtn = li.querySelector('.msg__menu-btn');
    if (menuBtn) {
      menuBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        openActionMenu(m, e.currentTarget);
      });
    }

    // 리액션 chip 클릭 → 그 이모지 toggle
    li.querySelectorAll('.reaction[data-emoji]').forEach((chip) => {
      chip.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleReaction(m.id, chip.dataset.emoji);
      });
    });

    // 인용 박스 클릭 → 원본 메시지로 스크롤 (있으면)
    const quote = li.querySelector('.msg__quote[data-jump-to]');
    if (quote) {
      quote.addEventListener('click', () => {
        const target = $('#messages').querySelector(`[data-id="${quote.dataset.jumpTo}"]`);
        if (target) {
          target.scrollIntoView({ behavior: 'smooth', block: 'center' });
          target.classList.add('msg-row--flash');
          setTimeout(() => target.classList.remove('msg-row--flash'), 1500);
        }
      });
    }
    return li;
  }

  function applyMessage(m) {
    messageById.set(m.id, m);
    const wrap = $('#messages');
    const existing = wrap.querySelector(`[data-id="${m.id}"]`);
    const newEl = renderMessage(m);
    if (existing) {
      existing.replaceWith(newEl);
    } else {
      const wasAtBottom = isAtBottom();
      wrap.appendChild(newEl);
      if (wasAtBottom) scrollToBottom();
    }
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

  // ───────────── 멤버 패널 ─────────────
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

  // ───────────── 초대 모달 ─────────────
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
    } catch (e) {
      msg.className = 'msg error';
      msg.textContent = e.message;
    } finally {
      $('#inviteSubmitBtn').disabled = false;
    }
  }

  // ───────────── 나가기 ─────────────
  $('#leaveBtn').addEventListener('click', async () => {
    if (!confirm('이 그룹채팅에서 나가시겠습니까? 다시 들어오려면 다시 초대받아야 합니다.')) return;
    try {
      await api('/api/chat-rooms/' + roomId + '/leave', { method: 'POST' });
      location.href = '/home';
    } catch (e) { alert(e.message); }
  });

  // ───────────── 방 안 검색 ─────────────
  const $searchPanel = $('#searchPanel');
  const $searchInput = $('#searchInput');
  const $searchResults = $('#searchResults');
  let searchDebounceTimer = null;
  let searchAbortController = null;

  $('#searchBtn').addEventListener('click', openSearch);
  $searchPanel.addEventListener('click', (e) => {
    if (e.target.dataset.closeSearch !== undefined) closeSearch();
  });
  $searchInput.addEventListener('input', () => {
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => runSearch($searchInput.value), 300);
  });
  $searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeSearch();
  });

  function openSearch() {
    $searchPanel.classList.remove('hidden');
    $searchResults.innerHTML = '<div class="search-empty">검색어를 입력하세요</div>';
    setTimeout(() => $searchInput.focus(), 0);
  }
  function closeSearch() {
    $searchPanel.classList.add('hidden');
    if (searchAbortController) { searchAbortController.abort(); searchAbortController = null; }
  }

  async function runSearch(rawQuery) {
    const query = (rawQuery || '').trim();
    if (!query) {
      $searchResults.innerHTML = '<div class="search-empty">검색어를 입력하세요</div>';
      return;
    }
    if (searchAbortController) searchAbortController.abort();
    searchAbortController = new AbortController();

    $searchResults.innerHTML = '<div class="search-empty">검색 중…</div>';
    try {
      const res = await fetch(
        '/api/chat-rooms/' + roomId + '/messages/search?q=' + encodeURIComponent(query) + '&size=50',
        { headers: { ...Auth.authHeader() }, signal: searchAbortController.signal }
      );
      const json = await res.json().catch(() => ({}));
      if (res.status === 401) { Auth.clear(); location.href = '/login'; return; }
      if (!json.success) throw new Error(json.error?.message || '검색 실패');

      const items = json.data || [];
      if (!items.length) {
        $searchResults.innerHTML = '<div class="search-empty">결과 없음</div>';
        return;
      }
      $searchResults.innerHTML = '';
      items.forEach((m) => $searchResults.appendChild(renderSearchResult(m, query)));
    } catch (e) {
      if (e.name === 'AbortError') return;
      $searchResults.innerHTML = `<div class="search-empty">${escape(e.message)}</div>`;
    }
  }

  function renderSearchResult(m, query) {
    const card = document.createElement('div');
    card.className = 'search-result';
    card.dataset.id = m.id;

    const previewText =
      m.type === 'IMAGE' ? '📷 ' + (m.content || '이미지') :
      m.type === 'FILE'  ? '📎 ' + (m.content || '파일') :
      (m.content || '');
    const preview = highlightMatch(previewText, query);
    const time = new Date(m.createdAt).toLocaleString();

    card.innerHTML = `
      ${Avatar.renderAvatarHTML(m.senderNickname, m.senderProfileImageUrl, 'avatar--sm')}
      <div class="search-result__main">
        <div class="search-result__head">
          <strong>${escape(m.senderNickname || '')}</strong>
          <span class="muted small">${escape(time)}</span>
        </div>
        <div class="search-result__preview">${preview}</div>
      </div>`;
    card.addEventListener('click', () => jumpToMessage(m.id));
    return card;
  }

  /** 매치 부분만 <mark> 로 감싸기 (대소문자 무시) */
  function highlightMatch(text, query) {
    const safe = escape(text || '');
    if (!query) return safe;
    const lower = String(text || '').toLowerCase();
    const q = query.toLowerCase();
    if (!lower.includes(q)) return safe;
    const parts = [];
    let i = 0;
    while (i < text.length) {
      const idx = lower.indexOf(q, i);
      if (idx < 0) { parts.push(escape(text.slice(i))); break; }
      if (idx > i) parts.push(escape(text.slice(i, idx)));
      parts.push('<mark>' + escape(text.slice(idx, idx + q.length)) + '</mark>');
      i = idx + q.length;
    }
    return parts.join('');
  }

  /** 검색 결과 클릭 → 해당 메시지로 점프. 화면에 없으면 그 메시지 주변 페이지를 다시 로드. */
  async function jumpToMessage(targetId) {
    closeSearch();
    const wrap = $('#messages');
    let target = wrap.querySelector(`[data-id="${targetId}"]`);

    if (!target) {
      // 그 메시지를 포함하는 페이지 로드 (cursorId = targetId+1 이면 m.id <= targetId 인 30개)
      try {
        const list = await api('/api/chat-rooms/' + roomId + '/messages?cursorId=' + (targetId + 1) + '&size=30');
        if (!list.length) return;
        wrap.innerHTML = '';
        const ordered = [...list].reverse();
        ordered.forEach((mm) => wrap.appendChild(renderMessage(mm)));
        oldestId = list[list.length - 1].id;
        target = wrap.querySelector(`[data-id="${targetId}"]`);
      } catch (e) {
        alert(e.message);
        return;
      }
    }

    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      target.classList.add('msg-row--flash');
      setTimeout(() => target.classList.remove('msg-row--flash'), 1500);
    }
  }
})();

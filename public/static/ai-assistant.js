// ============================================================
// AI TOURISM ASSISTANT MODULE
// Calls FastAPI backend at https://belagavi-tourism-yuvaraj21.vercel.app
// No Gemini credentials — all API calls go through the backend.
// ============================================================

(function () {
    'use strict';

    // ── Configuration ─────────────────────────────────────────
    const _isLocal = typeof window !== 'undefined' && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');
    const _defaultBackendUrl = _isLocal ? 'http://127.0.0.1:8000' : 'https://belagavi-tourism-yuvaraj21.vercel.app';
    const AI_BACKEND_URL = window.AI_BACKEND_URL || _defaultBackendUrl;
    window.AI_BACKEND_URL = AI_BACKEND_URL;
    const FETCH_TIMEOUT_MS = 30000; // 30 s (Gemini retries can add ~6 s)

    const DEFAULT_SUGGESTIONS = [
        { label: 'Best places to visit', query: 'Best places to visit in Belagavi' },
        { label: 'Waterfalls near Belagavi', query: 'Waterfalls near Belagavi' },
        { label: 'Hidden gems in Belagavi', query: 'Hidden gems in Belagavi' },
        { label: 'Weekend trip ideas', query: 'Weekend trip ideas in Belagavi' },
        { label: 'Wildlife sanctuaries', query: 'Wildlife sanctuaries in Belagavi' },
        { label: 'Best time to visit', query: 'Best time to visit Belagavi' }
    ];

    function getContextualSuggestions(placeName) {
        return [
            { label: 'History', query: 'What is the history of ' + placeName + '?' },
            { label: 'Entry Fee', query: 'What is the entry fee for ' + placeName + '?' },
            { label: 'Best Time', query: 'What is the best time to visit ' + placeName + '?' },
            { label: 'How to Reach', query: 'How to reach ' + placeName + ' from Belagavi?' },
            { label: 'Nearby Places', query: 'What are nearby places to visit around ' + placeName + '?' }
        ];
    }

    // ── State ──────────────────────────────────────────────────
    let _initialized = false;
    let _isLoading   = false;
    let _isLoadingChat = false;
    let _loadingEl   = null;
    let _conversationHistory = [];
    let _currentDestinationContext = null;

    // ── Chat History State ─────────────────────────────────────
    let _currentUid = null;
    let _activeChatId = null;
    let _isNewChat = true;
    let _chatList = [];

    // ── Firebase / Firestore helpers ───────────────────────────
    function _getFirestore() {
        return window.fbDb || null;
    }

    function _getFirestoreMethods() {
        return window.fbFirestoreMethods || null;
    }

    function _getAuth() {
        return window.fbAuth || null;
    }

    function _getCurrentUid() {
        if (_currentUid) return _currentUid;
        const auth = _getAuth();
        if (auth && auth.currentUser) {
            _currentUid = auth.currentUser.uid;
            return _currentUid;
        }
        if (window.currentUser && window.currentUser.uid) {
            _currentUid = window.currentUser.uid;
            return _currentUid;
        }
        return null;
    }

    // ── Deterministic Chat Title Generator ─────────────────────
    function generateChatTitle(message) {
        if (!message || typeof message !== 'string') return 'New Conversation';
        var text = message.trim();
        var cleaned = text
            .replace(/^(can you\s+)?(tell me about|what is|what are|where is|where are|how to reach|how do i reach|how can i go to|show me|give me|i want to know about|do you know about|what about|any info on|information about)\s+(the\s+)?/i, '')
            .replace(/^(is|are)\s+([a-z0-9\s]+)\s+(open today|open now|open\??)$/i, '$2 opening hours')
            .replace(/^(what is the\s+)?(entry fee|ticket price|timings?|opening hours?)\s+(for|of)\s+/i, '')
            .replace(/^(what are\s+)?(the\s+)?best\s+/i, 'Best ')
            .replace(/\?+$/, '')
            .trim();

        if (!cleaned) cleaned = text;
        cleaned = cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
        if (cleaned.length > 45) {
            cleaned = cleaned.slice(0, 42).trim() + '...';
        }
        return cleaned;
    }

    // ── Chat History UI Renderer ───────────────────────────────
    function renderHistoryUI() {
        var listEl = _el('ai-history-list');
        if (!listEl) return;

        var uid = _getCurrentUid();
        if (!uid) {
            listEl.innerHTML = '<div class="ai-history-empty" id="ai-history-empty">Log in to save and view chat history</div>';
            return;
        }

        if (!_chatList || _chatList.length === 0) {
            listEl.innerHTML = '<div class="ai-history-empty" id="ai-history-empty">No previous chats yet</div>';
            return;
        }

        listEl.innerHTML = '';
        _chatList.forEach(function(chat) {
            var item = document.createElement('div');
            item.className = 'ai-history-item' + (_activeChatId === chat.id ? ' active' : '');
            item.setAttribute('role', 'button');
            item.setAttribute('tabindex', '0');
            item.dataset.chatId = chat.id;

            var left = document.createElement('div');
            left.className = 'ai-history-item-left';

            var icon = document.createElement('i');
            icon.className = 'fa-regular fa-message ai-history-item-icon';
            icon.setAttribute('aria-hidden', 'true');

            var titleSpan = document.createElement('span');
            titleSpan.className = 'ai-history-title-text';
            titleSpan.textContent = chat.title || 'Conversation';
            titleSpan.title = chat.title || 'Conversation';

            left.appendChild(icon);
            left.appendChild(titleSpan);

            var delBtn = document.createElement('button');
            delBtn.type = 'button';
            delBtn.className = 'ai-history-delete-btn';
            delBtn.setAttribute('aria-label', 'Delete conversation');
            delBtn.title = 'Delete conversation';
            delBtn.innerHTML = '<i class="fa-solid fa-trash-can" aria-hidden="true"></i>';

            delBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                deleteChat(chat.id);
            });

            item.appendChild(left);
            item.appendChild(delBtn);

            item.addEventListener('click', function(e) {
                e.preventDefault();
                openChat(chat.id);
            });

            item.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    openChat(chat.id);
                }
            });

            listEl.appendChild(item);
        });
    }

    // ── Load Chat History ──────────────────────────────────────
    let _isLoadingHistory = false;
    async function loadUserChatHistory(uid) {
        if (!uid) {
            console.log('[AI History] loadUserChatHistory called with empty UID, clearing history UI');
            _chatList = [];
            renderHistoryUI();
            return;
        }
        const db = _getFirestore();
        const fm = _getFirestoreMethods();
        if (!db || !fm) {
            console.warn('[AI History] Firestore not initialized yet.');
            return;
        }

        if (_isLoadingHistory) {
            console.log('[AI History] Load already in progress for UID:', uid);
            return;
        }
        _isLoadingHistory = true;

        try {
            console.log('[AI History] AI HISTORY UID:', uid);
            console.log('[AI History] Querying path: users/' + uid + '/ai_chats');
            const chatsRef = fm.collection(db, 'users', uid, 'ai_chats');
            let snap;
            try {
                const q = fm.query(chatsRef, fm.orderBy('updatedAt', 'desc'));
                snap = await fm.getDocs(q);
            } catch (queryErr) {
                console.warn('[AI History] orderBy query failed, fetching unsorted chats:', queryErr);
                snap = await fm.getDocs(chatsRef);
            }

            var list = [];
            snap.forEach(function(docSnap) {
                var data = docSnap.data() || {};
                list.push({
                    id: docSnap.id,
                    title: data.title || 'Conversation',
                    createdAt: data.createdAt,
                    updatedAt: data.updatedAt
                });
            });

            // Sort descending by updatedAt in JavaScript (handles Firestore Timestamp, Date, or numbers)
            list.sort(function(a, b) {
                var timeA = 0;
                if (a.updatedAt) {
                    if (typeof a.updatedAt.toMillis === 'function') timeA = a.updatedAt.toMillis();
                    else if (a.updatedAt.seconds) timeA = a.updatedAt.seconds * 1000;
                    else timeA = new Date(a.updatedAt).getTime() || 0;
                }
                var timeB = 0;
                if (b.updatedAt) {
                    if (typeof b.updatedAt.toMillis === 'function') timeB = b.updatedAt.toMillis();
                    else if (b.updatedAt.seconds) timeB = b.updatedAt.seconds * 1000;
                    else timeB = new Date(b.updatedAt).getTime() || 0;
                }
                return timeB - timeA;
            });

            _chatList = list;
            console.log('[AI History] Firestore returned', _chatList.length, 'chats for UID:', uid);
            renderHistoryUI();
        } catch (err) {
            console.error('[AI History] Error loading user chat history:', err);
            renderHistoryUI();
        } finally {
            _isLoadingHistory = false;
        }
    }
    window.loadUserChatHistory = loadUserChatHistory;

    function refreshAIChatHistory() {
        const uid = _getCurrentUid();
        if (uid) {
            loadUserChatHistory(uid);
        } else {
            renderHistoryUI();
        }
    }
    window.refreshAIChatHistory = refreshAIChatHistory;

    // ── Start New Chat ─────────────────────────────────────────
    function startNewChat() {
        _activeChatId = 'local_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
        _isNewChat = true;
        _conversationHistory = [];
        _currentDestinationContext = null;

        const uid = _getCurrentUid();
        if (uid) {
            try { sessionStorage.removeItem('bt_ai_active_chat_' + uid); } catch(e) {}
        }

        var msgs = _el('ai-messages');
        if (msgs) {
            msgs.innerHTML = '';
            appendMessage('assistant',
                '<strong>Hello! I\'m your Belagavi Tourism AI Assistant.</strong><br><br>' +
                'I can help you discover waterfalls, forts, temples, wildlife sanctuaries, and more across the Belagavi district.<br><br>' +
                'Try one of the suggestions above, or ask me anything about Belagavi tourism!'
            );
        }

        var inputEl = _el('ai-input');
        if (inputEl) {
            inputEl.value = '';
            inputEl.placeholder = 'Ask about Belagavi…';
        }

        var indicator = _el('ai-context-indicator');
        if (indicator) indicator.style.display = 'none';

        var titleEl = _el('ai-page-title');
        var subEl   = _el('ai-page-sub');
        if (titleEl) titleEl.textContent = 'AI Travel Guide';
        if (subEl) subEl.textContent = "Ask anything about Belagavi's destinations, history, and travel tips.";

        _updateChatState();
        _renderChips();
        renderHistoryUI();

        closeChatHistoryDrawer();

        if (inputEl) {
            setTimeout(function() {
                try { inputEl.focus(); } catch (e) {}
            }, 60);
        }
    }
    window.startNewChat = startNewChat;

    // ── Open History Item ──────────────────────────────────────
    async function openChat(chatId) {
        if (!chatId || _isLoading || _isLoadingChat) return;
        if (_activeChatId === chatId && !_isNewChat) {
            closeChatHistoryDrawer();
            return;
        }

        const uid = _getCurrentUid();
        const db = _getFirestore();
        const fm = _getFirestoreMethods();
        if (!uid || !db || !fm) return;

        _isLoadingChat = true;
        _activeChatId = chatId;
        _isNewChat = false;
        renderHistoryUI();

        var msgs = _el('ai-messages');
        if (msgs) {
            msgs.innerHTML = '';
            appendMessage('assistant', '<div class="ai-history-loading" style="display:flex;align-items:center;gap:8px;color:var(--ink-secondary);"><i class="fa-solid fa-spinner fa-spin"></i> Loading conversation...</div>');
        }

        try {
            const msgsRef = fm.collection(db, 'users', uid, 'ai_chats', chatId, 'messages');
            let snap;
            try {
                const q = fm.query(msgsRef, fm.orderBy('timestamp', 'asc'));
                snap = await fm.getDocs(q);
            } catch (queryErr) {
                console.warn('[AI History] orderBy timestamp query failed, fetching unsorted messages:', queryErr);
                snap = await fm.getDocs(msgsRef);
            }

            if (msgs) msgs.innerHTML = '';
            _conversationHistory = [];

            var rawMsgs = [];
            snap.forEach(function(docSnap) {
                rawMsgs.push(docSnap.data() || {});
            });

            // Sort ascending by timestamp in JavaScript
            rawMsgs.sort(function(a, b) {
                var timeA = 0;
                if (a.timestamp) {
                    if (typeof a.timestamp.toMillis === 'function') timeA = a.timestamp.toMillis();
                    else if (a.timestamp.seconds) timeA = a.timestamp.seconds * 1000;
                    else timeA = new Date(a.timestamp).getTime() || 0;
                }
                var timeB = 0;
                if (b.timestamp) {
                    if (typeof b.timestamp.toMillis === 'function') timeB = b.timestamp.toMillis();
                    else if (b.timestamp.seconds) timeB = b.timestamp.seconds * 1000;
                    else timeB = new Date(b.timestamp).getTime() || 0;
                }
                return timeA - timeB;
            });

            rawMsgs.forEach(function(data) {
                var role = data.role || 'assistant';
                var content = data.content || '';

                if (role === 'user') {
                    appendMessage('user', content);
                    _conversationHistory.push({ role: 'user', content: content });
                } else {
                    var safeAnswer = _escHtml(content).replace(/\n/g, '<br>');
                    appendMessage('assistant', safeAnswer);
                    _conversationHistory.push({ role: 'assistant', content: content });

                    if (Array.isArray(data.destinations) && data.destinations.length > 0) {
                        var wrap = document.createElement('div');
                        wrap.className = 'ai-dest-cards-wrap';
                        if (data.destinations.length > 1) {
                            var lbl = document.createElement('div');
                            lbl.className = 'ai-dest-label';
                            lbl.textContent = data.destinations.length + ' Recommended Destinations';
                            wrap.appendChild(lbl);
                        }
                        data.destinations.forEach(function(d) { wrap.appendChild(_renderDestCard(d)); });
                        msgs.appendChild(wrap);
                    }

                    if (Array.isArray(data.web_sources) && data.web_sources.length > 0) {
                        var webWrap = document.createElement('div');
                        webWrap.className = 'ai-web-sources-container';
                        var webTitle = document.createElement('span');
                        webTitle.className = 'text-muted small me-1';
                        webTitle.innerHTML = '<i class="fa-solid fa-globe fa-xs me-1"></i>Web Sources:';
                        webWrap.appendChild(webTitle);
                        data.web_sources.forEach(function(ws) {
                            var link = document.createElement('a');
                            link.className = 'ai-web-source-pill';
                            link.href = ws.url;
                            link.target = '_blank';
                            link.rel = 'noopener noreferrer';
                            link.innerHTML = _escHtml(ws.domain || ws.title || 'Source') + ' <i class="fa-solid fa-arrow-up-right-from-square fa-2xs"></i>';
                            webWrap.appendChild(link);
                        });
                        msgs.appendChild(webWrap);
                    }
                }
            });

            if (_conversationHistory.length > 10) {
                _conversationHistory = _conversationHistory.slice(-10);
            }

            try { sessionStorage.setItem('bt_ai_active_chat_' + uid, chatId); } catch(e) {}
            _updateChatState();
            _renderChips();
            _scrollBottom();
        } catch (err) {
            console.error('[AI History] Error opening chat:', err);
            if (msgs) {
                msgs.innerHTML = '';
                appendMessage('assistant', '<span class="ai-error-text"><i class="fa-solid fa-triangle-exclamation me-1"></i>Could not load conversation. Please try again.</span>');
            }
        } finally {
            _isLoadingChat = false;
            closeChatHistoryDrawer();
        }
    }
    window.openChat = openChat;

    // ── Delete Chat ────────────────────────────────────────────
    async function deleteChat(chatId) {
        if (!chatId) return;
        const uid = _getCurrentUid();
        const db = _getFirestore();
        const fm = _getFirestoreMethods();
        if (!uid || !db || !fm) return;

        if (!confirm('Are you sure you want to delete this conversation?')) return;

        try {
            // Delete messages subcollection
            const msgsRef = fm.collection(db, 'users', uid, 'ai_chats', chatId, 'messages');
            const snap = await fm.getDocs(msgsRef);
            const deletePromises = [];
            snap.forEach(function(d) {
                deletePromises.push(fm.deleteDoc(fm.doc(db, 'users', uid, 'ai_chats', chatId, 'messages', d.id)));
            });
            await Promise.all(deletePromises);

            // Delete chat document
            await fm.deleteDoc(fm.doc(db, 'users', uid, 'ai_chats', chatId));

            // Update in-memory list
            _chatList = _chatList.filter(function(c) { return c.id !== chatId; });
            renderHistoryUI();

            // If active chat was deleted, start new chat
            if (_activeChatId === chatId) {
                try { sessionStorage.removeItem('bt_ai_active_chat_' + uid); } catch(e) {}
                startNewChat();
            }
        } catch (err) {
            console.error('[AI History] Error deleting chat:', err);
            alert('Could not delete conversation. Please try again.');
        }
    }
    window.deleteChat = deleteChat;

    // ── Toggle / Close Drawer ──────────────────────────────────
    function toggleChatHistoryDrawer() {
        var sidebar = _el('ai-history-sidebar');
        var backdrop = _el('ai-history-backdrop');
        var container = _el('ai-assistant');

        if (window.innerWidth <= 899) {
            if (sidebar) sidebar.classList.toggle('open');
            if (backdrop) backdrop.classList.toggle('open');
        } else {
            if (container) container.classList.toggle('ai-sidebar-collapsed');
        }
    }
    window.toggleChatHistoryDrawer = toggleChatHistoryDrawer;

    function closeChatHistoryDrawer() {
        var sidebar = _el('ai-history-sidebar');
        var backdrop = _el('ai-history-backdrop');
        if (sidebar) sidebar.classList.remove('open');
        if (backdrop) backdrop.classList.remove('open');
    }
    window.closeChatHistoryDrawer = closeChatHistoryDrawer;

    // ── DOM helpers ────────────────────────────────────────────
    function _el(id) { return document.getElementById(id); }

    function _escHtml(str) {
        const d = document.createElement('div');
        d.appendChild(document.createTextNode(str || ''));
        return d.innerHTML;
    }

    function _scrollBottom() {
        const msgs = _el('ai-messages');
        if (msgs) {
            msgs.scrollTop = msgs.scrollHeight;
            requestAnimationFrame(function () {
                if (msgs) msgs.scrollTop = msgs.scrollHeight;
            });
        }
    }

    function hasUserMessages() {
        if (_conversationHistory && _conversationHistory.some(function(m) { return m.role === 'user'; })) {
            return true;
        }
        var msgs = _el('ai-messages');
        if (msgs && msgs.querySelector('.ai-msg-user')) {
            return true;
        }
        return false;
    }

    function _updateChatState() {
        var wrap = _el('ai-suggestion-chips') || _el('ai-chips');
        var sectionWrap = document.querySelector('.ai-section-wrap');
        var userActive = hasUserMessages();

        if (userActive) {
            if (wrap) wrap.style.display = 'none';
            if (sectionWrap) sectionWrap.classList.add('ai-chat-started');
        } else {
            if (wrap) wrap.style.display = 'flex';
            if (sectionWrap) sectionWrap.classList.remove('ai-chat-started');
        }
    }

    // ── fetch with timeout ─────────────────────────────────────
    async function _fetchWithTimeout(url, options, ms) {
        const ctrl  = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), ms);
        try {
            const resp = await fetch(url, { ...options, signal: ctrl.signal });
            clearTimeout(timer);
            return resp;
        } catch (err) {
            clearTimeout(timer);
            throw err;
        }
    }

    // ── Bubble rendering ───────────────────────────────────────
    function appendMessage(role, html) {
        const msgs = _el('ai-messages');
        if (!msgs) return;
        const wrap = document.createElement('div');
        if (role === 'user') {
            wrap.className = 'ai-msg-row ai-msg-user';
            wrap.innerHTML = '<div class="ai-bubble ai-bubble-user">' + _escHtml(html) + '</div>';
        } else {
            wrap.className = 'ai-msg-row ai-msg-assistant';
            wrap.innerHTML =
                '<div class="ai-bubble-avatar" aria-hidden="true"><i class="fa-solid fa-robot"></i></div>' +
                '<div class="ai-bubble ai-bubble-assistant">' + html + '</div>';
        }
        msgs.appendChild(wrap);
        _scrollBottom();
    }
    window.appendAIMessage = appendMessage;

    // ── Strip internal database IDs (e.g., "(ID: 6)") and citation tags from user-facing text ──
    function stripInternalIds(text) {
        if (!text || typeof text !== 'string') return '';
        return text
            .replace(/[^\S\r\n]*\(\s*ID:\s*\d+\s*\)/gi, '')
            .replace(/^([^\S\r\n]*)\(\s*ID:\s*\d+\s*\)[^\S\r\n]*/gim, '$1')
            .replace(/\[\s*WEB\s*SOURCE\s*\d+\s*(?:,\s*WEB\s*SOURCE\s*\d+\s*)*\]/gi, '')
            .replace(/\(\s*SOURCE\s*[A-Z]\s*\)/gi, '')
            .replace(/\bSOURCE\s*[A-Z]\b/gi, '')
            .replace(/\s{2,}/g, ' ')
            .trim();
    }

    // ── Loading dots ───────────────────────────────────────────
    function showLoading() {
        if (_loadingEl) return;
        const msgs = _el('ai-messages');
        if (!msgs) return;
        _loadingEl = document.createElement('div');
        _loadingEl.id = 'ai-typing-indicator';
        _loadingEl.className = 'ai-msg-row ai-msg-assistant';
        _loadingEl.innerHTML =
            '<div class="ai-bubble-avatar" aria-hidden="true"><i class="fa-solid fa-robot"></i></div>' +
            '<div class="ai-bubble ai-bubble-assistant ai-typing-bubble">' +
                '<div class="ai-typing-dots" role="status" aria-label="Thinking">' +
                    '<span></span><span></span><span></span>' +
                '</div>' +
            '</div>';
        msgs.appendChild(_loadingEl);
        _scrollBottom();
    }

    function hideLoading() {
        if (_loadingEl) { _loadingEl.remove(); _loadingEl = null; }
    }

    // ── Image Lightbox ─────────────────────────────────────────
    let _lightboxEl = null;

    function _getOrCreateLightbox() {
        if (_lightboxEl) return _lightboxEl;
        let lb = document.getElementById('ai-image-lightbox');
        if (!lb) {
            lb = document.createElement('div');
            lb.id = 'ai-image-lightbox';
            lb.className = 'ai-lightbox';
            lb.setAttribute('role', 'dialog');
            lb.setAttribute('aria-modal', 'true');
            lb.setAttribute('aria-label', 'Destination Image Preview');
            lb.innerHTML =
                '<div class="ai-lightbox-backdrop" aria-hidden="true"></div>' +
                '<div class="ai-lightbox-container">' +
                    '<button type="button" class="ai-lightbox-close" aria-label="Close image preview">' +
                        '<i class="fa-solid fa-xmark"></i>' +
                    '</button>' +
                    '<div class="ai-lightbox-img-frame">' +
                        '<img class="ai-lightbox-img" src="" alt="Enlarged destination image">' +
                    '</div>' +
                    '<div class="ai-lightbox-caption"></div>' +
                '</div>';
            document.body.appendChild(lb);

            lb.querySelector('.ai-lightbox-backdrop').addEventListener('click', closeImageLightbox);

            lb.querySelector('.ai-lightbox-close').addEventListener('click', function(e) {
                e.stopPropagation();
                closeImageLightbox();
            });

            lb.querySelector('.ai-lightbox-container').addEventListener('click', function(e) {
                if (e.target === this) {
                    closeImageLightbox();
                }
            });
        }
        _lightboxEl = lb;
        return _lightboxEl;
    }

    function openImageLightbox(src, caption) {
        if (!src) return;
        const lb = _getOrCreateLightbox();
        const img = lb.querySelector('.ai-lightbox-img');
        const cap = lb.querySelector('.ai-lightbox-caption');

        img.src = src;
        img.alt = caption || 'Destination image';
        if (cap) {
            cap.textContent = caption || '';
            cap.style.display = caption ? 'block' : 'none';
        }

        lb.classList.add('ai-lightbox-active');
        document.body.classList.add('ai-lightbox-open');

        const closeBtn = lb.querySelector('.ai-lightbox-close');
        if (closeBtn) closeBtn.focus();

        document.addEventListener('keydown', _handleLightboxKeyDown);
    }

    function closeImageLightbox() {
        if (!_lightboxEl || !_lightboxEl.classList.contains('ai-lightbox-active')) return;
        _lightboxEl.classList.remove('ai-lightbox-active');
        document.body.classList.remove('ai-lightbox-open');
        document.removeEventListener('keydown', _handleLightboxKeyDown);
    }

    function _handleLightboxKeyDown(e) {
        if (e.key === 'Escape') {
            e.preventDefault();
            closeImageLightbox();
        }
    }

    window.openImageLightbox = openImageLightbox;
    window.closeImageLightbox = closeImageLightbox;

    // ── Destination card ───────────────────────────────────────
    function _renderDestCard(dest) {
        const id       = dest.place_id;
        const rawName  = stripInternalIds(dest.name || 'Destination');
        const name     = _escHtml(rawName);
        const category = _escHtml(dest.category || '');
        const city     = _escHtml(dest.city     || '');
        const fee      = _escHtml(dest.entry_fee     || '');
        const duration = _escHtml(dest.visit_duration || '');
        const folder   = dest.folder_name || 'place';
        const reason   = dest.reason ? _escHtml(stripInternalIds(dest.reason)) : '';
        const icons    = { waterfall:'💧', temple:'🛕', fort:'🏰', nature:'🌿',
                           wildlife:'🦅', lake:'🏞️', dam:'🌊', garden:'🌳', park:'🌳' };
        const emoji    = icons[(category).toLowerCase()] || '📍';

        const card = document.createElement('div');
        card.className = 'ai-dest-card';
        card.setAttribute('role', 'button');
        card.setAttribute('tabindex', '0');
        card.setAttribute('aria-label', 'View details for ' + rawName);

        var feeHtml      = fee      ? '<div class="ai-dest-tag"><i class="fa-solid fa-ticket fa-xs me-1"></i>' + fee + '</div>' : '';
        var durationHtml = duration ? '<div class="ai-dest-tag"><i class="fa-regular fa-clock fa-xs me-1"></i>' + duration + '</div>' : '';
        var reasonHtml   = reason   ? '<div class="ai-dest-reason">' + reason + '</div>' : '';
        var metaSep      = city ? ' · ' + city : '';

        card.innerHTML =
            '<img class="ai-dest-img" src="/static/images/' + folder + '/1.jpg" alt="' + name + '" title="Click to view large image" loading="lazy" onerror="this.src=\'/static/icon-192.png\'">' +
            '<div class="ai-dest-body">' +
                '<div class="ai-dest-name">' + emoji + ' ' + name + '</div>' +
                '<div class="ai-dest-meta">' + category + metaSep + '</div>' +
                feeHtml + durationHtml + reasonHtml +
            '</div>' +
            '<div class="ai-dest-arrow"><i class="fa-solid fa-chevron-right"></i></div>';

        // Lightbox trigger only on image click
        const imgEl = card.querySelector('.ai-dest-img');
        if (imgEl) {
            imgEl.addEventListener('click', function(e) {
                e.stopPropagation();
                openImageLightbox(imgEl.currentSrc || imgEl.src, rawName);
            });
            imgEl.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.stopPropagation();
                    e.preventDefault();
                    openImageLightbox(imgEl.currentSrc || imgEl.src, rawName);
                }
            });
            imgEl.setAttribute('role', 'button');
            imgEl.setAttribute('tabindex', '0');
            imgEl.setAttribute('aria-label', 'View large image of ' + rawName);
        }

        function _go() {
            if (typeof navigateToPlace === 'function') {
                navigateToPlace(id);
            } else {
                window.location.hash = '#place-' + id;
            }
        }
        card.addEventListener('click', _go);
        card.addEventListener('keydown', function(e) {
            if (e.target === imgEl) return;
            if (e.key === 'Enter' || e.key === ' ') _go();
        });
        return card;
    }

    // ── Intent Classifier for Destination Recommendations ──────
    function shouldShowRecommendations(userQuery, data) {
        if (!userQuery || typeof userQuery !== 'string') return false;
        if (!data || !Array.isArray(data.destinations) || data.destinations.length === 0) return false;

        var q = userQuery.trim().toLowerCase();

        // 1. Explicit Negation / No-Recommendation Intent (Always Suppress)
        var negationPatterns = [
            /\bdon'?t\s+recommend\b/,
            /\bdo\s+not\s+recommend\b/,
            /\bdon'?t\s+suggest\b/,
            /\bdo\s+not\s+suggest\b/,
            /\bno\s+recommendations?\b/,
            /\bno\s+suggestions?\b/,
            /\bwithout\s+recommendations?\b/,
            /\bwithout\s+suggestions?\b/,
            /\bstop\s+recommending\b/,
            /\bstop\s+suggesting\b/,
            /\bjust\s+answer\b/,
            /\b(?:i\s+)?don'?t\s+want\s+(?:any\s+)?(?:recommendations?|suggestions?|places)\b/,
            /\b(?:i\s+)?do\s+not\s+want\s+(?:any\s+)?(?:recommendations?|suggestions?|places)\b/,
            /\bdon'?t\s+(?:show|give)\s+(?:me\s+)?(?:places|recommendations?|suggestions?)\b/,
            /\bdo\s+not\s+(?:show|give)\s+(?:me\s+)?(?:places|recommendations?|suggestions?)\b/,
            /\bnot?\s+(?:looking\s+for|interested\s+in)\s+recommendations?\b/
        ];
        for (var i = 0; i < negationPatterns.length; i++) {
            if (negationPatterns[i].test(q)) {
                return false;
            }
        }

        // 2. Specific Informational / Attribute Inquiries (Suppress unless asking for alternatives/other places)
        var hasAlternativeIntent = /\b(?:other\s+places|places\s+near|nearby\s+places|places\s+around|alternatives?|similar\s+places)\b/.test(q);
        if (!hasAlternativeIntent) {
            var specificAttributePatterns = [
                /\b(?:history\s+of|what\s+happened\s+in|who\s+built|who\s+founded|architecture\s+of)\b/,
                /\b(?:entry\s+fee|ticket\s+price|ticket\s+cost|admission|how\s+much\s+does\s+it\s+cost|how\s+much\s+is\s+the\s+ticket)\b/,
                /\b(?:timings?|opening\s+hours?|closing\s+hours?|open\s+hours?|visiting\s+hours?|what\s+time)\b/,
                /\b(?:open\s+today|is\s+it\s+open|are\s+they\s+open|is\s+open|closed\s+today|open\s+now)\b/,
                /\b(?:how\s+do\s+i\s+reach|how\s+to\s+reach|how\s+far\s+is|distance\s+to|directions?\s+to|route\s+to)\b/,
                /\bbest\s+time\s+to\s+visit\s+(?!belagavi\b)(?:the\s+)?[a-z0-9\s]+\b/,
                /^tell\s+me\s+about\s+(?:the\s+)?(?!waterfalls|temples|forts|places|sanctuaries|spots|gems)[a-z0-9\s]+$/i,
                /^what\s+(?:is|about)\s+(?:the\s+)?(?!best|top|places|waterfalls|temples|forts)[a-z0-9\s]+$/i
            ];
            for (var j = 0; j < specificAttributePatterns.length; j++) {
                if (specificAttributePatterns[j].test(q)) {
                    return false;
                }
            }
        }

        // 3. Recommendation & Discovery Intent Triggers (Show recommendations)
        var discoveryPatterns = [
            /\brecommend\b/,
            /\brecommendations?\b/,
            /\bsuggest\b/,
            /\bsuggestions?\b/,
            /\bwhich\s+(?:places?|destinations?|spots?|attractions?|waterfalls?|temples?|forts?|sanctuar(?:y|ies))\b/,
            /\bwhich\s+(?:one|waterfall|temple|fort|sanctuary|place)\s+should\s+(?:i|we)\s+visit\b/,
            /\bwhat\s+(?:are\s+the\s+)?best\s+(?:places?|destinations?|spots?|waterfalls?|temples?|forts?|sanctuar(?:y|ies))\b/,
            /\bbest\s+places?\b/,
            /\btop\s+places?\b/,
            /\bplaces?\s+to\s+visit\b/,
            /\bspots?\s+to\s+visit\b/,
            /\bdestinations?\s+to\s+visit\b/,
            /\battractions?\s+to\s+visit\b/,
            /\bwhere\s+(?:should|can|could|to)\s+(?:i|we|one)\s+(?:go|visit|travel)\b/,
            /\bwhere\s+to\s+go\b/,
            /\bhidden\s+gems?\b/,
            /\boffbeat\s+places?\b/,
            /\bweekend\s+trip\b/,
            /\bday\s+trip\b/,
            /\btrip\s+ideas?\b/,
            /\bitinerary\b/,
            /\bplan\s+(?:a|my|our)\s+trip\b/,
            /\bplan\s+(?:an?\s+)?itinerary\b/,
            /\bwhat\s+(?:else|other\s+places)\s+can\s+i\s+visit\b/,
            /\bwhat\s+other\s+places\b/,
            /\bother\s+places\b/,
            /\bnearby\s+places\b/,
            /\bplaces\s+nearby\b/,
            /\bplaces\s+around\b/,
            /\balternatives?\s+(?:to|for)\b/,
            /\bplaces\s+like\b/,
            /\bgood\s+places?\s+for\b/,
            /\bthings\s+to\s+do\b/,
            /\bwhat\s+to\s+see\b/,
            /\bwhat\s+to\s+visit\b/,
            /\b(?:waterfalls?|temples?|forts?|sanctuar(?:y|ies)|lakes?|dams?|gardens?)\s+(?:in|near|around|across)\s+belagavi\b/
        ];

        for (var k = 0; k < discoveryPatterns.length; k++) {
            if (discoveryPatterns[k].test(q)) {
                return true;
            }
        }

        return false;
    }

    // ── Render API response ────────────────────────────────────
    function _renderAIResponse(data, userQuery) {
        var rawAnswer = (data.answer || '').trim();
        var dests  = Array.isArray(data.destinations) ? data.destinations : [];
        var isFallback = rawAnswer.indexOf('unable to process') !== -1 || rawAnswer.indexOf('try again later') !== -1;
        var showRecommendations = shouldShowRecommendations(userQuery, data);

        if (rawAnswer) {
            var displayAnswer = stripInternalIds(rawAnswer);
            var safeAnswer = _escHtml(displayAnswer).replace(/\n/g, '<br>');
            appendMessage('assistant', safeAnswer);
        }

        if (showRecommendations && dests.length > 0) {
            var msgs = _el('ai-messages');
            if (msgs) {
                var wrap = document.createElement('div');
                wrap.className = 'ai-dest-cards-wrap';
                if (dests.length > 1) {
                    var lbl = document.createElement('div');
                    lbl.className = 'ai-dest-label';
                    lbl.textContent = dests.length + ' Recommended Destinations';
                    wrap.appendChild(lbl);
                }
                dests.forEach(function(d) { wrap.appendChild(_renderDestCard(d)); });
                msgs.appendChild(wrap);
                _scrollBottom();
            }
        }

        // Render live web citations if returned by web research
        if (Array.isArray(data.web_sources) && data.web_sources.length > 0) {
            var msgsWeb = _el('ai-messages');
            if (msgsWeb) {
                var webWrap = document.createElement('div');
                webWrap.className = 'ai-web-sources-container';
                var webTitle = document.createElement('span');
                webTitle.className = 'text-muted small me-1';
                webTitle.innerHTML = '<i class="fa-solid fa-globe fa-xs me-1"></i>Web Sources:';
                webWrap.appendChild(webTitle);
                data.web_sources.forEach(function(ws) {
                    var link = document.createElement('a');
                    link.className = 'ai-web-source-pill';
                    link.href = ws.url;
                    link.target = '_blank';
                    link.rel = 'noopener noreferrer';
                    link.innerHTML = _escHtml(ws.domain || ws.title || 'Source') + ' <i class="fa-solid fa-arrow-up-right-from-square fa-2xs"></i>';
                    webWrap.appendChild(link);
                });
                msgsWeb.appendChild(webWrap);
                _scrollBottom();
            }
        }

        if (isFallback && dests.length > 0) {
            var msgs2 = _el('ai-messages');
            if (msgs2) {
                var hint = document.createElement('div');
                hint.className = 'ai-msg-row ai-msg-assistant';
                hint.innerHTML =
                    '<div class="ai-bubble-avatar" aria-hidden="true"><i class="fa-solid fa-robot"></i></div>' +
                    '<div class="ai-bubble ai-bubble-assistant ai-fallback-hint">' +
                    '<i class="fa-solid fa-circle-info me-1 text-primary"></i>' +
                    'The AI is experiencing high demand right now. Showing closest matches from our database.' +
                    '</div>';
                msgs2.appendChild(hint);
                _scrollBottom();
            }
        }

        if (!rawAnswer && dests.length === 0) {
            appendMessage('assistant',
                '<i class="fa-solid fa-circle-info me-1 text-primary"></i>' +
                "I don't have specific information about that. Try asking about Belagavi waterfalls, forts, temples, or nature spots!");
        }
    }

    // ── Send message ───────────────────────────────────────────
    async function sendAIMessage(message) {
        if (_isLoading) return;

        var input = _el('ai-input');
        if ((!message || typeof message !== 'string') && input) {
            message = input.value;
        }
        message = (message || '').trim();
        if (!message) return;

        if (input) input.value = '';

        _isLoading = true;
        var sendBtn = _el('ai-send-btn');
        if (sendBtn) {
            sendBtn.disabled = true;
            sendBtn.style.opacity = '0.5';
        }

        appendMessage('user', message);
        _updateChatState();
        showLoading();

        // ── Chat History: Save user message & create/update chat ───
        const uid = _getCurrentUid();
        const db = _getFirestore();
        const fm = _getFirestoreMethods();
        const isFirstMessage = _isNewChat || !_activeChatId || _activeChatId.startsWith('local_');
        let currentChatId = _activeChatId;

        if (uid && db && fm) {
            try {
                console.log('[AI History] AI HISTORY UID:', uid);
                if (isFirstMessage) {
                    const title = generateChatTitle(message);
                    currentChatId = 'chat_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
                    _activeChatId = currentChatId;
                    _isNewChat = false;
                    try { sessionStorage.setItem('bt_ai_active_chat_' + uid, currentChatId); } catch(e) {}

                    const newChatMeta = { id: _activeChatId, title: title, createdAt: new Date(), updatedAt: new Date() };

                    const chatDocRef = fm.doc(db, 'users', uid, 'ai_chats', currentChatId);
                    console.log('[AI History] Awaiting Firestore WRITE: users/' + uid + '/ai_chats/' + currentChatId);
                    await fm.setDoc(chatDocRef, {
                        title: title,
                        createdAt: fm.serverTimestamp(),
                        updatedAt: fm.serverTimestamp()
                    });
                    console.log('[AI History] Chat document created successfully in Firestore');

                    _chatList.unshift(newChatMeta);
                    renderHistoryUI();
                } else {
                    const chatDocRef = fm.doc(db, 'users', uid, 'ai_chats', currentChatId);
                    await fm.setDoc(chatDocRef, { updatedAt: fm.serverTimestamp() }, { merge: true });
                    const idx = _chatList.findIndex(function(c) { return c.id === currentChatId; });
                    if (idx > -1) {
                        const item = _chatList.splice(idx, 1)[0];
                        item.updatedAt = new Date();
                        _chatList.unshift(item);
                        renderHistoryUI();
                    }
                }

                const msgDocId = 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
                const msgDocRef = fm.doc(db, 'users', uid, 'ai_chats', currentChatId, 'messages', msgDocId);
                console.log('[AI History] Awaiting Firestore WRITE message: users/' + uid + '/ai_chats/' + currentChatId + '/messages/' + msgDocId);
                await fm.setDoc(msgDocRef, {
                    role: 'user',
                    content: message,
                    timestamp: fm.serverTimestamp()
                });
                console.log('[AI History] User message saved successfully in Firestore');
            } catch (histErr) {
                console.error('[AI History] User message persistence error:', histErr);
            }
        }

        // Build history payload (last 8 turns)
        var historyPayload = _conversationHistory.slice(-8);

        // Destination context grounding
        var apiMessage = message;
        if (_currentDestinationContext && _currentDestinationContext.name) {
            var placeName = _currentDestinationContext.name;
            if (!message.toLowerCase().includes(placeName.toLowerCase())) {
                apiMessage = placeName + ': ' + message;
            }
        } else if (historyPayload.length === 0 && window.currentPlaceData && window.currentPlaceData.name) {
            var placeName = window.currentPlaceData.name;
            if (!message.toLowerCase().includes(placeName.toLowerCase())) {
                historyPayload = [
                    { role: 'user', content: 'I am viewing ' + placeName + '.' },
                    { role: 'assistant', content: 'I am ready to help you with ' + placeName + '!' }
                ];
            }
        }

        let resp;
        try {
            resp = await _fetchWithTimeout(
                `${AI_BACKEND_URL}/api/chat`,
                {
                    method:  'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body:    JSON.stringify({
                        message: apiMessage,
                        history: historyPayload
                    })
                },
                FETCH_TIMEOUT_MS
            );
        } catch (fetchErr) {
            hideLoading();
            console.error('[AI Assistant] Network/Fetch Error:', fetchErr);
            var userMsg;
            if (fetchErr.name === 'AbortError') {
                userMsg = 'The request timed out. The AI assistant may be busy — please try again in a moment.';
            } else if (!navigator.onLine) {
                userMsg = 'No internet connection detected. Please check your network and try again.';
            } else {
                userMsg = 'AI assistant is temporarily unavailable. Please check your network connection and try again.';
            }
            appendMessage('assistant', '<span class="ai-error-text"><i class="fa-solid fa-wifi-slash me-1"></i>' + userMsg + '</span>');
            return;
        } finally {
            _isLoading = false;
            if (sendBtn) {
                sendBtn.disabled = false;
                sendBtn.style.opacity = '1';
            }
            if (input) input.focus();
        }

        // Successfully contacted server — remove loading indicator
        hideLoading();

        if (!resp.ok) {
            var errDetail = await resp.text().catch(function() { return ''; });
            console.error('[Global AI Guide API Error]', {
                status: resp.status,
                statusText: resp.statusText,
                url: `${AI_BACKEND_URL}/api/chat`,
                body: errDetail
            });
            var errMsg = 'AI assistant returned an error (' + resp.status + '). Please try again.';
            if (resp.status === 422) errMsg = 'Your message could not be processed. Please rephrase and try again.';
            else if (resp.status >= 500) errMsg = 'AI service is temporarily busy. Please try again in a moment.';
            appendMessage('assistant', '<span class="ai-error-text"><i class="fa-solid fa-triangle-exclamation me-1"></i>' + errMsg + '</span>');
            return;
        }

        let data;
        try {
            data = await resp.json();
        } catch (jsonErr) {
            console.error('[AI Assistant] JSON Parse Error:', jsonErr);
            appendMessage('assistant', '<span class="ai-error-text"><i class="fa-solid fa-triangle-exclamation me-1"></i>Invalid response from AI server.</span>');
            return;
        }

        // Render AI response safely (a UI render exception will never show a false network error!)
        try {
            _renderAIResponse(data, message);

            if (data && data.answer) {
                _conversationHistory.push({ role: 'user', content: apiMessage });
                _conversationHistory.push({ role: 'assistant', content: data.answer });
                if (_conversationHistory.length > 10) {
                    _conversationHistory = _conversationHistory.slice(-10);
                }

                // ── Chat History: Save assistant response ────────────
                if (uid && db && fm && currentChatId && !currentChatId.startsWith('local_')) {
                    try {
                        const rawAnswer = (data.answer || '').trim();
                        const displayAnswer = stripInternalIds(rawAnswer);
                        const showRecs = shouldShowRecommendations(message, data);
                        const destsToSave = (showRecs && Array.isArray(data.destinations) && data.destinations.length > 0) ? data.destinations : null;
                        const sourcesToSave = (Array.isArray(data.web_sources) && data.web_sources.length > 0) ? data.web_sources : null;

                        const asstMsg = {
                            role: 'assistant',
                            content: displayAnswer || '',
                            timestamp: fm.serverTimestamp()
                        };
                        if (destsToSave) asstMsg.destinations = destsToSave;
                        if (sourcesToSave) asstMsg.web_sources = sourcesToSave;

                        const asstMsgId = 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
                        const asstDocRef = fm.doc(db, 'users', uid, 'ai_chats', currentChatId, 'messages', asstMsgId);
                        console.log('[AI History] Awaiting Firestore WRITE assistant message: users/' + uid + '/ai_chats/' + currentChatId + '/messages/' + asstMsgId);
                        await fm.setDoc(asstDocRef, asstMsg);

                        const chatDocRef = fm.doc(db, 'users', uid, 'ai_chats', currentChatId);
                        await fm.setDoc(chatDocRef, { updatedAt: fm.serverTimestamp() }, { merge: true });
                        console.log('[AI History] Assistant message and chat updatedAt saved successfully in Firestore');
                    } catch (saveErr) {
                        console.error('[AI History] Assistant message persistence error:', saveErr);
                    }
                }
            }
        } catch (renderErr) {
            console.error('[AI Assistant] UI Render Error:', renderErr);
        }
    }
    window.sendAIMessage = sendAIMessage;

    // ── Chips ──────────────────────────────────────────────────
    function _renderChips() {
        var wrap = _el('ai-suggestion-chips') || _el('ai-chips');
        if (!wrap) return;

        if (hasUserMessages()) {
            wrap.style.display = 'none';
            return;
        }

        wrap.style.display = 'flex';
        wrap.innerHTML = '';
        var items = (_currentDestinationContext && _currentDestinationContext.name)
            ? getContextualSuggestions(_currentDestinationContext.name)
            : DEFAULT_SUGGESTIONS;

        items.forEach(function(item) {
            var chip = document.createElement('button');
            chip.className = 'ai-chip';
            chip.type = 'button';
            chip.textContent = item.label;
            chip.addEventListener('click', function(e) {
                e.preventDefault();
                var inp = _el('ai-input');
                if (inp) inp.value = item.query;
                sendAIMessage(item.query);
            });
            wrap.appendChild(chip);
        });
    }

    // ── Context Management ─────────────────────────────────────
    function setAIGuideContext(place) {
        if (!place) return;
        _currentDestinationContext = place;
        _conversationHistory = [];

        var msgs = _el('ai-messages');
        if (msgs) msgs.innerHTML = '';

        var indicator = _el('ai-context-indicator');
        var nameSpan  = _el('ai-context-name');
        if (indicator) indicator.style.display = 'flex';
        if (nameSpan) nameSpan.textContent = 'Exploring ' + place.name;

        var titleEl = _el('ai-page-title');
        var subEl   = _el('ai-page-sub');
        if (titleEl) titleEl.textContent = 'AI Travel Guide';
        if (subEl) subEl.textContent = 'About ' + place.name + ' — I can help you learn more about ' + place.name + '.';

        var inputEl = _el('ai-input');
        if (inputEl) {
            inputEl.placeholder = 'Ask about ' + place.name + '…';
            inputEl.value = '';
        }

        _updateChatState();
        _renderChips();

        appendMessage('assistant',
            '<strong>I can help you learn more about ' + _escHtml(place.name) + '.</strong><br><br>' +
            'Ask me anything about its history, timings, entry fee, transport, or travel tips. You can select one of the suggested topics above or type your own question below!'
        );
    }
    window.setAIGuideContext = setAIGuideContext;

    function clearAIGuideContext() {
        _currentDestinationContext = null;

        var indicator = _el('ai-context-indicator');
        if (indicator) indicator.style.display = 'none';

        var titleEl = _el('ai-page-title');
        var subEl   = _el('ai-page-sub');
        if (titleEl) titleEl.textContent = 'AI Travel Guide';
        if (subEl) subEl.textContent = "Ask anything about Belagavi's destinations, history, and travel tips.";

        var inputEl = _el('ai-input');
        if (inputEl) {
            inputEl.placeholder = 'Ask about Belagavi…';
            inputEl.value = '';
        }

        _updateChatState();
        _renderChips();
    }
    window.clearAIGuideContext = clearAIGuideContext;

    // ── Auth & History Listener ────────────────────────────────
    function initAuthHistoryListener() {
        async function onUserChange(user) {
            console.log('[AI History] onUserChange triggered. User:', user ? user.uid : 'null');
            if (user && user.uid) {
                const isUserSwitch = _currentUid && _currentUid !== user.uid;
                _currentUid = user.uid;
                if (isUserSwitch) {
                    _chatList = [];
                    _activeChatId = null;
                }
                await loadUserChatHistory(_currentUid);
                try {
                    const savedActiveId = sessionStorage.getItem('bt_ai_active_chat_' + _currentUid);
                    if (savedActiveId && _chatList.some(function(c) { return c.id === savedActiveId; })) {
                        if (_activeChatId !== savedActiveId) {
                            await openChat(savedActiveId);
                        }
                    } else if (!_activeChatId || _activeChatId.startsWith('local_')) {
                        startNewChat();
                    }
                } catch(e) {
                    if (!_activeChatId || _activeChatId.startsWith('local_')) {
                        startNewChat();
                    }
                }
            } else {
                _currentUid = null;
                _chatList = [];
                _activeChatId = null;
                renderHistoryUI();
                startNewChat();
            }
        }
        window.onAIAuthChange = onUserChange;

        function tryAttach() {
            if (window.fbAuth && window.fbAuthMethods && typeof window.fbAuthMethods.onAuthStateChanged === 'function') {
                window.fbAuthMethods.onAuthStateChanged(window.fbAuth, function(user) {
                    onUserChange(user);
                });
                if (window.fbAuth.currentUser) {
                    onUserChange(window.fbAuth.currentUser);
                } else if (window.currentUser) {
                    onUserChange(window.currentUser);
                }
                return true;
            }
            return false;
        }

        if (!tryAttach()) {
            var retries = 0;
            var interval = setInterval(function() {
                retries++;
                if (tryAttach() || retries > 50) {
                    clearInterval(interval);
                }
            }, 100);
        }
    }

    // ── Init ───────────────────────────────────────────────────
    function initAIAssistant() {
        if (!_activeChatId) {
            startNewChat();
        }

        const uid = _getCurrentUid();
        if (uid && _chatList.length === 0) {
            loadUserChatHistory(uid);
        }

        _updateChatState();
        _renderChips();
        renderHistoryUI();

        if (_initialized) return;
        _initialized = true;

        initAuthHistoryListener();

        var sendBtn = _el('ai-send-btn');
        if (sendBtn) {
            sendBtn.onclick = function(e) {
                if (e) e.preventDefault();
                var inp = _el('ai-input');
                sendAIMessage(inp ? inp.value : '');
            };
        }

        var inputEl = _el('ai-input');
        if (inputEl) {
            inputEl.onkeydown = function(e) {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendAIMessage(inputEl.value);
                }
            };
        }

        var newChatBtnSidebar = _el('ai-new-chat-btn');
        if (newChatBtnSidebar) {
            newChatBtnSidebar.onclick = function(e) {
                if (e) e.preventDefault();
                startNewChat();
            };
        }

        var closeBtn = _el('ai-history-close-btn');
        if (closeBtn) {
            closeBtn.onclick = function(e) {
                if (e) e.preventDefault();
                closeChatHistoryDrawer();
            };
        }

        var backdrop = _el('ai-history-backdrop');
        if (backdrop) {
            backdrop.onclick = function(e) {
                if (e) e.preventDefault();
                closeChatHistoryDrawer();
            };
        }

        // Greeting on first open
        var msgs = _el('ai-messages');
        if (msgs && msgs.children.length === 0) {
            appendMessage('assistant',
                '<strong>Hello! I\'m your Belagavi Tourism AI Assistant.</strong><br><br>' +
                'I can help you discover waterfalls, forts, temples, wildlife sanctuaries, and more across the Belagavi district.<br><br>' +
                'Try one of the suggestions above, or ask me anything about Belagavi tourism!'
            );
        }
    }
    window.initAIAssistant = initAIAssistant;

    // Run automatically when script is loaded or DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAIAssistant);
    } else {
        initAIAssistant();
    }

})();

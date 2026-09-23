// ============================================================
// AI TOURISM ASSISTANT MODULE
// Calls FastAPI backend at https://belagavi-tourism-yuvaraj21.vercel.app
// No Gemini credentials — all API calls go through the backend.
// ============================================================

(function () {
    'use strict';

    // ── Configuration ─────────────────────────────────────────
    const AI_BACKEND_URL = 'https://belagavi-tourism-yuvaraj21.vercel.app';
    const FETCH_TIMEOUT_MS = 30000; // 30 s (Gemini retries can add ~6 s)

    const SUGGESTED_QUESTIONS = [
        'Best waterfalls to visit?',
        'Tell me about Belagavi Fort',
        'Best places for a short trip?',
        'Which temples can I visit?',
        'Places to visit near Khanapur?'
    ];

    // ── State ──────────────────────────────────────────────────
    let _initialized = false;
    let _isLoading   = false;
    let _loadingEl   = null;

    // ── DOM helpers ────────────────────────────────────────────
    function _el(id) { return document.getElementById(id); }

    function _escHtml(str) {
        const d = document.createElement('div');
        d.appendChild(document.createTextNode(str || ''));
        return d.innerHTML;
    }

    function _scrollBottom() {
        const msgs = _el('ai-messages');
        if (msgs) msgs.scrollTop = msgs.scrollHeight;
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
            '<div class="ai-bubble ai-bubble-assistant ai-typing-dots"><span></span><span></span><span></span></div>';
        msgs.appendChild(_loadingEl);
        _scrollBottom();
    }

    function hideLoading() {
        if (_loadingEl) { _loadingEl.remove(); _loadingEl = null; }
    }

    // ── Destination card ───────────────────────────────────────
    function _renderDestCard(dest) {
        const id       = dest.place_id;
        const name     = _escHtml(dest.name     || 'Destination');
        const category = _escHtml(dest.category || '');
        const city     = _escHtml(dest.city     || '');
        const fee      = _escHtml(dest.entry_fee     || '');
        const duration = _escHtml(dest.visit_duration || '');
        const folder   = dest.folder_name || 'place';
        const reason   = dest.reason ? _escHtml(dest.reason) : '';
        const icons    = { waterfall:'💧', temple:'🛕', fort:'🏰', nature:'🌿',
                           wildlife:'🦅', lake:'🏞️', dam:'🌊', garden:'🌳', park:'🌳' };
        const emoji    = icons[(category).toLowerCase()] || '📍';

        const card = document.createElement('div');
        card.className = 'ai-dest-card';
        card.setAttribute('role', 'button');
        card.setAttribute('tabindex', '0');
        card.setAttribute('aria-label', 'View details for ' + dest.name);

        var feeHtml      = fee      ? '<div class="ai-dest-tag"><i class="fa-solid fa-ticket fa-xs me-1"></i>' + fee + '</div>' : '';
        var durationHtml = duration ? '<div class="ai-dest-tag"><i class="fa-regular fa-clock fa-xs me-1"></i>' + duration + '</div>' : '';
        var reasonHtml   = reason   ? '<div class="ai-dest-reason">' + reason + '</div>' : '';
        var metaSep      = city ? ' · ' + city : '';

        card.innerHTML =
            '<img class="ai-dest-img" src="/static/images/' + folder + '/1.jpg" alt="' + name + '" loading="lazy" onerror="this.src=\'/static/icon-192.png\'">' +
            '<div class="ai-dest-body">' +
                '<div class="ai-dest-name">' + emoji + ' ' + name + '</div>' +
                '<div class="ai-dest-meta">' + category + metaSep + '</div>' +
                feeHtml + durationHtml + reasonHtml +
            '</div>' +
            '<div class="ai-dest-arrow"><i class="fa-solid fa-chevron-right"></i></div>';

        function _go() {
            if (typeof navigateToPlace === 'function') {
                navigateToPlace(id);
            } else {
                window.location.hash = '#place-' + id;
            }
        }
        card.addEventListener('click', _go);
        card.addEventListener('keydown', function(e) { if (e.key === 'Enter' || e.key === ' ') _go(); });
        return card;
    }

    // ── Render API response ────────────────────────────────────
    function _renderAIResponse(data) {
        var answer = (data.answer || '').trim();
        var dests  = Array.isArray(data.destinations) ? data.destinations : [];
        var isFallback = answer.indexOf('unable to process') !== -1 || answer.indexOf('try again later') !== -1;

        if (answer) {
            var safeAnswer = _escHtml(answer).replace(/\n/g, '<br>');
            appendMessage('assistant', safeAnswer);
        }

        if (dests.length > 0) {
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

        if (!answer && dests.length === 0) {
            appendMessage('assistant',
                '<i class="fa-solid fa-circle-info me-1 text-primary"></i>' +
                "I don't have specific information about that. Try asking about Belagavi waterfalls, forts, temples, or nature spots!");
        }
    }

    // ── Send message ───────────────────────────────────────────
    async function sendAIMessage(message) {
        if (_isLoading) return;
        message = (message || '').trim();
        if (!message) return;

        var input = _el('ai-input');
        if (input) input.value = '';

        _isLoading = true;
        var sendBtn = _el('ai-send-btn');
        if (sendBtn) sendBtn.disabled = true;

        appendMessage('user', message);
        showLoading();

        try {
            var resp = await _fetchWithTimeout(
                AI_BACKEND_URL + '/api/chat',
                {
                    method:  'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body:    JSON.stringify({ message: message })
                },
                FETCH_TIMEOUT_MS
            );

            hideLoading();

            if (!resp.ok) {
                var errMsg = 'AI assistant returned an unexpected error. Please try again.';
                if (resp.status === 422) errMsg = 'Your message could not be processed. Please rephrase and try again.';
                appendMessage('assistant', '<span class="ai-error-text"><i class="fa-solid fa-triangle-exclamation me-1"></i>' + errMsg + '</span>');
                return;
            }

            var data = await resp.json();
            _renderAIResponse(data);

        } catch (err) {
            hideLoading();
            var userMsg;
            if (err.name === 'AbortError') {
                userMsg = 'The request timed out. The AI assistant may be busy — please try again in a moment.';
            } else if (!navigator.onLine) {
                userMsg = 'No internet connection detected. Please check your network and try again.';
            } else {
                userMsg = 'AI assistant is temporarily unavailable. Please make sure the backend server is running and try again.';
            }
            appendMessage('assistant', '<span class="ai-error-text"><i class="fa-solid fa-wifi-slash me-1"></i>' + userMsg + '</span>');
        } finally {
            _isLoading = false;
            if (sendBtn) sendBtn.disabled = false;
            if (input) input.focus();
        }
    }
    window.sendAIMessage = sendAIMessage;

    // ── Chips ──────────────────────────────────────────────────
    function _renderChips() {
        var wrap = _el('ai-chips');
        if (!wrap) return;
        wrap.innerHTML = '';
        SUGGESTED_QUESTIONS.forEach(function(q) {
            var chip = document.createElement('button');
            chip.className = 'ai-chip';
            chip.type = 'button';
            chip.textContent = q;
            chip.addEventListener('click', function() {
                var inp = _el('ai-input');
                if (inp) inp.value = '';
                sendAIMessage(q);
            });
            wrap.appendChild(chip);
        });
    }

    // ── Init ───────────────────────────────────────────────────
    function initAIAssistant() {
        if (_initialized) return;
        _initialized = true;

        _renderChips();

        var sendBtn = _el('ai-send-btn');
        if (sendBtn) {
            sendBtn.addEventListener('click', function() {
                var inp = _el('ai-input');
                if (inp) sendAIMessage(inp.value);
            });
        }

        var inputEl = _el('ai-input');
        if (inputEl) {
            inputEl.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendAIMessage(inputEl.value);
                }
            });
        }

        // Greeting on first open
        var msgs = _el('ai-messages');
        if (msgs && msgs.children.length === 0) {
            appendMessage('assistant',
                '<strong>Hello! I\'m your Belagavi Tourism AI Assistant.</strong><br><br>' +
                'I can help you discover waterfalls, forts, temples, wildlife sanctuaries, and more across the Belagavi district.<br><br>' +
                'Try one of the suggestions below, or ask me anything about Belagavi tourism!'
            );
        }
    }
    window.initAIAssistant = initAIAssistant;

})();

/**
 * reviews.js — Firestore-backed Reviews & Ratings Module
 * -------------------------------------------------------
 * Handles: star ratings, review submission, and review rendering
 * for each Place Details page.
 *
 * Firestore Collections:
 *   ratings/{placeId}/votes/{userId}  → { rating: 1-5, timestamp }
 *   reviews/{placeId}/comments/{auto} → { userId, username, text, rating, timestamp }
 *
 * Usage: include this script on details.html, call initReviews(placeId)
 */

// Firebase is initialized in details.html via the module script block.
// This file exports helpers that are called after Firebase is ready.

(function () {
    'use strict';

    // ── State ────────────────────────────────────────────────────────────────
    let _db = null;
    let _auth = null;
    let _placeId = null;
    let _selectedRating = 0;

    // ── Public Init ──────────────────────────────────────────────────────────
    window.initReviews = function (firestoreDb, firebaseAuth, placeId) {
        _db = firestoreDb;
        _auth = firebaseAuth;
        _placeId = String(placeId);
        _loadAverageRating();
        _loadReviews();
        _bindStarHover();
    };

    // ── Star Rating Widget ────────────────────────────────────────────────────
    function _bindStarHover() {
        const stars = document.querySelectorAll('.review-star');
        stars.forEach(star => {
            star.addEventListener('mouseenter', () => _highlightStars(+star.dataset.value));
            star.addEventListener('mouseleave', () => _highlightStars(_selectedRating));
            star.addEventListener('click', () => {
                _selectedRating = +star.dataset.value;
                _highlightStars(_selectedRating);
            });
        });
    }

    function _highlightStars(upTo) {
        document.querySelectorAll('.review-star').forEach(s => {
            s.classList.toggle('star-filled', +s.dataset.value <= upTo);
        });
    }

    // ── Load Average Rating ────────────────────────────────────────────────────
    async function _loadAverageRating() {
        if (!_db) return;
        try {
            const { collection, getDocs } = await import('https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js');
            const snap = await getDocs(collection(_db, 'ratings', _placeId, 'votes'));
            if (snap.empty) return;
            const total = snap.docs.reduce((sum, d) => sum + (d.data().rating || 0), 0);
            const avg = total / snap.size;
            _renderAvgRating(avg, snap.size);
        } catch (e) {
            console.warn('Could not load ratings:', e);
        }
    }

    function _renderAvgRating(avg, count) {
        const el = document.getElementById('avg-rating-display');
        if (!el) return;
        const full = Math.round(avg);
        let stars = '';
        for (let i = 1; i <= 5; i++) {
            stars += `<i class="fa-${i <= full ? 'solid' : 'regular'} fa-star text-warning" style="font-size:1rem"></i>`;
        }
        el.innerHTML = `
            <div class="d-flex align-items-center gap-2">
                <div>${stars}</div>
                <span class="fw-bold text-dark">${avg.toFixed(1)}</span>
                <span class="text-muted small">(${count} ${count === 1 ? 'review' : 'reviews'})</span>
            </div>`;
    }

    // ── Load Reviews ──────────────────────────────────────────────────────────
    async function _loadReviews() {
        if (!_db) return;
        const container = document.getElementById('reviews-list');
        if (!container) return;
        try {
            const { collection, getDocs, query, orderBy, limit } = await import('https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js');
            const q = query(
                collection(_db, 'reviews', _placeId, 'comments'),
                orderBy('timestamp', 'desc'),
                limit(20)
            );
            const snap = await getDocs(q);
            if (snap.empty) {
                container.innerHTML = `<p class="text-muted text-center py-3 small">No reviews yet. Be the first!</p>`;
                return;
            }
            container.innerHTML = '';
            snap.docs.forEach(doc => _renderReview(doc.data()));
        } catch (e) {
            console.warn('Could not load reviews:', e);
            container.innerHTML = `<p class="text-muted small text-center">Reviews unavailable.</p>`;
        }
    }

    function _renderReview(data) {
        const container = document.getElementById('reviews-list');
        if (!container) return;
        const initials = (data.username || 'U').substring(0, 1).toUpperCase();
        const date = data.timestamp?.toDate
            ? data.timestamp.toDate().toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
            : 'Recently';
        let stars = '';
        for (let i = 1; i <= 5; i++) {
            stars += `<i class="fa-${i <= (data.rating || 0) ? 'solid' : 'regular'} fa-star text-warning" style="font-size:0.75rem"></i>`;
        }
        const el = document.createElement('div');
        el.className = 'review-card mb-3 p-3 rounded-4 border bg-white shadow-sm';
        el.innerHTML = `
            <div class="d-flex align-items-start gap-3">
                <div class="review-avatar bg-primary text-white rounded-circle d-flex align-items-center justify-content-center fw-bold flex-shrink-0"
                     style="width:40px;height:40px;font-size:1rem">${initials}</div>
                <div class="flex-grow-1">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <span class="fw-bold small text-dark">${_escHtml(data.username || 'Anonymous')}</span>
                        <small class="text-muted" style="font-size:0.72rem">${date}</small>
                    </div>
                    <div class="mb-1">${stars}</div>
                    <p class="mb-0 text-dark" style="font-size:0.88rem;line-height:1.5">${_escHtml(data.text || '')}</p>
                </div>
            </div>`;
        container.appendChild(el);
    }

    // ── Submit Review ──────────────────────────────────────────────────────────
    window.submitReview = async function () {
        if (!_db || !_auth) {
            _showReviewMsg('error', 'Not connected to database. Please refresh.');
            return;
        }
        const user = _auth.currentUser;
        if (!user) {
            _showReviewMsg('error', 'You must be logged in to leave a review.');
            return;
        }
        const text = (document.getElementById('review-text') || {}).value?.trim();
        if (!text) { _showReviewMsg('error', 'Please write something before submitting.'); return; }
        if (!_selectedRating) { _showReviewMsg('error', 'Please select a star rating.'); return; }

        const btn = document.getElementById('submit-review-btn');
        if (btn) { btn.disabled = true; btn.textContent = 'Posting...'; }

        try {
            const { collection, addDoc, setDoc, doc, serverTimestamp } = await import('https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js');

            // Save review
            await addDoc(collection(_db, 'reviews', _placeId, 'comments'), {
                userId: user.uid,
                username: user.displayName || user.email?.split('@')[0] || 'Traveller',
                text,
                rating: _selectedRating,
                timestamp: serverTimestamp()
            });

            // Save/update this user's rating vote
            await setDoc(doc(_db, 'ratings', _placeId, 'votes', user.uid), {
                rating: _selectedRating,
                timestamp: serverTimestamp()
            });

            _showReviewMsg('success', '✅ Review posted! Thank you.');
            document.getElementById('review-text').value = '';
            _selectedRating = 0;
            _highlightStars(0);
            // Reload both list and average
            document.getElementById('reviews-list').innerHTML = '';
            await _loadReviews();
            await _loadAverageRating();
        } catch (e) {
            _showReviewMsg('error', 'Failed to submit. Please try again.');
            console.error(e);
        } finally {
            if (btn) { btn.disabled = false; btn.textContent = 'Post Review'; }
        }
    };

    // ── Utilities ──────────────────────────────────────────────────────────────
    function _showReviewMsg(type, msg) {
        const el = document.getElementById('review-feedback');
        if (!el) return;
        el.className = `alert py-2 small text-center rounded-3 alert-${type === 'error' ? 'danger' : 'success'}`;
        el.textContent = msg;
        el.style.display = 'block';
        setTimeout(() => { el.style.display = 'none'; }, 4000);
    }

    function _escHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
})();

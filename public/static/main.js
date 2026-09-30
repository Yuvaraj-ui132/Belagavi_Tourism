// ============================================================
// NAVIGATION & SEARCH
// ============================================================

function goHome() {
    show('home');
    const searchContainer = document.querySelector(".search-container");
    if (searchContainer) searchContainer.style.display = "block";
}

function show(id) {
    // Normalize aliases
    let targetTab = null;
    if (id === 'settings') {
        id = 'profile';
        targetTab = 'settings';
    } else if (id === 'budget') {
        id = 'expense';
    }

    if (!document.getElementById('home')) {
        const hash = id === 'expense' ? 'budget' : (targetTab === 'settings' ? 'settings' : id);
        window.location.href = `/planner#${hash}`;
        return;
    }

    // Hide all sections
    document.querySelectorAll('.section').forEach(s => {
        s.classList.remove('active-section');
        s.style.display = 'none';
    });

    const target = document.getElementById(id);
    if (target) {
        target.classList.add('active-section');
        target.style.display = (id === 'ai-assistant') ? 'flex' : 'block';
    }

    // Update desktop nav
    document.querySelectorAll('.nav-link-btn').forEach(b => b.classList.remove('active'));
    const dNavBtn = document.getElementById('nav-' + id);
    if (dNavBtn) dNavBtn.classList.add('active');

    // Update mobile nav
    document.querySelectorAll('.bottom-nav button').forEach(b => b.classList.remove('active'));
    const mNavBtn = document.getElementById('mob-nav-' + id);
    if (mNavBtn) mNavBtn.classList.add('active');

    if (id === 'profile') {
        refreshProfileStats();
        if (targetTab === 'settings' && typeof switchProfileTab === 'function') {
            switchProfileTab('settings');
        }
    }
    if (id === 'wishlist') {
        if (typeof renderWishlistPage === 'function') renderWishlistPage();
    }
    if (id === 'expense') {
        populateDestinationSelector();
        if (typeof updateExpenseUI === 'function') updateExpenseUI();
    }

    if (id === 'explore') {
        if (!exploreMap) {
            initLeafletMap();
        }
        setTimeout(() => { if (exploreMap) exploreMap.invalidateSize(); }, 150);
    }

    if (id === 'ai-assistant') {
        if (typeof initAIAssistant === 'function') initAIAssistant();
        if (typeof window.refreshAIChatHistory === 'function') window.refreshAIChatHistory();
    }

    const hash = id === 'expense' ? 'budget' : id;
    if (history.pushState) {
        history.pushState(null, null, `#${hash}`);
    } else {
        window.location.hash = `#${hash}`;
    }

    window.scrollTo(0, 0);
}

let currentHomeCategory = 'all';

function filterByCategory(category, btnEl) {
    currentHomeCategory = category;
    if (btnEl) {
        document.querySelectorAll('.hero-pill').forEach(b => b.classList.remove('active'));
        btnEl.classList.add('active');
    }
    filterPlaces();
}

function filterPlaces() {
    const searchEl = document.getElementById("search");
    const q = (searchEl ? searchEl.value : '').toLowerCase().trim();
    let visibleCount = 0;
    document.querySelectorAll("#home .col-12").forEach(item => {
        const nameAttr = (item.getAttribute('data-name') || '').toLowerCase();
        const catAttr = (item.getAttribute('data-category') || '').toLowerCase();
        const nameEl = item.querySelector("h3");
        const name = nameAttr || (nameEl ? nameEl.innerText.toLowerCase() : '');
        
        const matchesQuery = !q || name.includes(q);
        const matchesCat = currentHomeCategory === 'all' || catAttr.includes(currentHomeCategory.toLowerCase());
        const show = matchesQuery && matchesCat;
        item.style.display = show ? 'block' : 'none';
        if (show) visibleCount++;
    });

    const badge = document.getElementById('destination-count-badge');
    if (badge) badge.innerText = `${visibleCount} Destinations`;
}

// Synchronize and update visual state of wishlist buttons across the app (icon-only for destination cards)
function updateWishlistButtons(placeId, isSaved, name) {
    if (!window.userWishlistPlaceIds) window.userWishlistPlaceIds = new Set();
    if (isSaved) {
        window.userWishlistPlaceIds.add(String(placeId));
    } else {
        window.userWishlistPlaceIds.delete(String(placeId));
    }

    const buttons = document.querySelectorAll(`.save-btn-${placeId}`);
    buttons.forEach(btn => {
        const destName = name || btn.getAttribute('data-name') || 'destination';
        btn.classList.toggle('saved', isSaved);
        btn.setAttribute('aria-pressed', isSaved ? 'true' : 'false');
        btn.setAttribute('aria-label', isSaved ? `Remove ${destName} from saved places` : `Save ${destName}`);
        btn.setAttribute('title', isSaved ? `Remove ${destName} from saved places` : `Save ${destName}`);

        if (btn.classList.contains('card-save')) {
            // Destination card wishlist button: ICON ONLY, NO TEXT
            btn.innerHTML = `<i class="fa-${isSaved ? 'solid' : 'regular'} fa-heart"></i>`;
        } else if (btn.classList.contains('detail-btn-save')) {
            // Detail page action button
            btn.innerHTML = `<i class="fa-${isSaved ? 'solid' : 'regular'} fa-heart me-1"></i> ${isSaved ? 'Saved' : 'Save'}`;
        } else {
            btn.innerHTML = `<i class="fa-${isSaved ? 'solid' : 'regular'} fa-heart"></i>`;
        }
    });
}
window.updateWishlistButtons = updateWishlistButtons;

function syncWishlistCardStates() {
    const savedIds = window.userWishlistPlaceIds || new Set(
        Array.from(document.querySelectorAll('[id^="wish-item-"]'))
             .map(el => el.id.replace('wish-item-', ''))
    );
    document.querySelectorAll('.card-save').forEach(btn => {
        const match = btn.className.match(/save-btn-(\d+)/);
        if (!match) return;
        const pId = match[1];
        const isSaved = savedIds.has(String(pId));
        const name = btn.getAttribute('data-name') || 'destination';
        btn.classList.toggle('saved', isSaved);
        btn.setAttribute('aria-pressed', isSaved ? 'true' : 'false');
        btn.setAttribute('aria-label', isSaved ? `Remove ${name} from saved places` : `Save ${name}`);
        btn.setAttribute('title', isSaved ? `Remove ${name} from saved places` : `Save ${name}`);
        btn.innerHTML = `<i class="fa-${isSaved ? 'solid' : 'regular'} fa-heart"></i>`;
    });
}
window.syncWishlistCardStates = syncWishlistCardStates;

function toggleWishlist(btn, placeId, name, folder) {
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        // Direct Firebase-Only Client Firestore Execution (Phase 1)
        toggleWishlistFirestore(btn, placeId, name, folder);
        return;
    }

    // Legacy Flask Proxy Execution (Localhost Fallback)
    const container = document.getElementById('wishlist-items');
    const existing = document.getElementById(`wish-item-${placeId}`);

    if (existing) {
        fetch(`/remove_from_wishlist/${placeId}`, { method: 'POST' });
        existing.remove();
        if (container && container.querySelectorAll('.card, .wishlist-card').length === 0) {
            renderWishlistEmptyState();
        }
        updateWishlistButtons(placeId, false, name);
    } else {
        fetch(`/add_to_wishlist/${placeId}`, { method: 'POST' });
        const div = document.createElement('div');
        div.id = `wish-item-${placeId}`;
        div.className = "wishlist-card";
        div.innerHTML = `
            <a href="#place-${placeId}" class="wishlist-link" onclick="navigateToPlace(${placeId}); return false;">
                <img src="/static/images/${folder}/1.jpg" loading="lazy" class="wishlist-thumb" onerror="this.src='/static/icon-192.png'" alt="${name}">
                <div class="wishlist-info">
                    <h6 class="wishlist-title">${name}</h6>
                    <span class="wishlist-subtitle"><i class="fa-solid fa-location-dot me-1 text-primary"></i> Belagavi Landmark</span>
                </div>
            </a>
            <div class="wishlist-actions">
                <button class="btn btn-sm btn-outline-danger btn-wishlist-remove" onclick="toggleWishlist(null, ${placeId})" title="Remove from saved places">
                    <i class="fa-regular fa-trash-can"></i>
                </button>
            </div>`;
        if (container) {
            const emptyEl = container.querySelector('.saved-empty, .wishlist-loading-state');
            if (emptyEl) emptyEl.remove();
            container.appendChild(div);
        }
        updateWishlistButtons(placeId, true, name);
    }
}

// Direct client-side Firestore toggling with robust document query & deletion
async function toggleWishlistFirestore(btn, placeId, name, folder) {
    if (!window.fbAuth?.currentUser || !window.fbDb) return;
    const uid = window.fbAuth.currentUser.uid;
    const docId = `${uid}_${placeId}`;
    const docRef = window.fbFirestoreMethods.doc(window.fbDb, "wishlist", docId);
    const container = document.getElementById('wishlist-items');
    const existing = document.getElementById(`wish-item-${placeId}`);

    try {
        // Query to find any matching wishlist documents for this user & placeId
        const q = window.fbFirestoreMethods.query(
            window.fbFirestoreMethods.collection(window.fbDb, "wishlist"),
            window.fbFirestoreMethods.where("user_id", "==", uid)
        );
        const querySnapshot = await window.fbFirestoreMethods.getDocs(q);
        
        const matchingDocs = querySnapshot.docs.filter(docSnap => {
            const data = docSnap.data();
            const pId = data.placeId ?? data.place_id ?? docSnap.id.split('_').pop();
            return String(pId) === String(placeId) || docSnap.id === docId;
        });

        if (existing || matchingDocs.length > 0) {
            // DELETE: Delete all matching documents from Firestore
            for (const d of matchingDocs) {
                await window.fbFirestoreMethods.deleteDoc(d.ref).catch(e => console.warn(e));
            }
            // Also attempt docRef deletion
            await window.fbFirestoreMethods.deleteDoc(docRef).catch(() => {});

            if (existing) existing.remove();

            // Reset heart button state without text
            updateWishlistButtons(placeId, false, name);

            if (window.userWishlistPlaceIds) {
                window.userWishlistPlaceIds.delete(String(placeId));
            }
            if (window.cachedWishlistPlaces) {
                window.cachedWishlistPlaces = window.cachedWishlistPlaces.filter(it => String(it.pid) !== String(placeId));
            }

            const remaining = container ? container.querySelectorAll('.card, .wishlist-card').length : 0;
            if (remaining === 0) {
                renderWishlistEmptyState();
            }
        } else {
            // ADD: Save to Firestore with both place_id and placeId for cross-platform compatibility
            await window.fbFirestoreMethods.setDoc(docRef, {
                user_id: uid,
                place_id: Number(placeId) || placeId,
                placeId: Number(placeId) || placeId,
                name: name || 'Landmark',
                placeName: name || 'Landmark',
                folder_name: folder || 'place',
                timestamp: window.fbFirestoreMethods.serverTimestamp(),
                created_at: window.fbFirestoreMethods.serverTimestamp()
            });
            
            // Set saved heart button state without text
            updateWishlistButtons(placeId, true, name);

            // Reload wishlist UI to reflect new item
            await loadWishlistFirestore();
        }

        refreshProfileStats();
    } catch (err) {
        console.error("Firestore wishlist operation failed:", err);
    }
}

// ============================================================
// GOOGLE MAPS — EXPLORE MAP (map-first + side panel)
// ============================================================
let exploreMap = null;
let markerClustererInstance = null;
let allMarkers = [];
let exploreInfoWindow = null;
let exploreActiveCategory = 'all';
let panelOpen = false;

function toggleSidePanel() {
    panelOpen = !panelOpen;
    const panel = document.getElementById('explore-side-panel');
    if (panel) panel.style.right = panelOpen ? '0' : '-310px';
    const btnText = document.getElementById('panel-btn-text');
    if (btnText) btnText.textContent = panelOpen ? 'Close' : 'Places';
}

function initExploreMap() {
    const mapEl = document.getElementById('explore-map');
    if (!mapEl || !window.google) return;

    exploreMap = new google.maps.Map(mapEl, {
        center: { lat: 15.8497, lng: 74.4977 },
        zoom: 10,
        mapTypeControl: false,
        streetViewControl: false,
        fullscreenControl: false,
        gestureHandling: 'greedy',
        styles: [
            { featureType: 'poi', elementType: 'labels', stylers: [{ visibility: 'off' }] },
            { featureType: 'transit', elementType: 'labels', stylers: [{ visibility: 'off' }] }
        ]
    });

    exploreInfoWindow = new google.maps.InfoWindow();
    const bounds = new google.maps.LatLngBounds();
    const rawMarkers = [];
    const panelList = document.getElementById('side-panel-list');
    
    // Phase 2A: Query from Firestore places collection if loaded, fallback to static database
    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;

    if (sourceData && panelList) {
        panelList.innerHTML = "";
        sourceData.forEach(p => {
            if (p.lat && p.lon) {
                const position = { lat: p.lat, lng: p.lon };
                bounds.extend(position);

                const marker = new google.maps.Marker({ position, title: p.name });

                marker.addListener('click', () => {
                    const catEmoji = { waterfall: '💧', temple: '🛕', fort: '🏰', nature: '🌿', wildlife: '🦅', lake: '🏞️', dam: '🌊', garden: '🌳' };
                    const emoji = catEmoji[p.category.toLowerCase()] || '📍';
                    exploreInfoWindow.setContent(`
                        <div style="width:220px;font-family:Inter,sans-serif;padding:4px">
                            <img src="/static/images/${p.folder_name}/1.jpg"
                                 style="width:100%;height:115px;object-fit:cover;border-radius:10px;margin-bottom:10px"
                                 onerror="this.style.display='none'">
                            <div style="font-weight:700;font-size:0.92rem;color:#1e293b;margin-bottom:2px">${p.name}</div>
                            <div style="color:#64748b;font-size:0.75rem;text-transform:capitalize;margin-bottom:10px">
                                ${emoji} ${p.category}
                            </div>
                            <a href="/place/${p.id}"
                               style="display:block;text-align:center;background:linear-gradient(135deg,#2563eb,#1d4ed8);
                                      color:white;padding:8px;border-radius:20px;text-decoration:none;
                                      font-weight:700;font-size:0.82rem;letter-spacing:0.3px"
                               onclick="navigateToPlace(${p.id}); return false;">
                                View Details →
                            </a>
                        </div>`);
                    exploreInfoWindow.open(exploreMap, marker);
                    exploreMap.panTo(position);
                });

                allMarkers.push({ name: p.name.toLowerCase(), category: p.category.toLowerCase(), marker });
                rawMarkers.push(marker);
            }

            // Side panel card
            const item = document.createElement('a');
            item.href = `/place/${p.id}`;
            item.className = 'side-panel-item d-flex align-items-center gap-3 p-2 rounded-3 text-decoration-none text-dark mb-1';
            item.setAttribute('data-name', p.name.toLowerCase());
            item.setAttribute('data-category', p.category.toLowerCase());
            item.style.cssText = 'transition:background 0.15s;border:1px solid transparent';
            item.onmouseover = () => { item.style.background = '#f1f5f9'; item.style.borderColor = '#e2e8f0'; };
            item.onmouseout = () => { item.style.background = ''; item.style.borderColor = 'transparent'; };
            item.onclick = (e) => { e.preventDefault(); navigateToPlace(p.id); };
            item.innerHTML = `
                <img src="/static/images/${p.folder_name}/1.jpg"
                     style="width:58px;height:58px;object-fit:cover;border-radius:12px;flex-shrink:0"
                     onerror="this.src='/static/icon-192.png'">
                <div class="overflow-hidden">
                    <div style="font-weight:700;font-size:0.82rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${p.name}</div>
                    <div style="color:#64748b;font-size:0.72rem;text-transform:capitalize">${p.category}</div>
                </div>`;
            panelList.appendChild(item);
        });
    }

    if (rawMarkers.length > 0) {
        exploreMap.fitBounds(bounds);
        markerClustererInstance = new markerClusterer.MarkerClusterer({ markers: rawMarkers, map: exploreMap });
    }

    const countEl = document.getElementById('panel-count');
    if (countEl) countEl.textContent = `${allMarkers.length} places`;
}

function recenterExploreMap() {
    if (!exploreMap) return;
    const visible = allMarkers.filter(m => m.marker.getMap() !== null);
    if (visible.length > 0) {
        const bounds = new google.maps.LatLngBounds();
        visible.forEach(m => bounds.extend(m.marker.getPosition()));
        exploreMap.fitBounds(bounds);
    } else {
        exploreMap.setCenter({ lat: 15.8497, lng: 74.4977 });
        exploreMap.setZoom(10);
    }
}

function setCategory(category, btnElement) {
    exploreActiveCategory = category.toLowerCase();
    document.querySelectorAll('.explore-filter-btn').forEach(btn => {
        btn.classList.remove('btn-primary', 'fw-bold');
        btn.classList.add('btn-light', 'border');
    });
    btnElement.classList.remove('btn-light', 'border');
    btnElement.classList.add('btn-primary', 'fw-bold');
    applyExploreFilters();
}

function applyExploreFilters() {
    const searchInput = (document.getElementById('exploreSearch') || {}).value || '';
    const q = searchInput.toLowerCase();
    let visibleCount = 0;
    const visibleMarkers = [];

    // Filter side panel items
    document.querySelectorAll('.side-panel-item').forEach(item => {
        const matchSearch = item.getAttribute('data-name').includes(q);
        const matchCat = exploreActiveCategory === 'all' || item.getAttribute('data-category').includes(exploreActiveCategory);
        const show = matchSearch && matchCat;
        item.style.display = show ? 'flex' : 'none';
        if (show) visibleCount++;
    });

    const countEl = document.getElementById('panel-count');
    if (countEl) countEl.textContent = `${visibleCount} places`;

    // Filter map markers
    allMarkers.forEach(m => {
        const matchSearch = m.name.includes(q);
        const matchCat = exploreActiveCategory === 'all' || m.category.includes(exploreActiveCategory);
        const show = matchSearch && matchCat;
        m.marker.setMap(show ? exploreMap : null);
        if (show) visibleMarkers.push(m.marker);
    });

    const noResults = document.getElementById('no-results-msg');
    if (noResults) noResults.style.display = visibleCount === 0 ? 'block' : 'none';

    if (markerClustererInstance) {
        markerClustererInstance.clearMarkers();
        markerClustererInstance.addMarkers(visibleMarkers);
    }
}

// ============================================================
// EXPENSE MODULE
// ============================================================
let expenses = [];

function getBudgetLimit() {
    const stored = parseInt(localStorage.getItem('budget_limit') || '10000', 10);
    return Number.isNaN(stored) || stored <= 0 ? 10000 : stored;
}

function getCategoryIcon(category) {
    const icons = { 'Transport': '🚗', 'Food': '🍽️', 'Entry Fee': '🎟️', 'Stay': '🏨', 'Misc': '📦' };
    return icons[category] || '💰';
}

async function loadExpenses() {
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        loadExpensesFirestore();
        return;
    }

    // Legacy Fallback
    try {
        const res = await fetch('/api/expenses');
        if (res.ok) { expenses = await res.json(); updateExpenseUI(); }
    } catch (err) { console.error('Failed to load expenses', err); }
}

async function loadExpensesFirestore() {
    if (!window.fbAuth?.currentUser || !window.fbDb) return;
    const uid = window.fbAuth.currentUser.uid;
    const q = window.fbFirestoreMethods.query(
        window.fbFirestoreMethods.collection(window.fbDb, "expenses"),
        window.fbFirestoreMethods.where("user_id", "==", uid)
    );

    try {
        const querySnapshot = await window.fbFirestoreMethods.getDocs(q);
        expenses = [];
        querySnapshot.forEach(doc => {
            const data = doc.data();
            expenses.push({
                id: doc.id,
                location: data.location || data.placeName || 'General',
                name: data.name || data.title || 'Expense',
                amount: typeof data.amount === 'number' ? data.amount : (parseFloat(data.amount) || 0),
                category: data.category || 'Misc',
                date: data.date || '',
                created_at: data.created_at
            });
        });
        // Sort client side (newest first) to avoid composite index requirement
        expenses.sort((a, b) => {
            const tA = a.created_at?.seconds || 0;
            const tB = b.created_at?.seconds || 0;
            return tB - tA;
        });
        window.expenses = expenses;
        updateExpenseUI();
    } catch (err) {
        console.error("Firestore expenses load failed:", err);
    }
}

async function addExpenseDirect() {
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        return addExpenseFirestore();
    }
    return addExpense();
}

async function addExpenseFirestore() {
    const locInput = document.getElementById("expense-dest") || document.getElementById("item_location");
    const nameInput = document.getElementById("expense-label") || document.getElementById("item_name");
    const amountInput = document.getElementById("expense-amount") || document.getElementById("item_amount");
    const categoryInput = document.getElementById("expense-category") || document.getElementById("item_category");

    const location = locInput ? locInput.value : '';
    const name = nameInput ? nameInput.value.trim() : '';
    const amount = amountInput ? parseFloat(amountInput.value) : NaN;
    const category = categoryInput ? categoryInput.value : 'Misc';

    if (!location || isNaN(amount) || amount <= 0) {
        alert("Please select a valid destination and enter an amount.");
        return;
    }

    const finalName = name || category || 'Expense';
    const payload = {
        location,
        placeName: location,
        name: finalName,
        title: finalName,
        amount,
        category,
        date: new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
    };

    try {
        const uid = window.fbAuth.currentUser.uid;
        const docRef = await window.fbFirestoreMethods.addDoc(
            window.fbFirestoreMethods.collection(window.fbDb, "expenses"), 
            {
                user_id: uid,
                location: location,
                placeName: location,
                name: finalName,
                title: finalName,
                amount: amount,
                category: category,
                date: payload.date,
                created_at: window.fbFirestoreMethods.serverTimestamp()
            }
        );
        payload.id = docRef.id;
        expenses.unshift(payload);
        updateExpenseUI();
        if (nameInput) nameInput.value = "";
        if (amountInput) amountInput.value = "";
        if (locInput) locInput.selectedIndex = 0;
        refreshProfileStats();
    } catch (err) {
        console.error("Firestore expense write failed:", err);
        alert("Failed to save expense. Please check your connection.");
    }
}

async function addExpense() {
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        return addExpenseFirestore();
    }

    const locInput = document.getElementById("expense-dest") || document.getElementById("item_location");
    const nameInput = document.getElementById("expense-label") || document.getElementById("item_name");
    const amountInput = document.getElementById("expense-amount") || document.getElementById("item_amount");
    const categoryInput = document.getElementById("expense-category") || document.getElementById("item_category");

    const location = locInput ? locInput.value : '';
    const name = nameInput ? nameInput.value.trim() : '';
    const amount = amountInput ? parseFloat(amountInput.value) : NaN;
    const category = categoryInput ? categoryInput.value : 'Misc';

    if (!location || isNaN(amount) || amount <= 0) {
        alert("Please select a valid destination and enter an amount.");
        return;
    }

    const finalName = name || category || 'Expense';
    const payload = {
        location,
        placeName: location,
        name: finalName,
        title: finalName,
        amount,
        category,
        date: new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
    };

    try {
        const res = await fetch('/api/expenses', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.status === 'success') {
            payload.id = data.id || ('local_' + Date.now());
            expenses.unshift(payload);
            updateExpenseUI();
            if (nameInput) nameInput.value = "";
            if (amountInput) amountInput.value = "";
            if (locInput) locInput.selectedIndex = 0;
        }
    } catch (err) {
        console.warn("Backend API not reachable, saving client-side:", err);
        payload.id = 'local_' + Date.now();
        expenses.unshift(payload);
        updateExpenseUI();
        if (nameInput) nameInput.value = "";
        if (amountInput) amountInput.value = "";
        if (locInput) locInput.selectedIndex = 0;
    }
}

async function deleteExpenseDirect(id) {
    if (!confirm("Remove this expense?")) return;
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        try {
            await window.fbFirestoreMethods.deleteDoc(
                window.fbFirestoreMethods.doc(window.fbDb, "expenses", id)
            );
            expenses = expenses.filter(e => e.id !== id);
            updateExpenseUI();
            refreshProfileStats();
        } catch (err) {
            console.error("Firestore delete expense failed:", err);
        }
        return;
    }
    
    // Legacy Fallback
    deleteExpense(id);
}

async function deleteExpense(id) {
    try {
        const res = await fetch(`/api/expenses/${id}`, { method: 'DELETE' });
        if (res.ok) { expenses = expenses.filter(e => e.id !== id); updateExpenseUI(); }
        else { expenses = expenses.filter(e => e.id !== id); updateExpenseUI(); }
    } catch (err) {
        expenses = expenses.filter(e => e.id !== id);
        updateExpenseUI();
    }
}

async function clearAllExpensesDirect() {
    if (!confirm("Clear all budget data?")) return;
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        const uid = window.fbAuth.currentUser.uid;
        try {
            const q = window.fbFirestoreMethods.query(
                window.fbFirestoreMethods.collection(window.fbDb, "expenses"),
                window.fbFirestoreMethods.where("user_id", "==", uid)
            );
            const querySnapshot = await window.fbFirestoreMethods.getDocs(q);
            const deletePromises = [];
            querySnapshot.forEach((d) => {
                deletePromises.push(window.fbFirestoreMethods.deleteDoc(d.ref));
            });
            await Promise.all(deletePromises);
            expenses = [];
            updateExpenseUI();
            refreshProfileStats();
        } catch (err) {
            console.error("Firestore clear expenses failed:", err);
        }
        return;
    }
    
    clearAllExpenses();
}

async function clearAllExpenses() {
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        return clearAllExpensesDirect();
    }
    try {
        const res = await fetch('/api/expenses/clear', { method: 'POST' });
        if (res.ok) { expenses = []; updateExpenseUI(); }
    } catch (err) {
        expenses = [];
        updateExpenseUI();
    }
}

window.addExpenseDirect = addExpenseDirect;
window.addExpense = addExpense;
window.deleteExpenseDirect = deleteExpenseDirect;
window.deleteExpense = deleteExpense;
window.clearAllExpensesDirect = clearAllExpensesDirect;
window.clearAllExpenses = clearAllExpenses;

window.getCategoryTotals = function() {
    const totals = {};
    expenses.forEach(e => {
        const cat = e.category || 'Misc';
        totals[cat] = (totals[cat] || 0) + e.amount;
    });
    return totals;
};

window.getDestinationTotals = function() {
    const totals = {};
    expenses.forEach(e => {
        const dest = e.location || e.placeName || 'General';
        totals[dest] = (totals[dest] || 0) + e.amount;
    });
    return totals;
};

function updateExpenseUI() {
    const list     = document.getElementById('expense-list');
    const totalEl  = document.getElementById('total-expense');
    const fillEl   = document.getElementById('budget-progress-fill');
    const textEl   = document.getElementById('budget-progress-text');

    // Legacy element IDs fallback
    const totalFallback = document.getElementById('total-amount');

    if (!list) return;

    window.expenses = expenses;
    list.innerHTML = '';
    let total = 0;
    const grouped = {};

    expenses.forEach(exp => {
        total += exp.amount;
        const loc = exp.location || exp.placeName || 'General';
        if (!grouped[loc]) grouped[loc] = [];
        grouped[loc].push(exp);
    });

    if (expenses.length === 0) {
        list.innerHTML = `
            <div style="text-align:center;padding:48px 20px;">
                <div style="font-size:2rem;color:var(--ink-faint);margin-bottom:12px;">💳</div>
                <div style="font-family:var(--font-display);font-size:1.15rem;color:var(--ink);margin-bottom:6px;">No expenses yet</div>
                <p style="font-size:.82rem;color:var(--ink-muted);">Add your first trip expense above</p>
            </div>`;
    } else {
        const catIcon = { 'Food':'🍽️','Transport':'🚌','Stay':'🏨','Entry':'🎟️','Shopping':'🛍️','Other':'📦','Misc':'📦','Entry Fee':'🎟️' };
        for (const [location, items] of Object.entries(grouped)) {
            const locTotal = items.reduce((s, i) => s + i.amount, 0);
            const itemsHtml = items.map(exp => `
                <div class="expense-item">
                    <div class="expense-item-icon">${catIcon[exp.category] || '💰'}</div>
                    <div class="expense-item-info">
                        <div class="expense-item-label">${exp.category}</div>
                        <div class="expense-item-note">${exp.name || '—'} · ${exp.date || ''}</div>
                    </div>
                    <div class="expense-item-right">
                        <div class="expense-item-amount">₹${exp.amount.toLocaleString('en-IN')}</div>
                        <button class="btn-remove-exp" onclick="deleteExpenseDirect('${exp.id}')">Remove</button>
                    </div>
                </div>`).join('');

            list.innerHTML += `
                <div class="expense-group">
                    <div class="expense-group-header">
                        <span class="expense-group-name"><i class="fa-solid fa-location-dot" style="color:var(--forest);margin-right:6px;font-size:.8rem;"></i>${location}</span>
                        <span class="expense-group-total">₹${locTotal.toLocaleString('en-IN')}</span>
                    </div>
                    ${itemsHtml}
                </div>`;
        }
    }

    const fmt = v => `₹${v.toLocaleString('en-IN')}`;
    if (totalEl) totalEl.textContent = fmt(total);
    if (totalFallback) totalFallback.textContent = fmt(total);

    const limit   = getBudgetLimit();
    const pct     = limit > 0 ? Math.min((total / limit) * 100, 100) : 0;
    const badgeEl = document.getElementById('budget-alert-badge');

    if (limit > 0 && total > limit) {
        const over = total - limit;
        if (fillEl) {
            fillEl.style.width = '100%';
            fillEl.style.backgroundColor = '#f43f5e';
            fillEl.className = 'budget-progress-fill exceeded';
        }
        if (textEl) {
            textEl.textContent = `${fmt(total)} of ${fmt(limit)} used (${fmt(over)} over budget)`;
        }
        if (badgeEl) {
            badgeEl.style.display = 'inline-flex';
            badgeEl.className = 'budget-alert-badge exceeded';
            badgeEl.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i> Budget exceeded by ${fmt(over)}`;
        }
    } else if (limit > 0 && total === limit) {
        if (fillEl) {
            fillEl.style.width = '100%';
            fillEl.style.backgroundColor = '#f97316';
            fillEl.className = 'budget-progress-fill reached';
        }
        if (textEl) {
            textEl.textContent = `${fmt(total)} of ${fmt(limit)} used (100%)`;
        }
        if (badgeEl) {
            badgeEl.style.display = 'inline-flex';
            badgeEl.className = 'budget-alert-badge reached';
            badgeEl.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i> Budget limit reached (100%)`;
        }
    } else if (limit > 0 && total >= 0.8 * limit) {
        if (fillEl) {
            fillEl.style.width = pct + '%';
            fillEl.style.backgroundColor = '#f59e0b';
            fillEl.className = 'budget-progress-fill warning';
        }
        if (textEl) {
            textEl.textContent = `${fmt(total)} of ${fmt(limit)} used`;
        }
        if (badgeEl) {
            badgeEl.style.display = 'inline-flex';
            badgeEl.className = 'budget-alert-badge warning';
            badgeEl.innerHTML = `<i class="fa-solid fa-circle-exclamation"></i> You're close to your budget`;
        }
    } else {
        if (fillEl) {
            fillEl.style.width = pct + '%';
            fillEl.style.backgroundColor = 'var(--cream)';
            fillEl.className = 'budget-progress-fill';
        }
        if (textEl) {
            textEl.textContent = `${fmt(total)} of ${fmt(limit)} used`;
        }
        if (badgeEl) {
            badgeEl.style.display = 'none';
            badgeEl.className = 'budget-alert-badge';
            badgeEl.innerHTML = '';
        }
    }

    // Budget limit text display
    const limitText = document.getElementById('budget-limit-text');
    if (limitText) limitText.textContent = fmt(limit);

    // Budget inputs sync
    const budgetInput = document.getElementById('budget-goal-input');
    if (budgetInput && !budgetInput.matches(':focus')) budgetInput.value = limit;
    const profileInput = document.getElementById('profile-budget-input');
    if (profileInput && !profileInput.matches(':focus')) profileInput.value = limit;

    // ── Spending Breakdown (By Category & By Destination) ──
    const catList  = document.getElementById('breakdown-category-list');
    const destList = document.getElementById('breakdown-destination-list');

    if (catList && destList) {
        const emptyStateHtml = `
            <div class="breakdown-empty-card">
                <div class="breakdown-empty-icon"><i class="fa-solid fa-receipt"></i></div>
                <div class="breakdown-empty-title">No expenses yet</div>
                <p class="breakdown-empty-desc">Add your first trip expense to start tracking your spending.</p>
            </div>`;

        if (expenses.length === 0 || total === 0) {
            catList.innerHTML = emptyStateHtml;
            destList.innerHTML = emptyStateHtml;
        } else {
            // 1. By Category using existing getCategoryTotals()
            const catTotals = (typeof getCategoryTotals === 'function') ? getCategoryTotals() : {};
            const catEntries = Object.entries(catTotals).filter(([_, amt]) => amt > 0);
            catEntries.sort((a, b) => b[1] - a[1]);

            const catIcons = {
                'Transport': '🚗',
                'Stay': '🏨',
                'Food': '🍽️',
                'Tickets': '🎟️',
                'Entry': '🎟️',
                'Entry Fee': '🎟️',
                'Shopping': '🛍️',
                'Misc': '📦',
                'Other': '📦',
                'Others': '📦'
            };

            if (catEntries.length === 0) {
                catList.innerHTML = emptyStateHtml;
            } else {
                catList.innerHTML = catEntries.map(([cat, amt]) => {
                    const rawPct = (total > 0) ? (amt / total) * 100 : 0;
                    const pctFormatted = rawPct.toFixed(1);
                    const icon = catIcons[cat] || '🏷️';
                    return `
                    <div class="breakdown-row">
                        <div class="breakdown-row-top">
                            <div class="breakdown-label">
                                <span class="breakdown-icon">${icon}</span>
                                <span>${cat}</span>
                            </div>
                            <span class="breakdown-amount">₹${amt.toLocaleString('en-IN')}</span>
                        </div>
                        <div class="breakdown-bar-row">
                            <div class="breakdown-bar-bg">
                                <div class="breakdown-bar-fill" style="width:${Math.min(rawPct, 100)}%;"></div>
                            </div>
                            <span class="breakdown-pct">${pctFormatted}%</span>
                        </div>
                    </div>`;
                }).join('');
            }

            // 2. By Destination using existing getDestinationTotals()
            const destTotals = (typeof getDestinationTotals === 'function') ? getDestinationTotals() : {};
            const destEntries = Object.entries(destTotals).filter(([_, amt]) => amt > 0);
            destEntries.sort((a, b) => b[1] - a[1]);

            if (destEntries.length === 0) {
                destList.innerHTML = emptyStateHtml;
            } else {
                const top5DestEntries = destEntries.slice(0, 5);
                const hasMoreDestinations = destEntries.length >= 5;
                const rowsHtml = top5DestEntries.map(([dest, amt]) => {
                    const rawPct = (total > 0) ? (amt / total) * 100 : 0;
                    const pctFormatted = rawPct.toFixed(1);
                    return `
                    <div class="breakdown-row">
                        <div class="breakdown-row-top">
                            <div class="breakdown-label">
                                <i class="fa-solid fa-location-dot breakdown-location-icon"></i>
                                <span>${dest}</span>
                            </div>
                            <span class="breakdown-amount">₹${amt.toLocaleString('en-IN')}</span>
                        </div>
                        <div class="breakdown-bar-row">
                            <div class="breakdown-bar-bg">
                                <div class="breakdown-bar-fill" style="width:${Math.min(rawPct, 100)}%;"></div>
                            </div>
                            <span class="breakdown-pct">${pctFormatted}%</span>
                        </div>
                    </div>`;
                }).join('');

                const viewAllHtml = hasMoreDestinations ? `
                    <div class="breakdown-view-all-wrap">
                        <a href="#expense-list" class="breakdown-view-all-link" onclick="scrollToExpenseList(event)">
                            View all destinations <span class="breakdown-view-all-arrow">→</span>
                        </a>
                    </div>` : '';

                destList.innerHTML = rowsHtml + viewAllHtml;
            }
        }
    }
}

function scrollToExpenseList(event) {
    if (event) event.preventDefault();
    const el = document.getElementById('expense-list') || document.querySelector('.expense-list-header');
    if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}
window.scrollToExpenseList = scrollToExpenseList;

// Load profile stats and recent activity directly from Firestore
async function loadWebProfileDataFirestore() {
    if (!window.fbDb || !window.fbAuth || !window.fbAuth.currentUser) return;
    const uid = window.fbAuth.currentUser.uid;

    const wishEl = document.getElementById('stat-wishlist-count');
    const expEl = document.getElementById('stat-expense-count');
    const revEl = document.getElementById('stat-review-count');
    const visEl = document.getElementById('stat-visited-count');
    const recentActivityEl = document.getElementById('profile-recent-activity');

    try {
        // 1. User profile doc for budget
        try {
            const userRef = window.fbFirestoreMethods.doc(window.fbDb, "users", uid);
            const userSnap = await window.fbFirestoreMethods.getDoc(userRef);
            if (userSnap.exists() && userSnap.data().budget) {
                const b = parseInt(userSnap.data().budget, 10);
                if (b && b > 0) {
                    localStorage.setItem('budget_limit', String(b));
                    const limitInput = document.getElementById("budget-goal-input");
                    if (limitInput) limitInput.value = b;
                    const profileBudgetInput = document.getElementById("profile-budget-input");
                    if (profileBudgetInput) profileBudgetInput.value = b;
                    const limitText = document.getElementById("budget-limit-text");
                    if (limitText) limitText.innerText = `₹${b.toLocaleString('en-IN')}`;
                }
            }
        } catch (bErr) {
            console.warn("[Web Profile] Budget fetch warning:", bErr);
        }

        // 2. Wishlist query (root collection wishlist where user_id == uid)
        let wishlistDocs = [];
        try {
            const wishQuery = window.fbFirestoreMethods.query(
                window.fbFirestoreMethods.collection(window.fbDb, "wishlist"),
                window.fbFirestoreMethods.where("user_id", "==", uid)
            );
            const wishSnap = await window.fbFirestoreMethods.getDocs(wishQuery);
            wishlistDocs = wishSnap.docs.map(d => ({ id: d.id, ...d.data() }));
        } catch (we) {
            console.warn("[Web Profile] Wishlist count query warning:", we);
        }
        if (wishEl) wishEl.innerText = wishlistDocs.length;

        // 3. Expenses query (root collection expenses where user_id == uid)
        let expenseDocs = [];
        try {
            const expQuery = window.fbFirestoreMethods.query(
                window.fbFirestoreMethods.collection(window.fbDb, "expenses"),
                window.fbFirestoreMethods.where("user_id", "==", uid)
            );
            const expSnap = await window.fbFirestoreMethods.getDocs(expQuery);
            expenseDocs = expSnap.docs.map(d => ({ id: d.id, ...d.data() }));
        } catch (ee) {
            console.warn("[Web Profile] Expenses count query warning:", ee);
        }
        if (expEl) expEl.innerText = expenseDocs.length;

        // 4. Reviews query (collectionGroup comments where userId == uid with per-place fallback)
        let reviewDocs = [];
        try {
            const { collectionGroup, query, where, getDocs } = window.fbFirestoreMethods;
            if (collectionGroup) {
                const revQuery = query(collectionGroup(window.fbDb, "comments"), where("userId", "==", uid));
                const revSnap = await getDocs(revQuery);
                reviewDocs = revSnap.docs.map(d => ({ id: d.id, parentPlaceId: d.ref?.parent?.parent?.id, ...d.data() }));
            }
        } catch (revErr) {
            console.warn("[Web Profile] CollectionGroup comments query warning:", revErr);
        }

        // Fallback if collectionGroup returned no docs: scan known places comments
        if (reviewDocs.length === 0 && (window.cachedPlacesFromFirestore || window.allPlacesData)) {
            const placesList = window.cachedPlacesFromFirestore || window.allPlacesData || [];
            const { collection, query, where, getDocs } = window.fbFirestoreMethods;
            for (const p of placesList) {
                try {
                    const commentsRef = collection(window.fbDb, "reviews", String(p.id), "comments");
                    const qUser = query(commentsRef, where("userId", "==", uid));
                    const snapUser = await getDocs(qUser);
                    snapUser.docs.forEach(d => {
                        reviewDocs.push({ id: d.id, parentPlaceId: String(p.id), ...d.data() });
                    });
                } catch (e) {
                    // Ignore individual place query errors
                }
            }
        }
        if (revEl) revEl.innerText = reviewDocs.length;

        // 5. Visited count = Unique places interacted with across wishlist, expenses, and reviews
        const visitedPlaces = new Set();
        expenseDocs.forEach(e => {
            const loc = e.location || e.placeName;
            if (loc) visitedPlaces.add(loc.trim().toLowerCase());
        });
        wishlistDocs.forEach(w => {
            const name = w.name || w.placeName;
            if (name) visitedPlaces.add(name.trim().toLowerCase());
        });
        reviewDocs.forEach(r => {
            if (r.parentPlaceId) visitedPlaces.add(`place_${r.parentPlaceId}`);
        });
        if (visEl) visEl.innerText = visitedPlaces.size;

        // 6. Recent Activity Stream
        const activities = [];

        wishlistDocs.forEach(w => {
            const name = w.name || w.placeName || 'Landmark';
            const ts = w.timestamp?.seconds ? w.timestamp.seconds * 1000 : (w.created_at?.seconds ? w.created_at.seconds * 1000 : 0);
            activities.push({
                type: 'wishlist',
                icon: 'fa-heart text-danger',
                title: `Saved ${name}`,
                subtitle: 'Added to your wishlist',
                dateStr: ts ? new Date(ts).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : 'Saved',
                timestamp: ts
            });
        });

        expenseDocs.forEach(e => {
            const name = e.name || e.title || 'Expense';
            const amount = e.amount || 0;
            const category = e.category || 'Misc';
            const location = e.location || e.placeName || 'Belagavi';
            const ts = e.created_at?.seconds ? e.created_at.seconds * 1000 : 0;
            activities.push({
                type: 'expense',
                icon: 'fa-wallet text-primary',
                title: `Tracked ₹${amount.toLocaleString('en-IN')} for ${category}`,
                subtitle: `${name} at ${location}`,
                dateStr: e.date || 'Recent',
                timestamp: ts
            });
        });

        reviewDocs.forEach(r => {
            const rating = r.rating || 5;
            const text = r.text || r.comment || '';
            const ts = r.timestamp?.seconds ? r.timestamp.seconds * 1000 : 0;

            let placeName = '';
            if (r.parentPlaceId) {
                const placesList = window.cachedPlacesFromFirestore || window.allPlacesData || [];
                const found = placesList.find(p => String(p.id) === String(r.parentPlaceId));
                if (found) placeName = found.name;
            }

            const displayTitle = placeName ? `Reviewed ${placeName} (${rating}★)` : `Reviewed place (${rating}★)`;

            activities.push({
                type: 'review',
                icon: 'fa-star text-warning',
                title: displayTitle,
                subtitle: text ? `"${text.substring(0, 32)}..."` : 'Submitted a review',
                dateStr: r.date || (ts ? new Date(ts).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : 'Recent'),
                timestamp: ts
            });
        });

        activities.sort((a, b) => b.timestamp - a.timestamp);

        if (recentActivityEl) {
            if (activities.length === 0) {
                recentActivityEl.innerHTML = `
                    <div style="text-align:center;padding:24px 16px;color:var(--ink-muted);">
                        <i class="fa-solid fa-clock-rotate-left mb-2" style="font-size:1.5rem;opacity:0.5;"></i>
                        <p style="font-size:.85rem;margin:0;">No recent activity yet. Save places, submit reviews, and track expenses to see them here!</p>
                    </div>`;
            } else {
                let html = '<div style="display:flex;flex-direction:column;gap:12px;">';
                activities.slice(0, 5).forEach(act => {
                    html += `
                        <div style="display:flex;align-items:center;justify-content:space-between;padding:8px 0;border-bottom:1px solid var(--border-light);">
                            <div style="display:flex;align-items:center;gap:12px;">
                                <div style="width:36px;height:36px;border-radius:50%;background:var(--cream);display:flex;align-items:center;justify-content:center;">
                                    <i class="fa-solid ${act.icon}" style="font-size:.9rem;"></i>
                                </div>
                                <div>
                                    <div style="font-weight:600;font-size:.85rem;color:var(--ink);">${act.title}</div>
                                    <div style="color:var(--ink-muted);font-size:.75rem;">${act.subtitle}</div>
                                </div>
                            </div>
                            <span style="color:var(--ink-muted);font-size:.7rem;font-weight:500;">${act.dateStr}</span>
                        </div>`;
                });
                html += '</div>';
                recentActivityEl.innerHTML = html;
            }
        }
    } catch (err) {
        console.error("[Web Profile] Firestore load profile data failed:", err);
    }
}

function refreshProfileStats() {
    loadWebProfileDataFirestore();
}

// Client-side dynamically render places lists inside index.html home section
function renderFeaturedPlaces(categoryFilter, searchQuery) {
    const container = document.getElementById('places-container');
    if (!container) return;

    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (!sourceData || sourceData.length === 0) return;

    const cat = (categoryFilter || currentHomeCategory || 'all').toLowerCase();
    const q   = (searchQuery || '').toLowerCase().trim();

    const filtered = sourceData.filter(p => {
        if (!p) return false;
        const matchCat = cat === 'all' || (p.category || '').toLowerCase().includes(cat);
        const matchQ   = !q || (p.name || '').toLowerCase().includes(q) ||
                          (p.category || '').toLowerCase().includes(q) ||
                          (p.city || '').toLowerCase().includes(q);
        return matchCat && matchQ;
    });

    if (filtered.length === 0) {
        container.innerHTML = `
            <div style="grid-column:1/-1;text-align:center;padding:80px 20px;">
                <div style="font-size:2.5rem;color:var(--ink-faint);margin-bottom:16px;">🔍</div>
                <div style="font-family:var(--font-display);font-size:1.4rem;color:var(--ink);margin-bottom:8px;">No destinations found</div>
                <p style="font-size:.875rem;color:var(--ink-muted);">Try a different search or category</p>
            </div>`;
        return;
    }

    // Check saved state
    const savedIds = window.userWishlistPlaceIds || new Set(
        Array.from(document.querySelectorAll('[id^="wish-item-"]'))
             .map(el => el.id.replace('wish-item-', ''))
    );

    container.innerHTML = filtered.map(p => {
        const isSaved = savedIds.has(String(p.id));
        const city = p.city || 'Belagavi District';
        const desc = (p.description || p.history || '').substring(0, 90);
        return `
        <div class="dest-grid-item">
            <a class="dest-card" href="#place-${p.id}"
               onclick="navigateToPlace(${p.id}); return false;"
               aria-label="${p.name}, ${p.category}">
                <div class="card-img-wrap">
                    <img src="/static/images/${p.folder_name}/1.jpg"
                         alt="${p.name}"
                         loading="lazy"
                         onerror="this.src='/static/icon-192.png'">
                    <span class="card-category">${p.category}</span>
                    <button class="card-save save-btn-${p.id} ${isSaved ? 'saved' : ''}"
                            data-name="${p.name.replace(/"/g, '&quot;')}"
                            onclick="event.preventDefault();event.stopPropagation();toggleWishlist(this,${p.id},'${p.name.replace(/'/g,"\\'")}','${p.folder_name}')"
                            aria-label="${isSaved ? 'Remove ' + p.name + ' from saved places' : 'Save ' + p.name}"
                            aria-pressed="${isSaved ? 'true' : 'false'}"
                            title="${isSaved ? 'Remove ' + p.name + ' from saved places' : 'Save ' + p.name}">
                        <i class="fa-${isSaved ? 'solid' : 'regular'} fa-heart"></i>
                    </button>
                </div>
                <div class="card-body">
                    <div class="card-name">${p.name}</div>
                    <div class="card-meta">
                        <i class="fa-solid fa-location-dot" style="font-size:.7rem;color:var(--forest);"></i>
                        ${city}
                        ${p.best_time ? `<span class="card-meta-dot"></span>${p.best_time}` : ''}
                    </div>
                </div>
            </a>
        </div>`;
    }).join('');
}
window.renderFeaturedPlaces = renderFeaturedPlaces;


function populateDestinationSelector() {
    const select = document.getElementById("expense-dest") || document.getElementById("item_location");
    if (!select) return;

    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (!sourceData || sourceData.length === 0) return;
    
    select.innerHTML = '<option value="" disabled selected>Select Destination</option>';
    
    sourceData.forEach(p => {
        if (!p || !p.name) return;
        const opt = document.createElement("option");
        opt.value = p.name;
        opt.textContent = p.name;
        select.appendChild(opt);
    });
    
    const generalOpt = document.createElement("option");
    generalOpt.value = "General";
    generalOpt.textContent = "General / Misc";
    select.appendChild(generalOpt);
}
window.populateDestinationSelector = populateDestinationSelector;

// Navigation wrapper
function navigateToPlace(placeId) {
    if (window.fbDb) {
        // On static Firebase Hosting, dynamic place pages can be handled inside hash routes
        window.location.hash = `#place-${placeId}`;
    } else {
        // Localhost fallback
        window.location.href = `/place/${placeId}`;
    }
}

// Wishlist state management: 'idle' | 'loading' | 'loaded' | 'error'
window.wishlistStatus = 'idle';
window.cachedWishlistPlaces = [];

function renderWishlistEmptyState() {
    const container = document.getElementById('wishlist-items');
    if (!container) return;
    container.innerHTML = `
        <div class="saved-empty" style="grid-column:1/-1;">
            <div class="saved-empty-icon"><i class="fa-regular fa-heart"></i></div>
            <div class="saved-empty-title">Nothing saved yet</div>
            <p class="saved-empty-sub">Tap the ♡ on any destination to save it here</p>
            <button class="btn-discover" onclick="show('home')">Browse Destinations</button>
        </div>`;
}
window.renderWishlistEmptyState = renderWishlistEmptyState;

function renderWishlistLoading() {
    const container = document.getElementById('wishlist-items');
    if (!container) return;
    container.innerHTML = `
        <div class="wishlist-loading-state" style="grid-column:1/-1;">
            <div class="wishlist-spinner"></div>
            <div class="wishlist-loading-text">Loading saved places...</div>
        </div>`;
}
window.renderWishlistLoading = renderWishlistLoading;

function renderWishlistCards(items) {
    const container = document.getElementById('wishlist-items');
    if (!container) return;
    container.innerHTML = items.map(item => `
        <div class="wishlist-card" id="wish-item-${item.pid}"
             onclick="navigateToPlace(${item.pid})">
            <img class="wish-img"
                 src="/static/images/${item.folder}/1.jpg"
                 alt="${item.name}"
                 onerror="this.src='/static/icon-192.png'">
            <div class="wish-body">
                <div class="wish-name">${item.name}</div>
                <div class="wish-meta"><i class="fa-solid fa-location-dot" style="color:var(--forest);font-size:.7rem;margin-right:4px;"></i>Belagavi District</div>
                <button class="wish-remove"
                        onclick="event.stopPropagation();handleRemoveFromWishlist(${item.pid},'${(item.name || '').replace(/'/g,"\\'")}','${item.folder}');">
                    Remove
                </button>
            </div>
        </div>`).join('');
}
window.renderWishlistCards = renderWishlistCards;

async function handleRemoveFromWishlist(placeId, name, folder) {
    const cardEl = document.getElementById(`wish-item-${placeId}`);
    if (cardEl) cardEl.remove();

    if (window.userWishlistPlaceIds) {
        window.userWishlistPlaceIds.delete(String(placeId));
    }
    if (window.cachedWishlistPlaces) {
        window.cachedWishlistPlaces = window.cachedWishlistPlaces.filter(item => String(item.pid) !== String(placeId));
    }

    const container = document.getElementById('wishlist-items');
    const remainingCards = container ? container.querySelectorAll('.wishlist-card').length : 0;
    if (remainingCards === 0) {
        renderWishlistEmptyState();
    }

    await toggleWishlistFirestore(null, placeId, name, folder);
}
window.handleRemoveFromWishlist = handleRemoveFromWishlist;

function renderWishlistPage() {
    const container = document.getElementById('wishlist-items');
    if (!container) return;

    if (!window.fbAuth?.currentUser) {
        renderWishlistLoading();
        return;
    }

    if (window.wishlistStatus === 'loading') {
        renderWishlistLoading();
    } else if (window.wishlistStatus === 'loaded') {
        if (!window.cachedWishlistPlaces || window.cachedWishlistPlaces.length === 0) {
            renderWishlistEmptyState();
        } else {
            const currentCards = container.querySelectorAll('.wishlist-card').length;
            if (currentCards === 0) {
                renderWishlistCards(window.cachedWishlistPlaces);
            }
        }
    } else {
        renderWishlistLoading();
        loadWishlistFirestore();
    }
}
window.renderWishlistPage = renderWishlistPage;

// Load wishlist items directly from Firestore client-side
async function loadWishlistFirestore() {
    if (!window.fbAuth?.currentUser || !window.fbDb) return;
    const uid = window.fbAuth.currentUser.uid;
    const container = document.getElementById('wishlist-items');

    window.wishlistStatus = 'loading';
    if (container && (!window.cachedWishlistPlaces || window.cachedWishlistPlaces.length === 0)) {
        renderWishlistLoading();
    }

    const q = window.fbFirestoreMethods.query(
        window.fbFirestoreMethods.collection(window.fbDb, 'wishlist'),
        window.fbFirestoreMethods.where('user_id', '==', uid)
    );
    try {
        const snap = await window.fbFirestoreMethods.getDocs(q);
        if (!window.userWishlistPlaceIds) window.userWishlistPlaceIds = new Set();
        window.userWishlistPlaceIds.clear();

        if (snap.empty) {
            window.wishlistStatus = 'loaded';
            window.cachedWishlistPlaces = [];
            renderWishlistEmptyState();
            syncWishlistCardStates();
            refreshProfileStats();
            return;
        }

        const items = [];
        snap.forEach(doc => {
            const d = doc.data();
            const pid = d.placeId ?? d.place_id;
            if (pid !== undefined && pid !== null) {
                window.userWishlistPlaceIds.add(String(pid));
            }
            items.push({
                id:     doc.id,
                pid:    pid,
                name:   d.placeName ?? d.name ?? 'Destination',
                folder: d.folder_name ?? 'place'
            });
        });

        window.wishlistStatus = 'loaded';
        window.cachedWishlistPlaces = items;
        renderWishlistCards(items);

        syncWishlistCardStates();
        refreshProfileStats();
    } catch (err) {
        console.error('Firestore wishlist load failed:', err);
        window.wishlistStatus = 'error';
        if (container && (!window.cachedWishlistPlaces || window.cachedWishlistPlaces.length === 0)) {
            renderWishlistEmptyState();
        }
    }
}

async function syncAllPlacesToFirestore() {
    if (!window.fbDb || !window.allPlacesData?.length) return false;
    const { doc, setDoc } = window.fbFirestoreMethods;
    console.log(`[Firebase SPA] Syncing ${window.allPlacesData.length} places to Firestore (setDoc overwrite)...`);
    for (const place of window.allPlacesData) {
        if (!place.id || place.id < 1 || place.id > 31) continue;
        const docRef = doc(window.fbDb, 'places', String(place.id));
        await setDoc(docRef, place);
    }
    console.log('[Firebase SPA] All place documents overwritten from data.js.');
    return true;
}

// Phase 2A: Query and cache places collection directly from Cloud Firestore
async function loadPlacesFromFirestore() {
    if (!window.fbDb) return;
    try {
        const placesCol = window.fbFirestoreMethods.collection(window.fbDb, 'places');
        const storedVersion = localStorage.getItem('placesSyncVersion');
        const mustSync = storedVersion !== window.PLACES_SYNC_VERSION;

        if (mustSync) {
            await syncAllPlacesToFirestore();
            localStorage.setItem('placesSyncVersion', window.PLACES_SYNC_VERSION);
        }

        const snapshot = await window.fbFirestoreMethods.getDocs(placesCol);
        window.cachedPlacesFromFirestore = [];
        snapshot.forEach(docSnap => {
            const data = docSnap.data() || {};
            // Normalize types to avoid subtle string-vs-number drift.
            data.id = Number(data.id ?? docSnap.id);
            if (data.lat !== undefined) data.lat = Number(data.lat);
            if (data.lon !== undefined) data.lon = Number(data.lon);
            window.cachedPlacesFromFirestore.push(data);
        });

        if (snapshot.empty) {
            console.log('[Firebase SPA] Firestore places collection was empty; seeding from data.js...');
            await syncAllPlacesToFirestore();
            const seededSnapshot = await window.fbFirestoreMethods.getDocs(placesCol);
            window.cachedPlacesFromFirestore = [];
            seededSnapshot.forEach(docSnap => {
                const data = docSnap.data() || {};
                data.id = Number(data.id ?? docSnap.id);
                if (data.lat !== undefined) data.lat = Number(data.lat);
                if (data.lon !== undefined) data.lon = Number(data.lon);
                window.cachedPlacesFromFirestore.push(data);
            });
        } else {
            console.log(`[Firebase SPA] Loaded ${window.cachedPlacesFromFirestore.length} places from Firestore.`);
            // Safety net: fix any lat/lon drift without touching other fields in memory
            for (const p of window.allPlacesData) {
                const cached = window.cachedPlacesFromFirestore.find(c => c.id === p.id);
                if (cached && (cached.lat !== p.lat || cached.lon !== p.lon)) {
                    console.warn(`[Firebase SPA] Coords still mismatched for ID ${p.id}; re-syncing.`);
                    await window.fbFirestoreMethods.setDoc(
                        window.fbFirestoreMethods.doc(window.fbDb, 'places', String(p.id)),
                        p
                    );
                    cached.lat = p.lat;
                    cached.lon = p.lon;
                }
            }
        }
    } catch (err) {
        console.error('Firestore places load failed:', err);
    }
}

// Phase 3A: Geolocation & maps calculations state variables
window.BELAGAVI_LAT = 15.8497;
window.BELAGAVI_LON = 74.4977;
window.BELAGAVI_LABEL = 'Belagavi, Karnataka';

window.userLat = null;
window.userLng = null;
window.isFallbackOrigin = false;
window.destLat = null;
window.destLon = null;
window.placeName = '';
window.placeCategory = '';

// Dynamic Geolocation detection
window.detectUserLocation = function(destLat, destLon, placeName, category) {
    window.destLat = destLat;
    window.destLon = destLon;
    window.placeName = placeName;
    window.placeCategory = category;
    
    const statusEl = document.getElementById("route-origin-status");
    if (statusEl) statusEl.innerHTML = `<span class="spinner-border spinner-border-sm text-primary me-2"></span>Locating origin GPS...`;

    if (!navigator.geolocation) {
        console.log("[GPS] Geolocation not supported. Falling back to Belagavi.");
        window.useBelagaviFallback();
        return;
    }

    navigator.geolocation.getCurrentPosition(
        pos => {
            const lat = pos.coords.latitude;
            const lng = pos.coords.longitude;
            
            // Validate within India bounds to catch emulator defaults
            if (lat < 5 || lat > 37 || lng < 65 || lng > 98) {
                console.log("[GPS] Location outside India bounds. Falling back to Belagavi.");
                window.useBelagaviFallback();
            } else {
                window.userLat = lat;
                window.userLng = lng;
                window.isFallbackOrigin = false;
                if (statusEl) statusEl.innerHTML = `🟢 Live Location (${lat.toFixed(4)}, ${lng.toFixed(4)})`;
                window.calculateSmartRoute();
            }
        },
        err => {
            console.log("[GPS] Geolocation denied or failed. Falling back to Belagavi.");
            window.useBelagaviFallback();
        },
        { timeout: 6000, enableHighAccuracy: false }
    );
};

window.useBelagaviFallback = function() {
    window.userLat = window.BELAGAVI_LAT;
    window.userLng = window.BELAGAVI_LON;
    window.isFallbackOrigin = true;
    const statusEl = document.getElementById("route-origin-status");
    if (statusEl) statusEl.innerHTML = `🏢 Belagavi, Karnataka (Fallback origin)`;
    window.calculateSmartRoute();
};

window.getHaversineDistance = function(lat1, lon1, lat2, lon2) {
    const R = 6371; // km
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = 
        Math.sin(dLat/2) * Math.sin(dLat/2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * 
        Math.sin(dLon/2) * Math.sin(dLon/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
};

// Fail-safe dynamic routing engine (never fails silently, no routing errors)
window.calculateSmartRoute = async function() {
    if (!window.destLat || !window.destLon || !window.userLat) return;

    const distValEl = document.getElementById("smart-dist-val");
    const timeValEl = document.getElementById("smart-time-val");
    const routeNameEl = document.getElementById("smart-route-name");

    if (distValEl) distValEl.innerHTML = `<span class="spinner-border spinner-border-sm text-secondary"></span>`;
    if (timeValEl) timeValEl.innerHTML = `<span class="spinner-border spinner-border-sm text-secondary"></span>`;
    if (routeNameEl) routeNameEl.innerHTML = `Calculating...`;

    // Layer 1: Google Directions SDK (client-side directly inside browser with zero server proxy billing)
    if (window.google && window.google.maps) {
        try {
            const directionsService = new google.maps.DirectionsService();
            directionsService.route({
                origin: { lat: window.userLat, lng: window.userLng },
                destination: { lat: window.destLat, lng: window.destLon },
                travelMode: google.maps.TravelMode.DRIVING
            }, (response, status) => {
                if (status === 'OK' && response.routes && response.routes.length > 0) {
                    const route = response.routes[0];
                    const leg = route.legs[0];
                    const distanceText = leg.distance.text;
                    const durationText = leg.duration.text;
                    const routeSummary = route.summary || "NH48 / Highway Link";

                    if (distValEl) distValEl.textContent = distanceText;
                    if (timeValEl) timeValEl.textContent = durationText;
                    if (routeNameEl) routeNameEl.textContent = routeSummary;

                    const distanceKm = parseFloat(distanceText.replace(/[^\d.]/g, '')) || 0;
                    window.updateExpenseEstimates(distanceKm);
                    window.updateDynamicReachSection(distanceKm);
                } else {
                    window.useMathematicalRouteFallback("Google Maps Directions non-OK status: " + status);
                }
            });
            return;
        } catch (e) {
            console.log("[Route] Direct DirectionsService call failed:", e);
        }
    }

    // Layer 2: Legacy Local Server API proxy fallback (if active on localhost)
    try {
        const res = await fetch(`/api/directions?origin=${window.userLat},${window.userLng}&destination=${window.destLat},${window.destLon}`);
        if (res.ok) {
            const data = await res.json();
            if (data.status === 'OK' && data.routes && data.routes.length > 0) {
                const route = data.routes[0];
                const leg = route.legs[0];
                const distanceText = leg.distance.text;
                const durationText = leg.duration.text;
                const routeSummary = route.summary || "NH48 / Highway Link";

                if (distValEl) distValEl.textContent = distanceText;
                if (timeValEl) timeValEl.textContent = durationText;
                if (routeNameEl) routeNameEl.textContent = routeSummary;

                const distanceKm = parseFloat(distanceText.replace(/[^\d.]/g, '')) || 0;
                window.updateExpenseEstimates(distanceKm);
                window.updateDynamicReachSection(distanceKm);
                return;
            }
        }
    } catch (err) {
        console.log("[Route] Flask Directions API proxy failed:", err);
    }

    // Layer 3: High-fidelity mathematical road road estimation (Perfect fallback, never crashes)
    window.useMathematicalRouteFallback("All directions service nodes unconfigured or offline");
};

window.useMathematicalRouteFallback = function(reason) {
    console.log("[Route] Using mathematical fallback because:", reason);
    const distValEl = document.getElementById("smart-dist-val");
    const timeValEl = document.getElementById("smart-time-val");
    const routeNameEl = document.getElementById("smart-route-name");

    // Haversine straight line x 1.3 to approximate winding highway routes
    const straightLineDist = window.getHaversineDistance(window.userLat, window.userLng, window.destLat, window.destLon);
    const approxRoadDist = Math.max(1, Math.round(straightLineDist * 1.3 * 10) / 10);
    
    // Average travel speed ~40 km/h in ghat/rural regions
    const hours = approxRoadDist / 40;
    let durationText = "";
    if (hours < 1) {
        durationText = `${Math.round(hours * 60)} mins`;
    } else {
        const h = Math.floor(hours);
        const m = Math.round((hours - h) * 60);
        durationText = `${h} hr ${m} mins`;
    }

    if (distValEl) distValEl.textContent = `${approxRoadDist} km`;
    if (timeValEl) timeValEl.textContent = durationText;
    if (routeNameEl) routeNameEl.textContent = "Direct Road Route (Estimated)";

    window.updateExpenseEstimates(approxRoadDist);
    window.updateDynamicReachSection(approxRoadDist);
};

window.updateExpenseEstimates = function(distanceKm) {
    const costBikeEl = document.getElementById("cost-bike");
    const costCarEl = document.getElementById("cost-car");
    const costBusEl = document.getElementById("cost-bus");

    if (costBikeEl) costBikeEl.textContent = `₹${Math.round(distanceKm * 2.5)}`;
    if (costCarEl) costCarEl.textContent = `₹${Math.round(distanceKm * 7.0)}`;
    if (costBusEl) costBusEl.textContent = `₹${Math.round(distanceKm * 1.5)}`;
};

// Dynamic reach text: detects closest known city from user GPS and returns context-aware route advice
window.getDynamicReachText = function(userLat, userLng, isFallback, placeName, approxDistKm) {
    const knownCities = [
        { name: 'Belagavi', lat: 15.8497, lng: 74.4977 },
        { name: 'Bengaluru', lat: 12.9716, lng: 77.5946 },
        { name: 'Hubli', lat: 15.3647, lng: 75.1240 },
        { name: 'Pune', lat: 18.5204, lng: 73.8567 }
    ];

    let closestCity = 'Belagavi';
    if (!isFallback && userLat && userLng) {
        let minDist = Infinity;
        for (const c of knownCities) {
            const d = window.getHaversineDistance(userLat, userLng, c.lat, c.lng);
            if (d < minDist) { minDist = d; closestCity = c.name; }
        }
    }

    const dist = approxDistKm.toFixed(1);
    const routes = {
        'Belagavi':  `Departure from Belagavi. To reach ${placeName} (approx. ${dist} km away):\nTake a local bus from CBT, hire an auto-rickshaw, or self-drive via local routes.`,
        'Bengaluru': `Departure from Bengaluru. To reach ${placeName} (approx. ${dist} km away):\nTake an overnight train (e.g., Rani Chennamma Express) or a KSRTC/private sleeper bus from Bengaluru to Belagavi, then proceed to the destination.`,
        'Hubli':     `Departure from Hubli. To reach ${placeName} (approx. ${dist} km away):\nTake a KSRTC express bus or a passenger/express train from Hubli to Belagavi, then proceed to the destination.`,
        'Pune':      `Departure from Pune. To reach ${placeName} (approx. ${dist} km away):\nTake an express train or NH48 KSRTC/private bus from Pune to Belagavi, then proceed to the destination.`
    };
    return routes[closestCity] || `Departure from your live location. To reach ${placeName} (approx. ${dist} km away):\nNavigate using state highway networks. Ensure vehicle suitability for local terrain.`;
};

// Dynamic travel mode suggestion based on distance
window.getTravelModeSuggestion = function(distanceKm) {
    if (distanceKm <= 50)  return '0–50 km range:\n🚲 Bike · 🛺 Auto · 🚌 Local Bus · 🚗 Self Drive';
    if (distanceKm <= 250) return '50–250 km range:\n🚌 KSRTC/Private Bus · 🚗 Self Drive · 🏘️ Nearest Major Town Route';
    return '250+ km range:\n🚆 Train recommended';
};

// Injects dynamic reach text into the How to Reach accordion section
window.updateDynamicReachSection = function(distanceKm) {
    const el = document.getElementById('dynamic-reach-text');
    if (!el) return;
    const reachText = window.getDynamicReachText(
        window.userLat, window.userLng, window.isFallbackOrigin,
        window.placeName, distanceKm
    );
    const modeText = window.getTravelModeSuggestion(distanceKm);
    el.innerHTML = `
        <div class="mb-3">
            <div class="fw-bold small text-dark mb-1">📍 From Your Location</div>
            <div class="small text-muted" style="white-space:pre-line;">${reachText}</div>
        </div>
        <div>
            <div class="fw-bold small text-dark mb-1">🚦 Travel Mode Recommendation</div>
            <div class="small text-muted" style="white-space:pre-line;">${modeText}</div>
        </div>
    `;
};

window.startNavigation = function() {
    let origin = window.isFallbackOrigin ? window.BELAGAVI_LABEL : `${window.userLat},${window.userLng}`;
    const destination = `${window.destLat},${window.destLon}`;
    const url = `https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&travelmode=driving`;
    window.open(url, '_blank');
};

// Phase 3A: Live Open-Meteo Weather API integration (fully client-side)
window.fetchLiveWeather = async function(lat, lon, category) {
    const weatherDiv = document.getElementById("weather-content");
    const advisoryDescEl = document.getElementById("smart-advisory-desc");
    if (!weatherDiv) return;

    try {
        const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true`);
        if (res.ok) {
            const data = await res.json();
            if (data.current_weather) {
                const temp = data.current_weather.temperature;
                const code = data.current_weather.weathercode;
                
                // Real-time advisories based on weather codes
                let weatherAlert = "✨ Optimal Weather: Conditions are optimal for outdoor sightseeing today!";
                let weatherBadge = "✨ Perfect weather for outdoors!";
                
                if (code >= 51 && code <= 67) {
                    weatherAlert = "🌧️ Rain Alert: Light rain or drizzle detected. Nature trails and ghat road surfaces may be highly slippery. Drive with care.";
                    weatherBadge = "🌧️ Light rain alert";
                } else if (code >= 71 && code <= 86) {
                    weatherAlert = "⛈️ Storm Alert: Heavy rain active. Access roads to waterfalls and ghat sections may experience blockages. Postpone hikes.";
                    weatherBadge = "⛈️ Storm alert";
                } else if (temp > 34) {
                    weatherAlert = "🔥 Midday Heat Warning: High heat index. Wear sunscreen/hats, carry plenty of hydration, and avoid peak sun hikes.";
                    weatherBadge = "🔥 Midday heat alert";
                }

                // Update Widget HTML
                weatherDiv.innerHTML = `
                    <div class="d-flex align-items-center justify-content-center gap-3">
                        <span style="font-size: 2.2rem;">🌡️</span>
                        <div class="text-start">
                            <h4 class="fw-bold mb-0 text-dark">${temp}°C</h4>
                            <small class="text-secondary fw-bold">${weatherBadge}</small>
                        </div>
                    </div>
                `;

                // Update Advisory HTML
                if (advisoryDescEl) {
                    const categoryTip = getCategoryAdvisoryTip(category);
                    advisoryDescEl.innerHTML = `
                        <div class="mb-2">${weatherAlert}</div>
                        <div class="fw-bold text-dark mb-1">Local Travel Advice</div>
                        <div class="text-muted" style="white-space: pre-line;">${categoryTip}</div>
                    `;
                }
                return;
            }
        }
    } catch (err) {
        console.log("[Weather] Direct Open-Meteo fetch failed:", err);
    }

    // Weather Offline Fallback
    weatherDiv.innerHTML = `<small class="text-muted">Live weather forecast unavailable.</small>`;
    if (advisoryDescEl) {
        const categoryTip = getCategoryAdvisoryTip(category);
        advisoryDescEl.innerHTML = `
            <div class="mb-2">⚠️ Weather Offline: Could not connect to real-time weather stations. Assess the sky before departing!</div>
            <div class="fw-bold text-dark mb-1">Local Travel Advice</div>
            <div class="text-muted" style="white-space: pre-line;">${categoryTip}</div>
        `;
    }
};

function getCategoryAdvisoryTip(category) {
    const tips = {
        waterfall: "🌊 Waterfall Safety:\nRocks may be slippery. Avoid standing near cliff edges during monsoon seasons.",
        temple: "🛕 Temple Visit Tip:\nRemove footwear, dress modestly, and visit during morning or evening for a peaceful experience.",
        fort: "🏰 Heritage Site Tip:\nCarry water and wear comfortable shoes for walking long distances.",
        nature: "🌿 Nature Trekking Tip:\nStay on marked paths, carry drinking water, and avoid isolated areas after sunset.",
        lake: "🌅 Lake Visit Tip:\nBest visited during sunrise or sunset. Be cautious near slippery banks.",
        reservoir: "🌅 Reservoir Visit Tip:\nEnjoy the scenic views safely. Do not enter the water and stay on designated paths.",
        park: "🌿 Park Visit Tip:\nKeep the surroundings clean. Walk along paved paths and enjoy the scenery.",
        wildlife: "🦅 Wildlife Sanctuary Tip:\nKeep vehicle windows rolled up, do not feed or disturb wild animals, and stick to safari tracks."
    };
    return tips[category.toLowerCase()] || "📍 Belagavi Discovery:\nRespect local culture, keep locations clean, and discover responsibly!";
}

// Global contextual navigation to AI Guide
window.openAIGuideForPlace = function(placeId) {
    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    const p = sourceData && sourceData.find(x => String(x.id) === String(placeId));
    const targetPlace = p || (window.currentPlaceData && String(window.currentPlaceData.id) === String(placeId) ? window.currentPlaceData : { id: placeId, name: 'this destination' });
    if (window.setAIGuideContext) {
        window.setAIGuideContext(targetPlace);
    }
    if (window.show) {
        window.show('ai-assistant');
    }
};

// ── Destination Photo Gallery & Lightbox Controller ─────────
const DESTINATION_IMAGE_COUNTS = {
    belagavi_fort: 2,
    bhimagad_wildlife_sanctury: 3,
    bird_sanctuary_gahatprabha: 3,
    chorla_ghat: 3,
    godachimalaki_falls: 3,
    gokak: 5,
    hidkal_dam_hukkeri: 4,
    huliyamma_devi_temple: 3,
    jalavane_falls: 5,
    jamboti_falls: 2,
    jamboti_hills: 2,
    kamalbasadi_belagavi: 6,
    kapileshwar_temple_belagavi: 2,
    khanapur_forest: 2,
    kittur_fort: 3,
    kopeshwara_temple: 3,
    kote_kere_belagavi: 4,
    military_mahadev_temple_belagavi: 4,
    navilutirtha_reservoir: 1,
    parasgad_fort: 2,
    rakaskop_reservoir: 2,
    ramdurga_fort: 2,
    sada_falls: 2,
    shankarling_temple_sankeshwar: 5,
    siddheshwar_temple_belagavi: 3,
    surala_falls: 2,
    tilari_forest_belagavi: 4,
    vajrapoha_falls: 3,
    vidhansoudha_belagavi: 4,
    yellamadevi_temple_savandati: 3,
    yellur_fort: 4
};

function getPlaceImages(place) {
    if (!place) return [];
    if (Array.isArray(place.images) && place.images.length > 0) {
        return place.images;
    }
    const folder = place.folder_name || 'place';
    const count = (DESTINATION_IMAGE_COUNTS && DESTINATION_IMAGE_COUNTS[folder]) || place.image_count || 1;
    const list = [];
    for (let i = 1; i <= count; i++) {
        list.push(`/static/images/${folder}/${i}.jpg`);
    }
    return list;
}

let _currentPlaceGalleryImages = [];
let _currentPlaceGalleryIndex = 0;
let _currentPlaceGalleryName = '';
let _placeGalleryLightboxEl = null;

function _getOrCreatePlaceGalleryLightbox() {
    if (_placeGalleryLightboxEl) return _placeGalleryLightboxEl;
    let lb = document.getElementById('place-gallery-lightbox');
    if (!lb) {
        lb = document.createElement('div');
        lb.id = 'place-gallery-lightbox';
        lb.className = 'place-lightbox';
        lb.setAttribute('role', 'dialog');
        lb.setAttribute('aria-modal', 'true');
        lb.setAttribute('aria-label', 'Destination Photo Gallery');
        lb.innerHTML = `
            <div class="place-lightbox-backdrop" aria-hidden="true"></div>
            <div class="place-lightbox-dialog">
                <div class="place-lightbox-topbar">
                    <div class="place-lightbox-counter" id="place-lightbox-counter">1 / 1</div>
                    <button type="button" class="place-lightbox-close" id="place-lightbox-close" aria-label="Close photo gallery">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>
                <div class="place-lightbox-stage">
                    <button type="button" class="place-lightbox-nav place-lightbox-prev" id="place-lightbox-prev" aria-label="Previous photo">
                        <i class="fa-solid fa-chevron-left"></i>
                    </button>
                    <div class="place-lightbox-img-wrap">
                        <img class="place-lightbox-img" id="place-lightbox-img" src="" alt="Destination Photo" onerror="this.src='/static/icon-192.png'" />
                    </div>
                    <button type="button" class="place-lightbox-nav place-lightbox-next" id="place-lightbox-next" aria-label="Next photo">
                        <i class="fa-solid fa-chevron-right"></i>
                    </button>
                </div>
                <div class="place-lightbox-caption" id="place-lightbox-caption"></div>
            </div>
        `;
        document.body.appendChild(lb);

        lb.querySelector('.place-lightbox-backdrop').addEventListener('click', closePlaceGallery);

        lb.querySelector('#place-lightbox-close').addEventListener('click', function(e) {
            e.stopPropagation();
            closePlaceGallery();
        });

        lb.querySelector('#place-lightbox-prev').addEventListener('click', function(e) {
            e.stopPropagation();
            stepPlaceGallery(-1);
        });

        lb.querySelector('#place-lightbox-next').addEventListener('click', function(e) {
            e.stopPropagation();
            stepPlaceGallery(1);
        });

        lb.querySelector('.place-lightbox-stage').addEventListener('click', function(e) {
            if (e.target === this || e.target.classList.contains('place-lightbox-img-wrap')) {
                closePlaceGallery();
            }
        });
    }
    _placeGalleryLightboxEl = lb;
    return _placeGalleryLightboxEl;
}

function _updatePlaceGalleryView() {
    const lb = _getOrCreatePlaceGalleryLightbox();
    if (!_currentPlaceGalleryImages || _currentPlaceGalleryImages.length === 0) return;
    const total = _currentPlaceGalleryImages.length;
    const currentSrc = _currentPlaceGalleryImages[_currentPlaceGalleryIndex];

    const img = lb.querySelector('#place-lightbox-img');
    const counter = lb.querySelector('#place-lightbox-counter');
    const caption = lb.querySelector('#place-lightbox-caption');
    const prevBtn = lb.querySelector('#place-lightbox-prev');
    const nextBtn = lb.querySelector('#place-lightbox-next');

    img.src = currentSrc;
    img.alt = `${_currentPlaceGalleryName} - Photo ${_currentPlaceGalleryIndex + 1}`;

    counter.textContent = `${_currentPlaceGalleryIndex + 1} / ${total}`;
    caption.textContent = _currentPlaceGalleryName;

    if (total <= 1) {
        prevBtn.style.display = 'none';
        nextBtn.style.display = 'none';
    } else {
        prevBtn.style.display = 'flex';
        nextBtn.style.display = 'flex';
    }
}

function openPlaceGallery(index) {
    if (!_currentPlaceGalleryImages || _currentPlaceGalleryImages.length === 0) {
        if (window.currentPlaceData) {
            _currentPlaceGalleryImages = getPlaceImages(window.currentPlaceData);
            _currentPlaceGalleryName = window.currentPlaceData.name || '';
        }
    }
    if (!_currentPlaceGalleryImages || _currentPlaceGalleryImages.length === 0) return;

    _currentPlaceGalleryIndex = typeof index === 'number' ? index : 0;
    if (_currentPlaceGalleryIndex < 0) _currentPlaceGalleryIndex = 0;
    if (_currentPlaceGalleryIndex >= _currentPlaceGalleryImages.length) {
        _currentPlaceGalleryIndex = _currentPlaceGalleryImages.length - 1;
    }

    const lb = _getOrCreatePlaceGalleryLightbox();
    _updatePlaceGalleryView();

    lb.classList.add('place-lightbox-active');
    document.body.classList.add('place-gallery-open');

    const closeBtn = lb.querySelector('#place-lightbox-close');
    if (closeBtn) closeBtn.focus();

    document.addEventListener('keydown', _handlePlaceGalleryKeyDown);
}

function closePlaceGallery() {
    if (!_placeGalleryLightboxEl || !_placeGalleryLightboxEl.classList.contains('place-lightbox-active')) return;
    _placeGalleryLightboxEl.classList.remove('place-lightbox-active');
    document.body.classList.remove('place-gallery-open');
    document.removeEventListener('keydown', _handlePlaceGalleryKeyDown);
}

function stepPlaceGallery(dir) {
    if (!_currentPlaceGalleryImages || _currentPlaceGalleryImages.length <= 1) return;
    const total = _currentPlaceGalleryImages.length;
    _currentPlaceGalleryIndex = (_currentPlaceGalleryIndex + dir + total) % total;
    _updatePlaceGalleryView();
}

function _handlePlaceGalleryKeyDown(e) {
    if (e.key === 'Escape') {
        e.preventDefault();
        closePlaceGallery();
    } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        stepPlaceGallery(-1);
    } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        stepPlaceGallery(1);
    }
}

window.openPlaceGallery = openPlaceGallery;
window.closePlaceGallery = closePlaceGallery;
window.stepPlaceGallery = stepPlaceGallery;

// Phase 2A: Compile and render high-fidelity details card client-side inside #place-details-content
async function renderPlaceDetailsFirestore(placeId) {
    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (!sourceData) { console.warn('No place data available'); return; }

    const place = sourceData.find(p => p && String(p.id) === String(placeId));
    if (!place) { console.warn('Place not found:', placeId); return; }

    // Store for navigation / AI context
    window.currentPlaceData = place;

    // Prepare gallery photos
    const photos = getPlaceImages(place);
    _currentPlaceGalleryImages = photos;
    _currentPlaceGalleryName = place.name;

    // Build transport HTML
    let transportHtml = '';
    if (place.transport && typeof place.transport === 'object') {
        if (place.transport.bus_routes?.length) {
            transportHtml += `<div class="transport-badge"><i class="fa-solid fa-bus"></i> Bus Routes</div>`;
            place.transport.bus_routes.forEach(r => {
                const tags = [r.route_no, r.via, r.operator, r.schedule].filter(Boolean);
                transportHtml += `
                    <div class="bus-route-card">
                        <div class="bus-route-name">${r.route_name || r.route_no || 'Route'}</div>
                        <div class="bus-tags">${tags.map(t => `<span class="bus-tag">${t}</span>`).join('')}</div>
                    </div>`;
            });
        }
        ['train','auto_taxi','drive'].forEach(mode => {
            if (!place.transport[mode]) return;
            const labels = { train:'By Train', auto_taxi:'Auto / Taxi', drive:'By Car' };
            const icons  = { train:'fa-train', auto_taxi:'fa-taxi', drive:'fa-car' };
            transportHtml += `
                <div class="transport-mode">
                    <div class="transport-mode-label">
                        <i class="fa-solid ${icons[mode]}" style="color:var(--forest);"></i>${labels[mode]}
                    </div>
                    <div class="transport-mode-text">${place.transport[mode]}</div>
                </div>`;
        });
    } else {
        transportHtml = `<div class="transport-mode-text" style="white-space:pre-line">${place.how_to_reach || 'Route info not available.'}</div>`;
    }

    const city = place.city || 'Belagavi District';
    const isSaved = (window.userWishlistPlaceIds && window.userWishlistPlaceIds.has(String(place.id))) || false;
    const contentEl = document.getElementById('place-details-content');
    if (!contentEl) return;

    const galleryThumbsHtml = photos.map((src, idx) => {
        const isMain = (photos.length === 3 && idx === 0) ? ' g-main' : '';
        return `
            <div class="gallery-thumb-item${isMain}"
                 onclick="openPlaceGallery(${idx})"
                 role="button"
                 tabindex="0"
                 onkeydown="if(event.key==='Enter'||event.key===' ') openPlaceGallery(${idx})"
                 aria-label="View photo ${idx + 1} of ${place.name}">
                <img src="${src}"
                     alt="${place.name} photo ${idx + 1}"
                     loading="lazy"
                     onerror="this.src='/static/icon-192.png'">
                <div class="gallery-thumb-overlay" aria-hidden="true">
                    <i class="fa-solid fa-expand"></i>
                </div>
            </div>
        `;
    }).join('');

    contentEl.innerHTML = `
    <div class="place-detail-wrap">

        <!-- Hero Banner -->
        <div class="detail-hero" onclick="openPlaceGallery(0)" role="button" tabindex="0" title="Click to view full photo gallery" style="cursor:pointer;" onkeydown="if(event.key==='Enter'||event.key===' ') openPlaceGallery(0)">
            <img src="/static/images/${place.folder_name}/1.jpg"
                 alt="${place.name}"
                 onerror="this.src='/static/icon-192.png'">
            <div class="detail-hero-overlay"></div>

            <button class="detail-back"
                    onclick="event.stopPropagation(); show('home'); window.location.hash='#home';"
                    aria-label="Back to destinations">
                <i class="fa-solid fa-arrow-left"></i>
            </button>

            <div class="detail-actions" onclick="event.stopPropagation()">
                <button class="detail-btn detail-btn-save save-btn-${place.id} ${isSaved ? 'saved' : ''}"
                        onclick="toggleWishlist(this,${place.id},'${place.name.replace(/'/g,"\\'")}','${place.folder_name}')"
                        aria-label="${isSaved ? 'Remove ' + place.name + ' from saved places' : 'Save ' + place.name}"
                        aria-pressed="${isSaved ? 'true' : 'false'}">
                    <i class="fa-${isSaved ? 'solid' : 'regular'} fa-heart me-1"></i> ${isSaved ? 'Saved' : 'Save'}
                </button>
                <button class="detail-btn detail-btn-nav"
                        onclick="startNavigation()"
                        aria-label="Get directions to ${place.name}">
                    <i class="fa-solid fa-location-arrow"></i> Directions
                </button>
            </div>

            <div class="hero-photo-badge" onclick="event.stopPropagation(); openPlaceGallery(0);" role="button" tabindex="0" aria-label="View all photos">
                <i class="fa-solid fa-images"></i> ${photos.length} Photo${photos.length > 1 ? 's' : ''}
            </div>

            <div class="detail-hero-identity" onclick="event.stopPropagation(); openPlaceGallery(0);">
                <span class="detail-category-badge">${place.category}</span>
                <h1 class="detail-place-name">${place.name}</h1>
                <div class="detail-place-loc">
                    <i class="fa-solid fa-location-dot" style="font-size:.75rem;"></i>
                    ${city} · Karnataka Tourism Verified
                </div>
            </div>
        </div>

        <!-- Content grid -->
        <div class="detail-content">

            <!-- Main column -->
            <div>

                <!-- About -->
                <div class="detail-about-card">
                    <div class="detail-section-label">About this place</div>
                    <p class="detail-about-text">${place.history || place.description || 'Local highlight in Belagavi district.'}</p>

                    <!-- Deep history accordion -->
                    ${place.detailed_history ? `
                    <div class="detail-accordion" style="margin-top:20px;">
                        <div class="accordion-item">
                            <button class="accordion-trigger" id="acc-history-btn" onclick="toggleAccordion('acc-history-btn','acc-history-body')">
                                <span>📖 Deep History</span>
                                <i class="fa-solid fa-chevron-down acc-arrow"></i>
                            </button>
                            <div class="accordion-body" id="acc-history-body" style="white-space:pre-line;">
                                ${place.detailed_history}
                            </div>
                        </div>
                    </div>` : ''}

                    <!-- Transport accordion -->
                    <div class="detail-accordion" style="margin-top:${place.detailed_history ? '8px' : '20px'};">
                        <div class="accordion-item">
                            <button class="accordion-trigger" id="acc-reach-btn" onclick="toggleAccordion('acc-reach-btn','acc-reach-body')">
                                <span>🚌 How to Reach & Local Tips</span>
                                <i class="fa-solid fa-chevron-down acc-arrow"></i>
                            </button>
                            <div class="accordion-body" id="acc-reach-body">
                                ${transportHtml}
                                <div id="dynamic-reach-text" style="margin-top:10px;font-size:.82rem;color:var(--ink-muted);">
                                    📍 Detecting your location…
                                </div>
                                ${place.local_tips ? `
                                <div style="margin-top:14px;padding-top:14px;border-top:1px solid var(--border-light);">
                                    <div style="font-size:.72rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--ink-muted);margin-bottom:6px;">Local Tips</div>
                                    <div style="font-size:.845rem;color:var(--ink-secondary);line-height:1.65;">${place.local_tips}</div>
                                </div>` : ''}
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Smart Route Card -->
                <div class="route-card">
                    <div class="route-card-title">
                        <i class="fa-solid fa-route" style="color:var(--forest);"></i>
                        Smart Route Planner
                    </div>

                    <div class="origin-row">
                        <div>
                            <div class="origin-label">Your Departure</div>
                            <div class="origin-val" id="route-origin-status">Detecting location…</div>
                        </div>
                        <button class="btn-retry-gps" onclick="detectUserLocation(${place.lat},${place.lon},'${place.name.replace(/'/g,"\\'")}','${place.category}')">
                            <i class="fa-solid fa-location-crosshairs"></i> Retry
                        </button>
                    </div>

                    <div class="route-stats">
                        <div class="route-stat">
                            <div class="route-stat-label">Distance</div>
                            <div class="route-stat-value" id="smart-dist-val">—</div>
                        </div>
                        <div class="route-stat">
                            <div class="route-stat-label">Drive Time</div>
                            <div class="route-stat-value" id="smart-time-val">—</div>
                        </div>
                    </div>

                    <!-- Fuel cost accordion -->
                    <div class="detail-accordion">
                        <div class="accordion-item">
                            <button class="accordion-trigger" id="acc-fuel-btn" onclick="toggleAccordion('acc-fuel-btn','acc-fuel-body')">
                                <span>⛽ Travel Cost Estimate</span>
                                <i class="fa-solid fa-chevron-down acc-arrow"></i>
                            </button>
                            <div class="accordion-body" id="acc-fuel-body">
                                <div class="expense-estimator">
                                    <div class="est-row">
                                        <span class="est-label">🏍️ Bike</span>
                                        <span class="est-val" id="cost-bike">₹0</span>
                                    </div>
                                    <div class="est-row">
                                        <span class="est-label">🚗 Car</span>
                                        <span class="est-val" id="cost-car">₹0</span>
                                    </div>
                                    <div class="est-row">
                                        <span class="est-label">🚌 Bus (KSRTC)</span>
                                        <span class="est-val" id="cost-bus">₹0</span>
                                    </div>
                                    <div class="est-note">Approx. rates: Bike ₹2.5/km · Car ₹7/km · Bus ₹1.5/km</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <button class="btn-start-nav" onclick="startNavigation()">
                        <i class="fa-solid fa-location-arrow"></i> Start Navigation
                    </button>
                </div>

                <!-- Gallery -->
                <div class="detail-gallery">
                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;">
                        <div class="detail-section-label" style="margin-bottom:0;">Photo Gallery</div>
                        <span style="font-size:.78rem;color:var(--ink-muted);font-weight:500;">
                            ${photos.length} photo${photos.length > 1 ? 's' : ''} · Click to view large
                        </span>
                    </div>
                    <div class="gallery-grid grid-${Math.min(photos.length, 6)}">
                        ${galleryThumbsHtml}
                    </div>
                </div>

                <!-- AI Guide CTA Card -->
                <div class="dest-ai-cta-card">
                    <div class="dest-ai-cta-badge">
                        <i class="fa-solid fa-sparkles"></i> AI Guide
                    </div>
                    <h3 class="dest-ai-cta-title">Curious about ${place.name}?</h3>
                    <p class="dest-ai-cta-desc">Ask the Belagavi AI Guide for more information about this destination.</p>
                    <button class="dest-ai-cta-btn" type="button" onclick="openAIGuideForPlace('${place.id}')">
                        Ask AI about ${place.name} <i class="fa-solid fa-arrow-right" style="margin-left:8px;"></i>
                    </button>
                </div>

                <!-- Reviews -->
                <div class="detail-reviews" id="reviews-section">
                    <div class="reviews-header">
                        <div class="reviews-title">Reviews</div>
                        <div class="avg-rating" id="avg-rating-display">Loading…</div>
                    </div>

                    <!-- Write review form -->
                    <div class="review-form">
                        <div class="star-input" id="star-input">
                            <i class="fa-regular fa-star review-star" data-value="1" onclick="selectStar(1)"></i>
                            <i class="fa-regular fa-star review-star" data-value="2" onclick="selectStar(2)"></i>
                            <i class="fa-regular fa-star review-star" data-value="3" onclick="selectStar(3)"></i>
                            <i class="fa-regular fa-star review-star" data-value="4" onclick="selectStar(4)"></i>
                            <i class="fa-regular fa-star review-star" data-value="5" onclick="selectStar(5)"></i>
                        </div>
                        <textarea class="review-textarea" id="review-text"
                                  placeholder="Share your experience at ${place.name}…" rows="3"></textarea>
                        <div id="review-feedback"></div>
                        <button class="btn-post-review" id="submit-review-btn" onclick="submitReview()">
                            <i class="fa-solid fa-paper-plane" style="margin-right:6px;"></i>Post Review
                        </button>
                    </div>

                    <div id="reviews-list">
                        <div style="text-align:center;padding:20px;color:var(--ink-muted);font-size:.875rem;">
                            <span style="display:inline-block;width:16px;height:16px;border:2px solid var(--border);border-top-color:var(--forest);border-radius:50%;animation:spin .8s linear infinite;margin-right:8px;vertical-align:middle;"></span>
                            Loading reviews…
                        </div>
                    </div>
                </div>

                <!-- Nearby Spots -->
                <div class="detail-nearby">
                    <div class="detail-section-label">Nearby Destinations</div>
                    <div class="nearby-grid" id="recommended-container"></div>
                </div>

            </div><!-- /main col -->

            <!-- Sidebar -->
            <div>

                <!-- Quick Facts -->
                <div class="quick-facts">
                    <div class="quick-facts-title">Quick Facts</div>
                    <div class="fact-row">
                        <span class="fact-label"><i class="fa-regular fa-clock" style="color:var(--forest);"></i>Visit Duration</span>
                        <span class="fact-value">${place.visit_duration || '2 Hours'}</span>
                    </div>
                    <div class="fact-row">
                        <span class="fact-label"><i class="fa-solid fa-indian-rupee-sign" style="color:var(--forest);"></i>Entry Fee</span>
                        <span class="fact-value">${place.entry_fee || 'Free'}</span>
                    </div>
                    <div class="fact-row">
                        <span class="fact-label"><i class="fa-regular fa-calendar-days" style="color:var(--forest);"></i>Best Time</span>
                        <span class="fact-value">${place.best_time || 'Year-round'}</span>
                    </div>
                    ${place.lat ? `
                    <div class="fact-row">
                        <span class="fact-label"><i class="fa-solid fa-location-dot" style="color:var(--forest);"></i>Coordinates</span>
                        <span class="fact-value" style="font-size:.78rem;">${parseFloat(place.lat).toFixed(4)}, ${parseFloat(place.lon).toFixed(4)}</span>
                    </div>` : ''}
                </div>

                <!-- Weather Widget -->
                <div class="weather-widget" id="weather-widget" style="margin-top:16px;">
                    <div class="weather-label">Live Weather</div>
                    <div id="weather-content" style="color:var(--ink-muted);font-size:.82rem;">
                        <span style="display:inline-block;width:14px;height:14px;border:2px solid var(--border);border-top-color:var(--forest);border-radius:50%;animation:spin .8s linear infinite;margin-right:6px;vertical-align:middle;"></span>
                        Loading…
                    </div>
                </div>

            </div><!-- /sidebar -->

        </div><!-- /detail-content -->

    </div><!-- /place-detail-wrap -->
    `;

    // Re-trigger tab button events
    const triggerEl = document.querySelectorAll('#placeTab button');
    triggerEl.forEach(tabEl => {
        tabEl.addEventListener('click', (event) => {
            event.preventDefault();
            const targetSelector = tabEl.getAttribute('data-bs-target');
            document.querySelectorAll('#placeTab .nav-link').forEach(btn => btn.classList.remove('active'));
            tabEl.classList.add('active');
            document.querySelectorAll('#placeTabContent .tab-pane').forEach(pane => pane.classList.remove('show', 'active'));
            const pane = document.querySelector(targetSelector);
            if (pane) pane.classList.add('show', 'active');
        });
    });

    // Nearby spots
    const recContainer = document.getElementById('recommended-container');
    const sourceDataAll = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (recContainer && sourceDataAll) {
        const baseLat = Number(place.lat);
        const baseLon = Number(place.lon);
        const recommended = sourceDataAll
            .filter(p => p && p.id !== place.id && p.lat !== undefined && p.lon !== undefined)
            .map(p => ({ place: p, distKm: window.getHaversineDistance(baseLat, baseLon, Number(p.lat), Number(p.lon)) }))
            .sort((a, b) => a.distKm - b.distKm)
            .slice(0, 3)
            .map(x => x.place);

        if (recommended.length === 0) {
            recContainer.innerHTML = `<p style="font-size:.82rem;color:var(--ink-muted);grid-column:1/-1;">No nearby spots found.</p>`;
        } else {
            recContainer.innerHTML = recommended.map(p => `
                <a class="nearby-card" href="#place-${p.id}"
                   onclick="navigateToPlace(${p.id});return false;">
                    <img src="/static/images/${p.folder_name}/1.jpg"
                         alt="${p.name}"
                         onerror="this.src='/static/icon-192.png'">
                    <div class="nearby-card-body">
                        <div class="nearby-card-name">${p.name}</div>
                        <div class="nearby-card-cat">${p.category}</div>
                    </div>
                </a>`).join('');
        }
    }

    // Star selector for reviews
    window.selectStar = function(val) {
        document.querySelectorAll('#star-input .review-star').forEach((star, i) => {
            star.className = 'fa-' + (i < val ? 'solid' : 'regular') + ' fa-star review-star' + (i < val ? ' active' : '');
            star.dataset.value = i + 1;
        });
        document.getElementById('star-input').dataset.selected = val;
    };

    // Trigger geolocation, weather, reviews
    setTimeout(() => {
        if (window.detectUserLocation) window.detectUserLocation(place.lat, place.lon, place.name, place.category);
        if (window.fetchLiveWeather) window.fetchLiveWeather(place.lat, place.lon, place.category);
        if (window.initReviews) window.initReviews(window.fbDb, window.fbAuth, place.id);
    }, 50);
}


function initReactiveSPAAuth() {
    if (!window.fbAuthMethods || !window.fbAuth) return;

    window.fbAuthMethods.onAuthStateChanged(window.fbAuth, async (user) => {
        const loader = document.getElementById('splash-loader');
        const authContainer = document.getElementById('auth-container');
        const appView = document.getElementById('app-view');

        if (user) {
            console.log("[Firebase SPA] User is logged in:", user.email);
            
            // Sync user object globally
            window.currentUser = user;
            if (window.onAIAuthChange) {
                window.onAIAuthChange(user);
            }

            // Toggle screens immediately so user never feels frozen or stuck
            if (authContainer) authContainer.style.display = "none";
            if (appView) appView.style.display = "block";

            // Bind profile stats visually
            const avatar = document.getElementById('profile-avatar');
            const nameEl = document.getElementById('profile-username');
            const emailEl = document.getElementById('profile-email');
            const navAvatarEl = document.getElementById('nav-avatar-char');
            const navUsernameEl = document.getElementById('nav-username-short');
            
            const displayName = user.displayName || (user.email ? user.email.split('@')[0] : 'Traveller');
            const initial = displayName.charAt(0).toUpperCase();

            if (avatar) avatar.textContent = initial;
            if (nameEl) nameEl.textContent = displayName;
            if (emailEl) emailEl.innerHTML = `<i class="fa-solid fa-envelope me-1"></i> ${user.email || ''}`;

            if (navAvatarEl) navAvatarEl.textContent = initial;
            if (navUsernameEl) navUsernameEl.textContent = displayName.split(' ')[0];

            // Default view to home if not hashed or hash is unrecognised
            const knownSections = ['home','explore','wishlist','expense','budget','profile','settings','place-details','ai-assistant'];
            const rawHash = window.location.hash ? window.location.hash.substring(1).toLowerCase() : '';
            const isKnownHash = rawHash && (knownSections.includes(rawHash) || rawHash.startsWith('place-'));
            if (isKnownHash) {
                handleHashRouting();
            } else {
                show('home');
            }

            // Save user profile directly to Firestore in background
            try {
                const userRef = window.fbFirestoreMethods.doc(window.fbDb, "users", user.uid);
                await window.fbFirestoreMethods.setDoc(userRef, {
                    username: displayName,
                    email: user.email,
                    updated_at: window.fbFirestoreMethods.serverTimestamp()
                }, { merge: true });
            } catch (e) {
                console.error("Firestore user sync failed:", e);
            }

            // Phase 2A: Query places and wishlist data from Firestore & populate UI
            try {
                const wishlistPromise = loadWishlistFirestore();
                await loadPlacesFromFirestore();
                try { await wishlistPromise; } catch(e) { console.error("Wishlist load failed:", e); }
                renderFeaturedPlaces();
                populateDestinationSelector();
            } catch (e) {
                console.error("Places load failed:", e);
            }
            
            // Load direct data from Firestore safely
            try { await loadExpensesFirestore(); } catch(e) { console.error("Expenses load failed:", e); }
            try { await loadWebProfileDataFirestore(); } catch(e) { console.error("Profile load failed:", e); }
        } else {
            console.log("[Firebase SPA] User is logged out.");
            window.currentUser = null;
            window.wishlistStatus = 'idle';
            window.cachedWishlistPlaces = [];
            if (window.userWishlistPlaceIds) window.userWishlistPlaceIds.clear();
            syncWishlistCardStates();
            expenses = [];
            localStorage.removeItem('budget_limit');
            window.currentUser = null;
            if (window.onAIAuthChange) {
                window.onAIAuthChange(null);
            }

            // Reset profile stats and state on logout so user data never leaks
            const wishEl = document.getElementById('stat-wishlist-count');
            const expEl = document.getElementById('stat-expense-count');
            const revEl = document.getElementById('stat-review-count');
            const visEl = document.getElementById('stat-visited-count');
            const recentActivityEl = document.getElementById('profile-recent-activity');
            const wishlistContainer = document.getElementById('wishlist-items');
            const expenseList = document.getElementById('expense-list');
            const budgetInput = document.getElementById('budget-goal-input');

            if (wishEl) wishEl.innerText = "0";
            if (expEl) expEl.innerText = "0";
            if (revEl) revEl.innerText = "0";
            if (visEl) visEl.innerText = "0";
            if (recentActivityEl) recentActivityEl.innerHTML = `<p style="font-size:.85rem;color:var(--ink-muted);text-align:center;padding:16px 0;">Logged out.</p>`;
            if (wishlistContainer) {
                wishlistContainer.innerHTML = `
                    <div class="wishlist-loading-state" style="grid-column:1/-1;">
                        <div class="wishlist-spinner"></div>
                        <div class="wishlist-loading-text">Loading saved places...</div>
                    </div>`;
            }
            if (expenseList) expenseList.innerHTML = '';
            if (budgetInput) budgetInput.value = "10000";

            if (appView) appView.style.display = "none";
            if (authContainer) authContainer.style.display = "block";

            // Clear the URL hash so a stale section (#explore, #place-5, etc.)
            // from this session does not reopen on the next login.
            if (history.replaceState) {
                history.replaceState(null, '', window.location.pathname);
            } else {
                window.location.hash = '';
            }

            // Show welcome landing screen by default
            switchAuthView('welcome');
        }

        // Deactivate splash loader once initialized
        if (loader) {
            loader.style.opacity = "0";
            setTimeout(() => loader.style.display = "none", 400);
        }
    });
}
window.initReactiveSPAAuth = initReactiveSPAAuth;

// Client-side auth tab swapper
window.switchAuthView = function(viewId) {
    document.querySelectorAll('.auth-view').forEach(v => v.style.display = "none");
    const target = document.getElementById(`${viewId}-view`);
    if (target) {
        target.style.display = 'flex';
    }
};

// Client-side register call
window.firebaseRegisterDirect = async function() {
    const emailEl = document.getElementById('register-email');
    const passEl = document.getElementById('register-password');
    const confirmEl = document.getElementById('register-confirm');
    const errEl = document.getElementById('register-error');
    const btn = document.getElementById('register-submit-btn') || document.querySelector('#register-view .btn-form-submit');

    const email = emailEl ? emailEl.value.trim() : '';
    const pass = passEl ? passEl.value : '';
    const confirm = confirmEl ? confirmEl.value : '';

    if (errEl) errEl.style.display = "none";

    if (!email || !pass) {
        if (errEl) { errEl.textContent = "Email and password are required."; errEl.style.display = "block"; }
        return;
    }
    if (pass !== confirm) {
        if (errEl) { errEl.textContent = "Passwords do not match."; errEl.style.display = "block"; }
        return;
    }
    if (pass.length < 6) {
        if (errEl) { errEl.textContent = "Password must be at least 6 characters."; errEl.style.display = "block"; }
        return;
    }

    if (btn) {
        btn.disabled = true;
        btn.dataset.origText = btn.innerHTML;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-2"></i> Creating account...';
    }

    try {
        await window.fbAuthMethods.createUserWithEmailAndPassword(window.fbAuth, email, pass);
    } catch(e) {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = btn.dataset.origText || 'Create Account';
        }
        if (errEl) { errEl.textContent = e.message.replace('Firebase: ', ''); errEl.style.display = "block"; }
    }
};

// Client-side login calls
window.firebaseEmailLoginDirect = async function() {
    const emailEl = document.getElementById('login-email');
    const passEl = document.getElementById('login-password');
    const errEl = document.getElementById('login-error');
    const btn = document.getElementById('login-submit-btn') || document.querySelector('#login-view .btn-form-submit');

    const email = emailEl ? emailEl.value.trim() : '';
    const pass = passEl ? passEl.value : '';

    if (errEl) errEl.style.display = "none";

    if (!email || !pass) {
        if (errEl) { errEl.textContent = "Email and password are required."; errEl.style.display = "block"; }
        return;
    }

    if (btn) {
        btn.disabled = true;
        btn.dataset.origText = btn.innerHTML;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-2"></i> Signing in...';
    }

    try {
        await window.fbAuthMethods.signInWithEmailAndPassword(window.fbAuth, email, pass);
    } catch(e) {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = btn.dataset.origText || 'Sign In';
        }
        if (errEl) { errEl.textContent = e.message.replace('Firebase: ', ''); errEl.style.display = "block"; }
    }
};

window.firebaseGoogleLoginDirect = async function() {
    const isRegister = document.getElementById('register-view')?.style.display !== 'none';
    const errEl = isRegister ? document.getElementById('register-error') : document.getElementById('login-error');
    if (errEl) errEl.style.display = "none";

    try {
        const provider = new window.fbProviders.GoogleAuthProvider();
        provider.setCustomParameters({ prompt: 'select_account' });
        await window.fbAuthMethods.signInWithPopup(window.fbAuth, provider);
    } catch(e) {
        console.error("Google sign in failed:", e);
        if (errEl) {
            let msg = e.message ? e.message.replace('Firebase: ', '') : 'Google sign-in was cancelled or failed.';
            if (e.code === 'auth/popup-closed-by-user') {
                msg = 'Sign-in popup was closed before completing.';
            } else if (e.code === 'auth/popup-blocked') {
                msg = 'Sign-in popup was blocked by browser. Please allow popups for this site.';
            } else if (e.code === 'auth/unauthorized-domain') {
                msg = 'Domain not authorized in Firebase Console.';
            }
            errEl.textContent = msg;
            errEl.style.display = "block";
        }
    }
};

window.forgotPasswordDirect = async function() {
    const email = document.getElementById('login-email').value.trim();
    const errEl = document.getElementById('login-error');
    const okEl = document.getElementById('forgot-success');

    if (errEl) errEl.style.display = "none";
    if (okEl) okEl.style.display = "none";

    if (!email) {
        if (errEl) { errEl.textContent = "Please enter your email above first."; errEl.style.display = "block"; }
        return;
    }

    try {
        await window.fbAuthMethods.sendPasswordResetEmail(window.fbAuth, email);
        if (okEl) { okEl.textContent = "Reset link sent! Check your inbox."; okEl.style.display = "block"; }
    } catch(e) {
        if (errEl) { errEl.textContent = e.message.replace('Firebase: ', ''); errEl.style.display = "block"; }
    }
};

window.firebaseLogoutDirect = async function() {
    try {
        await window.fbAuthMethods.signOut(window.fbAuth);
        window.location.hash = "#welcome";
    } catch (e) {
        console.error("Sign out failed:", e);
    }
};

// Client-side hash routing listener
function handleHashRouting() {
    if (!window.location.hash) return;
    let sectionId = window.location.hash.substring(1).toLowerCase();
    if (sectionId === 'settings') {
        show('settings');
        return;
    }
    if (sectionId === 'budget') sectionId = 'expense';
    
    if (sectionId.startsWith('place-')) {
        const placeId = parseInt(sectionId.split('-')[1]);
        if (!isNaN(placeId)) {
            show('place-details');
            renderPlaceDetailsFirestore(placeId);
        }
        return;
    }

    // AI assistant section requires init call
    if (sectionId === 'ai-assistant') {
        show('ai-assistant');
        if (typeof initAIAssistant === 'function') initAIAssistant();
        return;
    }

    if (document.getElementById(sectionId)) {
        show(sectionId);
    }
}

window.addEventListener('hashchange', handleHashRouting);

// ============================================================
// INITIALIZATION
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
    // If Firebase Bridged SDK has initialized, start reactive SPA lifecycle
    if (window.fbAuthMethods) {
        initReactiveSPAAuth();
    } else {
        // Local dynamic fallback
        loadExpenses();
    }

    const limit = getBudgetLimit();
    const limitText = document.getElementById("budget-limit-text");
    const budgetInput = document.getElementById("budget-goal-input");
    if (limitText) limitText.innerText = `₹${limit.toLocaleString('en-IN')}`;
    if (budgetInput) budgetInput.value = limit;
    populateDestinationSelector();

    if (window.location.hash) {
        let sectionId = window.location.hash.substring(1).toLowerCase();
        if (sectionId === 'settings') {
            show('settings');
        } else if (sectionId === 'budget') {
            sectionId = 'expense';
            if (document.getElementById(sectionId)) show(sectionId);
        } else if (document.getElementById(sectionId)) {
            show(sectionId);
        } else {
            const activeBtn = document.getElementById(`nav-home`);
            if (activeBtn) activeBtn.classList.add('active');
        }
    } else {
        const activeBtn = document.getElementById(`nav-home`);
        if (activeBtn) activeBtn.classList.add('active');
    }
});

// ============================================================
// CHATBOT GUIDE FUNCTIONS
// ============================================================
window.toggleChat = function() {
    const chatWindow = document.getElementById("ai-chat-window");
    if (!chatWindow) return;
    chatWindow.style.display = chatWindow.style.display === "none" ? "flex" : "none";
    if (chatWindow.style.display === "flex") {
        const input = document.getElementById("ai-chat-input");
        if (input) input.focus();
    }
};

window.appendChatMessage = function(sender, text) {
    const messagesDiv = document.getElementById("ai-chat-messages");
    if (!messagesDiv) return;
    const msgContainer = document.createElement("div");
    msgContainer.className = sender === "user" ? "d-flex justify-content-end" : "d-flex align-items-start gap-2";
    
    const bubble = document.createElement("div");
    if (sender === "user") {
        bubble.className = "bg-primary text-white p-2 rounded-3 shadow-sm";
        bubble.style.maxWidth = "85%";
        bubble.innerText = text;
    } else {
        bubble.className = "bg-light text-dark p-2 rounded-3 shadow-sm border";
        bubble.style.maxWidth = "85%";
        bubble.style.whiteSpace = "pre-line";
        bubble.innerHTML = text; 
    }
    
    msgContainer.appendChild(bubble);
    messagesDiv.appendChild(msgContainer);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
};

window.handleChatSubmit = function(event, placeName, placeHistory, placeTips, placeReach, placeFee, placeTime, placeDuration) {
    event.preventDefault();
    const input = document.getElementById("ai-chat-input");
    if (!input) return;
    const query = input.value.trim();
    if (!query) return;

    window.appendChatMessage("user", query);
    input.value = "";
    
    const messagesDiv = document.getElementById("ai-chat-messages");
    const typingId = "typing-" + Date.now();
    const typingContainer = document.createElement("div");
    typingContainer.id = typingId;
    typingContainer.className = "d-flex align-items-start gap-2";
    typingContainer.innerHTML = `<div class="bg-light text-dark p-2 rounded-3 shadow-sm border" style="font-style: italic;">Typing...</div>`;
    messagesDiv.appendChild(typingContainer);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;

    setTimeout(() => {
        const typingEl = document.getElementById(typingId);
        if (typingEl) typingEl.remove();
        
        const q = query.toLowerCase();
        let response = "";

        if (q.includes("history") || q.includes("story") || q.includes("about") || q.includes("who built")) {
            response = "📖 History of " + placeName + ":<br><br>" + placeHistory;
        } else if (q.includes("fee") || q.includes("ticket") || q.includes("cost") || q.includes("price")) {
            response = "💰 Entry Fee: " + placeFee;
        } else if (q.includes("time") || q.includes("when to visit") || q.includes("best month") || q.includes("season")) {
            response = "🌤️ Best Time to Visit: " + placeTime;
        } else if (q.includes("duration") || q.includes("how long")) {
            response = "⏱️ Visit Duration: " + placeDuration;
        } else if (q.includes("reach") || q.includes("how to go") || q.includes("bus") || q.includes("route")) {
            response = "🚌 How to Reach: " + placeReach;
        } else if (q.includes("tips") || q.includes("advice") || q.includes("warning")) {
            response = "💡 Local Tips: " + placeTips;
        } else {
            response = "🤔 I am just a local AI! I only know about the history, timings, fees, and tips for " + placeName + ". Try asking me 'What is the history?'";
        }

        window.appendChatMessage("bot", response);
    }, 800);
};
// ── Premium UI helpers ────────────────────────────────────────────────────

window.updateBudgetGoal = function(val) {
    const n = parseInt(val, 10);
    if (!isNaN(n) && n > 0) {
        localStorage.setItem('budget_limit', n);
        // Sync all budget inputs
        ['budget-goal-input','profile-budget-input'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = n;
        });
        updateExpenseUI();
    }
};

window.clearAllExpenses = async function() {
    if (!confirm('Clear all expenses?')) return;
    if (window.fbDb && window.fbAuth?.currentUser) {
        const uid = window.fbAuth.currentUser.uid;
        const q = window.fbFirestoreMethods.query(
            window.fbFirestoreMethods.collection(window.fbDb, 'expenses'),
            window.fbFirestoreMethods.where('user_id', '==', uid)
        );
        const snap = await window.fbFirestoreMethods.getDocs(q);
        for (const d of snap.docs) {
            await window.fbFirestoreMethods.deleteDoc(d.ref);
        }
    }
    expenses = [];
    updateExpenseUI();
    refreshProfileStats();
};

// Map filter bridge (Leaflet-based)
window.filterMapMarkers = function(query, cat) {
    if (!window.leafletMarkers) return;
    const q = (query || '').toLowerCase();
    const c = (cat || 'all').toLowerCase();
    let count = 0;
    window.leafletMarkers.forEach(({name, category, marker}) => {
        const show = (!q || name.includes(q)) && (c === 'all' || category.includes(c));
        if (show) { marker.addTo(window.exploreMap); count++; }
        else if (window.exploreMap && window.exploreMap.hasLayer(marker)) { window.exploreMap.removeLayer(marker); }
    });
    const cEl = document.getElementById('panel-count');
    if (cEl) cEl.textContent = count + ' places';

    // Sync panel items
    document.querySelectorAll('.panel-item').forEach(item => {
        const nm = (item.dataset.name || '').toLowerCase();
        const ct = (item.dataset.cat || '').toLowerCase();
        item.style.display = ((!q || nm.includes(q)) && (c === 'all' || ct.includes(c))) ? 'flex' : 'none';
    });
};

// Leaflet map init (replaces Google Maps)
window.initExploreMap = window.initLeafletMap = function() {
    const mapEl = document.getElementById('explore-map');
    if (!mapEl || window.exploreMap) return;

    window.exploreMap = L.map('explore-map', {
        center: [15.85, 74.5],
        zoom: 10,
        zoomControl: true
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors',
        maxZoom: 18
    }).addTo(window.exploreMap);

    window.leafletMarkers = [];
    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (!sourceData) return;

    const panelList = document.getElementById('panel-list');
    if (panelList) panelList.innerHTML = '';

    const bounds = [];

    sourceData.forEach(p => {
        if (!p.lat || !p.lon) return;
        const lat = parseFloat(p.lat), lng = parseFloat(p.lon);
        if (isNaN(lat) || isNaN(lng)) return;

        bounds.push([lat, lng]);

        const icon = L.divIcon({
            className: '',
            html: `<div style="
                width:34px;height:34px;
                background:var(--forest,#2d5a3d);
                border:2.5px solid white;
                border-radius:50% 50% 50% 0;
                transform:rotate(-45deg);
                box-shadow:0 2px 8px rgba(0,0,0,.25);
                display:flex;align-items:center;justify-content:center;
            "><span style="transform:rotate(45deg);font-size:.75rem;color:white;">📍</span></div>`,
            iconSize: [34, 34],
            iconAnchor: [17, 34],
            popupAnchor: [0, -36]
        });

        const marker = L.marker([lat, lng], { icon });
        const popupHtml = `
            <div style="font-family:Inter,sans-serif;width:210px;">
                <img src="/static/images/${p.folder_name}/1.jpg"
                     style="width:100%;height:110px;object-fit:cover;border-radius:8px;margin-bottom:10px;"
                     onerror="this.style.display='none'">
                <div style="font-weight:700;font-size:.88rem;color:#1a1208;margin-bottom:3px;">${p.name}</div>
                <div style="font-size:.72rem;color:#8a7968;text-transform:capitalize;margin-bottom:10px;">${p.category}</div>
                <a href="#place-${p.id}"
                   style="display:block;text-align:center;background:#2d5a3d;color:white;
                          padding:8px;border-radius:20px;text-decoration:none;
                          font-weight:600;font-size:.78rem;"
                   onclick="navigateToPlace(${p.id});return false;">
                    View Destination →
                </a>
            </div>`;
        marker.bindPopup(popupHtml, { maxWidth: 230 });
        marker.addTo(window.exploreMap);

        window.leafletMarkers.push({
            name: (p.name || '').toLowerCase(),
            category: (p.category || '').toLowerCase(),
            marker
        });

        // Side panel item
        if (panelList) {
            const item = document.createElement('a');
            item.className = 'panel-item';
            item.dataset.name = (p.name || '').toLowerCase();
            item.dataset.cat  = (p.category || '').toLowerCase();
            item.href = '#place-' + p.id;
            item.onclick = e => { e.preventDefault(); navigateToPlace(p.id); };
            item.innerHTML = `
                <img src="/static/images/${p.folder_name}/1.jpg"
                     onerror="this.src='/static/icon-192.png'"
                     alt="${p.name}">
                <div>
                    <div class="panel-item-name">${p.name}</div>
                    <div class="panel-item-cat">${p.category}</div>
                </div>`;
            panelList.appendChild(item);
        }
    });

    const countEl = document.getElementById('panel-count');
    if (countEl) countEl.textContent = sourceData.length + ' places';

    if (bounds.length > 0) {
        window.exploreMap.fitBounds(bounds, { padding: [40, 40] });
    }
};

window.recenterMap = function() {
    if (window.exploreMap) window.exploreMap.setView([15.85, 74.5], 10);
};

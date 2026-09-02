// ============================================================
// NAVIGATION & SEARCH
// ============================================================

function goHome() {
    show('home');
    const searchContainer = document.querySelector(".search-container");
    if (searchContainer) searchContainer.style.display = "block";
}

function show(id) {
    // Normalize budget -> expense internally
    if (id === 'budget') id = 'expense';

    // If not on planner page and running under dynamic Flask local server, redirect with hash
    if (!document.getElementById('home')) {
        const hash = id === 'expense' ? 'budget' : id;
        window.location.href = `/planner#${hash}`;
        return;
    }

    document.querySelectorAll(".section").forEach(s => s.style.display = "none");

    const target = document.getElementById(id);
    if (target) target.style.display = "block";

    document.querySelectorAll(".bottom-nav button").forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`nav-${id}`);
    if (activeBtn) activeBtn.classList.add('active');

    if (id === "profile") refreshProfileStats();

    if (id === "explore") {
        if (!exploreMap) {
            // If Maps API already loaded, init now; else defer to callback
            if (window.googleMapsLoaded) {
                initExploreMap();
            } else {
                window.onGoogleMapsReady = initExploreMap;
            }
        }
    }

    // Sync hash to URL without page reload
    const hash = id === 'expense' ? 'budget' : id;
    if (history.pushState) {
        history.pushState(null, null, `#${hash}`);
    } else {
        window.location.hash = `#${hash}`;
    }
}

function filterPlaces() {
    const q = document.getElementById("search").value.toLowerCase();
    document.querySelectorAll("#home .col-12").forEach(item => {
        const nameAttr = (item.getAttribute('data-name') || '').toLowerCase();
        const nameEl = item.querySelector("h3");
        const name = nameAttr || (nameEl ? nameEl.innerText.toLowerCase() : '');
        item.style.display = (!q || name.includes(q)) ? 'block' : 'none';
    });
}

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
        if (container.children.length === 0 && document.getElementById('empty-msg')) {
            document.getElementById('empty-msg').style.display = 'block';
        }
        if (btn) {
            btn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Save`;
            btn.classList.replace('btn-primary', 'btn-outline-primary');
        }
    } else {
        fetch(`/add_to_wishlist/${placeId}`, { method: 'POST' });
        const div = document.createElement('div');
        div.id = `wish-item-${placeId}`;
        div.className = "card mb-3 p-2 shadow-sm d-flex flex-row align-items-center border-0 rounded-4";
        div.innerHTML = `
            <img src="/static/images/${folder}/1.jpg" loading="lazy" style="width: 70px; height: 70px; object-fit: cover; border-radius: 12px;" onerror="this.src='/static/icon-192.png'">
            <div class="ms-3 flex-grow-1">
                <h6 class="m-0 fw-bold">${name}</h6>
            </div>
            <button class="btn btn-link text-danger text-decoration-none" onclick="toggleWishlist(null, ${placeId})"><i class="fa-solid fa-trash-can"></i></button>`;
        container.appendChild(div);
        if (document.getElementById('empty-msg')) document.getElementById('empty-msg').style.display = 'none';
        if (btn) {
            btn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Saved`;
            btn.classList.replace('btn-outline-primary', 'btn-primary');
        }
    }
}

// Direct client-side Firestore toggling
async function toggleWishlistFirestore(btn, placeId, name, folder) {
    const uid = window.fbAuth.currentUser.uid;
    const docId = `${uid}_${placeId}`;
    const docRef = window.fbFirestoreMethods.doc(window.fbDb, "wishlist", docId);
    const container = document.getElementById('wishlist-items');
    const existing = document.getElementById(`wish-item-${placeId}`);

    try {
        if (existing) {
            await window.fbFirestoreMethods.deleteDoc(docRef);
            existing.remove();
            if (container.children.length === 0 && document.getElementById('empty-msg')) {
                document.getElementById('empty-msg').style.display = 'block';
            }
            if (btn) {
                btn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Save`;
                btn.classList.replace('btn-primary', 'btn-outline-primary');
            }
        } else {
            await window.fbFirestoreMethods.setDoc(docRef, {
                user_id: uid,
                place_id: placeId,
                name: name,
                folder_name: folder,
                created_at: window.fbFirestoreMethods.serverTimestamp()
            });
            
            const div = document.createElement('div');
            div.id = `wish-item-${placeId}`;
            div.className = "card mb-3 p-2 shadow-sm d-flex flex-row align-items-center border-0 rounded-4";
            div.innerHTML = `
                <img src="/static/images/${folder}/1.jpg" loading="lazy" style="width: 70px; height: 70px; object-fit: cover; border-radius: 12px;" onerror="this.src='/static/icon-192.png'">
                <div class="ms-3 flex-grow-1">
                    <h6 class="m-0 fw-bold">${name}</h6>
                </div>
                <button class="btn btn-link text-danger text-decoration-none" onclick="toggleWishlist(null, ${placeId})"><i class="fa-solid fa-trash-can"></i></button>`;
            container.appendChild(div);
            if (document.getElementById('empty-msg')) document.getElementById('empty-msg').style.display = 'none';
            if (btn) {
                btn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Saved`;
                btn.classList.replace('btn-outline-primary', 'btn-primary');
            }
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
                location: data.location || data.placeName || 'Belagavi',
                name: data.name || data.title || 'Expense',
                amount: data.amount,
                category: data.category,
                date: data.date,
                created_at: data.created_at
            });
        });
        // Sort client side (newest first) to avoid composite index requirement
        expenses.sort((a, b) => {
            const tA = a.created_at?.seconds || 0;
            const tB = b.created_at?.seconds || 0;
            return tB - tA;
        });
        updateExpenseUI();
    } catch (err) {
        console.error("Firestore expenses load failed:", err);
    }
}

async function addExpenseDirect() {
    if (window.fbDb && window.fbAuth && window.fbAuth.currentUser) {
        addExpenseFirestore();
        return;
    }
    
    // Legacy Fallback Call
    addExpense();
}

async function addExpenseFirestore() {
    const locInput = document.getElementById("item_location");
    const nameInput = document.getElementById("item_name");
    const amountInput = document.getElementById("item_amount");
    const categoryInput = document.getElementById("item_category");

    const location = locInput.value;
    const name = nameInput.value.trim();
    const amount = parseFloat(amountInput.value);
    const category = categoryInput.value;

    if (!location || isNaN(amount) || amount <= 0) {
        alert("Please select a valid destination and enter an amount.");
        return;
    }

    const payload = {
        location, name, amount, category,
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
                name: name,
                title: name,
                amount: amount,
                category: category,
                date: payload.date,
                created_at: window.fbFirestoreMethods.serverTimestamp()
            }
        );
        payload.id = docRef.id;
        expenses.unshift(payload);
        updateExpenseUI();
        nameInput.value = "";
        amountInput.value = "";
        locInput.selectedIndex = 0;
        refreshProfileStats();
    } catch (err) {
        console.error("Firestore expense write failed:", err);
    }
}

async function addExpense() {
    const locInput = document.getElementById("item_location");
    const nameInput = document.getElementById("item_name");
    const amountInput = document.getElementById("item_amount");
    const categoryInput = document.getElementById("item_category");

    const location = locInput.value;
    const name = nameInput.value.trim();
    const amount = parseFloat(amountInput.value);
    const category = categoryInput.value;

    if (!location || isNaN(amount) || amount <= 0) {
        alert("Please select a valid destination and enter an amount.");
        return;
    }

    const payload = {
        location, name, amount, category,
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
            payload.id = data.id;
            expenses.unshift(payload);
            updateExpenseUI();
            nameInput.value = "";
            amountInput.value = "";
            locInput.selectedIndex = 0;
        }
    } catch (err) { console.error("Failed to add expense", err); }
}

function updateExpenseUI() {
    const list = document.getElementById("expense-list");
    const totalDisplay = document.getElementById("total-amount");
    const progressBar = document.getElementById("budget-progress");
    const warningText = document.getElementById("budget-warning");
    if (!list || !totalDisplay) return;

    list.innerHTML = "";
    let total = 0;
    const grouped = {};
    expenses.forEach(exp => {
        total += exp.amount;
        if (!grouped[exp.location]) grouped[exp.location] = [];
        grouped[exp.location].push(exp);
    });

    for (const [location, items] of Object.entries(grouped)) {
        const locationTotal = items.reduce((s, i) => s + i.amount, 0);
        let cardHtml = `
        <div class="card mb-3 border-0 shadow-sm rounded-4 overflow-hidden">
            <div class="bg-light p-3 border-bottom d-flex justify-content-between align-items-center">
                <h6 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-location-dot text-primary me-2"></i>${location}</h6>
                <span class="badge bg-primary rounded-pill px-3 py-2 shadow-sm">₹${locationTotal.toLocaleString('en-IN')}</span>
            </div>
            <div class="card-body p-0">`;
        items.forEach(exp => {
            cardHtml += `
            <div class="d-flex align-items-center justify-content-between p-3 border-bottom">
                <div class="d-flex align-items-center">
                    <div class="rounded-circle bg-light d-flex align-items-center justify-content-center me-3 text-primary" style="width:40px;height:40px;font-size:1.2rem">${getCategoryIcon(exp.category)}</div>
                    <div>
                        <h6 class="mb-1 fw-bold text-dark" style="font-size:0.9rem">${exp.category}</h6>
                        <small class="text-muted d-block" style="font-size:0.75rem">${exp.name || 'No note'} • ${exp.date}</small>
                    </div>
                </div>
                <div class="text-end">
                    <span class="fw-bold text-dark d-block mb-1">₹${exp.amount.toLocaleString('en-IN')}</span>
                    <button class="btn btn-sm btn-link text-danger p-0 text-decoration-none" style="font-size:0.75rem" onclick="deleteExpenseDirect('${exp.id}')">Remove</button>
                </div>
            </div>`;
        });
        cardHtml += `</div></div>`;
        list.innerHTML += cardHtml;
    }

    totalDisplay.innerText = `₹${total.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    const limit = getBudgetLimit();
    const percent = limit > 0 ? (total / limit) * 100 : 0;
    if (progressBar) {
        progressBar.style.width = Math.min(percent, 100) + "%";
        progressBar.className = percent > 90 ? "progress-bar bg-danger" : percent > 70 ? "progress-bar bg-warning" : "progress-bar bg-success";
    }
    if (warningText) warningText.innerText = `${Math.round(percent)}% of your ₹${limit.toLocaleString('en-IN')} budget used`;
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
    } catch (err) { console.error("Failed to delete", err); }
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
            querySnapshot.forEach(async (d) => {
                await window.fbFirestoreMethods.deleteDoc(d.ref);
            });
            expenses = [];
            updateExpenseUI();
            refreshProfileStats();
        } catch (err) {
            console.error("Firestore clear expenses failed:", err);
        }
        return;
    }
    
    // Legacy Fallback
    clearAllExpenses();
}

async function clearAllExpenses() {
    try {
        const res = await fetch('/api/expenses/clear', { method: 'POST' });
        if (res.ok) { expenses = []; updateExpenseUI(); }
    } catch (err) { console.error("Failed to clear", err); }
}

function updateBudgetGoal(newLimit) {
    const limitNumber = parseInt(newLimit, 10);
    if (!limitNumber || limitNumber < 1) return;
    localStorage.setItem('budget_limit', String(limitNumber));
    const limitText = document.getElementById("budget-limit-text");
    if (limitText) limitText.innerText = `₹${limitNumber.toLocaleString('en-IN')}`;
    updateExpenseUI();
}

function refreshProfileStats() {
    const wishCount = document.querySelectorAll('#wishlist-items .card').length;
    const uniqueTrips = [...new Set(expenses.map(e => e.location))].length;
    const wishEl = document.getElementById('stat-wishlist-count');
    if (wishEl) wishEl.innerText = wishCount;
    const tripEl = document.getElementById('stat-expense-count');
    if (tripEl) tripEl.innerText = uniqueTrips;
}

// Client-side dynamically render places lists inside index.html home section
function renderFeaturedPlaces() {
    const grid = document.querySelector("#placeGrid .row");
    if (!grid) return;
    grid.innerHTML = "";
    
    // Phase 2A: Query from Firestore places collection if loaded, fallback to static database
    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (!sourceData) return;

    // Exact same home page featured selection
    const featuredKeys = ['jalavane_falls', 'kamalbasadi_belagavi', 'vidhansoudha_belagavi', 'chorla_ghat', 'yellur_fort', 'vajrapoha_falls'];
    const featured = sourceData.filter(p => featuredKeys.includes(p.folder_name));
    
    featured.forEach(p => {
        const col = document.createElement("div");
        col.className = "col-12 col-md-6 col-lg-4";
        col.innerHTML = `
            <div class="place-card bg-white rounded shadow-sm overflow-hidden">
                <a href="/place/${p.id}" class="d-block position-relative" style="text-decoration: none;" onclick="navigateToPlace(${p.id}); return false;">
                    <img src="/static/images/${p.folder_name}/1.jpg" 
                         alt="${p.name}" loading="lazy"
                         style="width: 100%; height: 200px; object-fit: cover; cursor: pointer;">
                    <span class="badge bg-dark position-absolute top-0 end-0 m-2">${p.category}</span>
                </a>

                <div class="p-3">
                    <a href="/place/${p.id}" style="text-decoration: none; color: inherit;" onclick="navigateToPlace(${p.id}); return false;">
                        <h3 class="h5 fw-bold mb-1 text-truncate">${p.name}</h3>
                    </a>
                    
                    <p class="text-muted small mb-3 text-truncate">${p.description.substring(0, 60)}...</p>
                    
                    <div class="d-flex gap-2">
                        <button class="btn btn-outline-primary w-100 fw-bold save-btn-${p.id}" onclick="toggleWishlist(this, ${p.id}, '${p.name.replace(/'/g, "\\'")}', '${p.folder_name}')" style="font-size: 0.85rem;">
                            <i class="fa-solid fa-heart me-1"></i> Save
                        </button>
                    </div>
                </div>
            </div>
        `;
        grid.appendChild(col);
    });
}

// Client-side populate expense locations
function populateDestinationSelector() {
    const select = document.getElementById("item_location");
    if (!select) return;

    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (!sourceData) return;
    
    const disabledOpt = select.options[0];
    select.innerHTML = "";
    select.appendChild(disabledOpt);
    
    sourceData.forEach(p => {
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

// Load wishlist items directly from Firestore client-side
async function loadWishlistFirestore() {
    const uid = window.fbAuth.currentUser.uid;
    const q = window.fbFirestoreMethods.query(
        window.fbFirestoreMethods.collection(window.fbDb, "wishlist"),
        window.fbFirestoreMethods.where("user_id", "==", uid)
    );

    try {
        const querySnapshot = await window.fbFirestoreMethods.getDocs(q);
        const wishlistContainer = document.getElementById('wishlist-items');
        if (!wishlistContainer) return;
        
        wishlistContainer.innerHTML = '';
        let count = 0;
        
        querySnapshot.forEach(docSnap => {
            count++;
            const data = docSnap.data();
            const div = document.createElement('div');
            div.id = `wish-item-${data.place_id}`;
            div.className = "card mb-3 p-2 shadow-sm d-flex flex-row align-items-center border-0 rounded-4";
            div.innerHTML = `
                <img src="/static/images/${data.folder_name}/1.jpg" loading="lazy" style="width: 70px; height: 70px; object-fit: cover; border-radius: 12px;" onerror="this.src='/static/icon-192.png'">
                <div class="ms-3 flex-grow-1">
                    <h6 class="m-0 fw-bold">${data.name}</h6>
                    <small class="text-muted">Saved Landmark</small>
                </div>
                <button class="btn btn-link text-danger text-decoration-none" onclick="toggleWishlist(null, ${data.place_id})"><i class="fa-solid fa-trash-can"></i></button>`;
            wishlistContainer.appendChild(div);
            
            // Sync heart button states in Home page grids
            const saveBtn = document.querySelector(`.save-btn-${data.place_id}`);
            if (saveBtn) {
                saveBtn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Saved`;
                saveBtn.classList.replace('btn-outline-primary', 'btn-primary');
            }
        });

        const emptyMsg = document.getElementById('empty-msg');
        if (emptyMsg) emptyMsg.style.display = count === 0 ? 'block' : 'none';
        refreshProfileStats();
    } catch (err) {
        console.error("Firestore load wishlist failed:", err);
    }
}

// Bump when place coordinates or core metadata change (forces one-time Firestore overwrite).
window.PLACES_SYNC_VERSION = 'coords-2026-05-27-v6';

/** Overwrite places/{id} docs from window.allPlacesData (IDs 1–31). Reviews/ratings/wishlists are separate collections. */
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

// Phase 2A: Compile and render high-fidelity details card client-side inside #place-details-content
async function renderPlaceDetailsFirestore(placeId) {
    const contentEl = document.getElementById('place-details-content');
    if (!contentEl) return;
    
    contentEl.innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading details...</span>
            </div>
            <p class="text-muted mt-2 small">Retrieving landmarks facts from Cloud Firestore...</p>
        </div>
    `;

    let place = null;
    if (window.cachedPlacesFromFirestore) {
        place = window.cachedPlacesFromFirestore.find(p => p.id === placeId);
    }
    if (!place) {
        try {
            const docRef = window.fbFirestoreMethods.doc(window.fbDb, "places", String(placeId));
            const docSnap = await window.fbFirestoreMethods.getDoc(docRef);
            if (docSnap.exists()) {
                place = docSnap.data();
                // Normalize types for consistent distance/weather/routing usage.
                if (place) {
                    place.id = Number(place.id ?? placeId);
                    if (place.lat !== undefined) place.lat = Number(place.lat);
                    if (place.lon !== undefined) place.lon = Number(place.lon);
                }
            }
        } catch (e) {
            console.error("Firestore single place read failed:", e);
        }
    }

    if (!place) {
        contentEl.innerHTML = `<div class="alert alert-danger">Error: Place details could not be loaded from Cloud Firestore.</div>`;
        return;
    }

    // Build rich dynamic transport details
    let transportHtml = "";
    if (place.transport) {
        transportHtml += `
            <div class="pt-2 pb-1">
                <span class="badge rounded-pill px-3 py-2 fw-bold mb-3 d-inline-block" style="background:#eff6ff;color:#1d4ed8;font-size:0.82rem">
                    📍 ${place.transport.distance_from_city}
                </span>
            </div>
        `;
        if (place.transport.bus && place.transport.bus.length > 0) {
            transportHtml += `
                <div class="pb-2">
                    <div class="fw-bold small text-dark mb-2">
                        <i class="fa-solid fa-bus text-primary me-2"></i>Bus Routes
                    </div>
            `;
            place.transport.bus.forEach(b => {
                transportHtml += `
                    <div class="card border-0 rounded-3 mb-2 p-3" style="background:#f8fafc">
                        <div class="fw-bold small text-dark mb-2">${b.route}</div>
                        <div class="d-flex gap-3 flex-wrap">
                            <span class="badge bg-light text-dark border small">🕐 ${b.frequency}</span>
                            <span class="badge bg-light text-dark border small">⏱️ ${b.duration}</span>
                            <span class="badge bg-success bg-opacity-10 text-success border border-success small">💰 ${b.fare}</span>
                        </div>
                    </div>
                `;
            });
            transportHtml += `</div>`;
        }
        if (place.transport.train) {
            transportHtml += `
                <div class="pb-2">
                    <div class="fw-bold small text-dark mb-1">
                        <i class="fa-solid fa-train text-warning me-2"></i>By Train
                    </div>
                    <div class="small text-muted ps-3 mb-2">${place.transport.train}</div>
                </div>
            `;
        }
        if (place.transport.auto_taxi) {
            transportHtml += `
                <div class="pb-2">
                    <div class="fw-bold small text-dark mb-1">
                        <i class="fa-solid fa-taxi text-warning me-2"></i>Auto / Taxi
                    </div>
                    <div class="small text-muted ps-3 mb-2">${place.transport.auto_taxi}</div>
                </div>
            `;
        }
        if (place.transport.drive) {
            transportHtml += `
                <div class="pb-3">
                    <div class="fw-bold small text-dark mb-1">
                        <i class="fa-solid fa-car text-primary me-2"></i>By Car
                    </div>
                    <div class="small text-muted ps-3">${place.transport.drive}</div>
                </div>
            `;
        }
    } else {
        transportHtml += `
            <div class="small text-muted" style="line-height:1.6;white-space:pre-line">
                ${place.how_to_reach || 'Routing details not available yet.'}
            </div>
        `;
    }

    // Embed the exact template layout mirroring templates/details.html
    contentEl.innerHTML = `
        <div class="d-flex align-items-center gap-2 mb-3">
            <button class="btn btn-sm btn-light rounded-circle shadow-sm" onclick="show('home'); window.location.hash='#home'; return false;">
                <i class="fa-solid fa-arrow-left"></i>
            </button>
            <span class="text-primary fw-semibold small text-uppercase mb-0">Belagavi District</span>
        </div>
        
        <header class="mb-4 pb-3 border-bottom border-light mt-2">
            <div class="d-flex justify-content-between align-items-start">
                <div>
                    <h1 class="place-details-title fw-bold text-dark mb-2">${place.name}</h1>
                    <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
                        <span class="badge bg-primary px-3 py-2 rounded-pill shadow-sm">${place.category}</span>
                        <span class="text-secondary fw-bold small">📍 ${place.city || 'Belagavi'}</span>
                    </div>
                </div>
            </div>
        </header>

        <ul class="nav nav-pills mb-4 nav-fill shadow-sm rounded-pill p-1 bg-white border" id="placeTab" role="tablist">
            <li class="nav-item" role="presentation">
                <button class="nav-link active rounded-pill fw-bold" id="overview-tab" data-bs-toggle="pill" data-bs-target="#overview" type="button" role="tab">Overview</button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link rounded-pill fw-bold" id="gallery-tab" data-bs-toggle="pill" data-bs-target="#gallery" type="button" role="tab">Gallery</button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link rounded-pill fw-bold" id="recommended-tab" data-bs-toggle="pill" data-bs-target="#recommended" type="button" role="tab">Nearby Spots</button>
            </li>
        </ul>

        <div class="tab-content pb-5" id="placeTabContent">
            <!-- OVERVIEW TAB -->
            <div class="tab-pane show active" id="overview" role="tabpanel">
                <div class="row g-4">
                    <div class="col-lg-8">
                        
                        <!-- Smart Route & Travel Planner Widget (Phase 3A Active!) -->
                        <div class="card border-0 shadow-sm rounded-4 p-4 mb-4" style="background: linear-gradient(145deg, #ffffff, #f8fafc); border: 1px solid rgba(37,99,235,0.08) !important;">
                            <h4 class="fw-bold mb-3 text-dark d-flex align-items-center">
                                <i class="fa-solid fa-compass text-primary me-2 animate-pulse"></i> Smart Route &amp; Travel Planner
                            </h4>
                            
                            <!-- Origin GPS Status -->
                            <div class="d-flex justify-content-between align-items-center bg-white p-3 rounded-3 shadow-sm border mb-3">
                                <div class="overflow-hidden">
                                    <span class="d-block text-secondary text-uppercase fw-bold" style="font-size:0.68rem; letter-spacing:0.5px;">📍 Departure Origin</span>
                                    <span id="route-origin-status" class="fw-semibold text-dark text-truncate d-block small">Detecting live location...</span>
                                </div>
                                <button onclick="detectUserLocation(${place.lat}, ${place.lon}, '${place.name.replace(/'/g, "\\'")}', '${place.category}')" class="btn btn-sm btn-outline-primary rounded-pill px-3 fw-bold" style="font-size:0.75rem;">
                                    <i class="fa-solid fa-location-crosshairs"></i> Retry GPS
                                </button>
                            </div>

                            <!-- Route Metrics Grid -->
                            <div class="row g-3 mb-3 text-center">
                                <div class="col-6 col-md-4">
                                    <div class="bg-white p-3 rounded-3 border shadow-sm h-100 d-flex flex-column justify-content-center">
                                        <div class="text-secondary small fw-bold mb-1"><i class="fa-solid fa-route text-primary me-1"></i> Distance</div>
                                        <h5 class="fw-bold text-dark m-0" id="smart-dist-val">—</h5>
                                    </div>
                                </div>
                                <div class="col-6 col-md-4">
                                    <div class="bg-white p-3 rounded-3 border shadow-sm h-100 d-flex flex-column justify-content-center">
                                        <div class="text-secondary small fw-bold mb-1"><i class="fa-solid fa-clock text-warning me-1"></i> Drive Time</div>
                                        <h5 class="fw-bold text-dark m-0" id="smart-time-val">—</h5>
                                    </div>
                                </div>
                                <div class="col-12 col-md-4">
                                    <div class="bg-white p-3 rounded-3 border shadow-sm h-100 d-flex flex-column justify-content-center">
                                        <div class="text-secondary small fw-bold mb-1"><i class="fa-solid fa-map-signs text-success me-1"></i> Route Link</div>
                                        <div class="fw-bold text-dark small text-truncate" id="smart-route-name">—</div>
                                    </div>
                                </div>
                            </div>

                            <!-- Travel Advisory Alert -->
                            <div id="smart-advisory-alert" class="alert alert-info border-0 rounded-3 p-3 mb-3 d-flex align-items-start gap-2 shadow-sm">
                                <span style="font-size:1.3rem;">💡</span>
                                <div class="small w-100">
                                    <div class="fw-bold text-dark mb-1">Live Safety Advisory</div>
                                    <span class="text-muted" id="smart-advisory-desc">Checking weather &amp; local tips for your trip...</span>
                                </div>
                            </div>

                            <!-- Expense Estimate Accordion -->
                            <div class="accordion accordion-flush mb-4 border rounded-3 overflow-hidden shadow-sm" id="expenseEstimator">
                                <div class="accordion-item">
                                    <h2 class="accordion-header">
                                        <button class="accordion-button collapsed fw-bold small text-dark p-3 bg-white" type="button" data-bs-toggle="collapse" data-bs-target="#collapseExpense">
                                            ⛽ Smart Travel Expense Estimator (Approx.)
                                        </button>
                                    </h2>
                                    <div id="collapseExpense" class="accordion-collapse collapse" data-bs-parent="#expenseEstimator">
                                        <div class="accordion-body bg-light p-3">
                                            <div class="d-flex justify-content-between align-items-center mb-2 pb-2 border-bottom">
                                                <span class="small"><i class="fa-solid fa-motorcycle text-primary me-2"></i>Bike Fuel Cost</span>
                                                <span class="fw-bold text-dark small" id="cost-bike">₹0</span>
                                            </div>
                                            <div class="d-flex justify-content-between align-items-center mb-2 pb-2 border-bottom">
                                                <span class="small"><i class="fa-solid fa-car text-success me-2"></i>Car Fuel Cost</span>
                                                <span class="fw-bold text-dark small" id="cost-car">₹0</span>
                                            </div>
                                            <div class="d-flex justify-content-between align-items-center">
                                                <span class="small"><i class="fa-solid fa-bus text-warning me-2"></i>Bus Estimate (avg. KSRTC)</span>
                                                <span class="fw-bold text-dark small" id="cost-bus">₹0</span>
                                            </div>
                                            <small class="text-muted d-block mt-2" style="font-size:0.7rem;">Rates: Bike (~₹2.5/km), Car (~₹7.0/km), Bus (~₹1.5/km).</small>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Start Navigation Button -->
                            <button onclick="startNavigation()" id="smart-nav-btn" class="btn btn-success w-100 rounded-pill py-3 fw-bold shadow-lg d-flex align-items-center justify-content-center gap-2" style="font-size: 1.1rem; transition: transform 0.2s, box-shadow 0.2s; border: none; background: linear-gradient(135deg, #10b981, #059669);">
                                <i class="fa-solid fa-location-arrow fa-beat"></i> 🚗 Start Navigation
                            </button>
                        </div>

                        <!-- Main About Description -->
                        <div class="card border-0 shadow-sm rounded-4 p-4">
                            <h4 class="fw-bold mb-3 text-dark">About</h4>
                            <p class="text-muted" style="line-height: 1.8; font-size: 1.05rem;">
                                ${place.history || place.description || 'Local highlight in Belagavi district.'}
                            </p>
                            
                            <div class="accordion accordion-flush mt-4" id="detailsAccordion">
                                <div class="accordion-item border rounded-3 mb-2 shadow-sm">
                                    <h2 class="accordion-header">
                                        <button class="accordion-button collapsed fw-bold rounded-3" type="button" data-bs-toggle="collapse" data-bs-target="#collapseHistory">
                                            📖 Deep History
                                        </button>
                                    </h2>
                                    <div id="collapseHistory" class="accordion-collapse collapse" data-bs-parent="#detailsAccordion">
                                        <div class="accordion-body text-muted small" style="line-height: 1.6; white-space: pre-line;">
                                            ${place.detailed_history || 'Detailed historical guides not available yet.'}
                                        </div>
                                    </div>
                                </div>

                                <div class="accordion-item border rounded-3 mb-2 shadow-sm">
                                    <h2 class="accordion-header">
                                        <button class="accordion-button collapsed fw-bold rounded-3" type="button" data-bs-toggle="collapse" data-bs-target="#collapseReach">
                                            🚌 How to Reach &amp; Local Info
                                        </button>
                                    </h2>
                                    <div id="collapseReach" class="accordion-collapse collapse" data-bs-parent="#detailsAccordion">
                                        <div class="accordion-body p-3">
                                            ${transportHtml}
                                            <!-- Dynamic reach section: populated by updateDynamicReachSection() after GPS resolves -->
                                            <div id="dynamic-reach-text" class="p-2 rounded-3 border mb-3 bg-light small text-muted" style="white-space:pre-line;">
                                                📍 Detecting your location for personalised route info...
                                            </div>
                                            <div class="fw-bold text-dark small mb-1 mt-1">Local Travel Tips</div>
                                            <p class="text-muted small m-0">${place.local_tips || 'Carry water and plan ahead.'}</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Sidebar Quick Facts & Live Weather check widget -->
                    <div class="col-lg-4">
                        <!-- Live Weather Check Widget (Phase 3A Active!) -->
                        <div id="weather-widget" class="bg-white rounded-3 p-4 shadow-sm mb-4 text-center border">
                            <h6 class="fw-bold text-dark mb-3" style="font-size: 0.95rem;"><i class="fa-solid fa-cloud-sun text-primary me-2"></i>Live Weather Check</h6>
                            <div id="weather-content">
                                <div class="spinner-border spinner-border-sm text-primary" role="status"></div>
                                <small class="text-muted d-block mt-2">Checking open-meteo stations...</small>
                            </div>
                        </div>

                        <div class="card border-0 shadow-sm rounded-4 p-4 mb-4">
                            <h5 class="fw-bold mb-3 text-dark"><i class="fa-solid fa-circle-info text-primary me-2"></i>Quick Facts</h5>
                            <div class="d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom">
                                <span class="small text-muted"><i class="fa-regular fa-clock me-2 text-primary"></i>Visit Duration</span>
                                <span class="fw-bold text-dark small">${place.visit_duration || '2 Hours'}</span>
                            </div>
                            <div class="d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom">
                                <span class="small text-muted"><i class="fa-solid fa-indian-rupee-sign me-2 text-success"></i>Entry Fee</span>
                                <span class="fw-bold text-dark small">${place.entry_fee || 'Free'}</span>
                            </div>
                            <div class="d-flex justify-content-between align-items-center">
                                <span class="small text-muted"><i class="fa-regular fa-calendar-days me-2 text-warning"></i>Best Time</span>
                                <span class="fw-bold text-dark small">${place.best_time || 'Year-round'}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- GALLERY TAB -->
            <div class="tab-pane fade" id="gallery" role="tabpanel">
                <div class="card border-0 shadow-sm rounded-4 p-4">
                    <h5 class="fw-bold mb-4 text-dark"><i class="fa-regular fa-images text-primary me-2"></i>Photo Gallery</h5>
                    <div class="row g-3">
                        <div class="col-6 col-md-4">
                            <img src="/static/images/${place.folder_name}/1.jpg" class="img-fluid rounded-4 shadow-sm" style="height: 180px; width: 100%; object-fit: cover;" onerror="this.src='/static/icon-192.png'">
                        </div>
                        <div class="col-6 col-md-4">
                            <img src="/static/images/${place.folder_name}/2.jpg" class="img-fluid rounded-4 shadow-sm" style="height: 180px; width: 100%; object-fit: cover;" onerror="this.src='/static/icon-192.png'">
                        </div>
                        <div class="col-6 col-md-4">
                            <img src="/static/images/${place.folder_name}/3.jpg" class="img-fluid rounded-4 shadow-sm" style="height: 180px; width: 100%; object-fit: cover;" onerror="this.src='/static/icon-192.png'">
                        </div>
                    </div>
                </div>
            </div>

            <!-- RECOMMENDED TAB -->
            <div class="tab-pane fade" id="recommended" role="tabpanel">
                <div class="card border-0 shadow-sm rounded-4 p-4">
                    <h5 class="fw-bold mb-4 text-dark"><i class="fa-solid fa-layer-group text-primary me-2"></i>Nearby Spots</h5>
                    <div class="row g-3" id="recommended-container">
                        <!-- Dynamic suggestions list -->
                    </div>
                </div>
            </div>
        </div>

        <!-- ═══════════════════════════════════════════════════════
             REVIEWS & RATINGS SECTION (Firestore-backed)
        ════════════════════════════════════════════════════════ -->
        <div class="mt-5 mb-4 px-3" id="reviews-section">
            <div class="d-flex align-items-center justify-content-between mb-3">
                <h4 class="fw-bold text-dark mb-0">⭐ Reviews &amp; Ratings</h4>
                <div id="avg-rating-display">
                    <span class="text-muted small">Loading ratings...</span>
                </div>
            </div>

            <!-- Write a Review -->
            <div class="card border-0 shadow-sm rounded-4 p-4 mb-4">
                <h6 class="fw-bold text-dark mb-3">Write a Review</h6>

                <!-- Star Rating Input -->
                <div class="mb-3 d-flex gap-1" id="star-input">
                    <i class="fa-regular fa-star review-star fs-4 text-warning" data-value="1" style="cursor:pointer;transition:transform 0.15s" onmouseenter="this.style.transform='scale(1.2)'" onmouseleave="this.style.transform='scale(1)'"></i>
                    <i class="fa-regular fa-star review-star fs-4 text-warning" data-value="2" style="cursor:pointer;transition:transform 0.15s" onmouseenter="this.style.transform='scale(1.2)'" onmouseleave="this.style.transform='scale(1)'"></i>
                    <i class="fa-regular fa-star review-star fs-4 text-warning" data-value="3" style="cursor:pointer;transition:transform 0.15s" onmouseenter="this.style.transform='scale(1.2)'" onmouseleave="this.style.transform='scale(1)'"></i>
                    <i class="fa-regular fa-star review-star fs-4 text-warning" data-value="4" style="cursor:pointer;transition:transform 0.15s" onmouseenter="this.style.transform='scale(1.2)'" onmouseleave="this.style.transform='scale(1)'"></i>
                    <i class="fa-regular fa-star review-star fs-4 text-warning" data-value="5" style="cursor:pointer;transition:transform 0.15s" onmouseenter="this.style.transform='scale(1.2)'" onmouseleave="this.style.transform='scale(1)'"></i>
                </div>

                <textarea id="review-text" class="form-control border-light bg-light rounded-3 mb-3" rows="3" placeholder="Share your experience at ${place.name}..." style="resize:none;font-size:0.92rem"></textarea>

                <div id="review-feedback" class="mb-2" style="display:none"></div>

                <button id="submit-review-btn" onclick="submitReview()" class="btn btn-primary rounded-pill px-4 fw-bold shadow-sm">
                    <i class="fa-solid fa-paper-plane me-2"></i>Post Review
                </button>
            </div>

            <!-- Reviews List -->
            <div id="reviews-list">
                <div class="text-center py-4">
                    <span class="spinner-border spinner-border-sm text-primary me-2"></span>
                    <small class="text-muted">Loading reviews...</small>
                </div>
            </div>
        </div>

        <!-- AI Chatbot Floating UI -->
        <div id="ai-chat-widget" style="position: fixed; bottom: 85px; right: 30px; z-index: 1050;">
            <!-- Chat Window -->
            <div id="ai-chat-window" class="card shadow-lg border-0 rounded-4" style="display: none; width: 350px; height: 400px; background: rgba(255, 255, 255, 0.95); backdrop-filter: blur(10px); flex-direction: column; overflow: hidden; margin-bottom: 15px;">
                <div class="card-header bg-primary text-white p-3 fw-bold d-flex justify-content-between align-items-center">
                    <span>🤖 AI Guide</span>
                    <button type="button" class="btn-close btn-close-white" aria-label="Close" onclick="toggleChat()"></button>
                </div>
                <div id="ai-chat-messages" class="card-body p-3 text-sm" style="overflow-y: auto; flex-grow: 1; display: flex; flex-direction: column; gap: 10px; font-size: 0.9rem;">
                    <div class="d-flex align-items-start gap-2">
                        <div class="bg-light text-dark p-2 rounded-3 shadow-sm border" style="max-width: 85%;">
                            Hi! I am your AI Guide. Ask me about the history, entry fees, tips, or how to reach <strong>${place.name}</strong>!
                        </div>
                    </div>
                </div>
                <div class="card-footer bg-white p-2 border-top">
                    <form id="ai-chat-form" class="d-flex gap-2 m-0" onsubmit="window.handleChatSubmit(event, '${place.name.replace(/'/g, "\\'")}', '${(place.detailed_history || place.history || '').replace(/\n/g, '\\n').replace(/'/g, "\\'")}', '${(place.local_tips || '').replace(/\n/g, '\\n').replace(/'/g, "\\'")}', '${(place.how_to_reach || '').replace(/\n/g, '\\n').replace(/'/g, "\\'")}', '${place.entry_fee}', '${place.best_time}', '${place.visit_duration}')">
                        <input type="text" id="ai-chat-input" class="form-control rounded-pill border-secondary" placeholder="Ask a question..." autocomplete="off">
                        <button type="submit" class="btn btn-primary rounded-pill px-3 shadow-sm">
                            ➤
                        </button>
                    </form>
                </div>
            </div>

            <!-- Floating Button -->
            <button id="ai-chat-btn" class="btn btn-primary shadow-lg rounded-circle d-flex align-items-center justify-content-center" style="width: 60px; height: 60px; font-size: 1.5rem; float: right;" onclick="toggleChat()">
                💬
            </button>
        </div>
    `;

    // Re-trigger Bootstrap pills dynamic selection inside JS
    const triggerEl = document.querySelectorAll('#placeTab button');
    triggerEl.forEach(tabEl => {
        tabEl.addEventListener('click', (event) => {
            event.preventDefault();
            const targetSelector = tabEl.getAttribute('data-bs-target');
            
            // Toggle nav links
            document.querySelectorAll('#placeTab .nav-link').forEach(btn => btn.classList.remove('active'));
            tabEl.classList.add('active');
            
            // Toggle panes
            document.querySelectorAll('#placeTabContent .tab-pane').forEach(pane => pane.classList.remove('show', 'active'));
            const pane = document.querySelector(targetSelector);
            if (pane) pane.classList.add('show', 'active');
        });
    });

    // Render true "Nearby Spots" based on distance (not same-category).
    const recContainer = document.getElementById('recommended-container');
    const sourceData = window.cachedPlacesFromFirestore || window.allPlacesData;
    if (recContainer && sourceData) {
        const baseLat = Number(place.lat);
        const baseLon = Number(place.lon);

        const recommended = sourceData
            .filter(p => p && p.id !== place.id && p.lat !== undefined && p.lon !== undefined)
            .map(p => ({
                place: p,
                distKm: window.getHaversineDistance(baseLat, baseLon, Number(p.lat), Number(p.lon))
            }))
            .sort((a, b) => a.distKm - b.distKm)
            .slice(0, 3)
            .map(x => x.place);
        
        if (recommended.length === 0) {
            recContainer.innerHTML = `<p class="text-muted small">No nearby spots found.</p>`;
        } else {
            recommended.forEach(p => {
                const col = document.createElement('div');
                col.className = 'col-12 col-md-4';
                col.innerHTML = `
                    <div class="place-card bg-light rounded shadow-sm overflow-hidden border">
                        <a href="#place-${p.id}" class="d-block" style="text-decoration:none;" onclick="navigateToPlace(${p.id}); return false;">
                            <img src="/static/images/${p.folder_name}/1.jpg" style="width:100%; height:120px; object-fit:cover;" onerror="this.src='/static/icon-192.png'">
                            <div class="p-2 text-center">
                                <div style="font-weight:700; font-size:0.8rem; color:#1e293b;" class="text-truncate">${p.name}</div>
                            </div>
                        </a>
                    </div>
                `;
                recContainer.appendChild(col);
            });
        }
    }

    // Phase 3A & 3B: Trigger geolocation, weather, and reviews loading
    setTimeout(() => {
        window.detectUserLocation(place.lat, place.lon, place.name, place.category);
        window.fetchLiveWeather(place.lat, place.lon, place.category);
        if (window.initReviews) {
            window.initReviews(window.fbDb, window.fbAuth, place.id);
        }
    }, 50);
}

// Reactive Authentication bindings for Index.html (SPA mode)
function initReactiveSPAAuth() {
    if (!window.fbAuthMethods) return;

    window.fbAuthMethods.onAuthStateChanged(window.fbAuth, async (user) => {
        const loader = document.getElementById('splash-loader');
        const authContainer = document.getElementById('auth-container');
        const appView = document.getElementById('app-view');

        if (user) {
            console.log("[Firebase SPA] User is logged in:", user.email);
            
            // Sync user object globally
            window.currentUser = user;

            // Save user profile directly to Firestore
            try {
                const userRef = window.fbFirestoreMethods.doc(window.fbDb, "users", user.uid);
                await window.fbFirestoreMethods.setDoc(userRef, {
                    username: user.displayName || user.email.split('@')[0],
                    email: user.email,
                    updated_at: window.fbFirestoreMethods.serverTimestamp()
                }, { merge: true });
            } catch (e) {
                console.error("Firestore user sync failed:", e);
            }

            // Bind profile stats visually
            const avatar = document.getElementById('profile-avatar');
            const nameEl = document.getElementById('profile-username');
            const emailEl = document.getElementById('profile-email');
            
            if (avatar) avatar.textContent = (user.displayName || user.email)[0].toUpperCase();
            if (nameEl) nameEl.textContent = user.displayName || user.email.split('@')[0];
            if (emailEl) emailEl.innerHTML = `<i class="fa-solid fa-envelope me-1"></i> ${user.email}`;

            // Phase 2A: Query places data from Firestore, auto-seeding if empty
            await loadPlacesFromFirestore();

            // Populate items
            renderFeaturedPlaces();
            populateDestinationSelector();
            
            // Load direct data from Firestore
            await loadWishlistFirestore();
            await loadExpensesFirestore();

            // Toggle screens
            if (authContainer) authContainer.style.display = "none";
            if (appView) appView.style.display = "block";
            
            // Default view to home if not hashed or hash is unrecognised
            const knownSections = ['home','explore','wishlist','expense','budget','profile','place-details'];
            const rawHash = window.location.hash ? window.location.hash.substring(1).toLowerCase() : '';
            const isKnownHash = rawHash && (knownSections.includes(rawHash) || rawHash.startsWith('place-'));
            if (isKnownHash) {
                handleHashRouting();
            } else {
                show('home');
            }
        } else {
            console.log("[Firebase SPA] User is logged out.");
            window.currentUser = null;
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
    const email = document.getElementById('register-email').value.trim();
    const pass = document.getElementById('register-password').value;
    const confirm = document.getElementById('register-confirm').value;
    const errEl = document.getElementById('register-error');

    if (errEl) errEl.style.display = "none";

    if (!email || !pass) {
        if (errEl) { errEl.textContent = "Email and password are required."; errEl.style.display = "block"; }
        return;
    }
    if (pass !== confirm) {
        if (errEl) { errEl.textContent = "Passwords do not match."; errEl.style.display = "block"; }
        return;
    }

    try {
        await window.fbAuthMethods.createUserWithEmailAndPassword(window.fbAuth, email, pass);
    } catch(e) {
        if (errEl) { errEl.textContent = e.message.replace('Firebase: ', ''); errEl.style.display = "block"; }
    }
};

// Client-side login calls
window.firebaseEmailLoginDirect = async function() {
    const email = document.getElementById('login-email').value.trim();
    const pass = document.getElementById('login-password').value;
    const errEl = document.getElementById('login-error');

    if (errEl) errEl.style.display = "none";

    try {
        await window.fbAuthMethods.signInWithEmailAndPassword(window.fbAuth, email, pass);
    } catch(e) {
        if (errEl) { errEl.textContent = e.message.replace('Firebase: ', ''); errEl.style.display = "block"; }
    }
};

window.firebaseGoogleLoginDirect = async function() {
    const errEl = document.getElementById('login-error') || document.getElementById('register-error');
    if (errEl) errEl.style.display = "none";

    try {
        const provider = new window.fbProviders.GoogleAuthProvider();
        provider.setCustomParameters({ prompt: 'select_account' });
        await window.fbAuthMethods.signInWithPopup(window.fbAuth, provider);
    } catch(e) {
        if (errEl) { errEl.textContent = e.message.replace('Firebase: ', ''); errEl.style.display = "block"; }
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
    if (sectionId === 'budget') sectionId = 'expense';
    
    if (sectionId.startsWith('place-')) {
        const placeId = parseInt(sectionId.split('-')[1]);
        if (!isNaN(placeId)) {
            show('place-details');
            renderPlaceDetailsFirestore(placeId);
        }
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

    if (window.location.hash) {
        let sectionId = window.location.hash.substring(1).toLowerCase();
        if (sectionId === 'budget') sectionId = 'expense';
        if (document.getElementById(sectionId)) {
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
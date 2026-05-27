// ============================================================
// NAVIGATION & SEARCH
// ============================================================

function goHome() {
    show('home');
    const searchContainer = document.querySelector(".search-container");
    if (searchContainer) searchContainer.style.display = "block";
}

function show(id) {
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
        // Google Maps resizes automatically — no invalidateSize needed
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

    if (window.allPlacesData) {
        window.allPlacesData.forEach(p => {
            if (p.lat && p.lon) {
                const position = { lat: p.lat, lng: p.lon };
                bounds.extend(position);

                const marker = new google.maps.Marker({ position, title: p.name });

                marker.addListener('click', () => {
                    const catEmoji = { waterfall: '💧', temple: '🛕', fort: '🏰', nature: '🌿', wildlife: '🦅', lake: '🏞️', dam: '🌊', garden: '🌳' };
                    const emoji = catEmoji[p.category.toLowerCase()] || '📍';
                    exploreInfoWindow.setContent(`
                        <div style="width:220px;font-family:Inter,sans-serif;padding:4px">
                            <img src="/static/images/${p.folder}/1.jpg"
                                 style="width:100%;height:115px;object-fit:cover;border-radius:10px;margin-bottom:10px"
                                 onerror="this.style.display='none'">
                            <div style="font-weight:700;font-size:0.92rem;color:#1e293b;margin-bottom:2px">${p.name}</div>
                            <div style="color:#64748b;font-size:0.75rem;text-transform:capitalize;margin-bottom:10px">
                                ${emoji} ${p.category}
                            </div>
                            <a href="/place/${p.id}"
                               style="display:block;text-align:center;background:linear-gradient(135deg,#2563eb,#1d4ed8);
                                      color:white;padding:8px;border-radius:20px;text-decoration:none;
                                      font-weight:700;font-size:0.82rem;letter-spacing:0.3px">
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
            if (panelList) {
                const item = document.createElement('a');
                item.href = `/place/${p.id}`;
                item.className = 'side-panel-item d-flex align-items-center gap-3 p-2 rounded-3 text-decoration-none text-dark mb-1';
                item.setAttribute('data-name', p.name.toLowerCase());
                item.setAttribute('data-category', p.category.toLowerCase());
                item.style.cssText = 'transition:background 0.15s;border:1px solid transparent';
                item.onmouseover = () => { item.style.background = '#f1f5f9'; item.style.borderColor = '#e2e8f0'; };
                item.onmouseout = () => { item.style.background = ''; item.style.borderColor = 'transparent'; };
                item.innerHTML = `
                    <img src="/static/images/${p.folder}/1.jpg"
                         style="width:58px;height:58px;object-fit:cover;border-radius:12px;flex-shrink:0"
                         onerror="this.src='/static/icon-192.png'">
                    <div class="overflow-hidden">
                        <div style="font-weight:700;font-size:0.82rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${p.name}</div>
                        <div style="color:#64748b;font-size:0.72rem;text-transform:capitalize">${p.category}</div>
                    </div>`;
                panelList.appendChild(item);
            }
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
// BACKEND-SYNCED EXPENSE MODULE

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
    try {
        const res = await fetch('/api/expenses');
        if (res.ok) { expenses = await res.json(); updateExpenseUI(); }
    } catch (err) { console.error('Failed to load expenses', err); }
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
                    <button class="btn btn-sm btn-link text-danger p-0 text-decoration-none" style="font-size:0.75rem" onclick="deleteExpense(${exp.id})">Remove</button>
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

async function deleteExpense(id) {
    if (!confirm("Remove this expense?")) return;
    try {
        const res = await fetch(`/api/expenses/${id}`, { method: 'DELETE' });
        if (res.ok) { expenses = expenses.filter(e => e.id !== id); updateExpenseUI(); }
    } catch (err) { console.error("Failed to delete", err); }
}

async function clearAllExpenses() {
    if (confirm("Clear all budget data?")) {
        try {
            const res = await fetch('/api/expenses/clear', { method: 'POST' });
            if (res.ok) { expenses = []; updateExpenseUI(); }
        } catch (err) { console.error("Failed to clear", err); }
    }
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

// ============================================================
// INITIALIZATION
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
    loadExpenses();

    const limit = getBudgetLimit();
    const limitText = document.getElementById("budget-limit-text");
    const budgetInput = document.getElementById("budget-goal-input");
    if (limitText) limitText.innerText = `₹${limit.toLocaleString('en-IN')}`;
    if (budgetInput) budgetInput.value = limit;

    if (window.location.hash) {
        const sectionId = window.location.hash.substring(1);
        if (document.getElementById(sectionId)) show(sectionId);
    } else {
        const activeBtn = document.getElementById(`nav-home`);
        if (activeBtn) activeBtn.classList.add('active');
    }
});
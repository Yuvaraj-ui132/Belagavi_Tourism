// --- NAVIGATION & SEARCH ---

function goHome() {
    show('home');
    const searchContainer = document.querySelector(".search-container");
    if (searchContainer) searchContainer.style.display = "block";
}

function show(id) {
    // Hide all sections smoothly
    document.querySelectorAll(".section").forEach(s => {
        s.style.display = "none";
    });

    // Show target section
    const target = document.getElementById(id);
    if (target) {
        target.style.display = "block";
    }

    // Handle Active Nav State
    document.querySelectorAll(".bottom-nav button").forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`nav-${id}`);
    if (activeBtn) activeBtn.classList.add('active');

    if (id === "profile") refreshProfileStats();
    
    // Map handling logic (fix Leaflet render bug)
    if (id === "explore") {
        if (!exploreMap) initExploreMap();
        else exploreMap.invalidateSize();
    }
}

function filterPlaces() {
    const q = document.getElementById("search").value.toLowerCase();
    const items = document.querySelectorAll(".search-item");
    const grid = document.getElementById("placeGrid");
    
    if (q.length > 0) {
        // Find grid items to show/hide in the regular search
        const placeCards = document.querySelectorAll("#home .col-12");
        placeCards.forEach(item => {
            const name = item.querySelector("h3").innerText.toLowerCase();
            if (name.includes(q)) {
                item.style.display = "block";
            } else {
                item.style.display = "none";
            }
        });
    } else {
        // Reset
        document.querySelectorAll("#home .col-12").forEach(item => item.style.display = "block");
    }
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
        if(btn) {
            btn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Save`;
            btn.classList.replace('btn-primary', 'btn-outline-primary');
        }
    } else {
        fetch(`/add_to_wishlist/${placeId}`, { method: 'POST' });
        const div = document.createElement('div');
        div.id = `wish-item-${placeId}`;
        div.className = "card mb-3 p-2 shadow-sm d-flex flex-row align-items-center border-0 rounded-4";
        div.innerHTML = `
            <img src="/static/images/${folder}/1.jpg" loading="lazy" style="width: 70px; height: 70px; object-fit: cover; border-radius: 12px;">
            <div class="ms-3 flex-grow-1">
                <h6 class="m-0 fw-bold">${name}</h6>
            </div>
            <button class="btn btn-link text-danger text-decoration-none" onclick="toggleWishlist(null, ${placeId})"><i class="fa-solid fa-trash-can"></i></button>`;
        container.appendChild(div);
        if(document.getElementById('empty-msg')) document.getElementById('empty-msg').style.display = 'none';
        
        if(btn) {
            btn.innerHTML = `<i class="fa-solid fa-heart me-1"></i> Saved`;
            btn.classList.replace('btn-outline-primary', 'btn-primary');
        }
    }
}

// --- EXPLORE & MAP LOGIC ---
let exploreMap = null;
let markerClusterGroup = null;
let allMarkers = [];
let isMapView = true;
let exploreActiveCategory = 'all';

function initExploreMap() {
    exploreMap = L.map('explore-map', { zoomControl: false }).setView([15.8497, 74.4977], 10);
    L.control.zoom({ position: 'bottomleft' }).addTo(exploreMap);
    
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(exploreMap);
    
    markerClusterGroup = L.markerClusterGroup({
        maxClusterRadius: 50,
        spiderfyOnMaxZoom: true,
        showCoverageOnHover: false,
        zoomToBoundsOnClick: true
    });
    exploreMap.addLayer(markerClusterGroup);
    
    let bounds = [];

    if(window.allPlacesData) {
        window.allPlacesData.forEach(p => {
            if (p.lat && p.lon) {
                const coords = [p.lat, p.lon];
                bounds.push(coords);
                
                const popupHtml = `
                    <div class="explore-popup">
                        <img src="/static/images/${p.folder}/1.jpg" class="popup-img">
                        <div class="popup-content">
                            <h6 class="fw-bold mb-1">${p.name}</h6>
                            <small class="text-muted d-block mb-2 text-capitalize">${p.category}</small>
                            <a href="/place/${p.id}" class="btn btn-sm btn-primary w-100 fw-bold shadow-sm">View Details</a>
                        </div>
                    </div>
                `;
                
                const marker = L.marker(coords).bindPopup(popupHtml);
                
                marker.on('click', function() {
                    exploreMap.flyTo(coords, 14, { duration: 0.5 });
                });

                allMarkers.push({
                    name: p.name.toLowerCase(),
                    category: p.category.toLowerCase(),
                    marker: marker
                });
                markerClusterGroup.addLayer(marker);
            }
        });
    }

    if (bounds.length > 0) exploreMap.fitBounds(bounds, { padding: [50, 50] });
}

function toggleExploreView() {
    isMapView = !isMapView;
    document.getElementById('explore-map-container').style.display = isMapView ? 'block' : 'none';
    document.getElementById('explore-grid-container').style.display = isMapView ? 'none' : 'block';
    document.getElementById('view-toggle-text').innerHTML = isMapView ? '<i class="fa-solid fa-table-cells me-1"></i> Grid' : '<i class="fa-solid fa-map me-1"></i> Map';
    if (isMapView && exploreMap) exploreMap.invalidateSize();
}

function recenterExploreMap() {
    if (!exploreMap) return;
    const visibleMarkers = allMarkers.filter(m => markerClusterGroup.hasLayer(m.marker));
    if (visibleMarkers.length > 0) {
        exploreMap.fitBounds(visibleMarkers.map(m => m.marker.getLatLng()), { padding: [50, 50], maxZoom: 14 });
    } else {
        exploreMap.setView([15.8497, 74.4977], 10);
    }
}

function setCategory(category, btnElement) {
    exploreActiveCategory = category.toLowerCase();
    
    document.querySelectorAll('.explore-filter-btn').forEach(btn => {
        btn.classList.remove('btn-primary', 'fw-bold', 'text-white');
        btn.classList.add('btn-light', 'border');
    });

    btnElement.classList.remove('btn-light', 'border');
    btnElement.classList.add('btn-primary', 'fw-bold', 'text-white');
    applyExploreFilters();
}

function applyExploreFilters() {
    const searchInput = document.getElementById('exploreSearch').value.toLowerCase();
    let visibleCount = 0;

    // Grid
    document.querySelectorAll('.explore-item').forEach(item => {
        const matchesSearch = item.getAttribute('data-name').includes(searchInput);
        const matchesCat = (exploreActiveCategory === 'all' || item.getAttribute('data-category').includes(exploreActiveCategory));

        if (matchesSearch && matchesCat) {
            item.style.display = 'block';
            visibleCount++;
        } else {
            item.style.display = 'none';
        }
    });
    document.getElementById('no-results-msg').style.display = visibleCount === 0 ? 'block' : 'none';

    // Map
    if (markerClusterGroup) {
        markerClusterGroup.clearLayers();
        allMarkers.forEach(m => {
            const matchesSearch = m.name.includes(searchInput);
            const matchesCat = (exploreActiveCategory === 'all' || m.category.includes(exploreActiveCategory));
            if (matchesSearch && matchesCat) markerClusterGroup.addLayer(m.marker);
        });
    }
}

// --- BACKEND-SYNCED EXPENSE MODULE ---
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
        if (res.ok) {
            expenses = await res.json();
            updateExpenseUI();
        }
    } catch (err) {
        console.error('Failed to load expenses', err);
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
        alert("Please select a valid destination and amount.");
        return;
    }

    const payload = {
        location: location, 
        name: name,
        amount: amount,
        category: category,
        date: new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
    };

    try {
        const res = await fetch('/api/expenses', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.status === 'success') {
            payload.id = data.id;
            expenses.unshift(payload);
            updateExpenseUI();
            
            // Reset form
            nameInput.value = "";
            amountInput.value = "";
            locInput.selectedIndex = 0;
        }
    } catch (err) {
        console.error("Failed to add expense", err);
    }
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
        let locationTotal = items.reduce((sum, item) => sum + item.amount, 0);
        
        let cardHtml = `
        <div class="card mb-3 border-0 shadow-sm rounded-4 overflow-hidden">
            <div class="bg-light p-3 border-bottom d-flex justify-content-between align-items-center">
                <h6 class="fw-bold mb-0 text-dark"><i class="fa-solid fa-location-dot text-primary me-2"></i> ${location}</h6>
                <span class="badge bg-primary rounded-pill px-3 py-2 shadow-sm">₹${locationTotal.toLocaleString('en-IN')}</span>
            </div>
            <div class="card-body p-0">
        `;
        
        items.forEach(exp => {
            cardHtml += `
            <div class="d-flex align-items-center justify-content-between p-3 border-bottom">
                <div class="d-flex align-items-center">
                    <div class="rounded-circle bg-light d-flex align-items-center justify-content-center me-3 text-primary" style="width: 40px; height: 40px; font-size: 1.2rem;">
                        ${getCategoryIcon(exp.category)}
                    </div>
                    <div>
                        <h6 class="mb-1 fw-bold text-dark" style="font-size: 0.9rem;">${exp.category}</h6>
                        <small class="text-muted d-block" style="font-size: 0.75rem;">${exp.name || 'No note'} • ${exp.date}</small>
                    </div>
                </div>
                <div class="text-end">
                    <span class="fw-bold text-dark d-block mb-1">₹${exp.amount.toLocaleString('en-IN')}</span>
                    <button class="btn btn-sm btn-link text-danger p-0 text-decoration-none" style="font-size: 0.75rem;" onclick="deleteExpense(${exp.id})">Remove</button>
                </div>
            </div>
            `;
        });
        
        cardHtml += `</div></div>`;
        list.innerHTML += cardHtml;
    }

    totalDisplay.innerText = `₹${total.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;

    const limit = getBudgetLimit();
    const percent = limit > 0 ? (total / limit) * 100 : 0;
    if (progressBar) {
        progressBar.style.width = Math.min(percent, 100) + "%";
        if (percent > 90) progressBar.className = "progress-bar bg-danger";
        else if (percent > 70) progressBar.className = "progress-bar bg-warning";
        else progressBar.className = "progress-bar bg-success";
    }

    if (warningText) {
        warningText.innerText = `${Math.round(percent)}% of your ₹${limit.toLocaleString('en-IN')} budget used`;
    }
}

async function deleteExpense(id) {
    if(!confirm("Remove this expense?")) return;
    try {
        const res = await fetch(`/api/expenses/${id}`, { method: 'DELETE' });
        if (res.ok) {
            expenses = expenses.filter(e => e.id !== id);
            updateExpenseUI();
        }
    } catch(err) {
        console.error("Failed to delete", err);
    }
}

async function clearAllExpenses() {
    if(confirm("Are you sure you want to clear all budget data?")) {
        try {
            const res = await fetch('/api/expenses/clear', { method: 'POST' });
            if (res.ok) {
                expenses = [];
                updateExpenseUI();
            }
        } catch(err) {
            console.error("Failed to clear", err);
        }
    }
}

function updateBudgetGoal(newLimit) {
    const limitNumber = parseInt(newLimit, 10);
    if (!limitNumber || limitNumber < 1) return;
    localStorage.setItem('budget_limit', String(limitNumber));
    const limitText = document.getElementById("budget-limit-text");
    if (limitText) {
        limitText.innerText = `₹${limitNumber.toLocaleString('en-IN')}`;
    }
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


// Initialization
document.addEventListener('DOMContentLoaded', () => {
    // Load persisted data
    loadExpenses();

    // Init Budget limits
    const limit = getBudgetLimit();
    const limitText = document.getElementById("budget-limit-text");
    const budgetInput = document.getElementById("budget-goal-input");
    if (limitText) limitText.innerText = `₹${limit.toLocaleString('en-IN')}`;
    if (budgetInput) budgetInput.value = limit;

    // Check URL Hash for deep linking
    if (window.location.hash) {
        const sectionId = window.location.hash.substring(1);
        if (document.getElementById(sectionId)) {
            show(sectionId);
        }
    } else {
        // Set home active by default if on planner
        const activeBtn = document.getElementById(`nav-home`);
        if (activeBtn) activeBtn.classList.add('active');
    }
});
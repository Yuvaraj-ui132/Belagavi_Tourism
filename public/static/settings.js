// ============================================================
// SETTINGS / ACCOUNT / PRIVACY & SECURITY MODULE
// Belagavi Tourism Smart Guide
// ============================================================

(function () {
    'use strict';

    // ── Tab Management inside #profile ──────────────────────────
    function switchProfileTab(tabName) {
        const overviewEl = document.getElementById('profile-tab-content-overview');
        const settingsEl = document.getElementById('profile-tab-content-settings');
        const tabOverviewBtn = document.getElementById('tab-profile-overview');
        const tabSettingsBtn = document.getElementById('tab-profile-settings');

        if (tabName === 'settings') {
            if (overviewEl) overviewEl.style.display = 'none';
            if (settingsEl) settingsEl.style.display = 'block';
            if (tabOverviewBtn) {
                tabOverviewBtn.classList.remove('active');
                tabOverviewBtn.setAttribute('aria-selected', 'false');
            }
            if (tabSettingsBtn) {
                tabSettingsBtn.classList.add('active');
                tabSettingsBtn.setAttribute('aria-selected', 'true');
            }
            loadUserSettingsData();
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', '#settings');
            }
        } else {
            if (overviewEl) overviewEl.style.display = 'block';
            if (settingsEl) settingsEl.style.display = 'none';
            if (tabOverviewBtn) {
                tabOverviewBtn.classList.add('active');
                tabOverviewBtn.setAttribute('aria-selected', 'true');
            }
            if (tabSettingsBtn) {
                tabSettingsBtn.classList.remove('active');
                tabSettingsBtn.setAttribute('aria-selected', 'false');
            }
            if (typeof window.refreshProfileStats === 'function') {
                window.refreshProfileStats();
            }
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', '#profile');
            }
        }
    }
    window.switchProfileTab = switchProfileTab;

    // ── Load User Settings & Account Info ───────────────────────
    async function loadUserSettingsData() {
        const auth = window.fbAuth;
        const user = auth ? auth.currentUser : null;
        if (!user) return;

        const uid = user.uid;
        const db = window.fbDb;
        const fm = window.fbFirestoreMethods;

        // 1. Account Info Binding
        const nameVal = user.displayName || (user.email ? user.email.split('@')[0] : 'Traveller');
        const emailVal = user.email || '—';
        const initialVal = nameVal.charAt(0).toUpperCase();

        const settingsNameEl = document.getElementById('settings-account-name');
        const settingsEmailEl = document.getElementById('settings-account-email');
        const settingsUidEl = document.getElementById('settings-account-uid');
        const settingsAvatarEl = document.getElementById('settings-account-avatar');
        const settingsProviderBadge = document.getElementById('settings-provider-badge');
        const securityProviderInfo = document.getElementById('security-provider-info');
        const passwordChangeCard = document.getElementById('password-change-card');
        const googleAccountInfo = document.getElementById('google-account-info');

        if (settingsNameEl) settingsNameEl.textContent = nameVal;
        if (settingsEmailEl) settingsEmailEl.textContent = emailVal;
        if (settingsUidEl) settingsUidEl.textContent = uid;
        if (settingsAvatarEl) settingsAvatarEl.textContent = initialVal;

        // Detect provider
        let isGoogle = false;
        let isPassword = false;
        if (Array.isArray(user.providerData)) {
            user.providerData.forEach(p => {
                if (p.providerId === 'google.com') isGoogle = true;
                if (p.providerId === 'password') isPassword = true;
            });
        }
        // Fallback check
        if (!isGoogle && !isPassword) {
            if (user.providerId === 'google.com') isGoogle = true;
            else isPassword = true;
        }

        if (settingsProviderBadge) {
            if (isGoogle) {
                settingsProviderBadge.innerHTML = '<i class="fa-brands fa-google me-1" style="color:#4285F4;"></i> Google Account';
            } else {
                settingsProviderBadge.innerHTML = '<i class="fa-solid fa-envelope me-1"></i> Email &amp; Password';
            }
        }

        if (securityProviderInfo) {
            if (isGoogle) {
                securityProviderInfo.textContent = 'Authenticated via Google Single Sign-On.';
            } else {
                securityProviderInfo.textContent = 'Authenticated with Email and Password.';
            }
        }

        // Show/hide password change card and toggle based on provider
        const passwordAccountRow = document.getElementById('security-password-account-row');
        const toggleBtn = document.getElementById('btn-toggle-password-form');

        if (isGoogle) {
            if (passwordAccountRow) passwordAccountRow.style.display = 'none';
            if (passwordChangeCard) passwordChangeCard.style.display = 'none';
            if (googleAccountInfo) googleAccountInfo.style.display = 'flex';
        } else {
            if (passwordAccountRow) passwordAccountRow.style.display = 'flex';
            if (passwordChangeCard) passwordChangeCard.style.display = 'none';
            if (toggleBtn) toggleBtn.style.display = 'inline-flex';
            if (googleAccountInfo) googleAccountInfo.style.display = 'none';
        }

        // 2. Preferences from Firestore users/{uid}
        if (db && fm) {
            try {
                const userRef = fm.doc(db, 'users', uid);
                const userSnap = await fm.getDoc(userRef);
                if (userSnap.exists()) {
                    const data = userSnap.data() || {};
                    const prefs = data.preferences || {};

                    const prefAlerts = document.getElementById('pref-travel-alerts');
                    const prefBudget = document.getElementById('pref-budget-reminders');
                    const prefLang = document.getElementById('pref-language');

                    if (prefAlerts && prefs.travelAlerts !== undefined) {
                        prefAlerts.checked = !!prefs.travelAlerts;
                    }
                    if (prefBudget && prefs.budgetReminders !== undefined) {
                        prefBudget.checked = !!prefs.budgetReminders;
                    }
                    if (prefLang && prefs.language) {
                        prefLang.value = prefs.language;
                    }
                }
            } catch (err) {
                console.warn('[Settings] Error fetching user preferences:', err);
            }
        }

        // 3. Check Location Permission
        checkLocationPermissionState();

        // 4. Update Data Category Counts
        updateDataCategoryCounts();
    }
    window.loadUserSettingsData = loadUserSettingsData;

    // ── Update Data Category Counts ─────────────────────────────
    async function updateDataCategoryCounts() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user || !window.fbDb || !window.fbFirestoreMethods) return;

        const uid = user.uid;
        const db = window.fbDb;
        const fm = window.fbFirestoreMethods;

        const wishCountEl = document.getElementById('settings-data-wishlist-count');
        const expCountEl = document.getElementById('settings-data-expenses-count');
        const revCountEl = document.getElementById('settings-data-reviews-count');

        try {
            // Wishlist
            const wishQuery = fm.query(fm.collection(db, 'wishlist'), fm.where('user_id', '==', uid));
            const wishSnap = await fm.getDocs(wishQuery);
            if (wishCountEl) wishCountEl.textContent = `${wishSnap.size} places`;

            // Expenses
            const expQuery = fm.query(fm.collection(db, 'expenses'), fm.where('user_id', '==', uid));
            const expSnap = await fm.getDocs(expQuery);
            if (expCountEl) expCountEl.textContent = `${expSnap.size} records`;

            // Reviews count from stat-review-count if already computed
            const existingRevStat = document.getElementById('stat-review-count');
            if (revCountEl && existingRevStat) {
                revCountEl.textContent = `${existingRevStat.textContent || '0'} reviews`;
            }
        } catch (e) {
            console.warn('[Settings] Count update warning:', e);
        }
    }
    window.updateDataCategoryCounts = updateDataCategoryCounts;

    // ── Profile Name Editing ────────────────────────────────────
    function openEditProfileModal() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user) return;
        const currentName = user.displayName || (user.email ? user.email.split('@')[0] : '');
        const input = document.getElementById('edit-profile-name-input');
        const errEl = document.getElementById('edit-profile-error');
        if (input) input.value = currentName;
        if (errEl) errEl.style.display = 'none';

        const modal = document.getElementById('modal-edit-profile');
        if (modal) modal.style.display = 'flex';
    }
    window.openEditProfileModal = openEditProfileModal;

    function closeEditProfileModal() {
        const modal = document.getElementById('modal-edit-profile');
        if (modal) modal.style.display = 'none';
    }
    window.closeEditProfileModal = closeEditProfileModal;

    async function saveProfileName() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user) return;

        const input = document.getElementById('edit-profile-name-input');
        const errEl = document.getElementById('edit-profile-error');
        const btn = document.getElementById('save-profile-name-btn');
        const newName = (input ? input.value : '').trim();

        if (errEl) errEl.style.display = 'none';

        if (!newName || newName.length < 2) {
            if (errEl) {
                errEl.textContent = 'Please enter a valid name (at least 2 characters).';
                errEl.style.display = 'block';
            }
            return;
        }

        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-1"></i> Saving...';
        }

        try {
            // 1. Update Firebase Auth Profile
            if (window.fbAuthMethods && typeof window.fbAuthMethods.updateProfile === 'function') {
                await window.fbAuthMethods.updateProfile(user, { displayName: newName });
            }

            // 2. Update Firestore users/{uid}
            if (window.fbDb && window.fbFirestoreMethods) {
                const userRef = window.fbFirestoreMethods.doc(window.fbDb, 'users', user.uid);
                await window.fbFirestoreMethods.setDoc(userRef, {
                    username: newName,
                    updated_at: window.fbFirestoreMethods.serverTimestamp()
                }, { merge: true });
            }

            // 3. Update Visual Displays Across SPA
            const initial = newName.charAt(0).toUpperCase();
            ['profile-avatar', 'nav-avatar-char', 'settings-account-avatar'].forEach(id => {
                const el = document.getElementById(id);
                if (el) el.textContent = initial;
            });
            ['profile-username', 'settings-account-name'].forEach(id => {
                const el = document.getElementById(id);
                if (el) el.textContent = newName;
            });
            const navUserShort = document.getElementById('nav-username-short');
            if (navUserShort) navUserShort.textContent = newName.split(' ')[0];

            closeEditProfileModal();
            showSettingsAlert('success', 'Profile name updated successfully.');
        } catch (err) {
            console.error('[Settings] Error saving profile name:', err);
            if (errEl) {
                errEl.textContent = err.message ? err.message.replace('Firebase: ', '') : 'Could not update name. Please try again.';
                errEl.style.display = 'block';
            }
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = 'Save Changes';
            }
        }
    }
    window.saveProfileName = saveProfileName;

    // ── Password Change Flow ───────────────────────────────────
    async function changeUserPassword() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user) return;

        const currentPassInput = document.getElementById('security-current-pass');
        const newPassInput = document.getElementById('security-new-pass');
        const confirmPassInput = document.getElementById('security-confirm-pass');
        const errEl = document.getElementById('security-password-error');
        const btn = document.getElementById('security-update-pass-btn');

        if (errEl) errEl.style.display = 'none';

        const currentPass = currentPassInput ? currentPassInput.value : '';
        const newPass = newPassInput ? newPassInput.value : '';
        const confirmPass = confirmPassInput ? confirmPassInput.value : '';

        if (!currentPass) {
            if (errEl) { errEl.textContent = 'Please enter your current password.'; errEl.style.display = 'block'; }
            return;
        }
        if (!newPass || newPass.length < 6) {
            if (errEl) { errEl.textContent = 'New password must be at least 6 characters.'; errEl.style.display = 'block'; }
            return;
        }
        if (newPass !== confirmPass) {
            if (errEl) { errEl.textContent = 'New passwords do not match.'; errEl.style.display = 'block'; }
            return;
        }

        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-1"></i> Updating...';
        }

        try {
            const authMethods = window.fbAuthMethods;
            const providers = window.fbProviders;

            // 1. Re-authenticate to ensure recent session
            if (authMethods.reauthenticateWithCredential && providers.EmailAuthProvider && user.email) {
                const cred = providers.EmailAuthProvider.credential(user.email, currentPass);
                await authMethods.reauthenticateWithCredential(user, cred);
            }

            // 2. Update password
            if (authMethods.updatePassword) {
                await authMethods.updatePassword(user, newPass);
            } else {
                throw new Error('updatePassword method unavailable.');
            }

            if (currentPassInput) currentPassInput.value = '';
            if (newPassInput) newPassInput.value = '';
            if (confirmPassInput) confirmPassInput.value = '';

            togglePasswordForm(false);
            showSettingsAlert('success', 'Password updated successfully! Your credentials have been updated.');
        } catch (err) {
            console.error('[Settings] Error changing password:', err);
            let msg = 'Could not update password. Please check your current password.';
            if (err.code === 'auth/wrong-password' || err.code === 'auth/invalid-credential') {
                msg = 'Incorrect current password. Please try again.';
            } else if (err.code === 'auth/weak-password') {
                msg = 'Password is too weak. Please choose a stronger password.';
            } else if (err.code === 'auth/requires-recent-login') {
                msg = 'For security, please sign out and sign in again before changing password.';
            }
            if (errEl) {
                errEl.textContent = msg;
                errEl.style.display = 'block';
            }
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-key me-1"></i> Update Password';
            }
        }
    }
    window.changeUserPassword = changeUserPassword;

    // ── Toggle Password Update Form ────────────────────────────
    function togglePasswordForm(show) {
        const card = document.getElementById('password-change-card');
        const toggleBtn = document.getElementById('btn-toggle-password-form');
        const errEl = document.getElementById('security-password-error');
        const currentPass = document.getElementById('security-current-pass');
        const newPass = document.getElementById('security-new-pass');
        const confirmPass = document.getElementById('security-confirm-pass');

        if (show) {
            if (card) card.style.display = 'block';
            if (toggleBtn) toggleBtn.style.display = 'none';
            if (currentPass) currentPass.focus();
        } else {
            if (card) card.style.display = 'none';
            if (toggleBtn) toggleBtn.style.display = 'inline-flex';
            if (currentPass) currentPass.value = '';
            if (newPass) newPass.value = '';
            if (confirmPass) confirmPass.value = '';
            if (errEl) errEl.style.display = 'none';
        }
    }
    window.togglePasswordForm = togglePasswordForm;

    // ── Preferences Persistence ────────────────────────────────
    async function saveUserPreferences() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user || !window.fbDb || !window.fbFirestoreMethods) return;

        const prefAlerts = document.getElementById('pref-travel-alerts');
        const prefBudget = document.getElementById('pref-budget-reminders');
        const prefLang = document.getElementById('pref-language');

        const prefsData = {
            travelAlerts: prefAlerts ? prefAlerts.checked : true,
            budgetReminders: prefBudget ? prefBudget.checked : true,
            language: prefLang ? prefLang.value : 'en'
        };

        try {
            const userRef = window.fbFirestoreMethods.doc(window.fbDb, 'users', user.uid);
            await window.fbFirestoreMethods.setDoc(userRef, {
                preferences: prefsData,
                updated_at: window.fbFirestoreMethods.serverTimestamp()
            }, { merge: true });

            showSettingsAlert('success', 'Preferences saved.');
        } catch (err) {
            console.warn('[Settings] Error saving preferences:', err);
        }
    }
    window.saveUserPreferences = saveUserPreferences;

    // ── Location Services Check ────────────────────────────────
    function checkLocationPermissionState() {
        const statusBadge = document.getElementById('settings-location-status');
        if (!statusBadge) return;

        if (!navigator.geolocation) {
            statusBadge.innerHTML = '<span class="settings-status-badge neutral"><span class="settings-status-dot"></span> Unavailable</span>';
            return;
        }

        if (navigator.permissions && navigator.permissions.query) {
            navigator.permissions.query({ name: 'geolocation' }).then(result => {
                updateLocationBadge(result.state);
                result.onchange = () => updateLocationBadge(result.state);
            }).catch(() => {
                statusBadge.innerHTML = '<span class="settings-status-badge neutral"><span class="settings-status-dot"></span> Prompt</span>';
            });
        } else {
            statusBadge.innerHTML = '<span class="settings-status-badge neutral"><span class="settings-status-dot"></span> Prompt</span>';
        }
    }

    function updateLocationBadge(state) {
        const statusBadge = document.getElementById('settings-location-status');
        if (!statusBadge) return;
        if (state === 'granted') {
            statusBadge.innerHTML = '<span class="settings-status-badge allowed"><span class="settings-status-dot"></span> Allowed</span>';
        } else if (state === 'denied') {
            statusBadge.innerHTML = '<span class="settings-status-badge blocked"><span class="settings-status-dot"></span> Blocked</span>';
        } else if (state === 'prompt') {
            statusBadge.innerHTML = '<span class="settings-status-badge neutral"><span class="settings-status-dot"></span> Prompt</span>';
        } else {
            statusBadge.innerHTML = '<span class="settings-status-badge neutral"><span class="settings-status-dot"></span> Unavailable</span>';
        }
    }

    function requestLocationCheck() {
        if (!navigator.geolocation) {
            alert('Geolocation is not supported by your browser.');
            return;
        }
        navigator.geolocation.getCurrentPosition(
            pos => {
                updateLocationBadge('granted');
                showSettingsAlert('success', `Location active: Coordinates acquired (${pos.coords.latitude.toFixed(2)}, ${pos.coords.longitude.toFixed(2)}).`);
            },
            err => {
                if (err.code === err.PERMISSION_DENIED) {
                    updateLocationBadge('denied');
                    showSettingsAlert('warning', 'Location access was blocked in your browser settings.');
                } else {
                    showSettingsAlert('info', 'Location check completed.');
                }
            },
            { timeout: 8000 }
        );
    }
    window.requestLocationCheck = requestLocationCheck;

    // ── Data Clear Actions & Confirmation Dialog ───────────────
    function requestClearData(type) {
        const modal = document.getElementById('modal-clear-confirm');
        const titleEl = document.getElementById('clear-confirm-title');
        const bodyEl = document.getElementById('clear-confirm-body');
        const btn = document.getElementById('clear-confirm-action-btn');

        if (type === 'wishlist') {
            if (titleEl) titleEl.textContent = 'Clear Saved Places?';
            if (bodyEl) bodyEl.textContent = 'This will remove your saved places from your wishlist. This action cannot be undone.';
            if (btn) {
                btn.innerHTML = '<i class="fa-solid fa-trash-can me-1"></i> Clear Saved Places';
                btn.onclick = () => {
                    closeClearConfirmModal();
                    executeClearWishlist();
                };
            }
        } else if (type === 'expenses') {
            if (titleEl) titleEl.textContent = 'Clear Logged Expenses?';
            if (bodyEl) bodyEl.textContent = 'This will remove all trip expense records and reset your budget spending tracker. This action cannot be undone.';
            if (btn) {
                btn.innerHTML = '<i class="fa-solid fa-trash-can me-1"></i> Clear Expenses';
                btn.onclick = () => {
                    closeClearConfirmModal();
                    executeClearExpenses();
                };
            }
        }
        if (modal) modal.style.display = 'flex';
    }
    window.requestClearData = requestClearData;

    function closeClearConfirmModal() {
        const modal = document.getElementById('modal-clear-confirm');
        if (modal) modal.style.display = 'none';
    }
    window.closeClearConfirmModal = closeClearConfirmModal;

    async function executeClearWishlist() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user || !window.fbDb || !window.fbFirestoreMethods) return;

        try {
            const fm = window.fbFirestoreMethods;
            const db = window.fbDb;
            const q = fm.query(fm.collection(db, 'wishlist'), fm.where('user_id', '==', user.uid));
            const snap = await fm.getDocs(q);

            const deletes = [];
            snap.forEach(d => deletes.push(fm.deleteDoc(d.ref)));
            await Promise.all(deletes);

            const q2 = fm.query(fm.collection(db, 'wishlist'), fm.where('userId', '==', user.uid));
            const snap2 = await fm.getDocs(q2);
            const deletes2 = [];
            snap2.forEach(d => deletes2.push(fm.deleteDoc(d.ref)));
            await Promise.all(deletes2);

            if (window.userWishlistPlaceIds) {
                window.userWishlistPlaceIds.clear();
            }
            if (typeof window.syncWishlistCardStates === 'function') {
                window.syncWishlistCardStates();
            }
            if (typeof window.renderWishlistUI === 'function') {
                window.renderWishlistUI();
            }
            if (typeof window.refreshProfileStats === 'function') {
                window.refreshProfileStats();
            }
            updateDataCategoryCounts();
            showSettingsAlert('success', 'All saved places cleared.');
        } catch (err) {
            console.error('[Settings] Error clearing wishlist:', err);
            showSettingsAlert('danger', 'Could not clear wishlist. Please try again.');
        }
    }

    async function executeClearExpenses() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user || !window.fbDb || !window.fbFirestoreMethods) return;

        try {
            const fm = window.fbFirestoreMethods;
            const db = window.fbDb;
            const q = fm.query(fm.collection(db, 'expenses'), fm.where('user_id', '==', user.uid));
            const snap = await fm.getDocs(q);

            const deletes = [];
            snap.forEach(d => deletes.push(fm.deleteDoc(d.ref)));
            await Promise.all(deletes);

            const q2 = fm.query(fm.collection(db, 'expenses'), fm.where('userId', '==', user.uid));
            const snap2 = await fm.getDocs(q2);
            const deletes2 = [];
            snap2.forEach(d => deletes2.push(fm.deleteDoc(d.ref)));
            await Promise.all(deletes2);

            if (typeof window.loadExpenses === 'function') {
                window.loadExpenses();
            }
            if (typeof window.refreshProfileStats === 'function') {
                window.refreshProfileStats();
            }
            updateDataCategoryCounts();
            showSettingsAlert('success', 'All trip expenses cleared.');
        } catch (err) {
            console.error('[Settings] Error clearing expenses:', err);
            showSettingsAlert('danger', 'Could not clear expenses. Please try again.');
        }
    }

    function clearAllWishlistDirect() {
        requestClearData('wishlist');
    }
    window.clearAllWishlistDirect = clearAllWishlistDirect;

    function clearAllExpensesDirect() {
        requestClearData('expenses');
    }
    window.clearAllExpensesDirect = clearAllExpensesDirect;

    // ── Privacy & Terms Modals ─────────────────────────────────
    function openPrivacyModal() {
        const m = document.getElementById('modal-privacy-policy');
        if (m) m.style.display = 'flex';
    }
    window.openPrivacyModal = openPrivacyModal;

    function closePrivacyModal() {
        const m = document.getElementById('modal-privacy-policy');
        if (m) m.style.display = 'none';
    }
    window.closePrivacyModal = closePrivacyModal;

    function openTermsModal() {
        const m = document.getElementById('modal-terms-of-service');
        if (m) m.style.display = 'flex';
    }
    window.openTermsModal = openTermsModal;

    function closeTermsModal() {
        const m = document.getElementById('modal-terms-of-service');
        if (m) m.style.display = 'none';
    }
    window.closeTermsModal = closeTermsModal;

    // ── Help & Support / Feedback ──────────────────────────────
    function openFeedbackModal() {
        const m = document.getElementById('modal-support-feedback');
        const err = document.getElementById('support-feedback-error');
        const text = document.getElementById('support-feedback-text');
        if (err) err.style.display = 'none';
        if (text) text.value = '';
        if (m) m.style.display = 'flex';
    }
    window.openFeedbackModal = openFeedbackModal;

    function closeFeedbackModal() {
        const m = document.getElementById('modal-support-feedback');
        if (m) m.style.display = 'none';
    }
    window.closeFeedbackModal = closeFeedbackModal;

    async function submitUserFeedback() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        const textEl = document.getElementById('support-feedback-text');
        const typeEl = document.getElementById('support-feedback-type');
        const errEl = document.getElementById('support-feedback-error');
        const btn = document.getElementById('submit-feedback-btn');

        const message = (textEl ? textEl.value : '').trim();
        const type = typeEl ? typeEl.value : 'General Inquiry';

        if (errEl) errEl.style.display = 'none';

        if (!message) {
            if (errEl) {
                errEl.textContent = 'Please enter your message before sending.';
                errEl.style.display = 'block';
            }
            return;
        }

        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-1"></i> Sending...';
        }

        try {
            if (window.fbDb && window.fbFirestoreMethods) {
                const fm = window.fbFirestoreMethods;
                await fm.addDoc(fm.collection(window.fbDb, 'feedback'), {
                    userId: user ? user.uid : 'anonymous',
                    email: user ? (user.email || '') : '',
                    type: type,
                    message: message,
                    timestamp: fm.serverTimestamp()
                });
            }
            closeFeedbackModal();
            showSettingsAlert('success', 'Thank you! Your feedback has been received.');
        } catch (err) {
            console.error('[Settings] Feedback submission error:', err);
            // Fallback: mailto link
            window.location.href = `mailto:support@belagavitourism.com?subject=Belagavi%20Tourism%20${encodeURIComponent(type)}&body=${encodeURIComponent(message)}`;
            closeFeedbackModal();
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = 'Send Message';
            }
        }
    }
    window.submitUserFeedback = submitUserFeedback;

    // ── DANGER ZONE: Permanent Account Deletion ────────────────
    function openDeleteAccountModal() {
        const modal = document.getElementById('modal-delete-account');
        const confirmCheck = document.getElementById('delete-confirm-checkbox');
        const confirmInput = document.getElementById('delete-confirm-word');
        const errEl = document.getElementById('delete-account-error');
        const reauthCard = document.getElementById('delete-reauth-section');
        const deleteBtn = document.getElementById('final-delete-btn');

        if (confirmCheck) confirmCheck.checked = false;
        if (confirmInput) confirmInput.value = '';
        if (errEl) errEl.style.display = 'none';
        if (reauthCard) reauthCard.style.display = 'none';
        if (deleteBtn) deleteBtn.disabled = true;

        if (modal) modal.style.display = 'flex';
    }
    window.openDeleteAccountModal = openDeleteAccountModal;

    function closeDeleteAccountModal() {
        const modal = document.getElementById('modal-delete-account');
        if (modal) modal.style.display = 'none';
    }
    window.closeDeleteAccountModal = closeDeleteAccountModal;

    function checkDeleteInputsReady() {
        const confirmCheck = document.getElementById('delete-confirm-checkbox');
        const confirmInput = document.getElementById('delete-confirm-word');
        const deleteBtn = document.getElementById('final-delete-btn');

        const isChecked = confirmCheck ? confirmCheck.checked : false;
        const textVal = (confirmInput ? confirmInput.value : '').trim();

        if (deleteBtn) {
            deleteBtn.disabled = !(isChecked && textVal === 'DELETE');
        }
    }
    window.checkDeleteInputsReady = checkDeleteInputsReady;

    async function executePermanentAccountDeletion() {
        const auth = window.fbAuth;
        const user = auth ? auth.currentUser : null;
        if (!user) {
            alert('No authenticated user session found.');
            return;
        }

        const uid = user.uid;
        const db = window.fbDb;
        const fm = window.fbFirestoreMethods;
        const errEl = document.getElementById('delete-account-error');
        const btn = document.getElementById('final-delete-btn');
        const statusEl = document.getElementById('delete-account-status');
        const passwordInput = document.getElementById('delete-reauth-password');

        if (errEl) errEl.style.display = 'none';

        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-1"></i> Deleting Account...';
        }
        if (statusEl) {
            statusEl.style.display = 'block';
            statusEl.textContent = 'Preparing account deletion...';
        }

        try {
            // ── Step 1: Reauthentication if needed ─────────────────
            let isGoogle = false;
            if (Array.isArray(user.providerData)) {
                user.providerData.forEach(p => { if (p.providerId === 'google.com') isGoogle = true; });
            }
            if (!isGoogle && user.providerId === 'google.com') isGoogle = true;

            const authMethods = window.fbAuthMethods;
            const providers = window.fbProviders;

            if (passwordInput && passwordInput.value && authMethods.reauthenticateWithCredential && providers.EmailAuthProvider && user.email) {
                if (statusEl) statusEl.textContent = 'Verifying credentials...';
                const cred = providers.EmailAuthProvider.credential(user.email, passwordInput.value);
                await authMethods.reauthenticateWithCredential(user, cred);
            }

            // ── Step 2: Delete User-Owned AI Chat History ──────────
            if (statusEl) statusEl.textContent = 'Deleting conversation history...';
            try {
                const chatsRef = fm.collection(db, 'users', uid, 'ai_chats');
                const chatsSnap = await fm.getDocs(chatsRef);
                for (const chatDoc of chatsSnap.docs) {
                    const msgsRef = fm.collection(db, 'users', uid, 'ai_chats', chatDoc.id, 'messages');
                    const msgsSnap = await fm.getDocs(msgsRef);
                    const msgDeletes = [];
                    msgsSnap.forEach(mDoc => msgDeletes.push(fm.deleteDoc(mDoc.ref)));
                    await Promise.all(msgDeletes);
                    await fm.deleteDoc(chatDoc.ref);
                }
            } catch (chatDelErr) {
                console.warn('[Account Deletion] AI chats delete warning:', chatDelErr);
            }

            // ── Step 3: Delete User-Owned Wishlist Items ───────────
            if (statusEl) statusEl.textContent = 'Deleting saved places...';
            try {
                const wishQuery1 = fm.query(fm.collection(db, 'wishlist'), fm.where('user_id', '==', uid));
                const wishSnap1 = await fm.getDocs(wishQuery1);
                const wishDeletes = [];
                wishSnap1.forEach(d => wishDeletes.push(fm.deleteDoc(d.ref)));

                // Also check userId field
                const wishQuery2 = fm.query(fm.collection(db, 'wishlist'), fm.where('userId', '==', uid));
                const wishSnap2 = await fm.getDocs(wishQuery2);
                wishSnap2.forEach(d => wishDeletes.push(fm.deleteDoc(d.ref)));

                await Promise.all(wishDeletes);
            } catch (wErr) {
                console.warn('[Account Deletion] Wishlist delete warning:', wErr);
            }

            // ── Step 4: Delete User-Owned Expenses ─────────────────
            if (statusEl) statusEl.textContent = 'Deleting expense records...';
            try {
                const expQuery1 = fm.query(fm.collection(db, 'expenses'), fm.where('user_id', '==', uid));
                const expSnap1 = await fm.getDocs(expQuery1);
                const expDeletes = [];
                expSnap1.forEach(d => expDeletes.push(fm.deleteDoc(d.ref)));

                const expQuery2 = fm.query(fm.collection(db, 'expenses'), fm.where('userId', '==', uid));
                const expSnap2 = await fm.getDocs(expQuery2);
                expSnap2.forEach(d => expDeletes.push(fm.deleteDoc(d.ref)));

                await Promise.all(expDeletes);
            } catch (eErr) {
                console.warn('[Account Deletion] Expenses delete warning:', eErr);
            }

            // ── Step 5: Delete User-Owned Reviews/Comments & Votes ──
            if (statusEl) statusEl.textContent = 'Deleting submitted reviews...';
            try {
                if (fm.collectionGroup) {
                    const commentsQuery = fm.query(fm.collectionGroup(db, 'comments'), fm.where('userId', '==', uid));
                    const commentsSnap = await fm.getDocs(commentsQuery);
                    const commDeletes = [];
                    commentsSnap.forEach(d => commDeletes.push(fm.deleteDoc(d.ref)));
                    await Promise.all(commDeletes);
                }
            } catch (cErr) {
                console.warn('[Account Deletion] Comments delete warning:', cErr);
            }

            // Delete rating votes from known places
            try {
                const placesList = window.cachedPlacesFromFirestore || window.allPlacesData || [];
                const voteDeletes = [];
                placesList.forEach(p => {
                    const voteRef = fm.doc(db, 'ratings', String(p.id), 'votes', uid);
                    voteDeletes.push(fm.deleteDoc(voteRef).catch(() => {}));
                });
                await Promise.all(voteDeletes);
            } catch (vErr) {
                console.warn('[Account Deletion] Votes delete warning:', vErr);
            }

            // ── Step 6: Delete User Profile Document ───────────────
            if (statusEl) statusEl.textContent = 'Deleting user profile...';
            try {
                const userDocRef = fm.doc(db, 'users', uid);
                await fm.deleteDoc(userDocRef);
            } catch (uErr) {
                console.warn('[Account Deletion] User profile doc delete warning:', uErr);
            }

            // ── Step 7: Delete Firebase Authentication Account ──────
            if (statusEl) statusEl.textContent = 'Deleting authentication record...';
            if (authMethods.deleteUser) {
                await authMethods.deleteUser(user);
            } else if (typeof user.delete === 'function') {
                await user.delete();
            }

            // ── Step 8: Clear Local App State & Redirect ────────────
            if (statusEl) statusEl.textContent = 'Account deleted successfully.';

            // Clear local storage
            try {
                localStorage.removeItem('budget_limit');
                sessionStorage.removeItem('bt_ai_active_chat_' + uid);
            } catch (e) {}

            // Reset SPA state
            window.currentUser = null;
            if (window.userWishlistPlaceIds) window.userWishlistPlaceIds.clear();
            if (typeof window.expenses !== 'undefined') window.expenses = [];

            closeDeleteAccountModal();
            alert('Your account and personal data have been permanently deleted.');

            // Switch to welcome/landing screen
            if (typeof window.switchAuthView === 'function') {
                const appView = document.getElementById('app-view');
                const authContainer = document.getElementById('auth-container');
                if (appView) appView.style.display = 'none';
                if (authContainer) authContainer.style.display = 'block';
                window.switchAuthView('welcome');
            } else {
                window.location.reload();
            }
        } catch (err) {
            console.error('[Account Deletion] Error executing deletion:', err);
            let errMsg = 'Failed to delete account. Please try again.';

            if (err.code === 'auth/requires-recent-login') {
                errMsg = 'Security verification required: Please enter your password below to confirm deletion.';
                const reauthCard = document.getElementById('delete-reauth-section');
                if (reauthCard) reauthCard.style.display = 'block';
            } else if (err.code === 'auth/wrong-password' || err.code === 'auth/invalid-credential') {
                errMsg = 'Incorrect password. Verification failed.';
            } else if (err.message) {
                errMsg = err.message.replace('Firebase: ', '');
            }

            if (errEl) {
                errEl.textContent = errMsg;
                errEl.style.display = 'block';
            }
            if (statusEl) statusEl.style.display = 'none';
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-trash-can me-1"></i> Permanently Delete My Account';
            }
        }
    }
    window.executePermanentAccountDeletion = executePermanentAccountDeletion;

    // ── Google Reauth Helper for Delete ────────────────────────
    async function reauthGoogleForDeletion() {
        const user = window.fbAuth ? window.fbAuth.currentUser : null;
        if (!user || !window.fbAuthMethods || !window.fbProviders) return;

        const errEl = document.getElementById('delete-account-error');
        if (errEl) errEl.style.display = 'none';

        try {
            const provider = new window.fbProviders.GoogleAuthProvider();
            provider.setCustomParameters({ prompt: 'select_account' });
            await window.fbAuthMethods.reauthenticateWithPopup(user, provider);
            // Reauth succeeded: execute deletion directly
            executePermanentAccountDeletion();
        } catch (e) {
            console.error('[Settings] Google reauth failed:', e);
            if (errEl) {
                errEl.textContent = 'Google verification was cancelled or failed. Please try again.';
                errEl.style.display = 'block';
            }
        }
    }
    window.reauthGoogleForDeletion = reauthGoogleForDeletion;

    // ── Toast/Alert Helper ─────────────────────────────────────
    function showSettingsAlert(type, message) {
        let alertEl = document.getElementById('settings-toast-alert');
        if (!alertEl) {
            alertEl = document.createElement('div');
            alertEl.id = 'settings-toast-alert';
            alertEl.className = 'settings-toast';
            document.body.appendChild(alertEl);
        }
        alertEl.className = `settings-toast settings-toast-${type} active`;
        alertEl.innerHTML = `<i class="fa-solid ${type === 'success' ? 'fa-circle-check text-success' : 'fa-circle-info text-primary'} me-2"></i> ${message}`;
        setTimeout(() => {
            alertEl.classList.remove('active');
        }, 3500);
    }
    window.showSettingsAlert = showSettingsAlert;

})();

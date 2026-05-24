

let expenses = [];

let monthlyIncome = 0;

const API_BASE_URL = "https://api.brandon.my/v1/api";
const PROFILE_STORAGE_KEY = "masterauth_profile_v1";
const LAST_SYNC_STORAGE_KEY = "masterauth_last_sync_v1";
const PASSWORD_KEY_COOKIE = "masterauth_password_key";
const RESERVED_SYNC_KEYS = [PROFILE_STORAGE_KEY, LAST_SYNC_STORAGE_KEY];

const snapshotFilePath = "snapshot-2026-05.json";

let currentFilter = "all";

const tableBody = document.getElementById("expenseTableBody");

const totalExpense = document.getElementById("totalExpense");
const remainingBalance = document.getElementById("remainingBalance");
const monthlyIncomeText = document.getElementById("monthlyIncome");

const expenseModal = new bootstrap.Modal(document.getElementById('expenseModal'));
const settingsModal = new bootstrap.Modal(document.getElementById('settingsModal'));
const chartModal = new bootstrap.Modal(document.getElementById('chartModal'));

let chartInstance = null;

function getAppIdentifier() {
    return "expenses-tracker";
}

function getProfile() {

    try {
        return JSON.parse(localStorage.getItem(PROFILE_STORAGE_KEY)) || null;
    } catch {
        return null;
    }
}

function setProfile(email) {

    const payload = {
        email: email,
        apps: getAppIdentifier()
    };

    localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(payload));
}

function clearProfile() {
    localStorage.removeItem(PROFILE_STORAGE_KEY);
}

function setCookie(name, value, maxAgeSeconds) {

    document.cookie = `${name}=${encodeURIComponent(value)}; path=/; max-age=${maxAgeSeconds}; SameSite=Lax`;
}

function getCookie(name) {

    const prefix = `${name}=`;

    const matched = document.cookie.split(";").map(p => p.trim()).find(p => p.startsWith(prefix));

    if (!matched) {
        return "";
    }

    return decodeURIComponent(matched.slice(prefix.length));
}

function deleteCookie(name) {
    document.cookie = `${name}=; path=/; max-age=0; SameSite=Lax`;
}

function isLoggedIn() {

    const profile = getProfile();
    const passwordKey = getCookie(PASSWORD_KEY_COOKIE);

    return Boolean(profile && profile.email && profile.apps && passwordKey);
}

function updateAuthStatusText() {

    const el = document.getElementById("authStatusText");
    const credentialFields = document.getElementById("authCredentialFields");
    const guestActions = document.getElementById("authGuestActions");
    const memberActions = document.getElementById("authMemberActions");

    if (!el) {
        return;
    }

    const profile = getProfile();

    if (isLoggedIn() && profile) {
        el.textContent = `Logged in as ${profile.email}`;

        if (credentialFields) {
            credentialFields.classList.add("d-none");
        }

        if (guestActions) {
            guestActions.classList.add("d-none");
        }

        if (memberActions) {
            memberActions.classList.remove("d-none");
        }

        return;
    }

    el.textContent = "Not logged in";

    if (credentialFields) {
        credentialFields.classList.remove("d-none");
    }

    if (guestActions) {
        guestActions.classList.remove("d-none");
    }

    if (memberActions) {
        memberActions.classList.add("d-none");
    }
}

function markLocalSyncTimestamp(isoTimestamp = new Date().toISOString()) {

    localStorage.setItem(LAST_SYNC_STORAGE_KEY, JSON.stringify({
        last_sync: isoTimestamp
    }));
}

function getLocalSyncTimestamp() {

    try {
        const parsed = JSON.parse(localStorage.getItem(LAST_SYNC_STORAGE_KEY));
        return parsed?.last_sync || null;
    } catch {
        return null;
    }
}

function buildAppDataSnapshot() {

    const result = {};

    for (let i = 0; i < localStorage.length; i += 1) {

        const key = localStorage.key(i);

        if (!key || RESERVED_SYNC_KEYS.includes(key)) {
            continue;
        }

        result[key] = localStorage.getItem(key);
    }

    return result;
}

function applyCloudSnapshot(snapshot) {

    const profileRaw = localStorage.getItem(PROFILE_STORAGE_KEY);
    const syncRaw = localStorage.getItem(LAST_SYNC_STORAGE_KEY);

    localStorage.clear();

    if (profileRaw !== null) {
        localStorage.setItem(PROFILE_STORAGE_KEY, profileRaw);
    }

    if (syncRaw !== null) {
        localStorage.setItem(LAST_SYNC_STORAGE_KEY, syncRaw);
    }

    Object.keys(snapshot || {}).forEach((key) => {
        const value = snapshot[key];

        if (typeof value === "string") {
            localStorage.setItem(key, value);
            return;
        }

        localStorage.setItem(key, JSON.stringify(value));
    });
}

function parseConfigSnapshot(rawConfig) {

    if (!rawConfig) {
        return {};
    }

    let parsed = rawConfig;

    if (typeof parsed === "string") {
        try {
            parsed = JSON.parse(parsed);
        } catch {
            return {};
        }
    }

    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return {};
    }

    return parsed;
}

function sanitizeForDebug(payload) {

    if (!payload || typeof payload !== "object") {
        return payload;
    }

    const copy = { ...payload };

    if (Object.prototype.hasOwnProperty.call(copy, "password")) {
        copy.password = "***";
    }

    if (Object.prototype.hasOwnProperty.call(copy, "password_key")) {
        copy.password_key = "***";
    }

    return copy;
}

function normalizeLoginResponse(result) {

    console.log("[CloudAuth] normalizeLoginResponse input", result);

    if (result && result.status === "success-login" && result.password_key) {
        console.log("[CloudAuth] Login response matched status format");
        return {
            ok: true,
            passwordKey: result.password_key,
            configSnapshot: {}
        };
    }

    if (Array.isArray(result) && result.length > 0 && result[0]?.password_key) {
        console.log("[CloudAuth] Login response matched array format", {
            rows: result.length,
            hasConfig: Boolean(result[0].config)
        });
        return {
            ok: true,
            passwordKey: result[0].password_key,
            configSnapshot: parseConfigSnapshot(result[0].config)
        };
    }

    return {
        ok: false,
        status: result?.status || "login-failed"
    };
}

function normalizeRemoteSyncResponse(remote) {

    console.log("[CloudAuth] normalizeRemoteSyncResponse input", remote);

    if (remote && remote.status === "data-found") {
        return {
            ok: true,
            data: remote.data,
            lastSync: remote.last_sync
        };
    }

    if (remote && Object.prototype.hasOwnProperty.call(remote, "data")) {
        return {
            ok: true,
            data: remote.data,
            lastSync: remote.last_sync || remote.updatedAt || "new-data"
        };
    }

    if (Array.isArray(remote) && remote.length > 0) {
        return {
            ok: true,
            data: parseConfigSnapshot(remote[0].config),
            lastSync: remote[0].updatedAt || "new-data"
        };
    }

    return {
        ok: false,
        status: remote?.status || "sync-failed"
    };
}

async function apiRequest(path, method, payload = {}) {

    let url = `${API_BASE_URL}/${path}`;

    const options = {
        method: method,
        headers: {}
    };

    if (method === "GET") {

        const params = new URLSearchParams();

        Object.keys(payload).forEach((key) => {

            const value = payload[key];

            if (value === undefined || value === null) {
                return;
            }

            if (typeof value === "object") {
                params.set(key, JSON.stringify(value));
                return;
            }

            params.set(key, String(value));
        });

        const query = params.toString();

        if (query) {
            url = `${url}?${query}`;
        }

    } else {

        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(payload);
    }

    console.log("[CloudAuth] API request", {
        path: path,
        method: method,
        url: url,
        payload: sanitizeForDebug(payload)
    });

    const response = await fetch(url, options);

    if (!response.ok) {
        console.log("[CloudAuth] API non-200 response", {
            path: path,
            method: method,
            status: response.status
        });
        throw new Error(`HTTP ${response.status}`);
    }

    const data = await response.json();

    console.log("[CloudAuth] API response", {
        path: path,
        method: method,
        data: data
    });

    return data;
}

function logoutCloudAccount(showFeedback = true) {

    deleteCookie(PASSWORD_KEY_COOKIE);
    clearProfile();
    updateAuthStatusText();

    if (showFeedback) {
        showToast("Logged out");
    }
}

async function uploadLocalDataToCloud(showFeedback = false) {

    const profile = getProfile();
    const passwordKey = getCookie(PASSWORD_KEY_COOKIE);

    if (!profile || !passwordKey) {
        return false;
    }

    const payload = {
        email: profile.email,
        apps: profile.apps,
        password_key: passwordKey,
        app_data: buildAppDataSnapshot()
    };

    const result = await apiRequest("config/app", "POST", payload);

    if (result.status !== "data-updated") {
        if (result.status === "invalid-password_key") {
            logoutCloudAccount(false);
        }

        throw new Error(result.status || "data-update-failed");
    }

    const now = new Date().toISOString();
    markLocalSyncTimestamp(now);

    if (showFeedback) {
        showToast("Cloud sync complete");
    }

    return true;
}

async function syncCloudData(showFeedback = false, forcePullFromCloud = false) {

    const profile = getProfile();
    const passwordKey = getCookie(PASSWORD_KEY_COOKIE);

    if (!profile || !passwordKey) {
        if (showFeedback) {
            showToast("Please login first");
        }
        return;
    }

    const getPayload = {
        email: profile.email,
        apps: profile.apps,
        password_key: passwordKey
    };

    const remote = await apiRequest("config/app", "GET", getPayload);

    if (remote.status === "invalid-password_key") {
        logoutCloudAccount(false);
        throw new Error("Session expired. Please login again.");
    }

    const normalized = normalizeRemoteSyncResponse(remote);

    console.log("[CloudAuth] syncCloudData normalized", normalized);

    if (!normalized.ok) {
        throw new Error(normalized.status || "sync-failed");
    }

    let remoteData = normalized.data;

    if (typeof remoteData === "string") {
        try {
            remoteData = JSON.parse(remoteData);
        } catch {
            remoteData = {};
        }
    }

    if (!remoteData || typeof remoteData !== "object") {
        remoteData = {};
    }

    const serverHasData = Object.keys(remoteData).length > 0;
    const serverStamp = normalized.lastSync;

    const localStamp = getLocalSyncTimestamp();
    const localStampMs = localStamp ? Date.parse(localStamp) : 0;
    const serverStampMs = serverStamp && serverStamp !== "new-data" ? Date.parse(serverStamp) : 0;

    const localHasData = expenses.length > 0 || monthlyIncome > 0;
    const shouldPullFromCloud = forcePullFromCloud || (
        serverHasData && (
            !localHasData || !localStampMs || (serverStampMs && serverStampMs > localStampMs)
        )
    );

    console.log("[CloudAuth] sync decision", {
        serverHasData: serverHasData,
        serverStamp: serverStamp,
        localStamp: localStamp,
        localHasData: localHasData,
        shouldPullFromCloud: shouldPullFromCloud
    });

    if (shouldPullFromCloud) {

        applyCloudSnapshot(remoteData);
        loadFromLocalStorage();
        markLocalSyncTimestamp(serverStamp && serverStamp !== "new-data" ? serverStamp : new Date().toISOString());
        renderExpenses();

        if (showFeedback) {
            showToast("Data restored from cloud");
        }

        return;
    }

    if (localHasData || serverStamp === "new-data") {
        await uploadLocalDataToCloud(showFeedback);
    }
}

function triggerCloudSync() {

    if (!isLoggedIn()) {
        return;
    }

    syncCloudData(false).catch(() => {
        // Keep local UX uninterrupted for background sync failures.
    });
}

function uploadExpenseChangeInBackground() {

    if (!isLoggedIn()) {
        return;
    }

    showToast("Cloud update started");

    uploadLocalDataToCloud(false).catch(() => {
        showToast("Cloud update failed");
    });
}

function uploadSettingsChangeInBackground() {
    uploadExpenseChangeInBackground();
}

function getAuthFormValues() {

    const email = (document.getElementById("authEmail")?.value || "").trim();
    const password = (document.getElementById("authPassword")?.value || "").trim();

    return {
        email: email,
        password: password,
        apps: getAppIdentifier()
    };
}

async function handleRegister() {

    const payload = getAuthFormValues();

    if (!payload.email || !payload.password) {
        showToast("Enter email and password");
        return;
    }

    const result = await apiRequest("auth/register", "POST", payload);

    if (result.status !== "success-registered" || !result.password_key) {
        throw new Error(result.status || "registration-failed");
    }

    setProfile(payload.email);
    setCookie(PASSWORD_KEY_COOKIE, result.password_key, 60 * 60 * 24 * 30);
    updateAuthStatusText();

    await uploadLocalDataToCloud(false);
    showToast("Registration successful");
}

async function handleLogin() {

    const payload = getAuthFormValues();

    if (!payload.email || !payload.password) {
        showToast("Enter email and password");
        return;
    }

    const result = await apiRequest("auth/login", "POST", payload);
    const normalized = normalizeLoginResponse(result);

    console.log("[CloudAuth] handleLogin normalized", {
        ok: normalized.ok,
        status: normalized.status,
        hasConfigSnapshot: Boolean(normalized.configSnapshot && Object.keys(normalized.configSnapshot).length > 0)
    });

    if (!normalized.ok) {
        throw new Error(normalized.status || "login-failed");
    }

    setProfile(payload.email);
    setCookie(PASSWORD_KEY_COOKIE, normalized.passwordKey, 60 * 60 * 24 * 30);
    updateAuthStatusText();

    if (Object.keys(normalized.configSnapshot).length > 0) {
        applyCloudSnapshot(normalized.configSnapshot);
        loadFromLocalStorage();
        renderExpenses();
        markLocalSyncTimestamp(new Date().toISOString());

        if (showToast) {
            showToast("Login successful");
        }

        return;
    }

    try {
        await syncCloudData(true, true);
    } catch (error) {
        console.log("[CloudAuth] handleLogin sync error", {
            message: error?.message,
            error: error
        });
        throw new Error("sync-failed");
    }
}

async function runAuthAction(actionFn) {

    try {
        await actionFn();
    } catch (error) {
        console.log("[CloudAuth] runAuthAction caught error", {
            message: error?.message,
            error: error
        });
        Swal.fire({
            icon: "error",
            title: "Cloud account error",
            text: error.message || "Request failed"
        });
    }
}

function initializeAuthControls() {

    const profile = getProfile();

    if (profile?.email) {
        const emailInput = document.getElementById("authEmail");
        if (emailInput) {
            emailInput.value = profile.email;
        }
    }

    updateAuthStatusText();

    const registerBtn = document.getElementById("registerBtn");
    const loginBtn = document.getElementById("loginBtn");
    const syncNowBtn = document.getElementById("syncNowBtn");
    const logoutBtn = document.getElementById("logoutBtn");

    if (registerBtn) {
        registerBtn.addEventListener("click", () => runAuthAction(handleRegister));
    }

    if (loginBtn) {
        loginBtn.addEventListener("click", () => runAuthAction(handleLogin));
    }

    if (syncNowBtn) {
        syncNowBtn.addEventListener("click", () => runAuthAction(() => syncCloudData(true)));
    }

    if (logoutBtn) {
        logoutBtn.addEventListener("click", () => logoutCloudAccount(true));
    }
}

function loadFromLocalStorage() {

    expenses = JSON.parse(localStorage.getItem("expenses")) || [];

    monthlyIncome = parseFloat(localStorage.getItem("monthlyIncome")) || 0;
}

async function loadSnapshotData() {

    const response = await fetch(snapshotFilePath, { cache: "no-store" });

    if (!response.ok) {
        throw new Error("Unable to load snapshot JSON");
    }

    const data = await response.json();

    expenses = Array.isArray(data.expenses) ? data.expenses : [];

    monthlyIncome = parseFloat(data.monthlyIncome) || 0;

    saveExpenses();
    saveIncome();
}

async function initializeData() {

    const hasStoredExpenses = localStorage.getItem("expenses") !== null;
    const hasStoredIncome = localStorage.getItem("monthlyIncome") !== null;

    if (hasStoredExpenses || hasStoredIncome) {
        loadFromLocalStorage();
        return;
    }

    try {
        await loadSnapshotData();
    } catch (error) {
        loadFromLocalStorage();
    }
}

function saveExpenses() {
    localStorage.setItem("expenses", JSON.stringify(expenses));
    markLocalSyncTimestamp();
}

function saveIncome() {
    localStorage.setItem("monthlyIncome", monthlyIncome);
    markLocalSyncTimestamp();
}

function downloadBackup() {

    const backupData = {
        monthlyIncome: monthlyIncome,
        expenses: expenses,
        exportDate: new Date().toISOString()
    };

    const dataStr = JSON.stringify(backupData, null, 2);

    const blob = new Blob(
        [dataStr],
        { type: "application/json" }
    );

    const url = window.URL.createObjectURL(blob);

    const link = document.createElement("a");

    const today = new Date().toISOString().split("T")[0];

    link.href = url;
    link.download = `expense-backup-${today}.json`;

    document.body.appendChild(link);

    link.click();

    setTimeout(() => {

        document.body.removeChild(link);

        window.URL.revokeObjectURL(url);

    }, 100);

    showToast("Backup downloaded");
}

function restoreBackup(file) {

    const reader = new FileReader();

    reader.onload = function(event) {

        try {

            const data = JSON.parse(event.target.result);

            if (!data.expenses || !Array.isArray(data.expenses)) {

                Swal.fire({
                    icon: 'error',
                    title: 'Invalid Backup File',
                    text: 'Unable to restore backup.'
                });

                return;
            }

            Swal.fire({
                title: 'Restore backup?',
                text: 'Current data will be replaced.',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Restore'
            }).then((result) => {

                if (result.isConfirmed) {

                    expenses = data.expenses || [];

                    monthlyIncome = parseFloat(data.monthlyIncome) || 0;

                    saveExpenses();
                    saveIncome();

                    renderExpenses();

                    triggerCloudSync();

                    document.getElementById("incomeInput").value = monthlyIncome;

                    showToast("Backup restored");

                    settingsModal.hide();
                }

            });

        } catch (error) {

            Swal.fire({
                icon: 'error',
                title: 'Restore Failed',
                text: 'Invalid JSON backup file.'
            });
        }
    };

    reader.readAsText(file);
}

function showToast(message) {

    document.getElementById("toastMessage").innerText = message;

    const toast = new bootstrap.Toast(document.getElementById('liveToast'));

    toast.show();
}

function formatMonthYear(dateString) {

    const date = new Date(dateString);

    return date.toLocaleString('default', {
        month: 'long',
        year: 'numeric'
    });
}

function populateMonthFilter() {

    const monthFilter = document.getElementById("monthFilter");

    const uniqueMonths = [...new Set(
        expenses.map(expense => formatMonthYear(expense.date))
    )];

    uniqueMonths.sort((a, b) => new Date(b) - new Date(a));

    monthFilter.innerHTML = `
        <option value="all">All Months</option>
    `;

    uniqueMonths.forEach(month => {

        monthFilter.innerHTML += `
            <option value="${month}">
                ${month}
            </option>
        `;
    });

    monthFilter.value = currentFilter;
}

function getFilteredExpenses() {

    if (currentFilter === "all") {
        return expenses;
    }

    return expenses.filter(expense => {
        return formatMonthYear(expense.date) === currentFilter;
    });
}

function refreshDropdownOptions() {

    const titleList = [...new Set(expenses.map(e => e.title))];
    const categoryList = [...new Set(expenses.map(e => e.category))];

    $('#title').empty();
    $('#category').empty();

    titleList.forEach(item => {
        $('#title').append(new Option(item, item));
    });

    categoryList.forEach(item => {
        $('#category').append(new Option(item, item));
    });

    $('#title').select2({
        tags: true,
        dropdownParent: $('#expenseModal'),
        placeholder: "Select or create title",
        width: '100%'
    });

    $('#category').select2({
        tags: true,
        dropdownParent: $('#expenseModal'),
        placeholder: "Select or create category",
        width: '100%'
    });
}

function renderSummaryCards() {

    let total = 0;

    const filteredExpenses = getFilteredExpenses();

    filteredExpenses.forEach(expense => {
        total += parseFloat(expense.amount);
    });

    const balance = monthlyIncome - total;

    totalExpense.innerText = "$" + total.toFixed(2);
    remainingBalance.innerText = "$" + balance.toFixed(2);
    monthlyIncomeText.innerText = "$" + monthlyIncome.toFixed(2);
}

function renderExpenses() {

    tableBody.innerHTML = "";

    populateMonthFilter();

    const filteredExpenses = getFilteredExpenses();

    if (filteredExpenses.length === 0) {

        tableBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-muted py-4">
                    No expenses found
                </td>
            </tr>
        `;
    }

    filteredExpenses.forEach((expense) => {

        const originalIndex = expenses.indexOf(expense);

        tableBody.innerHTML += `
            <tr>
                <td>${expense.date}</td>
                <td>${expense.title}</td>
                <td>${expense.category}</td>
                <td class="amount">$${parseFloat(expense.amount).toFixed(2)}</td>

                <td>

                    <button
                            class="btn btn-sm btn-warning me-1"
                            onclick="editExpense(${originalIndex})">

                        <i class="bi bi-pencil"></i>

                    </button>

                    <button
                            class="btn btn-sm btn-danger"
                            onclick="deleteExpense(${originalIndex})">

                        <i class="bi bi-trash"></i>

                    </button>

                </td>
            </tr>
        `;
    });

    renderSummaryCards();
}

function openAddModal() {

    refreshDropdownOptions();

    document.getElementById("modalTitle").innerText = "Add Expense";

    document.getElementById("expenseForm").reset();

    document.getElementById("expenseId").value = "";

    $('#title').val(null).trigger('change');
    $('#category').val(null).trigger('change');

    document.getElementById("date").valueAsDate = new Date();

    expenseModal.show();
}

function editExpense(index) {

    refreshDropdownOptions();

    const expense = expenses[index];

    document.getElementById("modalTitle").innerText = "Edit Expense";

    document.getElementById("expenseId").value = index;

    if ($('#title option[value="' + expense.title + '"]').length === 0) {
        $('#title').append(new Option(expense.title, expense.title));
    }

    if ($('#category option[value="' + expense.category + '"]').length === 0) {
        $('#category').append(new Option(expense.category, expense.category));
    }

    $('#title').val(expense.title).trigger('change');
    $('#category').val(expense.category).trigger('change');

    document.getElementById("amount").value = expense.amount;
    document.getElementById("date").value = expense.date;

    expenseModal.show();
}

function deleteExpense(index) {

    Swal.fire({
        title: 'Delete expense?',
        text: 'This action cannot be undone.',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#d33',
        confirmButtonText: 'Delete'
    }).then((result) => {

        if (result.isConfirmed) {

            expenses.splice(index, 1);

            saveExpenses();

            renderExpenses();

            triggerCloudSync();

            showToast("Expense deleted");
        }

    });
}

function openSettingsModal() {

    document.getElementById("incomeInput").value = monthlyIncome;

    settingsModal.show();
}

function openChartModal() {

    chartModal.show();

    const categoryTotals = {};

    const filteredExpenses = getFilteredExpenses();

    filteredExpenses.forEach(expense => {

        if (!categoryTotals[expense.category]) {
            categoryTotals[expense.category] = 0;
        }

        categoryTotals[expense.category] += parseFloat(expense.amount);
    });

    const labels = Object.keys(categoryTotals);
    const data = Object.values(categoryTotals);

    const summaryList = document.getElementById("categorySummaryList");

    if (labels.length === 0) {

        summaryList.innerHTML = `
            <div class="text-center text-muted py-4">
                No data available
            </div>
        `;

        return;
    }

    summaryList.innerHTML = `
        <div class="table-responsive">

            <table class="table">

                <thead>
                <tr>
                    <th>Category</th>
                    <th>Total</th>
                </tr>
                </thead>

                <tbody>

                ${labels.map((label, i) => `
                    <tr>
                        <td>${label}</td>
                        <td>$${data[i].toFixed(2)}</td>
                    </tr>
                `).join("")}

                </tbody>

            </table>

        </div>
    `;

    const ctx = document.getElementById("expenseChart");

    if (chartInstance) {
        chartInstance.destroy();
    }

    chartInstance = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: labels,
            datasets: [{
                data: data
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
}

document.getElementById("expenseForm").addEventListener("submit", function(e) {

    e.preventDefault();

    const id = document.getElementById("expenseId").value;

    const expense = {
        title: $('#title').val(),
        category: $('#category').val(),
        amount: document.getElementById("amount").value,
        date: document.getElementById("date").value
    };

    if (id === "") {

        expenses.push(expense);

        showToast("Expense added");

    } else {

        expenses[id] = expense;

        showToast("Expense updated");
    }

    saveExpenses();

    renderExpenses();

    uploadExpenseChangeInBackground();

    expenseModal.hide();
});

document.getElementById("settingsForm").addEventListener("submit", function(e) {

    e.preventDefault();

    monthlyIncome = parseFloat(document.getElementById("incomeInput").value) || 0;

    saveIncome();

    renderSummaryCards();

    uploadSettingsChangeInBackground();

    settingsModal.hide();

    showToast("Settings saved");
});

document.getElementById("monthFilter").addEventListener("change", function() {

    currentFilter = this.value;

    renderExpenses();
});

document.getElementById("restoreFile").addEventListener("change", function(e) {

    const file = e.target.files[0];

    if (!file) {
        return;
    }

    restoreBackup(file);

    this.value = "";
});

initializeData().then(async () => {

    initializeAuthControls();
    renderExpenses();

    if (isLoggedIn()) {
        try {
            await syncCloudData(false);
            updateAuthStatusText();
        } catch {
            // Keep app usable with local data if cloud sync fails on startup.
        }
    }
});
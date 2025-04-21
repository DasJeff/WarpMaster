// Global variables
let currentPlayerUuid = null;
let apiKey = '';

// DOM elements
const playerList = document.getElementById('playerList');
const playerSearch = document.getElementById('playerSearch');
const welcomeScreen = document.getElementById('welcomeScreen');
const playerDetails = document.getElementById('playerDetails');
const playerHead = document.getElementById('playerHead');
const playerName = document.getElementById('playerName');
const playerUuid = document.getElementById('playerUuid');
const warpCount = document.getElementById('warpCount');
const warpLimit = document.getElementById('warpLimit');
const warpsList = document.getElementById('warpsList');
const setLimitBtn = document.getElementById('setLimitBtn');
const limitModal = document.getElementById('limitModal');
const currentLimit = document.getElementById('currentLimit');
const newLimit = document.getElementById('newLimit');
const saveLimitBtn = document.getElementById('saveLimitBtn');
const cancelLimitBtn = document.getElementById('cancelLimitBtn');
const deleteWarpModal = document.getElementById('deleteWarpModal');
const deleteWarpName = document.getElementById('deleteWarpName');
const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
const cancelDeleteBtn = document.getElementById('cancelDeleteBtn');

// Event listeners
document.addEventListener('DOMContentLoaded', init);
playerSearch.addEventListener('input', filterPlayers);
setLimitBtn.addEventListener('click', openLimitModal);
saveLimitBtn.addEventListener('click', saveNewLimit);
cancelLimitBtn.addEventListener('click', closeLimitModal);
confirmDeleteBtn.addEventListener('click', deleteWarp);
cancelDeleteBtn.addEventListener('click', closeDeleteWarpModal);

// Close buttons for modals
document.querySelectorAll('.modal .close').forEach(closeBtn => {
    closeBtn.addEventListener('click', () => {
        document.querySelectorAll('.modal').forEach(modal => {
            modal.classList.remove('show');
        });
    });
});

// Initialization
async function init() {
    // Create toast container
    createToastContainer();

    // Check if an API key is present in the session storage
    apiKey = sessionStorage.getItem('apiKey');

    if (!apiKey) {
        // If no API key is present, redirect to the login page
        window.location.href = '/login.html';
        return;
    }

    // Load player list
    try {
        await loadPlayers();
    } catch (error) {
        // If authentication error, redirect to the login page
        if (error.message === 'Invalid API key') {
            sessionStorage.removeItem('apiKey');
            window.location.href = '/login.html';
            return;
        }
    }

    // Add logout button
    addLogoutButton();
}

// API functions
async function fetchApi(endpoint, options = {}) {
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': apiKey
        }
    };

    const mergedOptions = { ...defaultOptions, ...options };

    try {
        const response = await fetch(endpoint, mergedOptions);

        if (!response.ok) {
            // Try to parse error json
            let errorData = { error: `HTTP error! status: ${response.status}` };
            try {
                 errorData = await response.json();
            } catch (e) {
                // Ignore json parsing error if response is not json
            }
            throw new Error(errorData.error || 'Ein Fehler ist aufgetreten');
        }

        // Handle 204 No Content
        if (response.status === 204) {
             return null;
        }

        // For other responses, parse JSON
        return await response.json();

    } catch (error) {
        showToast('Fehler', error.message, 'error');
        console.error('API-Fehler:', error);
        throw error;
    }
}

async function loadPlayers() {
    try {
        playerList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Lade Spieler...</div>';

        const players = await fetchApi('/api/players');

        if (players.length === 0) {
            playerList.innerHTML = '<div class="no-data"><i class="fas fa-users-slash"></i><p>Keine Spieler mit Warps gefunden</p></div>';
            return;
        }

        playerList.innerHTML = '';

        players.forEach(player => {
            const playerItem = document.createElement('div');
            playerItem.className = 'player-item';
            playerItem.dataset.uuid = player.uuid;
            playerItem.innerHTML = `
                <div class="player-item-avatar">
                    <img src="https://api.mcheads.org/head/${player.name}/40" alt="${player.name}">
                </div>
                <div class="player-item-info">
                    <h3>${player.name}</h3>
                    <p>${formatUuid(player.uuid)}</p>
                </div>
            `;

            playerItem.addEventListener('click', () => loadPlayerDetails(player.uuid));

            playerList.appendChild(playerItem);
        });
    } catch (error) {
        playerList.innerHTML = '<div class="no-data"><i class="fas fa-exclamation-triangle"></i><p>Fehler beim Laden der Spieler</p></div>';
    }
}

async function loadPlayerDetails(uuid) {
    try {
        // Mark the active player
        document.querySelectorAll('.player-item').forEach(item => {
            item.classList.remove('active');
            if (item.dataset.uuid === uuid) {
                item.classList.add('active');
            }
        });

        currentPlayerUuid = uuid;

        // Show player details
        welcomeScreen.style.display = 'none';
        playerDetails.style.display = 'flex';

        // Reset warps list
        warpsList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Lade Warps...</div>';

        // Load player data
        const playerData = await fetchApi(`/api/player/${uuid}`);

        // Update UI
        playerHead.src = `https://api.mcheads.org/head/${playerData.name}/80`;
        playerName.textContent = playerData.name;
        playerUuid.textContent = formatUuid(uuid);
        warpCount.textContent = playerData.warpCount;
        warpLimit.textContent = playerData.warpLimit;
        currentLimit.textContent = playerData.warpLimit;
        newLimit.value = playerData.warpLimit;

        // Load warps
        const warps = await fetchApi(`/api/warps/${uuid}`);

        if (warps.length === 0) {
            warpsList.innerHTML = '<div class="no-data"><i class="fas fa-map-marked-alt"></i><p>Keine Warps gefunden</p></div>';
            return;
        }

        warpsList.innerHTML = '';

        warps.forEach(warp => {
            const warpCard = document.createElement('div');
            warpCard.className = 'warp-card';
            warpCard.innerHTML = `
                <div class="warp-header">
                    <h4>${warp.name}</h4>
                    <div class="actions">
                        <button class="delete-warp" title="Warp löschen">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    </div>
                </div>
                <div class="warp-body">
                    <div class="warp-info">
                        <div class="warp-info-item">
                            <i class="fas fa-globe"></i>
                            <span>${warp.worldName}</span>
                        </div>
                        <div class="warp-info-item">
                            <i class="fas fa-map-marker-alt"></i>
                            <span>X: ${Math.round(warp.x)}, Y: ${Math.round(warp.y)}, Z: ${Math.round(warp.z)}</span>
                        </div>
                        <div class="warp-info-item">
                            <i class="fas fa-compass"></i>
                            <span>Yaw: ${Math.round(warp.yaw)}, Pitch: ${Math.round(warp.pitch)}</span>
                        </div>
                        <div class="warp-info-item">
                            <i class="fas fa-clock"></i>
                            <span>Erstellt: ${formatDate(warp.createdAt)}</span>
                        </div>
                    </div>
                </div>
            `;

            // Event listener for delete button
            warpCard.querySelector('.delete-warp').addEventListener('click', () => {
                openDeleteWarpModal(warp.name);
            });

            warpsList.appendChild(warpCard);
        });
    } catch (error) {
        playerDetails.style.display = 'none';
        welcomeScreen.style.display = 'flex';
    }
}

async function saveNewLimit() {
    const limit = parseInt(newLimit.value);

    if (isNaN(limit) || limit < 0) {
        showToast('Fehler', 'Bitte geben Sie ein gültiges Limit ein (≥ 0)', 'error');
        return;
    }

    try {
        await fetchApi(`/api/player/${currentPlayerUuid}/limit`, {
            method: 'PUT',
            body: JSON.stringify({ limit })
        });

        // Update UI
        warpLimit.textContent = limit;
        currentLimit.textContent = limit;

        closeLimitModal();
        showToast('Erfolg', 'Warp-Limit wurde aktualisiert', 'success');
    } catch (error) {
        // Error is handled in fetchApi
    }
}

async function deleteWarp() {
    const warpName = deleteWarpName.textContent;

    try {
        await fetchApi(`/api/warp/${currentPlayerUuid}/${warpName}`, {
            method: 'DELETE'
        });

        closeDeleteWarpModal();
        showToast('Erfolg', `Warp "${warpName}" wurde gelöscht`, 'success');

        // Reload player details
        await loadPlayerDetails(currentPlayerUuid);
    } catch (error) {
        // Error is handled in fetchApi
    }
}

// UI functions
function filterPlayers() {
    const searchTerm = playerSearch.value.toLowerCase();
    const playerItems = document.querySelectorAll('.player-item');

    playerItems.forEach(item => {
        const playerNameText = item.querySelector('h3').textContent.toLowerCase();
        const playerUuidText = item.querySelector('p').textContent.toLowerCase();

        if (playerNameText.includes(searchTerm) || playerUuidText.includes(searchTerm)) {
            item.style.display = 'flex';
        } else {
            item.style.display = 'none';
        }
    });
}

function openLimitModal() {
    limitModal.classList.add('show');
}

function closeLimitModal() {
    limitModal.classList.remove('show');
}

function openDeleteWarpModal(warpName) {
    deleteWarpName.textContent = warpName;
    deleteWarpModal.classList.add('show');
}

function closeDeleteWarpModal() {
    deleteWarpModal.classList.remove('show');
}

// Helper functions
function formatUuid(uuid) {
    return uuid.substring(0, 8) + '...';
}

function formatDate(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleDateString('de-DE') + ' ' + date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
}

function createToastContainer() {
    const container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
}

// Logout function
function addLogoutButton() {
    // Check if the logout button already exists
    if (document.getElementById('logoutButton')) {
        return;
    }

    // Create logout button
    const logoutButton = document.createElement('button');
    logoutButton.id = 'logoutButton';
    logoutButton.className = 'logout-button';
    logoutButton.innerHTML = '<i class="fas fa-sign-out-alt"></i> Abmelden';

    // Event listener for logout button
    logoutButton.addEventListener('click', () => {
        // Remove API key from session storage
        sessionStorage.removeItem('apiKey');

        // Redirect to the login page
        window.location.href = '/login.html';
    });

    // Add logout button to the header
    const header = document.querySelector('header');
    header.appendChild(logoutButton);
}

function showToast(title, message, type = 'info') {
    const container = document.querySelector('.toast-container');

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    let icon;
    switch (type) {
        case 'success':
            icon = 'fa-check-circle';
            break;
        case 'error':
            icon = 'fa-exclamation-circle';
            break;
        default:
            icon = 'fa-info-circle';
    }

    toast.innerHTML = `
        <i class="fas ${icon}"></i>
        <div class="toast-content">
            <div class="toast-title">${title}</div>
            <div class="toast-message">${message}</div>
        </div>
        <span class="close">&times;</span>
    `;

    container.appendChild(toast);

    // Close toast after 5 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, 5000);

    // Close button
    toast.querySelector('.close').addEventListener('click', () => {
        toast.style.opacity = '0';
        setTimeout(() => {
            toast.remove();
        }, 300);
    });
}

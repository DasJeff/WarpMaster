// DOM Elements
const apiKeyInput = document.getElementById('apiKey');
const togglePasswordButton = document.getElementById('togglePassword');
const loginButton = document.getElementById('loginButton');
const errorMessage = document.getElementById('errorMessage');

// Event Listeners
document.addEventListener('DOMContentLoaded', init);
togglePasswordButton.addEventListener('click', togglePasswordVisibility);
loginButton.addEventListener('click', attemptLogin);
apiKeyInput.addEventListener('keypress', function(event) {
    if (event.key === 'Enter') {
        attemptLogin();
    }
});

// Initialization
function init() {
    // Check if already authenticated
    if (sessionStorage.getItem('apiKey')) {
        redirectToDashboard();
    }
    
    // Focus on the API key input
    apiKeyInput.focus();
}

// Toggle password visibility
function togglePasswordVisibility() {
    const type = apiKeyInput.getAttribute('type') === 'password' ? 'text' : 'password';
    apiKeyInput.setAttribute('type', type);
    
    // Toggle icon
    const icon = togglePasswordButton.querySelector('i');
    icon.classList.toggle('fa-eye');
    icon.classList.toggle('fa-eye-slash');
}

// Login function
async function attemptLogin() {
    const apiKey = apiKeyInput.value.trim();
    
    if (!apiKey) {
        showError('Bitte geben Sie einen API-Schlüssel ein');
        return;
    }
    
    try {
        // Test the API key by making a request to the API
        const response = await fetch('/api/players', {
            headers: {
                'X-API-Key': apiKey
            }
        });
        
        if (response.ok) {
            // Store the API key in session storage
            sessionStorage.setItem('apiKey', apiKey);
            
            // Redirect to the dashboard
            redirectToDashboard();
        } else {
            const data = await response.json();
            showError(data.error || 'Ungültiger API-Schlüssel');
        }
    } catch (error) {
        showError('Verbindungsfehler. Bitte versuchen Sie es später erneut.');
        console.error('Login error:', error);
    }
}

// Show error message
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.classList.add('show');
    
    // Shake animation for the input
    apiKeyInput.classList.add('shake');
    setTimeout(() => {
        apiKeyInput.classList.remove('shake');
    }, 500);
}

// Redirect to dashboard
function redirectToDashboard() {
    window.location.href = '/index.html';
}

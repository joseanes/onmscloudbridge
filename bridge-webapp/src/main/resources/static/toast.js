/**
 * Toast notification system for OpenNMS Cloud Bridge
 * This provides a simple notification mechanism for the application
 */

// Create a container for toast messages if it doesn't exist
function createToastContainer() {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }
    return container;
}

/**
 * Show a toast notification message
 * @param {string} message - The message to display
 * @param {string} type - The type of toast (success, error, warning, info)
 * @param {number} duration - How long to display the toast in ms (default: 3000ms)
 */
function showToast(message, type = 'info', duration = 3000) {
    const container = createToastContainer();
    
    // Create toast element
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <div class="toast-content">
            <span class="toast-message">${message}</span>
        </div>
    `;
    
    // Add to container
    container.appendChild(toast);
    
    // Trigger animation
    setTimeout(() => {
        toast.classList.add('show');
    }, 10);
    
    // Auto remove after duration
    setTimeout(() => {
        toast.classList.remove('show');
        toast.classList.add('hide');
        
        // Remove from DOM after animation
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, duration);
}

// Add toast styles to page
function addToastStyles() {
    const style = document.createElement('style');
    style.textContent = `
        #toast-container {
            position: fixed;
            bottom: 20px;
            right: 20px;
            z-index: 1050;
        }
        
        .toast {
            min-width: 250px;
            margin-top: 10px;
            padding: 12px 16px;
            border-radius: 4px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.2);
            background-color: #333;
            color: white;
            font-size: 14px;
            opacity: 0;
            transform: translateY(20px);
            transition: all 0.3s ease-in-out;
            overflow: hidden;
        }
        
        .toast.show {
            opacity: 1;
            transform: translateY(0);
        }
        
        .toast.hide {
            opacity: 0;
            transform: translateY(-20px);
        }
        
        .toast-success {
            background-color: #4e9a06;
            color: white;
        }
        
        .toast-error {
            background-color: #cc0000;
            color: white;
        }
        
        .toast-warning {
            background-color: #f57900;
            color: white;
        }
        
        .toast-info {
            background-color: #3465a4;
            color: white;
        }
    `;
    document.head.appendChild(style);
}

// Initialize toast system on page load
document.addEventListener('DOMContentLoaded', () => {
    addToastStyles();
    createToastContainer();
    
    // Make showToast globally available
    window.showToast = showToast;
});
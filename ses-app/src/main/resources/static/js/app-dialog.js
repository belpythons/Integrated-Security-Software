/**
 * AppDialog — Lightweight Vanilla JS Dialog & Toast Helper for SES
 * Built on native Bootstrap 5 Modal & Toast. Zero external dependencies.
 */
(function(window) {
    'use strict';

    const AppDialog = {
        /**
         * Show a sleek confirmation dialog.
         * @param {Object} options
         * @param {string} [options.title] - Dialog header title (default: 'Konfirmasi')
         * @param {string} [options.message] - Dialog description / question
         * @param {string} [options.icon] - FontAwesome icon class (e.g., 'fa-triangle-exclamation text-warning')
         * @param {string} [options.confirmText] - Label for confirm button (default: 'Ya, Lanjutkan')
         * @param {string} [options.cancelText] - Label for cancel button (default: 'Batal')
         * @param {string} [options.confirmBtnClass] - Button variant (default: 'btn-primary')
         * @param {Function} [options.onConfirm] - Callback when confirmed
         * @param {Function} [options.onCancel] - Callback when cancelled/closed
         */
        confirm: function(options) {
            options = options || {};
            const title = options.title || 'Konfirmasi';
            const message = options.message || 'Apakah Anda yakin ingin melanjutkan tindakan ini?';
            const icon = options.icon || 'bi bi-question-circle-fill text-primary';
            const confirmText = options.confirmText || 'Ya, Lanjutkan';
            const cancelText = options.cancelText || 'Batal';
            const confirmBtnClass = options.confirmBtnClass || 'btn-primary';

            let modalEl = document.getElementById('appGlobalConfirmModal');
            if (!modalEl) {
                modalEl = document.createElement('div');
                modalEl.id = 'appGlobalConfirmModal';
                modalEl.className = 'modal fade';
                modalEl.tabIndex = -1;
                modalEl.setAttribute('aria-hidden', 'true');
                modalEl.innerHTML = `
                    <div class="modal-dialog modal-dialog-centered modal-sm" style="max-width: 420px;">
                        <div class="modal-content shadow-lg border-0" style="border-radius: var(--radius, 1rem); overflow: hidden;">
                            <div class="modal-body p-4 text-center">
                                <div id="appDialogIconContainer" class="icon-badge icon-badge-primary mx-auto mb-3" style="width: 56px; height: 56px; font-size: 1.5rem; display: flex; align-items: center; justify-content: center; border-radius: 50%;">
                                    <i id="appDialogIcon" class="${icon}"></i>
                                </div>
                                <h5 class="fw-bold mb-2" id="appDialogTitle" style="color: var(--navy, #001e3c);">${title}</h5>
                                <p class="text-muted small mb-4" id="appDialogMessage">${message}</p>
                                <div class="d-flex gap-2 justify-content-center">
                                    <button type="button" class="btn btn-outline-secondary btn-sm flex-fill" data-bs-dismiss="modal" id="appDialogCancelBtn">${cancelText}</button>
                                    <button type="button" class="btn ${confirmBtnClass} btn-sm flex-fill shadow-sm" id="appDialogConfirmBtn">${confirmText}</button>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
                document.body.appendChild(modalEl);
            } else {
                document.getElementById('appDialogTitle').textContent = title;
                document.getElementById('appDialogMessage').textContent = message;
                const iconEl = document.getElementById('appDialogIcon');
                iconEl.className = icon;
                const confirmBtn = document.getElementById('appDialogConfirmBtn');
                confirmBtn.className = 'btn ' + confirmBtnClass + ' btn-sm flex-fill shadow-sm';
                confirmBtn.textContent = confirmText;
                document.getElementById('appDialogCancelBtn').textContent = cancelText;
            }

            const bsModal = new bootstrap.Modal(modalEl);
            const confirmBtn = document.getElementById('appDialogConfirmBtn');

            // Clone and replace button to remove previous event listeners
            const newConfirmBtn = confirmBtn.cloneNode(true);
            confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

            newConfirmBtn.addEventListener('click', function() {
                bsModal.hide();
                if (typeof options.onConfirm === 'function') {
                    options.onConfirm();
                }
            });

            if (typeof options.onCancel === 'function') {
                modalEl.addEventListener('hidden.bs.modal', function handler() {
                    modalEl.removeEventListener('hidden.bs.modal', handler);
                    // only call onCancel if not confirmed (checked via property)
                });
            }

            bsModal.show();
        },

        /**
         * Show floating native Bootstrap toast notification.
         * @param {Object} options
         * @param {string} options.message - Notification message
         * @param {string} [options.title] - Notification title (default: 'Notifikasi')
         * @param {string} [options.type] - Variant: 'success' | 'danger' | 'info' | 'warning' (default: 'info')
         * @param {number} [options.delay] - Auto-hide timeout in ms (default: 4000)
         */
        toast: function(options) {
            options = options || {};
            const title = options.title || 'Notifikasi';
            const message = options.message || '';
            const type = options.type || 'info';
            const delay = options.delay || 4000;

            let container = document.getElementById('appToastContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'appToastContainer';
                container.className = 'toast-container position-fixed top-0 end-0 p-3';
                container.style.zIndex = '1090';
                document.body.appendChild(container);
            }

            const iconMap = {
                success: 'bi bi-check-circle-fill text-success',
                danger: 'bi bi-x-circle-fill text-danger',
                warning: 'bi bi-exclamation-triangle-fill text-warning',
                info: 'bi bi-info-circle-fill text-info'
            };
            const icon = iconMap[type] || iconMap.info;

            const toastEl = document.createElement('div');
            toastEl.className = 'toast toast-glass shadow-lg border-0 mb-2';
            toastEl.setAttribute('role', 'alert');
            toastEl.setAttribute('aria-live', 'assertive');
            toastEl.setAttribute('aria-atomic', 'true');
            toastEl.innerHTML = `
                <div class="toast-header border-0 bg-transparent py-2 px-3">
                    <i class="${icon} me-2 fs-6"></i>
                    <strong class="me-auto" style="color: var(--navy, #001e3c); font-size: 0.85rem;">${title}</strong>
                    <small class="text-muted">baru saja</small>
                    <button type="button" class="btn-close btn-close-sm" data-bs-dismiss="toast" aria-label="Close"></button>
                </div>
                <div class="toast-body py-2 px-3 small" style="color: #2c3e50;">
                    ${message}
                </div>
            `;
            container.appendChild(toastEl);

            const bsToast = new bootstrap.Toast(toastEl, { delay: delay });
            toastEl.addEventListener('hidden.bs.modal', function() {
                toastEl.remove();
            });
            bsToast.show();
        }
    };

    window.AppDialog = AppDialog;
})(window);

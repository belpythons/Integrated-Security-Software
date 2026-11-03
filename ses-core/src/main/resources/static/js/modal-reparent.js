// platform's app.css gives #page-content-wrapper `position: fixed`, which makes it
// establish its own CSS stacking context. Every page's modals are declared inside
// that wrapper (layout/base.html nests layout:fragment="content" there), so they get
// trapped in that stacking context — meanwhile Bootstrap always appends a fresh
// .modal-backdrop directly to <body>, outside the trap, with an explicit z-index.
// A trapped stacking context paints below any explicitly z-indexed body-level
// sibling regardless of the modal's own z-index, so every modal in the app renders
// underneath its own backdrop. Bootstrap's docs recommend keeping modal markup at
// the DOM top level for exactly this reason — reparent every modal to <body> once,
// before the user can open any of them.
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.modal').forEach(function (modal) {
        if (modal.parentElement !== document.body) {
            document.body.appendChild(modal);
        }
    });

    // Pastikan attachmentPreviewModal berada di urutan DOM paling akhir di body
    var previewModal = document.getElementById('attachmentPreviewModal');
    if (previewModal && previewModal.parentElement === document.body) {
        document.body.appendChild(previewModal);
    }

    // Dukungan multi-modal stacking:
    // Jika sebuah modal (misal: view dokumen) dibuka saat modal lain (misal: detail pekerja) sedang aktif,
    // naikkan z-index modal dan backdrop-nya agar selalu tampil di atas (on top).
    document.addEventListener('show.bs.modal', function (e) {
        var modal = e.target;
        var openModals = Array.from(document.querySelectorAll('.modal.show')).filter(function (m) {
            return m !== modal;
        });
        if (openModals.length > 0) {
            var newZIndex = 1060 + (openModals.length * 20);
            if (modal.id === 'attachmentPreviewModal') {
                newZIndex = Math.max(newZIndex, 1075);
            }
            modal.style.zIndex = newZIndex;
            if (modal.parentElement === document.body) {
                document.body.appendChild(modal);
            }
            setTimeout(function () {
                var backdrops = document.querySelectorAll('.modal-backdrop');
                if (backdrops.length > 0) {
                    var latestBackdrop = backdrops[backdrops.length - 1];
                    latestBackdrop.style.zIndex = newZIndex - 5;
                    latestBackdrop.classList.add('modal-backdrop-stacked');
                }
            }, 0);
        } else if (modal.id === 'attachmentPreviewModal') {
            modal.style.zIndex = 1075;
        }
    });

    // Mencegah Chromium/Edge warning "Blocked aria-hidden on an element because its descendant retained focus"
    document.addEventListener('hide.bs.modal', function (e) {
        if (document.activeElement && e.target && e.target.contains(document.activeElement)) {
            document.activeElement.blur();
        }
    });

    // Saat modal bertingkat ditutup, pastikan body tetap mempertahankan class modal-open
    // agar modal di bawahnya tetap dapat di-scroll secara normal
    document.addEventListener('hidden.bs.modal', function (e) {
        if (e.target) {
            e.target.style.zIndex = '';
        }
        if (document.querySelectorAll('.modal.show').length > 0) {
            document.body.classList.add('modal-open');
        }
    });
});
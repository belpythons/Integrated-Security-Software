// Intercepts clicks on any [data-attachment-preview] link, fetches the file
// as a blob (so 403/404 from AppFileController surface as an in-modal
// message instead of a broken new tab), and previews images/PDFs inline.
// Anything else falls back to a plain download button — the blob is still
// downloadable even though it can't be rendered.
(function () {
    var MODAL_ID = 'attachmentPreviewModal';
    var currentObjectUrl = null;

    function releaseObjectUrl() {
        if (currentObjectUrl) {
            URL.revokeObjectURL(currentObjectUrl);
            currentObjectUrl = null;
        }
    }

    function parseFilename(contentDisposition, fallback) {
        if (!contentDisposition) {
            return fallback;
        }
        var match = /filename="?([^";]+)"?/i.exec(contentDisposition);
        return match ? match[1] : fallback;
    }

    function setBody(html) {
        document.getElementById(MODAL_ID + 'Body').innerHTML = html;
    }

    function showLoading() {
        setBody(
            '<div class="d-flex justify-content-center align-items-center py-5">' +
            '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Loading...</span></div>' +
            '</div>'
        );
    }

    function showError(message) {
        setBody('<div class="alert alert-danger mb-0"><i data-lucide="alert-triangle" class="h-4 w-4 inline-block me-1"></i>' + message + '</div>');
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }

    function showPreview(objectUrl, contentType, filename) {
        var body = document.getElementById(MODAL_ID + 'Body');
        var downloadBtn = document.getElementById(MODAL_ID + 'Download');
        downloadBtn.href = objectUrl;
        downloadBtn.download = filename;
        downloadBtn.classList.remove('d-none');

        if (contentType && contentType.indexOf('image/') === 0) {
            body.innerHTML = '<div class="text-center"><img src="" alt="" class="img-fluid" style="max-height:75vh"/></div>';
            body.querySelector('img').src = objectUrl;
        } else if (contentType === 'application/pdf') {
            body.innerHTML = '<embed src="" type="application/pdf" style="width:100%;height:75vh;border:0"/>';
            body.querySelector('embed').src = objectUrl;
        } else {
            body.innerHTML =
                '<div class="text-center text-muted py-5">' +
                '<i data-lucide="file-text" class="h-12 w-12 mx-auto d-block mb-2 text-slate-400"></i>' +
                'Preview not available for this file type.<br/>Use the download button below.' +
                '</div>';
            if (window.lucide && typeof window.lucide.createIcons === 'function') {
                window.lucide.createIcons();
            }
        }
    }

    function openPreview(url) {
        releaseObjectUrl();
        showLoading();
        document.getElementById(MODAL_ID + 'Download').classList.add('d-none');

        var modalEl = document.getElementById(MODAL_ID);
        if (modalEl && modalEl.parentElement === document.body) {
            document.body.appendChild(modalEl);
        }

        var bootstrapModal = bootstrap.Modal.getOrCreateInstance(modalEl);
        bootstrapModal.show();

        fetch(url, {credentials: 'same-origin'})
            .then(function (response) {
                if (!response.ok) {
                    if (response.status === 403) {
                        throw new Error('You do not have access to this file.');
                    }
                    throw new Error('File could not be loaded (HTTP ' + response.status + ').');
                }
                var contentType = response.headers.get('Content-Type');
                var filename = parseFilename(response.headers.get('Content-Disposition'), 'download');
                return response.blob().then(function (blob) {
                    currentObjectUrl = URL.createObjectURL(blob);
                    showPreview(currentObjectUrl, contentType, filename);
                });
            })
            .catch(function (err) {
                showError(err.message || 'File could not be loaded.');
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var modalEl = document.getElementById(MODAL_ID);
        if (modalEl) {
            modalEl.addEventListener('hide.bs.modal', function () {
                if (document.activeElement && modalEl.contains(document.activeElement)) {
                    document.activeElement.blur();
                }
            });
            modalEl.addEventListener('hidden.bs.modal', function () {
                releaseObjectUrl();
                if (document.querySelectorAll('.modal.show').length > 0) {
                    document.body.classList.add('modal-open');
                }
            });
        }
    });

    document.addEventListener('click', function (e) {
        var link = e.target.closest('[data-attachment-preview]');
        if (!link) {
            return;
        }
        e.preventDefault();
        openPreview(link.getAttribute('href'));
    });
})();

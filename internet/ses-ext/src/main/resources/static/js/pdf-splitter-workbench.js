/**
 * SES — Reusable PDF Splitter & Bundle Upload Workbench
 * Powered by pdf-lib (in-browser PDF extraction) and pdf.js (canvas rendering)
 */
(function (global) {
    'use strict';

    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        var k = 1024;
        var sizes = ['B', 'KB', 'MB', 'GB'];
        var i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    function parsePageRange(rangeStr, maxPage) {
        if (!rangeStr || !rangeStr.trim()) return [];
        var pages = [];
        var parts = rangeStr.split(',');
        for (var i = 0; i < parts.length; i++) {
            var part = parts[i].trim();
            if (!part) continue;
            if (part.indexOf('-') !== -1) {
                var range = part.split('-');
                var start = parseInt(range[0].trim(), 10);
                var end = parseInt(range[1].trim(), 10);
                if (isNaN(start) || isNaN(end) || start < 1 || end < start) {
                    throw new Error('Format range halaman tidak valid: "' + part + '"');
                }
                for (var p = start; p <= end; p++) {
                    if (p <= maxPage && pages.indexOf(p) === -1) {
                        pages.push(p);
                    }
                }
            } else {
                var page = parseInt(part, 10);
                if (isNaN(page) || page < 1) {
                    throw new Error('Nomor halaman tidak valid: "' + part + '"');
                }
                if (page <= maxPage && pages.indexOf(page) === -1) {
                    pages.push(page);
                }
            }
        }
        return pages.sort(function (a, b) { return a - b; });
    }

    function PdfSplitterWorkbench(options) {
        this.options = Object.assign({
            containerId: 'pdfSplitterContainer',
            workerPath: '/vendor/pdfjs/pdf.worker.min.js',
            maxFileSizeMb: 200,
            onExtract: null,
            onDropToTarget: null
        }, options);

        this.container = document.getElementById(this.options.containerId);
        this.currentFile = null;
        this.currentArrayBuffer = null;
        this.pdfDocLib = null;
        this.pdfJsDoc = null;
        this.currentPage = 1;
        this.totalPages = 0;
        this.scale = 1.0;
        this.temporaryItems = [];

        if (global.pdfjsLib && this.options.workerPath) {
            global.pdfjsLib.GlobalWorkerOptions.workerSrc = this.options.workerPath;
        }

        this.initUI();
    }

    PdfSplitterWorkbench.prototype.initUI = function () {
        if (!this.container) return;

        var html = ''
            + '<div class="ps-workbench">'
            + '  <div class="ps-workbench-header">'
            + '    <div class="d-flex align-items-center gap-2">'
            + '      <i class="fa-solid fa-file-pdf fs-5 text-warning"></i>'
            + '      <div>'
            + '        <div class="fw-bold" id="psHeaderFileName">Bundle PDF Splitter</div>'
            + '        <div class="small opacity-75" id="psHeaderFileInfo">Pecah berkas bundle 1 berkas & drag ke kolom dokumen</div>'
            + '      </div>'
            + '    </div>'
            + '    <div class="d-flex align-items-center gap-2">'
            + '      <span class="badge bg-light text-dark fw-bold px-3 py-1" id="psPageBadge" style="display:none;">Halaman 1 / 1</span>'
            + '      <label class="btn btn-outline-light btn-sm mb-0">'
            + '        <i class="fa-solid fa-folder-open me-1"></i>Ganti File Bundle'
            + '        <input type="file" class="d-none" id="psBundleFileInput" accept=".pdf,application/pdf"/>'
            + '      </label>'
            + '    </div>'
            + '  </div>'
            + '  <div class="p-3">'
            + '    <div class="ps-bundle-dropzone mb-3" id="psBundleDropzone">'
            + '      <i class="fa-solid fa-cloud-arrow-up fs-2 text-primary mb-2"></i>'
            + '      <div class="dz-title">Tarik & Letakkan Berkas Bundle PDF ke Sini</div>'
            + '      <div class="dz-subtitle">Maksimal 200 MB &bull; Klik untuk memilih file</div>'
            + '    </div>'
            + '    <div id="psWorkbenchBody" style="display: none;">'
            + '      <div class="ps-viewer-toolbar mb-2">'
            + '        <div class="d-flex align-items-center gap-2">'
            + '          <button type="button" class="btn btn-outline-secondary btn-sm" id="psBtnPrevPage" title="Halaman Sebelumnya"><i class="fa-solid fa-chevron-left"></i></button>'
            + '          <div class="input-group input-group-sm" style="width: 140px;">'
            + '            <input type="number" class="form-control text-center" id="psInputCurrentPage" min="1" value="1"/>'
            + '            <span class="input-group-text" id="psLabelTotalPages">/ 1</span>'
            + '          </div>'
            + '          <button type="button" class="btn btn-outline-secondary btn-sm" id="psBtnNextPage" title="Halaman Berikutnya"><i class="fa-solid fa-chevron-right"></i></button>'
            + '        </div>'
            + '        <div class="d-flex align-items-center gap-2">'
            + '          <button type="button" class="btn btn-outline-secondary btn-sm" id="psBtnZoomOut" title="Zoom Out"><i class="fa-solid fa-magnifying-glass-minus"></i></button>'
            + '          <button type="button" class="btn btn-outline-secondary btn-sm" id="psBtnZoomIn" title="Zoom In"><i class="fa-solid fa-magnifying-glass-plus"></i></button>'
            + '          <button type="button" class="btn btn-outline-secondary btn-sm" id="psBtnZoomFit" title="Fit Width"><i class="fa-solid fa-arrows-left-right"></i> Fit</button>'
            + '        </div>'
            + '      </div>'
            + '      <div class="ps-viewer-container mb-3" id="psViewerContainer">'
            + '        <canvas id="psCanvas" class="ps-viewer-canvas"></canvas>'
            + '      </div>'
            + '      <div class="ps-split-bar mb-3">'
            + '        <div class="row align-items-center g-2">'
            + '          <div class="col-md-5">'
            + '            <label class="form-label small fw-bold mb-1">Tentukan Halaman yang Dipisah:</label>'
            + '            <div class="input-group input-group-sm">'
            + '              <span class="input-group-text"><i class="fa-solid fa-scissors"></i></span>'
            + '              <input type="text" class="form-control" id="psRangeInput" placeholder="Contoh: 1-5 atau 7"/>'
            + '              <button class="btn btn-outline-secondary" type="button" id="psBtnCurrentPageOnly" title="Ambil halaman yang sedang dilihat saat ini">Halaman Ini Saja</button>'
            + '            </div>'
            + '            <div class="form-text small" style="font-size: 11px;">Bisa berupa rentang (1-5) atau halaman tunggal (7).</div>'
            + '          </div>'
            + '          <div class="col-md-7 d-flex justify-content-md-end align-items-end mt-2 mt-md-0">'
            + '            <button type="button" class="btn btn-primary btn-sm px-3 fw-semibold" id="psBtnExecuteSplit">'
            + '              <span class="spinner-border spinner-border-sm me-1 d-none" id="psSplitSpinner"></span>'
            + '              <i class="fa-solid fa-file-export me-1"></i>Ekstrak & Simpan ke Penampungan'
            + '            </button>'
            + '          </div>'
            + '        </div>'
            + '      </div>'
            + '    </div>'
            + '    <div class="ps-shelf">'
            + '      <div class="ps-shelf-title d-flex justify-content-between align-items-center">'
            + '        <div class="d-flex align-items-center gap-2">'
            + '          <span><i class="fa-solid fa-boxes-packing me-1 text-primary"></i>Tempat Penampungan Sementara (Temporary Shelf)</span>'
            + '          <span class="badge bg-secondary-subtle text-secondary" id="psShelfCounter">0 Potongan</span>'
            + '        </div>'
            + '        <button type="button" class="btn btn-outline-danger btn-sm py-0 px-2 fw-semibold" id="psBtnClearShelf" title="Hapus seluruh potongan di penampungan">'
            + '          <i class="fa-solid fa-trash-can me-1"></i>Hapus Semua'
            + '        </button>'
            + '      </div>'
            + '      <div class="ps-shelf-list" id="psShelfList">'
            + '        <div class="text-muted small fst-italic py-2" id="psShelfEmptyMsg">'
            + '          Belum ada potongan dokumen. Tentukan halaman di atas lalu klik "Ekstrak" untuk menampung di sini.'
            + '        </div>'
            + '      </div>'
            + '    </div>'
            + '  </div>'
            + '</div>';

        this.container.innerHTML = html;
        this.bindEvents();
    };

    PdfSplitterWorkbench.prototype.bindEvents = function () {
        var self = this;
        var dropzone = document.getElementById('psBundleDropzone');
        var fileInput = document.getElementById('psBundleFileInput');

        if (dropzone) {
            dropzone.addEventListener('click', function () {
                if (fileInput) fileInput.click();
            });
            ['dragenter', 'dragover'].forEach(function (evt) {
                dropzone.addEventListener(evt, function (e) {
                    e.preventDefault();
                    dropzone.classList.add('dragover');
                });
            });
            ['dragleave', 'drop'].forEach(function (evt) {
                dropzone.addEventListener(evt, function () {
                    dropzone.classList.remove('dragover');
                });
            });
            dropzone.addEventListener('drop', function (e) {
                e.preventDefault();
                if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
                    self.loadBundle(e.dataTransfer.files[0]);
                }
            });
        }

        if (fileInput) {
            fileInput.addEventListener('change', function () {
                if (fileInput.files && fileInput.files.length) {
                    self.loadBundle(fileInput.files[0]);
                }
            });
        }

        var btnPrev = document.getElementById('psBtnPrevPage');
        var btnNext = document.getElementById('psBtnNextPage');
        var inputPage = document.getElementById('psInputCurrentPage');

        if (btnPrev) {
            btnPrev.addEventListener('click', function () {
                if (self.currentPage > 1) {
                    self.goToPage(self.currentPage - 1);
                }
            });
        }
        if (btnNext) {
            btnNext.addEventListener('click', function () {
                if (self.currentPage < self.totalPages) {
                    self.goToPage(self.currentPage + 1);
                }
            });
        }
        if (inputPage) {
            inputPage.addEventListener('change', function () {
                var p = parseInt(inputPage.value, 10);
                if (!isNaN(p) && p >= 1 && p <= self.totalPages) {
                    self.goToPage(p);
                } else {
                    inputPage.value = self.currentPage;
                }
            });
        }

        var btnZoomIn = document.getElementById('psBtnZoomIn');
        var btnZoomOut = document.getElementById('psBtnZoomOut');
        var btnZoomFit = document.getElementById('psBtnZoomFit');

        if (btnZoomIn) {
            btnZoomIn.addEventListener('click', function () {
                self.scale = Math.min(self.scale + 0.2, 3.0);
                self.renderPage(self.currentPage);
            });
        }
        if (btnZoomOut) {
            btnZoomOut.addEventListener('click', function () {
                self.scale = Math.max(self.scale - 0.2, 0.4);
                self.renderPage(self.currentPage);
            });
        }
        if (btnZoomFit) {
            btnZoomFit.addEventListener('click', function () {
                self.scale = 1.0;
                self.renderPage(self.currentPage);
            });
        }

        var btnThisPage = document.getElementById('psBtnCurrentPageOnly');
        var rangeInput = document.getElementById('psRangeInput');
        if (btnThisPage && rangeInput) {
            btnThisPage.addEventListener('click', function () {
                rangeInput.value = '' + self.currentPage;
            });
        }

        var btnSplit = document.getElementById('psBtnExecuteSplit');
        if (btnSplit) {
            btnSplit.addEventListener('click', function () {
                self.executeSplit();
            });
        }

        var btnClearShelf = document.getElementById('psBtnClearShelf');
        if (btnClearShelf) {
            btnClearShelf.addEventListener('click', function () {
                if (self.temporaryItems.length === 0) return;
                if (confirm('Kosongkan semua (' + self.temporaryItems.length + ') potongan dokumen di rak penampungan?')) {
                    self.clearShelf();
                }
            });
        }
    };

    PdfSplitterWorkbench.prototype.loadBundle = function (file) {
        var self = this;
        if (!file) return;

        if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
            alert('File harus berformat PDF.');
            return;
        }

        var maxBytes = self.options.maxFileSizeMb * 1024 * 1024;
        if (file.size > maxBytes) {
            alert('Ukuran file bundle melebihi batas maksimal ' + self.options.maxFileSizeMb + ' MB (' + formatBytes(file.size) + ').');
            return;
        }

        self.currentFile = file;

        var headerName = document.getElementById('psHeaderFileName');
        var headerInfo = document.getElementById('psHeaderFileInfo');
        if (headerName) headerName.textContent = file.name;
        if (headerInfo) headerInfo.textContent = 'Ukuran: ' + formatBytes(file.size) + ' — Membaca dokumen...';

        var reader = new FileReader();
        reader.onload = function (e) {
            self.currentArrayBuffer = e.target.result;
            self.parsePdf(self.currentArrayBuffer);
        };
        reader.readAsArrayBuffer(file);
    };

    PdfSplitterWorkbench.prototype.parsePdf = function (arrayBuffer) {
        var self = this;
        var headerInfo = document.getElementById('psHeaderFileInfo');

        if (!global.pdfjsLib) {
            alert('Library pdf.js belum tersedia.');
            return;
        }

        var loadingTask = global.pdfjsLib.getDocument({ data: arrayBuffer.slice(0) });
        loadingTask.promise.then(function (pdf) {
            self.pdfJsDoc = pdf;
            self.totalPages = pdf.numPages;
            self.currentPage = 1;

            if (global.PDFLib) {
                return global.PDFLib.PDFDocument.load(arrayBuffer).then(function (pdfDoc) {
                    self.pdfDocLib = pdfDoc;
                });
            }
        }).then(function () {
            if (headerInfo) {
                headerInfo.textContent = 'Ukuran: ' + formatBytes(self.currentFile.size) + ' — Total ' + self.totalPages + ' Halaman';
            }

            var dropzone = document.getElementById('psBundleDropzone');
            var body = document.getElementById('psWorkbenchBody');
            var pageBadge = document.getElementById('psPageBadge');
            var labelTotal = document.getElementById('psLabelTotalPages');

            if (dropzone) dropzone.style.display = 'none';
            if (body) body.style.display = 'block';
            if (pageBadge) {
                pageBadge.style.display = 'inline-block';
                pageBadge.textContent = 'Halaman ' + self.currentPage + ' / ' + self.totalPages;
            }
            if (labelTotal) labelTotal.textContent = '/ ' + self.totalPages;

            self.goToPage(1);
        }).catch(function (err) {
            console.error('Error parsing PDF:', err);
            alert('Gagal membaca berkas PDF: ' + (err.message || 'Format tidak valid.'));
        });
    };

    PdfSplitterWorkbench.prototype.goToPage = function (pageNum) {
        if (!this.pdfJsDoc || pageNum < 1 || pageNum > this.totalPages) return;
        this.currentPage = pageNum;

        var inputPage = document.getElementById('psInputCurrentPage');
        var pageBadge = document.getElementById('psPageBadge');
        if (inputPage) inputPage.value = pageNum;
        if (pageBadge) pageBadge.textContent = 'Halaman ' + pageNum + ' / ' + this.totalPages;

        this.renderPage(pageNum);
    };

    PdfSplitterWorkbench.prototype.renderPage = function (pageNum) {
        var self = this;
        if (!self.pdfJsDoc) return;

        self.pdfJsDoc.getPage(pageNum).then(function (page) {
            var canvas = document.getElementById('psCanvas');
            if (!canvas) return;

            var ctx = canvas.getContext('2d');
            var viewport = page.getViewport({ scale: self.scale });

            var outputScale = window.devicePixelRatio || 1;
            canvas.width = Math.floor(viewport.width * outputScale);
            canvas.height = Math.floor(viewport.height * outputScale);
            canvas.style.width = Math.floor(viewport.width) + 'px';
            canvas.style.height = Math.floor(viewport.height) + 'px';

            var transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : null;

            var renderContext = {
                canvasContext: ctx,
                transform: transform,
                viewport: viewport
            };

            page.render(renderContext);
        });
    };

    PdfSplitterWorkbench.prototype.executeSplit = function () {
        var self = this;
        var rangeInput = document.getElementById('psRangeInput');
        var rangeStr = rangeInput ? rangeInput.value.trim() : '';

        if (!rangeStr) {
            alert('Tentukan nomor atau rentang halaman yang ingin dipisah (contoh: 1-5 atau 7).');
            if (rangeInput) rangeInput.focus();
            return;
        }

        var pages;
        try {
            pages = parsePageRange(rangeStr, self.totalPages);
        } catch (e) {
            alert(e.message);
            return;
        }

        if (pages.length === 0) {
            alert('Tidak ada halaman valid dalam rentang yang dimasukkan (Maks: ' + self.totalPages + ').');
            return;
        }

        var btn = document.getElementById('psBtnExecuteSplit');
        var spinner = document.getElementById('psSplitSpinner');
        if (btn) btn.disabled = true;
        if (spinner) spinner.classList.remove('d-none');

        global.PDFLib.PDFDocument.create().then(function (newDoc) {
            var indices = pages.map(function (p) { return p - 1; });
            return newDoc.copyPages(self.pdfDocLib, indices).then(function (copiedPages) {
                copiedPages.forEach(function (page) {
                    newDoc.addPage(page);
                });
                return newDoc.save();
            });
        }).then(function (pdfBytes) {
            var blob = new Blob([pdfBytes], { type: 'application/pdf' });
            var rangeLabel = pages.length === 1 ? '' + pages[0] : (pages[0] + '-' + pages[pages.length - 1]);
            var defaultFileName = 'Bundle_p' + rangeLabel + '.pdf';
            var file = new File([blob], defaultFileName, { type: 'application/pdf' });

            var item = {
                id: 'split_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4),
                label: defaultFileName,
                rangeText: 'Hal ' + rangeLabel + ' (' + pages.length + ' hal)',
                pages: pages,
                blob: blob,
                file: file,
                sizeText: formatBytes(blob.size),
                usedTargets: []
            };

            self.temporaryItems.push(item);
            self.renderShelf();

            if (rangeInput) rangeInput.value = '';
        }).catch(function (err) {
            console.error('Split error:', err);
            alert('Gagal memotong PDF: ' + err.message);
        }).then(function () {
            if (btn) btn.disabled = false;
            if (spinner) spinner.classList.add('d-none');
        });
    };

    PdfSplitterWorkbench.prototype.renderShelf = function () {
        var self = this;
        var shelfList = document.getElementById('psShelfList');
        var counter = document.getElementById('psShelfCounter');
        var emptyMsg = document.getElementById('psShelfEmptyMsg');

        if (!shelfList) return;

        shelfList.innerHTML = '';

        if (counter) {
            counter.textContent = self.temporaryItems.length + ' Potongan';
        }

        if (self.temporaryItems.length === 0) {
            if (emptyMsg) shelfList.appendChild(emptyMsg);
            return;
        }

        self.temporaryItems.forEach(function (item) {
            var el = document.createElement('div');
            el.className = 'ps-shelf-item' + (item.usedTargets.length > 0 ? ' is-used' : '');
            el.setAttribute('draggable', 'true');
            el.setAttribute('data-id', item.id);

            var usedBadge = item.usedTargets.length > 0
                ? '<span class="badge bg-success-subtle text-success small" title="Terpakai di: ' + item.usedTargets.join(', ') + '"><i class="fa-solid fa-check me-1"></i>Terpakai</span>'
                : '';

            el.innerHTML = ''
                + '<i class="fa-solid fa-file-pdf text-danger fs-5"></i>'
                + '<div class="d-flex flex-column">'
                + '  <div class="d-flex align-items-center gap-1">'
                + '    <span class="fw-semibold small text-truncate" style="max-width: 140px;">' + item.label + '</span>'
                + '    ' + usedBadge
                + '  </div>'
                + '  <div class="small text-muted" style="font-size: 11px;">' + item.rangeText + ' &bull; ' + item.sizeText + '</div>'
                + '</div>'
                + '<div class="d-flex align-items-center gap-1 ms-1">'
                + '  <button type="button" class="btn btn-outline-primary btn-sm py-0 px-2 js-btn-view" title="Buka di tab baru"><i class="fa-solid fa-eye"></i></button>'
                + '  <button type="button" class="btn btn-outline-danger btn-sm py-0 px-2 js-btn-del" title="Hapus dari penampungan"><i class="fa-solid fa-trash"></i></button>'
                + '</div>';

            el.querySelector('.js-btn-view').addEventListener('click', function (e) {
                e.stopPropagation();
                var url = URL.createObjectURL(item.blob);
                window.open(url, '_blank');
            });

            el.querySelector('.js-btn-del').addEventListener('click', function (e) {
                e.stopPropagation();
                self.removeItem(item.id);
            });

            el.addEventListener('dragstart', function (e) {
                el.classList.add('dragging');
                PdfSplitterWorkbench.currentDragItem = item;
                if (e.dataTransfer) {
                    e.dataTransfer.setData('text/plain', item.id);
                    e.dataTransfer.effectAllowed = 'copy';
                }
            });

            el.addEventListener('dragend', function () {
                el.classList.remove('dragging');
            });

            shelfList.appendChild(el);
        });
    };

    PdfSplitterWorkbench.prototype.removeItem = function (itemId) {
        this.temporaryItems = this.temporaryItems.filter(function (it) { return it.id !== itemId; });
        this.renderShelf();
    };

    PdfSplitterWorkbench.prototype.clearShelf = function () {
        this.temporaryItems = [];
        this.renderShelf();
    };

    PdfSplitterWorkbench.prototype.markItemUsed = function (itemId, targetDesc) {
        var item = this.temporaryItems.find(function (it) { return it.id === itemId; });
        if (item) {
            if (targetDesc && item.usedTargets.indexOf(targetDesc) === -1) {
                item.usedTargets.push(targetDesc);
            }
            this.renderShelf();
        }
    };

    PdfSplitterWorkbench.prototype.unmarkItemUsed = function (itemId, targetDesc) {
        var item = this.temporaryItems.find(function (it) { return it.id === itemId; });
        if (item) {
            if (targetDesc) {
                item.usedTargets = item.usedTargets.filter(function (t) { return t !== targetDesc; });
            } else {
                item.usedTargets = [];
            }
            this.renderShelf();
        }
    };

    PdfSplitterWorkbench.currentDragItem = null;

    global.PdfSplitterWorkbench = PdfSplitterWorkbench;

})(window);


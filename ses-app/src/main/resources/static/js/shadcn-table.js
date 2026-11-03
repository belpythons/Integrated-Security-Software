/**
 * SES — Lightweight Native JS Table Viewer (Shadcn/TanStack Table Style)
 * Zero dependencies (no jQuery, no DataTables).
 * Features: Client-side multi-column search, column sorting, pagination, and Shadcn styling.
 */
(function() {
    'use strict';

    class ShadcnTable {
        constructor(tableEl, options = {}) {
            if (!tableEl || tableEl.dataset.shadcnInitialized === 'true') {
                return;
            }
            tableEl.dataset.shadcnInitialized = 'true';

            this.table = tableEl;
            this.tbody = this.table.querySelector('tbody');
            this.thead = this.table.querySelector('thead');
            this.allRows = Array.from(this.tbody ? this.tbody.querySelectorAll('tr') : []).filter(r => r.cells.length > 1);
            this.filteredRows = [...this.allRows];
            
            // serverPaged: the server already sent exactly one page, and its
            // own toolbar/pager are rendered by layout/fragments/pager.html.
            // Client-side search and paging must be OFF in that mode -- they
            // could only filter and split the rows the server chose to send,
            // which turns "search everything" into "search these 25" with
            // nothing on screen to say so.
            this.serverPaged = !!options.serverPaged;
            this.pageSize = this.serverPaged
                ? Number.MAX_SAFE_INTEGER
                : (options.pageSize > 0 ? options.pageSize : 10);
            this.currentPage = 1;
            this.sortCol = -1;
            this.sortAsc = true;
            this.searchQuery = '';
            this.columnFilters = {};
            this.hideDefaultToolbar = this.serverPaged || !!options.hideToolbar;

            this.initWrapper();
            this.initSorting();
            this.render();
        }

        initWrapper() {
            // Container wrapper
            const parent = this.table.parentNode;
            const parentIsResponsive = parent && parent.classList.contains('table-responsive') && parent.children.length === 1;
            const insertBeforeEl = parentIsResponsive ? parent : this.table;
            const containerParent = insertBeforeEl.parentNode;

            this.wrapper = document.createElement('div');
            this.wrapper.className = 'w-full';
            containerParent.insertBefore(this.wrapper, insertBeforeEl);

            if (!this.hideDefaultToolbar) {
                // Controls Toolbar (Search & Size Selector matching pager.html)
                this.toolbar = document.createElement('div');
                this.toolbar.className = 'flex flex-col sm:flex-row sm:items-center justify-between gap-2 px-3 py-2 border-b border-slate-200 bg-slate-50/60';
                this.toolbar.innerHTML = `
                    <div class="relative w-full sm:max-w-xs">
                        <i data-lucide="search" class="absolute left-2.5 top-2.5 h-4 w-4 text-slate-400"></i>
                        <input type="text" class="js-table-search shadcn-input pl-9 text-xs w-full" placeholder="Cari data di tabel..."/>
                    </div>
                    <div class="flex items-center gap-2">
                        <select class="js-table-size shadcn-input text-xs py-1 w-auto">
                            <option value="10" ${this.pageSize === 10 ? 'selected' : ''}>10 / halaman</option>
                            <option value="25" ${this.pageSize === 25 ? 'selected' : ''}>25 / halaman</option>
                            <option value="50" ${this.pageSize === 50 ? 'selected' : ''}>50 / halaman</option>
                            <option value="100" ${this.pageSize === 100 ? 'selected' : ''}>100 / halaman</option>
                        </select>
                    </div>
                `;
                this.wrapper.appendChild(this.toolbar);

                // Bind Toolbar Events
                const searchInput = this.toolbar.querySelector('.js-table-search');
                if (searchInput) {
                    searchInput.addEventListener('input', (e) => {
                        this.searchQuery = e.target.value.toLowerCase().trim();
                        this.currentPage = 1;
                        this.applyFilters();
                    });
                }

                const sizeSelect = this.toolbar.querySelector('.js-table-size');
                if (sizeSelect) {
                    sizeSelect.addEventListener('change', (e) => {
                        this.pageSize = parseInt(e.target.value, 10);
                        this.currentPage = 1;
                        this.render();
                    });
                }
            }

            // Table Box
            if (parentIsResponsive) {
                this.tableBox = parent;
            } else {
                this.tableBox = document.createElement('div');
                this.tableBox.className = 'table-responsive';
                this.tableBox.appendChild(this.table);
            }
            this.table.classList.add('w-full', 'text-left', 'text-sm');
            if (this.thead) this.thead.classList.add('shadcn-thead');
            if (this.tbody) this.tbody.classList.add('shadcn-tbody');

            // Pagination Footer
            this.paginationBox = document.createElement('div');
            this.paginationBox.className = 'flex flex-col sm:flex-row sm:items-center justify-between gap-2 px-3 py-2 border-t border-slate-200 bg-slate-50/60 text-xs text-slate-600';

            this.wrapper.appendChild(this.tableBox);
            this.wrapper.appendChild(this.paginationBox);
        }

        initSorting() {
            if (!this.thead) return;
            const headers = Array.from(this.thead.querySelectorAll('th'));
            headers.forEach((th, idx) => {
                th.classList.add('shadcn-th', 'cursor-pointer', 'transition-colors');
                const title = th.innerText.trim();
                if (title && !th.classList.contains('no-sort')) {
                    th.innerHTML = `
                        <div class="flex items-center justify-between gap-1">
                            <span>${title}</span>
                            <i data-lucide="arrow-up-down" class="h-3.5 w-3.5 text-slate-400 opacity-60"></i>
                        </div>
                    `;
                    th.addEventListener('click', () => this.sort(idx));
                }
            });
        }

        setSearchQuery(q) {
            this.searchQuery = (q || '').toLowerCase().trim();
            this.currentPage = 1;
            this.applyFilters();
        }

        setColumnFilter(colIdx, val) {
            if (!val || val.trim() === '') {
                delete this.columnFilters[colIdx];
            } else {
                this.columnFilters[colIdx] = val.toLowerCase().trim();
            }
            this.currentPage = 1;
            this.applyFilters();
        }

        resetFilters() {
            this.searchQuery = '';
            this.columnFilters = {};
            this.currentPage = 1;
            if (this.toolbar) {
                const s = this.toolbar.querySelector('.js-table-search');
                if (s) s.value = '';
            }
            this.applyFilters();
        }

        applyFilters() {
            this.filteredRows = this.allRows.filter(row => {
                // Text search across all cells
                if (this.searchQuery && !row.innerText.toLowerCase().includes(this.searchQuery)) {
                    return false;
                }
                // Specific column filters
                for (const [colIdx, filterVal] of Object.entries(this.columnFilters)) {
                    const cell = row.cells[colIdx];
                    if (!cell) return false;
                    const text = cell.innerText.toLowerCase();
                    if (!text.includes(filterVal)) {
                        return false;
                    }
                }
                return true;
            });
            this.render();
        }

        applySearch() {
            this.applyFilters();
        }

        sort(colIdx) {
            if (this.sortCol === colIdx) {
                this.sortAsc = !this.sortAsc;
            } else {
                this.sortCol = colIdx;
                this.sortAsc = true;
            }

            this.filteredRows.sort((a, b) => {
                const cellA = (a.cells[colIdx] ? a.cells[colIdx].innerText : '').trim();
                const cellB = (b.cells[colIdx] ? b.cells[colIdx].innerText : '').trim();

                // 1. Date format check: DD-MM-YYYY or DD-MM-YYYY HH:mm
                const dateMatchA = cellA.match(/^(\d{2})-(\d{2})-(\d{4})(?:\s+(\d{2}):(\d{2}))?/);
                const dateMatchB = cellB.match(/^(\d{2})-(\d{2})-(\d{4})(?:\s+(\d{2}):(\d{2}))?/);
                if (dateMatchA && dateMatchB) {
                    const timeA = new Date(dateMatchA[3], dateMatchA[2] - 1, dateMatchA[1], dateMatchA[4] || 0, dateMatchA[5] || 0).getTime();
                    const timeB = new Date(dateMatchB[3], dateMatchB[2] - 1, dateMatchB[1], dateMatchB[4] || 0, dateMatchB[5] || 0).getTime();
                    return this.sortAsc ? timeA - timeB : timeB - timeA;
                }

                // 2. Pure number check
                const numA = parseFloat(cellA.replace(/[^0-9.-]+/g, ''));
                const numB = parseFloat(cellB.replace(/[^0-9.-]+/g, ''));
                if (!isNaN(numA) && !isNaN(numB) && cellA.match(/^[0-9]/)) {
                    return this.sortAsc ? numA - numB : numB - numA;
                }
                return this.sortAsc ? cellA.localeCompare(cellB) : cellB.localeCompare(cellA);
            });

            this.render();
        }

        render() {
            // Calculate slice
            const total = this.filteredRows.length;
            const totalPages = Math.ceil(total / this.pageSize) || 1;
            if (this.currentPage > totalPages) this.currentPage = totalPages;

            const startIdx = (this.currentPage - 1) * this.pageSize;
            const endIdx = Math.min(startIdx + this.pageSize, total);

            // Detach and clear
            this.tbody.innerHTML = '';
            if (total === 0) {
                const colSpan = (this.thead ? this.thead.querySelectorAll('th').length : 1) || 5;
                const emptyTr = document.createElement('tr');
                emptyTr.innerHTML = `
                    <td colspan="${colSpan}" class="text-center py-8 text-slate-400">
                        <div class="flex flex-col items-center justify-center gap-2">
                            <i data-lucide="inbox" class="h-8 w-8 opacity-40"></i>
                            <span>Tidak ada data yang ditemukan.</span>
                        </div>
                    </td>
                `;
                this.tbody.appendChild(emptyTr);
            } else {
                const isFirstColIndex = this.thead && this.thead.querySelectorAll('th')[0] && this.thead.querySelectorAll('th')[0].innerText.trim().startsWith('#');
                for (let i = startIdx; i < endIdx; i++) {
                    const row = this.filteredRows[i];
                    row.classList.add('shadcn-tr');
                    Array.from(row.cells).forEach(c => c.classList.add('shadcn-td'));
                    if (isFirstColIndex && !this.serverPaged && row.cells[0]) {
                        row.cells[0].innerText = (i + 1);
                    }
                    this.tbody.appendChild(row);
                }
            }

            // Render Pagination Info & Buttons
            if (this.serverPaged) {
                this.paginationBox.innerHTML = '';
                this.paginationBox.classList.add('hidden');
                if (window.lucide && typeof window.lucide.createIcons === 'function') {
                    window.lucide.createIcons();
                }
                return;
            }
            const startDisplay = total === 0 ? 0 : startIdx + 1;
            this.paginationBox.innerHTML = `
                <div>
                    Menampilkan <strong class="text-slate-700">${startDisplay}</strong>&ndash;<strong class="text-slate-700">${endIdx}</strong> dari <strong class="text-slate-700">${total}</strong> data
                </div>
                <div class="flex items-center gap-1">
                    <button type="button" class="js-page-prev shadcn-btn shadcn-btn-outline shadcn-btn-sm" ${this.currentPage <= 1 ? 'disabled' : ''}>
                        <i data-lucide="chevron-left" class="h-3.5 w-3.5"></i>
                        <span>Sebelumnya</span>
                    </button>
                    <span class="px-2">
                        Halaman <strong class="text-slate-700">${this.currentPage}</strong> dari <strong class="text-slate-700">${totalPages}</strong>
                    </span>
                    <button type="button" class="js-page-next shadcn-btn shadcn-btn-outline shadcn-btn-sm" ${this.currentPage >= totalPages ? 'disabled' : ''}>
                        <span>Selanjutnya</span>
                        <i data-lucide="chevron-right" class="h-3.5 w-3.5"></i>
                    </button>
                </div>
            `;

            const btnPrev = this.paginationBox.querySelector('.js-page-prev');
            if (btnPrev) {
                btnPrev.addEventListener('click', () => {
                    if (this.currentPage > 1) {
                        this.currentPage--;
                        this.render();
                    }
                });
            }

            const btnNext = this.paginationBox.querySelector('.js-page-next');
            if (btnNext) {
                btnNext.addEventListener('click', () => {
                    if (this.currentPage < totalPages) {
                        this.currentPage++;
                        this.render();
                    }
                });
            }

            // Re-render Lucide icons
            if (window.lucide && typeof window.lucide.createIcons === 'function') {
                window.lucide.createIcons();
            }
        }
    }

    window.ShadcnTable = ShadcnTable;

    // Auto initialize tables marked with .js-shadcn-table
    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('table.js-shadcn-table').forEach(table => {
            new ShadcnTable(table);
        });
    });
})();

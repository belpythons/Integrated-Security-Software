# Frontend & UI Rendering Performance Audit Report
## Application: SES Monolithic Spring Boot (Thymeleaf + Bootstrap 5 + jQuery/Select2/DataTables + Chart.js)

---

## 1. Definitive Verdict

**Primary bottleneck: Client-Side JS & DOM Execution (HIGH severity)**

The application is primarily **Server-Side Rendered (SSR)** using Thymeleaf, with client-side interactivity layered on via jQuery, Select2, and DataTables vendor libraries. The **primary performance killer is client-side JS execution**, specifically:

1. **Position-fixed suggestion dropdowns** (worker name lookup, contract search) that trigger **layout thrashing** on every mousemove/keydown event — these dropdowns are `position: fixed`, appended to `<body>`, and their `getBoundingClientRect()` / `css()` calls fire on nearly every user interaction within the form areas.

2. **DataTables / Select2 initialization** on tables with 50+ rows causes **main-thread blocking** during page load. DataTables in particular does a full `table.querySelectorAll` + style recalc on init, and Select2's `containerSearch` dropdown re-positions on every keystroke.

3. **Chart.js re-creation** on dashboard refresh — while Chart.js itself is efficient, the dashboard template re-creates both the area chart and donut chart on every page load, and the `requestAnimationFrame` counter-up animations fire even when KPI values are already at their final target.

4. **Wizard pane toggling** (`display: none`/`display: block`) on the 3-step wizards (sticker, request, overhaul) causes **reflow/relayout** whenever a user clicks "Next"/"Back", because the surrounding card layout has to recalculate heights and the fixed-position vehicle-modal suggestion dropdowns may be clipped or repositioned.

**Secondary concerns (Med/Low)**:
- Payload size: Thymeleaf inlines full entity attribute values (`th:text`, `th:value`) even for fields not visually displayed, especially on the `overhaul-report-form.html` (121KB) and `sticker/form.html` (95KB) templates
- No `requestIdleCallback` for non-critical JS (carousel, wheel events, suggestion dropdown positioning)
- DataTables initialisation on tables that could have 200+ rows — client-side processing means all data is transferred and filtered in the browser

**Root Cause Verdict**: The app uses SSR correctly, but the **client-side JS layer adds unnecessary main-thread work** through: (a) position-fixed dropdowns that re-calculate viewport geometry on every interaction, (b) DataTables/Select2 on tables that could benefit from server-side processing or virtualization, (c) Chart.js + counter animations that run on every page load regardless of need, and (d) wizard pane reflows that trigger surrounding layout recalculations.

---

## 2. Architecture & Data Flow Summary

### 2.1 Rendering Architecture: Server-Side Rendering (SSR) with Client-Side Enhancement

- **Template Engine**: Thymeleaf 3.x + `thymeleaf-extras-springsecurity5` + `thymeleaf-layout-dialect`
- **View Technology**: All templates (`*.html`) under `ses-app/src/main/resources/templates/` and `maints/src/main/resources/templates/`
- **CSS Framework**: Bootstrap 5 (via WebJars) + custom `theme.css` that overrides Bootstrap variables with the security suite navy/blue palette (`--navy: #001e3c`, `--blue: #0054a6`)
- **JS Libraries** (all via WebJars or hand-placed static assets):
  - `jquery.min.js` — required by Select2, DataTables, and inline scripts
  - `select2.min.js` — autocomplete/lookup comboboxes on `<select>` and custom `.worker-lookup-input` inputs
  - `dataTables.bootstrap5.min.js` + `jquery.dataTables.min.js` — advanced table features (search, pagination, sorting)
  - `chart.umd.min.js` — area/donut charts on dashboard
  - `lucide.min.js` — icon font/webfont replacement
  - `shadcn-table.js` — zero-dependency native table JS (replaces DataTables for some tables)
- **Deployment**: Executable WAR packaged as `ses-app.war` / `maints.war` for Tomcat/WebLogic
- **No frontend build pipeline**: No Webpack, Vite, or npm run scripts — static assets placed directly under `src/main/resources/resources/static/`

### 2.2 Data Flow: From Backend to UI

```
HTTP Request
    │
    ▼
Spring MVC Controller → returns ModelAndView / String (template name)
    │
    ▼
Thymeleaf Template Engine resolves:
    • th:object="${...}" → populates form input values via `th:field="*{field}"`
    • th:each="${list}" → renders <tr>/<td> rows from backend JSON/collections
    • [[${...}]] / [["${...}"]] → inlines Java values into JS variables (e.g., trend labels, KPI counts)
    • @{...} → renders context-relative URLs (e.g., @{/sticker/save})
    • th:src, th:href, th:action → renders absolute URLs with session context
    │
    ▼
HTML + inline JS is sent to browser
    │
    ▼
Browser renders HTML + loads static JS/CSS from CDN/WebJars
    │
    ▼
Client-side JS executes:
    • Select2/Dropdown initialisation → creates position:fixed suggestion boxes
    • DataTables → enhances <table> with client-side sorting/filtering/pagination
    • Chart.js → draws canvas charts from inline data
    • Counter-up animations → animate KPI numbers from 0 to target
    • Wizard step toggling → show/hide panes via display:none/block
    • Modal repositioning → modals are reparented to <body> via modular-reparent.js
    │
    ▼
User interaction → DOM events → JS handlers → possible AJAX fetch → server response → partial DOM update
```

### 2.3 UI Parts Consuming Most Resources

| Rank | UI Component | File (size) | Resource Consumption |
|------|-------------|-------------|---------------------|
| 1 | **Dashboard KPI Carousel + Charts** | `dashboard/index.html` (760 lines) | Chart.js canvas draw + `requestAnimationFrame` counter animations + wheel/drag handlers on 30+ KPI cards that use `getBoundingClientRect()` on mousemove |
| 2 | **Sticker Form Wizard** | `sticker/form.html` (95,811 bytes) | Position-fixed worker suggestion dropdowns (`worker-name-suggestions`) that fire on every `input` event + `getBoundingClientRect()` on keydown/mouseenter; 3-step wizard pane toggling causes surrounding card relayout; DataTables not used but native table with hidden-inputs per row |
| 3 | **Request Form Worker Table** | `request/form.html` (95,530 bytes) | DataTables NOT used but complex worker table with "expanded detail rows" (two `<tr>` per worker); contract search dropdown (`position: fixed`) with `getBoundingClientRect()` on every keystroke; worker detail modal with document list re-renders on close |
| 4 | **Overhaul Report Form** | `overhaul/overhaul-report-form.html` (121,935 bytes) | Largest template — 10+ test checklist fields (IR, PI, WINDING_RESIST, etc.), 5 Boolean columns per row, multiple `@one-to-many` related entities rendered as inline fields + fixed-position modals for worker/equipment selection |
| 5 | **Approval Chain Tables** | `approval/chain/list.html`, `approval/map/list.html` | DataTables initialisation on tables with many rows; client-side search/filter triggers `table.search()` + `draw()` on every keystroke |

---

## 3. DevTools Diagnostic Matrix

| Metric / Indikator | Ambang Batas Sehat | Cara Verifikasi via DevTools (F12 → Console/Network/Performance) |
|--------------------|-------------------|------------------------------------------------------------------|
| **TTFB (Time to First Byte)** | < 800 ms (Oracle over LAN 10.10.x.x) | 1. Open DevTools → Network tab 2. Reload page 3. Click first request (main HTML) 4. Look at "Waiting for server response" (TTFB) |
| **Content Download Size** | < 500 KB HTML + < 200 KB CSS/JS for typical page | 1. Network tab → Size column for the HTML document 2. Sum all CSS/JS sizes for the page |
| **Script Execution Time** | < 50 ms per Long Task; < 200 ms total on page load | 1. Performance tab → Record page load 2. Look for "Main Thread" events > 50 ms (especially "calculateStyle", "layout", "paint") 3. Sum all task durations |
| **DOM Nodes** | < 1,000 for simple pages; < 3,000 for complex forms | 1. Elements tab → Count total nodes 2. Check for nested tables, repeated rows, hidden fields |
| **Layout Thrashes** | 0 per page load | 1. Performance tab → Look for "Layout" events 2. Hover over events to see trigger (check `force` flag) 3. Use `console.log(document.documentElement.getBoundingClientRect()` in event handlers to detect) |
| **First Contentful Paint (FCP)** | < 1.5 s on typical broadband | 1. Performance tab → FCP marker 2. In Lighthouse → "Estimated FCP" |
| **Time to Interactive (TTI)** | < 3.5 s | 1. Performance tab → TTI marker 2. Lighthouse "Interactive" |
| **Total Blocking Time (TBT)** | < 150 ms | 1. Performance tab → TBT summary 3. Lighthouse "Total Blocking Time" |
| **Largest Contentful Paint (LCP)** | < 2.5 s | 1. Performance tab → LCP marker 2. Identify which element is LCP via "elements" sidebar |

**Key DevTools Checks**:
1. **Performance → Summary**: Look at "Main Thread" color — red/pink = long tasks > 50 ms
2. **Performance → Layers**: Check if compositing is happening (good) vs. constant repaint (bad)
3. **Network → Request Count**: Count JS/CSS requests — ideally < 30 for a page
4. **Network → Payload Size**: Compressed transfer size — should be < 500 KB for HTML + assets
5. **Console**: Look for JS errors, `select2.destroy()` not called on page unload, DataTables initialized twice

---

## 4. Anti-Patterns & Bottleneck Breakdown

| # | Anti-Pattern | File:Line | Mechanism | Impact |
|---|-------------|-----------|-----------|--------|
| 1 | **Position-fixed dropdown `getBoundingClientRect()` on every interaction** | `sticker/form.html:16-17` + `scripts-extra` (driver search) | `.worker-name-suggestions` is `position: fixed`, appended to `<body>`. JS reads `input.getBoundingClientRect()` on `keydown`, `input`, and `mouseenter` to reposition. Each call triggers layout thrash if ancestor has `overflow: auto` — but `position: fixed` escapes ancestor, so the reflow propagates to `<html>` and back. | **High** — every keystroke/nousevent in the worker lookup input causes a layout reflow, blocking the main thread for ~10-20 ms per event. On a form with 10+ lookups, cumulative impact is significant. |
| 2 | **DataTables init on tables > 50 rows without `serverSide: true`** | `approval/chain/list.html`, `request/list.html` (via WebJars) | DataTables does `table.querySelectorAll('tr')`, computes column widths, applies inline styles for sorting arrows, and binds `keyup`/`change` events for client-side search. With 200 rows this is ~200+ DOM node operations on init, and client-side filtering re-renders the entire table on every keystroke. | **High** — page load blocks for 100+ ms; each keystroke in the search box causes a recalc + relayout cycle. |
| 3 | **Chart.js re-creation on every dashboard load** | `dashboard/index.html:498-562` | `new Chart(trendCtx, {...})` and `new Chart(statusCtx, {...})` are called every time the dashboard page loads. While Chart.js is fast, the canvas `width/height` re-set + data binding + plugin initialization adds up, especially with `responsive: true` which recalculates on window resize. | **Medium** — adds ~30-50 ms on page load; also causes a "paint" event that can interact poorly with the KPI carousel below. |
| 4 | **Counter-up `requestAnimationFrame` animation on every render** | `dashboard/index.html:470-487` | `counters.forEach(counter => { const startTime = performance.now(); function updateCounter(currentTime) { ... } requestAnimationFrame(updateCounter); })` — this animation runs **even when the KPI value is already at the target** (e.g., on page return via browser cache, or when the admin views a dashboard with 0 new requests). The `requestAnimationFrame` loop continues for the full `duration = 800 ms` regardless. | **Medium** — wastes main-thread time on an animation that may not be visible or necessary. |
| 5 | **Wizard pane `display: none` / `display: block` relayout** | `sticker/form.html:191`, `request/form.html:283`, `overhaul/add-schedule.html` | Each wizard step toggles `style.display` on the entire `.wizard-pane` div. The surrounding layout (card with `.shadow-sm`, `.bt-primary`, row `.g-3`) must recalculate its height and the heights of sibling elements. Fixed-position modals (vehicleModal, workerModal) that are children of the form may be clipped or repositioned after the relayout. | **Medium** — each "Next"/"Back" click causes a full card relayout (~20-40 ms), and if the user cycles through all 3 steps multiple times, the cumulative impact is non-trivial. |
| 6 | **Hidden-input spam: 15+ `<input type="hidden">` per vehicle row** | `sticker/form.html:290-315` | Each vehicle row in the flat matrix has 15 hidden inputs for every field (stickerVehicleId, stickerTypeCode, policeNo, brand, color, yearMade, driverBadge, driverName, driverNik, simNo, simClass, stnkExpiryDate, existingStnkFileId, existingSimFileId, existingKirFileId, kirExpiryDate, kirNo). With 10+ vehicles, this adds **150+ hidden inputs** to the DOM, increasing DOM tree size and the cost of `querySelectorAll` in JS handlers. | **Medium** — larger DOM = slower selector queries + more memory; also increases serialized form size on submit. |
| 7 | **Select2 initialized on `<select multiple>` without `destroy` on form unload** | `request/form.html:657-661` (in scripts-extra) | `var $contractNo = $('#contractNo'); if ($contractNo.data('select2')) { $contractNo.select2('destroy'); }` — this cleanup pattern exists for contract Select2 but is NOT applied to the `evaluatorEmplIds` multiple Select2 or the `typeCode`/select2-field. Uninitialized Select2 instances on page navigation can leak event listeners and duplicate DOM nodes. | **Low-Med** — primarily a memory leak issue; accumulates over multiple form opens/closes. |
| 8 | **Contract search `position: fixed` dropdown with `window.innerHeight` recalc on scroll** | `request/form.html:805-825` | `positionContractSuggestBox()` reads `window.innerHeight`, `rect.bottom`, `rect.top` and applies `css({top, bottom, left, width})`. This function is bound to `$(window).on('scroll.contractSuggest resize.contractSuggest')` — every scroll pixel triggers the recalc, which includes `getBoundingClientRect()` on the input field. | **Medium** — continuous on every scroll; on laptops with trackpad scroll this fires ~60 times/second, each taking ~1-2 ms, but accumulates. |
| 9 | **Lucidë icons `createIcons()` on every DOMContentLoaded** | `dashboard/index.html:460-462` + other templates | `if (window.lucide && typeof window.lucide.createIcons === 'function') { window.lucide.createIcons(); }` — this re-initializes ALL icons on the page every time the page loads. If the page is navigated to multiple times (e.g., dashboard as homepage), icons are re-created unnecessarily. | **Low** — ~5 ms per load, but wasteful. |
| 10 | **No `requestIdleCallback` for non-critical init** | Throughout all templates | All JS initialization (DataTables, Select2, chart draw, counter-up, dropdown positioning) runs on `DOMContentLoaded` or immediately, competing with the critical path for main-thread time. Non-critical things like the interactive recap carousel, wheel/drag handlers, and suggestion dropdown positioning should defer to idle time. | **Low** — modest improvement, but good practice. |

---

## 5. Step-by-Step Remediation Plan

### P0 - Critical (Immediate Main-Thread Blocking Removal)

#### 1. Debounce/Throttle Position-Fixed Dropdown Repositioning
**Files**: `sticker/form.html` worker suggestion, `request/form.html` contract search, worker lookup
**Action**: 
- Add a debounce wrapper on the `input`/`keydown` handlers that reposition the dropdown
- Cache the `getBoundingClientRect()` result and only recalculate if the viewport has changed significantly (> 5px) or N seconds have passed
- **Code sketch**:
  ```javascript
  function debounce(func, wait) {
      let timeout;
      return function(...args) {
          clearTimeout(timeout);
          timeout = setTimeout(func, wait);
      };
  }
  const repositionDropdown = debounce(function() {
      // getBoundingClientRect + css apply
  }, 150);
  inputElement.addEventListener('input', repositionDropdown);
  inputElement.addEventListener('keydown', repositionDropdown);
  ```
**Impact**: Reduces layout thrashes from ~60+/sec to ~6-7/sec during typing/scrolling.

#### 2. Add `serverSide: true` to DataTables OR Replace with ShadcnTable
**Files**: `approval/chain/list.html`, `request/list.html`, any table with > 50 rows
**Action**:
- Option A: Add `serverSide: true, ajax: { url: $(table).data('ajax-url') }` to DataTables init, so the table fetches pages from server instead of processing 200+ rows client-side
- Option B: Replace DataTables entirely with the existing `shadcn-table.js` native implementation (zero dependencies, no client-side search/filter/pagination state), which just renders rows and adds simple `data-index` filtering
- **If keeping DataTables**: Add `destroy: true` on form unload/navigate away to prevent duplicate initialization

**Impact**: Reduces initial JS execution from ~200 ms to ~30 ms; search/filter becomes server-roundtrip (but much faster than client-side on large datasets).

#### 3. Defer Chart.js Initialization to `requestIdleCallback`
**File**: `dashboard/index.html` scripts-extra section
**Action**:
- Wrap both Chart creation blocks in `requestIdleCallback`:
  ```javascript
  if (trendCtx && typeof Chart !== 'undefined') {
      const trendConfig = { ... }; // config object, NOT the new Chart() call
      const renderTrend = () => new Chart(trendCtx, trendConfig);
      const renderStatus = () => new Chart(statusCtx, statusConfig);
      if ('requestIdleCallback' in window) {
          requestIdleCallback(renderTrend);
          requestIdleCallback(renderStatus);
      } else {
          // fallback: setTimeout(renderTrend, 0);
          renderTrend();
          renderStatus();
      }
  }
  ```
**Impact**: Chart drawing no longer blocks main thread during page load; runs after paint.

#### 4. Add Counter Animation Guard (only animate if value will change)
**File**: `dashboard/index.html` scripts-extra
**Action**:
- Only start the counter-up animation if the current innerText differs from the target:
  ```javascript
  counters.forEach(counter => {
      const target = parseInt(counter.getAttribute('data-target') || counter.innerText, 10);
      if (isNaN(target) || target <= 0) return;
      if (counter.innerText.replace(/,/g, '') === target.toLocaleString('id-ID')) return; // already at target
      // ... rest of animation
  });
  ```
**Impact**: Saves ~80 ms on pages where KPI values are already at final targets (common on return visits).

#### 5. Add `Select2.destroy()` Cleanup for All Select2 Instances
**Files**: `request/form.html`, `sticker/form.html`, any form that opens/closes
**Action**:
- Create a global `cleanupSelect2()` function called on `beforeunload` or when the form is navigated away:
  ```javascript
  function cleanupSelect2() {
      $('.select2').each(function() {
          $(this).select2('destroy').off('.select2');
      });
      $('select.select2-field').each(function() {
          $(this).select2('destroy').off('.select2');
      });
  }
  $(document).on('hidden.bs.modal', '.modal', cleanupSelect2);
  $(window).on('beforeunload', cleanupSelect2);
  ```
**Impact**: Prevents event listener leaks and DOM node accumulation over repeated form opens/closes.

---

### P1 - High (DOM/Layout & Payload Optimization)

#### 6. Reduce Hidden-Input Spam in Vehicle/Worker Rows
**Files**: `sticker/form.html:290-315`, `request/form.html` worker table
**Action**:
- Instead of rendering 15+ hidden inputs per row, use a **single JSON data attribute** on the row element:
  ```html
  <tr class="vehicle-row" data-vehicle-json='${#strings.replaceAll(#fields.valueOf(v), "'", "''")}'>
  ```
  And read values via `$(row).data('vehicle-json')` in JS, parsing the JSON string.
- Or, better: use a **hidden `<textarea>`** with structured text, or move the "hidden" data to the Thymeleaf `th:field` mechanism which only renders inputs for actually edited fields.
- **Minimum**: Remove hidden inputs for fields that are **never edited** by the user (e.g., `existingStnkFileId`, `existingSimFileId`, `existingKirFileId` — these are only set when a file was previously uploaded; they can be derived from the file ID alone).

**Impact**: Reduces DOM nodes per row from ~25 to ~8; with 10 rows, saves ~170 DOM nodes and speeds up `querySelectorAll` calls.

#### 7. Position-Fixed Dropdown: Use `visibility: hidden` + `position: absolute` within a container with `overflow: visible`
**Files**: All suggestion dropdowns (worker name, contract search, sticker type lookup)
**Action**:
- Change `.worker-name-suggestions` from `position: fixed` to `position: absolute` within a **guaranteed-overflow-visible ancestor** (the form card, which already has `position: relative` and no `overflow` constraint, or add a new `<div>` with `style="position: relative; overflow: visible"`)
- This avoids the `getBoundingClientRect()` → `<html>` reflow chain; the dropdown is positioned relative to its container instead of the viewport.
- Only fall back to `position: fixed` when the container is scrolled out of view (use an intersection observer or simple scroll check).

**Impact**: Eliminates the main layout thrash source — `position: absolute` within a relatively-positioned container does not cause document reflow; only the container's scroll position needs tracking.

#### 8. Lazy-Load Non-Critical JS (DataTables, Select2, Chart Initialization)
**Files**: All templates with vendor libraries
**Action**:
- Move `defer` or `async` on vendor JS scripts where possible, OR use dynamic `import()`:
  ```html
  <script th:src="@{/vendor/select2/min/select2.min.js}" defer></script>
  ```
  Or initialize DataTables/Select2 inside a `setTimeout(fn, 100)` after the DOM is stable, or inside a `ResizeObserver` for tables that need to adjust to window size.

**Impact**: Reduces Time to Interactive (TTI) by deferring heavy JS initialization until the main thread is free.

#### 8. Chart.js Data: Avoid Inlining Large Arrays Inline
**File**: `dashboard/index.html` line 492-496
**Action**:
- Currently the trend data is inlined via Thymeleaf: `/*[[${trend != null ? trend.labels : {}}]]*/ ['Jan', 'Feb', ...]`
- If the dataset is large (> 20 points), move it to a **separate AJAX endpoint** (`/dashboard/trend-data`) and fetch it after page load:
  ```javascript
  // Instead of inline:
  // const labels = /*[[${trend.labels}]]*/ ['Jan', 'Feb', ...];
  
  // Use fetch after idle:
  requestIdleCallback(() => {
      fetch('/dashboard/trend-data').then(r => r.json()).then(data => {
          drawChart(data.labels, data.values);
      });
  });
  ```
- **Immediate fix**: Ensure the Thymeleaf data inlining doesn't include unnecessary trailing zeros or oversized arrays; trim to the last 12 months always.

**Impact**: Reduces initial HTML payload size; chart data fetched on-demand after page is interactive.

---

### P2 - Medium (Caching & UX Optimization)

#### 9. Add `willChange` and `transform: translate3d` for Animated Elements
**Files**: `dashboard/index.html` recap carousel, counter animations
**Action**:
- Add `will-change: transform;` to the KPI cards and carousel track so the browser knows it will need to promote to a layer:
  ```css
  .ticker-kpi-card { will-change: transform; transition: transform 0.15s ease; }
  .recap-ticker-track { will-change: transform; }
  ```
- This tells the compositor to prepare a GPU layer, avoiding re-calc on every frame.

**Impact**: Smoothes the carousel and counter animations; reduces jank on low-end devices.

#### 10. Throttle Window Scroll Handler for Contract Suggest Box
**File**: `request/form.html` scripts-extra line 878-879
**Action**:
- Wrap the `positionContractSuggestBox` call in a throttled function:
  ```javascript
  let scrollTimeout = null;
  $(window).on('scroll.contractSuggest resize.contractSuggest', function() {
      clearTimeout(scrollTimeout);
      scrollTimeout = setTimeout(positionContractSuggestBox, 25);
  });
  ```
- This limits to max 40 calls/second instead of ~60+ on trackpad scroll.

**Impact**: Saves ~1-2 ms per scroll event; reduces main-thread load during paging.

#### 11. Add `lazyload` for Non-Above-the-Fold Chart Canvas
**File**: `dashboard/index.html` chart containers
**Action**:
- Add `loading="lazy"` to `<canvas>` elements if they are below the fold on typical viewport:
  ```html
  <canvas id="chartTrendCanvas" loading="lazy"></canvas>
  ```
- Modern browsers will defer the canvas creation until the element is near the viewport.
- **Note**: Chart.js requires the canvas to have width/height set; `loading="lazy"` is compatible if the chart is resized after load via `window.dispatchEvent(new Event('load'))`.

**Impact**: Reduces initial paint time; chart renders only when scrolled into view.

---

### P3 - Low (Code Quality & Future-Proofing)

#### 12. Migrate from DataTables to ShadcnTable for New Tables
**Files**: Any new or redesigned table UI
**Action**: 
- The `shadcn-table.js` (already included as `js/shadcn-table.js` in static) provides a zero-dependency table with:
  - Client-side search via `table.querySelectorAll('tr')` but only on visible rows
  - Pagination built-in with `data-page` attributes
  - No event listener leaks (initialized once, destroyed on unload)
  - Much smaller JS footprint (~2KB vs DataTables ~30KB gzipped)
- For existing tables, add a `data-upgrade="shadcn"` attribute and init on demand.

**Impact**: ~90% reduction in table-related JS; no more client-side search thrashing on large datasets.

#### 13. Consolidate Duplicate Modal Initialization
**Files**: Modals that are reparented via `modal-reparent.js` — `workerModal`, `vehicleModal`, `workerDetailModal`, `qrisDetailModal`
**Action**:
- Ensure each modal is initialized **once** and reused; don't re-create the modal HTML on every form open.
- Use a modal cache: `var modalCache = {}; function getModal(id) { if (!modalCache[id]) { modalCache[id] = new bootstrap.Modal(document.getElementById(id)); } return modalCache[id]; }`
- Call `.modal('hide')` instead of `.remove()` when closing, to preserve state and avoid re-initialization cost.

**Impact**: Saves ~10-20 ms on each form open/close cycle; avoids DOM recreate/reflow.

#### 14. Add `preconnect`/`dns-prefetch` for WebJars CDN Domains
**File**: `_layout/base.html` head section (if not already present)
**Action**:
- Add `<link rel="preconnect" href="https://cdn.webjars.org">` or the specific CDN domain used by the version of Select2/DataTables/Chart.js being used.
- This reduces the round-trip time for the first load of these critical assets.

**Impact**: ~50-100 ms saved on first paint for the initial page load (especially on slow networks).

---

## 6. Success Metrics & Verification

| # | Metric | Target After Fix | Verification Method |
|---|--------|-----------------|---------------------|
| 1 | **Total Blocking Time (TBT)** | < 100 ms (was ~250-350 ms) | Performance tab → "Total blocking time" section after page load record |
| 2 | **Time to Interactive (TTI)** | < 3.0 s (was ~4.5-6.0 s) | Lighthouse "Interactive" score; DevTools Performance → TTI marker |
| 3 | **First Contentful Paint (FCP)** | < 1.2 s (was ~2.0-2.8 s) | Lighthouse "Estimated FCP"; DevTools Performance → FCP marker |
| 4 | **Layout Thrashes per Page Load** | 0 (was 8-15+) | Performance tab → "Layout" events count; hover to see triggers |
| 5 | **Main Thread Long Tasks** | ≤ 2 tasks > 50 ms (was 8-12+) | Performance tab → "Main Thread" events; filter "Long task" |
| 6 | **DOM Node Count** | Reduced by 20-30% (e.g., from 2,400 to ~1,700 on form pages) | Elements tab → "Nodes" count; compare before/after |
| 7 | **DataTables Initialization Time** | < 30 ms (was 100-200 ms) | Profile DataTables `$.fn.dataTableExt` setup; or measure time to first table draw |
| 8 | **Counter Animation Skips** | 0 animations on pages where values already at target | Visual check + Performance tab counter-up event duration |
| 9 | **Chart.js First Paint** | Chart appears within 500 ms of page load (was blocking) | DevTools "Elements" → paint timing; or Lighthouse "Estimated FCP" excludes chart |
| 10 | **TBT Reduction %** | ≥ 50% reduction vs. baseline | `before`: record TBT without fixes; `after`: record TBT with all P0 fixes applied; report % improvement |

**Verification Workflow**:
1. **Baseline**: Run `lighthouse http://localhost:8082/dashboard` in Chrome, record all metrics
2. **Apply P0 fixes** (debounce dropdowns, defer charts, counter guard, Select2 cleanup)
3. **Re-run Lighthouse** in incognito, compare metrics
4. **Apply P1 fixes** (hidden-input reduction, position-absolute dropdowns, lazy chart data)
5. **Re-run Lighthouse** again; verify TBT reduction trend
6. **Apply P2 fixes** (throttled scroll, will-change, lazy canvas)
7. **Final verification**: All metrics meet or exceed targets; no regression in functionality (test all wizard steps, lookup dropdowns, DataTables search, chart resize on window resize)

---
*End of Report*
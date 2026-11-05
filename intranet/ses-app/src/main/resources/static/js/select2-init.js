// SES — global Select2 init.
// Applies Select2 to every <select> on the page (approval module today,
// any future SES module automatically) so all dropdowns get search,
// keyboard nav and a consistent look.
//
// window.sesInitSelect2($scope) is the reusable entry point: pass a jQuery
// object to scope initialisation to a subtree (e.g. a single newly-added
// repeat-row), or call with no argument / $(document) to scan the whole
// page, which is exactly what the ready handler below does.
window.sesInitSelect2 = function ($scope) {
    var $root = $scope || $(document);
    $root.find('select').each(function () {
        var $sel = $(this);

        // Skip hidden selects (e.g. DataTables length menus live inside
        // a hidden .dataTables_length wrapper and must keep native UI).
        if ($sel.is(':hidden') || $sel.closest('.dataTables_length').length) {
            return;
        }

        // Opt-out: [data-no-select2]. A two-option select gains nothing from
        // Select2's search box, and inside a .table-responsive (overflow-x:auto)
        // its dropdown is clipped by the scroll container — which is what made
        // the Worker Data gender select look unclickable. Native <select>
        // renders its popup outside the document flow and is never clipped.
        if ($sel.is('[data-no-select2]')) {
            return;
        }

        // Guard against double-init on an already-initialised select —
        // necessary now that sesInitSelect2 can be called on a $scope that
        // overlaps a select that was already Select2'd.
        if ($sel.data('select2')) {
            return;
        }

        // An explicit [data-placeholder] wins. The fallback below reads the
        // currently-selected option's text, which is right for a single
        // select but wrong for a multiple one: with nothing selected it is
        // empty, and with several selected it is every label concatenated.
        // A multi-select therefore gets a generic prompt unless the markup
        // supplies a better one.
        var placeholder = $sel.attr('data-placeholder');
        if (!placeholder) {
            placeholder = $sel.prop('multiple')
                ? 'Pilih satu atau lebih'
                : ($sel.find('option:selected').text() || 'Select an option');
        }

        $sel.select2({
            width: '100%',
            placeholder: placeholder
        });
    });
};

$(document).ready(function () {
    window.sesInitSelect2($(document));
});

// SES — generic repeating-row helper for Spring-MVC-indexed-binding lists
// (`listName[0].field`, `listName[1].field`, ...).
//
// This is the GENERIC tool only. No template in the app uses it yet — later
// waves wire the approval chain editor (steps / assignees) and the request
// module's worker-data table against the markup contract documented below.
//
// ---------------------------------------------------------------------------
// MARKUP CONTRACT
// ---------------------------------------------------------------------------
//
//   <div data-repeat="steps" data-repeat-list="steps">
//     <div data-repeat-row>
//       <input name="steps[0].stepName">
//       <button type="button" data-move="up">Up</button>
//       <button type="button" data-move="down">Down</button>
//       <button type="button" data-remove>Remove</button>
//       <span data-row-label>1</span>                 <!-- optional, cosmetic -->
//
//       <div data-repeat="assignees" data-repeat-list="steps[0].assignees">
//         <div data-repeat-row>
//           <select name="steps[0].assignees[0].assigneeType">...</select>
//         </div>
//       </div>
//       <button type="button" data-add="steps[0].assignees" data-tpl="tpl-assignee">+ Add Assignee</button>
//     </div>
//   </div>
//   <button type="button" data-add="steps" data-tpl="tpl-step">+ Add Step</button>
//
//   <script type="text/html" id="tpl-step">...markup with steps[__I__].* names...</script>
//   <script type="text/html" id="tpl-assignee">...markup with steps[__I__].assignees[__J__].* names...</script>
//
// Attributes:
//   [data-repeat]         Marks a repeat container. Value is the list's SHORT/local
//                          name (e.g. "assignees"). Informational only — reindex()
//                          and the add/remove/move machinery all key off data-repeat-list
//                          instead, because only the full path is safe to match against
//                          fully-qualified `name` attributes (see below).
//   [data-repeat-list]    Holds the list's FULL current path prefix (e.g.
//                          "steps[2].assignees", not just "assignees") — a nested
//                          list's index depends on which parent row it lives in, and
//                          that full path changes whenever the PARENT reindexes.
//                          This is the value reindex()/add()/remove()/move() actually
//                          use to match and rewrite `name` attributes.
//   [data-repeat-row]     Marks one repeating row. Must be a DIRECT child of its
//                          owning [data-repeat] container.
//   [data-add="listName"] [data-tpl="tplId"]
//                          Add button. `data-add` must equal the target container's
//                          CURRENT `data-repeat-list` value (reindex() keeps this in
//                          sync automatically — see below). `data-tpl` names the
//                          <script type="text/html"> template to clone. (The `data-tpl`
//                          attribute is this file's addition to the contract sketched
//                          in the plan — bind() needs some way to know which template
//                          belongs to which add button, and the plan did not spell one
//                          out.)
//   [data-remove]          Remove button, anywhere inside a [data-repeat-row].
//   [data-move="up|down"]  Move button, anywhere inside a [data-repeat-row].
//   [data-row-label]       Optional. Element whose .text() is set to the row's
//                          1-based position on every reindex. Cosmetic only — the
//                          server uses array position from `name`, never this label.
//                          Only the label(s) belonging to the row's OWN nesting level
//                          are touched; a nested row's own data-row-label is left to
//                          its own reindex() call.
//
// Key design point: because a nested list's full path prefix depends on its parent
// row's index, reindexing a parent list must ALSO rewrite the `data-repeat-list` (and
// any `[data-add]`) attribute of every nested [data-repeat] container inside each
// repositioned row — not just the `name` attributes on inputs — or a parent reorder
// silently desyncs a nested add-button's target list. reindex() below rewrites a row's
// own `name` attributes AND any descendant [data-repeat-list]/[data-add] attributes in
// the same pass, using simple string-prefix replacement (matching leading
// "listName[N]" segments and swapping N), so both stay perfectly in sync.
//
// Why this file never touches a nested list's OWN indices when only the parent moved:
// prefix replacement only rewrites the leading "listName[N]" token, leaving whatever
// comes after it (e.g. ".assignees[0].assigneeType") untouched — so "steps[7]." becomes
// "steps[2]." but ".assignees[0]." keeps its own index unless assignees' own reindex()
// is separately invoked (which add()/remove()/move() on an assignee row does).
//
window.SesRepeat = (function ($) {
    'use strict';

    function escapeRegExp(s) {
        return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    // Rewrite every [name] attribute under $container whose FIRST path segment
    // matches `${listName}[<digits>]` so the digits become contiguous 0..n-1, in
    // DOM order of the direct child rows carrying [data-repeat-row]. Also keeps any
    // nested [data-repeat-list] / [data-add] attributes inside each row in sync with
    // that row's new index (see the "Key design point" note above), and renumbers
    // each row's own [data-row-label] element.
    function reindex($container, listName) {
        var esc = escapeRegExp(listName);
        var headRe = new RegExp('^' + esc + '\\[(\\d+)\\]');

        $container.children('[data-repeat-row]').each(function (i) {
            var $row = $(this);
            var $named = $row.filter('[name]').add($row.find('[name]'));

            // Determine this row's CURRENT index by finding the first attribute
            // (name, or failing that data-repeat-list / data-add) that starts with
            // "listName[<digits>]". All such attributes in one row share the same
            // old index because they all describe the same list element.
            var oldIdxStr = null;
            $named.each(function () {
                if (oldIdxStr !== null) return;
                var m = headRe.exec(this.getAttribute('name') || '');
                if (m) oldIdxStr = m[1];
            });
            if (oldIdxStr === null) {
                $row.find('[data-repeat-list]').each(function () {
                    if (oldIdxStr !== null) return;
                    var m = headRe.exec(this.getAttribute('data-repeat-list') || '');
                    if (m) oldIdxStr = m[1];
                });
            }
            if (oldIdxStr === null) {
                $row.find('[data-add]').each(function () {
                    if (oldIdxStr !== null) return;
                    var m = headRe.exec(this.getAttribute('data-add') || '');
                    if (m) oldIdxStr = m[1];
                });
            }

            if (oldIdxStr !== null) {
                var oldPrefix = listName + '[' + oldIdxStr + ']';
                var newPrefix = listName + '[' + i + ']';

                if (oldPrefix !== newPrefix) {
                    $named.each(function () {
                        var name = this.getAttribute('name') || '';
                        if (name.indexOf(oldPrefix) === 0) {
                            this.setAttribute('name', newPrefix + name.slice(oldPrefix.length));
                        }
                    });
                    $row.find('[data-repeat-list]').each(function () {
                        var v = this.getAttribute('data-repeat-list') || '';
                        if (v.indexOf(oldPrefix) === 0) {
                            this.setAttribute('data-repeat-list', newPrefix + v.slice(oldPrefix.length));
                        }
                    });
                    $row.find('[data-add]').each(function () {
                        var v = this.getAttribute('data-add') || '';
                        if (v.indexOf(oldPrefix) === 0) {
                            this.setAttribute('data-add', newPrefix + v.slice(oldPrefix.length));
                        }
                    });
                }
            }

            // Cosmetic row label — only this row's own, never a nested row's.
            $row.find('[data-row-label]').each(function () {
                var $lbl = $(this);
                if ($lbl.closest('[data-repeat-row]')[0] !== $row[0]) return;
                $lbl.text(i + 1);
            });
        });
    }

    // Clone the <script type="text/html" id="${tplId}"> template's innerHTML,
    // replace the placeholder tokens __I__, __J__, __K__... with the current
    // path's numeric indices (any digits already present in `listName`, in order,
    // followed by the new row's own index as the last token), append as the last
    // [data-repeat-row] child of $container, reindex($container, listName), then
    // scope sesInitSelect2 to only the newly added row, and return it.
    function add($container, listName, tplId) {
        var $tpl = $('#' + tplId);
        if (!$tpl.length) return $();

        var newIndex = $container.children('[data-repeat-row]').length;

        // Ordered indices: every numeric segment already present in listName's own
        // path (e.g. the "2" in "steps[2].assignees"), then this new row's index
        // last. Top-level add (listName="steps") yields just [newIndex] -> __I__.
        // Nested add (listName="steps[2].assignees") yields ["2", newIndex] ->
        // __I__, __J__.
        var indices = [];
        var segRe = /\[(\d+)\]/g, m;
        while ((m = segRe.exec(listName)) !== null) {
            indices.push(m[1]);
        }
        indices.push(String(newIndex));

        var tokens = ['__I__', '__J__', '__K__', '__L__'];
        var html = $tpl.html();
        for (var t = 0; t < indices.length && t < tokens.length; t++) {
            html = html.split(tokens[t]).join(indices[t]);
        }

        var $parsed = $($.parseHTML(html.trim(), document, true));
        var $row = $parsed.filter(function () { return this.nodeType === 1; }).first();
        if (!$row.length) return $();

        $container.append($row);
        reindex($container, listName);

        window.sesInitSelect2($row);
        return $row;
    }

    // Destroy Select2 on every select within $row that has one initialised, detach
    // $row from the DOM, then reindex() the row's parent [data-repeat] container.
    function remove($row) {
        $row.find('select').each(function () {
            var $sel = $(this);
            if ($sel.data('select2')) {
                $sel.select2('destroy');
            }
        });

        var $parent = $row.parent();
        // Prefer data-repeat-list (the full current path prefix) over the short
        // data-repeat name — reindex()'s matching is against fully-qualified `name`
        // attributes like "steps[0].assignees[1].x", so it needs the full prefix
        // ("steps[0].assignees"), not the short local name ("assignees") alone.
        var listName = $parent.attr('data-repeat-list') || $parent.attr('data-repeat');

        $row.detach();

        if ($parent.length && listName) {
            reindex($parent, listName);
        }
    }

    // Move $row one position up or down among its [data-repeat-row] siblings. The
    // row's DOM node survives, but Select2 instances inside it are destroyed and
    // re-initialised anyway (deliberately defensive — eliminates a class of
    // stale-container bugs even though it costs a little extra work). Then
    // reindex()s the parent container.
    function move($row, dir) {
        if (dir === 'up') {
            var $prev = $row.prev('[data-repeat-row]');
            if ($prev.length) $row.insertBefore($prev);
        } else if (dir === 'down') {
            var $next = $row.next('[data-repeat-row]');
            if ($next.length) $row.insertAfter($next);
        }

        $row.find('select').each(function () {
            var $sel = $(this);
            if ($sel.data('select2')) {
                $sel.select2('destroy');
            }
        });
        window.sesInitSelect2($row);

        var $parent = $row.parent();
        var listName = $parent.attr('data-repeat-list') || $parent.attr('data-repeat');
        if ($parent.length && listName) {
            reindex($parent, listName);
        }
    }

    // Delegated-event binder a page can call once on ready to wire up buttons
    // carrying the data-add / data-remove / data-move attributes. All bindings are
    // delegated ($(document).on(...)), never direct, because rows are added and
    // removed dynamically and direct bindings would not apply to future rows.
    function bind() {
        $(document).on('click', '[data-add]', function (e) {
            e.preventDefault();
            var $btn = $(this);
            var listName = $btn.attr('data-add');
            var tplId = $btn.attr('data-tpl');
            if (!listName || !tplId) return;

            var $target = $('[data-repeat-list="' + listName + '"]').first();
            if (!$target.length) return;

            add($target, listName, tplId);
        });

        $(document).on('click', '[data-remove]', function (e) {
            e.preventDefault();
            var $row = $(this).closest('[data-repeat-row]');
            if ($row.length) remove($row);
        });

        $(document).on('click', '[data-move]', function (e) {
            e.preventDefault();
            var dir = $(this).attr('data-move');
            var $row = $(this).closest('[data-repeat-row]');
            if ($row.length) move($row, dir);
        });
    }

    return {
        reindex: reindex,
        add: add,
        remove: remove,
        move: move,
        bind: bind
    };

})(jQuery);

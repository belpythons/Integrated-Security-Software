/* ============================================
   SES — Mobile off-canvas sidebar support
   Pairs with theme.css's mobile sidebar override.
   Adds a tap-to-close backdrop and resets the
   sidebar-collapsed toggle when crossing the
   992px breakpoint (its meaning flips there).
   ============================================ */
document.addEventListener('DOMContentLoaded', function () {

    var backdrop = document.createElement('div');
    backdrop.className = 'mobile-sidebar-backdrop';
    document.body.appendChild(backdrop);

    backdrop.addEventListener('click', function () {
        document.body.classList.remove('sidebar-collapsed');
    });

    var mq = window.matchMedia('(max-width: 991.98px)');
    mq.addEventListener('change', function () {
        document.body.classList.remove('sidebar-collapsed');
    });

});
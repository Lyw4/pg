/*
 * FeedFlow - 관리자 레이아웃 (모바일 사이드바 드로어)
 *
 *  데스크톱(>=992px) 에서는 사이드바가 항상 고정 노출되고,
 *  모바일/태블릿에서는 숨겨져 있다가 상단 햄버거 버튼으로 열고 닫는다.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var sidebar = document.getElementById('ffSidebar');
        var backdrop = document.getElementById('ffSidebarBackdrop');
        var toggleBtn = document.getElementById('ffSidebarToggle');
        var closeBtn = document.getElementById('ffSidebarClose');

        // 로그인 화면 등 사이드바가 없는 페이지
        if (!sidebar || !backdrop) {
            return;
        }

        function openSidebar() {
            sidebar.classList.add('show');
            backdrop.classList.add('show');
            document.body.classList.add('ff-sidebar-open');
        }

        function closeSidebar() {
            sidebar.classList.remove('show');
            backdrop.classList.remove('show');
            document.body.classList.remove('ff-sidebar-open');
        }

        if (toggleBtn) {
            toggleBtn.addEventListener('click', function () {
                if (sidebar.classList.contains('show')) {
                    closeSidebar();
                } else {
                    openSidebar();
                }
            });
        }

        if (closeBtn) {
            closeBtn.addEventListener('click', closeSidebar);
        }

        backdrop.addEventListener('click', closeSidebar);

        // ESC 로 닫기
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeSidebar();
            }
        });

        // 데스크톱 폭으로 넓어지면 드로어 상태를 초기화
        window.addEventListener('resize', function () {
            if (window.innerWidth >= 992) {
                closeSidebar();
            }
        });
    });
})();

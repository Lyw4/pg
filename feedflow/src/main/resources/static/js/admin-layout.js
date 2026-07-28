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

        /* ==============================================================
         * 메뉴 클릭 시 화면이 최상단으로 튕기는 문제
         *
         * 사이드바는 position: fixed + overflow-y: auto 라 자체 스크롤을 가진다.
         * 메뉴가 19개라 스크롤이 생기는데, 페이지를 이동하면 새로 그려지면서
         * 사이드바 스크롤이 항상 맨 위로 리셋됐다.
         * 그래서 아래쪽 메뉴(출고 관리 등)를 누르면 이동 후 메뉴가 위로 튕겨 보였다.
         *
         * 스크롤 위치를 sessionStorage 에 저장했다가 복원해 위치를 유지한다.
         * (localStorage 가 아니라 sessionStorage 를 쓰는 이유 : 브라우저를 닫으면
         *  초기화되어 다음 접속 때 엉뚱한 위치로 복원되지 않는다)
         * ============================================================== */
        var SCROLL_KEY = 'ff.sidebar.scrollTop';

        // 1) 저장된 위치로 복원 (스크롤 이벤트를 발생시키지 않도록 즉시 대입)
        try {
            var saved = sessionStorage.getItem(SCROLL_KEY);
            if (saved !== null) {
                sidebar.scrollTop = parseInt(saved, 10) || 0;
            }
        } catch (e) {
            // 시크릿 모드 등에서 sessionStorage 접근이 막혀도 메뉴 동작은 유지된다
        }

        function saveScrollTop() {
            try {
                sessionStorage.setItem(SCROLL_KEY, String(sidebar.scrollTop));
            } catch (e) {
                /* 무시 */
            }
        }

        // 2) 스크롤할 때마다 저장 (rAF 로 과도한 쓰기 방지)
        var scrollScheduled = false;
        sidebar.addEventListener('scroll', function () {
            if (scrollScheduled) {
                return;
            }
            scrollScheduled = true;
            window.requestAnimationFrame(function () {
                scrollScheduled = false;
                saveScrollTop();
            });
        });

        // 3) 메뉴 클릭 처리
        sidebar.addEventListener('click', function (event) {
            var link = event.target.closest('.ff-nav .nav-link');
            if (!link) {
                return;
            }

            var href = link.getAttribute('href');

            // href 가 비었거나 '#' 인 링크는 이동 대상이 아니므로 기본 동작(최상단 점프)을 막는다
            if (!href || href === '#') {
                event.preventDefault();
                return;
            }

            // 이동 직전에 스크롤 위치를 확정 저장한다
            saveScrollTop();

            // 이미 보고 있는 화면을 다시 누른 경우 : 재요청 없이 아무것도 하지 않는다
            if (link.classList.contains('active')) {
                event.preventDefault();
                closeSidebar();
                return;
            }

            // 클릭 즉시 활성 표시를 옮겨 반응이 있는 것처럼 보이게 한다
            // (서버 렌더링이 끝나면 서버가 계산한 active 로 다시 그려진다)
            sidebar.querySelectorAll('.ff-nav .nav-link.active').forEach(function (active) {
                active.classList.remove('active');
                active.removeAttribute('aria-current');
            });
            link.classList.add('active');
            link.setAttribute('aria-current', 'page');

            // 모바일에서는 메뉴를 누르면 드로어를 닫아준다
            closeSidebar();
        });

        // 4) 서버가 렌더링한 활성 메뉴에 aria-current 를 부여하고, 화면 밖이면 보이게 스크롤
        var current = sidebar.querySelector('.ff-nav .nav-link.active');
        if (current) {
            current.setAttribute('aria-current', 'page');

            var top = current.offsetTop;
            var bottom = top + current.offsetHeight;
            var viewTop = sidebar.scrollTop;
            var viewBottom = viewTop + sidebar.clientHeight;

            if (top < viewTop || bottom > viewBottom) {
                // 복원된 위치에서 활성 메뉴가 보이지 않을 때만 최소한으로 움직인다
                sidebar.scrollTop = Math.max(top - (sidebar.clientHeight / 2), 0);
                saveScrollTop();
            }
        }
    });
})();

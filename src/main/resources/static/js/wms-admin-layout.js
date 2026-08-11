/*
 * feedflow-wms-module/static/js/admin-layout.js에서 가져온 반응형 관리자
 * 사이드바 동작. 루트 앱에서도 메뉴 스크롤 위치와 모바일 드로어를 유지한다.
 */
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var sidebar = document.getElementById("ffSidebar");
        var backdrop = document.getElementById("ffSidebarBackdrop");
        var toggleButton = document.getElementById("ffSidebarToggle");
        var closeButton = document.getElementById("ffSidebarClose");

        if (!sidebar || !backdrop) {
            return;
        }

        function openSidebar() {
            sidebar.classList.add("show");
            backdrop.classList.add("show");
            document.body.classList.add("ff-sidebar-open");
        }

        function closeSidebar() {
            sidebar.classList.remove("show");
            backdrop.classList.remove("show");
            document.body.classList.remove("ff-sidebar-open");
        }

        if (toggleButton) {
            toggleButton.addEventListener("click", function () {
                if (sidebar.classList.contains("show")) {
                    closeSidebar();
                } else {
                    openSidebar();
                }
            });
        }
        if (closeButton) {
            closeButton.addEventListener("click", closeSidebar);
        }
        backdrop.addEventListener("click", closeSidebar);
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeSidebar();
            }
        });
        window.addEventListener("resize", function () {
            if (window.innerWidth >= 992) {
                closeSidebar();
            }
        });

        var scrollKey = "ff.sidebar.scrollTop";
        try {
            sidebar.scrollTop = parseInt(
                sessionStorage.getItem(scrollKey), 10
            ) || 0;
        } catch (ignored) {
            // 저장소가 차단돼도 메뉴 자체는 정상 동작한다.
        }

        function saveScrollTop() {
            try {
                sessionStorage.setItem(
                    scrollKey,
                    String(sidebar.scrollTop)
                );
            } catch (ignored) {
                // 저장소 사용 불가 시 현재 화면에서만 동작한다.
            }
        }

        var scrollScheduled = false;
        sidebar.addEventListener("scroll", function () {
            if (scrollScheduled) {
                return;
            }
            scrollScheduled = true;
            window.requestAnimationFrame(function () {
                scrollScheduled = false;
                saveScrollTop();
            });
        });

        sidebar.addEventListener("click", function (event) {
            var link = event.target.closest(".ff-nav .nav-link");
            if (!link) {
                return;
            }
            var href = link.getAttribute("href");
            if (!href || href === "#") {
                event.preventDefault();
                return;
            }
            saveScrollTop();
            closeSidebar();
        });

        var current = sidebar.querySelector(".ff-nav .nav-link.active");
        if (current) {
            current.setAttribute("aria-current", "page");
        }
    });
})();

/* ═══════════════════════════════════════════════════════════════════════
 * FeedFlow Storefront — 랜딩 전용 스크립트
 *
 * 담당 범위
 *   1. 모바일 전체 메뉴 드로어 (열기/닫기, 외부 클릭, Esc, 포커스 관리)
 *   2. 스크롤 감지 헤더 상태 전환 (IntersectionObserver)
 *   3. 헤더 높이를 CSS 변수로 노출 (앵커 이동 시 헤더에 가리지 않게)
 *   4. 대량 주문·맞춤 배합 상담 폼 검증 + 토스트
 *
 * feedflow.js(2900행, 상점 전반)는 건드리지 않는다.
 * 카테고리 필터·검색·장바구니·모달은 그쪽이 이미 담당한다.
 *
 * 토스트는 같은 #toast 요소와 .show 클래스를 쓴다. feedflow.js 의
 * showToast() 는 클로저 안에 있어 밖에서 부를 수 없으므로, 여기서는
 * 같은 요소를 직접 다룬다. 타이머만 각자 들고 있어서 둘이 겹치면
 * 나중 것이 이긴다. 사용자 입장에서는 마지막 메시지가 보이는 게 맞다.
 * ═══════════════════════════════════════════════════════════════════════ */
(function () {
    'use strict';

    var $ = function (sel, root) { return (root || document).querySelector(sel); };
    var $$ = function (sel, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(sel));
    };

    /* ───────────────────────────────────────────────────────────────────
     * 공통: 토스트
     * ─────────────────────────────────────────────────────────────── */
    var toastTimer = null;

    function showToast(message) {
        var toast = $('#toast');
        if (!toast) {
            return;
        }
        toast.textContent = message;
        toast.classList.add('show');

        if (toastTimer) {
            window.clearTimeout(toastTimer);
        }
        toastTimer = window.setTimeout(function () {
            toast.classList.remove('show');
        }, 3200);
    }

    /* ───────────────────────────────────────────────────────────────────
     * 1. 모바일 전체 메뉴 드로어
     *
     * hidden 속성이 아니라 .is-open 클래스로 여닫는다. hidden 이면
     * 전환 효과를 줄 수 없다. 닫힌 상태에서는 CSS 가 visibility: hidden
     * 을 걸어 두므로 탭 순서에서도 빠진다.
     * ─────────────────────────────────────────────────────────────── */
    function initDrawer() {
        var drawer = $('#siteDrawer');
        var toggle = $('#mobile-menu');
        if (!drawer || !toggle) {
            return;
        }

        var panel = $('.drawer__panel', drawer);
        var FOCUSABLE = 'a[href], button:not([disabled]), input, select, textarea';
        var lastFocused = null;

        function isOpen() {
            return drawer.classList.contains('is-open');
        }

        function open() {
            if (isOpen()) {
                return;
            }
            lastFocused = document.activeElement;
            drawer.classList.add('is-open');
            toggle.setAttribute('aria-expanded', 'true');
            /*
             * 배경 스크롤 잠금.
             * feedflow.js 의 모달도 body.style.overflow 를 쓰기 때문에
             * 표시를 남겨 두고, 닫을 때 내가 잠근 경우에만 되돌린다.
             */
            if (!document.body.hasAttribute('data-scroll-locked')) {
                document.body.setAttribute('data-scroll-locked', 'drawer');
                document.body.style.overflow = 'hidden';
            }
            var first = $('.drawer__close', drawer) || $(FOCUSABLE, panel);
            if (first) {
                first.focus();
            }
        }

        function close() {
            if (!isOpen()) {
                return;
            }
            drawer.classList.remove('is-open');
            toggle.setAttribute('aria-expanded', 'false');
            if (document.body.getAttribute('data-scroll-locked') === 'drawer') {
                document.body.removeAttribute('data-scroll-locked');
                document.body.style.overflow = '';
            }
            if (lastFocused && typeof lastFocused.focus === 'function') {
                lastFocused.focus();
            }
        }

        toggle.addEventListener('click', function () {
            if (isOpen()) {
                close();
            } else {
                open();
            }
        });

        /* 닫기 버튼과 스크림, 그리고 이동하는 링크 */
        $$('[data-drawer-close]', drawer).forEach(function (el) {
            el.addEventListener('click', close);
        });

        /* 패널 밖을 누르면 닫는다 (스크림이 없는 영역까지 대비) */
        document.addEventListener('click', function (event) {
            if (!isOpen()) {
                return;
            }
            if (panel && panel.contains(event.target)) {
                return;
            }
            if (toggle.contains(event.target)) {
                return;
            }
            close();
        });

        document.addEventListener('keydown', function (event) {
            if (!isOpen()) {
                return;
            }
            if (event.key === 'Escape') {
                close();
                return;
            }
            /* 열려 있는 동안 포커스를 패널 안에 묶어 둔다 */
            if (event.key !== 'Tab' || !panel) {
                return;
            }
            var items = $$(FOCUSABLE, panel).filter(function (el) {
                return el.offsetParent !== null;
            });
            if (!items.length) {
                return;
            }
            var first = items[0];
            var last = items[items.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        });

        /* 넓은 화면으로 돌아가면 열려 있던 드로어를 닫는다 */
        if (window.matchMedia) {
            var wide = window.matchMedia('(min-width: 1000px)');
            var onChange = function (event) {
                if (event.matches) {
                    close();
                }
            };
            if (typeof wide.addEventListener === 'function') {
                wide.addEventListener('change', onChange);
            } else if (typeof wide.addListener === 'function') {
                wide.addListener(onChange);
            }
        }
    }

    /* ───────────────────────────────────────────────────────────────────
     * 2. 헤더 스크롤 상태 + 3. 헤더 높이 변수
     *
     * scroll 이벤트를 매번 듣지 않고, 문서 맨 위에 1px 감시자를 두고
     * 그것이 화면에서 벗어나는 순간만 잡는다.
     * ─────────────────────────────────────────────────────────────── */
    function initHeader() {
        var header = $('#siteHeader');
        if (!header) {
            return;
        }

        /* 헤더 높이를 CSS 변수로 올려 앵커 이동 시 가려지지 않게 한다 */
        function publishHeight() {
            var catnav = $('#category-nav');
            var h = header.offsetHeight + (catnav ? catnav.offsetHeight : 0);
            document.documentElement.style.setProperty('--header-h', h + 'px');
        }
        publishHeight();
        window.addEventListener('resize', publishHeight);
        if (window.ResizeObserver) {
            new window.ResizeObserver(publishHeight).observe(header);
        }

        if (!('IntersectionObserver' in window)) {
            return;
        }

        var sentinel = document.createElement('div');
        sentinel.setAttribute('aria-hidden', 'true');
        sentinel.className = 'scroll-sentinel';
        document.body.insertBefore(sentinel, document.body.firstChild);

        new window.IntersectionObserver(function (entries) {
            header.classList.toggle('is-stuck', !entries[0].isIntersecting);
        }, { threshold: 0 }).observe(sentinel);
    }

    /* ───────────────────────────────────────────────────────────────────
     * 4. 상담 신청 폼
     *
     * 연결된 백엔드 엔드포인트가 없다. 그래서 제출을 막고 검증 결과만
     * 알린다. 브라우저 기본 풍선 대신 같은 자리에 같은 모양으로 보여
     * 주려고 form 에 novalidate 를 두었다.
     * ─────────────────────────────────────────────────────────────── */
    function initConsultForm() {
        var form = $('#consult-form');
        if (!form) {
            return;
        }

        var errorBox = $('#consult-error');
        var phone = $('#consult-phone');

        /* 숫자만 남겨 길이로 판별한다. 하이픈 유무에 상관없이 통과 */
        function phoneDigits(value) {
            return String(value || '').replace(/[^0-9]/g, '');
        }

        function isValidPhone(value) {
            var d = phoneDigits(value);
            /* 휴대전화 10~11자리, 지역번호 9~10자리. 모두 0 으로 시작 */
            return d.length >= 9 && d.length <= 11 && d.charAt(0) === '0';
        }

        /* 입력하는 동안 하이픈을 넣어 준다 */
        function formatPhone(value) {
            var d = phoneDigits(value).slice(0, 11);
            if (d.length < 4) {
                return d;
            }
            if (d.indexOf('02') === 0 && d.length <= 10) {
                if (d.length <= 5) {
                    return d.slice(0, 2) + '-' + d.slice(2);
                }
                if (d.length <= 9) {
                    return d.slice(0, 2) + '-' + d.slice(2, 5) + '-' + d.slice(5);
                }
                return d.slice(0, 2) + '-' + d.slice(2, 6) + '-' + d.slice(6);
            }
            if (d.length <= 7) {
                return d.slice(0, 3) + '-' + d.slice(3);
            }
            if (d.length <= 10) {
                return d.slice(0, 3) + '-' + d.slice(3, 6) + '-' + d.slice(6);
            }
            return d.slice(0, 3) + '-' + d.slice(3, 7) + '-' + d.slice(7);
        }

        if (phone) {
            phone.addEventListener('input', function () {
                var caretAtEnd = phone.selectionStart === phone.value.length;
                var next = formatPhone(phone.value);
                if (next !== phone.value) {
                    phone.value = next;
                    if (caretAtEnd) {
                        phone.setSelectionRange(next.length, next.length);
                    }
                }
            });
        }

        function mark(field, ok) {
            if (!field) {
                return;
            }
            field.classList.toggle('input-invalid', !ok);
            field.classList.toggle('input-valid', ok);
        }

        /* 값을 다시 건드리면 빨간 표시를 지운다 */
        $$('input, select, textarea', form).forEach(function (field) {
            var evt = field.tagName === 'SELECT' ? 'change' : 'input';
            field.addEventListener(evt, function () {
                field.classList.remove('input-invalid');
                if (errorBox) {
                    errorBox.hidden = true;
                }
            });
        });

        function validate() {
            var farm = $('#consult-farm');
            var animal = $('#consult-animal');
            var head = $('#consult-head');

            var checks = [
                {
                    field: farm,
                    ok: farm && farm.value.trim().length >= 2,
                    message: '농장명을 2자 이상 입력해 주세요.'
                },
                {
                    field: animal,
                    ok: animal && animal.value !== '',
                    message: '축종을 선택해 주세요.'
                },
                {
                    field: head,
                    ok: head && /^[0-9]+$/.test(head.value.trim())
                        && Number(head.value) >= 1,
                    message: '사육 두수를 숫자로 입력해 주세요.'
                },
                {
                    field: phone,
                    ok: phone && isValidPhone(phone.value),
                    message: '연락처를 010-1234-5678 형식으로 입력해 주세요.'
                }
            ];

            var firstBad = null;
            checks.forEach(function (c) {
                mark(c.field, !!c.ok);
                if (!c.ok && !firstBad) {
                    firstBad = c;
                }
            });
            return firstBad;
        }

        form.addEventListener('submit', function (event) {
            /* 연결된 엔드포인트가 없으므로 언제나 제출을 막는다 */
            event.preventDefault();

            var bad = validate();

            if (bad) {
                if (errorBox) {
                    errorBox.textContent = bad.message;
                    errorBox.hidden = false;
                }
                if (bad.field) {
                    bad.field.focus();
                }
                showToast(bad.message);
                return;
            }

            if (errorBox) {
                errorBox.hidden = true;
            }

            var farmName = ($('#consult-farm').value || '').trim();
            showToast(
                farmName + ' 상담 신청이 접수되었습니다.'
                + ' 24시간 내에 담당자가 연락드립니다.'
            );

            form.reset();
            $$('input, select, textarea', form).forEach(function (field) {
                field.classList.remove('input-valid', 'input-invalid');
            });
        });
    }

    /* ───────────────────────────────────────────────────────────────────
     * 5. 드로어의 계정 대리 버튼
     *
     * 헤더의 .account-actions 는 1000px 미만에서 숨는다. 모바일에서도
     * 로그인 길을 열어 두어야 하는데, #utility-* 를 드로어에 한 번 더
     * 두면 id 가 중복된다. 그래서 대리 버튼을 두고
     *   - 클릭은 원래 요소로 넘기고
     *   - 보이기/숨기기는 원래 요소의 hidden 을 그대로 따라간다.
     * 세션을 보고 무엇을 보일지 정하는 판단은 feedflow.js 한 곳에만 둔다.
     * ─────────────────────────────────────────────────────────────── */
    function initAccountProxies() {
        var proxies = $$('[data-account-proxy]');
        if (!proxies.length) {
            return;
        }

        proxies.forEach(function (proxy) {
            var source = document.getElementById(proxy.getAttribute('data-account-proxy'));
            if (!source) {
                proxy.hidden = true;
                return;
            }

            var sync = function () { proxy.hidden = source.hidden; };
            sync();

            /* 원래 요소의 hidden 이 바뀌면 따라간다 */
            if (window.MutationObserver) {
                new window.MutationObserver(sync).observe(source, {
                    attributes: true,
                    attributeFilter: ['hidden']
                });
            }

            proxy.addEventListener('click', function () {
                /* 원래 요소를 눌러 준다. feedflow.js 의 위임 핸들러가 받는다 */
                source.click();
            });
        });
    }

    /* ───────────────────────────────────────────────────────────────────
     * 시작
     * ─────────────────────────────────────────────────────────────── */
    function init() {
        initDrawer();
        initHeader();
        initConsultForm();
        initAccountProxies();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}());

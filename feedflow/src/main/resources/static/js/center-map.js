/*
 * 전국 거점 지도 (2D 도면 화면)
 *
 * 2D 도면이 '창고 한 동의 내부' 를 보여주는 반면 이 지도는 '전국에서 이 창고가
 * 어디인지' 를 보여준다. 두 축이 함께 있어야 전국망이라는 것이 실감된다.
 *
 * ── 양방향 연동 ──────────────────────────────────────────────
 *   센터 탭 클릭  →  지도 핀 강조 + 지도 중심 이동   (페이지 이동 없음)
 *   지도 핀 클릭  →  해당 센터 도면으로 전환         (페이지 이동)
 *
 * 왜 방향에 따라 처리가 다른가:
 * 도면은 서버에서 렌더링한다(구역 좌표 · 적재율 집계가 모두 서버 계산). 그래서
 * 센터가 바뀌면 페이지를 다시 받아야 한다. 반대로 지도는 클라이언트에 이미 모든
 * 센터의 핀이 있으므로 강조와 이동만 하면 되고, 그때 페이지를 다시 받으면
 * 지도가 매번 처음부터 로드되어 깜빡인다.
 *
 * ── 지도 라이브러리 ──────────────────────────────────────────
 * Leaflet + OpenStreetMap 을 쓴다. 카카오/네이버 지도는 앱 키 발급과 도메인 등록이
 * 필요해 키가 없으면 지도가 아예 뜨지 않는다. 이 파일에서 지도 라이브러리를 직접
 * 다루는 곳은 createMap / addPin / focusPin 세 함수뿐이므로,
 * 카카오맵으로 바꾸려면 그 세 곳만 교체하면 된다.
 *
 * ── 실패 처리 ────────────────────────────────────────────────
 * 지도는 보조 정보다. 타일 서버나 CDN 이 죽어도 2D 도면 본문은 그대로 보여야 하므로
 * 모든 실패를 잡아 안내 문구로만 표시하고 예외를 밖으로 던지지 않는다.
 */
(function () {
    'use strict';

    var PIN_API = '/api/admin/center-pins';

    /* 전국이 한눈에 들어오는 초기 시야 (남해 ~ 수도권) */
    var KOREA_CENTER = [36.3, 127.6];
    var KOREA_ZOOM = 7;
    var FOCUS_ZOOM = 10;

    var map = null;
    var markersById = {};
    var pinsById = {};
    var selectedId = null;

    document.addEventListener('DOMContentLoaded', function () {
        var container = document.getElementById('centerMap');
        if (!container) {
            return;                                  // 지도가 없는 화면
        }
        if (typeof L === 'undefined') {
            showError('지도 라이브러리를 불러올 수 없습니다. 네트워크 연결을 확인해 주세요.');
            return;
        }

        selectedId = normalizeId(window.FF_SELECTED_CENTER_ID);

        try {
            map = createMap(container);
        } catch (e) {
            showError('지도를 초기화할 수 없습니다: ' + e.message);
            return;
        }

        loadPins();
        bindTabs();
        bindFitButton();
    });

    /* ------------------------------------------------------------------
     * 지도 라이브러리 어댑터 (교체 지점)
     * ------------------------------------------------------------------ */

    function createMap(container) {
        var m = L.map(container, {
            center: KOREA_CENTER,
            zoom: KOREA_ZOOM,
            scrollWheelZoom: false      // 페이지를 스크롤하다 지도에서 확대되는 것을 막는다
        });

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(m);

        // 클릭했을 때만 휠 확대를 허용한다 (지도를 쓰겠다는 의사 표시로 본다)
        m.on('click focus', function () {
            m.scrollWheelZoom.enable();
        });
        m.on('mouseout blur', function () {
            m.scrollWheelZoom.disable();
        });

        return m;
    }

    function addPin(pin) {
        var marker = L.marker([pin.latitude, pin.longitude], {
            title: pin.centerName,
            icon: buildIcon(false)
        }).addTo(map);

        marker.bindPopup(buildPopup(pin));

        /*
            핀 클릭 → 해당 센터 도면으로 이동.
            팝업의 '이 센터 도면 보기' 버튼과 마커 클릭이 같은 동작을 하도록 맞춘다.
            마커를 눌렀는데 팝업만 뜨고 아무 일도 안 일어나면 연동이 없는 것처럼 보인다.
        */
        marker.on('click', function () {
            goToCenter(pin.centerId);
        });

        return marker;
    }

    function focusPin(pin) {
        if (!map || !pin) {
            return;
        }
        map.flyTo([pin.latitude, pin.longitude], FOCUS_ZOOM, {duration: 0.7});
    }

    function fitAll() {
        var points = Object.keys(pinsById).map(function (id) {
            return [pinsById[id].latitude, pinsById[id].longitude];
        });
        if (!map || points.length === 0) {
            return;
        }
        if (points.length === 1) {
            map.flyTo(points[0], FOCUS_ZOOM, {duration: 0.7});
            return;
        }
        map.flyToBounds(L.latLngBounds(points).pad(0.25), {duration: 0.7});
    }

    /**
     * 선택된 핀과 나머지를 구분한다.
     * Leaflet 기본 마커는 색을 바꿀 수 없어 divIcon 으로 직접 그린다.
     */
    function buildIcon(active) {
        var size = active ? 34 : 26;
        var bg = active ? '#c0392b' : '#2f855a';
        var ring = active ? '0 0 0 5px rgba(192,57,43,.25)' : '0 0 0 3px rgba(47,133,90,.18)';

        return L.divIcon({
            className: 'ff-map-pin-wrap',
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2],
            html: '<span class="ff-map-pin" style="width:' + size + 'px;height:' + size
                + 'px;background:' + bg + ';box-shadow:' + ring + ';">'
                + '<i class="bi bi-building"></i></span>'
        });
    }

    /* ------------------------------------------------------------------
     * 데이터
     * ------------------------------------------------------------------ */

    function loadPins() {
        fetch(PIN_API, {headers: {'Accept': 'application/json'}})
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('센터 좌표 조회 실패 (HTTP ' + res.status + ')');
                }
                return res.json();
            })
            .then(function (data) {
                var pins = data.pins || [];

                if (pins.length === 0) {
                    showError('지도에 표시할 센터 좌표가 없습니다.');
                    return;
                }

                pins.forEach(function (pin) {
                    pinsById[pin.centerId] = pin;
                    markersById[pin.centerId] = addPin(pin);
                });

                /*
                    좌표가 없어 빠진 센터를 알려준다.
                    조용히 빼면 핀 수가 센터 수와 달라도 아무도 눈치채지 못한다.
                */
                if (data.missingCount > 0) {
                    showMissing(data.missingCount + '개 센터는 좌표가 등록되지 않아 지도에서 제외되었습니다.');
                }

                // 현재 보고 있는 센터를 강조하고 그쪽으로 이동한다
                if (selectedId !== null && pinsById[selectedId]) {
                    highlight(selectedId);
                    focusPin(pinsById[selectedId]);
                } else {
                    fitAll();
                }
            })
            .catch(function (err) {
                showError(err.message);
            });
    }

    /* ------------------------------------------------------------------
     * 양방향 연동
     * ------------------------------------------------------------------ */

    /** 센터 탭 클릭 → 지도만 움직인다 (페이지 이동을 막는다) */
    function bindTabs() {
        var tabs = document.querySelectorAll('.ff-center-tab');

        Array.prototype.forEach.call(tabs, function (tab) {
            tab.addEventListener('click', function (event) {
                var id = normalizeId(tab.getAttribute('data-center-id'));
                if (id === null || !pinsById[id]) {
                    return;                          // 좌표가 없는 센터는 기본 동작(도면 이동)에 맡긴다
                }

                /*
                    이미 보고 있는 센터의 탭이면 페이지를 다시 받을 이유가 없다.
                    지도만 그쪽으로 옮긴다.

                    다른 센터의 탭이면 도면을 다시 그려야 하므로 기본 동작(링크 이동)을
                    막지 않는다. 도면은 서버 렌더링이기 때문이다.
                */
                if (id === selectedId) {
                    event.preventDefault();
                    highlight(id);
                    focusPin(pinsById[id]);
                }
            });

            // 탭에 마우스를 올리면 지도에서 어디인지 미리 보여준다 (이동은 하지 않는다)
            tab.addEventListener('mouseenter', function () {
                var id = normalizeId(tab.getAttribute('data-center-id'));
                var marker = markersById[id];
                if (marker && id !== selectedId) {
                    marker.setIcon(buildIcon(true));
                }
            });
            tab.addEventListener('mouseleave', function () {
                var id = normalizeId(tab.getAttribute('data-center-id'));
                var marker = markersById[id];
                if (marker && id !== selectedId) {
                    marker.setIcon(buildIcon(false));
                }
            });
        });
    }

    function bindFitButton() {
        var btn = document.getElementById('mapFitBtn');
        if (btn) {
            btn.addEventListener('click', fitAll);
        }
    }

    /** 지도 핀 클릭 → 그 센터의 도면으로 (서버 렌더링이라 페이지를 다시 받는다) */
    function goToCenter(centerId) {
        if (normalizeId(centerId) === selectedId) {
            return;                                  // 이미 보고 있으면 이동하지 않는다
        }
        window.location.href = '/admin/warehouse-map?centerId=' + encodeURIComponent(centerId);
    }

    function highlight(centerId) {
        Object.keys(markersById).forEach(function (id) {
            markersById[id].setIcon(buildIcon(normalizeId(id) === normalizeId(centerId)));
        });
        selectedId = normalizeId(centerId);
    }

    /* ------------------------------------------------------------------
     * 표시
     * ------------------------------------------------------------------ */

    function buildPopup(pin) {
        var isCurrent = normalizeId(pin.centerId) === selectedId;
        var waiting = Math.max(pin.quantity - pin.storageQuantity, 0);

        return ''
            + '<div class="ff-map-popup">'
            + '  <div class="fw-bold">' + escapeHtml(pin.centerName) + '</div>'
            + '  <div class="text-muted small">' + escapeHtml(pin.region || '') + '</div>'
            + (pin.note ? '  <div class="small mt-1">' + escapeHtml(pin.note) + '</div>' : '')
            + '  <hr class="my-2">'
            + '  <div class="small">재고 <strong>' + pin.quantity.toLocaleString('ko-KR')
            + '</strong>포대 · 보관 적재율 <strong>' + pin.usageRate + '%</strong></div>'
            /*
                적재율은 보관 구역만 센다. 대기 구역이나 운송 중 재고가 있으면
                "재고 600 인데 적재율은 39%" 처럼 두 숫자가 안 맞아 보이므로
                차이를 그 자리에서 설명해 준다. 없으면 줄 자체를 만들지 않는다.
             */
            + (waiting > 0
                ? '  <div class="text-info" style="font-size:.72rem;">보관 '
                  + pin.storageQuantity.toLocaleString('ko-KR') + ' + 대기 · 운송 중 '
                  + waiting.toLocaleString('ko-KR') + '</div>'
                : '')
            + '  <div class="mt-2">'
            + (isCurrent
                ? '    <span class="badge bg-secondary">현재 보고 있는 센터</span>'
                : '    <a class="btn btn-sm btn-dark" href="/admin/warehouse-map?centerId='
                  + encodeURIComponent(pin.centerId) + '">이 센터 도면 보기</a>')
            + '  </div>'
            + '</div>';
    }

    function showError(message) {
        var box = document.getElementById('centerMapError');
        if (box) {
            box.textContent = message;
            box.classList.remove('d-none');
        }
    }

    function showMissing(message) {
        var box = document.getElementById('centerMapMissing');
        if (box) {
            box.textContent = message;
            box.classList.remove('d-none');
        }
    }

    /* ------------------------------------------------------------------
     * 유틸
     * ------------------------------------------------------------------ */

    /** data-* 속성은 문자열, JSON 은 숫자로 오므로 비교 전에 맞춘다 */
    function normalizeId(value) {
        if (value === null || value === undefined || value === '') {
            return null;
        }
        var n = Number(value);
        return isNaN(n) ? null : n;
    }

    /** 센터명 · 권역은 관리자가 입력하는 값이므로 그대로 innerHTML 에 넣지 않는다 */
    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
})();

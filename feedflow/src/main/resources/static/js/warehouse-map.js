/**
 * 창고 2D 도면 맵 - 구역 상세 모달.
 *
 * 도면 타일을 클릭하면 /api/admin/warehouse-map/bins/{binId} 를 호출해
 * 해당 구역에 보관 중인 품목 / 로트 / 수량 / 유통기한을 모달에 채운다.
 *
 * 프레임워크 없이 fetch + Bootstrap Modal 만 사용한다.
 */
(function () {
    'use strict';

    var modalEl = document.getElementById('binDetailModal');
    if (!modalEl) {
        return;
    }

    var modal = new bootstrap.Modal(modalEl);

    var el = {
        title: document.getElementById('binDetailTitle'),
        loading: document.getElementById('binDetailLoading'),
        error: document.getElementById('binDetailError'),
        errorText: document.getElementById('binDetailErrorText'),
        body: document.getElementById('binDetailBody'),
        location: document.getElementById('binDetailLocation'),
        status: document.getElementById('binDetailStatus'),
        load: document.getElementById('binDetailLoad'),
        remaining: document.getElementById('binDetailRemaining'),
        expiredAlert: document.getElementById('binDetailExpiredAlert'),
        rows: document.getElementById('binDetailRows'),
        empty: document.getElementById('binDetailEmpty'),
        inventoryLink: document.getElementById('binDetailInventoryLink'),
        moveLink: document.getElementById('binDetailMoveLink')
    };

    /** 숫자 천단위 구분 */
    function comma(value) {
        return Number(value || 0).toLocaleString('ko-KR');
    }

    /** XSS 방지용 텍스트 노드 생성 */
    function cell(text, className) {
        var td = document.createElement('td');
        td.textContent = text;
        if (className) {
            td.className = className;
        }
        return td;
    }

    function badgeCell(text, badgeClass) {
        var td = document.createElement('td');
        td.className = 'text-center';
        var span = document.createElement('span');
        span.className = 'badge ' + (badgeClass || 'bg-light text-dark border');
        span.textContent = text;
        td.appendChild(span);
        return td;
    }

    function showLoading() {
        el.loading.classList.remove('d-none');
        el.body.classList.add('d-none');
        el.error.classList.add('d-none');
    }

    function showError(message) {
        el.loading.classList.add('d-none');
        el.body.classList.add('d-none');
        el.error.classList.remove('d-none');
        el.errorText.textContent = message;
    }

    /** 응답으로 모달 내용을 채운다 */
    function render(detail) {
        var bin = detail.bin;

        el.title.textContent = bin.binCode + ' 구역 상세';
        el.location.textContent = bin.locationLabel;

        el.status.textContent = bin.statusLabel;
        el.status.className = 'badge ' + bin.statusBadgeClass;

        el.load.textContent = comma(bin.loadedQuantity) + ' / ' + comma(bin.maxCapacity)
            + ' (' + bin.usageRate + '%)';
        el.remaining.textContent = comma(bin.remainingCapacity) + ' 포대';

        el.inventoryLink.setAttribute('href', '/admin/inventory?binId=' + bin.binId);
        el.moveLink.setAttribute('href', '/admin/inventory/move?binId=' + bin.binId);

        // 재고 목록
        el.rows.innerHTML = '';
        var inventories = detail.inventories || [];

        // 옮길 재고가 없는 구역에서는 이동 버튼을 감춘다.
        // (사용 중지 구역이어도 재고가 있으면 빼낼 수 있어야 하므로 active 는 따지지 않는다)
        el.moveLink.classList.toggle('d-none', inventories.length === 0);

        inventories.forEach(function (inv) {
            var tr = document.createElement('tr');
            if (inv.expired) {
                tr.className = 'table-warning';
            }

            // 품목 : 코드 + 이름
            var productTd = document.createElement('td');
            var code = document.createElement('span');
            code.className = 'ff-code me-1';
            code.textContent = inv.productCode;
            productTd.appendChild(code);
            productTd.appendChild(document.createTextNode(inv.productName));
            tr.appendChild(productTd);

            tr.appendChild(cell(inv.lotNo, 'small text-muted'));
            tr.appendChild(badgeCell(inv.dDayLabel, inv.dDayBadgeClass));
            tr.appendChild(cell(comma(inv.quantity), 'text-end fw-semibold'));

            el.rows.appendChild(tr);
        });

        el.empty.classList.toggle('d-none', inventories.length > 0);
        el.expiredAlert.classList.toggle('d-none', !detail.hasExpired);

        el.loading.classList.add('d-none');
        el.error.classList.add('d-none');
        el.body.classList.remove('d-none');
    }

    function openDetail(binId, binCode) {
        el.title.textContent = binCode + ' 구역 상세';
        showLoading();
        modal.show();

        fetch('/api/admin/warehouse-map/bins/' + encodeURIComponent(binId), {
            headers: { 'Accept': 'application/json' }
        })
            .then(function (response) {
                if (response.status === 401 || response.status === 403) {
                    throw new Error('조회 권한이 없습니다. 다시 로그인해 주세요.');
                }
                if (response.status === 404) {
                    throw new Error('존재하지 않는 구역입니다.');
                }
                if (!response.ok) {
                    throw new Error('상세 정보를 불러오지 못했습니다. (' + response.status + ')');
                }
                return response.json();
            })
            .then(render)
            .catch(function (error) {
                showError(error.message || '상세 정보를 불러오지 못했습니다.');
            });
    }

    // 타일 클릭 (이벤트 위임 - 타일이 여러 구역에 흩어져 있으므로)
    document.addEventListener('click', function (event) {
        var tile = event.target.closest('.ff-bin-tile');
        if (!tile) {
            return;
        }
        // 사용 중지 구역은 상세를 열지 않는다
        if (tile.classList.contains('ff-bin-inactive')) {
            return;
        }

        var binId = tile.getAttribute('data-bin-id');
        var binCode = tile.getAttribute('data-bin-code');
        if (binId) {
            openDetail(binId, binCode);
        }
    });
})();

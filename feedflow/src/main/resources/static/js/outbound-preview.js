/*
 * FeedFlow - 직접 출고 화면의 FEFO 할당 미리보기
 *
 *  품목/수량을 선택하면 GET /api/admin/outbound/preview 를 호출해서
 *  "실제 출고 가능 재고"와 "어느 로트에서 몇 개가 빠지는지"를 미리 보여준다.
 *
 *  품목의 전체 재고(totalStock)와 출고 가능 재고는 다를 수 있다.
 *   · 유통기한이 지난 로트는 출고 대상에서 제외
 *   · 사용 중지된 구역의 재고도 제외
 *   · 입고 대기 · 검수 구역의 재고도 제외 (검수를 통과하지 않았다)
 *   · 운송 중(센터 간 이관) 재고도 제외 (트럭 위에 있어 집어올 수 없다)
 *
 *  부족 안내에 이 사유를 모두 적어야 한다. 유통기한만 적으면 담당자가
 *  있지도 않은 만료 로트를 찾게 된다.
 */
(function () {
    'use strict';

    // 공통 유틸 (ff-utils.js) 재사용
    var escapeHtml = window.FFUtils.escapeHtml;
    var number = window.FFUtils.number;
    var dDayBadge = window.FFUtils.dDayBadge;

    var PREVIEW_API = '/api/admin/outbound/preview';

    var productSelect = document.getElementById('productId');
    var quantityInput = document.getElementById('quantity');
    var previewBox = document.getElementById('outboundPreview');

    if (!productSelect || !quantityInput || !previewBox) {
        return;
    }

    var timer = null;




    function clear(message) {
        previewBox.innerHTML = '<p class="text-muted small mb-0">'
            + escapeHtml(message || '품목과 수량을 입력하면 출고 가능 재고와 차감 예정 로트가 표시됩니다.')
            + '</p>';
    }

    function render(plan) {
        var html = '';

        /* 요약 : 전체 재고와 출고 가능 재고를 나란히 보여준다 */
        html += '<div class="row g-2 text-center mb-3">';
        html += '  <div class="col-4"><div class="border rounded py-2">'
            + '<div class="text-muted" style="font-size:.72rem;">출고 가능 재고</div>'
            + '<div class="fw-bold fs-5">' + number(plan.availableQuantity) + '</div></div></div>';
        html += '  <div class="col-4"><div class="border rounded py-2">'
            + '<div class="text-muted" style="font-size:.72rem;">요청 수량</div>'
            + '<div class="fw-bold fs-5">' + number(plan.requestedQuantity) + '</div></div></div>';
        html += '  <div class="col-4"><div class="border rounded py-2">'
            + '<div class="text-muted" style="font-size:.72rem;">사용 로트</div>'
            + '<div class="fw-bold fs-5">' + number(plan.usedLotCount) + '</div></div></div>';
        html += '</div>';

        if (plan.requestedQuantity > 0 && !plan.fulfillable) {
            html += '<div class="alert alert-danger py-2 small">'
                + '<i class="bi bi-exclamation-triangle-fill me-1"></i>'
                + '출고 가능 재고가 <strong>' + number(plan.shortage) + '개</strong> 부족합니다. '
                + '(요청 ' + number(plan.requestedQuantity)
                + ' / 가능 ' + number(plan.availableQuantity) + ')<br>'
                + '<span class="text-muted">유통기한 경과 로트, 사용 중지 구역, '
                + '입고 대기 · 검수 구역(검수 전), 운송 중 재고는 출고 대상에서 제외되므로 '
                + '품목 목록의 전체 재고보다 적을 수 있습니다.<br>'
                + '입고 대기 구역의 재고는 <strong>구역 간 이동</strong>으로 보관 구역에 '
                + '넣어야 출고할 수 있습니다.</span>'
                + '</div>';
        } else if (plan.requestedQuantity > 0) {
            html += '<div class="alert alert-success py-2 small mb-3">'
                + '<i class="bi bi-check-circle-fill me-1"></i>'
                + '출고 가능합니다. 아래 순서대로 차감됩니다.</div>';
        }

        if (plan.lines && plan.lines.length > 0) {
            html += '<div class="table-responsive"><table class="table table-sm align-middle mb-0">';
            html += '  <thead class="table-light"><tr>'
                + '<th class="text-center" style="width:55px;">순서</th>'
                + '<th>로트번호</th>'
                + '<th class="text-center" style="width:85px;">잔여</th>'
                + '<th style="width:95px;">구역</th>'
                + '<th class="text-end" style="width:85px;">차감</th>'
                + '<th class="text-end" style="width:110px;">구역 잔여</th>'
                + '</tr></thead><tbody>';

            plan.lines.forEach(function (line) {
                var badge = dDayBadge(line.remainingDays);
                html += '<tr>';
                html += '  <td class="text-center"><span class="badge bg-secondary">'
                    + line.sequence + '</span></td>';
                html += '  <td><span class="ff-code small">' + escapeHtml(line.lotNo) + '</span>'
                    + '<div class="text-muted" style="font-size:.72rem;">'
                    + escapeHtml(line.expirationDate) + '</div></td>';
                html += '  <td class="text-center"><span class="badge ' + badge.cls + '">'
                    + badge.label + '</span></td>';
                html += '  <td><span class="ff-code">' + escapeHtml(line.binCode) + '</span></td>';
                html += '  <td class="text-end fw-bold text-danger">-'
                    + number(line.allocatedQuantity) + '</td>';
                html += '  <td class="text-end small text-muted">'
                    + number(line.binQuantityBefore) + ' &rarr; '
                    + '<span class="fw-semibold">' + number(line.binQuantityAfter) + '</span></td>';
                html += '</tr>';
            });

            html += '  </tbody></table></div>';
        } else if (plan.availableQuantity === 0) {
            html += '<p class="text-muted small mb-0">'
                + '출고 가능한 재고가 없습니다. 입고 등록을 먼저 진행하세요.</p>';
        }

        previewBox.innerHTML = html;
    }

    function loadPreview() {
        var productId = productSelect.value;
        if (!productId) {
            clear();
            return;
        }

        var quantity = parseInt(quantityInput.value, 10);
        if (isNaN(quantity) || quantity < 0) {
            quantity = 0;
        }

        fetch(PREVIEW_API + '?productId=' + encodeURIComponent(productId)
            + '&quantity=' + quantity, {headers: {'Accept': 'application/json'}})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('미리보기 조회 실패 (HTTP ' + response.status + ')');
                }
                return response.json();
            })
            .then(render)
            .catch(function (error) {
                clear(error.message);
            });
    }

    function schedulePreview() {
        if (timer) {
            clearTimeout(timer);
        }
        timer = setTimeout(loadPreview, 250);
    }

    productSelect.addEventListener('change', loadPreview);
    quantityInput.addEventListener('input', schedulePreview);

    // 검증 오류로 값이 유지된 채 다시 렌더링된 경우를 위해 초기 1회 조회
    if (productSelect.value) {
        loadPreview();
    } else {
        clear();
    }
})();

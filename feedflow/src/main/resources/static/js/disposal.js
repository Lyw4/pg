/*
 * FeedFlow - 재고 폐기 모달
 *
 *  목록의 [폐기] 버튼에 담긴 data-* 속성을 읽어 모달을 채운다.
 *  폐기 수량은 보관 수량을 초과할 수 없도록 max 를 설정하고,
 *  '유통기한 경과' 재고는 사유를 자동으로 선택해 준다.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var modal = document.getElementById('disposalModal');
        if (!modal) {
            return;   // 조회 권한만 있는 사용자는 모달이 렌더링되지 않는다
        }

        var fields = {
            inventoryId: document.getElementById('dmInventoryId'),
            product: document.getElementById('dmProduct'),
            lot: document.getElementById('dmLot'),
            bin: document.getElementById('dmBin'),
            stored: document.getElementById('dmStored'),
            expiredBadge: document.getElementById('dmExpiredBadge'),
            quantity: document.getElementById('dmQuantity'),
            reason: document.getElementById('dmReason'),
            memo: document.getElementById('dmMemo'),
            allBtn: document.getElementById('dmAllBtn')
        };

        var storedQuantity = 0;

        modal.addEventListener('show.bs.modal', function (event) {
            var button = event.relatedTarget;
            if (!button) {
                return;
            }

            storedQuantity = parseInt(button.getAttribute('data-quantity'), 10) || 0;
            var expired = button.getAttribute('data-expired') === 'true';

            fields.inventoryId.value = button.getAttribute('data-inventory-id') || '';
            fields.product.textContent = button.getAttribute('data-product') || '-';
            fields.lot.textContent = button.getAttribute('data-lot') || '-';
            fields.bin.textContent = button.getAttribute('data-bin') || '-';
            fields.stored.textContent = storedQuantity.toLocaleString('ko-KR');

            fields.expiredBadge.classList.toggle('d-none', !expired);

            fields.quantity.max = storedQuantity;
            fields.quantity.value = storedQuantity;   // 기본값은 전량 폐기
            fields.memo.value = '';

            // 유통기한이 지난 재고는 사유를 자동 선택
            fields.reason.value = expired ? 'EXPIRED' : 'DAMAGED';
        });

        // 보관 수량 초과 입력 방지
        fields.quantity.addEventListener('input', function () {
            var value = parseInt(fields.quantity.value, 10);
            if (!isNaN(value) && storedQuantity > 0 && value > storedQuantity) {
                fields.quantity.value = storedQuantity;
            }
        });

        if (fields.allBtn) {
            fields.allBtn.addEventListener('click', function () {
                fields.quantity.value = storedQuantity;
            });
        }
    });
})();

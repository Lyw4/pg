/*
 * FeedFlow - QR 라벨 렌더링
 *
 *  data-qr 속성에 담긴 코드(로트번호/품목코드)를 QR 코드 SVG 로 그린다.
 *  스캔에 사용하는 @zxing/library 의 Writer 를 그대로 사용하므로 추가 라이브러리가 필요 없다.
 */
(function () {
    'use strict';

    var QR_SIZE = 132;

    document.addEventListener('DOMContentLoaded', function () {
        var targets = document.querySelectorAll('[data-qr]');
        if (targets.length === 0) {
            return;
        }

        var writer = null;
        if (typeof window.ZXing !== 'undefined' && window.ZXing.BrowserQRCodeSvgWriter) {
            try {
                writer = new window.ZXing.BrowserQRCodeSvgWriter();
            } catch (error) {
                writer = null;
            }
        }

        Array.prototype.forEach.call(targets, function (element) {
            var code = element.getAttribute('data-qr');
            if (!code) {
                return;
            }

            if (!writer) {
                // 라이브러리를 못 불러온 경우 : 코드 문자열만이라도 보여준다
                element.classList.add('ff-label-qr-fallback');
                element.textContent = code;
                return;
            }

            try {
                element.innerHTML = '';
                writer.writeToDom(element, code, QR_SIZE, QR_SIZE);
            } catch (error) {
                element.classList.add('ff-label-qr-fallback');
                element.textContent = code;
            }
        });
    });
})();

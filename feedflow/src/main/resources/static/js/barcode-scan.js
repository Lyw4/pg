/*
 * FeedFlow - 현장 작업자용 바코드 / QR 스캔
 *
 *  · ZXing-js (HTML5 getUserMedia) 로 카메라 영상에서 코드를 디코딩한다.
 *  · 인식된 코드를 GET /api/admin/scan?code=... 로 비동기 조회하고 결과를 렌더링한다.
 *  · Native App 없이 브라우저에서만 동작한다.
 */
(function () {
    'use strict';

    var SCAN_API = '/api/admin/scan';
    var DUPLICATE_IGNORE_MS = 2500;   // 같은 코드 연속 인식 무시 시간
    var MAX_RECENT = 8;

    var elements = {
        preview: document.getElementById('scanPreview'),
        idle: document.getElementById('scannerIdle'),
        status: document.getElementById('scanStatus'),
        cameraSelect: document.getElementById('cameraSelect'),
        startBtn: document.getElementById('startScanBtn'),
        stopBtn: document.getElementById('stopScanBtn'),
        message: document.getElementById('scannerMessage'),
        result: document.getElementById('scanResult'),
        recent: document.getElementById('recentScans'),
        manualForm: document.getElementById('manualForm'),
        manualCode: document.getElementById('manualCode')
    };

    var state = {
        reader: null,
        scanning: false,
        lastCode: null,
        lastAt: 0,
        recent: []
    };

    /* ------------------------------------------------------------------
     * 유틸
     * ------------------------------------------------------------------ */

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function number(value) {
        if (value === null || value === undefined) {
            return '-';
        }
        return Number(value).toLocaleString('ko-KR');
    }

    function setStatus(text, badgeClass) {
        elements.status.textContent = text;
        elements.status.className = 'badge ' + badgeClass;
    }

    function showMessage(text, type) {
        if (!text) {
            elements.message.classList.add('d-none');
            return;
        }
        elements.message.className = 'alert alert-' + (type || 'warning') + ' mt-3 mb-0 small';
        elements.message.textContent = text;
    }

    function dDayBadge(remainingDays) {
        if (remainingDays === null || remainingDays === undefined) {
            return {label: '-', cls: 'bg-light text-dark border'};
        }
        if (remainingDays < 0) {
            return {label: '만료 ' + Math.abs(remainingDays) + '일 경과', cls: 'bg-dark'};
        }
        if (remainingDays <= 7) {
            return {label: 'D-' + remainingDays, cls: 'bg-danger'};
        }
        if (remainingDays <= 30) {
            return {label: 'D-' + remainingDays, cls: 'bg-warning text-dark'};
        }
        return {label: 'D-' + remainingDays, cls: 'bg-light text-dark border'};
    }

    /* ------------------------------------------------------------------
     * 카메라
     * ------------------------------------------------------------------ */

    function isZxingAvailable() {
        return typeof window.ZXing !== 'undefined' && window.ZXing.BrowserMultiFormatReader;
    }

    function getReader() {
        if (!state.reader) {
            state.reader = new window.ZXing.BrowserMultiFormatReader();
        }
        return state.reader;
    }

    function loadCameras() {
        if (!isZxingAvailable()) {
            elements.cameraSelect.innerHTML = '<option value="">사용 불가</option>';
            elements.startBtn.disabled = true;
            showMessage('바코드 라이브러리를 불러오지 못했습니다. 인터넷 연결을 확인하거나 아래 직접 입력을 사용하세요.', 'warning');
            return;
        }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            elements.cameraSelect.innerHTML = '<option value="">카메라 사용 불가</option>';
            elements.startBtn.disabled = true;
            showMessage('이 브라우저/환경에서는 카메라를 사용할 수 없습니다. (https 또는 localhost 필요) 직접 입력을 사용하세요.', 'warning');
            return;
        }

        getReader().listVideoInputDevices()
            .then(function (devices) {
                if (!devices || devices.length === 0) {
                    elements.cameraSelect.innerHTML = '<option value="">연결된 카메라 없음</option>';
                    elements.startBtn.disabled = true;
                    showMessage('연결된 카메라를 찾지 못했습니다. 직접 입력을 사용하세요.', 'warning');
                    return;
                }

                var html = '';
                devices.forEach(function (device, index) {
                    var label = device.label || ('카메라 ' + (index + 1));
                    html += '<option value="' + escapeHtml(device.deviceId) + '">'
                        + escapeHtml(label) + '</option>';
                });
                elements.cameraSelect.innerHTML = html;

                // 후면 카메라(back/rear/environment)가 있으면 기본 선택 - 모바일 현장 작업 고려
                for (var i = 0; i < devices.length; i++) {
                    var name = (devices[i].label || '').toLowerCase();
                    if (name.indexOf('back') >= 0 || name.indexOf('rear') >= 0
                        || name.indexOf('environment') >= 0 || name.indexOf('후면') >= 0) {
                        elements.cameraSelect.value = devices[i].deviceId;
                        break;
                    }
                }
                showMessage(null);
            })
            .catch(function (error) {
                elements.cameraSelect.innerHTML = '<option value="">카메라 조회 실패</option>';
                showMessage('카메라 목록을 가져오지 못했습니다: ' + error.message, 'danger');
            });
    }

    function startScan() {
        if (!isZxingAvailable()) {
            return;
        }

        var deviceId = elements.cameraSelect.value || null;

        getReader().decodeFromVideoDevice(deviceId, elements.preview, function (result, error) {
            if (result) {
                handleCode(result.getText());
            }
            // error 는 프레임마다 NotFoundException 이 발생하므로 무시한다
        }).then(function () {
            state.scanning = true;
            elements.startBtn.disabled = true;
            elements.stopBtn.disabled = false;
            elements.idle.classList.add('d-none');
            setStatus('스캔 중', 'bg-success');
            showMessage(null);
        }).catch(function (error) {
            setStatus('오류', 'bg-danger');
            showMessage('카메라를 시작할 수 없습니다: ' + error.message
                + ' (권한을 허용했는지, https/localhost 접속인지 확인하세요)', 'danger');
        });
    }

    function stopScan() {
        if (state.reader) {
            state.reader.reset();
        }
        state.scanning = false;
        elements.startBtn.disabled = false;
        elements.stopBtn.disabled = true;
        elements.idle.classList.remove('d-none');
        setStatus('정지', 'bg-secondary');
    }

    /* ------------------------------------------------------------------
     * 코드 처리 및 API 조회
     * ------------------------------------------------------------------ */

    function handleCode(code) {
        var now = Date.now();
        if (code === state.lastCode && (now - state.lastAt) < DUPLICATE_IGNORE_MS) {
            return;   // 같은 코드 연속 인식 방지
        }
        state.lastCode = code;
        state.lastAt = now;

        lookup(code);
    }

    function lookup(code) {
        var trimmed = (code || '').trim();
        if (!trimmed) {
            return;
        }

        setStatus('조회 중', 'bg-primary');

        fetch(SCAN_API + '?code=' + encodeURIComponent(trimmed), {
            headers: {'Accept': 'application/json'}
        })
            .then(function (response) {
                return response.json()
                    .catch(function () {
                        return {};
                    })
                    .then(function (body) {
                        return {ok: response.ok, status: response.status, body: body};
                    });
            })
            .then(function (payload) {
                if (payload.ok) {
                    renderResult(payload.body);
                    addRecent(trimmed, true);
                    setStatus(state.scanning ? '스캔 중' : '조회 완료', state.scanning ? 'bg-success' : 'bg-secondary');
                } else {
                    var message = payload.body && payload.body.message
                        ? payload.body.message
                        : ('조회 실패 (HTTP ' + payload.status + ')');
                    renderError(trimmed, payload.status, message);
                    addRecent(trimmed, false);
                    setStatus(state.scanning ? '스캔 중' : '대기', state.scanning ? 'bg-success' : 'bg-secondary');
                }
            })
            .catch(function (error) {
                renderError(trimmed, 0, '네트워크 오류: ' + error.message);
                setStatus('오류', 'bg-danger');
            });
    }

    /* ------------------------------------------------------------------
     * 렌더링
     * ------------------------------------------------------------------ */

    function renderError(code, status, message) {
        elements.result.innerHTML =
            '<div class="card ff-card border-danger">'
            + '  <div class="card-body">'
            + '    <div class="d-flex align-items-center mb-2">'
            + '      <i class="bi bi-exclamation-octagon-fill text-danger fs-4 me-2"></i>'
            + '      <div>'
            + '        <div class="fw-bold text-danger">조회 실패'
            + (status ? ' (' + status + ')' : '') + '</div>'
            + '        <div class="ff-code small">' + escapeHtml(code) + '</div>'
            + '      </div>'
            + '    </div>'
            + '    <p class="mb-0 small text-muted">' + escapeHtml(message) + '</p>'
            + '  </div>'
            + '</div>';
    }

    function renderResult(data) {
        var product = data.product || {};
        var lot = data.lot;
        var stocks = data.stocks || [];
        var isLot = data.scanType === 'LOT';

        var html = '';

        /* 헤더 */
        html += '<div class="card ff-card border-success mb-3">';
        html += '  <div class="card-body">';
        html += '    <div class="d-flex justify-content-between align-items-start mb-2">';
        html += '      <div>';
        html += '        <span class="badge ' + (isLot ? 'bg-primary' : 'bg-info text-dark') + '">'
            + (isLot ? '로트번호 인식' : '품목코드 인식') + '</span>';
        html += '        <div class="ff-code fs-5 fw-bold mt-1">' + escapeHtml(data.code) + '</div>';
        html += '      </div>';
        html += '      <i class="bi bi-check-circle-fill text-success fs-3"></i>';
        html += '    </div>';

        html += '    <div class="fw-bold">' + escapeHtml(product.name) + '</div>';
        html += '    <div class="small text-muted mb-3">'
            + '<span class="ff-code">' + escapeHtml(product.productCode) + '</span>'
            + ' · ' + escapeHtml(product.animalType)
            + ' · ' + number(product.weightKg) + 'kg'
            + ' · ' + number(product.price) + '원'
            + (product.active ? '' : ' · <span class="badge bg-secondary">사용중지</span>')
            + '</div>';

        /* 재고 요약 */
        html += '    <div class="row g-2 text-center">';
        html += '      <div class="col-4"><div class="border rounded py-2">'
            + '<div class="text-muted" style="font-size:.72rem;">' + (isLot ? '이 로트 재고' : '전체 재고') + '</div>'
            + '<div class="fw-bold fs-5">' + number(data.totalQuantity) + '</div></div></div>';
        html += '      <div class="col-4"><div class="border rounded py-2">'
            + '<div class="text-muted" style="font-size:.72rem;">안전 재고</div>'
            + '<div class="fw-bold fs-5 ' + (product.belowSafetyStock ? 'text-danger' : '') + '">'
            + number(product.safetyStock) + '</div></div></div>';
        html += '      <div class="col-4"><div class="border rounded py-2">'
            + '<div class="text-muted" style="font-size:.72rem;">보관 구역</div>'
            + '<div class="fw-bold fs-5">' + stocks.length + '</div></div></div>';
        html += '    </div>';

        if (product.belowSafetyStock) {
            html += '    <div class="alert alert-danger mt-3 mb-0 py-2 small">'
                + '<i class="bi bi-exclamation-triangle-fill me-1"></i>'
                + '안전재고 미달 품목입니다. (현재 ' + number(product.totalStock)
                + ' / 안전 ' + number(product.safetyStock) + ')</div>';
        }
        html += '  </div>';
        html += '</div>';

        /* 로트 상세 */
        if (lot) {
            var badge = dDayBadge(lot.remainingDays);
            html += '<div class="card ff-card mb-3">';
            html += '  <div class="card-header bg-white border-0 pt-3">'
                + '<span class="fw-bold"><i class="bi bi-tag me-1"></i>로트 정보</span></div>';
            html += '  <div class="card-body pt-2">';
            html += '    <table class="table table-sm mb-0">';
            html += '      <tbody>';
            html += '        <tr><th class="text-muted" style="width:120px;">로트번호</th>'
                + '<td class="ff-code">' + escapeHtml(lot.lotNo) + '</td></tr>';
            html += '        <tr><th class="text-muted">제조일자</th><td>'
                + escapeHtml(lot.manufacturedDate) + '</td></tr>';
            html += '        <tr><th class="text-muted">유통기한</th><td>'
                + escapeHtml(lot.expirationDate)
                + ' <span class="badge ' + badge.cls + ' ms-1">' + badge.label + '</span></td></tr>';
            html += '        <tr><th class="text-muted">로트 잔여</th><td class="fw-bold">'
                + number(lot.lotQuantity) + '</td></tr>';
            html += '      </tbody>';
            html += '    </table>';
            if (lot.expired) {
                html += '    <div class="alert alert-dark mt-3 mb-0 py-2 small">'
                    + '<i class="bi bi-slash-circle me-1"></i>'
                    + '유통기한이 지난 로트입니다. 출고 대상에서 제외됩니다.</div>';
            }
            html += '  </div>';
            html += '</div>';
        }

        /* 구역별 재고 */
        html += '<div class="card ff-card mb-3">';
        html += '  <div class="card-header bg-white border-0 pt-3">'
            + '<span class="fw-bold"><i class="bi bi-geo-alt me-1"></i>구역별 재고</span></div>';
        html += '  <div class="card-body pt-2">';

        if (stocks.length === 0) {
            html += '    <p class="text-muted small mb-0 py-2">보관 중인 재고가 없습니다.</p>';
        } else {
            html += '    <div class="table-responsive"><table class="table table-sm align-middle mb-0">';
            html += '      <thead class="table-light"><tr>'
                + '<th>구역</th>'
                + (isLot ? '' : '<th>로트번호</th>')
                + '<th class="text-center">유통기한</th>'
                + '<th class="text-end">수량</th>'
                + '</tr></thead><tbody>';

            stocks.forEach(function (stock) {
                var stockBadge = dDayBadge(stock.remainingDays);
                html += '<tr>';
                html += '  <td><span class="ff-code">' + escapeHtml(stock.binCode) + '</span>'
                    + '<div class="text-muted" style="font-size:.72rem;">'
                    + escapeHtml(stock.locationLabel) + '</div></td>';
                if (!isLot) {
                    html += '  <td><span class="ff-code small">' + escapeHtml(stock.lotNo) + '</span></td>';
                }
                html += '  <td class="text-center small">' + escapeHtml(stock.expirationDate)
                    + '<div><span class="badge ' + stockBadge.cls + '">' + stockBadge.label
                    + '</span></div></td>';
                html += '  <td class="text-end fw-semibold">' + number(stock.quantity) + '</td>';
                html += '</tr>';
            });

            html += '      </tbody></table></div>';
        }
        html += '  </div>';
        html += '</div>';

        /* 후속 작업 바로가기 */
        html += '<div class="card ff-card">';
        html += '  <div class="card-body d-grid gap-2 d-sm-flex">';
        html += '    <a class="btn btn-outline-primary flex-fill" href="/admin/inventory?productId='
            + encodeURIComponent(product.productId) + '">'
            + '<i class="bi bi-stack me-1"></i>재고 현황</a>';
        html += '    <a class="btn btn-outline-success flex-fill" href="/admin/inventory/inbound">'
            + '<i class="bi bi-box-arrow-in-down me-1"></i>입고 등록</a>';
        html += '    <a class="btn btn-outline-dark flex-fill" href="/admin/outbound/direct">'
            + '<i class="bi bi-box-arrow-up me-1"></i>출고 처리</a>';
        html += '  </div>';
        html += '</div>';

        elements.result.innerHTML = html;
    }

    function addRecent(code, success) {
        state.recent.unshift({
            code: code,
            success: success,
            at: new Date()
        });
        if (state.recent.length > MAX_RECENT) {
            state.recent.pop();
        }

        var html = '';
        state.recent.forEach(function (entry) {
            var time = entry.at.toTimeString().substring(0, 8);
            html += '<li class="list-group-item d-flex justify-content-between align-items-center px-0 py-2">'
                + '<span class="ff-code small">' + escapeHtml(entry.code) + '</span>'
                + '<span>'
                + '<span class="badge ' + (entry.success ? 'bg-success' : 'bg-danger') + ' me-2">'
                + (entry.success ? '성공' : '실패') + '</span>'
                + '<span class="text-muted" style="font-size:.72rem;">' + time + '</span>'
                + '</span>'
                + '</li>';
        });
        elements.recent.innerHTML = html;
    }

    /* ------------------------------------------------------------------
     * 초기화
     * ------------------------------------------------------------------ */

    document.addEventListener('DOMContentLoaded', function () {
        loadCameras();

        elements.startBtn.addEventListener('click', startScan);
        elements.stopBtn.addEventListener('click', stopScan);

        elements.manualForm.addEventListener('submit', function (event) {
            event.preventDefault();
            var code = elements.manualCode.value;
            lookup(code);
            elements.manualCode.select();
        });

        // 샘플 코드 버튼 (카메라 없이 동작 확인용)
        var sampleButtons = document.querySelectorAll('.ff-sample-code');
        Array.prototype.forEach.call(sampleButtons, function (button) {
            button.addEventListener('click', function () {
                var code = button.getAttribute('data-code');
                elements.manualCode.value = code;
                lookup(code);
            });
        });

        // 페이지 이탈 시 카메라 자원 해제
        window.addEventListener('beforeunload', function () {
            if (state.reader) {
                state.reader.reset();
            }
        });
    });
})();

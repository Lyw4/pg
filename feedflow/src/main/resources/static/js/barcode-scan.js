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

    var quick = {
        panel: document.getElementById('quickActions'),
        codeLabel: document.getElementById('qaCodeLabel'),
        message: document.getElementById('qaMessage'),
        binId: document.getElementById('qaBinId'),
        mfgRow: document.getElementById('qaMfgRow'),
        lotNoticeRow: document.getElementById('qaLotNoticeRow'),
        manufacturedDate: document.getElementById('qaManufacturedDate'),
        inQuantity: document.getElementById('qaInQuantity'),
        inMemo: document.getElementById('qaInMemo'),
        inboundBtn: document.getElementById('qaInboundBtn'),
        outQuantity: document.getElementById('qaOutQuantity'),
        outMemo: document.getElementById('qaOutMemo'),
        outboundBtn: document.getElementById('qaOutboundBtn')
    };

    var state = {
        reader: null,
        scanning: false,
        lastCode: null,
        lastAt: 0,
        recent: [],
        currentCode: null,
        currentScanType: null
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
                    showQuickActions(payload.body);
                    addRecent(trimmed, true);
                    setStatus(state.scanning ? '스캔 중' : '조회 완료', state.scanning ? 'bg-success' : 'bg-secondary');
                } else {
                    var message = payload.body && payload.body.message
                        ? payload.body.message
                        : ('조회 실패 (HTTP ' + payload.status + ')');
                    renderError(trimmed, payload.status, message);
                    hideQuickActions();
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
     * 스캔 즉시 입출고
     * ------------------------------------------------------------------ */

    function hideQuickActions() {
        if (quick.panel) {
            quick.panel.classList.add('d-none');
        }
        state.currentCode = null;
        state.currentScanType = null;
    }

    function showQuickActions(data) {
        if (!quick.panel) {
            return;
        }

        // 다른 코드를 스캔했을 때만 이전 처리 결과 메시지를 지운다
        // (입출고 처리 후 재조회로 갱신되는 경우에는 결과 메시지를 유지)
        var codeChanged = (state.currentCode !== data.code);

        state.currentCode = data.code;
        state.currentScanType = data.scanType;

        quick.codeLabel.textContent = data.code;
        quick.panel.classList.remove('d-none');

        if (codeChanged) {
            setQuickMessage(null);
        }

        // 품목코드를 스캔하면 새 로트를 만들어야 하므로 제조일자를 입력받는다
        var isProduct = (data.scanType === 'PRODUCT');
        quick.mfgRow.classList.toggle('d-none', !isProduct);
        quick.lotNoticeRow.classList.toggle('d-none', isProduct);
    }

    function setQuickMessage(html, type) {
        if (!quick.message) {
            return;
        }
        if (!html) {
            quick.message.classList.add('d-none');
            quick.message.innerHTML = '';
            return;
        }
        quick.message.className = 'alert alert-' + (type || 'success') + ' py-2 small';
        quick.message.innerHTML = html;
    }

    function csrfHeaders() {
        var headers = {'Content-Type': 'application/json', 'Accept': 'application/json'};
        var token = document.querySelector('meta[name="_csrf"]');
        var headerName = document.querySelector('meta[name="_csrf_header"]');
        if (token && headerName && token.content && headerName.content) {
            headers[headerName.content] = token.content;
        }
        return headers;
    }

    function positiveQuantity(input) {
        var value = parseInt(input.value, 10);
        if (isNaN(value) || value < 1) {
            return null;
        }
        return value;
    }

    function submitAction(url, body, button, onSuccess) {
        button.disabled = true;

        fetch(url, {
            method: 'POST',
            headers: csrfHeaders(),
            body: JSON.stringify(body)
        })
            .then(function (response) {
                return response.json()
                    .catch(function () {
                        return {};
                    })
                    .then(function (json) {
                        return {ok: response.ok, status: response.status, body: json};
                    });
            })
            .then(function (payload) {
                if (payload.ok) {
                    onSuccess(payload.body);
                    // 처리 후 최신 재고로 화면 갱신
                    lookup(state.currentCode);
                } else {
                    var message = payload.body && payload.body.message
                        ? payload.body.message
                        : ('처리 실패 (HTTP ' + payload.status + ')');
                    setQuickMessage('<i class="bi bi-exclamation-triangle-fill me-1"></i>'
                        + escapeHtml(message), 'danger');
                }
            })
            .catch(function (error) {
                setQuickMessage('<i class="bi bi-exclamation-triangle-fill me-1"></i>네트워크 오류: '
                    + escapeHtml(error.message), 'danger');
            })
            .finally(function () {
                button.disabled = false;
            });
    }

    function doInbound() {
        if (!state.currentCode) {
            return;
        }
        if (!quick.binId.value) {
            setQuickMessage('입고할 구역을 선택하세요.', 'warning');
            return;
        }

        var quantity = positiveQuantity(quick.inQuantity);
        if (quantity === null) {
            setQuickMessage('입고 수량을 1 이상으로 입력하세요.', 'warning');
            return;
        }

        var body = {
            code: state.currentCode,
            binId: Number(quick.binId.value),
            quantity: quantity,
            memo: quick.inMemo.value
        };
        if (state.currentScanType === 'PRODUCT' && quick.manufacturedDate.value) {
            body.manufacturedDate = quick.manufacturedDate.value;
        }

        submitAction('/api/admin/scan/inbound', body, quick.inboundBtn, function (result) {
            var html = '<i class="bi bi-check-circle-fill me-1"></i>'
                + '<strong>입고 완료</strong><br>'
                + '로트 <span class="ff-code">' + escapeHtml(result.lotNo) + '</span>'
                + (result.newLot ? ' (신규 로트)' : ' (기존 로트 합산)')
                + ' · 구역 ' + escapeHtml(result.binCode)
                + ' · +' + number(result.quantity)
                + '<br>유통기한 ' + escapeHtml(result.expirationDate)
                + ' · 구역 보관 ' + number(result.binQuantity)
                + ' · 품목 전체 재고 ' + number(result.productTotalStock);

            if (result.expiredLot) {
                html += '<br><span class="text-danger fw-semibold">'
                    + '주의: 이 로트는 이미 유통기한이 지나 출고 대상에서 제외됩니다.</span>';
            }

            setQuickMessage(html, 'success');
            quick.inQuantity.value = '';
        });
    }

    function doOutbound() {
        if (!state.currentCode) {
            return;
        }

        var quantity = positiveQuantity(quick.outQuantity);
        if (quantity === null) {
            setQuickMessage('출고 수량을 1 이상으로 입력하세요.', 'warning');
            return;
        }

        var body = {
            code: state.currentCode,
            quantity: quantity,
            memo: quick.outMemo.value
        };

        submitAction('/api/admin/scan/outbound', body, quick.outboundBtn, function (result) {
            var html = '<i class="bi bi-check-circle-fill me-1"></i>'
                + '<strong>출고 완료</strong> (선입선출 FEFO)<br>'
                + escapeHtml(result.productCode) + ' · -' + number(result.quantity)
                + ' · 품목 잔여 재고 ' + number(result.productTotalStock)
                + '<br><span class="text-muted">차감된 로트</span><ul class="mb-0 ps-3">';

            (result.lines || []).forEach(function (line) {
                html += '<li><span class="ff-code">' + escapeHtml(line.lotNo) + '</span>'
                    + ' (' + escapeHtml(line.binCode) + ', D-' + line.remainingDays + ')'
                    + ' -' + number(line.allocatedQuantity)
                    + (line.depleted ? ' <span class="badge bg-dark">전량 소진</span>' : '')
                    + '</li>';
            });
            html += '</ul>';

            setQuickMessage(html, 'success');
            quick.outQuantity.value = '';
        });
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

        // 스캔 즉시 입출고 버튼
        if (quick.inboundBtn) {
            quick.inboundBtn.addEventListener('click', doInbound);
        }
        if (quick.outboundBtn) {
            quick.outboundBtn.addEventListener('click', doOutbound);
        }

        // 페이지 이탈 시 카메라 자원 해제
        window.addEventListener('beforeunload', function () {
            if (state.reader) {
                state.reader.reset();
            }
        });
    });
})();

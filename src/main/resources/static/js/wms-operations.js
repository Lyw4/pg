(function () {
    "use strict";

    const inboundForm = document.getElementById("wmsInboundForm");
    if (inboundForm) {
        const existingPanel = inboundForm.querySelector("[data-inbound-existing]");
        const newPanel = inboundForm.querySelector("[data-inbound-new]");
        const existingSelect = inboundForm.querySelector("[name='existingLotId']");
        const binSelect = inboundForm.querySelector("[name='binId']");
        const quantityInput = inboundForm.querySelector("[name='quantity']");
        const binHint = inboundForm.querySelector("[data-inbound-bin-hint]");
        const newFields = Array.from(newPanel.querySelectorAll("input, select"));

        const refreshInboundMode = function () {
            const mode = inboundForm.querySelector("[name='inboundMode']:checked").value;
            const isNew = mode === "new";
            existingPanel.classList.toggle("d-none", isNew);
            newPanel.classList.toggle("d-none", !isNew);
            existingSelect.disabled = isNew;
            existingSelect.required = !isNew;
            newFields.forEach(function (field) {
                field.disabled = !isNew;
                field.required = isNew;
            });
            if (!isNew) {
                const selected = existingSelect.options[existingSelect.selectedIndex];
                const preferredBin = selected && selected.dataset.preferredBin;
                const preferredOption = preferredBin
                    ? binSelect.querySelector("option[value='" + preferredBin + "']")
                    : null;
                const quantity = Number(quantityInput.value || 0);
                const preferredHasCapacity = preferredOption
                    && Number(preferredOption.dataset.currentQuantity || 0) + quantity
                        <= Number(preferredOption.dataset.maxCapacity || 0);
                if (preferredHasCapacity) {
                    binSelect.value = preferredBin;
                    binHint.textContent = "기존 LOT가 보관 중인 구역을 자동 선택했습니다.";
                } else {
                    const available = Array.from(binSelect.options).find(function (option) {
                        return option.value && Number(option.dataset.currentQuantity || 0) + quantity
                            <= Number(option.dataset.maxCapacity || 0);
                    });
                    if (available) {
                        binSelect.value = available.value;
                        binHint.textContent = "기존 구역의 용량이 부족해 여유 구역을 자동 선택했습니다.";
                    } else {
                        binHint.textContent = "입고 가능한 여유 구역이 없어 직접 확인이 필요합니다.";
                    }
                }
            } else {
                binHint.textContent = "신규 LOT는 입고할 구역을 선택하세요.";
            }
        };

        inboundForm.querySelectorAll("[name='inboundMode']")
            .forEach(function (radio) {
                radio.addEventListener("change", refreshInboundMode);
            });
        existingSelect.addEventListener("change", refreshInboundMode);
        quantityInput.addEventListener("input", refreshInboundMode);
        refreshInboundMode();
    }

    const bulkInboundForm = document.getElementById("wmsBulkInboundForm");
    if (bulkInboundForm) {
        const selectAll = bulkInboundForm.querySelector("[data-bulk-select-all]");
        const targets = Array.from(bulkInboundForm.querySelectorAll("[data-bulk-target]"));
        const submit = bulkInboundForm.querySelector("[data-bulk-submit]");
        const count = bulkInboundForm.querySelector("[data-bulk-selected-count]");
        const refreshBulkSelection = function () {
            const selected = targets.filter(function (target) { return target.checked; }).length;
            count.textContent = selected + "건 선택";
            submit.disabled = selected === 0;
            selectAll.checked = selected > 0 && selected === targets.length;
            selectAll.indeterminate = selected > 0 && selected < targets.length;
        };
        selectAll.addEventListener("change", function () {
            targets.forEach(function (target) { target.checked = selectAll.checked; });
            refreshBulkSelection();
        });
        targets.forEach(function (target) {
            target.addEventListener("change", refreshBulkSelection);
        });
        refreshBulkSelection();
    }

    const scanner = document.getElementById("wmsScanner");
    if (scanner) {
        const video = document.getElementById("wmsScanVideo");
        const cameraSelect = document.getElementById("wmsCameraSelect");
        const startButton = document.getElementById("wmsScanStart");
        const stopButton = document.getElementById("wmsScanStop");
        const cameraEmpty = document.getElementById("wmsCameraEmpty");
        const cameraNotice = document.getElementById("wmsCameraNotice");
        const scanInput = document.getElementById("wmsScanInput");
        const soundOption = document.getElementById("wmsScanSound");
        const vibrateOption = document.getElementById("wmsScanVibrate");
        const scrollOption = document.getElementById("wmsScanScroll");
        const recentList = document.getElementById("wmsRecentScans");
        let cameraStream = null;
        let detector = null;
        let animationId = null;
        let detecting = false;
        let lastDetectionAt = 0;

        const showCameraNotice = function (message, danger) {
            cameraNotice.textContent = message;
            cameraNotice.classList.remove("d-none", "alert-info", "alert-danger");
            cameraNotice.classList.add(danger ? "alert-danger" : "alert-info");
        };

        const readRecent = function () {
            try {
                return JSON.parse(localStorage.getItem("feedflowRecentScans") || "[]");
            } catch (error) {
                return [];
            }
        };

        const renderRecent = function () {
            const recent = readRecent();
            recentList.innerHTML = "";
            if (recent.length === 0) {
                const empty = document.createElement("li");
                empty.className = "list-group-item text-muted";
                empty.textContent = "스캔 기록이 없습니다.";
                recentList.appendChild(empty);
                return;
            }
            recent.forEach(function (entry) {
                const item = document.createElement("li");
                item.className = "list-group-item d-flex justify-content-between align-items-center gap-3";
                const code = document.createElement("strong");
                code.className = "font-monospace";
                code.textContent = entry.code;
                const summary = document.createElement("span");
                summary.className = "ms-auto badge " + (entry.found ? "text-bg-success" : "text-bg-danger");
                summary.textContent = entry.found ? entry.type : "실패";
                const time = document.createElement("small");
                time.className = "text-muted";
                time.textContent = entry.time;
                item.append(code, summary, time);
                recentList.appendChild(item);
            });
        };

        const rememberServerResult = function () {
            const code = scanner.dataset.resultCode;
            if (!code) {
                return;
            }
            const recent = readRecent();
            const found = scanner.dataset.resultFound === "true";
            const entry = {
                code: code,
                type: scanner.dataset.resultType || "UNKNOWN",
                found: found,
                time: new Date().toLocaleTimeString("ko-KR", {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit"
                })
            };
            if (recent.length === 0 || recent[0].code !== entry.code
                    || recent[0].found !== entry.found) {
                recent.unshift(entry);
                try {
                    localStorage.setItem("feedflowRecentScans", JSON.stringify(recent.slice(0, 8)));
                } catch (error) {
                    // 저장 공간을 사용할 수 없어도 스캔 기능은 계속 동작한다.
                }
            }
        };

        const stopCamera = function () {
            if (animationId !== null) {
                cancelAnimationFrame(animationId);
                animationId = null;
            }
            if (cameraStream) {
                cameraStream.getTracks().forEach(function (track) {
                    track.stop();
                });
                cameraStream = null;
            }
            video.srcObject = null;
            cameraEmpty.classList.remove("d-none");
            startButton.disabled = cameraSelect.disabled;
            stopButton.disabled = true;
        };

        const beep = function () {
            if (!soundOption.checked) {
                return;
            }
            try {
                const AudioContext = window.AudioContext || window.webkitAudioContext;
                const context = new AudioContext();
                const oscillator = context.createOscillator();
                const gain = context.createGain();
                oscillator.frequency.value = 880;
                gain.gain.value = 0.08;
                oscillator.connect(gain);
                gain.connect(context.destination);
                oscillator.start();
                oscillator.stop(context.currentTime + 0.12);
            } catch (error) {
                // 소리를 지원하지 않는 브라우저에서는 조용히 계속 진행한다.
            }
        };

        const submitDetectedCode = function (code) {
            if (!code) {
                return;
            }
            beep();
            if (vibrateOption.checked && navigator.vibrate) {
                navigator.vibrate(120);
            }
            stopCamera();
            scanInput.value = code.trim();
            scanInput.form.requestSubmit();
        };

        const detectFrame = async function (timestamp) {
            if (!cameraStream) {
                return;
            }
            if (!detecting && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA
                    && timestamp - lastDetectionAt > 280) {
                detecting = true;
                lastDetectionAt = timestamp;
                try {
                    const results = await detector.detect(video);
                    if (results.length > 0 && results[0].rawValue) {
                        submitDetectedCode(results[0].rawValue);
                        detecting = false;
                        return;
                    }
                } catch (error) {
                    showCameraNotice("코드를 인식하는 중 오류가 발생했습니다. 직접 입력을 사용해 주세요.", true);
                } finally {
                    detecting = false;
                }
            }
            animationId = requestAnimationFrame(detectFrame);
        };

        const refreshCameras = async function () {
            const devices = await navigator.mediaDevices.enumerateDevices();
            const cameras = devices.filter(function (device) {
                return device.kind === "videoinput";
            });
            cameraSelect.innerHTML = "";
            if (cameras.length === 0) {
                const option = document.createElement("option");
                option.textContent = "카메라 사용 불가";
                cameraSelect.appendChild(option);
                cameraSelect.disabled = true;
                startButton.disabled = true;
                return;
            }
            cameras.forEach(function (camera, index) {
                const option = document.createElement("option");
                option.value = camera.deviceId;
                option.textContent = camera.label || "카메라 " + (index + 1);
                cameraSelect.appendChild(option);
            });
            cameraSelect.disabled = false;
            startButton.disabled = Boolean(cameraStream);
        };

        const cameraSupported = window.isSecureContext
                && navigator.mediaDevices
                && typeof navigator.mediaDevices.getUserMedia === "function"
                && "BarcodeDetector" in window;
        if (!cameraSupported) {
            cameraSelect.innerHTML = "<option>카메라 사용 불가</option>";
            cameraSelect.disabled = true;
            startButton.disabled = true;
            showCameraNotice(
                "이 브라우저/환경에서는 카메라를 사용할 수 없습니다. HTTPS 또는 localhost에서 접속하거나 직접 입력을 사용하세요.",
                false
            );
        } else {
            detector = new window.BarcodeDetector({
                formats: ["qr_code", "code_39", "code_128", "ean_13"]
            });
            refreshCameras().catch(function () {
                showCameraNotice("카메라 목록을 확인할 수 없습니다. 카메라 권한을 허용해 주세요.", false);
            });
        }

        startButton.addEventListener("click", async function () {
            try {
                stopCamera();
                const selectedDevice = cameraSelect.value;
                cameraStream = await navigator.mediaDevices.getUserMedia({
                    video: selectedDevice
                        ? {deviceId: {exact: selectedDevice}}
                        : {facingMode: {ideal: "environment"}},
                    audio: false
                });
                video.srcObject = cameraStream;
                await video.play();
                cameraEmpty.classList.add("d-none");
                startButton.disabled = true;
                stopButton.disabled = false;
                cameraNotice.classList.add("d-none");
                await refreshCameras();
                animationId = requestAnimationFrame(detectFrame);
            } catch (error) {
                stopCamera();
                showCameraNotice("카메라를 열 수 없습니다. 브라우저의 카메라 권한을 확인해 주세요.", true);
            }
        });

        stopButton.addEventListener("click", stopCamera);
        document.querySelectorAll("[data-scan-sample]").forEach(function (button) {
            button.addEventListener("click", function () {
                scanInput.value = button.dataset.code;
                scanInput.form.requestSubmit();
            });
        });
        window.addEventListener("pagehide", stopCamera);
        rememberServerResult();
        renderRecent();
        const currentResult = document.getElementById("wmsScanResult");
        if (currentResult && scrollOption.checked) {
            window.setTimeout(function () {
                currentResult.scrollIntoView({behavior: "smooth", block: "start"});
            }, 150);
        }
    }

    const binModalElement = document.getElementById("wmsBinDetailModal");
    if (binModalElement && window.bootstrap) {
        const binModal = new window.bootstrap.Modal(binModalElement);
        const detailElements = {
            title: document.getElementById("wmsBinDetailTitle"),
            loading: document.getElementById("wmsBinDetailLoading"),
            error: document.getElementById("wmsBinDetailError"),
            body: document.getElementById("wmsBinDetailBody"),
            location: document.getElementById("wmsBinDetailLocation"),
            status: document.getElementById("wmsBinDetailStatus"),
            load: document.getElementById("wmsBinDetailLoad"),
            remaining: document.getElementById("wmsBinDetailRemaining"),
            expired: document.getElementById("wmsBinExpiredAlert"),
            rows: document.getElementById("wmsBinDetailRows"),
            empty: document.getElementById("wmsBinDetailEmpty"),
            inventoryLink: document.getElementById("wmsBinInventoryLink"),
            moveLink: document.getElementById("wmsBinMoveLink")
        };

        const comma = function (value) {
            return Number(value || 0).toLocaleString("ko-KR");
        };

        const tableCell = function (text, className) {
            const cell = document.createElement("td");
            cell.textContent = text;
            if (className) {
                cell.className = className;
            }
            return cell;
        };

        const showDetailLoading = function (binCode) {
            detailElements.title.textContent = binCode + " 구역 상세";
            detailElements.loading.classList.remove("d-none");
            detailElements.error.classList.add("d-none");
            detailElements.body.classList.add("d-none");
            binModal.show();
        };

        const showDetailError = function (message) {
            detailElements.loading.classList.add("d-none");
            detailElements.body.classList.add("d-none");
            detailElements.error.textContent = message;
            detailElements.error.classList.remove("d-none");
        };

        const renderBinDetail = function (detail) {
            const bin = detail.bin;
            detailElements.title.textContent = bin.binCode + " 구역 상세";
            detailElements.location.textContent = bin.locationLabel;
            detailElements.status.textContent = bin.statusLabel;
            detailElements.status.className = "badge " + bin.statusBadgeClass;
            detailElements.load.textContent = comma(bin.loadedQuantity)
                + " / " + comma(bin.maxCapacity)
                + " (" + bin.usageRate + "%)";
            detailElements.remaining.textContent = comma(bin.remainingCapacity)
                + " 포대";
            detailElements.inventoryLink.href = "/inventory?view=stock&binId="
                + encodeURIComponent(bin.binId);
            detailElements.moveLink.href = "/admin/wms?view=move&binId="
                + encodeURIComponent(bin.binId);

            detailElements.rows.innerHTML = "";
            const inventories = detail.inventories || [];
            inventories.forEach(function (inventory) {
                const row = document.createElement("tr");
                if (inventory.expired) {
                    row.className = "table-warning";
                }

                const productCell = document.createElement("td");
                const productCode = document.createElement("span");
                productCode.className = "badge text-bg-light border text-dark me-1";
                productCode.textContent = inventory.productCode;
                productCell.appendChild(productCode);
                productCell.appendChild(
                    document.createTextNode(inventory.productName));
                row.appendChild(productCell);
                row.appendChild(tableCell(
                    inventory.lotNo,
                    "font-monospace small text-muted"));

                const expiryCell = document.createElement("td");
                expiryCell.className = "text-center";
                const expiryBadge = document.createElement("span");
                expiryBadge.className = "badge " + inventory.dDayBadgeClass;
                expiryBadge.textContent = inventory.dDayLabel;
                expiryBadge.title = inventory.expirationDate;
                expiryCell.appendChild(expiryBadge);
                row.appendChild(expiryCell);
                row.appendChild(tableCell(
                    comma(inventory.quantity),
                    "text-end fw-semibold"));
                detailElements.rows.appendChild(row);
            });

            detailElements.empty.classList.toggle(
                "d-none", inventories.length > 0);
            detailElements.expired.classList.toggle(
                "d-none", !detail.hasExpired);
            detailElements.moveLink.classList.toggle(
                "d-none", inventories.length === 0);
            detailElements.loading.classList.add("d-none");
            detailElements.error.classList.add("d-none");
            detailElements.body.classList.remove("d-none");
        };

        document.addEventListener("click", function (event) {
            const tile = event.target.closest(".ff-bin-tile");
            if (!tile || tile.classList.contains("ff-bin-inactive")) {
                return;
            }
            const binId = tile.dataset.binId;
            if (!binId) {
                return;
            }
            showDetailLoading(tile.dataset.binCode || "선택 구역");
            fetch("/api/admin/warehouse-map/bins/"
                    + encodeURIComponent(binId), {
                headers: {Accept: "application/json"}
            })
                .then(function (response) {
                    if (response.status === 401 || response.status === 403) {
                        throw new Error("조회 권한이 없습니다. 다시 로그인해 주세요.");
                    }
                    if (response.status === 404) {
                        throw new Error("존재하지 않는 구역입니다.");
                    }
                    if (!response.ok) {
                        throw new Error("구역 상세를 불러오지 못했습니다.");
                    }
                    return response.json();
                })
                .then(renderBinDetail)
                .catch(function (error) {
                    showDetailError(error.message
                        || "구역 상세를 불러오지 못했습니다.");
                });
        });
    }

    const mapElement = document.getElementById("wmsNetworkMap");
    const pinsElement = document.getElementById("wmsNetworkPins");
    if (!mapElement || !pinsElement) {
        return;
    }

    const pins = Array.from(pinsElement.querySelectorAll("span")).map(function (pin) {
        return {
            id: Number(pin.dataset.id),
            name: pin.dataset.name,
            code: pin.dataset.code,
            latitude: Number(pin.dataset.lat),
            longitude: Number(pin.dataset.lng),
            address: pin.dataset.address,
            quantity: Number(pin.dataset.quantity || 0)
        };
    });
    const selectedId = Number(mapElement.dataset.selectedId || 0);
    const selectedPin = pins.find(function (pin) { return pin.id === selectedId; });

    const renderFallback = function () {
        mapElement.classList.add("wms-network-fallback");
        pins.forEach(function (pin) {
            const marker = document.createElement("button");
            marker.type = "button";
            marker.className = "wms-network-pin"
                + (selectedPin && selectedPin.id === pin.id ? " is-selected" : "");
            marker.style.left = (((pin.longitude - 125.5) / 4.8) * 100) + "%";
            marker.style.top = (((38.8 - pin.latitude) / 5.4) * 100) + "%";
            marker.textContent = pin.name + " · " + pin.quantity.toLocaleString() + "포대";
            marker.title = pin.address;
            marker.addEventListener("click", function () {
                window.location.href = "/admin/warehouse-map?centerId=" + pin.id;
            });
            mapElement.appendChild(marker);
        });
    };

    const initializeLeaflet = function () {
        if (!window.L) {
            renderFallback();
            return;
        }
        const map = window.L.map(mapElement, {
            scrollWheelZoom: false
        }).setView(
            selectedPin ? [selectedPin.latitude, selectedPin.longitude] : [36.25, 127.7],
            selectedPin ? 11 : 7);
        window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 18,
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);
        pins.forEach(function (pin) {
            const marker = window.L.marker([pin.latitude, pin.longitude])
                .addTo(map)
                .bindPopup(
                    "<strong>" + pin.name + "</strong><br>" +
                    pin.address + "<br>LOT 실재고 " +
                    pin.quantity.toLocaleString() + "포대"
                );
            marker.on("click", function () {
                window.setTimeout(function () {
                    window.location.href = "/admin/warehouse-map?centerId=" + pin.id;
                }, 180);
            });
            if (selectedPin && selectedPin.id === pin.id) {
                marker.openPopup();
            }
        });
        const resetButton = document.getElementById("wmsNetworkReset");
        if (resetButton) {
            resetButton.addEventListener("click", function () {
                map.setView([36.25, 127.7], 7);
            });
        }
    };

    const leafletCss = document.createElement("link");
    leafletCss.rel = "stylesheet";
    leafletCss.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
    document.head.appendChild(leafletCss);

    const leafletScript = document.createElement("script");
    leafletScript.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    leafletScript.onload = initializeLeaflet;
    leafletScript.onerror = renderFallback;
    document.head.appendChild(leafletScript);
})();

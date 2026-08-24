(function () {
    "use strict";

    const paginationWindowSize = 10;

    const visiblePageRange = function (currentPage, pageCount) {
        const firstPage = Math.floor(
            (currentPage - 1) / paginationWindowSize
        ) * paginationWindowSize + 1;
        return {
            firstPage: firstPage,
            lastPage: Math.min(
                firstPage + paginationWindowSize - 1,
                pageCount
            )
        };
    };

    const mapTabs = Array.from(document.querySelectorAll("[data-wms-map-tab]"));
    const mapPanels = Array.from(document.querySelectorAll("[data-wms-map-panel]"));
    if (mapTabs.length && mapPanels.length) {
        const selectMapTab = function (selected) {
            mapTabs.forEach(function (tab) {
                const active = tab.dataset.wmsMapTab === selected;
                tab.classList.toggle("active", active);
                tab.setAttribute("aria-selected", String(active));
            });
            mapPanels.forEach(function (panel) {
                panel.hidden = panel.dataset.wmsMapPanel !== selected;
            });
        };
        mapTabs.forEach(function (tab) {
            tab.addEventListener("click", function () {
                selectMapTab(tab.dataset.wmsMapTab);
            });
        });
        selectMapTab("map");
    }

    const autoBinForm = document.getElementById("wmsAutoBinForm");
    if (autoBinForm) {
        const warehouseSelect = autoBinForm.querySelector("[name='warehouseId']");
        const productSelect = autoBinForm.querySelector("[name='productId']");
        const quantityInput = autoBinForm.querySelector("[name='plannedQuantity']");
        const posXInput = autoBinForm.querySelector("[name='posX']");
        const posYInput = autoBinForm.querySelector("[name='posY']");
        const posWidthInput = autoBinForm.querySelector("[name='posWidth']");
        const posHeightInput = autoBinForm.querySelector("[name='posHeight']");
        const productHint = autoBinForm.querySelector("[data-auto-bin-product-hint]");
        const preview = autoBinForm.querySelector("[data-auto-bin-preview]");
        const placementSummary = autoBinForm.querySelector("[data-auto-placement-summary]");
        const placementMaps = Array.from(document.querySelectorAll("[data-auto-placement-map]"));
        const placementStatus = document.querySelector("[data-placement-status]");
        const placementClear = document.querySelector("[data-auto-placement-clear]");
        let layoutWidth = 1;
        let layoutHeight = 1;
        const productOptions = Array.from(productSelect.options)
            .filter(function (option) { return option.value; });

        const showPlacementMap = function () {
            placementMaps.forEach(function (map) {
                map.hidden = map.dataset.warehouseId !== warehouseSelect.value;
            });
        };

        const resetPlacement = function () {
            posXInput.value = "";
            posYInput.value = "";
            posWidthInput.value = "";
            posHeightInput.value = "";
            placementMaps.forEach(function (map) {
                map.querySelectorAll(".wms-placement-cell.is-placement-selected")
                    .forEach(function (cell) {
                        cell.classList.remove("is-placement-selected");
                    });
                const candidate = map.querySelector("[data-placement-candidate]");
                if (candidate) candidate.hidden = true;
            });
            placementSummary.textContent = "위치를 선택하지 않으면 빈 공간에 자동 배치됩니다.";
            if (placementStatus) {
                placementStatus.className = "alert alert-light border py-2 mb-3";
                placementStatus.textContent = "빈 공간에서 새 구역의 왼쪽 위 시작점을 선택해 주세요.";
            }
        };

        const rectanglesOverlap = function (x, y, width, height, obstacle) {
            const obstacleX = Number(obstacle.dataset.x);
            const obstacleY = Number(obstacle.dataset.y);
            const obstacleWidth = Number(obstacle.dataset.width);
            const obstacleHeight = Number(obstacle.dataset.height);
            return x < obstacleX + obstacleWidth
                && x + width > obstacleX
                && y < obstacleY + obstacleHeight
                && y + height > obstacleY;
        };

        const placementAvailable = function (map, x, y, width, height) {
            const outside = x < 1 || y < 1
                || x + width - 1 > 26
                || y + height - 1 > 14;
            if (outside) return false;
            const overlaps = Array.from(map.querySelectorAll(".wms-placement-obstacle"))
                .some(function (obstacle) {
                    return rectanglesOverlap(
                        x, y, width, height, obstacle);
                });
            return !overlaps;
        };

        const placementAroundClickedCell = function (map, clickedX, clickedY) {
            const sizes = [[layoutWidth, layoutHeight]];
            if (layoutWidth !== layoutHeight) {
                sizes.push([layoutHeight, layoutWidth]);
            }
            // 도면은 위치를 설명하는 개념도이므로 계획 크기가 주변 구역에
            // 걸리면 클릭한 빈 칸 하나로 축소해도 실제 계획 수용량은 유지한다.
            sizes.push([1, 1]);
            for (const size of sizes) {
                const width = size[0];
                const height = size[1];
                for (let offsetY = 0; offsetY < height; offsetY++) {
                    for (let offsetX = 0; offsetX < width; offsetX++) {
                        const x = clickedX - offsetX;
                        const y = clickedY - offsetY;
                        if (placementAvailable(map, x, y, width, height)) {
                            return {x: x, y: y, width: width, height: height};
                        }
                    }
                }
            }
            return null;
        };

        const selectPlacement = function (map, clickedX, clickedY) {
            const placement = placementAroundClickedCell(map, clickedX, clickedY);
            if (!placement) {
                resetPlacement();
                placementStatus.className = "alert alert-danger py-2 mb-3";
                placementStatus.textContent = "상품이나 시설이 표시되지 않은 빈 칸을 선택해 주세요.";
                return;
            }

            const x = placement.x;
            const y = placement.y;
            const selectedWidth = placement.width;
            const selectedHeight = placement.height;

            posXInput.value = String(x);
            posYInput.value = String(y);
            posWidthInput.value = String(selectedWidth);
            posHeightInput.value = String(selectedHeight);
            placementMaps.forEach(function (panel) {
                panel.querySelectorAll(".wms-placement-cell.is-placement-selected")
                    .forEach(function (cell) {
                        cell.classList.remove("is-placement-selected");
                    });
                const candidate = panel.querySelector("[data-placement-candidate]");
                if (candidate) candidate.hidden = true;
            });
            map.querySelectorAll(".wms-placement-cell").forEach(function (cell) {
                const cellX = Number(cell.dataset.x);
                const cellY = Number(cell.dataset.y);
                const selected = cellX >= x && cellX < x + selectedWidth
                    && cellY >= y && cellY < y + selectedHeight;
                cell.classList.toggle("is-placement-selected", selected);
            });
            const candidate = map.querySelector("[data-placement-candidate]");
            candidate.style.gridArea = y + " / " + x + " / span "
                + selectedHeight + " / span " + selectedWidth;
            candidate.querySelector("[data-placement-candidate-size]").textContent =
                "X " + x + " · Y " + y + " · "
                + selectedWidth + "×" + selectedHeight;
            candidate.hidden = false;
            placementSummary.textContent = "선택 위치: X " + x + ", Y " + y
                + " · 도면 크기 " + selectedWidth + "×" + selectedHeight;
            placementStatus.className = "alert alert-success py-2 mb-3";
            placementStatus.textContent = selectedWidth === layoutWidth
                    && selectedHeight === layoutHeight
                ? "선택한 빈 공간에 새 구역을 생성합니다."
                : "선택한 빈 칸에 맞춰 도면 표시 크기를 조정했습니다. 계획 수용량은 그대로 유지됩니다.";
        };

        const refreshAutoBinForm = function () {
            const warehouseId = warehouseSelect.value;
            let availableCount = 0;
            productOptions.forEach(function (option) {
                const visible = option.dataset.warehouseId === warehouseId;
                option.hidden = !visible;
                option.disabled = !visible;
                if (visible) availableCount++;
            });
            const selectedOption = productSelect.selectedOptions[0];
            if (selectedOption && selectedOption.value
                    && selectedOption.dataset.warehouseId !== warehouseId) {
                productSelect.value = "";
            }
            productHint.textContent = availableCount > 0
                ? "이 창고에서 취급 중인 상품 " + availableCount + "개 중 선택하세요."
                : "이 창고에 등록된 취급 상품이 없습니다.";

            const quantity = Math.max(0, Number(quantityInput.value || 0));
            const cells = Math.max(1, Math.ceil(quantity / 500));
            layoutWidth = Math.ceil(Math.sqrt(cells));
            layoutHeight = Math.ceil(cells / layoutWidth);
            showPlacementMap();
            const selectedLocation = posXInput.value && posYInput.value
                ? " 선택 위치는 X " + posXInput.value + ", Y " + posYInput.value
                    + "이며 도면 크기는 " + posWidthInput.value + "×"
                    + posHeightInput.value + "입니다."
                : " 위치를 고르지 않으면 빈 공간에 자동 배정합니다.";
            preview.innerHTML = "<i class='bi bi-magic me-1 text-success'></i>"
                + quantity.toLocaleString("ko-KR") + "포 기준 약 "
                + layoutWidth + "×" + layoutHeight
                + " 크기로 계산합니다." + selectedLocation;
        };

        warehouseSelect.addEventListener("change", function () {
            resetPlacement();
            refreshAutoBinForm();
        });
        productSelect.addEventListener("change", refreshAutoBinForm);
        quantityInput.addEventListener("input", function () {
            resetPlacement();
            refreshAutoBinForm();
        });
        placementMaps.forEach(function (map) {
            map.addEventListener("click", function (event) {
                const cell = event.target.closest("[data-x][data-y].wms-placement-cell");
                if (!cell) return;
                selectPlacement(map, Number(cell.dataset.x), Number(cell.dataset.y));
                refreshAutoBinForm();
            });
        });
        if (placementClear) {
            placementClear.addEventListener("click", function () {
                resetPlacement();
                refreshAutoBinForm();
            });
        }

        const autoBinParams = new URLSearchParams(window.location.search);
        const requestedWarehouse = autoBinParams.get("binWarehouseId");
        const requestedProduct = autoBinParams.get("autoProductId");
        const requestedQuantity = autoBinParams.get("autoQuantity");
        if (requestedWarehouse
                && Array.from(warehouseSelect.options).some(function (option) {
                    return option.value === requestedWarehouse;
                })) {
            warehouseSelect.value = requestedWarehouse;
        }
        refreshAutoBinForm();
        if (requestedProduct) {
            const matchingProduct = productOptions.find(function (option) {
                return !option.disabled
                    && option.value === requestedProduct;
            });
            if (matchingProduct) productSelect.value = matchingProduct.value;
        }
        if (requestedQuantity && Number(requestedQuantity) > 0) {
            quantityInput.value = requestedQuantity;
        }
        refreshAutoBinForm();
    }

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

    const locationInventoryRows = Array.from(
        document.querySelectorAll("[data-location-inventory-row]")
    );
    const locationInventoryPagination = document.getElementById(
        "wmsLocationInventoryPagination"
    );
    const locationInventoryFilters = Array.from(
        document.querySelectorAll("[data-location-warehouse]")
    );
    const locationInventoryCount = document.getElementById(
        "wmsLocationInventoryCount"
    );
    const locationInventoryEmpty = document.getElementById(
        "wmsLocationInventoryEmpty"
    );
    const locationInventoryTable = document.getElementById(
        "wmsLocationInventoryTable"
    );
    const locationInventoryQuantityHeader = locationInventoryTable
        ?.querySelector("thead th:nth-child(3)");
    if (locationInventoryRows.length && locationInventoryPagination) {
        const configuredPageSize = Number(
            locationInventoryPagination.dataset.pageSize
        );
        const pageSize = Number.isInteger(configuredPageSize)
            && configuredPageSize > 0
            ? configuredPageSize
            : 10;
        let currentPage = 1;
        let activeWarehouse = "all";
        let locationInventorySortDirection = null;
        if (locationInventoryQuantityHeader) {
            const sortButton = document.createElement("button");
            sortButton.type = "button";
            sortButton.className = "wms-stock-sort-button";
            sortButton.title = "보유 포대수 내림차순 정렬";
            sortButton.setAttribute("aria-label", "보유 포대수 내림차순 정렬");
            sortButton.innerHTML = "보유 <span aria-hidden=\"true\">↓</span>";
            locationInventoryQuantityHeader.replaceChildren(sortButton);
            locationInventoryQuantityHeader.setAttribute("aria-sort", "none");
            sortButton.addEventListener("click", function () {
                locationInventorySortDirection =
                    locationInventorySortDirection === "descending"
                        ? "ascending"
                        : "descending";
                locationInventoryRows.sort(function (left, right) {
                    const leftQuantity = Number.parseInt(
                        left.cells[2]?.textContent?.replace(/[^0-9]/g, "")
                            || "0",
                        10
                    );
                    const rightQuantity = Number.parseInt(
                        right.cells[2]?.textContent?.replace(/[^0-9]/g, "")
                            || "0",
                        10
                    );
                    return locationInventorySortDirection === "descending"
                        ? rightQuantity - leftQuantity
                        : leftQuantity - rightQuantity;
                });
                const tableBody = locationInventoryTable.querySelector("tbody");
                locationInventoryRows.forEach(function (row) {
                    tableBody.insertBefore(row, locationInventoryEmpty || null);
                });
                currentPage = 1;
                locationInventoryQuantityHeader.setAttribute(
                    "aria-sort", locationInventorySortDirection
                );
                const descending =
                    locationInventorySortDirection === "descending";
                sortButton.innerHTML = descending
                    ? "보유 <span aria-hidden=\"true\">↓</span>"
                    : "보유 <span aria-hidden=\"true\">↑</span>";
                const nextDirection = descending ? "오름차순" : "내림차순";
                sortButton.title = "보유 포대수 " + nextDirection + " 정렬";
                sortButton.setAttribute(
                    "aria-label",
                    "현재 " + (descending ? "내림차순" : "오름차순")
                        + ", 보유 포대수 " + nextDirection + " 정렬"
                );
                sortButton.classList.add("active");
                renderLocationInventoryPage();
            });
        }
        const renderLocationInventoryPage = function () {
            const filteredRows = locationInventoryRows.filter(function (row) {
                return activeWarehouse === "all"
                    || row.dataset.locationWarehouseId === activeWarehouse;
            });
            const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
            currentPage = Math.min(currentPage, pageCount);
            const start = (currentPage - 1) * pageSize;
            const visibleRows = new Set(filteredRows.slice(start, start + pageSize));
            locationInventoryRows.forEach(function (row) {
                row.hidden = !visibleRows.has(row);
            });
            if (locationInventoryCount) {
                locationInventoryCount.textContent = filteredRows.length + "건";
            }
            if (locationInventoryEmpty) {
                locationInventoryEmpty.hidden = filteredRows.length !== 0;
            }
            locationInventoryPagination.replaceChildren();
            locationInventoryPagination.hidden = filteredRows.length <= pageSize;
            if (pageCount <= 1) return;

            const addButton = function (label, page, disabled, active) {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = label;
                button.disabled = disabled;
                if (active) {
                    button.classList.add("active");
                    button.setAttribute("aria-current", "page");
                }
                button.addEventListener("click", function () {
                    currentPage = page;
                    renderLocationInventoryPage();
                    document.getElementById("wmsLocationInventoryTable")
                        ?.scrollIntoView({ behavior: "smooth", block: "start" });
                });
                locationInventoryPagination.appendChild(button);
            };
            addButton("이전", currentPage - 1, currentPage === 1, false);
            const pageRange = visiblePageRange(currentPage, pageCount);
            for (let page = pageRange.firstPage;
                page <= pageRange.lastPage; page++) {
                addButton(String(page), page, false, page === currentPage);
            }
            addButton("다음", currentPage + 1,
                currentPage === pageCount, false);
        };
        locationInventoryFilters.forEach(function (filter) {
            filter.addEventListener("click", function () {
                activeWarehouse = filter.dataset.locationWarehouse || "all";
                currentPage = 1;
                locationInventoryFilters.forEach(function (item) {
                    const selected = item === filter;
                    item.classList.toggle("active", selected);
                    item.setAttribute("aria-selected", String(selected));
                });
                renderLocationInventoryPage();
            });
        });
        renderLocationInventoryPage();
    }

    const movementRows = Array.from(
        document.querySelectorAll("[data-wms-movement-row]")
    );
    const movementSearch = document.getElementById("wmsMovementSearch");
    const movementCount = document.getElementById("wmsMovementCount");
    const movementEmpty = document.getElementById("wmsMovementEmpty");
    const movementPagination = document.getElementById("wmsMovementPagination");
    if (movementPagination && movementRows.length) {
        const pageSize = 10;
        let currentPage = 1;
        const renderMovements = function () {
            const keyword = (movementSearch?.value || "").trim().toLowerCase();
            const filteredRows = movementRows.filter(function (row) {
                return !keyword
                    || (row.dataset.search || "").toLowerCase().includes(keyword);
            });
            const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
            currentPage = Math.min(currentPage, pageCount);
            const start = (currentPage - 1) * pageSize;
            const visibleRows = new Set(filteredRows.slice(start, start + pageSize));
            movementRows.forEach(function (row) {
                row.hidden = !visibleRows.has(row);
            });
            if (movementCount) movementCount.textContent = filteredRows.length + "건";
            if (movementEmpty) movementEmpty.hidden = filteredRows.length !== 0;
            movementPagination.replaceChildren();
            movementPagination.hidden = filteredRows.length <= pageSize;
            if (filteredRows.length <= pageSize) return;

            const addButton = function (label, page, disabled, active) {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = label;
                button.disabled = disabled;
                if (active) {
                    button.classList.add("active");
                    button.setAttribute("aria-current", "page");
                }
                button.addEventListener("click", function () {
                    currentPage = page;
                    renderMovements();
                    document.getElementById("wmsMovementTable")
                        ?.scrollIntoView({ behavior: "smooth", block: "start" });
                });
                movementPagination.appendChild(button);
            };
            addButton("이전", currentPage - 1, currentPage === 1, false);
            const pageRange = visiblePageRange(currentPage, pageCount);
            for (let page = pageRange.firstPage;
                page <= pageRange.lastPage; page++) {
                addButton(String(page), page, false, page === currentPage);
            }
            addButton("다음", currentPage + 1, currentPage === pageCount, false);
        };
        movementSearch?.addEventListener("input", function () {
            currentPage = 1;
            renderMovements();
        });
        renderMovements();
    }

    const directOutboundRows = Array.from(
        document.querySelectorAll("[data-direct-outbound-row]")
    );
    const directOutboundSearch = document.getElementById("wmsDirectOutboundSearch");
    const directOutboundCount = document.getElementById("wmsDirectOutboundCount");
    const directOutboundEmpty = document.getElementById("wmsDirectOutboundEmpty");
    const directOutboundPagination = document.getElementById("wmsDirectOutboundPagination");
    const directOutboundProduct = document.getElementById("directOutboundProduct");
    if (directOutboundPagination && directOutboundRows.length) {
        const pageSize = 10;
        let currentPage = 1;
        const renderDirectOutbound = function () {
            const keyword = (directOutboundSearch?.value || "").trim().toLowerCase();
            const filteredRows = directOutboundRows.filter(function (row) {
                return !keyword
                    || (row.dataset.search || "").toLowerCase().includes(keyword);
            });
            const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
            currentPage = Math.min(currentPage, pageCount);
            const start = (currentPage - 1) * pageSize;
            const visibleRows = new Set(filteredRows.slice(start, start + pageSize));
            directOutboundRows.forEach(function (row) {
                row.hidden = !visibleRows.has(row);
            });
            if (directOutboundCount) directOutboundCount.textContent = filteredRows.length + "건";
            if (directOutboundEmpty) directOutboundEmpty.hidden = filteredRows.length !== 0;
            directOutboundPagination.replaceChildren();
            directOutboundPagination.hidden = filteredRows.length <= pageSize;
            if (filteredRows.length <= pageSize) return;

            const addButton = function (label, page, disabled, active) {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = label;
                button.disabled = disabled;
                if (active) button.classList.add("active");
                button.addEventListener("click", function () {
                    currentPage = page;
                    renderDirectOutbound();
                });
                directOutboundPagination.appendChild(button);
            };
            addButton("이전", currentPage - 1, currentPage === 1, false);
            const pageRange = visiblePageRange(currentPage, pageCount);
            for (let page = pageRange.firstPage;
                page <= pageRange.lastPage; page++) {
                addButton(String(page), page, false, page === currentPage);
            }
            addButton("다음", currentPage + 1, currentPage === pageCount, false);
        };
        directOutboundSearch?.addEventListener("input", function () {
            currentPage = 1;
            renderDirectOutbound();
        });
        directOutboundRows.forEach(function (row) {
            row.classList.add("wms-selectable-row");
            row.addEventListener("click", function () {
                if (!directOutboundProduct) return;
                directOutboundProduct.value = row.dataset.productId || "";
                directOutboundProduct.scrollIntoView({ behavior: "smooth", block: "center" });
                directOutboundProduct.focus();
            });
        });
        renderDirectOutbound();
    }

    const bulkInboundForm = document.getElementById("wmsBulkInboundForm");
    const demandActionCard = document.getElementById("wmsDemandActionCard");
    if (bulkInboundForm && demandActionCard) {
        const selectAll = demandActionCard.querySelector("[data-bulk-select-all]");
        const targets = Array.from(demandActionCard.querySelectorAll("[data-bulk-target]"));
        const submit = demandActionCard.querySelector("[data-bulk-submit]");
        const count = demandActionCard.querySelector("[data-bulk-selected-count]");
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
        let qrDetector = null;
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
                    const results = await qrDetector.detect(video);
                    if (results.length > 0 && results[0].rawValue) {
                        submitDetectedCode(results[0].rawValue);
                        detecting = false;
                        return;
                    }
                } catch (error) {
                    showCameraNotice("QR 코드를 인식하는 중 오류가 발생했습니다. 직접 입력을 사용해 주세요.", true);
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
            qrDetector = new window.BarcodeDetector({
                formats: ["qr_code"]
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
            moveLink: document.getElementById("wmsBinMoveLink"),
            deleteForm: document.getElementById("wmsBinDeleteForm"),
            deleteWarehouseId: document.getElementById("wmsBinDeleteWarehouseId"),
            deleteButton: document.getElementById("wmsBinDeleteButton"),
            deleteReason: document.getElementById("wmsBinDeleteReason")
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
            detailElements.deleteForm.classList.add("d-none");
            detailElements.deleteReason.textContent = "";
            binModal.show();
        };

        const showDetailError = function (message) {
            detailElements.loading.classList.add("d-none");
            detailElements.body.classList.add("d-none");
            detailElements.error.textContent = message;
            detailElements.error.classList.remove("d-none");
            detailElements.deleteForm.classList.add("d-none");
            detailElements.deleteReason.textContent = "";
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
            detailElements.deleteForm.action = "/admin/wms/bins/"
                + encodeURIComponent(bin.binId) + "/delete";
            detailElements.deleteWarehouseId.value = bin.warehouseId;
            detailElements.deleteButton.disabled = !bin.deletable;
            detailElements.deleteForm.classList.remove("d-none");
            detailElements.deleteReason.textContent = bin.deletable
                ? "재고가 없는 구역은 안전하게 삭제할 수 있습니다."
                : (bin.deleteBlockedReason || "현재 이 구역은 삭제할 수 없습니다.");

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

    const traceResult = document.getElementById("lotTraceability");
    if (traceResult && window.location.hash === "#lotTraceability") {
        window.setTimeout(function () {
            const traceTop = traceResult.getBoundingClientRect().top + window.scrollY;
            window.scrollTo({ top: Math.max(0, traceTop - 92), behavior: "smooth" });
        }, 180);
    }

    const timeline = document.querySelector("[data-timeline-list]");
    if (timeline) {
        const timelineItems = Array.from(timeline.querySelectorAll("li[data-timestamp]"));
        const sortButtons = Array.from(document.querySelectorAll("[data-timeline-sort]"));
        sortButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                const direction = button.dataset.timelineSort;
                timelineItems
                    .slice()
                    .sort(function (left, right) {
                        const comparison = left.dataset.timestamp.localeCompare(right.dataset.timestamp);
                        return direction === "asc" ? comparison : -comparison;
                    })
                    .forEach(function (item) { timeline.appendChild(item); });
                sortButtons.forEach(function (candidate) {
                    candidate.classList.toggle("active", candidate === button);
                });
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

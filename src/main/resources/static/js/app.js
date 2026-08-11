document.addEventListener("DOMContentLoaded", () => {
    const all = (selector, root = document) =>
        Array.from(root.querySelectorAll(selector));

    const shipmentTabs = all("[data-shipment-view]");
    const shipmentPanels = all("[data-shipment-panel]");

    function selectShipmentView(view) {
        shipmentTabs.forEach(item => {
            const selected = item.dataset.shipmentView === view;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-selected", String(selected));
        });
        shipmentPanels.forEach(panel => {
            panel.hidden = panel.dataset.shipmentPanel !== view;
        });
    }

    shipmentTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            selectShipmentView(tab.dataset.shipmentView);
        });
    });

    if (shipmentTabs.length > 0) {
        const requestedShipmentView =
            new URLSearchParams(window.location.search).get("shipmentTab");
        selectShipmentView(
            requestedShipmentView === "cancelled" ? "cancelled" : "active"
        );
    }

    /*
     * 공통 모달
     */
    all("[data-open]").forEach(button => {
        button.addEventListener("click", () => {
            document.getElementById(button.dataset.open)?.showModal();
        });
    });

    all("[data-close]").forEach(button => {
        button.addEventListener("click", () => {
            button.closest("dialog")?.close();
        });
    });

    document.querySelector("[data-print-lot-label]")
        ?.addEventListener("click", () => window.print());

    all(".inbound-cancel-button").forEach(button => {
        button.closest("form")?.addEventListener("submit", event => {
            if (!window.confirm(
                "이 입고 건을 취소하면 해당 수량이 재고에서 차감됩니다. 계속할까요?"
            )) {
                event.preventDefault();
            }
        });
    });

    all("dialog").forEach(dialog => {
        dialog.addEventListener("click", event => {
            if (event.target === dialog) {
                dialog.close();
            }
        });
    });

    /*
     * ID와 이름을 전달해야 하는 모달
     */
    const modalBindings = [
        [".release-button", "releaseModal", "productId"],
        [".adjust-button", "adjustModal", "lotId"],
        [".delivery-button", "deliveryModal", "orderId"],
        [".delivery-cancel-button", "deliveryCancelModal", "deliveryId"],
        [".order-cancel-button", "orderCancelModal", "orderId"],
        [
            ".recurring-receive-button",
            "recurringReceiveModal",
            "recurringDeliveryId"
        ],
        [
            ".warehouse-plan-edit-button",
            "warehousePlanModal",
            "allocationId"
        ],
        [
            ".warehouse-stock-adjust-button",
            "warehouseStockModal",
            "allocationId"
        ]
    ];

    modalBindings.forEach(([selector, modalId, fieldName]) => {
        all(selector).forEach(button => {
            button.addEventListener("click", () => {
                openEntityModal(modalId, fieldName, button);

                if (selector === ".delivery-cancel-button") {
                    const cancelForm = document.querySelector(
                        "[data-delivery-cancel-form]"
                    );
                    if (cancelForm) {
                        cancelForm.action =
                            `/distribution/deliveries/${button.dataset.id}/cancel`;
                    }
                }

                if (selector === ".order-cancel-button") {
                    const cancelForm = document.querySelector(
                        "[data-order-cancel-form]"
                    );
                    const stageMessage = document.querySelector(
                        "[data-order-cancel-stage]"
                    );
                    if (cancelForm) {
                        cancelForm.action =
                            `/distribution/orders/${button.dataset.id}/cancel`;
                    }
                    if (stageMessage) {
                        stageMessage.textContent =
                            button.dataset.stage
                            ?? "예약 또는 출고 재고를 자동 반영합니다.";
                    }
                }

                if (selector === ".recurring-receive-button") {
                    const recurringModal =
                        document.getElementById("recurringReceiveModal");
                    const info = document.querySelector(
                        "#recurringReceiveModal .recurring-quantity-info"
                    );

                    if (info) {
                        info.textContent =
                            button.dataset.quantityText
                            ?? "예정 입고 수량을 확인해 주세요.";
                    }

                    if (recurringModal) {
                        recurringModal.dataset.shelfLifeMonths =
                            button.dataset.shelfLifeMonths ?? "6";
                        recurringModal.dataset.productId =
                            button.dataset.productId ?? "1";
                        recurringModal.dataset.category =
                            button.dataset.category ?? "";
                        const manufacturedInput = recurringModal.querySelector(
                            '[name="manufacturedDate"]'
                        );
                        manufacturedInput?.dispatchEvent(new Event("change"));
                    }
                }

                if (selector === ".warehouse-plan-edit-button") {
                    const warehouseModal =
                        document.getElementById("warehousePlanModal");
                    const monthlyInput = warehouseModal?.querySelector(
                        '[name="monthlyPlannedQuantity"]'
                    );
                    const targetInput = warehouseModal?.querySelector(
                        '[name="targetStockQuantity"]'
                    );

                    if (monthlyInput) {
                        monthlyInput.value = button.dataset.monthly ?? "0";
                    }
                    if (targetInput) {
                        targetInput.value = button.dataset.target ?? "0";
                    }
                }

                if (selector === ".warehouse-stock-adjust-button") {
                    const warehouseStockModal =
                        document.getElementById("warehouseStockModal");
                    const currentInput = warehouseStockModal?.querySelector(
                        '[name="currentStockQuantity"]'
                    );
                    const targetText = warehouseStockModal?.querySelector(
                        "[data-warehouse-target-stock]"
                    );

                    if (currentInput) {
                        currentInput.value = button.dataset.current ?? "0";
                    }
                    if (targetText) {
                        const target = Number(button.dataset.target ?? "0");
                        targetText.textContent =
                            `${target.toLocaleString("ko-KR")}포`;
                    }
                }
            });
        });
    });

    const deliverySearch = document.getElementById("deliverySearch");
    const deliveryStatusFilter =
        document.getElementById("deliveryStatusFilter");
    const deliveryRows = all(
        "#deliveryTrackingTable tbody tr[data-status]"
    );
    const deliveryFilterEmpty =
        document.getElementById("deliveryFilterEmpty");
    const deliveryViewTabs = all("[data-delivery-view]");
    const readyDeliveryPanel = document.querySelector(
        '[data-delivery-panel="ready"]'
    );
    const cancelledOrderPanel = document.querySelector(
        '[data-delivery-panel="cancelled_orders"]'
    );
    const farmCustomerPanel = document.querySelector(
        '[data-delivery-panel="farms"]'
    );
    const deliveryTrackingPanel = document.querySelector(
        '[data-delivery-panel="tracking"]'
    );
    const deliveryPanelTitle =
        document.getElementById("deliveryPanelTitle");
    const deliveryPanelDescription =
        document.getElementById("deliveryPanelDescription");
    let activeDeliveryView = "all";

    const deliveryViewCopy = {
        all: [
            "전체 배송",
            "진행 중이거나 완료된 모든 배송을 통합 조회합니다."
        ],
        picked_up: [
            "택배 인계",
            "운송장 수정, 배송 중 전환 또는 배송 취소를 처리합니다."
        ],
        in_transit: [
            "배송 중",
            "배송 완료, 운송장 수정과 예외 취소를 처리합니다."
        ],
        delivered: [
            "배송 완료",
            "수령처에 인도된 배송과 출고 LOT 이력을 확인합니다."
        ],
        delayed: [
            "배송 지연",
            "도착 예정일을 초과한 배송의 사유와 변경 일정을 관리합니다."
        ],
        cancelled: [
            "취소 배송",
            "취소 사유와 담당자를 확인하고 새 운송장으로 재배송합니다."
        ],
        returns: [
            "회수 관리",
            "배송 완료 상품의 회수 진행, 검수와 재고·불량 반영을 처리합니다."
        ]
    };

    function filterDeliveries() {
        const keyword =
            deliverySearch?.value.trim().toLowerCase() ?? "";
        const selectedStatus =
            deliveryStatusFilter?.value ?? "all";
        let visible = 0;

        deliveryRows.forEach(row => {
            const matchesKeyword =
                row.textContent.trim().toLowerCase().includes(keyword);
            const matchesView =
                activeDeliveryView === "all"
                    ? row.dataset.status !== "CANCELLED"
                    : activeDeliveryView === "returns"
                        ? Boolean(row.dataset.returnStatus)
                    : activeDeliveryView === "delayed"
                        ? row.dataset.delayed === "true"
                        : row.dataset.status
                            === activeDeliveryView.toUpperCase();
            const matchesStatus =
                selectedStatus === "all"
                || row.dataset.status === selectedStatus
                || (
                    selectedStatus === "DELAYED"
                    && row.dataset.delayed === "true"
                );
            const show =
                matchesKeyword && matchesView && matchesStatus;
            row.hidden = !show;
            if (show) {
                visible++;
            }
        });

        if (deliveryFilterEmpty) {
            deliveryFilterEmpty.hidden = visible !== 0;
        }
    }

    deliverySearch?.addEventListener("input", filterDeliveries);
    deliveryStatusFilter?.addEventListener("change", filterDeliveries);

    const cancelledOrderSearch =
        document.getElementById("cancelledOrderSearch");
    const cancelledOrderRows = all("[data-cancelled-order-row]");
    const cancelledOrderFilterEmpty =
        document.getElementById("cancelledOrderFilterEmpty");

    function filterCancelledOrders() {
        const keyword =
            cancelledOrderSearch?.value.trim().toLowerCase() ?? "";
        let visible = 0;

        cancelledOrderRows.forEach(row => {
            const show = row.textContent
                .trim()
                .toLowerCase()
                .includes(keyword);
            row.hidden = !show;
            if (show) {
                visible++;
            }
        });

        if (cancelledOrderFilterEmpty) {
            cancelledOrderFilterEmpty.hidden = visible !== 0;
        }
    }

    cancelledOrderSearch?.addEventListener(
        "input", filterCancelledOrders
    );

    const farmCustomerSearch =
        document.getElementById("farmCustomerSearch");
    const farmWarehouseCards = all(
        ".farm-warehouse-card[data-farm-warehouse]"
    );
    const farmAnimalTabs = all(
        ".farm-animal-tabs [data-farm-animal]"
    );
    const farmStatusTabs = all(
        ".farm-status-tabs [data-farm-status]"
    );
    const farmCustomerRows = all(
        "#farmCustomerTable tbody tr[data-farm-row]"
    );
    const farmCustomerVisibleCount =
        document.getElementById("farmCustomerVisibleCount");
    const farmCustomerFilterEmpty =
        document.getElementById("farmCustomerFilterEmpty");
    let activeFarmWarehouse = "all";
    let activeFarmAnimal = "all";
    let activeFarmStatus = "all";

    function filterFarmCustomers() {
        const keyword =
            farmCustomerSearch?.value.trim().toLowerCase() ?? "";
        let visible = 0;

        farmCustomerRows.forEach(row => {
            const matchesWarehouse =
                activeFarmWarehouse === "all"
                || row.dataset.warehouse === activeFarmWarehouse;
            const matchesAnimal =
                activeFarmAnimal === "all"
                || row.dataset.animal === activeFarmAnimal;
            const matchesStatus =
                activeFarmStatus === "all"
                || row.dataset.status === activeFarmStatus;
            const matchesKeyword = row.textContent
                .trim()
                .toLowerCase()
                .includes(keyword);
            const show =
                matchesWarehouse
                && matchesAnimal
                && matchesStatus
                && matchesKeyword;

            row.hidden = !show;
            if (show) {
                visible++;
            }
        });

        if (farmCustomerVisibleCount) {
            farmCustomerVisibleCount.textContent = String(visible);
        }
        if (farmCustomerFilterEmpty) {
            farmCustomerFilterEmpty.hidden = visible !== 0;
        }
    }

    farmCustomerSearch?.addEventListener("input", filterFarmCustomers);

    farmWarehouseCards.forEach(card => {
        card.addEventListener("click", () => {
            activeFarmWarehouse = card.dataset.farmWarehouse ?? "all";
            farmWarehouseCards.forEach(item => {
                const selected = item === card;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });
            filterFarmCustomers();
        });
    });

    farmAnimalTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeFarmAnimal = tab.dataset.farmAnimal ?? "all";
            farmAnimalTabs.forEach(item => {
                const selected = item === tab;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });
            filterFarmCustomers();
        });
    });

    farmStatusTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeFarmStatus = tab.dataset.farmStatus ?? "all";
            farmStatusTabs.forEach(item => {
                const selected = item === tab;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });
            filterFarmCustomers();
        });
    });

    all("[data-farm-status-form]").forEach(form => {
        form.addEventListener("submit", () => {
            const button = form.querySelector('button[type="submit"]');
            if (button) {
                button.disabled = true;
                button.textContent = "변경 중...";
            }
        });
    });

    document.querySelector("[data-order-cancel-form]")
        ?.addEventListener("submit", event => {
            if (!window.confirm(
                "주문을 취소하면 예약이 해제되며, 출고 완료 건은 LOT 재고가 원복됩니다. 계속할까요?"
            )) {
                event.preventDefault();
                return;
            }

            const submitButton = event.currentTarget.querySelector(
                'button[type="submit"], button:not([type])'
            );
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "취소 처리 중...";
            }
        });

    function selectDeliveryView(view) {
        activeDeliveryView = view;
        const isReady = view === "ready";
        const isCancelledOrders = view === "cancelled_orders";
        const isFarmCustomers = view === "farms";

        deliveryViewTabs.forEach(tab => {
            const selected = tab.dataset.deliveryView === view;
            tab.classList.toggle("active", selected);
            tab.setAttribute("aria-selected", String(selected));
        });

        if (readyDeliveryPanel) {
            readyDeliveryPanel.hidden =
                !(isReady || view === "all");
        }
        if (cancelledOrderPanel) {
            cancelledOrderPanel.hidden = !isCancelledOrders;
        }
        if (farmCustomerPanel) {
            farmCustomerPanel.hidden = !isFarmCustomers;
        }
        if (deliveryTrackingPanel) {
            deliveryTrackingPanel.hidden =
                isReady || isCancelledOrders || isFarmCustomers;
        }
        if (!isReady && !isCancelledOrders && !isFarmCustomers) {
            const copy = deliveryViewCopy[view] ?? deliveryViewCopy.all;
            if (deliveryPanelTitle) {
                deliveryPanelTitle.textContent = copy[0];
            }
            if (deliveryPanelDescription) {
                deliveryPanelDescription.textContent = copy[1];
            }
            if (deliveryStatusFilter) {
                deliveryStatusFilter.value = "all";
            }
            filterDeliveries();
        }

        const url = new URL(window.location.href);
        url.searchParams.set("view", view);
        window.history.replaceState({}, "", url);
    }

    deliveryViewTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            selectDeliveryView(tab.dataset.deliveryView ?? "all");
        });
    });

    if (deliveryViewTabs.length > 0) {
        const requestedDeliveryView =
            new URLSearchParams(window.location.search).get("view");
        const initialDeliveryView = deliveryViewTabs.some(tab =>
            tab.dataset.deliveryView === requestedDeliveryView
        )
            ? requestedDeliveryView
            : "all";
        selectDeliveryView(initialDeliveryView);

        const focusOrder = new URLSearchParams(window.location.search)
            .get("focusOrder");
        if (focusOrder) {
            window.setTimeout(() => {
                const row = document.getElementById(
                    `distribution-order-${focusOrder}`
                );
                row?.scrollIntoView({ behavior: "smooth", block: "center" });
            }, 120);
        }
    }

    const demoOrderLot = document.getElementById("demoOrderLot");
    const demoOrderQuantity =
        document.getElementById("demoOrderQuantity");
    const demoOrderDiscount =
        document.getElementById("demoOrderDiscount");
    const demoOrderStockInfo =
        document.getElementById("demoOrderStockInfo");
    const demoOrderTotal =
        document.getElementById("demoOrderTotal");
    const demoFarmCustomerId =
        document.getElementById("demoFarmCustomerId");
    const demoFarmSelection =
        document.getElementById("demoFarmSelection");
    const demoRecipientName = document.querySelector(
        '#demoOrderModal [name="recipientName"]'
    );
    const demoRecipientPhone = document.querySelector(
        '#demoOrderModal [name="recipientPhone"]'
    );

    function updateDemoOrderEstimate() {
        const option = demoOrderLot?.selectedOptions[0];
        const available = Number(option?.dataset.available ?? 0);
        const unitPrice = Number(option?.dataset.price ?? 0);
        const quantity = Math.max(
            0, Number(demoOrderQuantity?.value ?? 0)
        );
        const discount = Math.max(
            0, Number(demoOrderDiscount?.value ?? 0)
        );

        if (demoOrderQuantity) {
            demoOrderQuantity.max =
                available > 0 ? String(available) : "";
        }

        const formatter = new Intl.NumberFormat("ko-KR");
        if (demoOrderStockInfo) {
            demoOrderStockInfo.textContent = available > 0
                ? `가용 재고 ${formatter.format(available)}포`
                    + ` · 포당 ${formatter.format(unitPrice)}원`
                : "LOT를 선택하면 가용 수량과 주문 금액이 표시됩니다.";
        }

        if (demoOrderTotal) {
            const total = Math.max(
                unitPrice * quantity - discount, 0
            );
            demoOrderTotal.textContent =
                `${formatter.format(total)}원`;
        }
    }

    demoOrderLot?.addEventListener(
        "change", updateDemoOrderEstimate
    );
    demoOrderQuantity?.addEventListener(
        "input", updateDemoOrderEstimate
    );
    demoOrderDiscount?.addEventListener(
        "input", updateDemoOrderEstimate
    );

    const demoAddressSearch =
        document.getElementById("demoAddressSearch");
    const demoPostalCode =
        document.getElementById("demoPostalCode");
    const demoSelectedAddress =
        document.getElementById("demoSelectedAddress");
    const demoRoadAddress =
        document.getElementById("demoRoadAddress");
    const demoJibunAddress =
        document.getElementById("demoJibunAddress");
    const demoDetailAddress =
        document.getElementById("demoDetailAddress");
    const demoLatitude =
        document.getElementById("demoLatitude");
    const demoLongitude =
        document.getElementById("demoLongitude");
    const demoAddressMap =
        document.getElementById("demoAddressMap");
    const demoMapHint =
        document.getElementById("demoMapHint");
    const demoAddressSelectionStatus =
        document.getElementById("demoAddressSelectionStatus");
    let demoMap;
    let demoMapMarker;

    function clearDemoFarmSelection({ clearAddress = false } = {}) {
        if (demoFarmCustomerId) {
            demoFarmCustomerId.value = "";
        }
        if (demoFarmSelection) {
            demoFarmSelection.hidden = true;
        }
        if (!clearAddress) {
            return;
        }

        [
            demoRecipientName,
            demoRecipientPhone,
            demoPostalCode,
            demoSelectedAddress,
            demoRoadAddress,
            demoJibunAddress,
            demoDetailAddress,
            demoLatitude,
            demoLongitude
        ].forEach(input => {
            if (input) {
                input.value = "";
            }
        });
        if (demoAddressSelectionStatus) {
            demoAddressSelectionStatus.textContent =
                "선택된 주소가 없습니다.";
        }
        if (demoAddressMap) {
            demoAddressMap.hidden = true;
        }
        if (demoMapHint) {
            demoMapHint.textContent =
                "지도 미리보기는 주소 선택 후 표시됩니다.";
        }
    }

    document.getElementById("newDemoOrderButton")
        ?.addEventListener("click", () => {
            clearDemoFarmSelection({ clearAddress: true });
        });

    all(".farm-order-button").forEach(button => {
        button.addEventListener("click", () => {
            const farmName = button.dataset.farmName ?? "";
            const address = button.dataset.address ?? "";
            const postalCode = button.dataset.postalCode ?? "";
            const preferredFeed = button.dataset.preferredFeed ?? "";

            if (demoFarmCustomerId) {
                demoFarmCustomerId.value = button.dataset.farmId ?? "";
            }
            if (demoFarmSelection) {
                demoFarmSelection.hidden = false;
                const name = demoFarmSelection.querySelector("strong");
                if (name) {
                    name.textContent = farmName;
                }
            }
            if (demoRecipientName) {
                demoRecipientName.value =
                    button.dataset.representative ?? "";
            }
            if (demoRecipientPhone) {
                demoRecipientPhone.value = button.dataset.phone ?? "";
            }
            if (demoPostalCode) {
                demoPostalCode.value = postalCode;
            }
            if (demoSelectedAddress) {
                demoSelectedAddress.value = address;
            }
            if (demoRoadAddress) {
                demoRoadAddress.value = address;
            }
            if (demoJibunAddress) {
                demoJibunAddress.value = "";
            }
            if (demoDetailAddress) {
                demoDetailAddress.value = "";
            }
            if (demoLatitude) {
                demoLatitude.value = button.dataset.latitude ?? "";
            }
            if (demoLongitude) {
                demoLongitude.value = button.dataset.longitude ?? "";
            }
            if (demoAddressSelectionStatus) {
                demoAddressSelectionStatus.textContent =
                    `${postalCode} · ${farmName} 고객사 주소`;
            }
            if (demoAddressMap) {
                demoAddressMap.hidden = true;
            }
            if (demoMapHint) {
                demoMapHint.textContent =
                    `농장 등록 좌표 ${button.dataset.latitude}, `
                    + button.dataset.longitude;
            }

            if (demoOrderLot && preferredFeed) {
                const matchingOption = Array.from(demoOrderLot.options)
                    .find(option =>
                        option.textContent.includes(preferredFeed)
                        && Number(option.dataset.available ?? 0) > 0
                    );
                if (matchingOption) {
                    demoOrderLot.value = matchingOption.value;
                }
            }
            updateDemoOrderEstimate();
        });
    });

    function showDemoAddressOnMap(address) {
        if (!window.kakao?.maps?.load || !demoAddressMap) {
            if (demoMapHint) {
                demoMapHint.textContent =
                    "주소가 선택되었습니다. 지도 미리보기는 "
                    + "KAKAO_MAP_JAVASCRIPT_KEY 설정 시 활성화됩니다.";
            }
            return;
        }

        window.kakao.maps.load(() => {
            if (!window.kakao.maps.services) {
                if (demoMapHint) {
                    demoMapHint.textContent =
                        "카카오 지도 주소 변환 서비스를 사용할 수 없습니다.";
                }
                return;
            }
            const geocoder =
                new window.kakao.maps.services.Geocoder();

            geocoder.addressSearch(address, (results, status) => {
                if (
                    status
                    !== window.kakao.maps.services.Status.OK
                    || results.length === 0
                ) {
                    if (demoMapHint) {
                        demoMapHint.textContent =
                            "선택한 주소의 지도 좌표를 찾지 못했습니다.";
                    }
                    return;
                }

                const longitude = Number(results[0].x);
                const latitude = Number(results[0].y);
                const position = new window.kakao.maps.LatLng(
                    latitude, longitude
                );

                demoAddressMap.hidden = false;
                if (!demoMap) {
                    demoMap = new window.kakao.maps.Map(
                        demoAddressMap,
                        { center: position, level: 3 }
                    );
                } else {
                    demoMap.setCenter(position);
                }

                demoMapMarker?.setMap(null);
                demoMapMarker = new window.kakao.maps.Marker({
                    map: demoMap,
                    position
                });

                if (demoLatitude) {
                    demoLatitude.value = String(latitude);
                }
                if (demoLongitude) {
                    demoLongitude.value = String(longitude);
                }
                if (demoMapHint) {
                    demoMapHint.textContent =
                        `위도 ${latitude.toFixed(6)}`
                        + ` · 경도 ${longitude.toFixed(6)}`;
                }
            });
        });
    }

    demoAddressSearch?.addEventListener("click", () => {
        if (!window.kakao?.Postcode) {
            window.alert(
                "주소 검색 서비스를 불러오지 못했습니다. "
                + "인터넷 연결을 확인해 주세요."
            );
            return;
        }

        new window.kakao.Postcode({
            oncomplete(data) {
                const selectedAddress =
                    data.userSelectedType === "R"
                        ? data.roadAddress
                        : data.jibunAddress;

                clearDemoFarmSelection();
                if (demoPostalCode) {
                    demoPostalCode.value = data.zonecode ?? "";
                }
                if (demoSelectedAddress) {
                    demoSelectedAddress.value = selectedAddress;
                }
                if (demoRoadAddress) {
                    demoRoadAddress.value = data.roadAddress ?? "";
                }
                if (demoJibunAddress) {
                    demoJibunAddress.value = data.jibunAddress ?? "";
                }
                if (demoLatitude) {
                    demoLatitude.value = "";
                }
                if (demoLongitude) {
                    demoLongitude.value = "";
                }
                if (demoAddressSelectionStatus) {
                    demoAddressSelectionStatus.textContent =
                        `${data.zonecode} · 주소 선택 완료`;
                }

                demoDetailAddress?.focus();
                showDemoAddressOnMap(selectedAddress);
            }
        }).open();
    });

    const recurringReceiveModal =
        document.getElementById("recurringReceiveModal");
    const recurringManufacturedDate = recurringReceiveModal?.querySelector(
        '[name="manufacturedDate"]'
    );
    const recurringExpirationDate = recurringReceiveModal?.querySelector(
        '[name="expirationDate"]'
    );
    const shelfLifeHint = recurringReceiveModal?.querySelector(
        ".expiration-hint"
    );
    const recurringLotNo = recurringReceiveModal?.querySelector(
        '[name="lotNo"]'
    );

    function calculateExpirationDate() {
        const value = recurringManufacturedDate?.value;
        const months = Number(
            recurringReceiveModal?.dataset.shelfLifeMonths ?? 0
        );

        if (!value || months <= 0 || !recurringExpirationDate) {
            if (recurringExpirationDate) recurringExpirationDate.value = "";
            return;
        }

        const [year, month, day] = value.split("-").map(Number);
        const monthIndex = month - 1 + months;
        const targetYear = year + Math.floor(monthIndex / 12);
        const targetMonth = ((monthIndex % 12) + 12) % 12;
        const lastDay = new Date(
            Date.UTC(targetYear, targetMonth + 1, 0)
        ).getUTCDate();
        const targetDay = Math.min(day, lastDay);

        recurringExpirationDate.value = [
            targetYear,
            String(targetMonth + 1).padStart(2, "0"),
            String(targetDay).padStart(2, "0")
        ].join("-");

        if (recurringLotNo) {
            const categoryCode = {
                "소": "CATTLE",
                "돼지": "PIG",
                "조류(닭/오리)": "BIRD",
                "영양제": "SUP"
            }[recurringReceiveModal?.dataset.category] ?? "SUP";
            const productId = String(
                recurringReceiveModal?.dataset.productId ?? "1"
            ).padStart(3, "0");
            recurringLotNo.value =
                `LOT-${categoryCode}-${value.replaceAll("-", "")}-${productId}`;
        }

        if (shelfLifeHint) {
            shelfLifeHint.textContent =
                `상품 기준 ${months}개월이 자동 적용되었습니다.`;
        }
    }

    recurringManufacturedDate?.addEventListener(
        "change",
        calculateExpirationDate
    );

    all(".summary-receive-button").forEach(button => {
        button.addEventListener("click", () => {
            const select = document.querySelector(
                '#receiveModal select[name="productId"]'
            );

            if (select) {
                select.value = button.dataset.productId ?? "";
            }
        });
    });

    /*
     * 상품 삭제 전 실수 방지 확인
     */
    all(".product-delete-form").forEach(form => {
        form.addEventListener("submit", event => {
            const productName =
                form.dataset.productName ?? "선택한 상품";
            const confirmed = window.confirm(
                `'${productName}' 상품을 삭제할까요?\n`
                + "상품 목록과 운영 재고에서는 제외되며, "
                + "기존 LOT와 재고 이력은 보존됩니다."
            );

            if (!confirmed) {
                event.preventDefault();
            }
        });
    });

    /*
     * 테이블 페이지네이션
     */
    function createPager(table, options = {}) {
        const tbody = table?.tBodies?.[0];

        if (!tbody) {
            return null;
        }

        const rows = all("tr", tbody).filter(row =>
            !row.querySelector("td[colspan]")
        );

        if (rows.length === 0) {
            return null;
        }

        const wrap = table.closest(".table-wrap");

        if (!wrap) {
            return null;
        }

        let page = 1;
        let pageSize = options.pageSize ?? 8;
        let keyword = "";
        let category = "all";
        let sortKey = "";
        let sortDirection = "";

        const originalRowIndex = new Map(
            rows.map((row, index) => [row, index])
        );
        const sortButtons = options.sortButtonSelector
            ? all(options.sortButtonSelector, table)
            : [];

        function updateSortUi() {
            sortButtons.forEach(button => {
                const buttonKey = button.dataset.sortKey ?? "";
                const isActive =
                    buttonKey === sortKey && Boolean(sortDirection);
                const isAscending =
                    isActive && sortDirection === "asc";
                const ascendingLabel =
                    button.dataset.sortAscLabel ?? "오름차순";
                const descendingLabel =
                    button.dataset.sortDescLabel ?? "내림차순";
                const currentLabel = isAscending
                    ? ascendingLabel
                    : isActive
                        ? descendingLabel
                        : "기본 순서";
                const nextLabel = isAscending
                    ? descendingLabel
                    : ascendingLabel;
                const sortLabel =
                    button.dataset.sortLabel ?? "목록";
                const indicator = button.querySelector(
                    "[data-sort-indicator]"
                );

                button.dataset.sortDirection = isActive
                    ? sortDirection
                    : "none";
                button.classList.toggle("active", isActive);
                button.setAttribute(
                    "aria-label",
                    `${sortLabel} ${nextLabel}으로 정렬`
                );
                button.title = isActive
                    ? `${currentLabel} · 누르면 ${nextLabel}으로 변경`
                    : `${sortLabel} ${nextLabel}으로 정렬`;

                if (indicator) {
                    indicator.textContent = isAscending
                        ? "↑"
                        : isActive
                            ? "↓"
                            : "↕";
                }

                button.closest("th")?.setAttribute(
                    "aria-sort",
                    isAscending
                        ? "ascending"
                        : isActive
                            ? "descending"
                            : "none"
                );
            });
        }

        sortButtons.forEach(button => {
            button.addEventListener("click", () => {
                const selectedKey =
                    button.dataset.sortKey ?? "";
                sortDirection =
                    sortKey === selectedKey
                    && sortDirection === "asc"
                        ? "desc"
                        : "asc";
                sortKey = selectedKey;
                page = 1;
                updateSortUi();
                render();
            });
        });

        updateSortUi();

        const categories = [
            ...new Set(
                rows
                    .map(row => row.dataset.category)
                    .filter(Boolean)
            )
        ];

        let searchInput;
        let categorySelect;
        let sizeSelect;

        if (options.controls !== false) {
            const controls = document.createElement("div");
            controls.className = "inventory-table-tools";

            const filterGroup = document.createElement("div");
            filterGroup.className = "inventory-filter-group";

            searchInput = document.createElement("input");
            searchInput.type = "search";
            searchInput.className = "inventory-table-search";
            searchInput.placeholder =
                options.placeholder ?? "상품명, 제조사 또는 LOT 검색";
            searchInput.setAttribute("aria-label", searchInput.placeholder);
            filterGroup.append(searchInput);

            if (categories.length > 1 && options.categoryTabs) {
                const tabs = document.createElement("div");
                tabs.className = "category-tabs registered-product-tabs";
                tabs.setAttribute("role", "tablist");
                tabs.setAttribute("aria-label", "등록 상품 카테고리");

                const values = ["all", ...categories];
                values.forEach(value => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.className = "category-tab";
                    button.classList.toggle("active", value === "all");
                    button.dataset.category = value;
                    button.setAttribute("role", "tab");
                    button.setAttribute(
                        "aria-selected",
                        String(value === "all")
                    );

                    const label = value === "all"
                        ? "전체"
                        : value === "조류(닭/오리)"
                            ? "조류"
                            : value;
                    const count = value === "all"
                        ? rows.length
                        : rows.filter(row =>
                            row.dataset.category === value
                        ).length;

                    button.append(
                        document.createTextNode(label),
                        Object.assign(
                            document.createElement("span"),
                            { textContent: String(count) }
                        )
                    );

                    button.addEventListener("click", () => {
                        category = value;
                        page = 1;
                        tabs.querySelectorAll(".category-tab")
                            .forEach(item => {
                                const selected = item === button;
                                item.classList.toggle("active", selected);
                                item.setAttribute(
                                    "aria-selected",
                                    String(selected)
                                );
                            });
                        render();
                    });

                    tabs.append(button);
                });

                filterGroup.append(tabs);
            } else if (
                categories.length > 1
                && options.categorySelect !== false
            ) {
                categorySelect = document.createElement("select");
                categorySelect.className = "inventory-filter-select";
                categorySelect.setAttribute("aria-label", "카테고리 선택");
                categorySelect.add(new Option("전체 카테고리", "all"));

                categories.forEach(value => {
                    const label =
                        value === "조류(닭/오리)" ? "조류" : value;
                    categorySelect.add(new Option(label, value));
                });

                filterGroup.append(categorySelect);
            }

            sizeSelect = document.createElement("select");
            sizeSelect.className = "inventory-filter-select";
            sizeSelect.setAttribute("aria-label", "페이지당 표시 개수");

            [8, 10, 20].forEach(size => {
                sizeSelect.add(
                    new Option(`${size}개씩 보기`, String(size))
                );
            });
            sizeSelect.value = String(pageSize);

            const result = document.createElement("span");
            result.className = "inventory-filter-count";
            result.dataset.pagerCount = "";

            controls.append(filterGroup, sizeSelect, result);
            wrap.before(controls);

            searchInput.addEventListener("input", () => {
                keyword = searchInput.value.trim().toLowerCase();
                page = 1;
                render();
            });

            categorySelect?.addEventListener("change", () => {
                category = categorySelect.value;
                page = 1;
                render();
            });

            sizeSelect.addEventListener("change", () => {
                pageSize = Number(sizeSelect.value);
                page = 1;
                render();
            });
        }

        const footer = document.createElement("div");
        footer.className = "inventory-pagination";

        const rangeText = document.createElement("span");
        rangeText.className = "inventory-page-range";

        const buttons = document.createElement("div");
        buttons.className = "inventory-page-buttons";

        footer.append(rangeText, buttons);
        wrap.after(footer);

        function matchingRows() {
            const filtered = rows.filter(row => {
                const text = row.textContent.trim().toLowerCase();
                const matchesKeyword = text.includes(keyword);
                const matchesCategory =
                    category === "all"
                    || row.dataset.category === category;
                const matchesExternal =
                    options.filter ? options.filter(row) : true;

                return matchesKeyword
                    && matchesCategory
                    && matchesExternal;
            });

            if (sortKey && sortDirection && options.sorter) {
                filtered.sort((firstRow, secondRow) => {
                    const compared = options.sorter(
                        firstRow,
                        secondRow,
                        sortKey,
                        sortDirection
                    );

                    return compared
                        || originalRowIndex.get(firstRow)
                        - originalRowIndex.get(secondRow);
                });
            }

            return filtered;
        }

        function pageButton(label, targetPage, disabled, active = false) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "inventory-page-button";
            button.textContent = label;
            button.disabled = disabled;
            button.classList.toggle("active", active);

            button.addEventListener("click", () => {
                page = targetPage;
                render();
            });

            return button;
        }

        function render() {
            const filtered = matchingRows();
            const totalPages = Math.max(
                1,
                Math.ceil(filtered.length / pageSize)
            );

            page = Math.min(page, totalPages);

            const first = (page - 1) * pageSize;
            const last = Math.min(first + pageSize, filtered.length);
            const visible = new Set(filtered.slice(first, last));

            if (sortKey && sortDirection && options.sorter) {
                const filteredRows = new Set(filtered);
                const remainingRows = rows.filter(
                    row => !filteredRows.has(row)
                );

                [...filtered, ...remainingRows].forEach(row => {
                    tbody.append(row);
                });
            }

            rows.forEach(row => {
                row.hidden = !visible.has(row);
            });

            rangeText.textContent = filtered.length === 0
                ? "검색 결과 0개"
                : `총 ${filtered.length}개 중 ${first + 1}–${last}`;

            const count = footer.parentElement?.querySelector(
                "[data-pager-count]"
            );

            if (count) {
                count.textContent = `${filtered.length}개`;
            }

            buttons.replaceChildren();
            buttons.append(
                pageButton("‹", page - 1, page === 1)
            );

            const start = Math.max(1, page - 2);
            const end = Math.min(totalPages, start + 4);

            for (let number = start; number <= end; number++) {
                buttons.append(
                    pageButton(
                        String(number),
                        number,
                        false,
                        number === page
                    )
                );
            }

            buttons.append(
                pageButton("›", page + 1, page === totalPages)
            );

            footer.hidden =
                filtered.length <= pageSize && rows.length <= pageSize;

            options.onRender?.(filtered.length);
        }

        render();

        return {
            refresh(resetPage = false) {
                if (resetPage) {
                    page = 1;
                }
                render();
            }
        };
    }

    /*
     * 등록 상품, 부족 재고, 활성 LOT, 정기 배송, 유통기한 임박
     */
    let activeLotCategory = "all";
    let activeLotPager;

    all(
        ".summary-view table, #expiryManagement table"
    ).forEach(table => {
        if (
            table.id !== "stockTable"
            && !table.hasAttribute("data-no-auto-pager")
        ) {
            const isCancelledOutbound =
                table.classList.contains("cancelled-outbound-table");
            const isActiveLot = table.id === "activeLotTable";
            const isRegisteredProduct =
                table.id === "registeredProductTable";
            const pager = createPager(table, {
                pageSize: isCancelledOutbound ? 10 : 8,
                categorySelect: !isActiveLot,
                categoryTabs: isRegisteredProduct,
                filter: isActiveLot
                    ? row => activeLotCategory === "all"
                        || row.dataset.category === activeLotCategory
                    : undefined,
                placeholder: isCancelledOutbound
                    ? "출고 번호, 상품, LOT 또는 취소 사유 검색"
                    : isRegisteredProduct
                        ? "상품명 또는 제조사 검색"
                        : undefined,
                sortButtonSelector: isRegisteredProduct
                    ? "[data-sort-key]"
                    : undefined,
                sorter: isRegisteredProduct
                    ? (firstRow, secondRow, key, direction) => {
                        const directionFactor =
                            direction === "asc" ? 1 : -1;

                        if (key === "category") {
                            return (
                                firstRow.dataset.category ?? ""
                            ).localeCompare(
                                secondRow.dataset.category ?? "",
                                "ko"
                            ) * directionFactor;
                        }

                        if (key === "manufacturer") {
                            return (
                                firstRow.dataset.manufacturer ?? ""
                            ).localeCompare(
                                secondRow.dataset.manufacturer ?? "",
                                "ko"
                            ) * directionFactor;
                        }

                        if (key === "weight") {
                            return (
                                Number(firstRow.dataset.weight)
                                - Number(secondRow.dataset.weight)
                            ) * directionFactor;
                        }

                        if (key === "lot") {
                            const lotDifference =
                                Number(firstRow.dataset.lotCount)
                                - Number(secondRow.dataset.lotCount);

                            if (lotDifference !== 0) {
                                return lotDifference * directionFactor;
                            }

                            return (
                                Number(firstRow.dataset.lotQuantity)
                                - Number(secondRow.dataset.lotQuantity)
                            ) * directionFactor;
                        }

                        if (key === "expiration") {
                            const firstDate =
                                firstRow.dataset.expiration ?? "";
                            const secondDate =
                                secondRow.dataset.expiration ?? "";

                            if (!firstDate && !secondDate) {
                                return 0;
                            }
                            if (!firstDate) {
                                return 1;
                            }
                            if (!secondDate) {
                                return -1;
                            }

                            return firstDate.localeCompare(secondDate)
                                * directionFactor;
                        }

                        return 0;
                    }
                    : undefined
            });

            if (isActiveLot) {
                activeLotPager = pager;
            }
        }
    });

    const activeLotCategoryTabs = all(
        ".active-lot-category-tabs .category-tab[data-category]"
    );

    activeLotCategoryTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeLotCategory = tab.dataset.category ?? "all";

            activeLotCategoryTabs.forEach(item => {
                const selected = item === tab;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });

            activeLotPager?.refresh(true);
        });
    });

    /*
     * 5개 거점 창고별 상품 배치 계획
     */
    const warehousePlanPanel = document.querySelector(
        '[data-summary-panel="warehouses"]'
    );
    const warehouseTable =
        document.getElementById("warehouseAllocationTable");
    const warehouseCards = all(
        ".warehouse-selector-card[data-warehouse]",
        warehousePlanPanel ?? document
    );
    const warehouseCategoryTabs = all(
        ".warehouse-category-tabs .category-tab[data-category]",
        warehousePlanPanel ?? document
    );
    const selectedWarehouseName = document.querySelector(
        "[data-selected-warehouse-name]"
    );
    let activeWarehouse =
        warehouseCards[0]?.dataset.warehouse ?? "W01";
    let activeWarehouseCategory = "all";

    const warehousePager = createPager(warehouseTable, {
        controls: false,
        pageSize: 10,
        filter: row => {
            const matchesWarehouse =
                row.dataset.warehouse === activeWarehouse;
            const matchesCategory =
                activeWarehouseCategory === "all"
                || row.dataset.category === activeWarehouseCategory;
            return matchesWarehouse && matchesCategory;
        }
    });

    function selectWarehouse(card) {
        activeWarehouse = card.dataset.warehouse ?? "W01";
        activeWarehouseCategory = "all";

        warehouseCards.forEach(item => {
            const selected = item === card;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-selected", String(selected));
        });

        warehouseCategoryTabs.forEach(tab => {
            const selected = tab.dataset.category === "all";
            tab.classList.toggle("active", selected);
            tab.setAttribute("aria-selected", String(selected));
        });

        if (selectedWarehouseName) {
            selectedWarehouseName.textContent =
                card.dataset.warehouseName ?? activeWarehouse;
        }

        warehousePager?.refresh(true);
    }

    warehouseCards.forEach(card => {
        card.addEventListener("click", () => selectWarehouse(card));
    });

    warehouseCategoryTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeWarehouseCategory = tab.dataset.category ?? "all";

            warehouseCategoryTabs.forEach(item => {
                const selected = item === tab;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });

            warehousePager?.refresh(true);
        });
    });

    /*
     * 정기 입고: 창고별 1·2차 월간 일정
     */
    const recurringWarehousePanel = document.querySelector(
        '[data-summary-panel="recurring"]'
    );
    const recurringWarehouseTable =
        document.getElementById("recurringWarehouseTable");
    const recurringWarehouseCards = all(
        ".recurring-warehouse-card[data-warehouse]",
        recurringWarehousePanel ?? document
    );
    const recurringWarehouseCategoryTabs = all(
        ".recurring-warehouse-category-tabs "
        + ".category-tab[data-category]",
        recurringWarehousePanel ?? document
    );
    const selectedRecurringWarehouseName = document.querySelector(
        "[data-selected-recurring-warehouse-name]"
    );
    let activeRecurringWarehouse =
        recurringWarehouseCards[0]?.dataset.warehouse ?? "W01";
    let activeRecurringCategory = "all";

    const recurringWarehousePager = createPager(
        recurringWarehouseTable,
        {
            controls: false,
            pageSize: 10,
            filter: row => {
                const matchesWarehouse =
                    row.dataset.warehouse === activeRecurringWarehouse;
                const matchesCategory =
                    activeRecurringCategory === "all"
                    || row.dataset.category === activeRecurringCategory;
                return matchesWarehouse && matchesCategory;
            }
        }
    );

    function selectRecurringWarehouse(card) {
        activeRecurringWarehouse =
            card.dataset.warehouse ?? "W01";
        activeRecurringCategory = "all";

        recurringWarehouseCards.forEach(item => {
            const selected = item === card;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-selected", String(selected));
        });

        recurringWarehouseCategoryTabs.forEach(tab => {
            const selected = tab.dataset.category === "all";
            tab.classList.toggle("active", selected);
            tab.setAttribute("aria-selected", String(selected));
        });

        if (selectedRecurringWarehouseName) {
            selectedRecurringWarehouseName.textContent =
                card.dataset.warehouseName
                ?? activeRecurringWarehouse;
        }

        recurringWarehousePager?.refresh(true);
    }

    recurringWarehouseCards.forEach(card => {
        card.addEventListener(
            "click",
            () => selectRecurringWarehouse(card)
        );
    });

    recurringWarehouseCategoryTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeRecurringCategory =
                tab.dataset.category ?? "all";

            recurringWarehouseCategoryTabs.forEach(item => {
                const selected = item === tab;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });

            recurringWarehousePager?.refresh(true);
        });
    });

    /*
     * 전체 재고: 5개 거점 창고와 축종 기준으로 표시
     */
    const warehouseStockPanel = document.querySelector(
        '[data-summary-panel="stock"]'
    );
    const warehouseStockTable =
        document.getElementById("warehouseStockTable");
    const warehouseStockCards = all(
        ".warehouse-stock-selector-card[data-warehouse]",
        warehouseStockPanel ?? document
    );
    const warehouseStockCategoryTabs = all(
        ".warehouse-stock-category-tabs .category-tab[data-category]",
        warehouseStockPanel ?? document
    );
    const selectedStockWarehouseName = document.querySelector(
        "[data-selected-stock-warehouse-name]"
    );
    let activeStockWarehouse =
        warehouseStockCards[0]?.dataset.warehouse ?? "W01";
    let activeStockCategory = "all";

    const warehouseStockPager = createPager(warehouseStockTable, {
        controls: false,
        pageSize: 10,
        filter: row => {
            const matchesWarehouse =
                row.dataset.warehouse === activeStockWarehouse;
            const matchesCategory =
                activeStockCategory === "all"
                || row.dataset.category === activeStockCategory;
            return matchesWarehouse && matchesCategory;
        }
    });

    function selectStockWarehouse(card) {
        activeStockWarehouse = card.dataset.warehouse ?? "W01";
        activeStockCategory = "all";

        warehouseStockCards.forEach(item => {
            const selected = item === card;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-selected", String(selected));
        });

        warehouseStockCategoryTabs.forEach(tab => {
            const selected = tab.dataset.category === "all";
            tab.classList.toggle("active", selected);
            tab.setAttribute("aria-selected", String(selected));
        });

        if (selectedStockWarehouseName) {
            selectedStockWarehouseName.textContent =
                card.dataset.warehouseName ?? activeStockWarehouse;
        }

        warehouseStockPager?.refresh(true);
    }

    warehouseStockCards.forEach(card => {
        card.addEventListener(
            "click",
            () => selectStockWarehouse(card)
        );
    });

    warehouseStockCategoryTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeStockCategory = tab.dataset.category ?? "all";

            warehouseStockCategoryTabs.forEach(item => {
                const selected = item === tab;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });

            warehouseStockPager?.refresh(true);
        });
    });

    /*
     * 상단의 컴팩트 요약 탭
     */
    const summaryCards = all("[data-summary-view]");
    const summaryPanels = all("[data-summary-panel]");
    const inventoryStats = document.querySelector(".inventory-stats");
    const summaryDetailPanel = document.querySelector(
        ".summary-detail-panel"
    );
    const expiryPanel = document.getElementById("expiryManagement");
    const inventoryPageTitle = document.getElementById("inventoryPageTitle");
    const inventoryPageDescription = document.getElementById(
        "inventoryPageDescription"
    );
    const inventoryReceiveButton = document.getElementById(
        "inventoryReceiveButton"
    );
    const inventoryAlertCenter = document.querySelector(
        ".inventory-alert-center"
    );
    const inventoryAlertTitle = document.getElementById(
        "inventoryAlertTitle"
    );
    const inventoryAlertDescription = document.getElementById(
        "inventoryAlertDescription"
    );
    const unlocatedAlert = document.querySelector(
        ".inventory-alert-card.neutral"
    );

    function selectSummaryView(view, scroll = true) {
        summaryCards.forEach(card => {
            const selected = card.dataset.summaryView === view;
            card.classList.toggle("active", selected);
            card.setAttribute("aria-selected", String(selected));
            card.setAttribute("tabindex", selected ? "0" : "-1");
        });

        const isExpiry = view === "expiry";
        const isShipments = view === "shipments";

        if (inventoryStats) {
            inventoryStats.hidden = isShipments;
        }

        if (inventoryPageTitle) {
            inventoryPageTitle.textContent = isShipments
                ? "출고 대기 주문"
                : "재고 관리";
        }

        if (inventoryPageDescription) {
            inventoryPageDescription.textContent = isShipments
                ? "출고 대기 주문을 피킹·검수·출고 완료 순서로 처리합니다."
                : "LOT 단위로 입고하고 유통기한이 빠른 재고부터 출고합니다.";
        }

        if (inventoryReceiveButton) {
            inventoryReceiveButton.hidden = isShipments;
        }

        if (inventoryAlertCenter) {
            inventoryAlertCenter.classList.toggle(
                "shipment-context-alert",
                isShipments
            );
        }

        if (inventoryAlertTitle) {
            inventoryAlertTitle.textContent = isShipments
                ? "출고 참고 알림"
                : "재고 알림";
        }

        if (inventoryAlertDescription) {
            inventoryAlertDescription.textContent = isShipments
                ? "재고 부족·유통기한 임박 상품을 출고 전에 확인하세요."
                : "확인이 필요한 항목을 자동 집계합니다.";
        }

        if (unlocatedAlert) {
            unlocatedAlert.classList.toggle(
                "shipment-alert-hide",
                isShipments
            );
        }

        if (summaryDetailPanel) {
            summaryDetailPanel.hidden = isExpiry;
        }

        if (expiryPanel) {
            expiryPanel.hidden = !isExpiry;
        }

        summaryPanels.forEach(panel => {
            panel.hidden =
                isExpiry || panel.dataset.summaryPanel !== view;
        });

        const url = new URL(window.location.href);
        url.searchParams.set("view", view);
        window.history.replaceState({}, "", url);

        if (scroll) {
            (isExpiry ? expiryPanel : summaryDetailPanel)
                ?.scrollIntoView({
                    behavior: "smooth",
                    block: "start"
                });
        }
    }

    summaryCards.forEach(card => {
        card.addEventListener("click", () => {
            selectSummaryView(card.dataset.summaryView);
        });

        card.addEventListener("keydown", event => {
            if (!["ArrowLeft", "ArrowRight"].includes(event.key)) {
                return;
            }

            const index = summaryCards.indexOf(card);
            const direction = event.key === "ArrowRight" ? 1 : -1;
            const nextIndex =
                (index + direction + summaryCards.length)
                % summaryCards.length;

            summaryCards[nextIndex].focus();
            summaryCards[nextIndex].click();
        });
    });

    if (summaryCards.length > 0 || summaryPanels.length > 0) {
        const requestedView =
            new URLSearchParams(window.location.search).get("view");
        const availableViews = summaryCards.length > 0
            ? summaryCards.map(card => card.dataset.summaryView)
            : summaryPanels.map(panel => panel.dataset.summaryPanel);
        const initialView = availableViews.includes(requestedView)
            ? requestedView
            : "registered";

        selectSummaryView(initialView, false);
    }

    const defectRows = all("[data-defect-row]");
    const defectKeyword = document.querySelector("[data-defect-keyword]");
    const defectStatus = document.querySelector("[data-defect-status]");
    const defectType = document.querySelector("[data-defect-type]");
    const defectVisible = document.querySelector("[data-defect-visible]");

    function filterDefects() {
        const keyword = (defectKeyword?.value || "").trim().toLowerCase();
        const status = defectStatus?.value || "ALL";
        const type = defectType?.value || "ALL";
        let visible = 0;
        defectRows.forEach(row => {
            const matches = (!keyword || (row.dataset.search || "").toLowerCase().includes(keyword))
                && (status === "ALL" || row.dataset.status === status)
                && (type === "ALL" || row.dataset.type === type);
            row.hidden = !matches;
            if (matches) visible += 1;
        });
        if (defectVisible) defectVisible.textContent = String(visible);
    }

    defectKeyword?.addEventListener("input", filterDefects);
    defectStatus?.addEventListener("change", filterDefects);
    defectType?.addEventListener("change", filterDefects);
});

function openEntityModal(modalId, fieldName, button) {
    const modal = document.getElementById(modalId);

    if (!modal) {
        return;
    }

    const hiddenInput = modal.querySelector(`[name="${fieldName}"]`);
    const selectedName = modal.querySelector(".selected-name");

    if (hiddenInput) {
        hiddenInput.value = button.dataset.id ?? "";
    }

    if (selectedName) {
        selectedName.textContent = button.dataset.name ?? "";
    }

    if (!modal.open) {
        modal.showModal();
    }
}

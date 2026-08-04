(() => {
    function initializeFarmOrderButtons() {
        const modal = document.getElementById("demoOrderModal");
        const farmCustomerId =
            document.getElementById("demoFarmCustomerId");
        const farmSelection =
            document.getElementById("demoFarmSelection");
        const recipientName = modal?.querySelector(
            '[name="recipientName"]'
        );
        const recipientPhone = modal?.querySelector(
            '[name="recipientPhone"]'
        );
        const postalCode = document.getElementById("demoPostalCode");
        const selectedAddress =
            document.getElementById("demoSelectedAddress");
        const roadAddress =
            document.getElementById("demoRoadAddress");
        const jibunAddress =
            document.getElementById("demoJibunAddress");
        const detailAddress =
            document.getElementById("demoDetailAddress");
        const latitude = document.getElementById("demoLatitude");
        const longitude = document.getElementById("demoLongitude");
        const addressStatus =
            document.getElementById("demoAddressSelectionStatus");
        const mapPreview = document.getElementById("demoAddressMap");
        const mapHint = document.getElementById("demoMapHint");
        const orderLot = document.getElementById("demoOrderLot");

        document.querySelectorAll(".farm-order-button")
            .forEach(button => {
                button.addEventListener("click", () => {
                    const farmName = button.dataset.farmName ?? "";
                    const address = button.dataset.address ?? "";
                    const zip = button.dataset.postalCode ?? "";
                    const preferredFeed =
                        button.dataset.preferredFeed ?? "";

                    if (farmCustomerId) {
                        farmCustomerId.value =
                            button.dataset.farmId ?? "";
                    }
                    if (farmSelection) {
                        farmSelection.hidden = false;
                        const name = farmSelection.querySelector("strong");
                        if (name) {
                            name.textContent = farmName;
                        }
                    }
                    if (recipientName) {
                        recipientName.value =
                            button.dataset.representative ?? "";
                    }
                    if (recipientPhone) {
                        recipientPhone.value =
                            button.dataset.phone ?? "";
                    }
                    if (postalCode) {
                        postalCode.value = zip;
                    }
                    if (selectedAddress) {
                        selectedAddress.value = address;
                    }
                    if (roadAddress) {
                        roadAddress.value = address;
                    }
                    if (jibunAddress) {
                        jibunAddress.value = "";
                    }
                    if (detailAddress) {
                        detailAddress.value = "";
                    }
                    if (latitude) {
                        latitude.value =
                            button.dataset.latitude ?? "";
                    }
                    if (longitude) {
                        longitude.value =
                            button.dataset.longitude ?? "";
                    }
                    if (addressStatus) {
                        addressStatus.textContent =
                            `${zip} · ${farmName} 고객사 주소`;
                    }
                    if (mapPreview) {
                        mapPreview.hidden = true;
                    }
                    if (mapHint) {
                        mapHint.textContent =
                            `농장 등록 좌표 ${button.dataset.latitude}, `
                            + button.dataset.longitude;
                    }

                    if (orderLot && preferredFeed) {
                        const matchingOption =
                            Array.from(orderLot.options).find(option =>
                                option.textContent.includes(preferredFeed)
                                && Number(
                                    option.dataset.available ?? 0
                                ) > 0
                            );
                        if (matchingOption) {
                            orderLot.value = matchingOption.value;
                            orderLot.dispatchEvent(new Event("change"));
                        }
                    }

                    if (modal?.showModal && !modal.open) {
                        modal.showModal();
                    }
                });
            });
    }

    if (document.readyState === "loading") {
        document.addEventListener(
            "DOMContentLoaded",
            initializeFarmOrderButtons
        );
    } else {
        initializeFarmOrderButtons();
    }
})();

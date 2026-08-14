(() => {
    function initializeFarmCustomerFilters() {
        const all = selector => Array.from(
            document.querySelectorAll(selector)
        );
        const farmCustomerSearch =
            document.getElementById("farmCustomerSearch");
        const farmWarehouseCards = all(
            ".farmWarehouseCard[data-farm-warehouse]"
        );
        const farmAnimalTabs = all(
            ".farmAnimalTabs [data-farm-animal]"
        );
        const farmStatusTabs = all(
            ".farmStatusTabs [data-farm-status]"
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

        function selectOne(items, selectedItem) {
            items.forEach(item => {
                const selected = item === selectedItem;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-selected", String(selected));
            });
        }

        farmCustomerSearch?.addEventListener(
            "input", filterFarmCustomers
        );

        farmWarehouseCards.forEach(card => {
            card.addEventListener("click", () => {
                activeFarmWarehouse =
                    card.dataset.farmWarehouse ?? "all";
                selectOne(farmWarehouseCards, card);
                filterFarmCustomers();
            });
        });

        farmAnimalTabs.forEach(tab => {
            tab.addEventListener("click", () => {
                activeFarmAnimal = tab.dataset.farmAnimal ?? "all";
                selectOne(farmAnimalTabs, tab);
                filterFarmCustomers();
            });
        });

        farmStatusTabs.forEach(tab => {
            tab.addEventListener("click", () => {
                activeFarmStatus = tab.dataset.farmStatus ?? "all";
                selectOne(farmStatusTabs, tab);
                filterFarmCustomers();
            });
        });

        all("[data-farm-status-form]").forEach(form => {
            form.addEventListener("submit", () => {
                const button = form.querySelector(
                    'button[type="submit"]'
                );
                if (button) {
                    button.disabled = true;
                    button.textContent = "변경 중...";
                }
            });
        });

        filterFarmCustomers();
    }

    if (document.readyState === "loading") {
        document.addEventListener(
            "DOMContentLoaded",
            initializeFarmCustomerFilters
        );
    } else {
        initializeFarmCustomerFilters();
    }
})();

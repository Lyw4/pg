(() => {
    "use strict";

    const form = document.querySelector("#admin-product-form");
    const table = document.querySelector("#admin-product-table");
    const productCount = document.querySelector("#admin-product-count");
    const message = document.querySelector("#admin-message");
    const cancelEditButton = document.querySelector("#cancel-edit");
    const editorTitle = document.querySelector("#editor-title");
    const editorEyebrow = document.querySelector("#editor-eyebrow");
    const saveButton = document.querySelector("#save-product");
    const toast = document.querySelector("#toast");
    const imageFile = document.querySelector("#productImage");
    let previewObjectUrl = null;

    const fieldIds = [
        "manufacturerName", "productName", "animalType", "feedStage", "description",
        "weightKg", "price", "originalPrice", "badge", "proteinPercent", "fatPercent",
        "fiberPercent", "calciumPercent", "imageUrl",
        "lotNumber", "manufacturedDate", "expirationDate", "lotQuantity"
    ];

    const state = {
        products: [],
        editingId: null,
        query: "",
        animal: "ALL",
        stock: "ALL",
        event: "ALL"
    };

    const fields = Object.fromEntries(
        fieldIds.map((id) => [id, document.getElementById(id)])
    );

    const animalLabels = {
        CATTLE: "한우",
        DAIRY_CATTLE: "젖소",
        PIG: "돼지",
        CHICKEN: "닭",
        DUCK: "오리",
        PET: "반려동물",
        SUPPLEMENT: "영양제"
    };

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#039;");
    }

    function number(value) {
        return Number(value ?? 0).toLocaleString("ko-KR");
    }

    function isoDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
    }

    function showToast(text, isError = false) {
        toast.textContent = text;
        toast.classList.toggle("error", isError);
        toast.classList.add("show");
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2600);
    }

    async function readError(response) {
        try {
            const body = await response.json();
            return body.message || body.error || `요청 실패 (${response.status})`;
        } catch {
            return `요청 실패 (${response.status})`;
        }
    }

    async function loadProducts() {
        table.innerHTML = "<tr><td colspan=\"7\">상품을 불러오는 중입니다.</td></tr>";

        try {
            // 고객 카탈로그(/api/products)는 판매 중인 상품만 돌려주므로
            // 판매 중지한 상품이 목록에서 사라져 되돌릴 수 없습니다.
            const response = await fetch("/api/admin/products");
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            state.products = await response.json();
            renderProducts();
        } catch (error) {
            table.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
            showToast(error.message, true);
        }
    }

    function renderProducts() {
        productCount.textContent = state.products.length;
        document.querySelector("#metric-total").textContent = number(state.products.length);
        document.querySelector("#metric-soldout").textContent = number(state.products.filter((product) => product.stock < 1).length);
        document.querySelector("#metric-low").textContent = number(state.products.filter((product) => product.stock > 0 && product.stock <= 10).length);
        // lots에는 판매 가능 기간이 남은 LOT만 담기므로, 이미 만료된 LOT은
        // 이 목록에 없습니다. 만료 LOT까지 세려면 서버가 별도로 내려줘야 합니다.
        // 현재 값은 "판매 가능하지만 30일 이내 만료" 건수입니다.
        document.querySelector("#metric-expiry").textContent = number(state.products.flatMap((product) => product.lots || []).filter((lot) => lot.daysRemaining <= 30).length);

        const query = state.query.toLowerCase();
        const products = state.products.filter((product) => {
            const queryMatch = !query || `${product.name} ${product.productCode} ${product.lot || ""}`.toLowerCase().includes(query);
            const animalMatch = state.animal === "ALL"
                || (state.animal === "CATTLE_GROUP" && ["CATTLE", "DAIRY_CATTLE"].includes(product.animalType))
                || (state.animal === "POULTRY_GROUP" && ["CHICKEN", "DUCK"].includes(product.animalType))
                || state.animal === product.animalType;
            const stockMatch = state.stock === "ALL"
                || (state.stock === "AVAILABLE" && product.stock > 10)
                || (state.stock === "LOW" && product.stock > 0 && product.stock <= 10)
                || (state.stock === "SOLDOUT" && product.stock < 1);
            const eventMatch = state.event === "ALL"
                || (state.event === "EVENT" && Boolean(product.badge || product.originalPrice))
                || (state.event === "NORMAL" && !product.badge && !product.originalPrice);
            return queryMatch && animalMatch && stockMatch && eventMatch;
        });

        if (!products.length) {
            table.innerHTML = "<tr><td colspan=\"7\">조건에 맞는 상품이 없습니다.</td></tr>";
            return;
        }

        table.innerHTML = products.map((product) => {
            const stopped = product.active === false;
            const stockLabel = stopped
                ? "판매 중지"
                : product.stock < 1 ? "품절" : product.stock <= 10 ? "재고 부족" : "판매 중";
            const stockClass = stopped || product.stock < 1
                ? "sold-out"
                : product.stock <= 10 ? "low-stock" : "available";
            return `
            <tr>
                <td>
                    <small>${escapeHtml(product.productCode || `FF-P${product.id}`)}</small>
                    <strong>${escapeHtml(product.name)}</strong>
                    <small>${escapeHtml(product.manufacturer)} · ${escapeHtml(product.stage)}</small>
                </td>
                <td>${escapeHtml(product.animal || animalLabels[product.animalType])}</td>
                <td><strong>${number(product.weight)}kg / 포</strong><small>${number(product.price)}원</small></td>
                <td><strong>${escapeHtml(product.lot || "재고 LOT 없음")}</strong><small>${escapeHtml(product.expiry || "-")}</small></td>
                <td><span class="admin-status ${stockClass}">${stockLabel}</span><small>${number(product.stock)}포</small></td>
                <td>${product.badge || product.originalPrice ? `<span class="admin-event">${escapeHtml(product.badge || "할인")}</span>` : "-"}</td>
                <td>
                    <button type="button" data-action="edit" data-id="${product.id}">${stopped ? "수정 후 재개" : "수정"}</button>
                    ${stopped
                        ? ""
                        : `<button type="button" class="delete" data-action="delete" data-id="${product.id}">판매 중지</button>`}
                </td>
            </tr>
        `;}).join("");
    }

    function payloadFromForm() {
        return {
            manufacturerName: fields.manufacturerName.value.trim(),
            name: fields.productName.value.trim(),
            animalType: fields.animalType.value,
            feedStage: fields.feedStage.value.trim(),
            description: fields.description.value.trim(),
            weightKg: Number(fields.weightKg.value),
            price: Number(fields.price.value),
            originalPrice: fields.originalPrice.value ? Number(fields.originalPrice.value) : null,
            proteinPercent: Number(fields.proteinPercent.value),
            fatPercent: Number(fields.fatPercent.value),
            fiberPercent: Number(fields.fiberPercent.value),
            calciumPercent: Number(fields.calciumPercent.value),
            imageUrl: fields.imageUrl.value.trim() || null,
            badge: fields.badge.value.trim() || null,
            lotNumber: fields.lotNumber.value.trim(),
            manufacturedDate: fields.manufacturedDate.value,
            expirationDate: fields.expirationDate.value,
            lotQuantity: Number(fields.lotQuantity.value)
        };
    }

    function resetForm() {
        state.editingId = null;
        form.reset();
        fields.manufacturerName.value = "피드플로우 협력사";
        fields.animalType.value = "CATTLE";
        fields.weightKg.value = "25";
        fields.proteinPercent.value = "15";
        fields.fatPercent.value = "3";
        fields.fiberPercent.value = "8";
        fields.calciumPercent.value = "1";
        fields.lotQuantity.value = "0";

        const today = new Date();
        const nextYear = new Date(today);
        nextYear.setFullYear(today.getFullYear() + 1);
        fields.manufacturedDate.value = isoDate(today);
        fields.expirationDate.value = isoDate(nextYear);
        fields.lotNumber.value = `LOT-${isoDate(today).replaceAll("-", "")}-01`;
        updateImagePreview();

        editorEyebrow.textContent = "NEW PRODUCT";
        editorTitle.textContent = "새 상품 등록";
        saveButton.textContent = "새 상품 등록";
        cancelEditButton.hidden = true;
    }

    function editProduct(productId) {
        const product = state.products.find((item) => item.id === productId);
        if (!product) {
            showToast("상품 정보를 찾을 수 없습니다.", true);
            return;
        }

        state.editingId = productId;
        imageFile.value = "";
        fields.manufacturerName.value = product.manufacturer ?? "";
        fields.productName.value = product.name ?? "";
        fields.animalType.value = product.animalType ?? "CATTLE";
        fields.feedStage.value = product.stage ?? "";
        fields.description.value = product.description ?? "";
        fields.weightKg.value = product.weight ?? "";
        // 유통기한 임박 특가 상품은 price에 할인가, originalPrice에 정상가가
        // 담겨 옵니다. 그대로 되돌려 보내면 할인가가 정상가로 저장됩니다.
        if (product.expirySale) {
            fields.price.value = product.originalPrice ?? product.price ?? "";
            fields.originalPrice.value = "";
        } else {
            fields.price.value = product.price ?? "";
            fields.originalPrice.value = product.originalPrice ?? "";
        }
        fields.proteinPercent.value = product.protein ?? "";
        fields.fatPercent.value = product.fat ?? "";
        fields.fiberPercent.value = product.fiber ?? "";
        fields.calciumPercent.value = product.calcium ?? "";
        fields.imageUrl.value = product.imageUrl ?? "";
        fields.badge.value = product.badge ?? "";
        fields.lotNumber.value = product.lot ?? `LOT-${product.id}`;
        fields.manufacturedDate.value = product.manufacturedDate ?? isoDate(new Date());

        const defaultExpiry = new Date();
        defaultExpiry.setFullYear(defaultExpiry.getFullYear() + 1);
        fields.expirationDate.value = product.expiry ?? isoDate(defaultExpiry);
        // 서버는 이 값을 위 LOT 번호에 해당하는 LOT 하나의 수량으로 씁니다.
        // 상품 전체 판매가능 재고(product.stock)를 넣으면 예약 차감된 값이
        // LOT 수량으로 덮어써지면서 재고가 틀어집니다.
        const editingLot = (product.lots || []).find(
            (lot) => lot.lotNumber === product.lot
        );
        fields.lotQuantity.value = editingLot ? editingLot.quantity : 0;
        updateImagePreview();

        editorEyebrow.textContent = `PRODUCT #${product.id}`;
        editorTitle.textContent = "상품 정보 수정";
        saveButton.textContent = "변경 내용 저장";
        cancelEditButton.hidden = false;
        form.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    async function saveProduct(event) {
        event.preventDefault();
        if (!form.reportValidity()) {
            return;
        }

        const editing = state.editingId !== null;
        const url = editing ? `/api/admin/products/${state.editingId}` : "/api/admin/products";
        saveButton.disabled = true;
        saveButton.textContent = editing ? "저장 중..." : "등록 중...";

        try {
            if (imageFile.files.length > 0) {
                const formData = new FormData();
                formData.append("image", imageFile.files[0]);
                const uploadResponse = await fetch("/api/admin/products/image", {
                    method: "POST",
                    body: formData
                });
                if (!uploadResponse.ok) {
                    throw new Error(await readError(uploadResponse));
                }
                const uploaded = await uploadResponse.json();
                fields.imageUrl.value = uploaded.imageUrl;
            }
            const response = await fetch(url, {
                method: editing ? "PUT" : "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payloadFromForm())
            });
            if (!response.ok) {
                throw new Error(await readError(response));
            }

            showToast(editing ? "상품 정보가 수정되었습니다." : "새 상품이 등록되었습니다.");
            message.textContent = "상품 정보가 통합 재고·유통 데이터에 저장되었습니다.";
            resetForm();
            await loadProducts();
        } catch (error) {
            showToast(error.message, true);
            message.textContent = error.message;
        } finally {
            saveButton.disabled = false;
            saveButton.textContent = state.editingId === null ? "새 상품 등록" : "변경 내용 저장";
        }
    }

    async function deleteProduct(productId) {
        const product = state.products.find((item) => item.id === productId);
        if (!product || !window.confirm(`'${product.name}' 상품을 판매 중지하시겠습니까?`)) {
            return;
        }

        try {
            const response = await fetch(`/api/admin/products/${productId}`, {
                method: "DELETE"
            });
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            showToast("상품 판매가 중지되었습니다.");
            if (state.editingId === productId) {
                resetForm();
            }
            await loadProducts();
        } catch (error) {
            showToast(error.message, true);
        }
    }

    function updateImagePreview() {
        const preview = document.querySelector("#admin-preview-image");
        if (previewObjectUrl) {
            URL.revokeObjectURL(previewObjectUrl);
            previewObjectUrl = null;
        }
        if (imageFile.files.length > 0) {
            previewObjectUrl = URL.createObjectURL(imageFile.files[0]);
            preview.src = previewObjectUrl;
        } else {
            preview.src = fields.imageUrl.value.trim() || "/images/feed-bag-warehouse.png";
        }
        preview.onerror = () => {
            preview.onerror = null;
            preview.src = "/images/feed-bag-warehouse.png";
        };
    }

    form.addEventListener("submit", saveProduct);
    cancelEditButton.addEventListener("click", resetForm);
    table.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) {
            return;
        }
        const productId = Number(button.dataset.id);
        if (button.dataset.action === "edit") {
            editProduct(productId);
        } else if (button.dataset.action === "delete") {
            deleteProduct(productId);
        }
    });

    document.querySelector("#admin-search").addEventListener("input", (event) => {
        state.query = event.target.value.trim();
        renderProducts();
    });
    document.querySelector("#admin-animal-filter").addEventListener("change", (event) => {
        state.animal = event.target.value;
        renderProducts();
    });
    document.querySelector("#admin-stock-filter").addEventListener("change", (event) => {
        state.stock = event.target.value;
        renderProducts();
    });
    document.querySelector("#admin-event-filter").addEventListener("change", (event) => {
        state.event = event.target.value;
        renderProducts();
    });
    document.querySelector("#admin-filter-reset").addEventListener("click", () => {
        state.query = "";
        state.animal = "ALL";
        state.stock = "ALL";
        state.event = "ALL";
        document.querySelector("#admin-search").value = "";
        document.querySelector("#admin-animal-filter").value = "ALL";
        document.querySelector("#admin-stock-filter").value = "ALL";
        document.querySelector("#admin-event-filter").value = "ALL";
        renderProducts();
    });
    imageFile.addEventListener("change", updateImagePreview);

    resetForm();
    loadProducts();
})();

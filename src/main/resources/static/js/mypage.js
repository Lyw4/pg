(() => {
    "use strict";

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#039;");
    const statusLabels = {
        PAYMENT_PENDING: "결제 대기",
        PAID: "결제완료",
        PREPARING: "상품 준비중",
        SHIPPING: "배송중",
        DELIVERED: "배송완료",
        CANCELLED: "취소완료"
    };
    let member = null;
    let toastTimer;

    function showToast(message, error = false) {
        const toast = $("#toast");
        toast.textContent = message;
        toast.classList.toggle("error", error);
        toast.classList.add("show");
        window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2400);
    }

    async function api(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            ...options
        });
        const contentType = response.headers.get("content-type") || "";
        const result = contentType.includes("application/json") ? await response.json() : null;
        if (!response.ok) throw new Error(result?.message || `요청을 처리하지 못했습니다. (${response.status})`);
        return result;
    }

    async function renderFavorites() {
        const container = $("#mypage-favorite-products");
        try {
            const [products, favoriteProductIds] = await Promise.all([
                api("/api/products"),
                api("/api/wishlist")
            ]);
            const savedIds = new Set(favoriteProductIds.map(Number));
            const favorites = products.filter((product) => savedIds.has(product.id));
            container.innerHTML = favorites.length
                ? favorites.map((product) => `
                    <article>
                        <div class="mypage-product-mark"><img src="${escapeHtml(product.imageUrl || "/images/feed-bag-warehouse.png")}" alt="${escapeHtml(product.name)}"></div>
                        <div><span>${escapeHtml(product.animal)} · ${escapeHtml(product.stage)}</span><h3>${escapeHtml(product.name)}</h3><p>${escapeHtml(product.weight)}kg / 포 · ${product.stock > 0 ? `구매 가능 ${product.stock}포` : "품절"}</p></div>
                        <strong>${won(product.price)}</strong>
                        <a href="/#products" aria-label="${escapeHtml(product.name)} 상품 보러가기">구매</a>
                        <button type="button" data-remove-favorite="${product.id}" aria-label="${escapeHtml(product.name)} 관심상품 삭제">×</button>
                    </article>`).join("")
                : `<p class="mypage-empty">관심상품이 없습니다. 구매 화면에서 ♡ 버튼을 눌러 등록해보세요.</p>`;
        } catch (error) {
            container.innerHTML = `<p class="mypage-empty">${escapeHtml(error.message)}</p>`;
        }
    }

    function orderCard(order) {
        const orderedAt = order.orderedAt ? new Date(order.orderedAt).toLocaleString("ko-KR") : "-";
        const items = Array.isArray(order.items) ? order.items : [];
        const productNames = items.length
            ? items.map((item) => `${escapeHtml(item.productName)} ${item.quantity}포`).join(", ")
            : "주문 상품";
        const waitingForDeposit =
            order.paymentMethod === "BANK_TRANSFER"
            && order.paymentStatus === "WAITING_FOR_DEPOSIT";
        const statusLabel = waitingForDeposit
            ? "입금 대기"
            : order.paymentMethod === "BANK_TRANSFER"
                && order.paymentStatus === "DONE"
                ? "입금 완료"
                : order.status === "PAYMENT_PENDING"
                    ? "결제 확인 필요"
                    : statusLabels[order.status] || order.status;
        const actionButton = order.status === "PAID"
            ? `<button type="button" data-cancel-member-order="${escapeHtml(order.orderNumber)}">주문 취소</button>`
            : waitingForDeposit
                ? `
                    <button type="button" data-reconcile-payment="${escapeHtml(order.orderNumber)}">입금 확인</button>
                    <button type="button" data-cancel-member-order="${escapeHtml(order.orderNumber)}" data-cancel-before-deposit="true">입금 전 주문취소</button>
                `
                : order.status === "PAYMENT_PENDING"
                ? `<button type="button" data-reconcile-payment="${escapeHtml(order.orderNumber)}">결제상태 확인</button>`
                : "";
        const detailButton = `<a class="order-detail-link" href="/mypage/orders/${encodeURIComponent(order.orderNumber)}">주문 상세보기</a>`;
        const actions = `<div class="order-item-actions">${detailButton}${actionButton}</div>`;
        return `
            <article class="mypage-order-item">
                <div class="order-item-top"><div><span>${escapeHtml(orderedAt)}</span><strong>${escapeHtml(order.orderNumber)}</strong></div><b class="status-${String(order.status).toLowerCase()}">${escapeHtml(statusLabel)}</b></div>
                <p>${productNames}</p>
                <div class="order-item-bottom"><span>총 결제금액 <strong>${won(order.totalAmount)}</strong></span>${actions}</div>
            </article>`;
    }

    async function loadOrders() {
        const container = $("#mypage-order-list");
        try {
            const orders = await api("/api/orders/mine");
            container.innerHTML = orders.length
                ? orders.map(orderCard).join("")
                : `<div class="mypage-empty"><strong>아직 주문 내역이 없습니다.</strong><p>우리 농장에 맞는 사료를 둘러보세요.</p><a href="/#products">상품 보러가기</a></div>`;
            $("#status-paid").textContent = orders.filter((order) => ["PAID", "PREPARING"].includes(order.status)).length;
            $("#status-shipping").textContent = orders.filter((order) => order.status === "SHIPPING").length;
            $("#status-delivered").textContent = orders.filter((order) => order.status === "DELIVERED").length;
            $("#status-cancelled").textContent = orders.filter((order) => order.status === "CANCELLED").length;
        } catch (error) {
            container.innerHTML = `<p class="mypage-empty">${escapeHtml(error.message)}</p>`;
        }
    }

    function fillProfile(data) {
        member = data;
        const home = data.addresses?.find((item) => item.addressType === "HOME");
        const farm = data.addresses?.find((item) => item.addressType === "FARM");
        $("#profile-username").value = data.username || "";
        $("#profile-email").value = data.email || "";
        $("#profile-name").value = data.name || "";
        $("#profile-farm-name").value = data.farmName || "";
        $("#profile-phone").value = data.phone || "";
        $("#profile-business").value = data.businessNumber || "";
        $("#profile-home-address").value = home?.baseAddress || "";
        $("#profile-home-detail").value = home?.detailAddress || "";
        $("#profile-farm-address").value = farm?.baseAddress || "";
        $("#profile-unloading").value = farm?.unloadingLocation || "";
    }

    function switchPanel(name) {
        $$("[data-mypage-tab]").forEach((button) => button.classList.toggle("active", button.dataset.mypageTab === name));
        $$(".mypage-panel").forEach((panel) => { panel.hidden = panel.id !== `mypage-${name}-panel`; });
    }

    async function logout() {
        try {
            await api("/api/members/logout", { method: "POST" });
        } finally {
            window.sessionStorage.removeItem("feedflow-member");
            window.sessionStorage.removeItem("feedflow-last-order");
            window.location.href = "/";
        }
    }

    document.addEventListener("click", async (event) => {
        const tab = event.target.closest("[data-mypage-tab]");
        if (tab) switchPanel(tab.dataset.mypageTab);

        const remove = event.target.closest("[data-remove-favorite]");
        if (remove) {
            try {
                await api(
                    `/api/wishlist/${encodeURIComponent(remove.dataset.removeFavorite)}`,
                    { method: "DELETE" }
                );
                await renderFavorites();
                showToast("관심상품에서 삭제했습니다.");
            } catch (error) {
                showToast(error.message, true);
            }
        }

        const cancel = event.target.closest("[data-cancel-member-order]");
        const cancelMessage = cancel?.dataset.cancelBeforeDeposit
            ? "입금 전 가상계좌를 말소하고 주문을 취소하시겠습니까?"
            : "이 주문을 취소하고 LOT 재고를 복원하시겠습니까?";
        if (cancel && member && window.confirm(cancelMessage)) {
            try {
                await api(`/api/orders/mine/${encodeURIComponent(cancel.dataset.cancelMemberOrder)}/cancel`, {
                    method: "PATCH"
                });
                await loadOrders();
                showToast(
                    cancel.dataset.cancelBeforeDeposit
                        ? "가상계좌가 말소되고 주문이 취소되었습니다."
                        : "주문이 취소되고 LOT 재고가 복원되었습니다."
                );
            } catch (error) {
                showToast(error.message, true);
            }
        }

        const reconcile = event.target.closest("[data-reconcile-payment]");
        if (reconcile && member) {
            reconcile.disabled = true;
            try {
                const orderNumber = encodeURIComponent(
                    reconcile.dataset.reconcilePayment
                );
                const order = await api(
                    `/api/payments/portone/reconcile/${orderNumber}`,
                    { method: "POST" }
                );
                await loadOrders();
                showToast(
                    order.paymentStatus === "DONE"
                        ? "승인된 결제를 확인해 주문에 반영했습니다."
                        : "최신 결제상태를 확인했습니다."
                );
            } catch (error) {
                reconcile.disabled = false;
                showToast(error.message, true);
            }
        }
    });

    $("#profile-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!event.currentTarget.reportValidity()) return;
        try {
            const updated = await api("/api/members/me", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name: $("#profile-name").value.trim(),
                    farmName: $("#profile-farm-name").value.trim(),
                    phone: $("#profile-phone").value.trim(),
                    businessNumber: $("#profile-business").value.trim() || null,
                    regularDeliveryDay: member?.regularDeliveryDay || null,
                    homeAddress: $("#profile-home-address").value.trim(),
                    homeDetailAddress: $("#profile-home-detail").value.trim(),
                    farmAddress: $("#profile-farm-address").value.trim(),
                    unloadingLocation: $("#profile-unloading").value.trim()
                })
            });
            fillProfile(updated);
            window.sessionStorage.setItem("feedflow-member", JSON.stringify(updated));
            showToast("회원·배송 정보가 저장되었습니다.");
        } catch (error) {
            showToast(error.message, true);
        }
    });

    $("#logout-button").addEventListener("click", logout);
    $("#sidebar-logout").addEventListener("click", logout);

    api("/api/members/me")
        .then(fillProfile)
        .catch(() => { window.location.href = "/"; });
    loadOrders();
    renderFavorites();
})();

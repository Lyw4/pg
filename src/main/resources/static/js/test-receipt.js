(() => {
    "use strict";

    const app = document.querySelector("#receipt-test-app");
    const list = document.querySelector("#receipt-order-list");
    const message = document.querySelector("#receipt-message");
    const searchForm = document.querySelector("#receipt-search-form");
    const searchInput = document.querySelector("#receipt-order-number");
    const demoButton = document.querySelector("#show-demo-receipt");
    const demoReceipt = document.querySelector("#demo-receipt");
    const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
    const statusLabels = {
        PAYMENT_PENDING: "결제 대기",
        PAID: "결제 완료",
        PREPARING: "상품 준비중",
        SHIPPING: "배송중",
        DELIVERED: "배송 완료",
        CANCELLED: "취소"
    };
    let orders = [];

    async function api(url, options = {}) {
        const response = await fetch(url, { credentials: "same-origin", ...options });
        const type = response.headers.get("content-type") || "";
        const body = type.includes("application/json") ? await response.json() : null;
        if (!response.ok) {
            throw new Error(body?.message || `요청을 처리하지 못했습니다. (${response.status})`);
        }
        return body;
    }

    function render(source = orders) {
        if (!source.length) {
            list.innerHTML = `<p class="receipt-empty">조건에 맞는 주문이 없습니다.</p>`;
            return;
        }
        list.innerHTML = source.map((order) => {
            const orderedAt = order.orderedAt
                ? new Date(order.orderedAt).toLocaleString("ko-KR") : "-";
            const items = (order.items || []).map((item) =>
                `${escapeHtml(item.productName)} ${item.quantity}포`).join(", ");
            const localReceiptUrl = `/payments/receipt/${encodeURIComponent(order.orderNumber)}`;
            const mockupReceipt = order.receiptUrl
                && order.receiptUrl.includes("mockup-pg-web.kakao.com");
            const receipt = `<a class="receipt-link" href="${localReceiptUrl}" target="_blank" rel="noopener">영수증 열기</a>`
                + `<small class="receipt-provider-note">${mockupReceipt ? "카카오페이 테스트 전표 대신 FeedFlow 내부 영수증을 표시합니다." : "FeedFlow 내부 확인용 영수증입니다."}</small>`;
            return `<article class="receipt-order" data-order="${escapeHtml(order.orderNumber)}">
                <div class="receipt-order-head"><div><small>${escapeHtml(orderedAt)}</small><strong>${escapeHtml(order.orderNumber)}</strong></div>
                    <b>${escapeHtml(statusLabels[order.status] || order.status || "-")}</b></div>
                <p>${items || "주문 상품"}</p>
                <div class="receipt-order-meta"><span>결제수단 <strong>${escapeHtml(order.paymentMethod || "-")}</strong></span><span>결제금액 <strong>${won(order.totalAmount)}</strong></span></div>
                <div class="receipt-order-actions">${receipt}</div>
            </article>`;
        }).join("");
    }

    async function loadOrders() {
        try {
            orders = await api("/api/orders/mine");
            const requested = new URLSearchParams(window.location.search).get("orderNumber")
                || app.dataset.orderNumber;
            if (requested) searchInput.value = requested;
            render(requested ? orders.filter((order) => order.orderNumber === requested) : orders);
        } catch (error) {
            message.textContent = `${error.message} 로그인 후 다시 시도해주세요.`;
            list.innerHTML = `<p class="receipt-empty"><a href="/?account=login">로그인 화면으로 이동</a></p>`;
        }
    }

    searchForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const query = searchInput.value.trim().toLowerCase();
        render(query ? orders.filter((order) => order.orderNumber.toLowerCase() === query) : orders);
    });

    demoButton.addEventListener("click", () => {
        if (!demoReceipt.hidden) {
            demoReceipt.hidden = true;
            demoButton.textContent = "샘플 영수증 미리보기";
            return;
        }
        demoReceipt.hidden = false;
        demoReceipt.innerHTML = `<div class="demo-receipt-head"><span>FEED FLOW · TEST RECEIPT</span><b>샘플 화면</b></div>
            <h2>결제 영수증 미리보기</h2>
            <dl><div><dt>주문번호</dt><dd>FF-TEST-20260806</dd></div><div><dt>상품</dt><dd>한우 성장 플러스 25kg · 2포</dd></div><div><dt>결제수단</dt><dd>일반 신용카드</dd></div><div><dt>결제금액</dt><dd>${won(68000)}</dd></div></dl>
            <p>실제 PortOne 영수증은 결제 완료 주문의 ‘영수증 열기’ 버튼에서 확인합니다.</p>`;
        demoButton.textContent = "샘플 영수증 닫기";
    });

    list.addEventListener("click", async (event) => {
        const button = event.target.closest("[data-reconcile]");
        if (!button) return;
        button.disabled = true;
        try {
            await api(`/api/payments/portone/reconcile/${encodeURIComponent(button.dataset.reconcile)}`, { method: "POST" });
            message.textContent = "결제 상태를 갱신했습니다.";
            await loadOrders();
        } catch (error) {
            message.textContent = error.message;
            button.disabled = false;
        }
    });

    loadOrders();
})();

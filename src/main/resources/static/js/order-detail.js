(() => {
    "use strict";

    const $ = (selector, root = document) => root.querySelector(selector);
    const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
    const formatDate = (value) => value
        ? new Date(value).toLocaleString("ko-KR", {
            year: "numeric", month: "2-digit", day: "2-digit",
            hour: "2-digit", minute: "2-digit"
        })
        : "-";
    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
    const statusLabels = {
        PAYMENT_PENDING: "결제 대기",
        PAID: "결제 완료",
        PREPARING: "상품 준비",
        SHIPPING: "배송 중",
        DELIVERED: "배송 완료",
        CANCELLED: "주문 취소"
    };
    const timelineLabels = {
        ORDERED: "주문 접수",
        PAYMENT_CONFIRMED: "결제 확인",
        PREPARING: "상품 준비",
        SHIPPED: "출고 완료",
        DELIVERY_READY: "배송 준비",
        DELIVERY_PICKED_UP: "배송 시작",
        DELIVERY_IN_TRANSIT: "배송 중",
        DELIVERY_DELIVERED: "배송 완료",
        DELIVERY_CANCELLED: "배송 취소",
        CANCELLED: "주문 취소"
    };
    const timelineIcons = {
        ORDERED: "📝",
        PAYMENT_CONFIRMED: "💳",
        PREPARING: "📦",
        SHIPPED: "🚚",
        DELIVERY_READY: "📦",
        DELIVERY_PICKED_UP: "🚚",
        DELIVERY_IN_TRANSIT: "🚚",
        DELIVERY_DELIVERED: "🏠",
        DELIVERY_CANCELLED: "↩️",
        CANCELLED: "❌"
    };
    const paymentLabels = {
        CARD: "일반 카드 결제",
        KAKAO_PAY: "카카오페이",
        BANK_TRANSFER: "무통장 입금"
    };
    const paymentStatusLabels = {
        READY: "결제 준비",
        WAITING_FOR_DEPOSIT: "입금 대기",
        DONE: "결제 완료",
        FAILED: "결제 실패",
        CANCELLED: "결제 취소"
    };
    let toastTimer;

    function showToast(message, error = false) {
        const toast = $("#toast");
        if (!toast) return;
        toast.textContent = message;
        toast.classList.toggle("error", error);
        toast.classList.add("show");
        window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2600);
    }

    async function api(url, options = {}) {
        const response = await fetch(url, { credentials: "same-origin", ...options });
        const contentType = response.headers.get("content-type") || "";
        const result = contentType.includes("application/json") ? await response.json() : null;
        if (!response.ok) {
            throw new Error(result?.message || `요청을 처리하지 못했습니다. (${response.status})`);
        }
        return result;
    }

    function infoRow(label, value) {
        return `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value || "-")}</dd></div>`;
    }

    function renderTimeline(detail) {
        const container = $("#order-timeline");
        const events = Array.isArray(detail.timeline) ? detail.timeline : [];
        if (!events.length) {
            container.innerHTML = `<p class="order-detail-empty">아직 기록된 배송 이력이 없습니다.</p>`;
            return;
        }
        container.style.setProperty("--timeline-count", String(events.length));
        const lastIndex = events.length - 1;
        container.innerHTML = events.map((event, index) => `
            <div class="timeline-item completed ${index === lastIndex ? "current" : ""}">
                <span class="timeline-dot" aria-hidden="true">${timelineIcons[event.code] || "📍"}</span>
                <strong class="timeline-label">${escapeHtml(timelineLabels[event.code] || event.code)}</strong>
                <span class="timeline-date">${escapeHtml(formatDate(event.occurredAt))}</span>
                ${event.note ? `<small class="timeline-note">${escapeHtml(event.note)}</small>` : ""}
            </div>`).join("");
    }

    function renderDelivery(detail) {
        const delivery = detail.delivery;
        const shipment = detail.shipment;
        $("#delivery-status-label").textContent = delivery
            ? (timelineLabels[`DELIVERY_${delivery.status}`] || delivery.status)
            : shipment
                ? (shipment.status === "SHIPPED" ? "운송장 등록 대기" : "출고 진행 중")
                : "출고 준비 중";
        const rows = [];
        if (delivery) {
            rows.push(`<span>택배사 <strong>${escapeHtml(delivery.carrierName)}</strong></span>`);
            rows.push(`<span>운송장 <strong>${escapeHtml(delivery.trackingNumber)}</strong></span>`);
            if (delivery.expectedDeliveryAt) {
                rows.push(`<span>도착 예정 <strong>${escapeHtml(formatDate(delivery.expectedDeliveryAt))}</strong></span>`);
            }
            if (delivery.deliveredAt) {
                rows.push(`<span>배송 완료 <strong>${escapeHtml(formatDate(delivery.deliveredAt))}</strong></span>`);
            }
        } else if (shipment) {
            rows.push(`<span>출고 번호 <strong>${escapeHtml(shipment.shipmentNo)}</strong></span>`);
            rows.push(`<span>출고 상태 <strong>${escapeHtml(shipment.status)}</strong></span>`);
        } else {
            rows.push(`<span>출고 정보가 등록되면 배송 추적 정보가 표시됩니다.</span>`);
        }
        $("#delivery-summary").innerHTML = rows.join("");
    }

    function renderDetail(detail) {
        const order = detail.order;
        $("#order-number-label").textContent = `${order.orderNumber} · ${formatDate(order.orderedAt)}`;
        const status = $("#order-status-badge");
        status.textContent = statusLabels[order.status] || order.status || "확인 중";
        status.className = `order-detail-status status-${String(order.status || "").toLowerCase()}`;

        const items = Array.isArray(order.items) ? order.items : [];
        $("#order-items").innerHTML = items.length
            ? items.map((item) => `
                <div class="order-detail-item">
                    <strong>${escapeHtml(item.productName)}</strong>
                    <span>${item.quantity.toLocaleString("ko-KR")}개 · ${won(item.unitPrice)}</span>
                    <b>${won(item.lineAmount)}</b>
                </div>`).join("")
            : `<p class="order-detail-empty">주문 상품 정보가 없습니다.</p>`;

        $("#order-amounts").innerHTML = [
            infoRow("상품 금액", won(order.productAmount)),
            infoRow("배송비", order.deliveryFee ? won(order.deliveryFee) : "무료"),
            infoRow("할인 금액", order.discountAmount ? `-${won(order.discountAmount)}` : "-"),
            infoRow("최종 결제 금액", won(order.totalAmount))
        ].join("");

        const payment = detail.payment || {};
        const paymentRows = [
            infoRow("결제 수단", paymentLabels[payment.method || order.paymentMethod] || payment.method || order.paymentMethod),
            infoRow("결제 상태", paymentStatusLabels[payment.status || order.paymentStatus] || payment.status || order.paymentStatus),
            infoRow("결제 승인일", payment.approvedAt ? formatDate(payment.approvedAt) : "미승인")
        ];
        if (payment.transactionId) paymentRows.push(infoRow("거래번호", payment.transactionId));
        if (payment.virtualAccountNumber) {
            paymentRows.push(infoRow("입금 계좌", `${payment.virtualAccountBank || ""} ${payment.virtualAccountNumber}`.trim()));
            paymentRows.push(infoRow("입금 기한", payment.virtualAccountDueDate));
        }
        $("#payment-info").innerHTML = paymentRows.join("");
        const receipt = $("#receipt-action");
        const receiptUrl = payment.receiptUrl || order.receiptUrl;
        const mockupReceipt = receiptUrl
            && receiptUrl.includes("mockup-pg-web.kakao.com");
        const localReceiptUrl = `/payments/receipt/${encodeURIComponent(order.orderNumber)}`;
        receipt.innerHTML = `<a href="${localReceiptUrl}" target="_blank" rel="noopener">결제 영수증 보기 ↗</a>`
            + `<small>${mockupReceipt ? "카카오페이 테스트 전표 주소는 오류가 발생할 수 있어 내부 확인용 영수증만 제공합니다." : "FeedFlow 내부 확인용 영수증입니다."}</small>`;

        const recipient = detail.recipient || {};
        const address = [recipient.postalCode ? `[${recipient.postalCode}]` : "", recipient.roadAddress || recipient.jibunAddress, recipient.detailAddress]
            .filter(Boolean).join(" ");
        $("#recipient-info").innerHTML = [
            infoRow("받는 분", recipient.name),
            infoRow("연락처", recipient.phone),
            infoRow("배송지", address),
            infoRow("하차 위치", recipient.unloadingLocation),
            infoRow("배송 요청", recipient.deliveryRequest)
        ].join("");

        const warehouse = detail.fulfillment;
        $("#fulfillment-info").innerHTML = warehouse
            ? [
                infoRow("창고", `${warehouse.warehouseName} (${warehouse.warehouseCode})`),
                infoRow("창고 주소", warehouse.warehouseAddress),
                infoRow("거리", warehouse.distanceKm == null ? "-" : `${warehouse.distanceKm.toFixed(1)}km`),
                infoRow("배정 기준", warehouse.assignmentBasis)
            ].join("")
            : `<p class="order-detail-empty">배정된 출고 창고 정보가 없습니다.</p>`;

        renderTimeline(detail);
        renderDelivery(detail);
        $("#order-detail-loading").hidden = true;
        $("#order-detail-content").hidden = false;
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

    const orderNumber = document.body.dataset.orderNumber;
    $("#logout-button")?.addEventListener("click", logout);
    if (!orderNumber || orderNumber.includes("[[")) {
        $("#order-detail-loading").hidden = true;
        $("#order-detail-error").hidden = false;
        $("#order-detail-error").textContent = "주문번호를 확인할 수 없습니다.";
        return;
    }
    api(`/api/orders/mine/${encodeURIComponent(orderNumber)}/detail`)
        .then(renderDetail)
        .catch((error) => {
            $("#order-detail-loading").hidden = true;
            $("#order-detail-error").hidden = false;
            $("#order-detail-error").textContent = error.message;
            showToast(error.message, true);
        });
})();

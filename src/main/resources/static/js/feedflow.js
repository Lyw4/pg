(() => {
    "use strict";

    // 로그인 전 화면이 브라우저의 뒤로가기 캐시에 남아 있으면
    // 관리자 로그인 후에도 잠깐 비로그인 헤더가 보일 수 있습니다.
    // 캐시에서 복원된 경우 서버에서 현재 인증 상태로 다시 렌더링합니다.
    window.addEventListener("pageshow", (event) => {
        if (event.persisted) {
            window.location.reload();
        }
    });

    const state = {
        products: [],
        category: "ALL",
        query: "",
        sort: "recommended",
        cart: new Map(),
        favorites: new Set(),
        member: null,
        selectedProduct: null,
        pendingCheckout: false,
        checkoutSubmitting: false,
        pendingFavoriteId: null,
        usernameAvailable: false,
        emailAvailable: false,
        monthlyQuantityPromptSignature: null,
        monthlyQuantitySuggestion: null,
        paymentConfig: null
    };

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

    const won = (value) =>
        `${Number(value || 0).toLocaleString("ko-KR")}원`;

    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");

    let toastTimer;
    let resetCodeExpiresAt = 0;
    let resetCodeResendAt = 0;
    let resetCodeTimer = null;
    let emailAvailabilityTimer = null;
    let emailAvailabilityRequest = 0;

    function resetCodeIdentity() {
        return {
            username: $("#reset-password-username")?.value.trim() || "",
            email: $("#reset-password-email")?.value.trim() || "",
            phone: $("#reset-password-phone")?.value.trim() || ""
        };
    }

    function updateResetCodeTimer() {
        const message = $("#reset-password-code-message");
        const button = $("#reset-password-send-code");
        if (!message || !button) return;
        const remaining = Math.max(0, Math.ceil((resetCodeExpiresAt - Date.now()) / 1000));
        const cooldown = Math.max(0, Math.ceil((resetCodeResendAt - Date.now()) / 1000));
        message.textContent = remaining > 0
            ? `인증번호가 발급되었습니다. ${Math.floor(remaining / 60)}분 ${remaining % 60}초 동안 유효합니다.`
            : "인증번호가 만료되었습니다. 새 인증번호를 발급해주세요.";
        button.disabled = cooldown > 0;
        button.textContent = cooldown > 0 ? `${cooldown}초 후 재발급` : (remaining > 0 ? "인증번호 재발급" : "인증번호 발급");
        if (remaining === 0 && cooldown === 0) {
            window.clearInterval(resetCodeTimer);
            resetCodeTimer = null;
        }
    }

    function showToast(message) {
        const toast = $("#toast");

        if (!toast) {
            return;
        }

        toast.textContent = message;
        toast.classList.add("show");

        clearTimeout(toastTimer);

        toastTimer = window.setTimeout(() => {
            toast.classList.remove("show");
        }, 2400);
    }

    async function api(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            ...options
        });
        const contentType = response.headers.get("content-type") || "";

        const result = contentType.includes("application/json")
            ? await response.json()
            : null;

        if (!response.ok) {
            throw new Error(
                result?.message ||
                `요청을 처리하지 못했습니다. (${response.status})`
            );
        }

        return result;
    }

    /* Kakao 우편번호 검색 결과를 지정한 입력창에 채웁니다. */
    function openKakaoPostcode(
        addressInputId,
        postcodeInputId,
        focusInputId = null
    ) {
        if (
            !window.kakao
            || typeof window.kakao.Postcode !== "function"
        ) {
            showToast(
                "주소 검색 서비스를 불러오지 못했습니다. 인터넷 연결을 확인해주세요."
            );
            return;
        }

        new window.kakao.Postcode({
            oncomplete(data) {
                const selectedAddress =
                    data.userSelectedType === "R"
                        ? data.roadAddress || data.address
                        : data.jibunAddress || data.address;

                const addressInput =
                    document.getElementById(addressInputId);
                const postcodeInput =
                    document.getElementById(postcodeInputId);

                if (addressInput) {
                    addressInput.value = selectedAddress;
                    addressInput.dispatchEvent(
                        new Event("change", { bubbles: true })
                    );
                }

                if (postcodeInput) {
                    postcodeInput.value = data.zonecode || "";
                    postcodeInput.dispatchEvent(
                        new Event("change", { bubbles: true })
                    );
                }

                if (focusInputId) {
                    document.getElementById(focusInputId)?.focus();
                }
            }
        }).open();
    }

    async function loadPaymentConfig() {
        state.paymentConfig = await api("/api/payments/config");
        return state.paymentConfig;
    }

    function selectedPaymentMethod() {
        return $("input[name='payment']:checked")?.value || "CARD";
    }

    function ensurePaymentAvailable(method, config) {
        if (!config.portOneEnabled) {
            throw new Error(
                "포트원 고객사 식별코드 또는 REST API Key/Secret이 설정되지 않았습니다."
            );
        }
        if (method === "CARD" && !config.cardEnabled) {
            throw new Error("포트원 카드 결제 채널 키가 설정되지 않았습니다.");
        }
        if (method === "KAKAO_PAY" && !config.kakaoEnabled) {
            throw new Error("포트원 카카오페이 채널 키가 설정되지 않았습니다.");
        }
        if (method === "BANK_TRANSFER" && !config.virtualAccountEnabled) {
            throw new Error("포트원 가상계좌 채널 키가 설정되지 않았습니다.");
        }
    }

    async function markPortOneOrderFailed(order) {
        await api(
            `/api/payments/portone/fail?orderNumber=${encodeURIComponent(order.orderNumber)}`
            + `&token=${encodeURIComponent(order.paymentToken)}`,
            { method: "POST" }
        );
    }

    function normalizePortOneRequestError(error) {
        const message = String(error?.message || "");

        if (
            message.includes("Cannot read properties of null")
            && message.includes("find")
        ) {
            return new Error(
                "포트원 결제 채널을 찾지 못했습니다. "
                + "고객사 식별코드와 같은 계정에서 발급한 채널키를 설정해주세요."
            );
        }

        return error instanceof Error
            ? error
            : new Error("포트원 결제창을 열지 못했습니다.");
    }

    function requestPortOnePayment(order, method, config) {
        if (!window.IMP || typeof window.IMP.request_pay !== "function") {
            throw new Error("포트원 결제창 SDK를 불러오지 못했습니다.");
        }

        // 같은 페이지에서 중복 초기화하지 않도록 식별코드를 기억합니다.
        if (state.portOneInitializedCode !== config.portOneCustomerCode) {
            window.IMP.init(config.portOneCustomerCode);
            state.portOneInitializedCode = config.portOneCustomerCode;
        }

        // 결제창을 열기 전에 장바구니가 비면 rows[0] 접근에서 바로 예외가
        // 납니다. 호출부에서 가드하고 있지만 이 함수만 따로 쓰이는 경우에도
        // 안전하도록 여기서도 확인합니다.
        const rows = cartRows();
        if (!rows.length) {
            throw new Error("장바구니가 비어 있어 결제를 진행할 수 없습니다.");
        }
        const orderName = rows.length > 1
            ? `${rows[0].product.name} 외 ${rows.length - 1}건`
            : rows[0].product.name;
        const callbackToken = encodeURIComponent(order.paymentToken);

        const channelKey = method === "CARD"
            ? config.cardChannelKey
            : method === "KAKAO_PAY"
                ? config.kakaoChannelKey
                : config.virtualAccountChannelKey;

        return new Promise((resolve, reject) => {
            /*
             * PG SDK가 결제창을 띄우기 전에 비동기로 예외를 던지면 결제
             * 콜백도, 아래 try/catch도 타지 않습니다. 그러면 이 Promise가
             * 영원히 끝나지 않아 호출부의 finally가 실행되지 않고 제출
             * 버튼이 disabled로 남아 화면이 멈춥니다. 주문은 이미
             * PAYMENT_PENDING으로 만들어져 예약 재고까지 점유한 상태로
             * 남습니다.
             *
             * 새어 나온 예외를 붙잡아 사용자 문구로 정규화하고, 어떤 경로로
             * 끝나든 Promise가 한 번만 종료되도록 보장합니다.
             */
            let settled = false;
            const settleOnce = (finish) => (value) => {
                if (settled) {
                    return;
                }
                settled = true;
                window.removeEventListener("unhandledrejection", onEscapedError);
                window.removeEventListener("error", onEscapedError);
                finish(value);
            };

            function onEscapedError(event) {
                const reason = event?.reason ?? event?.error;
                if (!reason) {
                    return;
                }
                console.error("결제창 처리 중 예외", reason);
                event.preventDefault?.();
                fail(normalizePortOneRequestError(reason));
            }

            const done = settleOnce(resolve);
            const fail = settleOnce(reject);

            window.addEventListener("unhandledrejection", onEscapedError);
            window.addEventListener("error", onEscapedError);

            try {
                window.IMP.request_pay(
                    {
                    channelKey,
                    pay_method: method === "BANK_TRANSFER" ? "vbank" : "card",
                    merchant_uid: order.orderNumber,
                    name: orderName,
                    amount: order.totalAmount,
                    buyer_email: state.member?.email || "",
                    buyer_name: $("#order-name").value.trim(),
                    buyer_tel: $("#order-phone").value.trim(),
                    buyer_addr:
                        `${$("#order-address").value.trim()} `
                        + `${$("#order-detail").value.trim()}`.trim(),
                    buyer_postcode: $("#order-postcode").value.trim(),
                    m_redirect_url:
                        `${window.location.origin}/payments/portone/redirect`
                        + `?token=${callbackToken}`
                    },
                    async (response) => {
                    /*
                     * 최신 PortOne V1 SDK에서는 success/error_code만으로
                     * 결제 성공 여부를 판단하지 않습니다.
                     * imp_uid가 발급됐다면 서버가 PortOne REST API로
                     * 실제 결제 상태와 금액을 다시 조회하도록 합니다.
                     */
                    const impUid = String(response?.imp_uid || "").trim();
                    const merchantUid = String(
                        response?.merchant_uid || order.orderNumber
                    ).trim();

                    if (!impUid) {
                        try {
                            await markPortOneOrderFailed(order);
                        } catch (ignore) {
                            console.error("결제 취소 주문 정리 실패", ignore);
                        }
                        const error = new Error(
                            response.error_msg || "포트원 결제가 취소되었거나 실패했습니다."
                        );
                        error.orderHandled = true;
                        fail(error);
                        return;
                    }

                    try {
                        const completedOrder = await api(
                            "/api/payments/portone/complete",
                            {
                                method: "POST",
                                headers: {
                                    "Content-Type": "application/json"
                                },
                                body: JSON.stringify({
                                    impUid,
                                    merchantUid,
                                    paymentToken: order.paymentToken
                                })
                            }
                        );

                        const result = completedOrder.paymentStatus === "WAITING_FOR_DEPOSIT"
                            ? "waiting"
                            : completedOrder.paymentStatus === "DONE"
                                ? "success"
                                : "fail";

                        const message = result === "fail"
                            ? "결제 승인이 완료되지 않았습니다. 주문 내역을 확인해주세요."
                            : null;

                        window.location.assign(
                            `/?payment=${result}`
                            + `&orderNumber=${encodeURIComponent(completedOrder.orderNumber)}`
                            + (message
                                ? `&message=${encodeURIComponent(message)}`
                                : "")
                        );
                        done();
                    } catch (error) {
                        // 결제 자체는 발생했을 수 있으므로 주문과 재고를 자동 취소하지 않습니다.
                        error.paymentMayExist = true;
                        fail(error);
                    }
                    }
                );
            } catch (error) {
                fail(normalizePortOneRequestError(error));
            }
        });
    }

    async function handlePaymentReturn() {
        const params = new URLSearchParams(window.location.search);
        const paymentResult = params.get("payment");
        const orderNumber = params.get("orderNumber");

        if (!paymentResult || !orderNumber) {
            return;
        }

        window.history.replaceState({}, document.title, "/");

        if (paymentResult === "fail") {
            showToast(
                params.get("message")
                || "결제가 취소되었거나 승인에 실패했습니다."
            );
            return;
        }

        try {
            // 포트원 리다이렉트 결제는 로그인한 회원 주문으로만 생성됩니다.
            // 일반 주문 조회 API는 전화번호를 필수로 요구하므로, 세션 소유권을
            // 검증하는 회원 주문 상세 API로 조회해야 결제 완료 화면이 열립니다.
            const detail = await api(
                `/api/orders/mine/${encodeURIComponent(orderNumber)}/detail`
            );
            const order = detail.order;

            state.cart.clear();
            saveCart();
            renderCartCount();

            $("#success-order-number").textContent =
                `주문번호 ${order.orderNumber}`;

            if (paymentResult === "waiting") {
                $("#success-title").textContent =
                    "가상계좌가 발급되었습니다.";
                $("#success-payment-detail").textContent =
                    `은행코드 ${order.virtualAccountBank || "-"} · `
                    + `계좌번호 ${order.virtualAccountNumber || "-"} · `
                    + `입금기한 ${order.virtualAccountDueDate || "-"}`;
            } else {
                $("#success-title").textContent =
                    "결제가 완료되었습니다.";
                $("#success-payment-detail").textContent =
                    "결제 승인과 주문 접수가 정상적으로 완료되었습니다.";
            }

            openModal("success-modal");
        } catch (error) {
            showToast(error.message);
        }
    }

    const USERNAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]{4,19}$/;
    const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,64}$/;
    const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    function setValidationState(input, messageElement, valid, message) {
        input.classList.toggle("input-valid", valid);
        input.classList.toggle("input-invalid", !valid);
        messageElement.classList.toggle("success", valid);
        messageElement.classList.toggle("error", !valid);
        messageElement.textContent = message;
    }

    function validateUsernameFormat() {
        const input = $("#signup-username");
        const message = $("#signup-username-message");
        const valid = USERNAME_PATTERN.test(input.value.trim());

        if (!valid) {
            setValidationState(
                input,
                message,
                false,
                "영문으로 시작하는 5~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다."
            );
        }
        return valid;
    }

    function validatePassword() {
        const input = $("#signup-password");
        const message = $("#signup-password-message");
        const valid = PASSWORD_PATTERN.test(input.value);

        setValidationState(
            input,
            message,
            valid,
            valid
                ? "사용 가능한 비밀번호입니다."
                : "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다."
        );
        return valid;
    }

    // [수정] 비밀번호 재설정도 회원가입과 동일한 규칙으로 검사합니다.
    function validateResetPassword() {
        const input = $("#reset-password-new");
        const message = $("#reset-password-new-message");

        if (!input || !message) {
            return false;
        }

        const valid = PASSWORD_PATTERN.test(input.value);

        setValidationState(
            input,
            message,
            valid,
            valid
                ? "사용 가능한 비밀번호입니다."
                : "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다."
        );

        const confirmInput = $("#reset-password-confirm");

        if (confirmInput?.value) {
            validateResetPasswordConfirmation();
        }

        return valid;
    }

    // [수정] 새 비밀번호와 확인 입력값이 같은지 실시간 검사합니다.
    function validateResetPasswordConfirmation() {
        const newPasswordInput = $("#reset-password-new");
        const confirmInput = $("#reset-password-confirm");
        const message = $("#reset-password-confirm-message");

        if (!newPasswordInput || !confirmInput || !message) {
            return false;
        }

        const valid = confirmInput.value.length > 0
            && newPasswordInput.value === confirmInput.value;

        setValidationState(
            confirmInput,
            message,
            valid,
            valid
                ? "비밀번호가 일치합니다."
                : "새 비밀번호와 비밀번호 확인이 일치하지 않습니다."
        );

        return valid;
    }

    // [수정] 숫자만 입력해도 010-1234-5678 형식으로 표시합니다.
    function formatMobilePhone(value) {
        const numbers = String(value ?? "")
            .replace(/[^0-9]/g, "")
            .slice(0, 11);

        if (numbers.length <= 3) {
            return numbers;
        }

        if (numbers.length <= 7) {
            return `${numbers.slice(0, 3)}-${numbers.slice(3)}`;
        }

        return `${numbers.slice(0, 3)}-${numbers.slice(3, 7)}-${numbers.slice(7)}`;
    }

    function validateEmail() {
        const input = $("#signup-email");
        const message = $("#signup-email-message");
        const valid = EMAIL_PATTERN.test(input.value.trim());

        state.emailAvailable = false;

        setValidationState(
            input,
            message,
            valid,
            valid
                ? "올바른 이메일 형식입니다."
                : "올바른 이메일 주소를 입력해주세요."
        );
        return valid;
    }

    async function checkEmailAvailability(focusOnError = false) {
        const input = $("#signup-email");
        const message = $("#signup-email-message");
        if (!input || !message || !validateEmail()) {
            if (focusOnError) input?.focus();
            return false;
        }

        const email = input.value.trim();
        const requestId = ++emailAvailabilityRequest;
        message.classList.remove("success", "error");
        message.textContent = "이메일 중복 여부를 확인하고 있습니다.";

        try {
            const result = await api(
                `/api/members/check-email?email=${encodeURIComponent(email)}`
            );
            if (requestId !== emailAvailabilityRequest
                || input.value.trim() !== email) {
                return false;
            }
            state.emailAvailable = Boolean(result.available);
            setValidationState(
                input,
                message,
                state.emailAvailable,
                state.emailAvailable
                    ? "사용 가능한 이메일입니다."
                    : "이미 사용 중인 이메일입니다."
            );
            if (!state.emailAvailable && focusOnError) input.focus();
            return state.emailAvailable;
        } catch (error) {
            if (requestId !== emailAvailabilityRequest) return false;
            state.emailAvailable = false;
            setValidationState(input, message, false, error.message);
            if (focusOnError) input.focus();
            return false;
        }
    }

    async function checkUsernameAvailability() {
        const input = $("#signup-username");
        const message = $("#signup-username-message");
        const button = $("#check-username-button");
        state.usernameAvailable = false;

        if (!validateUsernameFormat()) {
            input.focus();
            return;
        }

        button.disabled = true;
        button.textContent = "확인 중";

        try {
            const result = await api(
                `/api/members/check-username?username=${encodeURIComponent(input.value.trim())}`
            );

            state.usernameAvailable = Boolean(result.available);
            setValidationState(
                input,
                message,
                state.usernameAvailable,
                state.usernameAvailable
                    ? "사용 가능한 아이디입니다."
                    : "이미 사용 중인 아이디입니다."
            );
        } catch (error) {
            setValidationState(
                input,
                message,
                false,
                error.message
            );
        } finally {
            button.disabled = false;
            button.textContent = "중복확인";
        }
    }

    function daysUntil(dateText) {
        if (!dateText) {
            return "";
        }

        const target = new Date(`${dateText}T00:00:00`);
        const today = new Date();

        today.setHours(0, 0, 0, 0);

        return Math.ceil((target - today) / 86400000);
    }

    function bagMarkup(product, extraClass = "") {
        const imageUrl = escapeHtml(
            product.imageUrl || "/images/feed-bag-warehouse.png"
        );

        return `
            <div class="product-photo ${extraClass}">
                <img
                    src="${imageUrl}"
                    alt="${escapeHtml(product.name)} 사료 이미지"
                    loading="lazy"
                >
            </div>
        `;
    }

    function purchasableStock(product) {
        if (!product) return 0;
        return product.expirySale
            ? Math.max(0, Math.min(
                Number(product.stock || 0),
                Number(product.saleStock || 0)
            ))
            : Math.max(0, Number(product.stock || 0));
    }

    function stockInfo(product) {
        const stock = purchasableStock(product);
        if (stock < 1) {
            return {
                label: "품절",
                className: "sold-out"
            };
        }

        if (product.expirySale) {
            return {
                label: `특가 재고 ${stock}포 · D-${Number(product.saleDaysRemaining)}일`,
                className: "sale-stock"
            };
        }

        if (stock <= 10) {
            return {
                label: `재고 얼마 남지 않음 · ${stock}포`,
                className: "low-stock"
            };
        }

        return {
            label: "구매 가능",
            className: "available"
        };
    }

    function filteredProducts() {
        const normalized = state.query.trim().toLowerCase();

        const result = state.products.filter((product) => {
            const categoryMatch =
                state.category === "ALL"
                || (
                    state.category === "SALE"
                    && Boolean(product.expirySale)
                )
                || (
                    state.category === "EVENT"
                    && Boolean(product.badge || product.originalPrice)
                )
                || (
                    state.category === "CATTLE_GROUP"
                    && ["CATTLE", "DAIRY_CATTLE"].includes(
                        product.animalType
                    )
                )
                || (
                    state.category === "POULTRY_GROUP"
                    && ["CHICKEN", "DUCK"].includes(
                        product.animalType
                    )
                )
                || product.animalType === state.category;

            const haystack = `
                ${product.name}
                ${product.animal}
                ${product.stage}
                ${product.description}
            `.toLowerCase();

            return categoryMatch
                && (!normalized || haystack.includes(normalized));
        });

        return result.sort((a, b) => {
            if (state.sort === "low") {
                return a.price - b.price;
            }

            if (state.sort === "high") {
                return b.price - a.price;
            }

            if (state.sort === "stock") {
                return purchasableStock(b) - purchasableStock(a);
            }

            return Number(Boolean(b.expirySale))
                - Number(Boolean(a.expirySale))
                || Number(b.discountRate || 0)
                - Number(a.discountRate || 0)
                || Number(a.saleDaysRemaining || Number.MAX_SAFE_INTEGER)
                - Number(b.saleDaysRemaining || Number.MAX_SAFE_INTEGER)
                || Number(Boolean(b.badge))
                - Number(Boolean(a.badge))
                || a.id - b.id;
        });
    }

    function renderProducts() {
        const products = filteredProducts();
        const grid = $("#product-grid");
        const emptyProducts = $("#empty-products");
        const productResultCount = $("#product-result-count");

        if (!grid || !emptyProducts || !productResultCount) {
            return;
        }

        updateProductHeading();

        emptyProducts.hidden = products.length > 0;

        productResultCount.textContent =
            `총 ${products.length.toLocaleString("ko-KR")}개`;

        grid.innerHTML = products.map((product) => {
            const favorite = state.favorites.has(product.id);
            const stock = stockInfo(product);

            return `
                <article
                    class="product-card"
                    data-product-id="${Number(product.id)}"
                >
                    <div
                        class="product-visual"
                        data-detail="${Number(product.id)}"
                        role="button"
                        tabindex="0"
                        aria-label="${escapeHtml(product.name)} 상세보기"
                    >
                        ${product.expirySale
                            ? `<span class="badge sale-badge">${escapeHtml(product.saleLabel)}</span>`
                            : (product.badge
                                ? `<span class="badge">${escapeHtml(product.badge)}</span>`
                                : "")}

                        <button
                            class="favorite ${favorite ? "active" : ""}"
                            type="button"
                            data-favorite="${Number(product.id)}"
                            aria-label="관심상품 ${favorite ? "해제" : "등록"}"
                        >
                            ${favorite ? "♥" : "♡"}
                        </button>

                        ${bagMarkup(product)}
                    </div>

                    <div class="product-info">

                        <div class="product-tags">
                            <span>${escapeHtml(product.animal)}</span>
                            <span>${escapeHtml(product.stage)}</span>
                        </div>

                        <h3>${escapeHtml(product.name)}</h3>

                        <p>${escapeHtml(product.description)}</p>

                        <div class="nutrition-chips">
                            <span>조단백 ${Number(product.protein)}%</span>
                            <span>조지방 ${Number(product.fat)}%</span>
                        </div>

                        <div class="stock-line ${stock.className}">
                            <span>${escapeHtml(product.weight)}kg / 포</span>
                            <strong>${stock.label}</strong>
                        </div>

                        <div class="product-price">
                            ${
                                product.originalPrice
                                    ? `<del>${won(product.originalPrice)}</del>`
                                    : ""
                            }

                            <strong>${won(product.price)}</strong>
                        </div>

                        <div class="card-actions">
                            <button
                                class="card-detail"
                                type="button"
                                data-detail="${Number(product.id)}"
                            >
                                상세보기
                            </button>

                            <button
                                class="card-add"
                                type="button"
                                data-add-cart="${Number(product.id)}"
                                ${purchasableStock(product) < 1 ? "disabled" : ""}
                            >
                                ${
                                    purchasableStock(product) < 1
                                        ? "품절"
                                        : (product.expirySale
                                            ? `${Number(product.discountRate)}% 특가 담기`
                                            : "장바구니 ＋")
                                }
                            </button>
                        </div>
                    </div>
                </article>
            `;
        }).join("");
    }

    function updateProductHeading() {
        const eyebrow = $("#products-eyebrow");
        const heading = $("#products-heading");
        const description = $("#products-description");
        const sale = state.category === "SALE";
        if (eyebrow) eyebrow.lastChild.textContent = sale
            ? " LIMITED SALE"
            : " FARMER'S CHOICE";
        if (heading) heading.textContent = sale
            ? "유통기한 임박 세일 상품"
            : "지금 농가에서 많이 찾는 사료";
        if (description) description.firstChild.textContent = sale
            ? "빠른 출고가 가능한 임박 LOT를 할인된 가격으로 만나보세요."
            : "축종과 성장 단계에 맞춰 엄선한 대표 배합사료입니다.";
    }

    async function loadProducts() {
        try {
            state.products = await api("/api/products");

            renderProducts();
            renderCartCount();
            openRequestedCartOrCheckout();
        } catch (error) {
            showToast(
                error.message || "상품 정보를 불러오지 못했습니다."
            );
        }
    }

    function productById(id) {
        return state.products.find(
            (product) => product.id === Number(id)
        );
    }

    function openModal(id) {
        const backdrop = $("#modal-backdrop");

        if (!backdrop) {
            return;
        }

        backdrop.hidden = false;

        $$(".modal").forEach((modal) => {
            modal.hidden = modal.id !== id;
        });

        document.body.style.overflow = "hidden";
    }

    function closeModal() {
        const backdrop = $("#modal-backdrop");

        if (!backdrop) {
            return;
        }

        const farmModelModal = $("#farm-model-modal");
        const hideToday = $("#farm-popup-hide-today");
        if (farmModelModal && !farmModelModal.hidden
            && hideToday?.checked && state.member?.id) {
            const today = new Date().toLocaleDateString("sv-SE");
            window.localStorage.setItem(
                `feedflow-farm-model-hide-day-${state.member.id}`,
                today
            );
        }

        backdrop.hidden = true;

        $$(".modal").forEach((modal) => {
            modal.hidden = true;
        });

        if (hideToday) {
            hideToday.checked = false;
        }

        document.body.style.overflow = "";
    }

    function showProduct(product) {
        state.selectedProduct = product;

        const stock = stockInfo(product);
        const lots = Array.isArray(product.lots)
            ? product.lots
            : [];

        const content = $("#product-modal-content");

        if (!content) {
            return;
        }

        content.innerHTML = `
            <div class="detail-layout">

                <div class="detail-visual">
                    ${bagMarkup(product)}
                </div>

                <div class="detail-info">

                    <div class="product-tags">
                        <span>${escapeHtml(product.animal)}</span>
                        <span>${escapeHtml(product.stage)}</span>
                    </div>

                    <h2 id="product-modal-title">
                        ${escapeHtml(product.name)}
                    </h2>

                    <small class="product-code">
                        상품 코드
                        ${escapeHtml(
                            product.productCode || `FF-P${product.id}`
                        )}
                        ·
                        ${escapeHtml(product.manufacturer)}
                    </small>

                    <p>${escapeHtml(product.description)}</p>

                    <div class="nutrients">
                        <div>
                            <span>조단백</span>
                            <strong>${Number(product.protein)}%</strong>
                        </div>

                        <div>
                            <span>조지방</span>
                            <strong>${Number(product.fat)}%</strong>
                        </div>

                        <div>
                            <span>조섬유</span>
                            <strong>${Number(product.fiber)}%</strong>
                        </div>

                        <div>
                            <span>칼슘</span>
                            <strong>${Number(product.calcium)}%</strong>
                        </div>
                    </div>

                    <div class="stock-banner ${stock.className}">
                        <strong>${stock.label}</strong>
                        <span>
                            유통기한이 빠른 LOT부터 자동 출고됩니다(FEFO).
                        </span>
                    </div>

                    <div class="detail-price">
                        ${product.originalPrice
                            ? `<del>${won(product.originalPrice)}</del>`
                            : ""}
                        ${won(product.price)}

                        <small>
                            · ${escapeHtml(product.weight)}kg / 포
                            ·
                            ${won(
                                Math.round(
                                    product.price
                                    / Number(product.weight || 1)
                                )
                            )}/kg
                        </small>
                    </div>

                    ${product.expirySale ? `
                        <div class="expiry-sale-notice">
                            <strong>${escapeHtml(product.saleLabel)}</strong>
                            <span>특가 LOT ${Number(product.saleStock)}포 한정 · ${escapeHtml(product.saleExpirationDate)}까지</span>
                        </div>
                    ` : ""}

                    <label class="detail-quantity">
                        구매 수량

                        <span>
                            <button
                                type="button"
                                data-detail-quantity="-1"
                                aria-label="수량 줄이기"
                            >
                                −
                            </button>

                            <input
                                id="detail-quantity"
                                type="number"
                                min="1"
                                max="${purchasableStock(product)}"
                                value="1"
                            >

                            <button
                                type="button"
                                data-detail-quantity="1"
                                aria-label="수량 늘리기"
                            >
                                ＋
                            </button>
                        </span>
                    </label>

                    <div class="detail-buttons">

                        <button
                            class="secondary-button"
                            type="button"
                            data-modal-add="${Number(product.id)}"
                            ${purchasableStock(product) < 1 ? "disabled" : ""}
                        >
                            장바구니 담기
                        </button>

                        <button
                            class="primary-button"
                            type="button"
                            data-quick-buy="${Number(product.id)}"
                            ${purchasableStock(product) < 1 ? "disabled" : ""}
                        >
                            바로 구매하기
                        </button>
                    </div>
                </div>
            </div>

            <section class="detail-lots">

                <div>
                    <h3>현재 판매 가능한 LOT</h3>
                    <p>
                        지난 유통기한의 LOT는 자동으로 제외됩니다.
                    </p>
                </div>

                ${
                    lots.length
                        ? `
                            <div class="lot-table-wrap">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>LOT 번호</th>
                                            <th>제조일</th>
                                            <th>유통기한</th>
                                            <th>D-day</th>
                                            <th>잔여 수량</th>
                                            <th>상태</th>
                                        </tr>
                                    </thead>

                                    <tbody>
                                        ${lots.map((lot) => `
                                            <tr>
                                                <td>
                                                    <strong>
                                                        ${escapeHtml(lot.lotNumber)}
                                                    </strong>
                                                </td>

                                                <td>
                                                    ${escapeHtml(lot.manufacturedDate)}
                                                </td>

                                                <td>
                                                    ${escapeHtml(lot.expirationDate)}
                                                </td>

                                                <td class="${
                                                    lot.daysRemaining <= 30
                                                        ? "warning-text"
                                                        : ""
                                                }">
                                                    D-${Number(lot.daysRemaining)}
                                                </td>

                                                <td>
                                                    ${Number(lot.quantity).toLocaleString("ko-KR")}포
                                                </td>

                                                <td>
                                                    ${escapeHtml(lot.status)}
                                                </td>
                                            </tr>
                                        `).join("")}
                                    </tbody>
                                </table>
                            </div>
                        `
                        : `
                            <div class="empty-state">
                                현재 판매 가능한 LOT가 없습니다.
                            </div>
                        `
                }
            </section>
        `;

        openModal("product-modal");
    }

    function addToCart(id, quantity = 1) {
        const product = productById(id);

        const stock = purchasableStock(product);
        if (!product || stock < 1) {
            showToast("주문 가능한 재고가 없습니다.");
            return;
        }

        const current = state.cart.get(product.id) || 0;

        state.cart.set(
            product.id,
            Math.min(stock, current + quantity)
        );

        saveCart();
        renderCartCount();

        showToast(`${product.name}을 장바구니에 담았습니다.`);
    }

    function cartRows() {
        return [...state.cart.entries()]
            .map(([id, quantity]) => ({
                product: productById(id),
                quantity
            }))
            .filter((item) => item.product);
    }

    function cartAmounts() {
        const productAmount = cartRows().reduce(
            (sum, item) =>
                sum + item.product.price * item.quantity,
            0
        );

        const deliveryFee =
            productAmount === 0 || productAmount >= 150000
                ? 0
                : 5000;

        return {
            productAmount,
            deliveryFee,
            total: productAmount + deliveryFee
        };
    }

    function renderCartCount() {
        const count = [...state.cart.values()].reduce(
            (sum, quantity) => sum + quantity,
            0
        );

        const cartCount = $("#cart-count");
        const cartTitleCount = $("#cart-title-count");

        if (cartCount) {
            cartCount.textContent = count;
        }

        if (cartTitleCount) {
            cartTitleCount.textContent = count;
        }
    }

    function renderCart() {
        const rows = cartRows();
        const cartItems = $("#cart-items");
        const cartSummary = $("#cart-summary");
        const startCheckout = $("#start-checkout");

        if (!cartItems || !cartSummary || !startCheckout) {
            return;
        }

        cartItems.innerHTML = rows.length
            ? rows.map(({ product, quantity }) => `
                <div class="cart-item">

                    <div>
                        <strong>${escapeHtml(product.name)}</strong>

                        <small>
                            ${won(product.price)}
                            · ${product.weight}kg
                            × ${quantity}포
                            =
                            ${
                                (
                                    Number(product.weight)
                                    * quantity
                                ).toLocaleString("ko-KR")
                            }kg
                        </small>
                    </div>

                    <div class="quantity">
                        <button
                            type="button"
                            data-cart-minus="${product.id}"
                        >
                            −
                        </button>

                        <span>${quantity}</span>

                        <button
                            type="button"
                            data-cart-plus="${product.id}"
                        >
                            ＋
                        </button>
                    </div>

                    <button
                        class="remove"
                        type="button"
                        data-cart-remove="${product.id}"
                        aria-label="삭제"
                    >
                        ×
                    </button>
                </div>
            `).join("")
            : `
                <div class="empty-state">
                    장바구니가 비어 있습니다.
                </div>
            `;

        const amounts = cartAmounts();

        const totalWeight = rows.reduce(
            (sum, item) =>
                sum
                + Number(item.product.weight)
                * item.quantity,
            0
        );

        cartSummary.innerHTML = `
            <div class="summary-line">
                <span>총 주문 중량</span>
                <strong>
                    ${totalWeight.toLocaleString("ko-KR")}kg
                </strong>
            </div>

            <div class="summary-line">
                <span>상품 금액</span>
                <strong>${won(amounts.productAmount)}</strong>
            </div>

            <div class="summary-line">
                <span>배송비</span>
                <strong>
                    ${
                        amounts.deliveryFee
                            ? won(amounts.deliveryFee)
                            : "무료"
                    }
                </strong>
            </div>

            <div class="summary-line total">
                <span>결제 예정금액</span>
                <strong>${won(amounts.total)}</strong>
            </div>
        `;

        startCheckout.disabled = rows.length === 0;

        renderCartCount();
    }

    function changeCart(id, amount) {
        const product = productById(id);

        const next = Math.min(
            purchasableStock(product),
            Math.max(
                0,
                (state.cart.get(Number(id)) || 0) + amount
            )
        );

        if (next) {
            state.cart.set(Number(id), next);
        } else {
            state.cart.delete(Number(id));
        }

        saveCart();
        renderCart();
    }

    function saveCart() {
        const key = cartStorageKey();
        if (!key) return;
        window.localStorage.setItem(
            key,
            JSON.stringify([...state.cart.entries()])
        );
    }

    function cartStorageKey() {
        if (document.getElementById("operator-session")) {
            return null;
        }
        return state.member?.id
            ? `feedflow-cart-member-${Number(state.member.id)}`
            : "feedflow-cart-guest";
    }

    function loadCartForCurrentIdentity() {
        const key = cartStorageKey();
        state.cart = new Map();
        if (!key) return;
        try {
            const cart = JSON.parse(
                window.localStorage.getItem(key) || "[]"
            );
            state.cart = new Map(
                cart
                    .filter((entry) => Array.isArray(entry))
                    .map(([id, quantity]) => [
                        Number(id),
                        Math.max(0, Number(quantity))
                    ])
                    .filter(([id, quantity]) => Number.isFinite(id)
                        && quantity > 0)
            );
        } catch {
            state.cart = new Map();
        }
    }

    function openRequestedCartOrCheckout() {
        const query = new URLSearchParams(window.location.search);
        const requestedProductId = Number(query.get("buy"));

        if (Number.isInteger(requestedProductId) && requestedProductId > 0) {
            query.delete("buy");
            const nextQuery = query.toString();
            window.history.replaceState(
                {},
                document.title,
                `${window.location.pathname}${nextQuery ? `?${nextQuery}` : ""}`
            );

            const product = productById(requestedProductId);
            if (!product) {
                showToast("주문할 상품을 찾을 수 없습니다.");
                return;
            }
            if (purchasableStock(product) < 1) {
                showToast("현재 주문 가능한 재고가 없는 상품입니다.");
                return;
            }

            state.cart.clear();
            addToCart(requestedProductId, 1);
            beginCheckout();
            return;
        }

        if (query.get("checkout") !== "favorites") return;

        query.delete("checkout");
        const nextQuery = query.toString();
        window.history.replaceState(
            {},
            document.title,
            `${window.location.pathname}${nextQuery ? `?${nextQuery}` : ""}`
        );

        if (!state.member) {
            showAccount("login");
            showToast("한 번에 구매하려면 다시 로그인해주세요.");
            return;
        }
        if (!cartRows().length) {
            showToast("구매 가능한 관심상품이 없습니다.");
            return;
        }
        beginCheckout();
    }

    async function toggleFavorite(id, forceAdd = false) {
        if (!state.member) {
            state.pendingFavoriteId = id;
            showAccount("login");
            showToast("관심상품은 로그인한 회원만 등록할 수 있습니다.");
            return;
        }

        const add = forceAdd || !state.favorites.has(id);

        try {
            await api(`/api/wishlist/${encodeURIComponent(id)}`, {
                method: add ? "POST" : "DELETE"
            });

            if (add) {
                state.favorites.add(id);
            } else {
                state.favorites.delete(id);
            }

            renderProducts();
            showToast(
                add
                    ? "관심상품에 등록했습니다."
                    : "관심상품에서 해제했습니다."
            );
        } catch (error) {
            showToast(error.message);
        }
    }

    async function restoreBrowserState() {
        // 이전 버전의 비회원 관심상품 데이터가 남지 않도록 제거합니다.
        window.localStorage.removeItem("feedflow-favorites");
        // 이전 버전의 공용 장바구니가 다른 회원에게 노출되지 않도록 폐기합니다.
        window.localStorage.removeItem("feedflow-cart");
        state.favorites = new Set();
        state.cart = new Map();

        try {
            state.member = await api("/api/members/me");

            window.sessionStorage.setItem(
                "feedflow-member",
                JSON.stringify(state.member)
            );
        } catch {
            state.member = null;

            window.sessionStorage.removeItem(
                "feedflow-member"
            );

            window.sessionStorage.removeItem(
                "feedflow-last-order"
            );
        }

        if (state.member) {
            try {
                const favoriteIds = await api("/api/wishlist");
                state.favorites = new Set(
                    favoriteIds.map(Number).filter(Number.isFinite)
                );
            } catch (error) {
                state.favorites = new Set();
                console.error("관심상품 불러오기 실패", error);
            }
        }

        updateMemberUi();
        const forcedMemberId = window.sessionStorage.getItem(
            "feedflow-show-farm-model-after-login"
        );
        const forcePopup = forcedMemberId === String(state.member?.id || "");
        if (forcePopup) {
            window.sessionStorage.removeItem(
                "feedflow-show-farm-model-after-login"
            );
        }
        showFarmModelPopup(forcePopup);

        const query = new URLSearchParams(window.location.search);
        if (query.get("sessionExpired") === "true" && !state.member) {
            showAccount("login");
            showToast(
                "로그인 세션이 만료되었거나 서버가 재시작되었습니다. 다시 로그인해 주세요."
            );
            query.delete("sessionExpired");
            const nextQuery = query.toString();
            window.history.replaceState(
                {},
                document.title,
                `${window.location.pathname}${nextQuery ? `?${nextQuery}` : ""}`
            );
        }

        loadCartForCurrentIdentity();
        renderCartCount();
    }

    /*
     * 로그인 상태에 따라 상단 유틸리티 바의 계정 메뉴를 변경합니다.
     *
     * 로그인 전: 로그인 / 회원가입
     * 로그인 후: 마이페이지 / 로그아웃
     *
     * 장바구니 옆에 있던 계정 버튼(#header-account-button)은 로그인 버튼이
     * 화면에 두 번 노출되는 문제로 제거했습니다. 그 버튼만 참조하던 코드도
     * 함께 정리했습니다.
     */
    function updateMemberUi() {
        const loggedIn = Boolean(state.member);

        const utilityLogin = $("#utility-login");
        const utilitySignup = $("#utility-signup");
        const utilityMyPage = $("#utility-mypage");
        const utilityLogout = $("#utility-logout");

        if (utilityLogin) {
            utilityLogin.hidden = loggedIn;
        }

        if (utilitySignup) {
            utilitySignup.hidden = loggedIn;
        }

        if (utilityMyPage) {
            utilityMyPage.hidden = !loggedIn;
        }

        if (utilityLogout) {
            utilityLogout.hidden = !loggedIn;
        }

        if (loggedIn) {
            fillCheckoutFromMember();
        }
    }

    function showFarmModelPopup(force = false) {
        const model = state.member?.farmModel;
        const assignment = state.member?.farmAssignment;
        if (!model || !assignment || !model.recommendedFeeds?.length) {
            return;
        }

        // 메인 페이지 재방문이 아니라 로그인 성공 직후에만 자동 표시합니다.
        if (!force) {
            return;
        }

        const createdAt = Date.parse(state.member.createdAt || "");
        const oneWeek = 7 * 24 * 60 * 60 * 1000;
        const membershipAge = Date.now() - createdAt;
        if (!Number.isFinite(createdAt)
            || membershipAge < 0
            || membershipAge >= oneWeek) {
            return;
        }

        const today = new Date().toLocaleDateString("sv-SE");
        const hideTodayKey = `feedflow-farm-model-hide-day-${state.member.id}`;
        if (window.localStorage.getItem(hideTodayKey) === today) {
            return;
        }

        const hideToday = $("#farm-popup-hide-today");
        if (hideToday) {
            hideToday.checked = false;
        }

        const remainingDays = Math.max(
            1,
            Math.ceil((oneWeek - membershipAge) / (24 * 60 * 60 * 1000))
        );
        const period = $("#farm-popup-period");
        if (period) {
            period.textContent = `신규 회원 맞춤 안내 · 앞으로 ${remainingDays}일간 로그인 시 제공`;
        }
        $("#farm-popup-animal").textContent = model.animalType || "-";
        $("#farm-popup-quantity").textContent =
            `${Number(model.monthlyFeedQuantity || 0).toLocaleString("ko-KR")}포대${model.monthlyQuantityEstimated ? " (예측)" : ""}`;
        $("#farm-popup-warehouse").textContent = assignment.warehouseName || "-";
        $("#farm-popup-products").innerHTML = model.recommendedFeeds
            .slice(0, 3)
            .map((feed) => `
                <article>
                    <img src="${escapeHtml(feed.imageUrl || "/images/feed-bag-warehouse.png")}" alt="${escapeHtml(feed.name)}">
                    <div><small>${escapeHtml(feed.animalType || model.animalType)} · ${escapeHtml(feed.feedStage || "일반 배합")}</small><strong>${escapeHtml(feed.name)}</strong><span>${escapeHtml(feed.reason || "농장 맞춤 추천")}</span><small>${escapeHtml(feed.availabilityLabel || "판매 가능")} · ${Number(feed.sellableStock || 0).toLocaleString("ko-KR")}포대</small></div>
                    <a href="/shop/products/${encodeURIComponent(feed.productId)}">상세 보기</a>
                </article>`)
            .join("");
        window.setTimeout(() => openModal("farm-model-modal"), 250);
    }

    function fillCheckoutFromMember() {
        if (!state.member) {
            return;
        }

        const farmAddress =
            state.member.addresses?.find(
                (item) => item.addressType === "FARM"
            )
            || state.member.addresses?.find(
                (item) => item.defaultAddress
            );

        const orderName = $("#order-name");
        const orderPhone = $("#order-phone");
        const orderAddress = $("#order-address");
        const orderPostcode = $("#order-postcode");
        const orderDetail = $("#order-detail");
        const orderUnloading = $("#order-unloading");

        if (orderName) {
            orderName.value = state.member.name || "";
        }

        if (orderPhone) {
            orderPhone.value = state.member.phone || "";
        }

        if (orderAddress) {
            orderAddress.value =
                farmAddress?.baseAddress || "";
        }

        if (orderPostcode) {
            orderPostcode.value = farmAddress?.postalCode || "";
        }

        if (orderDetail) {
            orderDetail.value =
                farmAddress?.detailAddress || "";
        }

        if (orderUnloading) {
            orderUnloading.value =
                farmAddress?.unloadingLocation || "";
        }
    }

    function beginCheckout() {
        if (!cartRows().length) {
            return;
        }

        if (state.member) {
            const suggestion = monthlyQuantitySuggestion();
            if (suggestion
                && suggestion.signature !== state.monthlyQuantityPromptSignature) {
                state.monthlyQuantitySuggestion = suggestion;
                renderMonthlyQuantitySuggestion(suggestion);
                openModal("monthly-quantity-modal");
                return;
            }
            openCheckoutModal();
            return;
        }

        state.pendingCheckout = true;
        showAccount("login");
        showToast("상품 주문은 로그인한 회원만 이용할 수 있습니다.");
    }

    function openCheckoutModal() {
        if (!state.member || !cartRows().length) return;
        fillCheckoutFromMember();
        openModal("checkout-modal");
        renderCheckoutSummary();
    }

    function monthlyMinimumQuantity() {
        const model = state.member?.farmModel;
        if (!model) return 0;
        const livestockCount = Number(model.livestockCount || 0);
        const bagsPerHead = Number(model.monthlyBagsPerHead || 0);
        if (livestockCount > 0 && bagsPerHead > 0) {
            return Math.max(1, Math.ceil(livestockCount * bagsPerHead));
        }
        return Math.max(0, Number(model.monthlyFeedQuantity || 0));
    }

    function monthlyQuantitySuggestion() {
        const rows = cartRows();
        const required = monthlyMinimumQuantity();
        const current = rows.reduce((sum, row) => sum + row.quantity, 0);
        if (!required || current >= required) return null;

        let shortage = required - current;
        const additions = new Map();
        rows.forEach(({ product, quantity }) => {
            if (shortage <= 0) return;
            const available = Math.max(0, purchasableStock(product) - quantity);
            const addition = Math.min(available, shortage);
            if (addition > 0) {
                additions.set(product.id, addition);
                shortage -= addition;
            }
        });

        const suggested = required - shortage;
        const signature = `${required}:` + rows
            .map(({ product, quantity }) => `${product.id}-${quantity}`)
            .sort()
            .join("|");
        return { required, current, suggested, shortage, additions, signature };
    }

    function renderMonthlyQuantitySuggestion(suggestion) {
        const model = state.member?.farmModel;
        const animalLabels = {
            CATTLE: "소",
            DAIRY_CATTLE: "젖소",
            PIG: "돼지",
            CHICKEN: "닭",
            DUCK: "오리",
            POULTRY: "조류"
        };
        const animal = animalLabels[model?.animalType]
            || model?.animalType
            || "등록 축종";
        $("#monthly-quantity-description").textContent =
            `${animal} ${Number(model?.livestockCount || 0).toLocaleString("ko-KR")}두/수의 `
            + `사육 정보를 기준으로 이번 달 최소 필요량은 약 `
            + `${suggestion.required.toLocaleString("ko-KR")}포대로 예상됩니다. `
            + "추천 수량을 적용하거나 현재 주문 수량을 그대로 유지할 수 있습니다.";
        $("#monthly-current-quantity").textContent =
            `${suggestion.current.toLocaleString("ko-KR")}포대`;
        $("#monthly-required-quantity").textContent =
            `${suggestion.required.toLocaleString("ko-KR")}포대`;
        $("#monthly-suggested-quantity").textContent =
            `${suggestion.suggested.toLocaleString("ko-KR")}포대`;
        $("#monthly-quantity-note").textContent = suggestion.shortage > 0
            ? `선택 상품의 판매 가능 재고가 부족하여 최소량보다 ${suggestion.shortage.toLocaleString("ko-KR")}포대 적게 제안됩니다.`
            : `현재 주문보다 ${(suggestion.required - suggestion.current).toLocaleString("ko-KR")}포대를 추가하면 월 예상 최소량에 맞출 수 있습니다.`;
        const applyButton = $("[data-monthly-quantity-apply]");
        if (applyButton) applyButton.disabled = suggestion.additions.size === 0;
    }

    function applyMonthlyQuantitySuggestion() {
        const suggestion = state.monthlyQuantitySuggestion;
        if (!suggestion) return;
        suggestion.additions.forEach((addition, productId) => {
            state.cart.set(
                Number(productId),
                (state.cart.get(Number(productId)) || 0) + addition
            );
        });
        saveCart();
        renderCartCount();
        state.monthlyQuantityPromptSignature = null;
        state.monthlyQuantitySuggestion = null;
        showToast("월 예상 최소량에 맞춰 주문 수량을 조정했습니다.");
        openCheckoutModal();
    }

    function keepCurrentMonthlyQuantity() {
        const suggestion = state.monthlyQuantitySuggestion;
        if (suggestion) {
            state.monthlyQuantityPromptSignature = suggestion.signature;
        }
        state.monthlyQuantitySuggestion = null;
        openCheckoutModal();
    }

    function statusLabel(status) {
        if (status === "PAID") {
            return "결제완료";
        }

        if (status === "CANCELLED") {
            return "취소완료";
        }

        if (status === "PAYMENT_PENDING") {
            return "결제대기";
        }

        return status || "-";
    }

    /*
     * 로그인된 상태에서 상단 계정 버튼을 누르면
     * 로그인 모달 대신 마이페이지로 이동합니다.
     */
    function showAccount(tab = "login") {
        if (tab === "login" && state.member) {
            window.location.href = "/mypage";
            return;
        }

        openModal("account-modal");
        switchAccountTab(tab);
    }

    function switchAccountTab(tab) {
        $$("[data-account-tab]").forEach((button) => {
            button.classList.toggle(
                "active",
                button.dataset.accountTab === tab
            );
        });

        const loginForm = $("#login-form");
        const signupForm = $("#signup-form");
        const findUsernameForm = $("#find-username-form");
        const resetPasswordForm = $("#reset-password-form");
        const accountTitle = $("#account-title");

        if (loginForm) {
            loginForm.hidden = tab !== "login";
        }

        if (signupForm) {
            signupForm.hidden = tab !== "signup";
        }

        if (findUsernameForm) {
            findUsernameForm.hidden = tab !== "find-username";
        }

        if (resetPasswordForm) {
            resetPasswordForm.hidden = tab !== "reset-password";
        }

        if (accountTitle) {
            const titles = {
                login: "농장 계정 로그인",
                signup: "농장 회원가입",
                "find-username": "아이디 찾기",
                "reset-password": "비밀번호 재설정"
            };

            accountTitle.textContent = titles[tab] || "농장 계정";
        }
    }

    function renderCheckoutSummary() {
        const checkoutSummary = $("#checkout-summary");
        const invoiceBody = $("#checkout-invoice-body");
        const invoiceTotal = $("#checkout-invoice-total");

        if (!checkoutSummary) {
            return;
        }

        const amounts = cartAmounts();
        const rows = cartRows();
        const paymentLabels = {
            CARD: "일반 신용카드",
            KAKAO_PAY: "카카오페이",
            BANK_TRANSFER: "무통장입금"
        };

        if (invoiceBody) {
            invoiceBody.innerHTML = rows.map(
                ({ product, quantity }) => `
                    <tr>
                        <td>${escapeHtml(product.name)}</td>
                        <td>${quantity}</td>
                        <td>${won(product.price)}</td>
                        <td><strong>${won(product.price * quantity)}</strong></td>
                    </tr>
                `
            ).join("");
        }

        if (invoiceTotal) {
            invoiceTotal.textContent = won(amounts.productAmount);
        }

        checkoutSummary.innerHTML = `
            <h3>주문 요약</h3>

            <div class="summary-line">
                <span>상품 금액</span>
                <strong>${won(amounts.productAmount)}</strong>
            </div>

            <div class="summary-line">
                <span>배송비</span>
                <strong>
                    ${
                        amounts.deliveryFee
                            ? won(amounts.deliveryFee)
                            : "무료"
                    }
                </strong>
            </div>

            <div class="summary-line">
                <span>결제 수단</span>
                <strong>
                    ${paymentLabels[selectedPaymentMethod()] || "-"}
                </strong>
            </div>

            <div class="summary-line total">
                <span>총 결제금액</span>
                <strong>${won(amounts.total)}</strong>
            </div>

            <button
                class="primary-button"
                type="submit"
            >
                결제하기
            </button>
        `;
    }

    $$("input[name='payment']").forEach((input) => {
        input.addEventListener("change", renderCheckoutSummary);
    });

    document.addEventListener("click", (event) => {
        const button = event.target.closest(
            "button, [role='button']"
        );

        if (!button) {
            return;
        }

        if (button.matches("[data-close-modal]")) {
            closeModal();
        }

        if (button.dataset.category) {
            state.category = button.dataset.category;

            $$("[data-category]").forEach((item) => {
                item.classList.toggle(
                    "active",
                    item === button
                );
            });

            renderProducts();

            $("#products")?.scrollIntoView({
                behavior: "smooth"
            });
        }

        if (button.dataset.addCart) {
            addToCart(button.dataset.addCart);
        }

        if (button.dataset.modalAdd) {
            addToCart(
                button.dataset.modalAdd,
                Math.max(
                    1,
                    Number($("#detail-quantity")?.value || 1)
                )
            );

            closeModal();
        }

        if (button.dataset.quickBuy) {
            state.cart.clear();

            const quantity = Math.max(
                1,
                Number($("#detail-quantity")?.value || 1)
            );

            addToCart(
                button.dataset.quickBuy,
                quantity
            );

            beginCheckout();
        }

        if (button.hasAttribute("data-monthly-quantity-apply")) {
            applyMonthlyQuantitySuggestion();
        }

        if (button.hasAttribute("data-monthly-quantity-keep")) {
            keepCurrentMonthlyQuantity();
        }

        if (button.dataset.detail) {
            const product = productById(
                button.dataset.detail
            );

            if (product) {
                showProduct(product);
            }
        }

        if (button.dataset.favorite) {
            const id = Number(button.dataset.favorite);
            void toggleFavorite(id);
        }

        if (button.dataset.openAccount) {
            showAccount(button.dataset.openAccount);
        }

        if (button.dataset.accountTab) {
            switchAccountTab(
                button.dataset.accountTab
            );
        }

        if (button.dataset.cartMinus) {
            changeCart(
                button.dataset.cartMinus,
                -1
            );
        }

        if (button.dataset.cartPlus) {
            changeCart(
                button.dataset.cartPlus,
                1
            );
        }

        if (button.dataset.cartRemove) {
            state.cart.delete(
                Number(button.dataset.cartRemove)
            );

            saveCart();
            renderCart();
        }

        if (button.dataset.detailQuantity) {
            const input = $("#detail-quantity");

            if (input) {
                input.value = Math.min(
                    Number(input.max),
                    Math.max(
                        1,
                        Number(input.value)
                        + Number(button.dataset.detailQuantity)
                    )
                );
            }
        }

        if (button.hasAttribute("data-scroll-consulting")) {
            $("#consulting")?.scrollIntoView({
                behavior: "smooth"
            });
        }

        if (button.hasAttribute("data-consult")) {
            showToast(
                "상담 신청이 접수되었습니다. 평일 중 연락드리겠습니다."
            );
        }

        if (button.dataset.footerMessage) {
            showToast(button.dataset.footerMessage);
        }

        if (button.hasAttribute("data-view-order")) {
            window.location.href = "/mypage";
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeModal();
        }

        if (
            event.key === "Enter"
            && event.target.matches("[data-detail]")
        ) {
            const product = productById(
                event.target.dataset.detail
            );

            if (product) {
                showProduct(product);
            }
        }
    });

    $("#search-input")?.addEventListener(
        "input",
        (event) => {
            state.query = event.target.value;
            renderProducts();
        }
    );

    $("#sort-select")?.addEventListener(
        "change",
        (event) => {
            state.sort = event.target.value;
            renderProducts();
        }
    );

    $("#mobile-menu")?.addEventListener(
        "click",
        () => {
            $("#category-nav")?.classList.toggle("open");
        }
    );

    $("#open-cart")?.addEventListener(
        "click",
        () => {
            renderCart();
            openModal("cart-modal");
        }
    );

    $("#start-checkout")?.addEventListener(
        "click",
        beginCheckout
    );

    $("#modal-backdrop")?.addEventListener(
        "mousedown",
        (event) => {
            if (event.target === event.currentTarget) {
                closeModal();
            }
        }
    );

    $("#check-username-button")?.addEventListener(
        "click",
        checkUsernameAvailability
    );

    $("#search-home-address")?.addEventListener(
        "click",
        () => openKakaoPostcode(
            "signup-home-address",
            "signup-home-postcode",
            "signup-home-detail"
        )
    );

    $("#search-farm-address")?.addEventListener(
        "click",
        () => openKakaoPostcode(
            "signup-farm-address",
            "signup-farm-postcode",
            "signup-unloading"
        )
    );

    $("#search-order-address")?.addEventListener(
        "click",
        () => openKakaoPostcode(
            "order-address",
            "order-postcode",
            "order-detail"
        )
    );

    $("#signup-username")?.addEventListener(
        "input",
        () => {
            state.usernameAvailable = false;
            const input = $("#signup-username");
            const message = $("#signup-username-message");
            input.classList.remove("input-valid");
            input.classList.remove("input-invalid");
            message.classList.remove("success");
            message.classList.remove("error");
            message.textContent =
                "아이디를 입력한 후 중복확인을 눌러주세요.";
        }
    );

    $("#signup-password")?.addEventListener(
        "input",
        validatePassword
    );

    // [수정] 비밀번호 재설정 입력값을 입력하는 즉시 검사합니다.
    $("#reset-password-new")?.addEventListener(
        "input",
        validateResetPassword
    );

    $("#reset-password-confirm")?.addEventListener(
        "input",
        validateResetPasswordConfirmation
    );

    $("#reset-password-phone")?.addEventListener(
        "input",
        (event) => {
            event.target.value = formatMobilePhone(event.target.value);
        }
    );

    const formatSignupPhone = function (value) {
        const digits = value.replace(/\D/g, "").slice(0, 11);
        if (digits.length <= 3) return digits;
        if (digits.length <= 7) return digits.slice(0, 3) + "-" + digits.slice(3);
        return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7);
    };

    $("#signup-phone")?.addEventListener("input", function (event) {
        event.target.value = formatSignupPhone(event.target.value);
    });

    $("#signup-same-address")?.addEventListener("change", function (event) {
        const farmAddress = $("#signup-farm-address");
        const farmPostcode = $("#signup-farm-postcode");
        const homeAddress = $("#signup-home-address");
        const homePostcode = $("#signup-home-postcode");
        const searchButton = $("#search-farm-address");
        if (event.target.checked) {
            farmAddress.dataset.previousValue = farmAddress.value;
            farmPostcode.dataset.previousValue = farmPostcode.value;
            farmAddress.value = homeAddress.value;
            farmPostcode.value = homePostcode.value;
            farmAddress.readOnly = true;
            searchButton.disabled = true;
            searchButton.title = "자택 주소와 동일하게 사용 중입니다.";
        } else {
            farmAddress.value = farmAddress.dataset.previousValue || "";
            farmPostcode.value = farmPostcode.dataset.previousValue || "";
            searchButton.disabled = false;
            searchButton.title = "";
        }
    });

    $("#signup-home-address")?.addEventListener("change", function () {
        const sameAddress = $("#signup-same-address");
        if (sameAddress?.checked) {
            $("#signup-farm-address").value = $("#signup-home-address").value;
            $("#signup-farm-postcode").value = $("#signup-home-postcode").value;
        }
    });

    $("#signup-home-postcode")?.addEventListener("change", function () {
        const sameAddress = $("#signup-same-address");
        if (sameAddress?.checked) {
            $("#signup-farm-postcode").value = $("#signup-home-postcode").value;
        }
    });

    $("#reset-password-send-code")?.addEventListener(
        "click",
        async () => {
            const identity = resetCodeIdentity();
            if (!identity.username || !identity.email || !identity.phone) {
                showToast("아이디, 이메일, 휴대전화를 먼저 입력해주세요.");
                return;
            }
            const button = $("#reset-password-send-code");
            button.disabled = true;
            try {
                const result = await api(
                    "/api/members/password-reset/code",
                    {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(identity)
                    }
                );
                resetCodeExpiresAt = Date.now() + Number(result.expiresInSeconds || 300) * 1000;
                resetCodeResendAt = Date.now() + Number(result.resendAvailableInSeconds || 30) * 1000;
                if (result.debugCode) {
                    $("#reset-password-code").value = result.debugCode;
                    showToast(`로컬 테스트 인증번호: ${result.debugCode}`);
                } else {
                    showToast("인증번호를 등록된 이메일로 발송했습니다. 메일함을 확인해 주세요.");
                }
                window.clearInterval(resetCodeTimer);
                resetCodeTimer = window.setInterval(updateResetCodeTimer, 1000);
                updateResetCodeTimer();
            } catch (error) {
                button.disabled = false;
                showToast(error.message);
            }
        }
    );

    $("#signup-email")?.addEventListener("input", () => {
        window.clearTimeout(emailAvailabilityTimer);
        emailAvailabilityRequest += 1;
        if (!validateEmail()) return;
        const message = $("#signup-email-message");
        if (message) {
            message.classList.remove("success", "error");
            message.textContent = "이메일 중복 여부를 확인하고 있습니다.";
        }
        emailAvailabilityTimer = window.setTimeout(
            () => checkEmailAvailability(),
            350
        );
    });

    $("#signup-email")?.addEventListener("blur", () => {
        window.clearTimeout(emailAvailabilityTimer);
        checkEmailAvailability();
    });

    $("#login-form")?.addEventListener(
        "submit",
        async (event) => {
            event.preventDefault();

            try {
                const pendingCart = state.pendingCheckout
                    ? new Map(state.cart)
                    : null;
                const login = await api(
                    "/api/auth/login",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            identifier:
                                $("#login-username").value.trim(),
                            password: $("#login-password").value
                        })
                    }
                );

                if (login.accountType === "ADMIN"
                    || login.accountType === "STAFF") {
                    window.location.href = login.redirectUrl || "/";
                    return;
                }

                const member = login.member;
                if (!member) {
                    throw new Error("로그인 회원 정보를 확인하지 못했습니다.");
                }

                state.member = member;

                loadCartForCurrentIdentity();
                if (pendingCart) {
                    pendingCart.forEach((quantity, productId) => {
                        const product = productById(productId);
                        const stock = purchasableStock(product);
                        if (product && stock > 0) {
                            state.cart.set(
                                Number(productId),
                                Math.min(stock, Math.max(1, Number(quantity)))
                            );
                        }
                    });
                    saveCart();
                }
                renderCartCount();

                window.sessionStorage.setItem(
                    "feedflow-member",
                    JSON.stringify(member)
                );

                window.sessionStorage.setItem(
                    "feedflow-show-farm-model-after-login",
                    String(member.id)
                );

                updateMemberUi();

                if (state.pendingFavoriteId) {
                    const productId = state.pendingFavoriteId;
                    state.pendingFavoriteId = null;
                    await toggleFavorite(productId, true);
                    window.location.href = "/mypage";
                    return;
                }

                if (state.pendingCheckout) {
                    state.pendingCheckout = false;
                    beginCheckout();
                } else {
                    // 일반 회원 로그인은 판매 메인 화면으로 이동합니다.
                    // 마이페이지는 헤더의 마이페이지 메뉴를 눌렀을 때 엽니다.
                    window.location.href = "/";
                }
            } catch (error) {
                showToast(error.message);
            }
        }
    );

    $("#find-username-form")?.addEventListener(
        "submit",
        async (event) => {
            event.preventDefault();

            const resultBox = $("#find-username-result");
            resultBox.hidden = true;
            resultBox.textContent = "";

            try {
                const result = await api(
                    "/api/members/find-username",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            name: $("#find-username-name").value.trim(),
                            email: $("#find-username-email").value.trim()
                        })
                    }
                );

                resultBox.textContent = `가입한 아이디는 ${result.username} 입니다.`;
                resultBox.hidden = false;
                $("#login-username").value = result.username;
                showToast(result.message);
            } catch (error) {
                showToast(error.message);
            }
        }
    );

    $("#reset-password-form")?.addEventListener(
        "submit",
        async (event) => {
            event.preventDefault();

            const username = $("#reset-password-username").value.trim();
            const newPassword = $("#reset-password-new").value;

            // [수정] 회원가입과 동일한 비밀번호 유효성 검사를 통과해야 합니다.
            if (!validateResetPassword()) {
                showToast(
                    "새 비밀번호는 영문, 숫자, 특수문자를 포함한 8자 이상이어야 합니다."
                );
                $("#reset-password-new").focus();
                return;
            }

            // [수정] 두 비밀번호가 일치하지 않으면 서버 요청을 차단합니다.
            if (!validateResetPasswordConfirmation()) {
                showToast("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                $("#reset-password-confirm").focus();
                return;
            }

            try {
                const result = await api(
                    "/api/members/reset-password",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            username,
                            email: $("#reset-password-email").value.trim(),
                            phone: $("#reset-password-phone").value.trim(),
                            code: $("#reset-password-code").value.trim(),
                            newPassword
                        })
                    }
                );

                $("#reset-password-form").reset();
                resetCodeExpiresAt = 0;
                resetCodeResendAt = 0;
                window.clearInterval(resetCodeTimer);
                resetCodeTimer = null;

                // [수정] 성공 후 입력창과 검사 안내를 처음 상태로 되돌립니다.
                $("#reset-password-new")?.classList.remove(
                    "input-valid",
                    "input-invalid"
                );
                $("#reset-password-confirm")?.classList.remove(
                    "input-valid",
                    "input-invalid"
                );

                const newMessage = $("#reset-password-new-message");
                const confirmMessage = $("#reset-password-confirm-message");

                if (newMessage) {
                    newMessage.classList.remove("success", "error");
                    newMessage.textContent =
                        "영문, 숫자, 특수문자를 모두 포함해주세요.";
                }

                if (confirmMessage) {
                    confirmMessage.classList.remove("success", "error");
                    confirmMessage.textContent =
                        "새 비밀번호를 다시 입력해주세요.";
                }

                $("#login-username").value = username;
                $("#login-password").value = "";
                switchAccountTab("login");
                showToast(result.message);
                $("#login-password").focus();
            } catch (error) {
                showToast(error.message);
            }
        }
    );

    $("#signup-form")?.addEventListener(
        "submit",
        async (event) => {
            event.preventDefault();

            const name = $("#signup-name").value;
            const phone = $("#signup-phone").value;

            if (!state.usernameAvailable) {
                showToast("아이디 중복확인을 완료해주세요.");
                $("#signup-username").focus();
                return;
            }

            if (!validatePassword()) {
                showToast("비밀번호 형식을 확인해주세요.");
                $("#signup-password").focus();
                return;
            }

            if (!await checkEmailAvailability(true)) {
                showToast("이메일 중복 여부를 확인해주세요.");
                return;
            }

            try {
                const member = await api(
                    "/api/members/signup",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            username:
                                $("#signup-username").value.trim(),
                            email: $("#signup-email").value,
                            password: $("#signup-password").value,
                            name,
                            farmName: $("#signup-farm-name").value,
                            phone,
                            businessNumber:
                                $("#signup-business").value
                                || null,
                            regularDeliveryDay:
                                $("#signup-delivery-day").value
                                    ? Number($("#signup-delivery-day").value)
                                    : null,
                            farmProfile: {
                                animalType:
                                    $("#signup-animal-type").value,
                                livestockCount:
                                    Number($("#signup-livestock-count").value),
                                monthlyFeedQuantity:
                                    $("#signup-monthly-feed").value
                                        ? Number($("#signup-monthly-feed").value)
                                        : null,
                                preferredFeed:
                                    $("#signup-preferred-feed").value.trim()
                                    || null,
                                latitude: null,
                                longitude: null
                            },
                            homeAddress: {
                                addressType: "HOME",
                                recipientName: name,
                                phone,
                                postalCode:
                                    $("#signup-home-postcode").value,
                                baseAddress:
                                    $("#signup-home-address").value,
                                detailAddress:
                                    $("#signup-home-detail").value,
                                unloadingLocation: "",
                                defaultAddress: true
                            },
                            farmAddress: {
                                addressType: "FARM",
                                recipientName: name,
                                phone,
                                postalCode:
                                    $("#signup-farm-postcode").value,
                                baseAddress:
                                    $("#signup-farm-address").value,
                                detailAddress:
                                    $("#signup-same-address")?.checked
                                        ? $("#signup-home-detail").value
                                        : "",
                                unloadingLocation:
                                    $("#signup-unloading").value,
                                defaultAddress: false
                            }
                        })
                    }
                );

                $("#login-username").value =
                    member.username;
                $("#login-password").value = "";
                state.usernameAvailable = false;
                const model = member.farmModel;
                const assignment = member.farmAssignment;
                if (model && assignment) {
                    $("#signup-result-warehouse").textContent =
                        `${assignment.warehouseName} · 약 ${Number(assignment.distanceKm).toFixed(1)}km`;
                    $("#signup-result-quantity").textContent =
                        `${Number(model.monthlyFeedQuantity).toLocaleString("ko-KR")}포대${model.monthlyQuantityEstimated ? " (예측)" : ""}`;
                    $("#signup-result-samples").textContent =
                        `유사 농장 ${model.comparableFarmCount}곳`;
                    $("#signup-result-feed").textContent =
                        model.recommendedFeeds?.[0]?.name
                        || model.preferredFeed
                        || "상담 후 지정";
                    $("#signup-result-basis").textContent = model.modelBasis;
                    $("#signup-model-result").hidden = false;
                    $("#signup-submit-button").disabled = true;
                    $("#signup-model-result").scrollIntoView({
                        behavior: "smooth",
                        block: "nearest"
                    });
                }
                showToast("회원가입과 농장 맞춤 분석이 완료되었습니다.");
            } catch (error) {
                showToast(error.message);
            }
        }
    );

    $("#checkout-form")?.addEventListener(
        "submit",
        async (event) => {
            event.preventDefault();

            if (!cartRows().length || state.checkoutSubmitting) {
                return;
            }

            state.checkoutSubmitting = true;
            const submitButton = event.currentTarget.querySelector(
                'button[type="submit"]'
            );
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "주문을 처리하고 있습니다...";
            }

            const phone = $("#order-phone").value.trim();
            const paymentMethod = selectedPaymentMethod();
            let createdOrder = null;

            try {
                const paymentConfig = await loadPaymentConfig();
                ensurePaymentAvailable(paymentMethod, paymentConfig);

                createdOrder = await api(
                    "/api/orders",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            customerName:
                                $("#order-name").value.trim(),
                            phone,
                            postalCode:
                                $("#order-postcode").value.trim(),
                            address:
                                $("#order-address").value.trim(),
                            detailAddress:
                                $("#order-detail").value.trim(),
                            unloadingLocation:
                                $("#order-unloading").value.trim(),
                            deliveryRequest:
                                $("#order-request").value.trim(),
                            paymentMethod:
                                paymentMethod,
                            regularDelivery: Boolean(
                                state.member?.regularDeliveryDay
                            ),
                            items: cartRows().map(
                                ({ product, quantity }) => ({
                                    productId: product.id,
                                    quantity
                                })
                            )
                        })
                    }
                );

                window.sessionStorage.setItem(
                    "feedflow-pending-order",
                    JSON.stringify(createdOrder)
                );

                await requestPortOnePayment(
                    createdOrder,
                    paymentMethod,
                    paymentConfig
                );
            } catch (error) {
                // 외부 거래가 발생하지 않은 주문만 취소하여 예약 재고를 복원합니다.
                // 결제 승인 후 서버 검증 중 오류가 난 경우에는 자동 취소하지 않고
                // 웹훅과 관리자 확인을 통해 결제 상태를 복구합니다.
                if (
                    createdOrder?.orderNumber
                    && !error.paymentMayExist
                    && !error.orderHandled
                ) {
                    try {
                        await markPortOneOrderFailed(createdOrder);
                    } catch (cancelError) {
                        console.error("결제 실패 주문 취소 오류", cancelError);
                    }
                }
                showToast(error.message);
            } finally {
                state.checkoutSubmitting = false;
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = "결제하기";
                }
            }
        }
    );

    $("#utility-logout")?.addEventListener(
        "click",
        async () => {
            try {
                await api(
                    "/api/members/logout",
                    {
                        method: "POST"
                    }
                );
            } finally {
                window.sessionStorage.removeItem(
                    "feedflow-show-farm-model-after-login"
                );
                state.member = null;
                state.cart = new Map();
                state.favorites = new Set();
                state.pendingFavoriteId = null;

                window.sessionStorage.removeItem(
                    "feedflow-member"
                );

                updateMemberUi();
                renderCartCount();
                renderProducts();

                showToast("로그아웃되었습니다.");
            }
        }
    );

    restoreBrowserState()
        .then(handlePaymentReturn)
        .finally(loadProducts);
})();

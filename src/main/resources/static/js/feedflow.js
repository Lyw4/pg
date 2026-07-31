(() => {
    "use strict";

    const state = {
        products: [],
        category: "ALL",
        query: "",
        sort: "recommended",
        cart: new Map(),
        favorites: new Set(),
        member: null,
        selectedProduct: null,
        lookupOrder: null,
        lastOrderPhone: ""
    };

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");

    let toastTimer;
    function showToast(message) {
        const toast = $("#toast");
        toast.textContent = message;
        toast.classList.add("show");
        clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2400);
    }

    async function api(url, options = {}) {
        const response = await fetch(url, options);
        const contentType = response.headers.get("content-type") || "";
        const result = contentType.includes("application/json")
            ? await response.json()
            : null;
        if (!response.ok) {
            throw new Error(result?.message || `요청을 처리하지 못했습니다. (${response.status})`);
        }
        return result;
    }

    function daysUntil(dateText) {
        if (!dateText) return "";
        const target = new Date(`${dateText}T00:00:00`);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        return Math.ceil((target - today) / 86400000);
    }

    function bagMarkup(product, extraClass = "") {
        const mark = escapeHtml(product.shape || product.animal || "FEED");
        return `
            <div class="feed-bag ${extraClass}">
                <small>FEED FLOW</small>
                <strong>${mark}</strong>
                <span>${escapeHtml(product.animal)} · ${escapeHtml(product.stage)}</span>
                <b>${escapeHtml(product.weight)} kg</b>
            </div>`;
    }

    function filteredProducts() {
        const normalized = state.query.trim().toLowerCase();
        const result = state.products.filter((product) => {
            const categoryMatch =
                state.category === "ALL"
                || (state.category === "CATTLE_GROUP" && ["CATTLE", "DAIRY_CATTLE"].includes(product.animalType))
                || (state.category === "POULTRY_GROUP" && ["CHICKEN", "DUCK"].includes(product.animalType))
                || product.animalType === state.category;
            const haystack = `${product.name} ${product.animal} ${product.stage} ${product.description}`.toLowerCase();
            return categoryMatch && (!normalized || haystack.includes(normalized));
        });
        return result.sort((a, b) => {
            if (state.sort === "low") return a.price - b.price;
            if (state.sort === "high") return b.price - a.price;
            if (state.sort === "stock") return b.stock - a.stock;
            return Number(Boolean(b.badge)) - Number(Boolean(a.badge)) || a.id - b.id;
        });
    }

    function renderProducts() {
        const products = filteredProducts();
        const grid = $("#product-grid");
        $("#empty-products").hidden = products.length > 0;
        grid.innerHTML = products.map((product) => {
            const favorite = state.favorites.has(product.id);
            return `
                <article class="product-card" data-product-id="${Number(product.id)}">
                    <div class="product-visual" data-detail="${Number(product.id)}" role="button" tabindex="0" aria-label="${escapeHtml(product.name)} 상세보기">
                        ${product.badge ? `<span class="badge">${escapeHtml(product.badge)}</span>` : ""}
                        <button class="favorite ${favorite ? "active" : ""}" type="button" data-favorite="${Number(product.id)}" aria-label="관심상품 ${favorite ? "해제" : "등록"}">${favorite ? "♥" : "♡"}</button>
                        ${bagMarkup(product)}
                    </div>
                    <div class="product-info">
                        <div class="product-tags"><span>${escapeHtml(product.animal)}</span><span>${escapeHtml(product.stage)}</span></div>
                        <h3>${escapeHtml(product.name)}</h3>
                        <p>${escapeHtml(product.description)}</p>
                        <div class="stock-line"><span>LOT ${escapeHtml(product.lot || "미등록")}</span><strong>재고 ${Number(product.stock).toLocaleString("ko-KR")}포</strong></div>
                        <div class="product-price">
                            ${product.originalPrice ? `<del>${won(product.originalPrice)}</del>` : ""}
                            <strong>${won(product.price)}</strong>
                        </div>
                        <button class="card-add" type="button" data-add-cart="${Number(product.id)}" ${product.stock < 1 ? "disabled" : ""}>${product.stock < 1 ? "품절" : "장바구니 담기 ＋"}</button>
                    </div>
                </article>`;
        }).join("");
    }

    async function loadProducts() {
        try {
            state.products = await api("/api/products");
            $("#connection-status").textContent = "H2 연동됨";
            renderProducts();
            renderCartCount();
        } catch (error) {
            $("#connection-status").textContent = "상품 API 확인 필요";
            showToast(error.message);
        }
    }

    function productById(id) {
        return state.products.find((product) => product.id === Number(id));
    }

    function openModal(id) {
        $("#modal-backdrop").hidden = false;
        $$(".modal").forEach((modal) => { modal.hidden = modal.id !== id; });
        document.body.style.overflow = "hidden";
    }

    function closeModal() {
        $("#modal-backdrop").hidden = true;
        $$(".modal").forEach((modal) => { modal.hidden = true; });
        document.body.style.overflow = "";
    }

    function showProduct(product) {
        state.selectedProduct = product;
        const dDay = daysUntil(product.expiry);
        $("#product-modal-content").innerHTML = `
            <div class="detail-layout">
                <div class="detail-visual">${bagMarkup(product)}</div>
                <div class="detail-info">
                    <div class="product-tags"><span>${escapeHtml(product.animal)}</span><span>${escapeHtml(product.stage)}</span></div>
                    <h2 id="product-modal-title">${escapeHtml(product.name)}</h2>
                    <p>${escapeHtml(product.description)}</p>
                    <div class="nutrients">
                        <div><span>조단백</span><strong>${Number(product.protein)}%</strong></div>
                        <div><span>조지방</span><strong>${Number(product.fat)}%</strong></div>
                        <div><span>조섬유</span><strong>${Number(product.fiber)}%</strong></div>
                        <div><span>칼슘</span><strong>${Number(product.calcium)}%</strong></div>
                    </div>
                    <div class="lot-panel">
                        <div><span>LOT 번호</span><strong>${escapeHtml(product.lot || "미등록")}</strong></div>
                        <div><span>유통기한</span><strong>${escapeHtml(product.expiry || "미정")} ${dDay >= 0 ? `(D-${dDay})` : "(기간 경과)"}</strong></div>
                        <div><span>잔여 재고</span><strong>${Number(product.stock).toLocaleString("ko-KR")}포</strong></div>
                    </div>
                    <div class="detail-price">${won(product.price)} <small>· ${escapeHtml(product.weight)}kg</small></div>
                    <div class="detail-buttons">
                        <button class="secondary-button" type="button" data-modal-add="${Number(product.id)}">장바구니 담기</button>
                        <button class="primary-button" type="button" data-quick-buy="${Number(product.id)}">바로 구매하기</button>
                    </div>
                </div>
            </div>`;
        openModal("product-modal");
    }

    function addToCart(id, quantity = 1) {
        const product = productById(id);
        if (!product || product.stock < 1) {
            showToast("주문 가능한 재고가 없습니다.");
            return;
        }
        const current = state.cart.get(product.id) || 0;
        state.cart.set(product.id, Math.min(product.stock, current + quantity));
        renderCartCount();
        showToast(`${product.name}을 장바구니에 담았습니다.`);
    }

    function cartRows() {
        return [...state.cart.entries()]
            .map(([id, quantity]) => ({ product: productById(id), quantity }))
            .filter((item) => item.product);
    }

    function cartAmounts(regular = false) {
        const productAmount = cartRows().reduce((sum, item) => sum + item.product.price * item.quantity, 0);
        const deliveryFee = productAmount === 0 || productAmount >= 150000 ? 0 : 5000;
        const discount = regular ? Math.round(productAmount * .03) : 0;
        return { productAmount, deliveryFee, discount, total: productAmount + deliveryFee - discount };
    }

    function renderCartCount() {
        const count = [...state.cart.values()].reduce((sum, quantity) => sum + quantity, 0);
        $("#cart-count").textContent = count;
        $("#cart-title-count").textContent = count;
    }

    function renderCart() {
        const rows = cartRows();
        $("#cart-items").innerHTML = rows.length
            ? rows.map(({ product, quantity }) => `
                <div class="cart-item">
                    <div><strong>${escapeHtml(product.name)}</strong><small>${won(product.price)} · 재고 ${product.stock}포</small></div>
                    <div class="quantity">
                        <button type="button" data-cart-minus="${product.id}">−</button>
                        <span>${quantity}</span>
                        <button type="button" data-cart-plus="${product.id}">＋</button>
                    </div>
                    <button class="remove" type="button" data-cart-remove="${product.id}" aria-label="삭제">×</button>
                </div>`).join("")
            : `<div class="empty-state">장바구니가 비어 있습니다.</div>`;
        const amounts = cartAmounts();
        $("#cart-summary").innerHTML = `
            <div class="summary-line"><span>상품 금액</span><strong>${won(amounts.productAmount)}</strong></div>
            <div class="summary-line"><span>배송비</span><strong>${amounts.deliveryFee ? won(amounts.deliveryFee) : "무료"}</strong></div>
            <div class="summary-line total"><span>결제 예정금액</span><strong>${won(amounts.total)}</strong></div>`;
        $("#start-checkout").disabled = rows.length === 0;
        renderCartCount();
    }

    function changeCart(id, amount) {
        const product = productById(id);
        const next = Math.min(product?.stock || 0, Math.max(0, (state.cart.get(Number(id)) || 0) + amount));
        if (next) state.cart.set(Number(id), next);
        else state.cart.delete(Number(id));
        renderCart();
    }

    function saveFavorites() {
        window.localStorage.setItem("feedflow-favorites", JSON.stringify([...state.favorites]));
    }

    function restoreBrowserState() {
        try {
            const favorites = JSON.parse(window.localStorage.getItem("feedflow-favorites") || "[]");
            state.favorites = new Set(favorites.map(Number).filter(Number.isFinite));
        } catch {
            state.favorites = new Set();
        }

        try {
            state.member = JSON.parse(window.sessionStorage.getItem("feedflow-member") || "null");
        } catch {
            state.member = null;
        }
        updateMemberUi();
    }

    function updateMemberUi() {
        const loggedIn = Boolean(state.member);
        $("#mypage-tab").hidden = !loggedIn;
        $$('[data-account-tab="login"], [data-account-tab="signup"]')
            .forEach((button) => { button.hidden = loggedIn; });
        $("#myfarm-label").textContent = loggedIn ? state.member.farmName || state.member.name : "마이팜";
    }

    function statusLabel(status) {
        if (status === "PAID") return "결제완료";
        if (status === "CANCELLED") return "취소완료";
        return status || "-";
    }

    function renderMyPage() {
        if (!state.member) return;

        const member = state.member;
        $("#mypage-profile").innerHTML = `
            <div class="mypage-welcome">
                <span>MY FARM</span>
                <h3>${escapeHtml(member.farmName || member.name)} 농장</h3>
                <p>${escapeHtml(member.name)}님, 반갑습니다.</p>
            </div>
            <dl class="mypage-profile-grid">
                <div><dt>이메일</dt><dd>${escapeHtml(member.email)}</dd></div>
                <div><dt>연락처</dt><dd>${escapeHtml(member.phone)}</dd></div>
                <div><dt>정기배송일</dt><dd>${member.regularDeliveryDay ? `매월 ${member.regularDeliveryDay}일` : "미지정"}</dd></div>
                <div><dt>사업자번호</dt><dd>${escapeHtml(member.businessNumber || "미등록")}</dd></div>
            </dl>`;

        const favorites = state.products.filter((product) => state.favorites.has(product.id));
        $("#mypage-favorites").innerHTML = favorites.length
            ? favorites.map((product) => `
                <button type="button" data-detail="${product.id}">
                    <span>${escapeHtml(product.name)}</span>
                    <strong>${won(product.price)}</strong>
                </button>`).join("")
            : `<p>관심상품이 아직 없습니다. 상품의 ♡ 버튼을 눌러 등록해보세요.</p>`;

        $("#mypage-order").innerHTML = state.lookupOrder
            ? `<div><span>${escapeHtml(state.lookupOrder.orderNumber)}</span><strong>${escapeHtml(statusLabel(state.lookupOrder.status))}</strong><b>${won(state.lookupOrder.totalAmount)}</b></div>`
            : `<p>현재 브라우저에서 확인한 주문이 없습니다.</p>`;
    }

    function showAccount(tab = "login") {
        openModal("account-modal");
        switchAccountTab(tab);
    }

    function switchAccountTab(tab) {
        if (state.member && (tab === "login" || tab === "signup")) {
            tab = "mypage";
        }
        if (tab === "mypage" && !state.member) {
            tab = "login";
        }
        $$("[data-account-tab]").forEach((button) => button.classList.toggle("active", button.dataset.accountTab === tab));
        $("#mypage-panel").hidden = tab !== "mypage";
        $("#login-form").hidden = tab !== "login";
        $("#signup-form").hidden = tab !== "signup";
        $("#lookup-form").hidden = tab !== "lookup";
        $("#account-title").textContent =
            tab === "mypage" ? "마이페이지"
            : tab === "signup" ? "농장 회원가입"
            : tab === "lookup" ? (state.member ? "주문 조회" : "비회원 주문 조회")
            : "농장 계정 로그인";
        if (tab === "mypage") renderMyPage();
    }

    function renderLookup(order) {
        state.lookupOrder = order;
        const result = $("#lookup-result");
        result.hidden = false;
        result.innerHTML = `
            <div><span>주문번호</span><strong>${escapeHtml(order.orderNumber)}</strong></div>
            <div><span>주문상태</span><strong>${escapeHtml(statusLabel(order.status))}</strong></div>
            <div><span>결제금액</span><strong>${won(order.totalAmount)}</strong></div>
            ${order.status === "PAID" ? `<button class="danger-button" type="button" data-cancel-order>주문 취소</button>` : ""}`;
        renderMyPage();
    }

    function renderCheckoutSummary() {
        const regular = $("#regular-delivery").checked;
        const amounts = cartAmounts(regular);
        $("#checkout-summary").innerHTML = `
            <h3>주문 요약</h3>
            ${cartRows().map(({ product, quantity }) => `<div class="summary-line"><span>${escapeHtml(product.name)} × ${quantity}</span><strong>${won(product.price * quantity)}</strong></div>`).join("")}
            <div class="summary-line"><span>상품 금액</span><strong>${won(amounts.productAmount)}</strong></div>
            <div class="summary-line"><span>배송비</span><strong>${amounts.deliveryFee ? won(amounts.deliveryFee) : "무료"}</strong></div>
            ${regular ? `<div class="summary-line"><span>정기배송 할인</span><strong>-${won(amounts.discount)}</strong></div>` : ""}
            <div class="summary-line total"><span>총 결제금액</span><strong>${won(amounts.total)}</strong></div>
            <button class="primary-button" type="submit">결제하기</button>`;
    }

    document.addEventListener("click", (event) => {
        const button = event.target.closest("button, [role='button']");
        if (!button) return;

        if (button.matches("[data-close-modal]")) closeModal();
        if (button.dataset.category) {
            state.category = button.dataset.category;
            $$("[data-category]").forEach((item) => item.classList.toggle("active", item === button));
            renderProducts();
            $("#products").scrollIntoView({ behavior: "smooth" });
        }
        if (button.dataset.addCart) addToCart(button.dataset.addCart);
        if (button.dataset.modalAdd) { addToCart(button.dataset.modalAdd); closeModal(); }
        if (button.dataset.quickBuy) {
            state.cart.clear();
            addToCart(button.dataset.quickBuy);
            openModal("checkout-modal");
            renderCheckoutSummary();
        }
        if (button.dataset.detail) {
            const product = productById(button.dataset.detail);
            if (product) showProduct(product);
        }
        if (button.dataset.favorite) {
            const id = Number(button.dataset.favorite);
            state.favorites.has(id) ? state.favorites.delete(id) : state.favorites.add(id);
            saveFavorites();
            renderProducts();
            renderMyPage();
            showToast(state.favorites.has(id) ? "관심상품에 등록했습니다." : "관심상품에서 해제했습니다.");
        }
        if (button.dataset.openAccount) showAccount(button.dataset.openAccount);
        if (button.dataset.accountTab) switchAccountTab(button.dataset.accountTab);
        if (button.dataset.cartMinus) changeCart(button.dataset.cartMinus, -1);
        if (button.dataset.cartPlus) changeCart(button.dataset.cartPlus, 1);
        if (button.dataset.cartRemove) { state.cart.delete(Number(button.dataset.cartRemove)); renderCart(); }
        if (button.hasAttribute("data-scroll-consulting")) $("#consulting").scrollIntoView({ behavior: "smooth" });
        if (button.hasAttribute("data-consult")) showToast("상담 신청이 접수되었습니다. 평일 중 연락드리겠습니다.");
        if (button.dataset.footerMessage) showToast(button.dataset.footerMessage);
        if (button.hasAttribute("data-view-order")) {
            showAccount("lookup");
            $("#lookup-number").value = state.lookupOrder?.orderNumber || "";
            $("#lookup-phone").value = state.lastOrderPhone;
        }
        if (button.hasAttribute("data-mypage-lookup")) switchAccountTab("lookup");
        if (button.hasAttribute("data-logout")) {
            state.member = null;
            window.sessionStorage.removeItem("feedflow-member");
            updateMemberUi();
            switchAccountTab("login");
            showToast("로그아웃되었습니다.");
        }
        if (button.hasAttribute("data-cancel-order")) cancelOrder();
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") closeModal();
        if (event.key === "Enter" && event.target.matches("[data-detail]")) {
            const product = productById(event.target.dataset.detail);
            if (product) showProduct(product);
        }
    });

    $("#search-input").addEventListener("input", (event) => {
        state.query = event.target.value;
        renderProducts();
    });
    $("#sort-select").addEventListener("change", (event) => {
        state.sort = event.target.value;
        renderProducts();
    });
    $("#mobile-menu").addEventListener("click", () => $("#category-nav").classList.toggle("open"));
    $("#open-cart").addEventListener("click", () => { renderCart(); openModal("cart-modal"); });
    $("#start-checkout").addEventListener("click", () => { openModal("checkout-modal"); renderCheckoutSummary(); });
    $("#regular-delivery").addEventListener("change", renderCheckoutSummary);
    $("#modal-backdrop").addEventListener("mousedown", (event) => {
        if (event.target === event.currentTarget) closeModal();
    });

    $("#login-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            const member = await api("/api/members/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email: $("#login-email").value, password: $("#login-password").value })
            });
            state.member = member;
            window.sessionStorage.setItem("feedflow-member", JSON.stringify(member));
            updateMemberUi();
            $("#order-name").value = member.name;
            $("#order-phone").value = member.phone;
            showToast(`${member.farmName} 계정으로 로그인했습니다.`);
            switchAccountTab("mypage");
        } catch (error) { showToast(error.message); }
    });

    $("#signup-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const name = $("#signup-name").value;
        const phone = $("#signup-phone").value;
        try {
            const member = await api("/api/members/signup", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    email: $("#signup-email").value,
                    password: $("#signup-password").value,
                    name,
                    farmName: $("#signup-farm-name").value,
                    phone,
                    businessNumber: $("#signup-business").value || null,
                    regularDeliveryDay: Number($("#signup-day").value),
                    homeAddress: {
                        addressType: "HOME", recipientName: name, phone, postalCode: "",
                        baseAddress: $("#signup-home-address").value,
                        detailAddress: $("#signup-home-detail").value,
                        unloadingLocation: "", defaultAddress: true
                    },
                    farmAddress: {
                        addressType: "FARM", recipientName: name, phone, postalCode: "",
                        baseAddress: $("#signup-farm-address").value,
                        detailAddress: "", unloadingLocation: $("#signup-unloading").value,
                        defaultAddress: false
                    }
                })
            });
            $("#login-email").value = member.email;
            $("#login-password").value = "";
            switchAccountTab("login");
            showToast("회원가입이 완료되었습니다. 로그인해주세요.");
        } catch (error) { showToast(error.message); }
    });

    $("#lookup-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            const orderNumber = $("#lookup-number").value.trim();
            const phone = $("#lookup-phone").value.trim();
            state.lastOrderPhone = phone;
            renderLookup(await api(`/api/orders/${encodeURIComponent(orderNumber)}?phone=${encodeURIComponent(phone)}`));
        } catch (error) {
            $("#lookup-result").hidden = true;
            showToast(error.message);
        }
    });

    async function cancelOrder() {
        if (!state.lookupOrder) return;
        try {
            const order = await api(`/api/orders/${encodeURIComponent(state.lookupOrder.orderNumber)}/cancel`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ phone: $("#lookup-phone").value.trim() })
            });
            renderLookup(order);
            await loadProducts();
            showToast("주문이 취소되고 LOT 재고가 복원되었습니다.");
        } catch (error) { showToast(error.message); }
    }

    $("#checkout-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!cartRows().length) return;
        const phone = $("#order-phone").value.trim();
        try {
            const order = await api("/api/orders", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    customerName: $("#order-name").value.trim(),
                    phone,
                    address: $("#order-address").value.trim(),
                    detailAddress: $("#order-detail").value.trim(),
                    unloadingLocation: $("#order-unloading").value.trim(),
                    deliveryRequest: $("#order-request").value,
                    paymentMethod: $("input[name='payment']:checked").value,
                    regularDelivery: $("#regular-delivery").checked,
                    items: cartRows().map(({ product, quantity }) => ({ productId: product.id, quantity }))
                })
            });
            state.lastOrderPhone = phone;
            state.lookupOrder = order;
            $("#lookup-number").value = order.orderNumber;
            $("#lookup-phone").value = phone;
            $("#success-order-number").textContent = `주문번호 ${order.orderNumber}`;
            state.cart.clear();
            renderCartCount();
            await loadProducts();
            openModal("success-modal");
        } catch (error) { showToast(error.message); }
    });

    restoreBrowserState();
    loadProducts().then(() => renderMyPage());
})();

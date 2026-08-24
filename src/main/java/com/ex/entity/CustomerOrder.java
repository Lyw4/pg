package com.ex.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "customer_order",
		indexes = {
				@Index(
				name = "idx_customer_order_number",
				columnList = "order_number",
				unique = true),
				@Index(
				name = "idx_customer_order_provider_tx",
				columnList = "provider_transaction_id",
				unique = true),
				@Index(
				name = "idx_customer_order_status",
				columnList = "status"),
				@Index(
				name = "idx_customer_order_farm_schedule",
				columnList = "farm_customer_id,scheduled_delivery_date",
				unique = true)
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

	/** PaymentApplyService가 가상계좌 입금기한을 저장할 때 쓰는 형식입니다. */
	private static final DateTimeFormatter VBANK_DUE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	public enum OrderStatus { PAYMENT_PENDING, PAID, PREPARING, SHIPPING, DELIVERED, CANCELLED }
	public enum OrderChannel { SHOP, FARM, ADMIN, WMS }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long orderId;

	@Version
	private Long version;

	@Column(name = "order_number", unique = true, length = 40)
	private String orderNumber;

	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "order_channel", length = 20)
	private OrderChannel orderChannel;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", length = 30)
	private PaymentMethod paymentMethod;

	private String customerName;
	private String phone;
	private BigDecimal productAmount;
	private BigDecimal deliveryFee;
	private BigDecimal totalPrice;
	private BigDecimal discountPrice;
	private BigDecimal finalPrice;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_provider", length = 20)
	private PaymentProvider paymentProvider;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", length = 30)
	private PaymentStatus paymentStatus;

	@Column(name = "provider_transaction_id", length = 200)
	private String providerTransactionId;
	@Column(name = "payment_callback_token", length = 36)
	private String paymentCallbackToken;
	@Column(name = "payment_receipt_url", length = 500)
	private String paymentReceiptUrl;
	@Column(name = "virtual_account_bank", length = 40)
	private String virtualAccountBank;
	@Column(name = "virtual_account_number", length = 80)
	private String virtualAccountNumber;
	@Column(name = "virtual_account_due_date", length = 40)
	private String virtualAccountDueDate;
	@Column(name = "payment_approved_at")
	private LocalDateTime paymentApprovedAt;
	private Boolean regularDelivery = false;
	private Boolean inventoryCommitted = false;

	@Enumerated(EnumType.STRING)
	private OrderStatus status;
	private String recipientName;
	private String recipientPhone;
	private String shippingAddress;
	private String postalCode;
	private String roadAddress;
	private String jibunAddress;
	private String detailAddress;
	private String unloadingLocation;
	private Double latitude;
	private Double longitude;
	private String deliveryRequest;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fulfillment_warehouse_id")
	private Warehouse fulfillmentWarehouse;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "farm_customer_id")
	private FarmCustomer farmCustomer;

	@Column(name = "scheduled_delivery_date")
	private LocalDate scheduledDeliveryDate;

	@Column(name = "automation_trigger", length = 20)
	private String automationTrigger;

	private Double fulfillmentDistanceKm;
	private String fulfillmentAssignmentBasis;

	@OneToMany(
			mappedBy = "order",
			cascade = CascadeType.ALL,
			orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDateTime cancelledAt;
	private String cancellationReason;
	private String cancellationManager;
	@Enumerated(EnumType.STRING)
	@Column(name = "cancellation_previous_status", length = 30)
	private OrderStatus cancellationPreviousStatus;
	@Enumerated(EnumType.STRING)
	@Column(name = "cancellation_previous_payment_status", length = 30)
	private PaymentStatus cancellationPreviousPaymentStatus;

	public CustomerOrder(Long userId, BigDecimal totalPrice, BigDecimal discountPrice, String shippingAddress) {
		this.userId = userId;
		this.totalPrice = totalPrice;
		this.productAmount = totalPrice;
		this.deliveryFee = BigDecimal.ZERO;
		this.discountPrice = discountPrice;
		this.finalPrice = totalPrice.subtract(discountPrice);
		this.shippingAddress = shippingAddress;
		this.status = OrderStatus.PAID;
		this.orderChannel = userId != null && userId == 0L
				? OrderChannel.ADMIN
				: OrderChannel.FARM;
	}

	public static CustomerOrder storefront(
			String orderNumber,
			String customerName,
			String phone,
			String address,
			String detailAddress,
			String unloadingLocation,
			String deliveryRequest,
			PaymentMethod paymentMethod,
			boolean regularDelivery,
			BigDecimal productAmount,
			BigDecimal deliveryFee,
			BigDecimal discountAmount) {
		CustomerOrder order = new CustomerOrder(
				0L,
				productAmount.add(deliveryFee),
				discountAmount,
				address);
		order.orderNumber = orderNumber;
		order.customerName = customerName;
		order.phone = phone;
		order.recipientName = customerName;
		order.recipientPhone = phone;
		order.detailAddress = detailAddress;
		order.unloadingLocation = unloadingLocation;
		order.deliveryRequest = deliveryRequest;
		order.paymentMethod = paymentMethod;
		order.regularDelivery = regularDelivery;
		order.productAmount = productAmount;
		order.deliveryFee = deliveryFee;
		order.totalPrice = productAmount.add(deliveryFee);
		order.discountPrice = discountAmount;
		order.finalPrice = order.totalPrice.subtract(discountAmount);
		order.orderChannel = OrderChannel.SHOP;
		// 판매 홈페이지 주문은 항상 결제 전 상태로 생성합니다. 공용 생성자가
		// 관리자·농장 주문 기준으로 PAID를 넣기 때문에 여기서 되돌리지 않으면
		// 전자결제 설정이 없는 환경에서 @PrePersist가 결제상태를 DONE으로
		// 채워 결제 없이 완료된 주문이 만들어집니다.
		order.status = OrderStatus.PAYMENT_PENDING;
		return order;
	}

	public void changeStatus(OrderStatus status) {
		this.status = status;
	}

	public void prepareExternalPayment() {
		paymentProvider = PaymentProvider.PORTONE;
		paymentStatus = PaymentStatus.READY;
		status = OrderStatus.PAYMENT_PENDING;
		if (paymentCallbackToken == null) {
			paymentCallbackToken = UUID.randomUUID().toString();
		}
	}

	public void completePayment(String transactionId, String receiptUrl) {
		providerTransactionId = transactionId;
		paymentReceiptUrl = receiptUrl;
		paymentStatus = PaymentStatus.DONE;
		paymentApprovedAt = LocalDateTime.now();
		status = OrderStatus.PAID;
	}

	public void waitForDeposit(
			String transactionId,
			String bank,
			String accountNumber,
			String dueDate) {
		providerTransactionId = transactionId;
		virtualAccountBank = bank;
		virtualAccountNumber = accountNumber;
		virtualAccountDueDate = dueDate;
		paymentStatus = PaymentStatus.WAITING_FOR_DEPOSIT;
		status = OrderStatus.PAYMENT_PENDING;
	}

	/**
	 * 가상계좌 입금기한이 지났는지 알려줍니다.
	 *
	 * 자동 취소는 하지 않습니다. 가상계좌 취소는 포트원에 대한 비가역
	 * 외부 호출이라 스케줄러가 임의로 실행하면 위험합니다. 관리자
	 * 입금 대기 목록에 경고만 띄워 담당자가 판단하도록 합니다.
	 *
	 * 기한 값이 없거나 형식이 다르면 판단하지 않습니다. 형식은
	 * PaymentApplyService가 저장할 때 쓰는 "yyyy-MM-dd HH:mm"입니다.
	 */
	public boolean isDepositOverdue() {
		if (paymentStatus != PaymentStatus.WAITING_FOR_DEPOSIT
				|| virtualAccountDueDate == null
				|| virtualAccountDueDate.isBlank()) {
			return false;
		}
		try {
			return LocalDateTime
					.parse(virtualAccountDueDate.trim(), VBANK_DUE_FORMAT)
					.isBefore(LocalDateTime.now());
		} catch (DateTimeParseException exception) {
			return false;
		}
	}

	public void failPayment() {
		paymentStatus = PaymentStatus.FAILED;
	}

	public void cancelPayment() {
		paymentStatus = PaymentStatus.CANCELLED;
		cancellationPreviousStatus = null;
		cancellationPreviousPaymentStatus = null;
	}

	public void beginPaymentCancellation(String manager) {
		if (status == OrderStatus.SHIPPING || status == OrderStatus.DELIVERED) {
			throw new IllegalStateException("배송이 시작된 주문은 취소할 수 없습니다.");
		}
		if (paymentStatus == PaymentStatus.CANCEL_REQUESTED) {
			return;
		}
		cancellationPreviousStatus = status;
		cancellationPreviousPaymentStatus = paymentStatus;
		status = OrderStatus.CANCELLED;
		paymentStatus = PaymentStatus.CANCEL_REQUESTED;
		cancellationReason = "결제 취소 처리 중";
		cancellationManager = manager;
		cancelledAt = LocalDateTime.now();
	}

	public void abortPaymentCancellation() {
		if (paymentStatus != PaymentStatus.CANCEL_REQUESTED) return;
		status = cancellationPreviousStatus == null
				? OrderStatus.PAID : cancellationPreviousStatus;
		paymentStatus = cancellationPreviousPaymentStatus == null
				? PaymentStatus.DONE : cancellationPreviousPaymentStatus;
		cancellationPreviousStatus = null;
		cancellationPreviousPaymentStatus = null;
		cancellationReason = null;
		cancellationManager = null;
		cancelledAt = null;
	}

	public void finalizeStagedCancellation(String reason, String manager) {
		if (status != OrderStatus.CANCELLED
				|| paymentStatus != PaymentStatus.CANCEL_REQUESTED) {
			throw new IllegalStateException("결제 취소 요청 상태가 아닙니다.");
		}
		if (reason == null || reason.isBlank()
				|| manager == null || manager.isBlank()) {
			throw new IllegalArgumentException("취소 사유와 담당자를 입력해 주세요.");
		}
		cancelPayment();
		cancellationReason = reason.trim();
		cancellationManager = manager.trim();
		cancelledAt = LocalDateTime.now();
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.assignOrder(this);
	}

	public void assignMember(Member member) {
		this.member = member;
		this.userId = member == null ? 0L : member.getId();
	}

	public void markInventoryCommitted() {
		inventoryCommitted = true;
	}

	public void releaseInventoryCommit() {
		inventoryCommitted = false;
	}

	public boolean isRegularDelivery() {
		return Boolean.TRUE.equals(regularDelivery);
	}

	public boolean isInventoryCommitted() {
		return Boolean.TRUE.equals(inventoryCommitted);
	}

	public void cancelByCustomer(String requestPhone) {
		if (phone == null || !phone.equals(requestPhone)) {
			throw new IllegalArgumentException(
					"주문자의 전화번호가 일치하지 않습니다.");
		}
		if (status == OrderStatus.SHIPPING
				|| status == OrderStatus.DELIVERED) {
			throw new IllegalStateException(
					"배송이 시작된 주문은 고객이 직접 취소할 수 없습니다.");
		}
		cancel("고객 요청", customerName == null ? "고객" : customerName);
	}

	public void cancel(String reason, String manager) {
		if (status == OrderStatus.DELIVERED) {
			throw new IllegalStateException(
					"배송 완료 주문은 취소할 수 없습니다. 반품으로 처리해 주세요.");
		}
		if (status == OrderStatus.CANCELLED) {
			throw new IllegalStateException("이미 취소된 주문입니다.");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("주문 취소 사유를 입력해 주세요.");
		}
		if (manager == null || manager.isBlank()) {
			throw new IllegalArgumentException("취소 담당자를 입력해 주세요.");
		}
		status = OrderStatus.CANCELLED;
		cancellationReason = reason.trim();
		cancellationManager = manager.trim();
		cancelledAt = LocalDateTime.now();
	}

	public void configureRecipient(
			String recipientName,
			String recipientPhone,
			String deliveryRequest) {
		if (recipientName == null || recipientName.isBlank()) {
			throw new IllegalArgumentException("수령인 이름을 입력해 주세요.");
		}
		if (recipientPhone == null || recipientPhone.isBlank()) {
			throw new IllegalArgumentException("수령인 연락처를 입력해 주세요.");
		}
		this.recipientName = recipientName.trim();
		this.recipientPhone = recipientPhone.trim();
		if (customerName == null) {
			customerName = this.recipientName;
		}
		if (phone == null) {
			phone = this.recipientPhone;
		}
		this.deliveryRequest = deliveryRequest == null
				? null
				: deliveryRequest.trim();
	}

	public void configureShippingAddress(
			String postalCode,
			String roadAddress,
			String jibunAddress,
			String detailAddress,
			Double latitude,
			Double longitude) {
		if (postalCode == null || postalCode.isBlank()) {
			throw new IllegalArgumentException("주소 검색을 통해 우편번호를 선택해 주세요.");
		}
		String baseAddress = roadAddress != null && !roadAddress.isBlank()
				? roadAddress.trim()
				: jibunAddress == null ? "" : jibunAddress.trim();
		if (baseAddress.isBlank()) {
			throw new IllegalArgumentException("주소 검색을 통해 기본주소를 선택해 주세요.");
		}
		this.postalCode = postalCode.trim();
		this.roadAddress = roadAddress == null ? null : roadAddress.trim();
		this.jibunAddress = jibunAddress == null ? null : jibunAddress.trim();
		this.detailAddress = detailAddress == null ? null : detailAddress.trim();
		this.latitude = latitude;
		this.longitude = longitude;
		this.shippingAddress = "[" + this.postalCode + "] " + baseAddress
				+ (this.detailAddress == null || this.detailAddress.isBlank()
						? ""
						: " " + this.detailAddress);
	}

	public void assignFulfillmentWarehouse(
			Warehouse warehouse,
			Double distanceKm,
			String assignmentBasis) {
		if (warehouse == null || !warehouse.isActive()) {
			throw new IllegalArgumentException(
					"운영 중인 출고 창고를 지정해 주세요.");
		}
		this.fulfillmentWarehouse = warehouse;
		this.fulfillmentDistanceKm = distanceKm;
		this.fulfillmentAssignmentBasis = assignmentBasis;
	}

	public void linkFarmCustomer(FarmCustomer farmCustomer) {
		if (farmCustomer == null
				|| farmCustomer.getStatus()
						!= FarmCustomer.CustomerStatus.ACTIVE) {
			throw new IllegalArgumentException(
					"거래 중인 농장 고객사만 주문에 연결할 수 있습니다.");
		}
		this.farmCustomer = farmCustomer;
	}

	public void markScheduledDelivery(
			LocalDate scheduledDeliveryDate,
			String automationTrigger) {
		if (farmCustomer == null || scheduledDeliveryDate == null) {
			throw new IllegalStateException("농장과 정기 납품 기준일이 필요합니다.");
		}
		this.scheduledDeliveryDate = scheduledDeliveryDate;
		this.automationTrigger = automationTrigger;
		this.regularDelivery = true;
	}

	public String getFulfillmentDistanceLabel() {
		if (fulfillmentDistanceKm == null) {
			return fulfillmentAssignmentBasis == null
					? "자동 배정"
					: fulfillmentAssignmentBasis;
		}
		return "직선거리 약 %.1fkm".formatted(fulfillmentDistanceKm);
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
		if (orderNumber == null || orderNumber.isBlank()) {
			orderNumber = "LEGACY-"
					+ UUID.randomUUID().toString()
							.replace("-", "")
							.substring(0, 12)
							.toUpperCase();
		}
		if (orderChannel == null) {
			orderChannel = OrderChannel.ADMIN;
		}
		if (regularDelivery == null) {
			regularDelivery = false;
		}
		if (inventoryCommitted == null) {
			inventoryCommitted = false;
		}
		if (paymentStatus == null) {
			paymentStatus = status == OrderStatus.PAYMENT_PENDING
					? PaymentStatus.READY
					: PaymentStatus.DONE;
		}
		if (paymentCallbackToken == null) {
			paymentCallbackToken = UUID.randomUUID().toString();
		}
		if (productAmount == null) {
			productAmount = totalPrice == null
					? BigDecimal.ZERO
					: totalPrice;
		}
		if (deliveryFee == null) {
			deliveryFee = BigDecimal.ZERO;
		}
		if (discountPrice == null) {
			discountPrice = BigDecimal.ZERO;
		}
		if (totalPrice == null) {
			totalPrice = productAmount.add(deliveryFee);
		}
		if (finalPrice == null) {
			finalPrice = totalPrice.subtract(discountPrice);
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}

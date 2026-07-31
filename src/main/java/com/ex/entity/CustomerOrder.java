package com.ex.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

	public enum OrderStatus { PAID, PREPARING, SHIPPING, DELIVERED, CANCELLED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long orderId;
	private Long userId;
	private BigDecimal totalPrice;
	private BigDecimal discountPrice;
	private BigDecimal finalPrice;

	@Enumerated(EnumType.STRING)
	private OrderStatus status;
	private String recipientName;
	private String recipientPhone;
	private String shippingAddress;
	private String postalCode;
	private String roadAddress;
	private String jibunAddress;
	private String detailAddress;
	private Double latitude;
	private Double longitude;
	private String deliveryRequest;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fulfillment_warehouse_id")
	private Warehouse fulfillmentWarehouse;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "farm_customer_id")
	private FarmCustomer farmCustomer;

	private Double fulfillmentDistanceKm;
	private String fulfillmentAssignmentBasis;

	private LocalDateTime createdAt;
	private LocalDateTime cancelledAt;
	private String cancellationReason;
	private String cancellationManager;

	public CustomerOrder(Long userId, BigDecimal totalPrice, BigDecimal discountPrice, String shippingAddress) {
		this.userId = userId;
		this.totalPrice = totalPrice;
		this.discountPrice = discountPrice;
		this.finalPrice = totalPrice.subtract(discountPrice);
		this.shippingAddress = shippingAddress;
		this.status = OrderStatus.PAID;
	}

	public void changeStatus(OrderStatus status) {
		this.status = status;
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
		createdAt = LocalDateTime.now();
	}
}

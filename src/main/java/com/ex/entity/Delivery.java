package com.ex.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

	public enum DeliveryStatus {
		READY("배송 준비", 20),
		PICKED_UP("택배 인계", 45),
		IN_TRANSIT("배송 중", 75),
		DELIVERED("배송 완료", 100),
		CANCELLED("배송 취소", 0);

		private final String label;
		private final int progress;

		DeliveryStatus(String label, int progress) {
			this.label = label;
			this.progress = progress;
		}

		public String getLabel() {
			return label;
		}

		public int getProgress() {
			return progress;
		}
	}

	public enum ReturnStatus {
		REQUESTED("회수 요청"),
		COLLECTING("회수 중"),
		INSPECTING("회수 완료·검수 대기"),
		COMPLETED("회수 처리 완료");

		private final String label;

		ReturnStatus(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long deliveryId;

	@Version
	private Long version;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", unique = true)
	private CustomerOrder order;

	private String carrierName;
	private String trackingNumber;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private DeliveryStatus status;
	private LocalDateTime shippedAt;
	private LocalDateTime expectedDeliveryAt;
	private LocalDateTime deliveredAt;
	private String delayReason;
	private String cancellationReason;
	private String cancellationManager;
	private LocalDateTime cancelledAt;
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private ReturnStatus returnStatus;
	private String returnReason;
	private String returnManager;
	private String returnInspectionResult;
	private LocalDateTime returnRequestedAt;
	private LocalDateTime returnCollectedAt;
	private LocalDateTime returnCompletedAt;

	public Delivery(CustomerOrder order, String carrierName, String trackingNumber) {
		this.order = order;
		this.carrierName = carrierName;
		this.trackingNumber = trackingNumber;
		this.status = DeliveryStatus.READY;
	}

	public void update(String carrierName, String trackingNumber, DeliveryStatus status) {
		this.carrierName = carrierName;
		this.trackingNumber = trackingNumber;
		this.status = status;
		if (status != DeliveryStatus.READY && shippedAt == null) {
			shippedAt = LocalDateTime.now();
		}
		if (status != DeliveryStatus.READY && expectedDeliveryAt == null) {
			expectedDeliveryAt = shippedAt.plusDays(3);
		}
		if (status == DeliveryStatus.DELIVERED) {
			deliveredAt = LocalDateTime.now();
		} else {
			deliveredAt = null;
		}
	}

	public boolean isDelayed() {
		return status != DeliveryStatus.DELIVERED
				&& status != DeliveryStatus.CANCELLED
				&& getExpectedDeliveryAt() != null
				&& LocalDateTime.now().isAfter(getExpectedDeliveryAt());
	}

	public LocalDateTime getExpectedDeliveryAt() {
		if (expectedDeliveryAt != null) {
			return expectedDeliveryAt;
		}
		return shippedAt == null ? null : shippedAt.plusDays(3);
	}

	public void reschedule(LocalDateTime expectedAt, String reason) {
		if (status == DeliveryStatus.DELIVERED
				|| status == DeliveryStatus.CANCELLED) {
			throw new IllegalStateException("완료되거나 취소된 배송은 일정을 변경할 수 없습니다.");
		}
		if (expectedAt == null || !expectedAt.isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("변경 예정일은 현재 시각보다 뒤여야 합니다.");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("일정 변경 사유를 입력해 주세요.");
		}
		expectedDeliveryAt = expectedAt;
		delayReason = reason.trim();
	}

	public void cancel(String reason, String manager) {
		if (status == DeliveryStatus.DELIVERED) {
			throw new IllegalStateException("배송 완료 건은 취소 대신 반품으로 처리해 주세요.");
		}
		if (status == DeliveryStatus.CANCELLED) {
			throw new IllegalStateException("이미 취소된 배송입니다.");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("배송 취소 사유를 입력해 주세요.");
		}
		if (manager == null || manager.isBlank()) {
			throw new IllegalArgumentException("취소 담당자를 입력해 주세요.");
		}
		status = DeliveryStatus.CANCELLED;
		cancellationReason = reason.trim();
		cancellationManager = manager.trim();
		cancelledAt = LocalDateTime.now();
	}

	public void reactivate(String carrierName, String trackingNumber) {
		if (status != DeliveryStatus.CANCELLED) {
			throw new IllegalStateException("취소된 배송만 재배송할 수 있습니다.");
		}
		if (carrierName == null || carrierName.isBlank()
				|| trackingNumber == null || trackingNumber.isBlank()) {
			throw new IllegalArgumentException("재배송 운송사와 운송장 번호를 입력해 주세요.");
		}
		this.carrierName = carrierName.trim();
		this.trackingNumber = trackingNumber.trim();
		status = DeliveryStatus.PICKED_UP;
		shippedAt = LocalDateTime.now();
		expectedDeliveryAt = shippedAt.plusDays(3);
		deliveredAt = null;
		delayReason = null;
		cancellationReason = null;
		cancellationManager = null;
		cancelledAt = null;
	}

	public void updateTracking(String carrierName, String trackingNumber) {
		if (status == DeliveryStatus.DELIVERED
				|| status == DeliveryStatus.CANCELLED) {
			throw new IllegalStateException("완료되거나 취소된 배송의 운송장은 수정할 수 없습니다.");
		}
		if (carrierName == null || carrierName.isBlank()
				|| trackingNumber == null || trackingNumber.isBlank()) {
			throw new IllegalArgumentException("운송사와 운송장 번호를 입력해 주세요.");
		}
		this.carrierName = carrierName.trim();
		this.trackingNumber = trackingNumber.trim();
	}

	public void requestReturn(String reason, String manager) {
		if (status != DeliveryStatus.DELIVERED) {
			throw new IllegalStateException("배송 완료 건만 회수를 요청할 수 있습니다.");
		}
		if (returnStatus != null) {
			throw new IllegalStateException("이미 회수 절차가 진행 중인 배송입니다.");
		}
		requireReturnText(reason, "회수 사유를 입력해 주세요.");
		requireReturnText(manager, "회수 담당자를 입력해 주세요.");
		returnStatus = ReturnStatus.REQUESTED;
		returnReason = reason.trim();
		returnManager = manager.trim();
		returnRequestedAt = LocalDateTime.now();
	}

	public void startReturn() {
		requireReturnStatus(ReturnStatus.REQUESTED);
		returnStatus = ReturnStatus.COLLECTING;
	}

	public void receiveReturn() {
		requireReturnStatus(ReturnStatus.COLLECTING);
		returnStatus = ReturnStatus.INSPECTING;
		returnCollectedAt = LocalDateTime.now();
	}

	public void completeReturn(String inspectionResult) {
		requireReturnStatus(ReturnStatus.INSPECTING);
		requireReturnText(inspectionResult, "회수 검수 결과를 입력해 주세요.");
		returnStatus = ReturnStatus.COMPLETED;
		returnInspectionResult = inspectionResult.trim();
		returnCompletedAt = LocalDateTime.now();
	}

	private void requireReturnStatus(ReturnStatus expected) {
		if (returnStatus != expected) {
			throw new IllegalStateException(
					expected.getLabel() + " 단계에서만 처리할 수 있습니다.");
		}
	}

	private void requireReturnText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}

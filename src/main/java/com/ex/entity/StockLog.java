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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLog {

	public enum ChangeType {
		INBOUND("입고"),
		OUTBOUND("출고"),
		ADJUSTMENT("재고 조정"),
		DEFECT("불량 격리"),
		DEFECT_RECOVERY("불량 복귀"),
		INVENTORY_AUDIT("재고 실사"),
		INBOUND_CANCEL("입고 취소"),
		DISPOSAL("재고 폐기");


		private final String label;

		ChangeType(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long logId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lot_id")
	private ProductLot lot;

	private Long managerId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private ChangeType changeType;
	private int changedQty;
	private String reason;
	private LocalDateTime createdAt;
	private LocalDateTime cancelledAt;
	private String cancelReason;

	public StockLog(ProductLot lot, Long managerId, ChangeType changeType, int changedQty, String reason) {
		this.lot = lot;
		this.managerId = managerId;
		this.changeType = changeType;
		this.changedQty = changedQty;
		this.reason = reason;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public boolean isCancelled() {
		return cancelledAt != null;
	}

	public void cancel(String reason) {
		if (changeType != ChangeType.OUTBOUND) {
			throw new IllegalStateException("출고 이력만 취소할 수 있습니다.");
		}
		if (isCancelled()) {
			throw new IllegalStateException("이미 취소된 출고 내역입니다.");
		}
		cancelReason = reason;
		cancelledAt = LocalDateTime.now();
	}

	public void cancelInbound(String reason) {
		if (changeType != ChangeType.INBOUND) {
			throw new IllegalStateException("입고 이력만 취소할 수 있습니다.");
		}
		if (isCancelled()) {
			throw new IllegalStateException("이미 취소된 입고 내역입니다.");
		}
		cancelReason = reason;
		cancelledAt = LocalDateTime.now();
	}
}

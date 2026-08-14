package com.ex.entity;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_lot", uniqueConstraints = @UniqueConstraint(columnNames = "lot_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long lotId;

	@Version
	@Column(nullable = false)
	private long version;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	private String lotNo;
	private LocalDate manufacturedDate;
	private LocalDate expirationDate;
	private int lotQuantity;
	private String warehouseLocation;

	public ProductLot(Product product, String lotNo, LocalDate manufacturedDate,
			LocalDate expirationDate, int lotQuantity) {
		this.product = product;
		this.lotNo = lotNo;
		this.manufacturedDate = manufacturedDate;
		this.expirationDate = expirationDate;
		this.lotQuantity = lotQuantity;
	}

	public Long getId() {
		return lotId;
	}

	public String getLotNumber() {
		return lotNo;
	}

	public int getQuantity() {
		return lotQuantity;
	}

	public void changeQuantity(int quantity) {
		int changed = lotQuantity + quantity;
		if (changed < 0) {
			throw new IllegalStateException("LOT 재고는 0보다 작을 수 없습니다.");
		}
		lotQuantity = changed;
	}

	public void changeLotNo(String lotNo) {
		if (lotNo == null || lotNo.isBlank()) {
			throw new IllegalArgumentException("LOT 번호는 비어 있을 수 없습니다.");
		}
		this.lotNo = lotNo;
	}

	public void changeWarehouseLocation(String warehouseLocation) {
		if (warehouseLocation == null || warehouseLocation.isBlank()) {
			throw new IllegalArgumentException("창고 위치를 입력해 주세요.");
		}
		String normalized = warehouseLocation.trim().toUpperCase();
		if (normalized.length() > 50) {
			throw new IllegalArgumentException("창고 위치는 50자 이내로 입력해 주세요.");
		}
		this.warehouseLocation = normalized;
	}

	public void decrease(int amount) {
		if (amount <= 0 || lotQuantity < amount) {
			throw new IllegalArgumentException("LOT 재고가 부족합니다.");
		}
		changeQuantity(-amount);
	}

	public void increase(int amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException(
					"복원 수량은 1개 이상이어야 합니다.");
		}
		changeQuantity(amount);
	}

	public void updateDetails(
			String lotNo,
			LocalDate manufacturedDate,
			LocalDate expirationDate,
			int quantity) {
		if (quantity < 0) {
			throw new IllegalArgumentException(
					"LOT 재고는 0보다 작을 수 없습니다.");
		}
		changeLotNo(lotNo);
		this.manufacturedDate = manufacturedDate;
		this.expirationDate = expirationDate;
		this.lotQuantity = quantity;
	}
}

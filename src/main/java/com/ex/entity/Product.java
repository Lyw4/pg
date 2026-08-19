package com.ex.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long productId;

    @Version
    private Long version;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "manufacturer_id")
	private Manufacturer manufacturer;

	private String name;
	private String animalType;
	private BigDecimal weightKg;
	private BigDecimal price;
	private int totalStock;
	private int safetyStock;
	private Integer shelfLifeMonths;
	private String imageUrl;
	private String description;
	private Boolean active = true;

	/*
	 * 구매 홈페이지에서 사용하는 선택 정보입니다.
	 * 기존 재고·유통 모델을 기준으로 유지하면서 고객용 상품 카드 정보만
	 * 같은 상품에 선택적으로 저장합니다.
	 */
	@Column(length = 50)
	private String feedStage;
	private Integer originalPrice;
	private BigDecimal proteinPercent;
	private BigDecimal fatPercent;
	private BigDecimal fiberPercent;
	private BigDecimal calciumPercent;
	@Column(length = 30)
	private String badge;
	@Column(length = 30)
	private String displayTone;
	@Column(length = 30)
	private String displayShape;

	@OneToMany(mappedBy = "product")
	@OrderBy("expirationDate ASC")
	private List<ProductLot> lots = new ArrayList<>();

	public Product(Manufacturer manufacturer, String name, String animalType, BigDecimal weightKg,
			BigDecimal price, int safetyStock, int shelfLifeMonths, String description) {
		this.manufacturer = manufacturer;
		this.name = name;
		this.animalType = animalType;
		this.weightKg = weightKg;
		this.price = price;
		this.safetyStock = safetyStock;
		this.shelfLifeMonths = shelfLifeMonths;
		this.description = description;
	}

	public Long getId() {
		return productId;
	}

	public int getEffectiveShelfLifeMonths() {
		if (shelfLifeMonths != null && shelfLifeMonths > 0) {
			return shelfLifeMonths;
		}
		return "영양제".equals(animalType) ? 24 : 6;
	}

	public void configureShelfLife(int months) {
		if (months <= 0) {
			throw new IllegalArgumentException("유통기한 개월 수는 1개월 이상이어야 합니다.");
		}
		shelfLifeMonths = months;
	}

	public void changeStock(int quantity) {
		int changed = totalStock + quantity;
		if (changed < 0) {
			throw new IllegalStateException("상품 재고는 0보다 작을 수 없습니다.");
		}
		totalStock = changed;
	}

	public boolean isLowStock() {
		return totalStock <= safetyStock;
	}

	/*
	 * 주문·LOT·재고 이력을 보존하기 위해 실제 행을 지우는 대신
	 * 운영 목록에서 제외하는 방식으로 상품을 삭제합니다.
	 * 기존 DB 데이터는 active 값이 null일 수 있으므로 활성 상품으로 처리합니다.
	 */
	public boolean isActive() {
		return active == null || active;
	}

	public void deactivate() {
		if (!isActive()) {
			throw new IllegalStateException("이미 삭제된 상품입니다.");
		}
		active = false;
	}

	public void activate() {
		active = true;
	}

	public void addLot(ProductLot lot) {
		if (lot != null && !lots.contains(lot)) {
			lots.add(lot);
		}
	}

	public void updateForStorefront(
			Manufacturer manufacturer,
			String name,
			String animalType,
			String feedStage,
			String description,
			BigDecimal weightKg,
			BigDecimal price,
			Integer originalPrice,
			BigDecimal proteinPercent,
			BigDecimal fatPercent,
			BigDecimal fiberPercent,
			BigDecimal calciumPercent,
			String imageUrl,
			String badge,
			String displayTone,
			String displayShape) {
		this.manufacturer = manufacturer;
		this.name = name;
		this.animalType = animalType;
		this.feedStage = feedStage;
		this.description = description;
		this.weightKg = weightKg;
		this.price = price;
		this.originalPrice = originalPrice;
		this.proteinPercent = proteinPercent;
		this.fatPercent = fatPercent;
		this.fiberPercent = fiberPercent;
		this.calciumPercent = calciumPercent;
		this.imageUrl = imageUrl;
		this.badge = badge;
		this.displayTone = displayTone;
		this.displayShape = displayShape;
		this.active = true;
	}
}

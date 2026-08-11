package com.ex.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long orderItemId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "product_name")
	private String productName;
	private int quantity;
	private BigDecimal orderPrice;
	private BigDecimal lineAmount;

	@OneToMany(
			mappedBy = "orderItem",
			cascade = CascadeType.ALL,
			orphanRemoval = true)
	private List<OrderLotAllocation> lotAllocations = new ArrayList<>();

	public OrderItem(CustomerOrder order, Product product, ProductLot lot, int quantity, BigDecimal orderPrice) {
		this(order, product, quantity, orderPrice);
		addLotAllocation(new OrderLotAllocation(lot, quantity));
	}

	public OrderItem(
			CustomerOrder order,
			Product product,
			int quantity,
			BigDecimal orderPrice) {
		this.order = order;
		this.product = product;
		this.productName = product.getName();
		this.quantity = quantity;
		this.orderPrice = orderPrice;
		this.lineAmount = orderPrice.multiply(
				BigDecimal.valueOf(quantity));
	}

	void assignOrder(CustomerOrder order) {
		this.order = order;
	}

	public void addLotAllocation(OrderLotAllocation allocation) {
		lotAllocations.add(allocation);
		allocation.assignOrderItem(this);
	}

	public ProductLot getLot() {
		return lotAllocations.isEmpty()
				? null
				: lotAllocations.getFirst().getProductLot();
	}
}

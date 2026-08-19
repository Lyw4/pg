package com.ex.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
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
@Table(name = "recurring_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringDelivery {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long recurringDeliveryId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "manufacturer_id")
	private Manufacturer manufacturer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "warehouse_id")
	private Warehouse warehouse;

	private int quantity;
	private int deliveryDay;
	private Integer deliverySequence;
	private LocalDate nextDeliveryDate;
	private LocalDate lastReceivedDate;
	private boolean active;
	private boolean safetyStockBased;
	private String notes;
	private LocalDateTime createdAt;

	/*
	 * 창고 도입 이전 초기화 코드와 기존 DB 마이그레이션을 위한
	 * 호환 생성자입니다. 새 일정은 창고를 받는 생성자를 사용합니다.
	 */
	public RecurringDelivery(
			Manufacturer manufacturer,
			Product product,
			int quantity,
			int deliveryDay,
			LocalDate nextDeliveryDate,
			String notes) {
		this(
				null,
				manufacturer,
				product,
				quantity,
				deliveryDay,
				0,
				nextDeliveryDate,
				false,
				notes);
	}

	public RecurringDelivery(
			Manufacturer manufacturer,
			Product product,
			int quantity,
			int deliveryDay,
			LocalDate nextDeliveryDate,
			boolean safetyStockBased,
			String notes) {
		this(
				null,
				manufacturer,
				product,
				quantity,
				deliveryDay,
				0,
				nextDeliveryDate,
				safetyStockBased,
				notes);
	}

	public RecurringDelivery(
			Warehouse warehouse,
			Manufacturer manufacturer,
			Product product,
			int quantity,
			int deliveryDay,
			int deliverySequence,
			LocalDate nextDeliveryDate,
			String notes) {

		this(
				warehouse,
				manufacturer,
				product,
				quantity,
				deliveryDay,
				deliverySequence,
				nextDeliveryDate,
				false,
				notes);
	}

	public RecurringDelivery(
			Warehouse warehouse,
			Manufacturer manufacturer,
			Product product,
			int quantity,
			int deliveryDay,
			int deliverySequence,
			LocalDate nextDeliveryDate,
			boolean safetyStockBased,
			String notes) {

		this.warehouse = warehouse;
		this.manufacturer = manufacturer;
		this.product = product;
		this.quantity = quantity;
		this.deliveryDay = deliveryDay;
		this.deliverySequence = deliverySequence;
		this.nextDeliveryDate = nextDeliveryDate;
		this.safetyStockBased = safetyStockBased;
		this.notes = notes;
		this.active = true;
	}

	public String getDeliverySequenceLabel() {
		return getDeliverySequence() > 0
				? getDeliverySequence() + "차"
				: "추가";
	}

	public int getDeliverySequence() {
		return deliverySequence == null ? 0 : deliverySequence;
	}

	public void recordReceipt(LocalDate receivedDate) {
		this.lastReceivedDate = receivedDate;
		advanceSchedule(receivedDate);
	}

	public void recordReview(LocalDate reviewedDate) {
		advanceSchedule(reviewedDate);
	}

	private void advanceSchedule(LocalDate baseDate) {
		do {
			this.nextDeliveryDate =
					this.nextDeliveryDate.plusMonths(1);
		} while (!this.nextDeliveryDate.isAfter(baseDate));
	}

	public void toggleActive() {
		this.active = !this.active;
	}

	public void pause() {
		this.active = false;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}

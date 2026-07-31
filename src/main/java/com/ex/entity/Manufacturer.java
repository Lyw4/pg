package com.ex.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "manufacturer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Manufacturer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long manufacturerId;
	private String companyName;
	private String contactPerson;
	private String phone;

	public Manufacturer(String companyName, String contactPerson, String phone) {
		this.companyName = companyName;
		this.contactPerson = contactPerson;
		this.phone = phone;
	}

	public Long getId() {
		return manufacturerId;
	}

	public String getName() {
		return companyName;
	}
}

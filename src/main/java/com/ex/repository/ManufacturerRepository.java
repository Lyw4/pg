package com.ex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.Manufacturer;

public interface ManufacturerRepository
        extends JpaRepository<Manufacturer, Long> {

    Optional<Manufacturer> findByCompanyName(String companyName);
}
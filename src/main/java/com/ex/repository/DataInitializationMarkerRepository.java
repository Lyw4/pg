package com.ex.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.DataInitializationMarker;

public interface DataInitializationMarkerRepository
        extends JpaRepository<DataInitializationMarker, String> {
}

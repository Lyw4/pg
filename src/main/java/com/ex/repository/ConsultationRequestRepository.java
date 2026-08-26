package com.ex.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.ConsultationRequest;

public interface ConsultationRequestRepository
        extends JpaRepository<ConsultationRequest, Long> {
}

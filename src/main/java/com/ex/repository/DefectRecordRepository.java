package com.ex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.DefectRecord;
import com.ex.entity.DefectRecord.DefectStatus;

public interface DefectRecordRepository extends JpaRepository<DefectRecord, Long> {

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "lot.product.manufacturer"
    })
    List<DefectRecord> findAllByOrderByCreatedAtDesc();

    long countByStatusNot(DefectStatus status);

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "lot.product.manufacturer"
    })
    List<DefectRecord> findByLotLotIdOrderByCreatedAtDesc(Long lotId);
}

package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.BinPurpose;
import com.ex.entity.WarehouseBin;

public interface WarehouseBinRepository
        extends JpaRepository<WarehouseBin, Long> {

    @EntityGraph(attributePaths = "warehouse")
    List<WarehouseBin>
            findAllByOrderByWarehouseDisplayOrderAscBinCodeAsc();

    @EntityGraph(attributePaths = "warehouse")
    List<WarehouseBin>
            findByWarehouseWarehouseIdOrderByBinCodeAsc(Long warehouseId);

    @EntityGraph(attributePaths = "warehouse")
    List<WarehouseBin>
            findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                    Long warehouseId);

    Optional<WarehouseBin>
            findByWarehouseWarehouseIdAndBinCode(
                    Long warehouseId,
                    String binCode);

    Optional<WarehouseBin>
            findFirstByWarehouseWarehouseIdAndPurposeAndActiveTrueOrderByBinCodeAsc(
                    Long warehouseId,
                    BinPurpose purpose);
}

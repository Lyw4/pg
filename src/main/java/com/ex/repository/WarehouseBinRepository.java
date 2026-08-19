package com.ex.repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WarehouseBin b where b.binId in :binIds order by b.binId")
    List<WarehouseBin> findAllByBinIdInForUpdate(
            @Param("binIds") Collection<Long> binIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WarehouseBin b join fetch b.warehouse where b.binId = :binId")
    Optional<WarehouseBin> findByBinIdForUpdate(@Param("binId") Long binId);
}

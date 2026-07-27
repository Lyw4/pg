package com.feedflow.repository;

import com.feedflow.domain.WarehouseBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, Long> {

    /** 등록 시 중복 검사 */
    boolean existsByBinCode(String binCode);

    /** 수정 시 중복 검사 (자기 자신은 제외) */
    boolean existsByBinCodeAndBinIdNot(String binCode, Long binId);

    Optional<WarehouseBin> findByBinCode(String binCode);

    /**
     * 구역 목록 검색.
     *
     * @param zone   구역 (null 이면 전체)
     * @param active 사용 여부 (null 이면 전체)
     */
    @Query("""
            select b
            from WarehouseBin b
            where (:zone is null or b.zone = :zone)
              and (:active is null or b.active = :active)
            order by b.binCode asc
            """)
    List<WarehouseBin> search(@Param("zone") String zone,
                              @Param("active") Boolean active);

    /** 검색 필터용 구역(Zone) 목록 */
    @Query("select distinct b.zone from WarehouseBin b order by b.zone asc")
    List<String> findDistinctZones();

    /** 사용 중인 구역 수 */
    long countByActive(boolean active);
}

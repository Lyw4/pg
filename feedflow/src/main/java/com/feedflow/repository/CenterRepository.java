package com.feedflow.repository;

import com.feedflow.domain.Center;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 물류센터 조회.
 * <p>
 * 목록은 {@code centerCode} 순으로 정렬한다. 코드 체계에 지역 순서를 담아두면
 * 별도 정렬 컬럼 없이도 원하는 순서가 나온다.
 */
public interface CenterRepository extends JpaRepository<Center, Long> {

    /** 전체 센터 (코드 순) */
    List<Center> findAllByOrderByCenterCodeAsc();

    /** 운영 중인 센터만 (코드 순) — 구역 등록 · 도면 탭 등에서 쓴다 */
    List<Center> findByActiveTrueOrderByCenterCodeAsc();

    Optional<Center> findByCenterCode(String centerCode);

    boolean existsByCenterCode(String centerCode);
}

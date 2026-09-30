package com.feedflow.repository;

import com.feedflow.domain.Manufacturer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 제조사(공급업체) 조회.
 * <p>
 * 목록은 이름 순으로 정렬한다. 제조사는 코드 체계가 없어(사업자등록번호는 식별자이지만
 * 사람이 읽는 순서가 아니다) 이름이 유일한 정렬 기준이다.
 */
public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {

    /** 전체 제조사 (이름 순) */
    List<Manufacturer> findAllByOrderByNameAsc();

    /** 거래 중인 제조사만 — 품목 등록 · 불량 반품 선택 목록에서 쓴다 */
    List<Manufacturer> findByActiveTrueOrderByNameAsc();

    Optional<Manufacturer> findByName(String name);

    /** 등록 시 중복 검사 */
    boolean existsByName(String name);
}

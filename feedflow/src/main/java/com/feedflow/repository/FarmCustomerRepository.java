package com.feedflow.repository;

import com.feedflow.admin.dto.CenterFarmRow;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.CustomerStatus;
import com.feedflow.domain.FarmCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 농장 고객사 조회.
 *
 * <h3>목록과 집계를 나눠 조회한다</h3>
 * 화면은 <b>필터가 걸린 목록</b>과 <b>필터와 무관한 센터별 집계</b>를 함께 보여준다.
 * 목록을 자바에서 그룹핑해 집계를 만들면 센터를 하나 고른 순간 집계도 그 센터
 * 하나로 줄어들어, "다른 센터에는 농장이 몇 곳인지" 를 알 수 없게 된다.
 * 재고 현황 화면의 센터 분포와 같은 이유다.
 * <p>
 * 그래서 쿼리는 <b>목록 1회 + 집계 1회</b>로 끝난다.
 */
public interface FarmCustomerRepository extends JpaRepository<FarmCustomer, Long> {

    /** 등록 시 중복 검사 */
    boolean existsByFarmCode(String farmCode);

    /**
     * 농장 목록 검색.
     *
     * <h3>정렬은 센터 → 거리 순이다</h3>
     * 담당자는 "이 센터가 담당하는 농장" 을 묶어서 보고, 그 안에서는
     * <b>가까운 곳부터</b> 배송 순서를 잡는다. 이름순으로 정렬하면 지도상
     * 흩어진 순서가 되어 배송 계획에 쓸 수 없다.
     *
     * <h3>목록이 센터명을 표시하므로 fetch join 한다</h3>
     * 없으면 농장 수만큼 센터 조회 쿼리가 추가로 나간다(N+1).
     *
     * @param centerId   담당 센터 (null 이면 전체)
     * @param animalType 축종 (null 이면 전체)
     * @param status     거래 상태 (null 이면 전체)
     * @param keyword    농장명 · 대표자 · 주소 · 농장코드 부분 일치.
     *                   <b>대문자로 변환해 {@code %...%} 까지 감싼 값</b>을 넘겨야 한다
     *                   (JPQL 에서 문자열을 조립하면 인덱스 사용 판단이 달라질 수 있어
     *                    호출부가 만든 값을 그대로 쓴다)
     */
    @Query("""
            select f
            from FarmCustomer f
                join fetch f.center c
            where (:centerId is null or c.centerId = :centerId)
              and (:animalType is null or f.animalType = :animalType)
              and (:status is null or f.status = :status)
              and (:keyword is null
                   or upper(f.farmName) like :keyword
                   or upper(f.representativeName) like :keyword
                   or upper(f.address) like :keyword
                   or upper(f.farmCode) like :keyword)
            order by c.centerCode asc, f.distanceKm asc
            """)
    List<FarmCustomer> search(@Param("centerId") Long centerId,
                              @Param("animalType") AnimalType animalType,
                              @Param("status") CustomerStatus status,
                              @Param("keyword") String keyword);

    /**
     * 센터별 담당 농장 집계 (센터 카드 · 전국 요약용).
     * <p>
     * <b>농장 수와 사육 규모는 전체를, 월 예상 사료량은 거래 중만</b> 센다.
     * 세는 기준이 다른 이유는 {@link CenterFarmRow} 에 적어 두었다.
     *
     * <h3>담당 농장이 없는 센터는 결과에 나오지 않는다</h3>
     * {@code group by} 결과이므로 행 자체가 없다. 호출부에서 0 으로 채워야 한다.
     * 여기서 {@code Center} 기준 outer join 으로 바꿀 수도 있지만, 그러면 이 쿼리가
     * "센터 목록" 의 책임까지 갖게 된다. 센터 목록은 이미 {@code CenterRepository} 가
     * 갖고 있으므로 두 결과를 호출부에서 맞추는 편이 책임이 섞이지 않는다.
     */
    @Query("""
            select new com.feedflow.admin.dto.CenterFarmRow(
                       c.centerId,
                       c.name,
                       count(f),
                       sum(case when f.status = com.feedflow.domain.CustomerStatus.ACTIVE
                                then 1L else 0L end),
                       sum(f.livestockCount),
                       sum(case when f.status = com.feedflow.domain.CustomerStatus.ACTIVE
                                then f.monthlyFeedQuantity else 0 end))
            from FarmCustomer f
                join f.center c
            group by c.centerId, c.name, c.centerCode
            order by c.centerCode asc
            """)
    List<CenterFarmRow> findFarmSummaryByCenter();

    /**
     * 농장 1건 + 센터 (상태 변경 결과 메시지에 센터명을 쓰는 경로용).
     * <p>
     * {@code findById} 로 읽으면 센터가 지연 로딩이라 {@code centerName()} 호출 시
     * 쿼리가 한 번 더 나간다.
     */
    @Query("""
            select f
            from FarmCustomer f
                join fetch f.center c
            where f.farmCustomerId = :farmCustomerId
            """)
    Optional<FarmCustomer> findWithCenterById(@Param("farmCustomerId") Long farmCustomerId);

    /**
     * 담당 센터가 취급하지 않는 축종을 가진 농장.
     * <p>
     * 센터의 운영 방향({@code Center.note})은 문장이라 기계가 판단할 수 없다.
     * 대신 <b>그 센터에 그 축종 사료 재고가 한 번도 없었는지</b>로 판단한다.
     * 예를 들어 나주 센터는 가금 전용이라 한우 사료를 두지 않는데, 담당 농장에
     * 한우 농장이 있으면 배정이 잘못된 것이다.
     *
     * <h3>이 쿼리를 만든 이유</h3>
     * 스마트 주문 할당(P4b)에서 <b>최단 거리 센터가 그 축종을 취급하지 않을 수 있다</b>는
     * 문제를 실제 데이터로 확인하기 위한 것이다. 지금은 배정 검토 화면에서
     * 경고를 띄우는 데 쓴다.
     */
    @Query("""
            select f
            from FarmCustomer f
                join fetch f.center c
            where not exists (
                      select 1
                      from Inventory i
                          join i.bin b
                          join i.lot l
                          join l.product p
                      where b.center = f.center
                        and p.animalType = f.animalType
                  )
            order by c.centerCode asc, f.farmCode asc
            """)
    List<FarmCustomer> findWithUnsupportedAnimalType();
}

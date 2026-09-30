package com.feedflow.repository;

import com.feedflow.admin.dto.DefectStatRow;
import com.feedflow.domain.DefectRecord;
import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectStatus;
import com.feedflow.domain.DefectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 불량 기록 조회 · 집계.
 *
 * <h3>목록과 집계를 나눠 조회한다</h3>
 * 화면은 <b>필터가 걸린 목록</b>과 <b>필터와 무관한 유형별 · 제조사별 집계</b>를 함께
 * 보여준다. 목록을 자바에서 그룹핑하면 상태를 하나 고른 순간 집계도 그 상태만 남아,
 * "다른 유형은 몇 건인가" 를 알 수 없게 된다.
 */
public interface DefectRecordRepository extends JpaRepository<DefectRecord, Long> {

    /** 등록 시 관리번호 중복 검사 */
    boolean existsByDefectNo(String defectNo);

    /**
     * 해당 월 접두어의 마지막 관리번호.
     * <p>
     * 건수를 세지 않고 <b>최댓값</b>을 쓴다. 관리번호는 문자열이지만 접두어가 같은
     * 구간에서는 자리수가 고정("DF-2607-001")이라 사전순 최댓값이 곧 마지막 번호다.
     * 건수로 매기면 중간에 한 건이라도 사라졌을 때 이미 쓴 번호를 다시 발급한다.
     *
     * @param prefix "DF-2607-" 형태의 월 접두어
     */
    @Query("""
            select max(d.defectNo)
            from DefectRecord d
            where d.defectNo like concat(:prefix, '%')
            """)
    Optional<String> findMaxDefectNo(@Param("prefix") String prefix);

    /**
     * 불량 목록 검색.
     *
     * <h3>정렬은 미처리 건이 먼저다</h3>
     * 격리해 둔 재고를 방치하는 것이 가장 나쁘다. 처리 완료된 건은 참고용이므로
     * 아래로 내린다. 같은 상태 안에서는 <b>오래된 것부터</b> 보여준다 —
     * 최신순으로 두면 오래 방치된 건이 목록 끝으로 밀려 영원히 안 보인다.
     *
     * <h3>fetch join 대상</h3>
     * 목록이 로트번호 · 품목명 · 제조사명 · 센터명을 표시한다. 없으면 행마다
     * 조회가 추가로 나간다(N+1). 제조사는 품목에 없을 수 있고 구역도 null 일 수 있어
     * <b>left join</b> 이어야 한다. inner join 으로 쓰면 제조사가 없는 품목의
     * 불량 기록이 목록에서 통째로 사라진다.
     *
     * @param status     처리 상태 (null 이면 전체)
     * @param defectType 불량 유형 (null 이면 전체)
     * @param stage      발견 단계 (null 이면 전체)
     * @param centerId   발견 센터 (null 이면 전국)
     */
    @Query("""
            select distinct d
            from DefectRecord d
                join fetch d.lot l
                join fetch l.product p
                left join fetch p.manufacturer m
                left join fetch d.bin b
                left join fetch b.center c
            where (:status is null or d.status = :status)
              and (:defectType is null or d.defectType = :defectType)
              and (:stage is null or d.stage = :stage)
              and (:centerId is null or c.centerId = :centerId)
            order by
                case d.status
                    when com.feedflow.domain.DefectStatus.QUARANTINED then 0
                    when com.feedflow.domain.DefectStatus.INSPECTING then 1
                    else 2
                end asc,
                d.createdAt asc
            """)
    List<DefectRecord> search(@Param("status") DefectStatus status,
                              @Param("defectType") DefectType defectType,
                              @Param("stage") DefectStage stage,
                              @Param("centerId") Long centerId);

    /**
     * 불량 1건 + 연관 (처리 화면 · 결과 메시지용).
     * <p>
     * 처리 결과 메시지에 로트번호 · 품목명 · 제조사명을 쓰므로 함께 읽는다.
     */
    @Query("""
            select d
            from DefectRecord d
                join fetch d.lot l
                join fetch l.product p
                left join fetch p.manufacturer m
                left join fetch d.bin b
                left join fetch b.center c
            where d.defectId = :defectId
            """)
    Optional<DefectRecord> findWithDetailById(@Param("defectId") Long defectId);

    /** 미처리 건수 (대시보드 · 목록 헤더) */
    @Query("""
            select count(d)
            from DefectRecord d
            where d.status <> com.feedflow.domain.DefectStatus.RESOLVED
            """)
    long countOpen();

    /**
     * 유형별 집계.
     * <p>
     * 어떤 문제가 반복되는지 본다. 파손이 잦으면 운송이나 포장을,
     * 오염이 잦으면 보관 환경을 봐야 한다.
     */
    @Query("""
            select new com.feedflow.admin.dto.DefectStatRow(
                       cast(d.defectType as string), count(d), sum(d.quantity))
            from DefectRecord d
            group by d.defectType
            order by sum(d.quantity) desc
            """)
    List<DefectStatRow> findStatsByType();

    /**
     * 발견 단계별 집계.
     * <p>
     * <b>입고 검사에서 잡히는 비중이 높아야 좋다.</b> 보관 중이나 출고 검사에서
     * 발견되는 비중이 크면 입고 검수가 제 역할을 못 하고 있다는 뜻이다.
     * 불량이 늦게 발견될수록 그 재고를 보관하는 데 쓴 자리와 시간이 낭비된다.
     */
    @Query("""
            select new com.feedflow.admin.dto.DefectStatRow(
                       cast(d.stage as string), count(d), sum(d.quantity))
            from DefectRecord d
            group by d.stage
            order by count(d) desc
            """)
    List<DefectStatRow> findStatsByStage();

    /**
     * 제조사별 집계 (공급업체 평가 근거).
     * <p>
     * 제조사가 등록되지 않은 품목의 불량은 <b>'미등록' 으로 묶어 함께 보여준다.</b>
     * 제외하면 합계가 전체와 맞지 않아 "왜 숫자가 다른가" 를 설명할 수 없고,
     * 제조사를 등록해야 한다는 사실도 드러나지 않는다.
     */
    @Query("""
            select new com.feedflow.admin.dto.DefectStatRow(
                       coalesce(m.name, '미등록'), count(d), sum(d.quantity))
            from DefectRecord d
                join d.lot l
                join l.product p
                left join p.manufacturer m
            group by m.name
            order by count(d) desc
            """)
    List<DefectStatRow> findStatsByManufacturer();

    /**
     * 오래 방치된 미처리 건.
     * <p>
     * 격리해 둔 재고는 자리를 차지하면서 출고도 되지 않는다. 처리를 미루면
     * <b>창고 공간만 잠식</b>한다. 기준일보다 오래된 미처리 건을 경고로 띄운다.
     *
     * @param threshold 이 시각보다 오래 등록된 건을 방치로 본다
     */
    @Query("""
            select d
            from DefectRecord d
                join fetch d.lot l
                join fetch l.product p
                left join fetch d.bin b
                left join fetch b.center c
            where d.status <> com.feedflow.domain.DefectStatus.RESOLVED
              and d.createdAt < :threshold
            order by d.createdAt asc
            """)
    List<DefectRecord> findStale(@Param("threshold") LocalDateTime threshold);
}

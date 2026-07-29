package com.feedflow.admin.service;

import com.feedflow.admin.dto.InventoryDto;
import com.feedflow.admin.dto.TraceEventDto;
import com.feedflow.admin.dto.TraceabilityDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.common.util.Texts;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.StockMovement;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 제품 이력 추적(Traceability) 조회 서비스.
 *
 * <h3>추적 근거</h3>
 * 로트의 생애주기는 {@code StockMovement} 이력을 시간순으로 재생해 복원한다.
 * 재고 테이블은 <b>현재 상태만</b> 담고 있어 과거를 알 수 없기 때문이다.
 * 출고 취소 복구도 {@code CANCEL} 유형으로 남아 있어 타임라인에 그대로 나타난다.
 *
 * <h3>쿼리 구성 (N+1 방지)</h3>
 * 조회는 로트당 <b>3회로 고정</b>된다.
 * <ol>
 *     <li>로트 + 품목 ({@code join fetch})</li>
 *     <li>이력 전체 + 품목 · 로트 · 구역 ({@code join fetch})</li>
 *     <li>현재 구역별 재고 + 로트 · 품목 · 구역 ({@code join fetch})</li>
 * </ol>
 * 이력 건수나 보관 구역 수가 늘어도 쿼리 수는 변하지 않는다.
 *
 * <p>모두 조회 전용이다. 재고를 변경하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraceabilityService {

    private final ProductLotRepository productLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryRepository inventoryRepository;

    /* ------------------------------------------------------------------
     * 검색
     * ------------------------------------------------------------------ */

    /**
     * 로트번호로 후보 로트를 찾는다.
     * <p>
     * 로트번호는 <b>품목 단위로만 유일</b>하다({@code ukProductLotNo}).
     * 서로 다른 품목이 같은 번호를 쓸 수 있으므로 목록으로 돌려주고,
     * 여러 건이면 화면에서 사용자가 고르게 한다.
     *
     * @param lotNo 로트번호 (대소문자 구분 없음, 공백 무시)
     * @return 후보 로트 (없으면 빈 목록)
     */
    public List<ProductLot> findCandidates(String lotNo) {
        String keyword = Texts.trimToNull(lotNo);
        if (keyword == null) {
            return List.of();
        }
        return productLotRepository.findAllByLotNo(keyword.toUpperCase());
    }

    /* ------------------------------------------------------------------
     * 추적
     * ------------------------------------------------------------------ */

    /**
     * 로트 하나의 생애주기를 조립한다.
     *
     * @param lotId 추적할 로트
     * @param today D-Day 계산 기준일
     * @throws ResourceNotFoundException 로트가 존재하지 않는 경우
     */
    public TraceabilityDto trace(Long lotId, LocalDate today) {
        ProductLot lot = productLotRepository.findWithProductById(lotId)
                .orElseThrow(() -> ResourceNotFoundException.ofProductLot(lotId));

        List<TraceEventDto> timeline = buildTimeline(lotId);

        List<InventoryDto> currentStorage = inventoryRepository.findByLotIdWithBin(lotId).stream()
                .map(inventory -> InventoryDto.of(inventory, today))
                .toList();

        return TraceabilityDto.of(lot, currentStorage, timeline, today);
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    /**
     * 이력을 시간순으로 누적해 타임라인을 만든다.
     * <p>
     * 각 이벤트에 <b>그 시점의 잔여 수량</b>을 함께 담는다.
     * 증감 방향은 {@code MovementType.sign} 이 이미 알고 있으므로
     * 유형별 분기를 서비스가 다시 구현하지 않는다.
     * (구역 이동 · 재고조정은 sign 이 0 이라 잔여 수량이 변하지 않는다)
     */
    private List<TraceEventDto> buildTimeline(Long lotId) {
        List<StockMovement> movements = stockMovementRepository.findLotHistory(lotId);

        List<TraceEventDto> timeline = new ArrayList<>(movements.size());
        int balance = 0;
        int sequence = 1;

        for (StockMovement movement : movements) {
            int quantity = movement.getQuantity() == null ? 0 : movement.getQuantity();
            balance += movement.getMovementType().getSign() * quantity;

            timeline.add(TraceEventDto.of(movement, sequence++, balance));
        }
        return timeline;
    }
}

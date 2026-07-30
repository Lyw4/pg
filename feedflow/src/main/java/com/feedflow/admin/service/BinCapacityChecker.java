package com.feedflow.admin.service;

import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.util.Numbers;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 적재 한도 검증기.
 *
 * <h3>왜 별도 컴포넌트인가</h3>
 * 재고를 <b>늘리는</b> 경로는 세 개다. 입고 · 구역 간 이동(도착지) · 출고 취소 복구.
 * 세 서비스가 같은 절차를 각각 구현하고 있었다.
 * <pre>
 *   1. 구역의 현재 적재량 조회 (sum 이 null 이면 0 으로 보정)
 *   2. WarehouseBin.canAccept() 로 한도 판정
 *   3. 초과 시 BusinessRuleException
 * </pre>
 * 판정 <b>규칙</b> 자체는 이미 {@link WarehouseBin#canAccept(int, int)} 로 도메인에 있었지만,
 * "조회해서 검증하고 예외를 던지는 절차" 는 여전히 세 곳에 복제되어 있었다.
 * 그 결과 예외 문구가 세 가지로 갈렸다.
 * ("최대 적재 수량을 초과합니다" / "적재 한도를 초과합니다" / "적재 한도를 초과해 되돌릴 수 없습니다")
 * <p>
 * 적재량 조회에 Repository 가 필요해 엔티티로 더 끌어올릴 수는 없다.
 * 그래서 도메인 규칙과 조회를 잇는 얇은 컴포넌트로 분리했다.
 *
 * <h3>다중 창고(Center) 확장</h3>
 * 판정 단위가 <b>구역(bin)</b> 이므로 구역이 특정 창고에 속하게 되어도 이 클래스는 바뀌지 않는다.
 * 적재 한도는 창고가 아니라 구역의 속성이기 때문이다.
 *
 * <h3>한도가 없는 구역</h3>
 * 센터 간 이관에 쓰는 <b>운송 중 가상 구역</b>은 창고 안의 바닥이 아니라 트럭 위다.
 * 면적이 없으므로 한도 판정을 건너뛴다.
 * ({@link com.feedflow.domain.BinPurpose#isPhysicalSpace()})
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BinCapacityChecker {

    private final InventoryRepository inventoryRepository;

    /**
     * 구역에 수량을 더 넣을 수 있는지 확인한다.
     *
     * @param bin         대상 구역
     * @param quantity    추가할 수량
     * @param actionLabel 예외 문구에 들어갈 작업 이름 (예: 입고 · 이동 · 복구)
     * @return 추가 <b>전</b> 구역 전체 적재량.
     *         호출한 쪽이 결과 화면에 "이동 후 남은 여유" 를 표기할 때 같은 값을 써야 하므로
     *         버리지 않고 돌려준다. 따로 다시 조회하면 두 값이 어긋날 수 있다.
     * @throws BusinessRuleException 적재 한도를 초과하는 경우
     */
    public int checkCanAccept(WarehouseBin bin, int quantity, String actionLabel) {
        int currentLoad = currentLoadOf(bin);

        // 물리적 공간이 아닌 구역(운송 중)은 한도를 셀 수 없다.
        // 트럭에 실려 있는 재고에 "몇 포대까지" 라는 바닥 면적은 존재하지 않는다.
        // maxCapacity 를 크게 잡아 우회하지 않고 검증 자체를 건너뛴다 —
        // 임의의 큰 수는 언젠가 넘고, 넘었을 때 왜 막혔는지 알 수 없다.
        //
        // 판정은 도메인에 맡긴다. 용도를 알 수 없는 구역은 한도가 있는 것으로 보므로
        // 검증이 조용히 꺼지지 않는다. (WarehouseBin.hasCapacityLimit)
        if (!bin.hasCapacityLimit()) {
            return currentLoad;
        }

        if (!bin.canAccept(currentLoad, quantity)) {
            throw new BusinessRuleException(
                    "구역 [" + bin.getBinCode() + "] 의 적재 한도를 초과합니다."
                            + " (현재 " + currentLoad + " + " + actionLabel + " " + quantity
                            + " > 한도 " + bin.capacityLimit() + ")");
        }
        return currentLoad;
    }

    /**
     * 구역의 현재 전체 적재량.
     * <p>
     * 재고가 한 건도 없는 구역은 {@code sum()} 이 null 을 반환하므로 0 으로 보정한다.
     * <p>
     * 특정 로트의 수량이 아니라 <b>구역에 쌓인 모든 로트의 합</b>이다.
     * 한도 판정과 여유 공간 표기는 반드시 이 값을 기준으로 해야 한다.
     */
    public int currentLoadOf(WarehouseBin bin) {
        return (int) Numbers.orZero(inventoryRepository.sumQuantityByBinId(bin.getBinId()));
    }
}

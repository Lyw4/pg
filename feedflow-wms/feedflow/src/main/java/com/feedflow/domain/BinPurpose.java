package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 구역의 용도.
 * <p>
 * 창고에는 재고를 쌓아두는 보관 구역만 있는 것이 아니라, 입고된 물건을 잠시 두는
 * 대기 구역과 출고 직전에 모아두는 구역도 있다. 이들은 <b>도면에는 그려야 하지만
 * 창고 적재율 계산에 넣으면 안 된다.</b> 상시 보관 공간이 아니기 때문이다.
 *
 * <h3>세 가지 축을 구분해야 한다</h3>
 * "대기 구역" 과 "운송 중" 은 통계에서 빠진다는 점은 같지만 성격이 전혀 다르다.
 * 대기 구역은 <b>창고 안의 실제 바닥</b>이라 적재 한도가 있고 도면에 그려야 한다.
 * 운송 중은 <b>물리적 공간이 아니다</b> — 한도를 셀 수 없고 도면에 그릴 자리도 없다.
 * 그래서 축을 세 개로 나눠 둔다.
 *
 * @see #isCountedInCapacity() 적재율 통계 포함 여부
 * @see #isPhysicalSpace()     실재하는 바닥인지 (한도 검증 · 도면 표시 대상)
 * @see #isSystemManaged()     시스템이 만드는 구역인지 (사람이 등록·수정할 수 없다)
 */
@Getter
@RequiredArgsConstructor
public enum BinPurpose {

    STORAGE("보관", "bg-primary", true, true, false),
    RECEIVING("입고 대기", "bg-info text-dark", false, true, false),
    SHIPPING("출고 대기", "bg-warning text-dark", false, true, false),
    INSPECTION("검수", "bg-secondary", false, true, false),

    /**
     * 센터 간 이관 중인 재고가 머무는 <b>가상 구역</b>.
     * <p>
     * 출발 센터에서 나와 도착 센터에 들어가기 전까지의 재고를 담는다.
     * 이 자리가 없으면 이관 도중 재고가 어디에도 속하지 않아
     * <b>3계층 불변식</b>({@code totalStock} = Σ{@code lotQuantity} = Σ{@code Inventory.quantity})
     * 이 깨지고, 재고 정합성 점검이 오탐한다.
     * <p>
     * <b>출발 센터 소속</b>이다. 운송 중 재고는 아직 출발 센터의 책임 아래 있고,
     * 분실·파손 시 책임 소재도 그쪽이기 때문이다. 다만 "팔 수 있는 재고" 는 아니므로
     * 센터별 가용 재고 집계에서는 제외한다.
     * <p>
     * 물리적 공간이 아니므로 <b>적재 한도를 검증하지 않고 도면에도 그리지 않는다.</b>
     * 시스템이 센터당 하나씩 자동으로 만든다.
     */
    IN_TRANSIT("운송 중", "bg-dark", false, false, true);

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 창고 전체 적재율 통계에 포함할 용도인지.
     * <p>
     * 입고/출고 대기 구역은 흐름상 잠시 머무는 곳이라 여기에 물건이 많은 것이
     * "창고가 찼다"는 뜻은 아니다. 통계에 섞으면 적재율이 왜곡된다.
     */
    private final boolean countedInCapacity;

    /**
     * 창고 안에 실재하는 바닥인지.
     * <p>
     * {@code false} 인 구역은 <b>적재 한도를 검증하지 않고 2D 도면에도 그리지 않는다.</b>
     * 운송 중 재고에 "몇 포대까지" 라는 한도를 매기는 것은 의미가 없고,
     * 도면에 그리면 창고에 없는 칸이 나타난다.
     */
    private final boolean physicalSpace;

    /**
     * 시스템이 만들고 관리하는 구역인지.
     * <p>
     * {@code true} 면 사람이 구역 등록·수정 화면에서 이 용도를 고를 수 없다.
     * 이관 로직이 규칙에 맞는 코드로 자동 생성하므로, 손으로 만들면
     * 코드 규칙이 어긋나 이관이 엉뚱한 구역을 찾게 된다.
     */
    private final boolean systemManaged;

    /** 사람이 등록·수정 화면에서 고를 수 있는 용도 */
    public boolean isSelectableByUser() {
        return !systemManaged;
    }
}

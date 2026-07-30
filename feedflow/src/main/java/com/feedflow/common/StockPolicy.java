package com.feedflow.common;

/**
 * 재고 관련 업무 정책 상수.
 *
 * <h3>왜 모아 두는가</h3>
 * 유통기한 임박 기준(30일)이 세 곳에 <b>따로</b> 정의되어 있었다.
 * <ul>
 *     <li>{@code DashboardService} : {@code @Value("${feedflow.dashboard.expiration-alert-days:30}")}</li>
 *     <li>{@code WarehouseBinMapDto} : {@code EXPIRING_SOON_DAYS = 30} (하드코딩)</li>
 *     <li>{@code DDay} : {@code WARNING_DAYS = 30} (하드코딩)</li>
 * </ul>
 * 설정 파일의 값만 바꾸면 대시보드 알림 기준은 변하지만 <b>2D 도면의 임박 뱃지와
 * D-Day 색상은 그대로 남아</b> 같은 로트가 화면마다 다르게 보인다.
 * 세 곳이 같은 값을 쓴다는 사실을 코드로 드러내기 위해 한곳에 모았다.
 *
 * <h3>설정과의 관계</h3>
 * {@link #EXPIRING_SOON_DAYS} 는 {@code feedflow.dashboard.expiration-alert-days} 의
 * <b>기본값과 같아야 한다.</b> 두 값이 어긋나면 대시보드와 나머지 화면의 기준이 갈린다.
 * 이 일치는 {@code StockPolicyTest} 가 {@code application.properties} 를 직접 읽어 고정한다.
 * <p>
 * 대시보드만 설정으로 기준을 바꿀 수 있는 이유는 그 화면이 요약 알림이라 운영 중
 * 조정 수요가 있었기 때문이다. 도면 뱃지와 D-Day 는 정적 표기라 상수로 충분하다.
 */
public final class StockPolicy {

    private StockPolicy() {
    }

    /**
     * 유통기한 임박 기준 (일).
     * <p>
     * 이 일수 이내로 남은 로트를 "임박" 으로 본다.
     * {@code feedflow.dashboard.expiration-alert-days} 의 기본값과 일치해야 한다.
     */
    public static final int EXPIRING_SOON_DAYS = 30;

    /**
     * 유통기한 위험 기준 (일).
     * <p>
     * 임박보다 더 급한 구간. D-Day 뱃지를 빨강으로 표시한다.
     */
    public static final int EXPIRY_CRITICAL_DAYS = 7;

    /**
     * 비율 표기 상한 (%).
     * <p>
     * 적재율은 한도를 넘을 수 있지만(구역 정리 중 초과 적재 등) 진행바는 100% 에서 멈춘다.
     * 막대가 칸을 넘어가면 레이아웃이 깨지기 때문이다. 숫자 자체는 원래 값을 보여준다.
     */
    public static final int MAX_PERCENT = 100;
}

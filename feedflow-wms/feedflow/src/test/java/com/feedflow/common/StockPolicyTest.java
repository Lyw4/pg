package com.feedflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 정책 상수와 설정 파일의 일치를 고정하는 테스트.
 *
 * <h3>왜 필요한가</h3>
 * 유통기한 임박 기준은 두 경로로 쓰인다.
 * <ul>
 *     <li>대시보드 알림 : {@code feedflow.dashboard.expiration-alert-days} 설정값</li>
 *     <li>2D 도면 임박 뱃지 · D-Day 색상 : {@link StockPolicy#EXPIRING_SOON_DAYS} 상수</li>
 * </ul>
 * 둘이 어긋나면 <b>같은 로트가 화면마다 다르게 보인다.</b> 대시보드는 "임박 없음" 인데
 * 도면에는 임박 뱃지가 붙는 식이다. 이런 불일치는 실행해 봐도 바로 눈에 띄지 않아
 * 설정을 고칠 때 상수를 함께 고치도록 테스트로 묶어 둔다.
 */
@DisplayName("StockPolicy 상수와 설정 일치")
class StockPolicyTest {

    private static final String PROPERTIES_PATH = "/application.properties";
    private static final String EXPIRATION_ALERT_DAYS_KEY = "feedflow.dashboard.expiration-alert-days";

    @Test
    @DisplayName("유통기한 임박 기준 상수가 application.properties 의 값과 같다")
    void expiringSoonDaysMatchesConfiguration() throws IOException {
        Properties properties = loadApplicationProperties();

        String configured = properties.getProperty(EXPIRATION_ALERT_DAYS_KEY);

        assertThat(configured)
                .as("설정 키 %s 가 application.properties 에 있어야 한다", EXPIRATION_ALERT_DAYS_KEY)
                .isNotNull();

        assertThat(Integer.parseInt(configured.trim()))
                .as("설정을 바꿨다면 StockPolicy.EXPIRING_SOON_DAYS 도 함께 바꿔야 한다. "
                        + "어긋나면 대시보드 알림과 도면 임박 뱃지의 기준이 갈린다")
                .isEqualTo(StockPolicy.EXPIRING_SOON_DAYS);
    }

    @Test
    @DisplayName("위험 구간은 임박 구간보다 짧다")
    void criticalIsShorterThanExpiringSoon() {
        // 위험(빨강)이 임박(노랑)보다 길면 색 구간이 뒤집혀 D-Day 뱃지가 뒤죽박죽 된다
        assertThat(StockPolicy.EXPIRY_CRITICAL_DAYS)
                .isLessThan(StockPolicy.EXPIRING_SOON_DAYS)
                .isPositive();
    }

    @Test
    @DisplayName("비율 상한은 100 이다")
    void maxPercentIsHundred() {
        assertThat(StockPolicy.MAX_PERCENT).isEqualTo(100);
    }

    private Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream(PROPERTIES_PATH)) {
            assertThat(in)
                    .as("application.properties 를 클래스패스에서 찾을 수 없다")
                    .isNotNull();
            properties.load(in);
        }
        return properties;
    }
}

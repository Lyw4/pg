-- =====================================================================
-- FeedFlow 초기 데이터 (H2 In-Memory)
--  · 서버 시작 시 자동 실행 (spring.jpa.defer-datasource-initialization=true)
--  · 테이블/컬럼명은 카멜 표기법(camelCase)으로 선언한다.
--    (H2 는 따옴표 없는 식별자를 대문자로 저장하므로 대소문자 구분 없이 조회된다)
--  · 비밀번호는 DelegatingPasswordEncoder 의 {noop}(평문) prefix 사용
--    → 실제 운영/회원가입 시에는 {bcrypt} 해시가 저장된다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 물류센터 5거점 : 구역(warehouseBins)이 FK 로 참조하므로 가장 먼저 넣는다
--    원래 Warehouse enum(WH1/WH2) 이었으나 전국 확장을 위해 엔티티로 승격했고(P1),
--    P4a 에서 실제 기획 거점 5곳으로 교체했다.
--
--    centerCode 에 순번(C1~C5)을 담아 정렬 순서를 만든다. 별도 정렬 컬럼 없이
--    centerCode 순으로 화면(탭 · 선택 상자 · 분포 차트) 순서가 정해진다.
--
--    각 센터의 note 는 운영 방향이고, 재고 배분이 그 방향을 따른다.
--      예산 양계·양돈 / 김제 닭·오리·돼지 / 의성 균형 / 안성 소·돼지 / 나주 닭·오리 최우선
--    구역(zone)도 축종 코드(CT 소 · PG 돼지 · PL 가금 · COLD 영양제)로 두어
--    2D 도면만 봐도 그 센터가 무엇을 다루는지 드러난다.
-- ---------------------------------------------------------------------
INSERT INTO centers (centerId, centerCode, name, region, address, note, latitude, longitude, active, createdAt) VALUES
(1, 'C1-YS', '충남 예산 센터', '충남 서북부', '충남 예산군 고덕면 몽곡리 667 일대', '양계 · 양돈 중심', 36.772, 126.771, TRUE, DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(2, 'C2-GJ', '전북 김제 센터', '전북 서부 · 새만금권', '전북 김제시 흥사동 서흥농공단지 외곽', '닭 · 오리 · 돼지 중심', 35.812, 126.873, TRUE, DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(3, 'C3-US', '경북 의성 센터', '안동 · 의성 · 경북 북부', '경북 의성군 단촌면 세촌리 국도 5호선 축', '소 · 돼지 · 조류 균형형', 36.418, 128.635, TRUE, DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(4, 'C4-AS', '경기 안성 센터', '경기 남부 · 충북 서부', '경기 안성시 미양면 계륵리 · 구수리', '소 · 돼지 강화형', 37.001, 127.225, TRUE, DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(5, 'C5-NJ', '전남 나주 센터', '전남 중서부', '전남 나주시 문평면 옥당리', '닭 · 오리 최우선', 35.098, 126.662, TRUE, DATEADD('DAY', -140, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 1. 사용자 : 사원(ADMIN 1, STAFF 1) + 고객(USER 3)
-- ---------------------------------------------------------------------
INSERT INTO users (userId, email, password, name, phone, role, createdAt) VALUES
(1, 'admin@feedflow.co.kr', '{noop}admin123', '김책임', '010-1111-1001', 'ADMIN', DATEADD('DAY', -400, CURRENT_TIMESTAMP)),
(2, 'staff@feedflow.co.kr', '{noop}staff123', '이사원', '010-2222-2002', 'STAFF', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(3, 'farm1@example.com',    '{noop}user123',  '정한우목장', '010-3333-3003', 'USER', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(4, 'farm2@example.com',    '{noop}user123',  '대성양돈',   '010-4444-4004', 'USER', DATEADD('DAY', -90,  CURRENT_TIMESTAMP)),
(5, 'farm3@example.com',    '{noop}user123',  '행복한계농장', '010-5555-5005', 'USER', DATEADD('DAY', -60, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 2. 제조사 5곳  ※ 기준 정보(Master Data)
--    품목(products)이 FK 로 참조하므로 품목보다 먼저 넣는다.
--    · manufacturerId 5 는 거래 중지(active FALSE) → 품목 등록 선택 목록에서 빠진다
--      (단종 품목 productId 12 와 짝을 이룬다. 거래를 끊은 곳의 사료가 단종된 상황)
-- ---------------------------------------------------------------------
INSERT INTO manufacturers (manufacturerId, name, businessNumber, phone, contactName, active, createdAt) VALUES
(1, '대한사료(주)',     '312-81-40021', '041-330-7001', '박영수', TRUE,  DATEADD('DAY', -320, CURRENT_TIMESTAMP)),
(2, '한신축산영양(주)', '404-81-55172', '063-540-7002', '최미경', TRUE,  DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(3, '정우피드텍(주)',   '511-81-27384', '054-830-7003', '이건우', TRUE,  DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(4, '그린바이오영양',   '128-81-63095', '031-670-7004', '정하늘', TRUE,  DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(5, '세종사료공업',     '307-81-11846', '044-850-7005', '한동식', FALSE, DATEADD('DAY', -380, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 3. 품목 13종  ※ 기준 정보(Master Data)
--    · 취급 축종은 CATTLE(소) / PIG(돼지) / POULTRY(조류: 닭·오리) 3종으로 고정
--    · 취급 품목 구분은 FEED(사료) / SUPPLEMENT(영양제) 2종으로 고정
--    · productId 1, 3 은 안전재고 미달 → 대시보드 '안전재고 알림' 노출
--    · productId 12 는 사용 중지(단종) → 미달이지만 알림에서 제외
--    · 10건 초과라서 품목 목록 화면의 페이징을 바로 확인할 수 있다
--    · productId 10 의 manufacturerId 는 일부러 NULL 이다.
--      제조사를 모르는 상태로 등록된 품목이 실제로 있고(샘플 · 자사생산 · 등록 누락),
--      이 로트에서 불량이 나면 "반품할 곳을 특정할 수 없다" 는 상황이 화면에 드러난다.
--      불량 관리 화면의 제조사별 집계에서 '미등록' 으로 묶여 표시된다.
-- ---------------------------------------------------------------------
INSERT INTO products (productId, productCode, name, manufacturerId, animalType, productType, weightKg, price, totalStock, safetyStock, shelfLifeDays, active, imageUrl, description, version) VALUES
(1,  'FD-CT-001', '프리미엄 육성우 배합사료', 1, 'CATTLE',  'FEED',       25, 32000,  40,  50, 180, TRUE,  '/images/feed-cattle.png',  '육성기 한우의 골격 형성을 돕는 고단백 배합사료입니다.', 0),
(2,  'FD-PG-001', '자돈용 배합사료',         2, 'PIG',     'FEED',       20, 28000, 690, 100, 180, TRUE,  '/images/feed-pig.png',     '이유 후 자돈의 소화 흡수율을 높인 프리스타터 사료입니다.', 0),
-- productId 3 : 정상 로트 80 + 만료 로트 20 = 100 (안전재고 120 미달 유지)
(3,  'FD-PL-001', '산란계 전용 배합사료',     3, 'POULTRY', 'FEED',       25, 24000, 100, 120,  90, TRUE,  '/images/feed-chicken.png', '산란율 향상을 위한 칼슘 강화 배합사료입니다.', 0),
(4,  'FD-CT-002', '번식우 유지 배합사료',     1, 'CATTLE',  'FEED',       25, 30000, 530,  80, 180, TRUE,  NULL, NULL, 0),
(5,  'FD-CT-003', '비육후기 고에너지 사료',   1, 'CATTLE',  'FEED',       25, 34000, 350,  60, 150, TRUE,  NULL, NULL, 0),
(6,  'FD-PG-002', '육성돈 배합사료',         2, 'PIG',     'FEED',       25, 26000, 680,  90, 180, TRUE,  NULL, NULL, 0),
(7,  'FD-PG-003', '임신돈 전용 사료',        2, 'PIG',     'FEED',       25, 27000, 300,  70, 180, TRUE,  NULL, NULL, 0),
(8,  'FD-PL-002', '육계 초기 사료',          3, 'POULTRY', 'FEED',       20, 25000, 240,  50,  90, TRUE,  NULL, NULL, 0),
(9,  'FD-PL-003', '육계 후기 사료',          3, 'POULTRY', 'FEED',       20, 23000, 720,  60,  90, TRUE,  NULL, NULL, 0),
(10, 'FD-PL-004', '산란오리 배합사료',       NULL, 'POULTRY', 'FEED',       25, 26000,  230,  40, 120, TRUE,  NULL, NULL, 0),
-- 영양제(보조제) : 포장 단위가 작고 유통기한이 길다
(11, 'SP-CT-001', '한우 비타민 영양제',      4, 'CATTLE',  'SUPPLEMENT',  5, 45000,  560,  30, 365, TRUE,  NULL, NULL, 0),
(13, 'SP-PG-001', '자돈 정장 영양제',        4, 'PIG',     'SUPPLEMENT',  5, 38000,  240,  20, 365, TRUE,  NULL, NULL, 0),
-- 단종(사용 중지) 품목: 재고가 안전재고보다 적지만 대시보드 알림에서 제외된다
(12, 'FD-CT-900', '구형 육성우 사료(단종)',  5, 'CATTLE',  'FEED',       25, 29000,  10,  50, 180, FALSE, NULL, NULL, 0);

-- ---------------------------------------------------------------------
-- 4. 로트 34건
--    lotId 1(D-5), 2(D-25), 5(D-18) → 대시보드 '유통기한 임박 알림'(30일 이내) 노출
-- ---------------------------------------------------------------------
--    ※ manufacturedDate = expirationDate - 품목의 shelfLifeDays 로 맞춰 두었다
--      (입고 시 자동 계산되는 값과 동일한 규칙)
INSERT INTO productLots (lotId, productId, lotNo, manufacturedDate, expirationDate, lotQuantity, version) VALUES
(1, 1, 'LOT-CT-2601', DATEADD('DAY', -175, CURRENT_DATE), DATEADD('DAY',   5, CURRENT_DATE),  20, 0),
(2, 1, 'LOT-CT-2602', DATEADD('DAY', -155, CURRENT_DATE), DATEADD('DAY',  25, CURRENT_DATE),  20, 0),
-- 입고 70+70+20+10 = 170 에서 주문 #9(20) · #5(10) 출고로 30 이 빠져 잔여 140
-- (stockMovements 를 처음부터 다시 재생해도 이 값이 나온다)
(3, 2, 'LOT-PG-2611', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY', 160, CURRENT_DATE), 140, 0),
(4, 2, 'LOT-PG-2612', DATEADD('DAY',  -10, CURRENT_DATE), DATEADD('DAY', 170, CURRENT_DATE), 150, 0),
(5, 3, 'LOT-PL-2621', DATEADD('DAY',  -72, CURRENT_DATE), DATEADD('DAY',  18, CURRENT_DATE),  80, 0),
-- 이미 유통기한이 지난 로트 (대시보드 '만료' 경고 + 출고 대상 제외 확인용)
(15, 3, 'LOT-PL-2620', DATEADD('DAY', -95, CURRENT_DATE), DATEADD('DAY',  -5, CURRENT_DATE),  20, 0),
-- 품목 4~12 의 로트 (products.totalStock 과 수량을 일치시킨다)
(6,  4, 'LOT-CT-2603', DATEADD('DAY',  -60, CURRENT_DATE), DATEADD('DAY', 120, CURRENT_DATE), 220, 0),
(7,  5, 'LOT-CT-2604', DATEADD('DAY',  -50, CURRENT_DATE), DATEADD('DAY', 100, CURRENT_DATE), 150, 0),
(8,  6, 'LOT-PG-2613', DATEADD('DAY',  -40, CURRENT_DATE), DATEADD('DAY', 140, CURRENT_DATE), 260, 0),
(9,  7, 'LOT-PG-2614', DATEADD('DAY',  -30, CURRENT_DATE), DATEADD('DAY', 150, CURRENT_DATE), 180, 0),
(10, 8, 'LOT-PL-2622', DATEADD('DAY',  -30, CURRENT_DATE), DATEADD('DAY',  60, CURRENT_DATE), 140, 0),
(11, 9, 'LOT-PL-2623', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY',  70, CURRENT_DATE), 310, 0),
(12, 10, 'LOT-PL-2631', DATEADD('DAY', -30, CURRENT_DATE), DATEADD('DAY',  90, CURRENT_DATE),  90, 0),
(14, 12, 'LOT-CT-2699', DATEADD('DAY', -140, CURRENT_DATE), DATEADD('DAY', 40, CURRENT_DATE),  10, 0),
-- 영양제 로트 (shelfLifeDays 365 규칙에 맞춰 제조일자 = 유통기한 - 365)
(13, 11, 'LOT-SP-2651', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY', 345, CURRENT_DATE),  70, 0),
(16, 13, 'LOT-SP-2661', DATEADD('DAY',  -30, CURRENT_DATE), DATEADD('DAY', 335, CURRENT_DATE),  30, 0),
-- 2D 도면에 적재 현황을 다양하게 보여주기 위한 추가 로트
-- (품목 1, 3, 12 는 안전재고 미달 / 단종 시나리오를 유지해야 하므로 추가하지 않는다)
(17,  4, 'LOT-CT-2605', DATEADD('DAY',  -60, CURRENT_DATE), DATEADD('DAY', 120, CURRENT_DATE), 180, 0),
(18,  5, 'LOT-CT-2606', DATEADD('DAY',  -40, CURRENT_DATE), DATEADD('DAY', 110, CURRENT_DATE), 200, 0),
(19,  6, 'LOT-PG-2615', DATEADD('DAY', -170, CURRENT_DATE), DATEADD('DAY',  10, CURRENT_DATE), 150, 0),
(20,  7, 'LOT-PG-2616', DATEADD('DAY',  -30, CURRENT_DATE), DATEADD('DAY', 150, CURRENT_DATE), 120, 0),
(21,  8, 'LOT-PL-2624', DATEADD('DAY',  -75, CURRENT_DATE), DATEADD('DAY',  15, CURRENT_DATE), 100, 0),
(22,  9, 'LOT-PL-2625', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY',  70, CURRENT_DATE), 230, 0),
(23, 10, 'LOT-PL-2632', DATEADD('DAY', -100, CURRENT_DATE), DATEADD('DAY',  20, CURRENT_DATE),  60, 0),
(24, 11, 'LOT-SP-2652', DATEADD('DAY',  -30, CURRENT_DATE), DATEADD('DAY', 335, CURRENT_DATE), 140, 0),
(25, 13, 'LOT-SP-2662', DATEADD('DAY',  -40, CURRENT_DATE), DATEADD('DAY', 325, CURRENT_DATE), 120, 0),
(26,  2, 'LOT-PG-2617', DATEADD('DAY',  -15, CURRENT_DATE), DATEADD('DAY', 165, CURRENT_DATE), 250, 0),
(27,  6, 'LOT-PG-2618', DATEADD('DAY',  -50, CURRENT_DATE), DATEADD('DAY', 130, CURRENT_DATE), 270, 0),
(28, 11, 'LOT-SP-2653', DATEADD('DAY',  -60, CURRENT_DATE), DATEADD('DAY', 305, CURRENT_DATE), 190, 0),
-- 저온(영양제) 구역 적재용 로트
(29, 13, 'LOT-SP-2663', DATEADD('DAY',  -25, CURRENT_DATE), DATEADD('DAY', 340, CURRENT_DATE),  90, 0),
(30, 11, 'LOT-SP-2654', DATEADD('DAY',  -35, CURRENT_DATE), DATEADD('DAY', 330, CURRENT_DATE), 160, 0),
(31,  4, 'LOT-CT-2607', DATEADD('DAY',  -45, CURRENT_DATE), DATEADD('DAY', 135, CURRENT_DATE), 130, 0),
(32, 10, 'LOT-PL-2633', DATEADD('DAY',  -55, CURRENT_DATE), DATEADD('DAY',  65, CURRENT_DATE),  80, 0),
-- 여러 센터에 나눠 적재되는 로트 (한 로트가 한 구역에만 있지 않다는 것을 보여준다)
(33,  2, 'LOT-PG-2619', DATEADD('DAY',  -22, CURRENT_DATE), DATEADD('DAY', 158, CURRENT_DATE), 150, 0),
(34,  9, 'LOT-PL-2626', DATEADD('DAY',  -18, CURRENT_DATE), DATEADD('DAY',  72, CURRENT_DATE), 180, 0);

-- ---------------------------------------------------------------------
-- 5. 최근 7일치 주문 15건
--    · PAID(오늘 2건)            → '신규 주문'
--    · READY(2건)                → '출고 대기'
--    · CANCELED(1건)             → 매출 집계에서 제외됨 (출고 전 취소)
--    · SHIPPED 1 + DELIVERED 9   → 10건 모두 OUTBOUND 이력을 가진다.
--      "배송 완료인데 창고에서 나간 기록이 없는" 주문을 남기지 않기 위한 것이다.
--      그래야 대시보드의 기간 입고/출고가 한쪽으로 쏠리지 않는다.
-- ---------------------------------------------------------------------
INSERT INTO orders (orderId, userId, totalPrice, discountPrice, finalPrice, shippingAddress, status, createdAt) VALUES
(1,  3, 320000,     0, 320000, '경북 상주시 낙동면 목장길 12',   'PAID',      DATEADD('HOUR', -2, CURRENT_TIMESTAMP)),
(2,  4, 560000, 20000, 540000, '충남 홍성군 갈산면 양돈로 45',   'PAID',      DATEADD('HOUR', -5, CURRENT_TIMESTAMP)),
(3,  5, 240000,     0, 240000, '전북 김제시 금산면 계사길 8',    'READY',     DATEADD('HOUR', -8, CURRENT_TIMESTAMP)),
(4,  3, 640000, 40000, 600000, '경북 상주시 낙동면 목장길 12',   'READY',     DATEADD('DAY',  -1, CURRENT_TIMESTAMP)),
(5,  4, 280000,     0, 280000, '충남 홍성군 갈산면 양돈로 45',   'SHIPPED',   DATEADD('DAY',  -1, CURRENT_TIMESTAMP)),
(6,  5, 480000,     0, 480000, '전북 김제시 금산면 계사길 8',    'DELIVERED', DATEADD('DAY',  -2, CURRENT_TIMESTAMP)),
(7,  3, 320000, 10000, 310000, '경북 상주시 낙동면 목장길 12',   'DELIVERED', DATEADD('DAY',  -2, CURRENT_TIMESTAMP)),
(8,  4, 840000, 40000, 800000, '충남 홍성군 갈산면 양돈로 45',   'DELIVERED', DATEADD('DAY',  -3, CURRENT_TIMESTAMP)),
(9,  5, 560000,     0, 560000, '전북 김제시 금산면 계사길 8',    'DELIVERED', DATEADD('DAY',  -3, CURRENT_TIMESTAMP)),
(10, 3, 240000,     0, 240000, '경북 상주시 낙동면 목장길 12',   'CANCELED',  DATEADD('DAY',  -3, CURRENT_TIMESTAMP)),
(11, 4, 720000, 20000, 700000, '충남 홍성군 갈산면 양돈로 45',   'DELIVERED', DATEADD('DAY',  -4, CURRENT_TIMESTAMP)),
(12, 5, 280000,     0, 280000, '전북 김제시 금산면 계사길 8',    'DELIVERED', DATEADD('DAY',  -5, CURRENT_TIMESTAMP)),
(13, 3, 480000,     0, 480000, '경북 상주시 낙동면 목장길 12',   'DELIVERED', DATEADD('DAY',  -5, CURRENT_TIMESTAMP)),
(14, 4, 960000, 60000, 900000, '충남 홍성군 갈산면 양돈로 45',   'DELIVERED', DATEADD('DAY',  -6, CURRENT_TIMESTAMP)),
(15, 5, 320000,     0, 320000, '전북 김제시 금산면 계사길 8',    'DELIVERED', DATEADD('DAY',  -6, CURRENT_TIMESTAMP));

-- 취소된 주문(#10)의 취소 정보.
-- 이 주문은 출고 이력(stockMovements)이 없으므로 '출고 전 취소' 사례다.
-- 재고가 차감된 적이 없어 되돌릴 재고도 없고, 취소 근거는 이 컬럼들뿐이다.
UPDATE orders
SET canceledAt     = DATEADD('DAY', -3, CURRENT_TIMESTAMP),
    cancelReason   = '고객 요청 - 사료 배합 변경으로 재주문',
    canceledById   = 1,
    canceledByName = '김책임'
WHERE orderId = 10;

-- ---------------------------------------------------------------------
-- 6. 주문 상세 (orderPrice = 주문 당시 단가)
-- ---------------------------------------------------------------------
INSERT INTO orderItems (orderItemId, orderId, productId, lotId, quantity, orderPrice) VALUES
(1,   1, 1, 1, 10, 32000),
(2,   2, 2, 3, 20, 28000),
(3,   3, 3, 5, 10, 24000),
(4,   4, 1, 2, 20, 32000),
(5,   5, 2, 3, 10, 28000),
(6,   6, 3, 5, 20, 24000),
(7,   7, 1, 1, 10, 32000),
(8,   8, 2, 4, 30, 28000),
(9,   9, 2, 3, 20, 28000),
(10, 10, 3, 5, 10, 24000),
(11, 11, 1, 2, 15, 32000),
(12, 11, 3, 5, 10, 24000),
(13, 12, 2, 4, 10, 28000),
(14, 13, 1, 1, 15, 32000),
(15, 14, 1, 2, 30, 32000),
(16, 15, 1, 1, 10, 32000);

-- ---------------------------------------------------------------------
-- 7. 창고 구역(Bin)  ※ 기준 정보(Master Data)
--    센터 5곳 × (보관 구역 + 입고/출고 대기 + 운송 중 가상 구역) = 51칸.
--    zone 코드가 축종을 뜻하므로 2D 도면만 봐도 그 센터의 운영 방향이 드러난다.
--      CT 소 · PG 돼지 · PL 가금 · COLD 영양제 · R 입고대기 · S 출고대기 · TRANSIT 운송중
--      예산 PL·PG / 김제 PL·PG / 의성 CT·PG·PL / 안성 CT·PG / 나주 PL
--    COLD 구역은 영양제를 취급하는 센터에만 둔다. 가금 전용인 나주에 두면
--    영구히 빈 칸이 되어 도면에 "쓰지 않는 구역"이 생긴다.
--
--    ★ 2D 도면 배치 좌표 (posX, posY, posWidth, posHeight)
--      창고 평면을 26열 x 14행 격자로 보고 사각형의 좌상단 위치와 크기를 지정한다.
--      1열 1행이 왼쪽 위. 출입구/벽/검수실은 건물 구조물이라 DB 가 아니라
--      WarehouseFacilityDto 상수로 관리한다. (5열은 하역장 통로이므로 비워둔다)
--
--    ★ binPurpose
--      STORAGE    : 보관 (적재율 통계 포함)
--      RECEIVING  : 입고 대기 / SHIPPING : 출고 대기 (통계 제외)
--      IN_TRANSIT : 센터 간 이관 중인 재고가 머무는 가상 구역 (P3a).
--                   물리 공간이 아니므로 maxCapacity 0 · 도면에 그리지 않는다.
-- ---------------------------------------------------------------------
INSERT INTO warehouseBins (binId, binCode, centerId, zone, binPurpose, rack, binLevel, maxCapacity, posX, posY, posWidth, posHeight, active, memo, createdAt) VALUES
-- 충남 예산 센터 (C1-YS) · 충남 서북부 · 양계 · 양돈 중심
(1, 'YS-PL-01', 1, 'PL', 'STORAGE', '01', 1, 200, 6, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(2, 'YS-PL-02', 1, 'PL', 'STORAGE', '01', 2, 200, 6, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(3, 'YS-PL-03', 1, 'PL', 'STORAGE', '02', 1, 200, 12, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(4, 'YS-PL-04', 1, 'PL', 'STORAGE', '02', 2, 200, 12, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(5, 'YS-PG-01', 1, 'PG', 'STORAGE', '03', 1, 250, 18, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(6, 'YS-PG-02', 1, 'PG', 'STORAGE', '03', 2, 250, 18, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(7, 'YS-PG-03', 1, 'PG', 'STORAGE', '04', 1, 250, 23, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(8, 'YS-PG-04', 1, 'PG', 'STORAGE', '04', 2, 250, 23, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(9, 'YS-COLD-01', 1, 'COLD', 'STORAGE', '05', 1, 200, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(10, 'YS-R-01', 1, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(11, 'YS-S-01', 1, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
-- 전북 김제 센터 (C2-GJ) · 전북 서부 · 새만금권 · 닭 · 오리 · 돼지 중심
(12, 'GJ-PL-01', 2, 'PL', 'STORAGE', '01', 1, 200, 6, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(13, 'GJ-PL-02', 2, 'PL', 'STORAGE', '01', 2, 200, 6, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(14, 'GJ-PL-03', 2, 'PL', 'STORAGE', '02', 1, 200, 12, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(15, 'GJ-PL-04', 2, 'PL', 'STORAGE', '02', 2, 200, 12, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(16, 'GJ-PG-01', 2, 'PG', 'STORAGE', '03', 1, 250, 18, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(17, 'GJ-PG-02', 2, 'PG', 'STORAGE', '03', 2, 250, 18, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -240, CURRENT_TIMESTAMP)),
(18, 'GJ-COLD-01', 2, 'COLD', 'STORAGE', '05', 1, 250, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(19, 'GJ-R-01', 2, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(20, 'GJ-S-01', 2, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
-- 경북 의성 센터 (C3-US) · 안동 · 의성 · 경북 북부 · 소 · 돼지 · 조류 균형형
(21, 'US-CT-01', 3, 'CT', 'STORAGE', '01', 1, 200, 6, 1, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(22, 'US-CT-02', 3, 'CT', 'STORAGE', '01', 2, 200, 6, 6, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(23, 'US-PG-01', 3, 'PG', 'STORAGE', '02', 1, 300, 12, 1, 5, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(24, 'US-PG-02', 3, 'PG', 'STORAGE', '02', 2, 300, 12, 6, 5, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(25, 'US-PL-01', 3, 'PL', 'STORAGE', '03', 1, 200, 18, 1, 4, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(26, 'US-PL-02', 3, 'PL', 'STORAGE', '03', 2, 200, 18, 6, 4, 5, FALSE, '천장 누수 보수 중 사용 중지', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(27, 'US-COLD-01', 3, 'COLD', 'STORAGE', '05', 1, 750, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(28, 'US-R-01', 3, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(29, 'US-S-01', 3, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
-- 경기 안성 센터 (C4-AS) · 경기 남부 · 충북 서부 · 소 · 돼지 강화형
(30, 'AS-CT-01', 4, 'CT', 'STORAGE', '01', 1, 400, 6, 1, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(31, 'AS-CT-02', 4, 'CT', 'STORAGE', '01', 2, 400, 6, 6, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(32, 'AS-CT-03', 4, 'CT', 'STORAGE', '02', 1, 400, 12, 1, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(33, 'AS-CT-04', 4, 'CT', 'STORAGE', '02', 2, 400, 12, 6, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(34, 'AS-PG-01', 4, 'PG', 'STORAGE', '03', 1, 250, 18, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(35, 'AS-PG-02', 4, 'PG', 'STORAGE', '03', 2, 250, 18, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(36, 'AS-PG-03', 4, 'PG', 'STORAGE', '04', 1, 250, 23, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(37, 'AS-PG-04', 4, 'PG', 'STORAGE', '04', 2, 250, 23, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(38, 'AS-COLD-01', 4, 'COLD', 'STORAGE', '05', 1, 500, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
(39, 'AS-R-01', 4, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(40, 'AS-S-01', 4, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
-- 전남 나주 센터 (C5-NJ) · 전남 중서부 · 닭 · 오리 최우선 (영양제 품목이 없어 저온 구역 없음)
(41, 'NJ-PL-01', 5, 'PL', 'STORAGE', '01', 1, 250, 6, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(42, 'NJ-PL-02', 5, 'PL', 'STORAGE', '01', 2, 250, 6, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(43, 'NJ-PL-03', 5, 'PL', 'STORAGE', '02', 1, 250, 12, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(44, 'NJ-PL-04', 5, 'PL', 'STORAGE', '02', 2, 250, 12, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(45, 'NJ-R-01', 5, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
(46, 'NJ-S-01', 5, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
-- 운송 중(IN_TRANSIT) 가상 구역 : 센터당 1개.
--   물리적 공간이 아니라 적재 한도를 검증하지 않고 2D 도면에도 그리지 않는다.
(47, 'TRANSIT-C1-YS', 1, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(48, 'TRANSIT-C2-GJ', 2, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(49, 'TRANSIT-C3-US', 3, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(50, 'TRANSIT-C4-AS', 4, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(51, 'TRANSIT-C5-NJ', 5, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -140, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 8. 재고 (로트 × 구역)
--    ★ 정합성 규칙 (반드시 지켜야 함)
--      · products.totalStock = 해당 품목 모든 로트의 inventories.quantity 합계
--      · productLots.lotQuantity = 해당 로트의 inventories.quantity 합계
--      이 값이 어긋나면 "전체 재고는 있는데 출고 가능 재고가 부족"한 현상이 발생한다.
--    · 각 구역의 합계는 warehouseBins.maxCapacity 를 넘지 않아야 한다
-- ---------------------------------------------------------------------
INSERT INTO inventories (inventoryId, lotId, binId, quantity, updatedAt, version) VALUES
(1, 1, 30, 20, DATEADD('DAY', -28, CURRENT_TIMESTAMP), 0),   -- AS-CT-01 (20/400 = 5%)
(2, 2, 31, 20, DATEADD('DAY', -5, CURRENT_TIMESTAMP), 0),   -- AS-CT-02 (20/400 = 5%)
(3, 3, 34, 70, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 0),   -- AS-PG-01 (70/250 = 28%)
(4, 3, 35, 70, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 0),   -- AS-PG-02 (70/250 = 28%)
(5, 4, 36, 75, DATEADD('DAY', -19, CURRENT_TIMESTAMP), 0),   -- AS-PG-03 (75/250 = 30%)
(6, 4, 37, 75, DATEADD('DAY', -19, CURRENT_TIMESTAMP), 0),   -- AS-PG-04 (75/250 = 30%)
(7, 5, 1, 80, DATEADD('DAY', -26, CURRENT_TIMESTAMP), 0),   -- YS-PL-01 (80/200 = 40%)
-- 아래 두 줄은 movementId 72 의 구역 이동(AS-CT-04 → AS-CT-03, 40포대)이 반영된 상태다.
-- 입고 직후에는 74 / 73 이었고, 이동 후 114 / 33 이 되었다.
(8, 6, 32, 114, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- AS-CT-03 (114/400 = 29%)
(9, 6, 33, 33, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- AS-CT-04 (33/400 = 8%)
(10, 6, 30, 73, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- AS-CT-01 (73/400 = 18%)
(11, 7, 31, 75, DATEADD('DAY', -10, CURRENT_TIMESTAMP), 0),   -- AS-CT-02 (75/400 = 19%)
(12, 7, 33, 75, DATEADD('DAY', -10, CURRENT_TIMESTAMP), 0),   -- AS-CT-04 (75/400 = 19%)
(13, 8, 34, 87, DATEADD('DAY', -17, CURRENT_TIMESTAMP), 0),   -- AS-PG-01 (87/250 = 35%)
(14, 8, 35, 87, DATEADD('DAY', -17, CURRENT_TIMESTAMP), 0),   -- AS-PG-02 (87/250 = 35%)
(15, 8, 36, 86, DATEADD('DAY', -17, CURRENT_TIMESTAMP), 0),   -- AS-PG-03 (86/250 = 34%)
(16, 9, 23, 90, DATEADD('DAY', -24, CURRENT_TIMESTAMP), 0),   -- US-PG-01 (90/300 = 30%)
(17, 9, 24, 90, DATEADD('DAY', -24, CURRENT_TIMESTAMP), 0),   -- US-PG-02 (90/300 = 30%)
(18, 10, 41, 70, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 0),   -- NJ-PL-01 (70/250 = 28%)
(19, 10, 42, 70, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 0),   -- NJ-PL-02 (70/250 = 28%)
(20, 11, 2, 95, DATEADD('DAY', -8, CURRENT_TIMESTAMP), 0),   -- YS-PL-02 (95/200 = 48%)
(21, 11, 3, 95, DATEADD('DAY', -8, CURRENT_TIMESTAMP), 0),   -- YS-PL-03 (95/200 = 48%)
-- 로트 12 는 김제로 90 입고된 뒤 60 을 예산으로 이관했다(movementId 77 · 78).
-- 그래서 출발지 30 + 도착지 60 = 90 으로 나뉘어 있다. (inventoryId 57 이 도착분)
(22, 12, 12, 30, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- GJ-PL-01 (30/200 = 15%)
(23, 13, 38, 70, DATEADD('DAY', -22, CURRENT_TIMESTAMP), 0),   -- AS-COLD-01 (70/500 = 14%)
(24, 14, 32, 10, DATEADD('DAY', -6, CURRENT_TIMESTAMP), 0),   -- AS-CT-03 (10/400 = 2%)
(25, 15, 13, 20, DATEADD('DAY', -6, CURRENT_TIMESTAMP), 0),   -- GJ-PL-02 (20/200 = 10%)
(26, 16, 9, 30, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- YS-COLD-01 (30/200 = 15%)
(27, 17, 32, 90, DATEADD('DAY', -20, CURRENT_TIMESTAMP), 0),   -- AS-CT-03 (90/400 = 22%)
-- 90 중 60 을 출고 대기 구역으로 피킹해 두었다(movementId 84) → 30 만 남아 있다
(28, 17, 30, 30, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 0),   -- AS-CT-01 (30/400 = 8%)
(29, 18, 31, 100, DATEADD('DAY', -27, CURRENT_TIMESTAMP), 0),   -- AS-CT-02 (100/400 = 25%)
(30, 18, 33, 100, DATEADD('DAY', -27, CURRENT_TIMESTAMP), 0),   -- AS-CT-04 (100/400 = 25%)
(31, 19, 5, 75, DATEADD('DAY', -4, CURRENT_TIMESTAMP), 0),   -- YS-PG-01 (75/250 = 30%)
(32, 19, 6, 75, DATEADD('DAY', -4, CURRENT_TIMESTAMP), 0),   -- YS-PG-02 (75/250 = 30%)
(33, 20, 7, 60, DATEADD('DAY', -11, CURRENT_TIMESTAMP), 0),   -- YS-PG-03 (60/250 = 24%)
(34, 20, 8, 60, DATEADD('DAY', -11, CURRENT_TIMESTAMP), 0),   -- YS-PG-04 (60/250 = 24%)
(35, 21, 43, 100, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- NJ-PL-03 (100/250 = 40%)
(36, 22, 44, 77, DATEADD('DAY', -25, CURRENT_TIMESTAMP), 0),   -- NJ-PL-04 (77/250 = 31%)
(37, 22, 41, 77, DATEADD('DAY', -25, CURRENT_TIMESTAMP), 0),   -- NJ-PL-01 (77/250 = 31%)
(38, 22, 42, 76, DATEADD('DAY', -25, CURRENT_TIMESTAMP), 0),   -- NJ-PL-02 (76/250 = 30%)
(39, 23, 4, 60, DATEADD('DAY', -2, CURRENT_TIMESTAMP), 0),   -- YS-PL-04 (60/200 = 30%)
(40, 24, 27, 140, DATEADD('DAY', -9, CURRENT_TIMESTAMP), 0),   -- US-COLD-01 (140/750 = 19%)
(41, 25, 18, 120, DATEADD('DAY', -16, CURRENT_TIMESTAMP), 0),   -- GJ-COLD-01 (120/250 = 48%)
(42, 26, 16, 125, DATEADD('DAY', -23, CURRENT_TIMESTAMP), 0),   -- GJ-PG-01 (125/250 = 50%)
(43, 26, 17, 125, DATEADD('DAY', -23, CURRENT_TIMESTAMP), 0),   -- GJ-PG-02 (125/250 = 50%)
(44, 27, 7, 90, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),   -- YS-PG-03 (90/250 = 36%)
(45, 27, 8, 90, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),   -- YS-PG-04 (90/250 = 36%)
(46, 27, 5, 90, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),   -- YS-PG-01 (90/250 = 36%)
(47, 28, 38, 190, DATEADD('DAY', -7, CURRENT_TIMESTAMP), 0),   -- AS-COLD-01 (190/500 = 38%)
(48, 29, 27, 90, DATEADD('DAY', -14, CURRENT_TIMESTAMP), 0),   -- US-COLD-01 (90/750 = 12%)
(49, 30, 27, 160, DATEADD('DAY', -21, CURRENT_TIMESTAMP), 0),   -- US-COLD-01 (160/750 = 21%)
(50, 31, 21, 65, DATEADD('DAY', -28, CURRENT_TIMESTAMP), 0),   -- US-CT-01 (65/200 = 32%)
(51, 31, 22, 65, DATEADD('DAY', -28, CURRENT_TIMESTAMP), 0),   -- US-CT-02 (65/200 = 32%)
(52, 32, 25, 80, DATEADD('DAY', -4, CURRENT_TIMESTAMP), 0),   -- US-PL-01 (80/200 = 40%)
(53, 33, 23, 75, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 0),   -- US-PG-01 (75/300 = 25%)
(54, 33, 24, 75, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 0),   -- US-PG-02 (75/300 = 25%)
(55, 34, 14, 90, DATEADD('DAY', -19, CURRENT_TIMESTAMP), 0),   -- GJ-PL-03 (90/200 = 45%)
(56, 34, 15, 90, DATEADD('DAY', -19, CURRENT_TIMESTAMP), 0),   -- GJ-PL-04 (90/200 = 45%)
-- 센터 간 이관(movementId 78)으로 예산에 도착한 로트 12 의 60포대
(57, 12, 4, 60, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- YS-PL-04 (60/200 = 30%)
-- ------------------------------------------------------------------
-- 대기 구역 재고 : 적재율에서 빠지지만 실물은 창고에 있다
--
--   이 두 줄이 없으면 "보관 구역 재고" 와 "센터 전체 재고" 가 항상 같아져서,
--   둘을 구분해 둔 설계(적재율의 분자 · 2D 도면의 '+ 대기 구역 N포대 별도' ·
--   센터 카드의 '보관 N + 대기 K')가 화면에 한 번도 나타나지 않는다.
--   H2 인메모리라 손으로 입고해 봐도 재시작하면 사라진다.
-- ------------------------------------------------------------------
-- 출고 대기 : READY 주문을 위해 보관 구역에서 피킹해 모아 둔 물량 (movementId 84)
(58, 17, 40, 60, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 0),   -- AS-S-01 (60/400) ※ 적재율 제외
-- 입고 대기 : 어제 도착해 검수를 기다리는 물량 (movementId 85)
--   검수 전이므로 출고 후보에서 제외된다. '구역 간 이동' 으로 보관 구역에 넣어야
--   가용 재고가 된다. 그래서 품목 9 는 "전체 재고는 720인데 출고 가능은 600" 이다.
(59, 11, 10, 120, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 0);   -- YS-R-01 (120/300) ※ 적재율 제외

-- ---------------------------------------------------------------------
-- 9. 재고 이력 83건
--    ★ 이 목록을 위에서부터 그대로 재생하면 inventories 가 정확히 나온다.
--      (로트 단위뿐 아니라 '로트 × 구역' 단위까지 맞춰 두었다. 그래서 이력 추적
--       화면에서 "이력상 수량과 실제 재고가 다르다" 는 경고가 뜨지 않는다)
--
--    · INBOUND      : 발주 입고. 오래된 것부터 시간순으로 쌓여 있다
--    · OUTBOUND     : 주문 출고. SHIPPED · DELIVERED 주문 10건 전부에 붙어 있고
--                     orderId 를 남겨 출고 취소 시 되돌릴 로트 · 구역의 근거가 된다
--    · DISPOSAL     : 유통기한 경과분 폐기
--    · MOVE         : 같은 센터 안에서 구역만 이동 (총량 불변)
--    · TRANSFER_OUT/IN : 센터 간 이관. 두 건의 합이 0 이므로 전국 총량은 변하지 않는다
--                        (출발 센터 소속 '운송 중' 가상 구역을 경유한다 - P3a)
--
--    ★ 최근 7일 구간은 입고 +310 / 출고 -180 · 폐기 -15 로 잡아 두었다.
--      한쪽만 있으면 대시보드의 기간 카드가 "물류가 멈춘 창고" 처럼 보인다.
--      출고에 대응하는 입고는 25일 이상 앞선 날짜로 넣어, 기간 집계는 움직이는데
--      최종 재고 배치는 그대로 유지되게 했다.
-- ---------------------------------------------------------------------
INSERT INTO stockMovements (movementId, movementType, productId, lotId, binId, fromBinId, quantity, orderId, memo, userId, userName, createdAt) VALUES
(1, 'INBOUND', 13, 25, 18, NULL, 120, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -105, CURRENT_TIMESTAMP)),
(2, 'INBOUND', 11, 24, 27, NULL, 140, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -98, CURRENT_TIMESTAMP)),
(3, 'INBOUND', 9, 11, 2, NULL, 95, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -97, CURRENT_TIMESTAMP)),
(4, 'INBOUND', 9, 11, 3, NULL, 95, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -97, CURRENT_TIMESTAMP)),
(5, 'INBOUND', 3, 15, 13, NULL, 20, NULL, '유통기한 경과 재고(폐기 대기)', 2, '이사원', DATEADD('DAY', -95, CURRENT_TIMESTAMP)),
(6, 'INBOUND', 3, 15, 13, NULL, 15, NULL, '유통기한 경과 재고(폐기 대기)', 2, '이사원', DATEADD('DAY', -95, CURRENT_TIMESTAMP)),
(7, 'INBOUND', 10, 23, 4, NULL, 60, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -91, CURRENT_TIMESTAMP)),
(8, 'INBOUND', 8, 10, 41, NULL, 70, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -90, CURRENT_TIMESTAMP)),
(9, 'INBOUND', 8, 10, 42, NULL, 70, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -90, CURRENT_TIMESTAMP)),
(10, 'INBOUND', 9, 22, 44, NULL, 77, NULL, '대량 발주 입고(1/3)', 2, '이사원', DATEADD('DAY', -84, CURRENT_TIMESTAMP)),
(11, 'INBOUND', 9, 22, 41, NULL, 77, NULL, '대량 발주 입고(2/3)', 2, '이사원', DATEADD('DAY', -84, CURRENT_TIMESTAMP)),
(12, 'INBOUND', 9, 22, 42, NULL, 76, NULL, '대량 발주 입고(3/3)', 2, '이사원', DATEADD('DAY', -84, CURRENT_TIMESTAMP)),
(13, 'INBOUND', 7, 9, 23, NULL, 90, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -83, CURRENT_TIMESTAMP)),
(14, 'INBOUND', 7, 9, 24, NULL, 90, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -83, CURRENT_TIMESTAMP)),
(15, 'INBOUND', 9, 34, 14, NULL, 90, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -78, CURRENT_TIMESTAMP)),
(16, 'INBOUND', 9, 34, 15, NULL, 90, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -78, CURRENT_TIMESTAMP)),
(17, 'INBOUND', 6, 8, 34, NULL, 87, NULL, '대량 발주 입고(1/3)', 2, '이사원', DATEADD('DAY', -76, CURRENT_TIMESTAMP)),
(18, 'INBOUND', 6, 8, 35, NULL, 87, NULL, '대량 발주 입고(2/3)', 2, '이사원', DATEADD('DAY', -76, CURRENT_TIMESTAMP)),
(19, 'INBOUND', 6, 8, 36, NULL, 86, NULL, '대량 발주 입고(3/3)', 2, '이사원', DATEADD('DAY', -76, CURRENT_TIMESTAMP)),
(20, 'INBOUND', 2, 33, 23, NULL, 75, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -71, CURRENT_TIMESTAMP)),
(21, 'INBOUND', 2, 33, 24, NULL, 75, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -71, CURRENT_TIMESTAMP)),
(22, 'INBOUND', 7, 20, 7, NULL, 60, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -70, CURRENT_TIMESTAMP)),
(23, 'INBOUND', 7, 20, 8, NULL, 60, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -70, CURRENT_TIMESTAMP)),
(24, 'INBOUND', 5, 7, 31, NULL, 75, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -69, CURRENT_TIMESTAMP)),
(25, 'INBOUND', 5, 7, 33, NULL, 75, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -69, CURRENT_TIMESTAMP)),
(26, 'INBOUND', 6, 19, 5, NULL, 75, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -63, CURRENT_TIMESTAMP)),
(27, 'INBOUND', 6, 19, 6, NULL, 75, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -63, CURRENT_TIMESTAMP)),
(28, 'INBOUND', 4, 6, 32, NULL, 74, NULL, '대량 발주 입고(1/3)', 2, '이사원', DATEADD('DAY', -62, CURRENT_TIMESTAMP)),
(29, 'INBOUND', 4, 6, 33, NULL, 73, NULL, '대량 발주 입고(2/3)', 2, '이사원', DATEADD('DAY', -62, CURRENT_TIMESTAMP)),
(30, 'INBOUND', 4, 6, 30, NULL, 73, NULL, '대량 발주 입고(3/3)', 2, '이사원', DATEADD('DAY', -62, CURRENT_TIMESTAMP)),
(31, 'INBOUND', 4, 31, 21, NULL, 65, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -57, CURRENT_TIMESTAMP)),
(32, 'INBOUND', 4, 31, 22, NULL, 65, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -57, CURRENT_TIMESTAMP)),
(33, 'INBOUND', 5, 18, 31, NULL, 100, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -56, CURRENT_TIMESTAMP)),
(34, 'INBOUND', 5, 18, 33, NULL, 100, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -56, CURRENT_TIMESTAMP)),
(35, 'INBOUND', 3, 5, 1, NULL, 80, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -55, CURRENT_TIMESTAMP)),
(36, 'INBOUND', 11, 30, 27, NULL, 160, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -50, CURRENT_TIMESTAMP)),
(37, 'INBOUND', 4, 17, 32, NULL, 90, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -49, CURRENT_TIMESTAMP)),
(38, 'INBOUND', 4, 17, 30, NULL, 90, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -49, CURRENT_TIMESTAMP)),
(39, 'INBOUND', 2, 4, 36, NULL, 75, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -48, CURRENT_TIMESTAMP)),
(40, 'INBOUND', 2, 4, 37, NULL, 75, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -48, CURRENT_TIMESTAMP)),
(41, 'INBOUND', 13, 29, 27, NULL, 90, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -43, CURRENT_TIMESTAMP)),
(42, 'INBOUND', 2, 3, 34, NULL, 70, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -41, CURRENT_TIMESTAMP)),
(43, 'INBOUND', 2, 3, 35, NULL, 70, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -41, CURRENT_TIMESTAMP)),
(44, 'INBOUND', 11, 28, 38, NULL, 190, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -36, CURRENT_TIMESTAMP)),
(45, 'INBOUND', 1, 2, 31, NULL, 20, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -34, CURRENT_TIMESTAMP)),
(46, 'INBOUND', 1, 1, 30, NULL, 10, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -31, CURRENT_TIMESTAMP)),
(47, 'INBOUND', 1, 2, 31, NULL, 30, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -31, CURRENT_TIMESTAMP)),
(48, 'INBOUND', 1, 1, 30, NULL, 15, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -30, CURRENT_TIMESTAMP)),
(49, 'INBOUND', 2, 4, 36, NULL, 10, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -30, CURRENT_TIMESTAMP)),
(50, 'INBOUND', 1, 2, 31, NULL, 15, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(51, 'INBOUND', 3, 5, 1, NULL, 10, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(52, 'INBOUND', 6, 27, 7, NULL, 90, NULL, '대량 발주 입고(1/3)', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(53, 'INBOUND', 6, 27, 8, NULL, 90, NULL, '대량 발주 입고(2/3)', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(54, 'INBOUND', 6, 27, 5, NULL, 90, NULL, '대량 발주 입고(3/3)', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(55, 'INBOUND', 2, 3, 34, NULL, 20, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -28, CURRENT_TIMESTAMP)),
(56, 'INBOUND', 2, 4, 36, NULL, 30, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -28, CURRENT_TIMESTAMP)),
(57, 'INBOUND', 1, 1, 30, NULL, 20, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -27, CURRENT_TIMESTAMP)),
(58, 'INBOUND', 1, 1, 30, NULL, 10, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -27, CURRENT_TIMESTAMP)),
(59, 'INBOUND', 3, 5, 1, NULL, 20, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -27, CURRENT_TIMESTAMP)),
(60, 'INBOUND', 2, 3, 34, NULL, 10, NULL, '주문 대응 입고', 2, '이사원', DATEADD('DAY', -26, CURRENT_TIMESTAMP)),
(61, 'INBOUND', 2, 26, 16, NULL, 125, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -22, CURRENT_TIMESTAMP)),
(62, 'INBOUND', 2, 26, 17, NULL, 125, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -22, CURRENT_TIMESTAMP)),
(63, 'INBOUND', 11, 13, 38, NULL, 70, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -21, CURRENT_TIMESTAMP)),
(64, 'OUTBOUND', 1, 1, 30, NULL, 10, 15, '주문 #15 FEFO 출고', 1, '김책임', DATEADD('DAY', -6, CURRENT_TIMESTAMP)),
(65, 'OUTBOUND', 1, 2, 31, NULL, 30, 14, '주문 #14 FEFO 출고', 1, '김책임', DATEADD('DAY', -6, CURRENT_TIMESTAMP)),
(66, 'OUTBOUND', 1, 1, 30, NULL, 15, 13, '주문 #13 FEFO 출고', 1, '김책임', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(67, 'OUTBOUND', 2, 4, 36, NULL, 10, 12, '주문 #12 FEFO 출고', 1, '김책임', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(68, 'INBOUND', 12, 14, 32, NULL, 10, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(69, 'DISPOSAL', 3, 15, 13, NULL, 15, NULL, '유통기한 경과분 폐기', 1, '김책임', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(70, 'OUTBOUND', 1, 2, 31, NULL, 15, 11, '주문 #11 FEFO 출고', 1, '김책임', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
(71, 'OUTBOUND', 3, 5, 1, NULL, 10, 11, '주문 #11 FEFO 출고', 1, '김책임', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
(72, 'MOVE', 4, 6, 32, 33, 40, NULL, '적재 재배치 (AS-CT-04 → AS-CT-03)', 1, '김책임', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
(73, 'OUTBOUND', 2, 3, 34, NULL, 20, 9, '주문 #9 FEFO 출고', 1, '김책임', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(74, 'OUTBOUND', 2, 4, 36, NULL, 30, 8, '주문 #8 FEFO 출고', 1, '김책임', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(75, 'INBOUND', 10, 12, 12, NULL, 90, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(76, 'INBOUND', 10, 32, 25, NULL, 80, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
-- 센터 간 이관 : 김제로 입고된 로트 12 중 60 을 예산 요청분으로 넘긴다.
-- 두 건이 한 쌍이고 합이 0 이므로 전국 총 재고는 변하지 않는다.
-- TRANSFER_OUT 의 도착지(binId 48)는 '출발 센터(김제) 소속' 운송 중 가상 구역이다.
(77, 'TRANSFER_OUT', 10, 12, 48, 12, 60, NULL, '[센터 이관] 전북 김제 센터 GJ-PL-01 → 충남 예산 센터 YS-PL-04', 1, '김책임', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(78, 'TRANSFER_IN', 10, 12, 4, 48, 60, NULL, '[센터 이관] 전북 김제 센터 GJ-PL-01 → 충남 예산 센터 YS-PL-04', 1, '김책임', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(79, 'OUTBOUND', 1, 1, 30, NULL, 10, 7, '주문 #7 FEFO 출고', 1, '김책임', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
(80, 'OUTBOUND', 3, 5, 1, NULL, 20, 6, '주문 #6 FEFO 출고', 1, '김책임', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
(81, 'INBOUND', 13, 16, 9, NULL, 30, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
(82, 'INBOUND', 8, 21, 43, NULL, 100, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
(83, 'OUTBOUND', 2, 3, 34, NULL, 10, 5, '주문 #5 FEFO 출고', 1, '김책임', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
-- 대기 구역으로 들어간 재고 (위 inventoryId 58 · 59 의 근거)
(84, 'MOVE', 4, 17, 40, 30, 60, NULL, '출고 대기 구역으로 피킹 (AS-CT-01 → AS-S-01)', 1, '김책임', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
(85, 'INBOUND', 9, 11, 10, NULL, 120, NULL, '정기 발주 입고 - 검수 대기', 2, '이사원', DATEADD('DAY', -1, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 10. 농장 고객사 20곳  ※ 기준 정보(Master Data)
--    B2C 담당 팀원의 고객 농장 모듈에서 옮겨온 데이터다.
--    옮기면서 이 프로젝트의 기준에 맞춰 세 가지를 바꿨다.
--
--      1) 담당 창고를 centers 로 참조한다.
--         원본은 별도 warehouse 테이블(W01~W05)을 참조했다. 같은 물류 거점이
--         두 테이블로 갈라지면 구역 · 재고 · 이력 · 이관 · 2D 도면이 모두
--         매달린 centers 와 어긋난다.
--      2) 축종을 AnimalType enum 으로 바꿨다.
--         원본은 '조류(닭/오리)' 같은 자유 문자열이라 products.animalType 과
--         맞춰볼 수 없었다. 두 축이 같은 값 체계를 써야 비교가 성립한다.
--      3) distanceKm 을 centers 좌표 기준으로 다시 계산했다(하버사인).
--         원본은 팀원 쪽 창고 좌표 기준이어서 최대 17km 차이가 났다(나주).
--
--    farmCode 의 W01~W05 는 팀원 창고 코드 체계지만 그대로 둔다.
--    팀원 모듈이 farmCode 를 자연 키로 삼아 데이터를 병합하므로 코드를 고치면
--    같은 농장이 두 건으로 늘어난다. 센터를 가리키는 일은 centerId 가 한다.
--
--    ■ 거래 보류(PAUSED) 2곳을 일부러 심는다
--      원본 데이터는 20곳이 전부 ACTIVE 였다. 그러면 거래 상태 필터가 한 번도
--      걸러내지 못하고, 월 사료량 합산이 '거래 중만' 세는지 확인할 수 없다
--      (전부 ACTIVE 라 필터가 있으나 없으나 결과가 같다).
--      대기 구역 재고가 0 이어서 관련 UI 가 한 번도 렌더링되지 않았던 것과
--      같은 문제다. 규칙을 어긴 데이터는 아니지만 기능을 보여주지 못한다.
--
--      전체 20곳 / 거래 중 18곳 / 거래 보류 2곳
--      월 예상 사료량 : 전체 35,970  ·  거래 중만 31,140  (차이 4,830)
--      사육 규모 합계 : 450,940 마리
-- ---------------------------------------------------------------------
INSERT INTO farmCustomers (farmCustomerId, farmCode, farmName, representativeName, phone,
                           postalCode, address, latitude, longitude, animalType,
                           livestockCount, monthlyFeedQuantity, preferredFeed,
                           recurringDeliveryDay, centerId, distanceKm, status, notes, createdAt) VALUES
-- 충남 예산 센터 (C1-YS) 담당 농장
(1, 'F-W01-01', '예산 고덕 한우농장', '김한우', '010-0000-1001',
 '32400', '충남 예산군 고덕면 농장권역', 36.742, 126.704, 'CATTLE',
 180, 720, '한우 성장 플러스', 1, 1, 6.8, 'ACTIVE', '송아지·육성우 혼합 사육', DATEADD('DAY', -280, CURRENT_TIMESTAMP)),
(2, 'F-W01-02', '당진 합덕 양돈농장', '이양돈', '010-0000-1002',
 '31800', '충남 당진시 합덕읍 농장권역', 36.79, 126.76, 'PIG',
 2400, 1850, '육성돈 그로우', 15, 1, 2.2, 'ACTIVE', '육성돈 중심 월 2회 공급', DATEADD('DAY', -274, CURRENT_TIMESTAMP)),
(3, 'F-W01-03', '홍성 광천 산란계농장', '박산란', '010-0000-1003',
 '32290', '충남 홍성군 광천읍 농장권역', 36.5, 126.62, 'POULTRY',
 60000, 2380, '산란계 산란 피크', 1, 1, 33.1, 'PAUSED', '사료 단가 재협상 중 · 공급 일시 보류', DATEADD('DAY', -268, CURRENT_TIMESTAMP)),
(4, 'F-W01-04', '아산 둔포 육계농장', '최육계', '010-0000-1004',
 '31400', '충남 아산시 둔포면 농장권역', 36.93, 127.04, 'POULTRY',
 42000, 2100, '육계 후기 사료', 15, 1, 29.7, 'ACTIVE', '출하 주기별 분할 배송', DATEADD('DAY', -262, CURRENT_TIMESTAMP)),
-- 전북 김제 센터 (C2-GJ) 담당 농장
(5, 'F-W02-01', '김제 백산 육계농장', '정백산', '010-0000-2001',
 '54320', '전북 김제시 백산면 농장권역', 35.84, 126.89, 'POULTRY',
 72000, 3100, '육계 전기 사료', 3, 2, 3.5, 'ACTIVE', '육계 전기·후기 혼합 공급', DATEADD('DAY', -250, CURRENT_TIMESTAMP)),
(6, 'F-W02-02', '익산 왕궁 양돈농장', '강왕궁', '010-0000-2002',
 '54570', '전북 익산시 왕궁면 농장권역', 35.97, 127.08, 'PIG',
 3100, 2200, '비육돈 피니셔', 17, 2, 25.6, 'ACTIVE', '비육돈 대량 수요 고객', DATEADD('DAY', -244, CURRENT_TIMESTAMP)),
(7, 'F-W02-03', '정읍 태인 한우농장', '윤태인', '010-0000-2003',
 '56110', '전북 정읍시 태인면 농장권역', 35.65, 126.93, 'CATTLE',
 230, 850, '한우 비육 후기', 3, 2, 18.7, 'ACTIVE', '비육 후기 사료 비중 높음', DATEADD('DAY', -238, CURRENT_TIMESTAMP)),
(8, 'F-W02-04', '부안 계화 오리농장', '한계화', '010-0000-2004',
 '56300', '전북 부안군 계화면 농장권역', 35.76, 126.7, 'POULTRY',
 28000, 1980, '육용오리 그로워', 17, 2, 16.6, 'ACTIVE', '오리 그로워 정기 공급', DATEADD('DAY', -232, CURRENT_TIMESTAMP)),
-- 경북 의성 센터 (C3-US) 담당 농장
(9, 'F-W03-01', '의성 단촌 한우농장', '신단촌', '010-0000-3001',
 '37320', '경북 의성군 단촌면 농장권역', 36.42, 128.7, 'CATTLE',
 260, 940, '한우 비육 전기', 5, 3, 5.8, 'ACTIVE', '거점 인접 우선 배송', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(10, 'F-W03-02', '안동 풍산 양돈농장', '조풍산', '010-0000-3002',
 '36620', '경북 안동시 풍산읍 농장권역', 36.58, 128.58, 'PIG',
 2700, 1960, '양돈 장건강 프로', 19, 3, 18.7, 'ACTIVE', '장건강 사료 고정 거래', DATEADD('DAY', -214, CURRENT_TIMESTAMP)),
(11, 'F-W03-03', '영주 안정 산란계농장', '배안정', '010-0000-3003',
 '36050', '경북 영주시 안정면 농장권역', 36.83, 128.56, 'POULTRY',
 52000, 2260, '산란계 육성 사료', 5, 3, 46.3, 'ACTIVE', '육성·산란 전환 수요', DATEADD('DAY', -208, CURRENT_TIMESTAMP)),
(12, 'F-W03-04', '상주 함창 육계농장', '오함창', '010-0000-3004',
 '37110', '경북 상주시 함창읍 농장권역', 36.57, 128.18, 'POULTRY',
 36000, 1720, '육계 후기 사료', 19, 3, 44.0, 'ACTIVE', '계약 갱신 대기 시연 데이터', DATEADD('DAY', -202, CURRENT_TIMESTAMP)),
-- 경기 안성 센터 (C4-AS) 담당 농장
(13, 'F-W04-01', '안성 미양 낙농목장', '서미양', '010-0000-4001',
 '17590', '경기 안성시 미양면 농장권역', 36.97, 127.21, 'CATTLE',
 190, 780, '젖소 착유우 밸런스', 8, 4, 3.7, 'ACTIVE', '착유우 전용 사료 정기 공급', DATEADD('DAY', -190, CURRENT_TIMESTAMP)),
(14, 'F-W04-02', '이천 설성 양돈농장', '임설성', '010-0000-4002',
 '17410', '경기 이천시 설성면 농장권역', 37.13, 127.52, 'PIG',
 3400, 2450, '비육돈 프리미엄 골드', 22, 4, 29.8, 'PAUSED', '축사 증축 공사로 납품 일시 중단', DATEADD('DAY', -184, CURRENT_TIMESTAMP)),
(15, 'F-W04-03', '평택 청북 육계농장', '문청북', '010-0000-4003',
 '17790', '경기 평택시 청북읍 농장권역', 37.02, 126.92, 'POULTRY',
 68000, 2880, '육계 전기 사료', 8, 4, 27.2, 'ACTIVE', '주 단위 출하 일정 연계', DATEADD('DAY', -178, CURRENT_TIMESTAMP)),
(16, 'F-W04-04', '음성 금왕 한우농장', '유금왕', '010-0000-4004',
 '27630', '충북 음성군 금왕읍 농장권역', 37.0, 127.59, 'CATTLE',
 210, 820, '한우 프리미엄 마블', 22, 4, 32.4, 'ACTIVE', '비육 후기 집중 관리', DATEADD('DAY', -172, CURRENT_TIMESTAMP)),
-- 전남 나주 센터 (C5-NJ) 담당 농장
(17, 'F-W05-01', '나주 문평 오리농장', '남문평', '010-0000-5001',
 '58200', '전남 나주시 문평면 농장권역', 35.05, 126.85, 'POULTRY',
 45000, 3200, '육용오리 그로워', 10, 5, 17.9, 'ACTIVE', '거점 인접 최우선 배송', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(18, 'F-W05-02', '영암 신북 한우농장', '고신북', '010-0000-5002',
 '58400', '전남 영암군 신북면 농장권역', 34.89, 126.69, 'CATTLE',
 170, 690, '한우 성장 플러스', 24, 5, 23.3, 'ACTIVE', '육성우 중심 고객', DATEADD('DAY', -148, CURRENT_TIMESTAMP)),
(19, 'F-W05-03', '함평 학교 양돈농장', '송학교', '010-0000-5003',
 '57160', '전남 함평군 학교면 농장권역', 35.03, 126.54, 'PIG',
 2100, 1580, '자돈 스타터 2호', 10, 5, 13.4, 'ACTIVE', '자돈·육성돈 혼합 공급', DATEADD('DAY', -130, CURRENT_TIMESTAMP)),
(20, 'F-W05-04', '장흥 부산 육계농장', '장부산', '010-0000-5004',
 '59300', '전남 장흥군 부산면 농장권역', 34.72, 126.9, 'POULTRY',
 33000, 1510, '가금 프리미엄 믹스', 24, 5, 47.3, 'ACTIVE', '계절 계약 보류 시연 데이터', DATEADD('DAY', -106, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 11. 불량 기록 7건
--    "검수에서 불량이 나오면 그 다음" 을 보여주는 데이터.
--
--    관리번호(defectNo)는 로트번호와 같이 고정 문자열로 둔다. 상대 날짜로 만들면
--    실행 월에 따라 접두어가 바뀌어 문서 · 테스트에서 이 번호를 가리킬 수 없다.
--    (운영 중 등록되는 번호는 DefectService 가 그 달 최댓값 +1 로 발급한다)
--
--    상태를 섞어 둔 이유 — 화면이 세 상태를 모두 렌더링해야 확인이 된다.
--      · 격리(QUARANTINED)   3건 : #1 #3 #7
--      · 검사 중(INSPECTING) 2건 : #2 #6
--      · 처리 완료(RESOLVED) 2건 : #4 #5
--
--    #1(12일 전) · #2(9일 전) 는 7일이 지난 미처리 건이다 →
--    목록 위쪽 '방치된 불량' 경고와 행 강조(table-warning)가 이 두 건으로 보인다.
--
--    #5 는 제조사가 없는 품목(productId 10)의 로트다 → 제조사별 집계에 '미등록' 이 뜬다.
--    #6 은 센터 간 이관 중 파손이라 binId 가 NULL 이다 → 구역을 특정할 수 없는 경우.
--      (이때는 구역으로 단계를 추정할 수 없으므로 stage 를 TRANSFER 로 직접 지정한다)
--
--    발견 단계 분포: 입고 검사 4 / 보관 1 / 출고 검사 1 / 이관 1 → 입고 적발률 57%
--    (입고에서 잡는 비중이 높아야 좋다. 늦게 발견될수록 보관 자리와 시간을 낭비한 것)
--
--    ※ 이 표는 재고 수량을 바꾸지 않는다. #4 는 공급업체 반품으로 처리했지만
--      inventories 는 그대로다 — 실제 차감은 재고 폐기 화면에서 해야 한다.
--      불량 관리 화면의 '재고 차감 대기' 카드가 이 1건을 센다.
-- ---------------------------------------------------------------------
INSERT INTO defectRecords (defectId, defectNo, lotId, binId, quantity, defectType, stage, status, resolution, memo, resolutionMemo, reportedByName, resolvedByName, createdAt, resolvedAt) VALUES
-- 방치 1 : 김제 센터 입고 검수 구역, 12일째 격리 상태
(1, 'DF-2607-001',  3, 19, 12, 'DAMAGE',         'RECEIVING', 'QUARANTINED', NULL,
 '하차 중 파렛트 하단 12포대 포장 찢어짐. 내용물 일부 유출.', NULL,
 '이사원', NULL, DATEADD('DAY', -12, CURRENT_TIMESTAMP), NULL),
-- 방치 2 : 예산 센터 입고 검수 구역, 9일째 검사 중
(2, 'DF-2607-002',  5, 10,  8, 'FOREIGN_MATTER', 'RECEIVING', 'INSPECTING',  NULL,
 '개봉 검사 중 이물(비닐 조각) 확인. 동일 파렛트 전량 재검사 중.', NULL,
 '이사원', NULL, DATEADD('DAY',  -9, CURRENT_TIMESTAMP), NULL),
-- 보관 중 발견 : 입고 검수는 통과했는데 나중에 문제가 드러난 경우
(3, 'DF-2607-003',  8,  5, 20, 'WET',            'STORAGE',   'QUARANTINED', NULL,
 '천장 누수로 하단 2단에 습기 유입. 보관 위치 변경 필요.', NULL,
 '이사원', NULL, DATEADD('DAY',  -5, CURRENT_TIMESTAMP), NULL),
-- 처리 완료 1 : 공급업체 반품 → 재고 차감이 아직 남아 있다
(4, 'DF-2607-004', 10, 28, 15, 'SPECIFICATION',  'RECEIVING', 'RESOLVED',    'SUPPLIER_RETURN',
 '표기 조단백 함량과 시험성적서 수치가 맞지 않음.',
 '반품 접수번호 R-2607-08 · 제조사 회수 차량 배차 완료.',
 '이사원', '김책임', DATEADD('DAY',  -3, CURRENT_TIMESTAMP), DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
-- 처리 완료 2 : 재작업 후 정상 복귀 (제조사 미등록 품목)
(5, 'DF-2607-005', 12, 45,  6, 'DAMAGE',         'RECEIVING', 'RESOLVED',    'REWORK',
 '외포장만 손상. 내부 포장과 내용물은 이상 없음.',
 '외포장 교체 후 보관 구역으로 이동 완료.',
 '이사원', '김책임', DATEADD('DAY',  -2, CURRENT_TIMESTAMP), DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
-- 이관 중 파손 : 어느 구역에서 났다고 말할 수 없어 binId 가 NULL
(6, 'DF-2607-006',  6, NULL, 10, 'DAMAGE',        'TRANSFER',  'INSPECTING',  NULL,
 '예산 → 안성 이관 차량 적재 붕괴. 도착 후 수량 · 상태 확인 중.', NULL,
 '이사원', NULL, DATEADD('DAY',  -1, CURRENT_TIMESTAMP), NULL),
-- 출고 검사에서 발견 : 가장 늦게 잡힌 경우 (보관 기간을 이미 낭비했다)
(7, 'DF-2607-007', 11, 11,  4, 'CONTAMINATION',  'SHIPPING',  'QUARANTINED', NULL,
 '출고 적재 중 변색 확인. 해당 파렛트 출고 보류.', NULL,
 '이사원', NULL, DATEADD('HOUR', -4, CURRENT_TIMESTAMP), NULL);

-- ---------------------------------------------------------------------
-- 12. IDENTITY 시퀀스 재시작
--    (명시적 ID 로 INSERT 했으므로, 이후 JPA 저장 시 PK 충돌을 막는다)
-- ---------------------------------------------------------------------
ALTER TABLE centers ALTER COLUMN centerId RESTART WITH 6;
ALTER TABLE users ALTER COLUMN userId RESTART WITH 6;
ALTER TABLE products ALTER COLUMN productId RESTART WITH 14;
ALTER TABLE productLots ALTER COLUMN lotId RESTART WITH 35;
ALTER TABLE orders ALTER COLUMN orderId RESTART WITH 16;
ALTER TABLE orderItems ALTER COLUMN orderItemId RESTART WITH 17;
ALTER TABLE warehouseBins ALTER COLUMN binId RESTART WITH 52;
ALTER TABLE inventories ALTER COLUMN inventoryId RESTART WITH 60;
ALTER TABLE stockMovements ALTER COLUMN movementId RESTART WITH 86;
ALTER TABLE farmCustomers ALTER COLUMN farmCustomerId RESTART WITH 21;
ALTER TABLE manufacturers ALTER COLUMN manufacturerId RESTART WITH 6;
ALTER TABLE defectRecords ALTER COLUMN defectId RESTART WITH 8;

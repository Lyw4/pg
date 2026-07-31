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
INSERT INTO centers (centerId, centerCode, name, region, address, note, active, createdAt) VALUES
(1, 'C1-YS', '충남 예산 센터', '충남 서북부', '충남 예산군 고덕면 몽곡리 667 일대', '양계 · 양돈 중심', TRUE, DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(2, 'C2-GJ', '전북 김제 센터', '전북 서부 · 새만금권', '전북 김제시 흥사동 서흥농공단지 외곽', '닭 · 오리 · 돼지 중심', TRUE, DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(3, 'C3-US', '경북 의성 센터', '안동 · 의성 · 경북 북부', '경북 의성군 단촌면 세촌리 국도 5호선 축', '소 · 돼지 · 조류 균형형', TRUE, DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(4, 'C4-AS', '경기 안성 센터', '경기 남부 · 충북 서부', '경기 안성시 미양면 계륵리 · 구수리', '소 · 돼지 강화형', TRUE, DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(5, 'C5-NJ', '전남 나주 센터', '전남 중서부', '전남 나주시 문평면 옥당리', '닭 · 오리 최우선', TRUE, DATEADD('DAY', -140, CURRENT_TIMESTAMP));

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
-- 2. 품목 13종  ※ 기준 정보(Master Data)
--    · 취급 축종은 CATTLE(소) / PIG(돼지) / POULTRY(조류: 닭·오리) 3종으로 고정
--    · 취급 품목 구분은 FEED(사료) / SUPPLEMENT(영양제) 2종으로 고정
--    · productId 1, 3 은 안전재고 미달 → 대시보드 '안전재고 알림' 노출
--    · productId 12 는 사용 중지(단종) → 미달이지만 알림에서 제외
--    · 10건 초과라서 품목 목록 화면의 페이징을 바로 확인할 수 있다
-- ---------------------------------------------------------------------
INSERT INTO products (productId, productCode, name, animalType, productType, weightKg, price, totalStock, safetyStock, shelfLifeDays, active, imageUrl, description, version) VALUES
(1,  'FD-CT-001', '프리미엄 육성우 배합사료', 'CATTLE',  'FEED',       25, 32000,  40,  50, 180, TRUE,  '/images/feed-cattle.png',  '육성기 한우의 골격 형성을 돕는 고단백 배합사료입니다.', 0),
(2,  'FD-PG-001', '자돈용 배합사료',         'PIG',     'FEED',       20, 28000, 690, 100, 180, TRUE,  '/images/feed-pig.png',     '이유 후 자돈의 소화 흡수율을 높인 프리스타터 사료입니다.', 0),
-- productId 3 : 정상 로트 80 + 만료 로트 20 = 100 (안전재고 120 미달 유지)
(3,  'FD-PL-001', '산란계 전용 배합사료',     'POULTRY', 'FEED',       25, 24000, 100, 120,  90, TRUE,  '/images/feed-chicken.png', '산란율 향상을 위한 칼슘 강화 배합사료입니다.', 0),
(4,  'FD-CT-002', '번식우 유지 배합사료',     'CATTLE',  'FEED',       25, 30000, 530,  80, 180, TRUE,  NULL, NULL, 0),
(5,  'FD-CT-003', '비육후기 고에너지 사료',   'CATTLE',  'FEED',       25, 34000, 350,  60, 150, TRUE,  NULL, NULL, 0),
(6,  'FD-PG-002', '육성돈 배합사료',         'PIG',     'FEED',       25, 26000, 680,  90, 180, TRUE,  NULL, NULL, 0),
(7,  'FD-PG-003', '임신돈 전용 사료',        'PIG',     'FEED',       25, 27000, 300,  70, 180, TRUE,  NULL, NULL, 0),
(8,  'FD-PL-002', '육계 초기 사료',          'POULTRY', 'FEED',       20, 25000, 240,  50,  90, TRUE,  NULL, NULL, 0),
(9,  'FD-PL-003', '육계 후기 사료',          'POULTRY', 'FEED',       20, 23000, 600,  60,  90, TRUE,  NULL, NULL, 0),
(10, 'FD-PL-004', '산란오리 배합사료',       'POULTRY', 'FEED',       25, 26000,  230,  40, 120, TRUE,  NULL, NULL, 0),
-- 영양제(보조제) : 포장 단위가 작고 유통기한이 길다
(11, 'SP-CT-001', '한우 비타민 영양제',      'CATTLE',  'SUPPLEMENT',  5, 45000,  560,  30, 365, TRUE,  NULL, NULL, 0),
(13, 'SP-PG-001', '자돈 정장 영양제',        'PIG',     'SUPPLEMENT',  5, 38000,  240,  20, 365, TRUE,  NULL, NULL, 0),
-- 단종(사용 중지) 품목: 재고가 안전재고보다 적지만 대시보드 알림에서 제외된다
(12, 'FD-CT-900', '구형 육성우 사료(단종)',  'CATTLE',  'FEED',       25, 29000,  10,  50, 180, FALSE, NULL, NULL, 0);

-- ---------------------------------------------------------------------
-- 3. 로트 5건
--    lotId 1(D-5), 2(D-25), 5(D-18) → 대시보드 '유통기한 임박 알림'(30일 이내) 노출
-- ---------------------------------------------------------------------
--    ※ manufacturedDate = expirationDate - 품목의 shelfLifeDays 로 맞춰 두었다
--      (입고 시 자동 계산되는 값과 동일한 규칙)
INSERT INTO productLots (lotId, productId, lotNo, manufacturedDate, expirationDate, lotQuantity, version) VALUES
(1, 1, 'LOT-CT-2601', DATEADD('DAY', -175, CURRENT_DATE), DATEADD('DAY',   5, CURRENT_DATE),  20, 0),
(2, 1, 'LOT-CT-2602', DATEADD('DAY', -155, CURRENT_DATE), DATEADD('DAY',  25, CURRENT_DATE),  20, 0),
-- 입고 100 + 50 = 150 에서 주문 #5 출고로 10 이 빠져 잔여 140 (stockMovements 와 일치)
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
(11, 9, 'LOT-PL-2623', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY',  70, CURRENT_DATE), 190, 0),
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
-- 제2창고 저온 구역 적재용 로트
(29, 13, 'LOT-SP-2663', DATEADD('DAY',  -25, CURRENT_DATE), DATEADD('DAY', 340, CURRENT_DATE),  90, 0),
(30, 11, 'LOT-SP-2654', DATEADD('DAY',  -35, CURRENT_DATE), DATEADD('DAY', 330, CURRENT_DATE), 160, 0),
(31,  4, 'LOT-CT-2607', DATEADD('DAY',  -45, CURRENT_DATE), DATEADD('DAY', 135, CURRENT_DATE), 130, 0),
(32, 10, 'LOT-PL-2633', DATEADD('DAY',  -55, CURRENT_DATE), DATEADD('DAY',  65, CURRENT_DATE),  80, 0),
-- 제2창고 상온 구역(N) 적재용 로트
(33,  2, 'LOT-PG-2619', DATEADD('DAY',  -22, CURRENT_DATE), DATEADD('DAY', 158, CURRENT_DATE), 150, 0),
(34,  9, 'LOT-PL-2626', DATEADD('DAY',  -18, CURRENT_DATE), DATEADD('DAY',  72, CURRENT_DATE), 180, 0);

-- ---------------------------------------------------------------------
-- 4. 최근 7일치 주문 15건
--    · PAID(오늘 2건)   → '신규 주문'
--    · READY(2건)       → '출고 대기'
--    · CANCELED(1건)    → 매출 집계에서 제외됨
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
-- 5. 주문 상세 (orderPrice = 주문 당시 단가)
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
-- 6. 창고 구역(Bin)  ※ 기준 정보(Master Data)
--    · 제1창고(WH1) 20칸 : 상온 - R / H / A / B / C / D / E 구역
--    · 제2창고(WH2) 20칸 : R / S / K 구역 + 상온 보관(N) 8칸 + 저온 보관(COLD) 6칸
--
--    ★ 2D 도면 배치 좌표 (posX, posY, posWidth, posHeight)
--      창고 평면을 24열 x 18행 격자로 보고 사각형의 좌상단 위치와 크기를 지정한다.
--      1열 1행이 왼쪽 위. 출입구/벽/검수실은 건물 구조물이라 DB 가 아니라
--      WarehouseFacilityDto 상수로 관리한다. (5열은 하역장 벽이므로 비워둔다)
--
--    ★ binPurpose
--      STORAGE   : 보관 (적재율 통계 포함)
--      RECEIVING : 입고 대기 / SHIPPING : 출고 대기 (통계 제외)
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
(18, 'GJ-COLD-01', 2, 'COLD', 'STORAGE', '05', 1, 200, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(19, 'GJ-R-01', 2, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(20, 'GJ-S-01', 2, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
-- 경북 의성 센터 (C3-US) · 안동 · 의성 · 경북 북부 · 소 · 돼지 · 조류 균형형
(21, 'US-CT-01', 3, 'CT', 'STORAGE', '01', 1, 200, 6, 1, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(22, 'US-CT-02', 3, 'CT', 'STORAGE', '01', 2, 200, 6, 6, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(23, 'US-PG-01', 3, 'PG', 'STORAGE', '02', 1, 300, 12, 1, 5, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(24, 'US-PG-02', 3, 'PG', 'STORAGE', '02', 2, 300, 12, 6, 5, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(25, 'US-PL-01', 3, 'PL', 'STORAGE', '03', 1, 200, 18, 1, 4, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(26, 'US-PL-02', 3, 'PL', 'STORAGE', '03', 2, 200, 18, 6, 4, 5, FALSE, '천장 누수 보수 중 사용 중지', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(27, 'US-COLD-01', 3, 'COLD', 'STORAGE', '05', 1, 650, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(28, 'US-R-01', 3, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(29, 'US-S-01', 3, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
-- 경기 안성 센터 (C4-AS) · 경기 남부 · 충북 서부 · 소 · 돼지 강화형
(30, 'AS-CT-01', 4, 'CT', 'STORAGE', '01', 1, 350, 6, 1, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(31, 'AS-CT-02', 4, 'CT', 'STORAGE', '01', 2, 350, 6, 6, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(32, 'AS-CT-03', 4, 'CT', 'STORAGE', '02', 1, 350, 12, 1, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(33, 'AS-CT-04', 4, 'CT', 'STORAGE', '02', 2, 350, 12, 6, 5, 5, TRUE, '소 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(34, 'AS-PG-01', 4, 'PG', 'STORAGE', '03', 1, 250, 18, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(35, 'AS-PG-02', 4, 'PG', 'STORAGE', '03', 2, 250, 18, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(36, 'AS-PG-03', 4, 'PG', 'STORAGE', '04', 1, 250, 23, 1, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(37, 'AS-PG-04', 4, 'PG', 'STORAGE', '04', 2, 250, 23, 6, 4, 5, TRUE, '돼지 사료 보관 구역', DATEADD('DAY', -160, CURRENT_TIMESTAMP)),
(38, 'AS-COLD-01', 4, 'COLD', 'STORAGE', '05', 1, 450, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
(39, 'AS-R-01', 4, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(40, 'AS-S-01', 4, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
-- 전남 나주 센터 (C5-NJ) · 전남 중서부 · 닭 · 오리 최우선
(41, 'NJ-PL-01', 5, 'PL', 'STORAGE', '01', 1, 200, 6, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(42, 'NJ-PL-02', 5, 'PL', 'STORAGE', '01', 2, 200, 6, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(43, 'NJ-PL-03', 5, 'PL', 'STORAGE', '02', 1, 200, 12, 1, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(44, 'NJ-PL-04', 5, 'PL', 'STORAGE', '02', 2, 200, 12, 6, 5, 5, TRUE, '가금 사료 보관 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(45, 'NJ-COLD-01', 5, 'COLD', 'STORAGE', '05', 1, 200, 6, 11, 5, 3, TRUE, '저온 보관(영양제) 구역', DATEADD('DAY', -100, CURRENT_TIMESTAMP)),
(46, 'NJ-R-01', 5, 'R', 'RECEIVING', '01', 1, 300, 1, 3, 4, 3, TRUE, '입고 검수 전 대기 구역', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
(47, 'NJ-S-01', 5, 'S', 'SHIPPING', '01', 1, 400, 1, 6, 4, 3, TRUE, '출고 직전 집합 구역', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
-- 운송 중(IN_TRANSIT) 가상 구역 : 센터당 1개.
--   물리적 공간이 아니라 적재 한도를 검증하지 않고 2D 도면에도 그리지 않는다.
(48, 'TRANSIT-C1-YS', 1, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(49, 'TRANSIT-C2-GJ', 2, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -260, CURRENT_TIMESTAMP)),
(50, 'TRANSIT-C3-US', 3, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -220, CURRENT_TIMESTAMP)),
(51, 'TRANSIT-C4-AS', 4, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(52, 'TRANSIT-C5-NJ', 5, 'TRANSIT', 'IN_TRANSIT', NULL, NULL, 0, 1, 1, 1, 1, TRUE, '센터 간 이관 중인 재고가 머무는 가상 구역 (시스템 자동 생성)', DATEADD('DAY', -140, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 7. 재고 (로트 × 구역)
--    ★ 정합성 규칙 (반드시 지켜야 함)
--      · products.totalStock = 해당 품목 모든 로트의 inventories.quantity 합계
--      · productLots.lotQuantity = 해당 로트의 inventories.quantity 합계
--      이 값이 어긋나면 "전체 재고는 있는데 출고 가능 재고가 부족"한 현상이 발생한다.
--    · 각 구역의 합계는 warehouseBins.maxCapacity 를 넘지 않아야 한다
-- ---------------------------------------------------------------------
INSERT INTO inventories (inventoryId, lotId, binId, quantity, updatedAt, version) VALUES
(1, 1, 30, 20, DATEADD('DAY', -28, CURRENT_TIMESTAMP), 0),   -- AS-CT-01 (20/350)
(2, 2, 31, 20, DATEADD('DAY', -5, CURRENT_TIMESTAMP), 0),   -- AS-CT-02 (20/350)
(3, 3, 34, 140, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 0),   -- AS-PG-01 (140/250)
(4, 4, 35, 150, DATEADD('DAY', -19, CURRENT_TIMESTAMP), 0),   -- AS-PG-02 (150/250)
(5, 5, 1, 80, DATEADD('DAY', -26, CURRENT_TIMESTAMP), 0),   -- YS-PL-01 (80/200)
(6, 6, 32, 220, DATEADD('DAY', -3, CURRENT_TIMESTAMP), 0),   -- AS-CT-03 (220/350)
(7, 7, 33, 150, DATEADD('DAY', -10, CURRENT_TIMESTAMP), 0),   -- AS-CT-04 (150/350)
(8, 8, 36, 250, DATEADD('DAY', -17, CURRENT_TIMESTAMP), 0),   -- AS-PG-03 (250/250)
(9, 8, 37, 10, DATEADD('DAY', -17, CURRENT_TIMESTAMP), 0),   -- AS-PG-04 (10/250)
(10, 9, 23, 180, DATEADD('DAY', -24, CURRENT_TIMESTAMP), 0),   -- US-PG-01 (180/300)
(11, 10, 41, 140, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 0),   -- NJ-PL-01 (140/200)
(12, 11, 2, 190, DATEADD('DAY', -8, CURRENT_TIMESTAMP), 0),   -- YS-PL-02 (190/200)
(13, 12, 12, 90, DATEADD('DAY', -15, CURRENT_TIMESTAMP), 0),   -- GJ-PL-01 (90/200)
(14, 13, 38, 70, DATEADD('DAY', -22, CURRENT_TIMESTAMP), 0),   -- AS-COLD-01 (70/450)
(15, 14, 30, 10, DATEADD('DAY', -29, CURRENT_TIMESTAMP), 0),   -- AS-CT-01 (10/350)
(16, 15, 13, 20, DATEADD('DAY', -6, CURRENT_TIMESTAMP), 0),   -- GJ-PL-02 (20/200)
(17, 16, 9, 30, DATEADD('DAY', -13, CURRENT_TIMESTAMP), 0),   -- YS-COLD-01 (30/200)
(18, 17, 31, 180, DATEADD('DAY', -20, CURRENT_TIMESTAMP), 0),   -- AS-CT-02 (180/350)
(19, 18, 30, 200, DATEADD('DAY', -27, CURRENT_TIMESTAMP), 0),   -- AS-CT-01 (200/350)
(20, 19, 5, 150, DATEADD('DAY', -4, CURRENT_TIMESTAMP), 0),   -- YS-PG-01 (150/250)
(21, 20, 6, 120, DATEADD('DAY', -11, CURRENT_TIMESTAMP), 0),   -- YS-PG-02 (120/250)
(22, 21, 42, 100, DATEADD('DAY', -18, CURRENT_TIMESTAMP), 0),   -- NJ-PL-02 (100/200)
(23, 22, 43, 200, DATEADD('DAY', -25, CURRENT_TIMESTAMP), 0),   -- NJ-PL-03 (200/200)
(24, 22, 44, 30, DATEADD('DAY', -25, CURRENT_TIMESTAMP), 0),   -- NJ-PL-04 (30/200)
(25, 23, 3, 60, DATEADD('DAY', -2, CURRENT_TIMESTAMP), 0),   -- YS-PL-03 (60/200)
(26, 24, 27, 140, DATEADD('DAY', -9, CURRENT_TIMESTAMP), 0),   -- US-COLD-01 (140/650)
(27, 25, 18, 120, DATEADD('DAY', -16, CURRENT_TIMESTAMP), 0),   -- GJ-COLD-01 (120/200)
(28, 26, 16, 250, DATEADD('DAY', -23, CURRENT_TIMESTAMP), 0),   -- GJ-PG-01 (250/250)
(29, 27, 7, 250, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),   -- YS-PG-03 (250/250)
(30, 27, 8, 20, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),   -- YS-PG-04 (20/250)
(31, 28, 38, 190, DATEADD('DAY', -7, CURRENT_TIMESTAMP), 0),   -- AS-COLD-01 (190/450)
(32, 29, 27, 90, DATEADD('DAY', -14, CURRENT_TIMESTAMP), 0),   -- US-COLD-01 (90/650)
(33, 30, 27, 160, DATEADD('DAY', -21, CURRENT_TIMESTAMP), 0),   -- US-COLD-01 (160/650)
(34, 31, 21, 130, DATEADD('DAY', -28, CURRENT_TIMESTAMP), 0),   -- US-CT-01 (130/200)
(35, 32, 25, 80, DATEADD('DAY', -5, CURRENT_TIMESTAMP), 0),   -- US-PL-01 (80/200)
(36, 33, 24, 150, DATEADD('DAY', -12, CURRENT_TIMESTAMP), 0),   -- US-PG-02 (150/300)
(37, 34, 14, 180, DATEADD('DAY', -19, CURRENT_TIMESTAMP), 0);   -- GJ-PL-03 (180/200)   -- N-03    180/200 = 90% 포화

-- ---------------------------------------------------------------------
-- 8. 재고 이력
--    입고(INBOUND) 는 위 재고와 대응하고, 여기에 아래 시나리오가 섞여 있다.
--      · OUTBOUND     : 주문 #5 출고 → 출고 취소 시 복구 근거 (orderId 를 남긴다)
--      · DISPOSAL     : 유통기한 경과분 폐기
--      · MOVE         : 같은 센터 안에서 구역만 이동 (sign 0, 총량 불변)
--      · TRANSFER_OUT/IN : 센터 간 이관. 두 건의 합이 0 이므로 전국 총량은 변하지 않는다
--                          (운송 중 가상 구역을 경유한다)
-- ---------------------------------------------------------------------
INSERT INTO stockMovements (movementId, movementType, productId, lotId, binId, fromBinId, quantity, orderId, memo, userId, userName, createdAt) VALUES
(1, 'INBOUND', 13, 25, 18, NULL, 120, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -105, CURRENT_TIMESTAMP)),
(2, 'INBOUND', 10, 12, 12, NULL, 90, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -104, CURRENT_TIMESTAMP)),
(3, 'INBOUND', 11, 24, 27, NULL, 140, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -98, CURRENT_TIMESTAMP)),
(4, 'INBOUND', 9, 11, 2, NULL, 190, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -97, CURRENT_TIMESTAMP)),
(5, 'INBOUND', 3, 15, 13, NULL, 20, NULL, '유통기한 경과 재고(폐기 대기)', 2, '이사원', DATEADD('DAY', -95, CURRENT_TIMESTAMP)),
(6, 'INBOUND', 3, 15, 13, NULL, 15, NULL, '유통기한 경과 재고(폐기 대기)', 2, '이사원', DATEADD('DAY', -95, CURRENT_TIMESTAMP)),
(7, 'INBOUND', 10, 23, 3, NULL, 60, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -91, CURRENT_TIMESTAMP)),
(8, 'INBOUND', 8, 10, 41, NULL, 140, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -90, CURRENT_TIMESTAMP)),
(9, 'INBOUND', 9, 22, 43, NULL, 200, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -84, CURRENT_TIMESTAMP)),
(10, 'INBOUND', 9, 22, 44, NULL, 30, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -84, CURRENT_TIMESTAMP)),
(11, 'INBOUND', 7, 9, 23, NULL, 180, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -83, CURRENT_TIMESTAMP)),
(12, 'INBOUND', 9, 34, 14, NULL, 180, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -78, CURRENT_TIMESTAMP)),
(13, 'INBOUND', 8, 21, 42, NULL, 100, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -77, CURRENT_TIMESTAMP)),
(14, 'INBOUND', 6, 8, 36, NULL, 250, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -76, CURRENT_TIMESTAMP)),
(15, 'INBOUND', 6, 8, 37, NULL, 10, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -76, CURRENT_TIMESTAMP)),
(16, 'INBOUND', 2, 33, 24, NULL, 150, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -71, CURRENT_TIMESTAMP)),
(17, 'INBOUND', 7, 20, 6, NULL, 120, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -70, CURRENT_TIMESTAMP)),
(18, 'INBOUND', 5, 7, 33, NULL, 150, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -69, CURRENT_TIMESTAMP)),
(19, 'INBOUND', 10, 32, 25, NULL, 80, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -64, CURRENT_TIMESTAMP)),
(20, 'INBOUND', 6, 19, 5, NULL, 150, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -63, CURRENT_TIMESTAMP)),
(21, 'INBOUND', 4, 6, 32, NULL, 220, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -62, CURRENT_TIMESTAMP)),
(22, 'INBOUND', 4, 31, 21, NULL, 130, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -57, CURRENT_TIMESTAMP)),
(23, 'INBOUND', 5, 18, 30, NULL, 200, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -56, CURRENT_TIMESTAMP)),
(24, 'INBOUND', 3, 5, 1, NULL, 80, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -55, CURRENT_TIMESTAMP)),
(25, 'INBOUND', 11, 30, 27, NULL, 160, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -50, CURRENT_TIMESTAMP)),
(26, 'INBOUND', 4, 17, 31, NULL, 180, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -49, CURRENT_TIMESTAMP)),
(27, 'INBOUND', 2, 4, 35, NULL, 150, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -48, CURRENT_TIMESTAMP)),
(28, 'INBOUND', 13, 29, 27, NULL, 90, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -43, CURRENT_TIMESTAMP)),
(29, 'INBOUND', 13, 16, 9, NULL, 30, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -42, CURRENT_TIMESTAMP)),
(30, 'INBOUND', 2, 3, 34, NULL, 140, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -41, CURRENT_TIMESTAMP)),
(31, 'INBOUND', 2, 3, 34, NULL, 10, NULL, '주문 대응 추가 입고', 2, '이사원', DATEADD('DAY', -41, CURRENT_TIMESTAMP)),
(32, 'INBOUND', 11, 28, 38, NULL, 190, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -36, CURRENT_TIMESTAMP)),
(33, 'INBOUND', 1, 2, 31, NULL, 20, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -34, CURRENT_TIMESTAMP)),
(34, 'INBOUND', 6, 27, 7, NULL, 250, NULL, '대량 발주 입고(1/2)', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(35, 'INBOUND', 6, 27, 8, NULL, 20, NULL, '대량 발주 입고(2/2)', 2, '이사원', DATEADD('DAY', -29, CURRENT_TIMESTAMP)),
(36, 'INBOUND', 12, 14, 30, NULL, 10, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -28, CURRENT_TIMESTAMP)),
(37, 'INBOUND', 1, 1, 30, NULL, 20, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -27, CURRENT_TIMESTAMP)),
(38, 'INBOUND', 2, 26, 16, NULL, 250, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -22, CURRENT_TIMESTAMP)),
(39, 'INBOUND', 11, 13, 38, NULL, 70, NULL, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -21, CURRENT_TIMESTAMP)),
(40, 'DISPOSAL', 3, 15, 13, NULL, 15, NULL, '유통기한 경과분 폐기', 1, '김책임', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(41, 'MOVE', 4, 6, 32, 33, 40, NULL, '적재 재배치', 1, '김책임', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
(42, 'TRANSFER_OUT', 9, 11, 49, 12, 60, NULL, '[센터 이관] 전북 김제 센터 GJ-PL-01 → 충남 예산 센터 YS-PL-02', 1, '김책임', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(43, 'TRANSFER_IN', 9, 11, 2, 49, 60, NULL, '[센터 이관] 전북 김제 센터 GJ-PL-01 → 충남 예산 센터 YS-PL-02', 1, '김책임', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(44, 'OUTBOUND', 2, 3, 34, NULL, 10, 5, '주문 #5 FEFO 출고', 1, '김책임', DATEADD('DAY', -1, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 8-1. 주문 기반 출고 이력  ※ 출고 취소 기능의 복구 근거
--    · 주문 #5 (SHIPPED) : 품목 2 / 로트 3 / 구역 5(B-01) 에서 10개 출고
--    · orderId 를 남겨야 취소 시 어느 로트·구역으로 되돌릴지 알 수 있다
--      (orderItems.lotId 는 대표 로트 하나만 기록하므로 근거로 쓸 수 없다)
--    · 취소하면 B-01 은 360 -> 370 (한도 600), 로트3 은 150 -> 160,
--      품목2 totalStock 은 700 -> 710 으로 복구된다
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- 9. IDENTITY 시퀀스 재시작
--    (명시적 ID 로 INSERT 했으므로, 이후 JPA 저장 시 PK 충돌을 막는다)
-- ---------------------------------------------------------------------
ALTER TABLE centers ALTER COLUMN centerId RESTART WITH 6;
ALTER TABLE users ALTER COLUMN userId RESTART WITH 6;
ALTER TABLE products ALTER COLUMN productId RESTART WITH 14;
ALTER TABLE productLots ALTER COLUMN lotId RESTART WITH 35;
ALTER TABLE orders ALTER COLUMN orderId RESTART WITH 16;
ALTER TABLE orderItems ALTER COLUMN orderItemId RESTART WITH 17;
ALTER TABLE warehouseBins ALTER COLUMN binId RESTART WITH 53;
ALTER TABLE inventories ALTER COLUMN inventoryId RESTART WITH 38;
ALTER TABLE stockMovements ALTER COLUMN movementId RESTART WITH 45;

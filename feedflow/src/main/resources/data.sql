-- =====================================================================
-- FeedFlow 초기 데이터 (H2 In-Memory)
--  · 서버 시작 시 자동 실행 (spring.jpa.defer-datasource-initialization=true)
--  · 테이블/컬럼명은 카멜 표기법(camelCase)으로 선언한다.
--    (H2 는 따옴표 없는 식별자를 대문자로 저장하므로 대소문자 구분 없이 조회된다)
--  · 비밀번호는 DelegatingPasswordEncoder 의 {noop}(평문) prefix 사용
--    → 실제 운영/회원가입 시에는 {bcrypt} 해시가 저장된다.
-- =====================================================================

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
(2,  'FD-PG-001', '자돈용 배합사료',         'PIG',     'FEED',       20, 28000, 550, 100, 180, TRUE,  '/images/feed-pig.png',     '이유 후 자돈의 소화 흡수율을 높인 프리스타터 사료입니다.', 0),
-- productId 3 : 정상 로트 80 + 만료 로트 20 = 100 (안전재고 120 미달 유지)
(3,  'FD-PL-001', '산란계 전용 배합사료',     'POULTRY', 'FEED',       25, 24000, 100, 120,  90, TRUE,  '/images/feed-chicken.png', '산란율 향상을 위한 칼슘 강화 배합사료입니다.', 0),
(4,  'FD-CT-002', '번식우 유지 배합사료',     'CATTLE',  'FEED',       25, 30000, 400,  80, 180, TRUE,  NULL, NULL, 0),
(5,  'FD-CT-003', '비육후기 고에너지 사료',   'CATTLE',  'FEED',       25, 34000, 350,  60, 150, TRUE,  NULL, NULL, 0),
(6,  'FD-PG-002', '육성돈 배합사료',         'PIG',     'FEED',       25, 26000, 680,  90, 180, TRUE,  NULL, NULL, 0),
(7,  'FD-PG-003', '임신돈 전용 사료',        'PIG',     'FEED',       25, 27000, 300,  70, 180, TRUE,  NULL, NULL, 0),
(8,  'FD-PL-002', '육계 초기 사료',          'POULTRY', 'FEED',       20, 25000, 240,  50,  90, TRUE,  NULL, NULL, 0),
(9,  'FD-PL-003', '육계 후기 사료',          'POULTRY', 'FEED',       20, 23000, 420,  60,  90, TRUE,  NULL, NULL, 0),
(10, 'FD-PL-004', '산란오리 배합사료',       'POULTRY', 'FEED',       25, 26000,  150,  40, 120, TRUE,  NULL, NULL, 0),
-- 영양제(보조제) : 포장 단위가 작고 유통기한이 길다
(11, 'SP-CT-001', '한우 비타민 영양제',      'CATTLE',  'SUPPLEMENT',  5, 45000,  400,  30, 365, TRUE,  NULL, NULL, 0),
(13, 'SP-PG-001', '자돈 정장 영양제',        'PIG',     'SUPPLEMENT',  5, 38000,  150,  20, 365, TRUE,  NULL, NULL, 0),
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
(3, 2, 'LOT-PG-2611', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY', 160, CURRENT_DATE), 150, 0),
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
(28, 11, 'LOT-SP-2653', DATEADD('DAY',  -60, CURRENT_DATE), DATEADD('DAY', 305, CURRENT_DATE), 190, 0);

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
-- 6. 창고 구역(Bin) 40건  ※ 기준 정보(Master Data)
--    · 제1창고(WH1) 20칸 : 상온 - R / H / A / B / C / D / E 구역
--    · 제2창고(WH2) 20칸 : 저온 - R / S / K / COLD 구역
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
INSERT INTO warehouseBins (binId, binCode, warehouse, zone, binPurpose, rack, binLevel, maxCapacity, posX, posY, posWidth, posHeight, active, memo, createdAt) VALUES
-- ===== 제1창고 (WH1) : 하역장(1~4열) | 벽(5열) | 보관 구역(6~23열) =====
(10, 'R-01', 'WH1', 'R', 'RECEIVING', '01', 1, 300,  1,  4, 4, 3, TRUE,  '입고 검수 전 대기 구역',   DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(11, 'H-01', 'WH1', 'H', 'STORAGE',   '01', 1, 700,  1,  7, 4, 3, TRUE,  '대형 파렛트 구역',         DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(12, 'H-02', 'WH1', 'H', 'STORAGE',   '02', 1, 700,  1, 10, 4, 3, TRUE,  NULL,                       DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(1,  'A-01', 'WH1', 'A', 'STORAGE',   '01', 1, 500,  6,  1, 2, 6, TRUE,  '입고 동선 우선 구역',      DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(2,  'A-02', 'WH1', 'A', 'STORAGE',   '01', 2, 500,  6,  7, 2, 6, TRUE,  NULL,                       DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(9,  'A-03', 'WH1', 'A', 'STORAGE',   '01', 3, 400,  6, 13, 2, 6, FALSE, '천장 누수 보수 중 사용 중지', DATEADD('DAY', -90, CURRENT_TIMESTAMP)),
(5,  'B-01', 'WH1', 'B', 'STORAGE',   '01', 1, 600,  9,  1, 7, 2, TRUE,  '대량 파렛트 적재 구역',    DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(6,  'B-02', 'WH1', 'B', 'STORAGE',   '01', 2, 600,  9,  4, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(7,  'B-03', 'WH1', 'B', 'STORAGE',   '02', 1, 300,  9,  7, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(13, 'B-04', 'WH1', 'B', 'STORAGE',   '02', 2, 300,  9, 10, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(3,  'C-01', 'WH1', 'C', 'STORAGE',   '01', 1, 400,  9, 13, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(4,  'C-02', 'WH1', 'C', 'STORAGE',   '01', 2, 400,  9, 16, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(14, 'D-01', 'WH1', 'D', 'STORAGE',   '01', 1, 500, 17,  1, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(15, 'D-02', 'WH1', 'D', 'STORAGE',   '01', 2, 500, 17,  4, 7, 2, TRUE,  NULL,                       DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(16, 'D-03', 'WH1', 'D', 'STORAGE',   '02', 1, 450, 17,  7, 3, 5, TRUE,  NULL,                       DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(17, 'D-04', 'WH1', 'D', 'STORAGE',   '02', 2, 450, 21,  7, 3, 5, TRUE,  NULL,                       DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(18, 'E-01', 'WH1', 'E', 'STORAGE',   '01', 1, 250, 17, 13, 3, 2, TRUE,  NULL,                       DATEADD('DAY', -150, CURRENT_TIMESTAMP)),
(19, 'E-02', 'WH1', 'E', 'STORAGE',   '01', 2, 250, 21, 13, 3, 2, TRUE,  NULL,                       DATEADD('DAY', -150, CURRENT_TIMESTAMP)),
(20, 'E-03', 'WH1', 'E', 'STORAGE',   '02', 1, 250, 17, 16, 3, 2, TRUE,  NULL,                       DATEADD('DAY', -150, CURRENT_TIMESTAMP)),
(21, 'E-04', 'WH1', 'E', 'STORAGE',   '02', 2, 250, 21, 16, 3, 2, TRUE,  NULL,                       DATEADD('DAY', -150, CURRENT_TIMESTAMP)),
-- ===== 제2창고 (WH2) : 저온 창고. COLD 구역을 이 창고에 배치 =====
(22, 'R-11',    'WH2', 'R',    'RECEIVING', '01', 1, 300,  1,  4, 4, 3, TRUE,  '저온 입고 대기(사전 냉각)', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(23, 'S-01',    'WH2', 'S',    'SHIPPING',  '01', 1, 400,  1,  7, 4, 3, TRUE,  '출고 피킹 집합 구역',       DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(24, 'S-02',    'WH2', 'S',    'SHIPPING',  '02', 1, 400,  1, 10, 4, 3, TRUE,  NULL,                        DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(25, 'K-01',    'WH2', 'K',    'STORAGE',   '01', 1, 350,  6,  1, 2, 6, TRUE,  '상온 병행 보관 구역',       DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(26, 'K-02',    'WH2', 'K',    'STORAGE',   '01', 2, 350,  6,  7, 2, 6, TRUE,  NULL,                        DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(27, 'K-03',    'WH2', 'K',    'STORAGE',   '01', 3, 350,  6, 13, 2, 6, TRUE,  NULL,                        DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(8,  'COLD-01', 'WH2', 'COLD', 'STORAGE',   '01', 1, 200,  9,  1, 7, 2, TRUE,  '저온 보관(영양제) 구역',    DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(28, 'COLD-02', 'WH2', 'COLD', 'STORAGE',   '01', 2, 200,  9,  4, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(29, 'COLD-03', 'WH2', 'COLD', 'STORAGE',   '02', 1, 200,  9,  7, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(30, 'COLD-04', 'WH2', 'COLD', 'STORAGE',   '02', 2, 200,  9, 10, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(31, 'COLD-05', 'WH2', 'COLD', 'STORAGE',   '03', 1, 200,  9, 13, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -110, CURRENT_TIMESTAMP)),
(32, 'COLD-06', 'WH2', 'COLD', 'STORAGE',   '03', 2, 200,  9, 16, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -110, CURRENT_TIMESTAMP)),
(33, 'COLD-07', 'WH2', 'COLD', 'STORAGE',   '04', 1, 250, 17,  1, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -110, CURRENT_TIMESTAMP)),
(34, 'COLD-08', 'WH2', 'COLD', 'STORAGE',   '04', 2, 250, 17,  4, 7, 2, TRUE,  NULL,                        DATEADD('DAY', -110, CURRENT_TIMESTAMP)),
(35, 'COLD-09', 'WH2', 'COLD', 'STORAGE',   '05', 1, 300, 17,  7, 3, 5, TRUE,  NULL,                        DATEADD('DAY', -110, CURRENT_TIMESTAMP)),
(36, 'COLD-10', 'WH2', 'COLD', 'STORAGE',   '05', 2, 300, 21,  7, 3, 5, TRUE,  NULL,                        DATEADD('DAY', -110, CURRENT_TIMESTAMP)),
(37, 'COLD-11', 'WH2', 'COLD', 'STORAGE',   '06', 1, 150, 17, 13, 3, 2, TRUE,  NULL,                        DATEADD('DAY', -100, CURRENT_TIMESTAMP)),
(38, 'COLD-12', 'WH2', 'COLD', 'STORAGE',   '06', 2, 150, 21, 13, 3, 2, TRUE,  NULL,                        DATEADD('DAY', -100, CURRENT_TIMESTAMP)),
(39, 'COLD-13', 'WH2', 'COLD', 'STORAGE',   '07', 1, 150, 17, 16, 3, 2, FALSE, '냉각기 교체 중 사용 중지',  DATEADD('DAY', -100, CURRENT_TIMESTAMP)),
(40, 'COLD-14', 'WH2', 'COLD', 'STORAGE',   '07', 2, 150, 21, 16, 3, 2, TRUE,  NULL,                        DATEADD('DAY', -100, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 7. 재고 (로트 × 구역) 16건
--    ★ 정합성 규칙 (반드시 지켜야 함)
--      · products.totalStock = 해당 품목 모든 로트의 inventories.quantity 합계
--      · productLots.lotQuantity = 해당 로트의 inventories.quantity 합계
--      이 값이 어긋나면 "전체 재고는 있는데 출고 가능 재고가 부족"한 현상이 발생한다.
--    · 각 구역의 합계는 warehouseBins.maxCapacity 를 넘지 않아야 한다
-- ---------------------------------------------------------------------
INSERT INTO inventories (inventoryId, lotId, binId, quantity, updatedAt, version) VALUES
(1,  1,  1,  20, DATEADD('DAY', -50, CURRENT_TIMESTAMP), 0),
(2,  2,  2,  20, DATEADD('DAY', -20, CURRENT_TIMESTAMP), 0),
(3,  3,  5, 100, DATEADD('DAY', -10, CURRENT_TIMESTAMP), 0),
(4,  3,  6,  50, DATEADD('DAY', -10, CURRENT_TIMESTAMP), 0),
(5,  4,  7, 150, DATEADD('DAY',  -5, CURRENT_TIMESTAMP), 0),
(6,  5,  3,  50, DATEADD('DAY', -35, CURRENT_TIMESTAMP), 0),
(7,  5,  4,  30, DATEADD('DAY', -35, CURRENT_TIMESTAMP), 0),
-- 품목 4~12 의 구역 재고
(8,  6,  1, 220, DATEADD('DAY', -60, CURRENT_TIMESTAMP), 0),
(9,  7,  2, 150, DATEADD('DAY', -50, CURRENT_TIMESTAMP), 0),
(10, 8,  5, 260, DATEADD('DAY', -40, CURRENT_TIMESTAMP), 0),
(11, 9,  6, 180, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),
(12, 10, 3, 140, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),
(13, 11, 4, 190, DATEADD('DAY', -20, CURRENT_TIMESTAMP), 0),
(14, 12, 8,  90, DATEADD('DAY', -30, CURRENT_TIMESTAMP), 0),
(15, 13, 8,  70, DATEADD('DAY', -20, CURRENT_TIMESTAMP), 0),
(16, 14, 7,  10, DATEADD('DAY', -140, CURRENT_TIMESTAMP), 0),
-- 만료된 로트의 재고 (출고 가능 재고에는 포함되지 않는다)
(17, 15, 3,  20, DATEADD('DAY',  -95, CURRENT_TIMESTAMP), 0),
-- 영양제 재고 (저온 구역 COLD-01 : 90 + 70 + 30 = 190 ≤ maxCapacity 200)
(18, 16, 8,  30, DATEADD('DAY',  -30, CURRENT_TIMESTAMP), 0),
-- 2D 도면 적재 현황용 추가 재고 (신규 구역에 분산 배치)
(19, 17, 14, 180, DATEADD('DAY',  -60, CURRENT_TIMESTAMP), 0),   -- D-01  180/500
(20, 18, 15, 200, DATEADD('DAY',  -40, CURRENT_TIMESTAMP), 0),   -- D-02  200/500
(21, 19, 16, 150, DATEADD('DAY', -170, CURRENT_TIMESTAMP), 0),   -- D-03  150/450 (D-10 임박)
(22, 20, 17, 120, DATEADD('DAY',  -30, CURRENT_TIMESTAMP), 0),   -- D-04  120/450
(23, 21, 11, 100, DATEADD('DAY',  -75, CURRENT_TIMESTAMP), 0),   -- H-01  100/700 (D-15 임박)
(24, 22, 12, 230, DATEADD('DAY',  -20, CURRENT_TIMESTAMP), 0),   -- H-02  230/700
(25, 23, 18,  60, DATEADD('DAY', -100, CURRENT_TIMESTAMP), 0),   -- E-01   60/250 (D-20 임박)
(26, 24, 25, 140, DATEADD('DAY',  -30, CURRENT_TIMESTAMP), 0),   -- K-01  140/350 (제2창고)
(27, 25, 28, 120, DATEADD('DAY',  -40, CURRENT_TIMESTAMP), 0),   -- COLD-02 120/200 = 60% 보통
(28, 26, 10, 250, DATEADD('DAY',  -15, CURRENT_TIMESTAMP), 0),   -- R-01  250/300 (입고 대기)
(29, 27, 13, 270, DATEADD('DAY',  -50, CURRENT_TIMESTAMP), 0),   -- B-04  270/300 = 90% 포화
(30, 28, 29, 190, DATEADD('DAY',  -60, CURRENT_TIMESTAMP), 0);   -- COLD-03 190/200 = 95% 포화

-- ---------------------------------------------------------------------
-- 8. 입고 이력 7건 (위 재고와 1:1 대응)
-- ---------------------------------------------------------------------
INSERT INTO stockMovements (movementId, movementType, productId, lotId, binId, quantity, memo, userId, userName, createdAt) VALUES
(1, 'INBOUND', 1, 1, 1,  20, '정기 발주 입고',       2, '이사원', DATEADD('DAY', -50, CURRENT_TIMESTAMP)),
(2, 'INBOUND', 1, 2, 2,  20, '정기 발주 입고',       2, '이사원', DATEADD('DAY', -20, CURRENT_TIMESTAMP)),
(3, 'INBOUND', 2, 3, 5, 100, '대량 발주 입고(1/2)',  2, '이사원', DATEADD('DAY', -10, CURRENT_TIMESTAMP)),
(4, 'INBOUND', 2, 3, 6,  50, '대량 발주 입고(2/2)',  2, '이사원', DATEADD('DAY', -10, CURRENT_TIMESTAMP)),
(5, 'INBOUND', 2, 4, 7, 150, '신규 로트 입고',       1, '김책임', DATEADD('DAY',  -5, CURRENT_TIMESTAMP)),
(6, 'INBOUND', 3, 5, 3,  50, '산란계 사료 입고(1/2)', 2, '이사원', DATEADD('DAY', -35, CURRENT_TIMESTAMP)),
(7,  'INBOUND', 3, 5,  4,  30, '산란계 사료 입고(2/2)', 2, '이사원', DATEADD('DAY', -35, CURRENT_TIMESTAMP)),
(8,  'INBOUND', 4,  6, 1, 220, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -60, CURRENT_TIMESTAMP)),
(9,  'INBOUND', 5,  7, 2, 150, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -50, CURRENT_TIMESTAMP)),
(10, 'INBOUND', 6,  8, 5, 260, '대량 발주 입고', 1, '김책임', DATEADD('DAY', -40, CURRENT_TIMESTAMP)),
(11, 'INBOUND', 7,  9, 6, 180, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -30, CURRENT_TIMESTAMP)),
(12, 'INBOUND', 8, 10, 3, 140, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -30, CURRENT_TIMESTAMP)),
(13, 'INBOUND', 9, 11, 4, 190, '정기 발주 입고', 2, '이사원', DATEADD('DAY', -20, CURRENT_TIMESTAMP)),
(14, 'INBOUND', 10, 12, 8,  90, '저온 구역 입고', 2, '이사원', DATEADD('DAY', -30, CURRENT_TIMESTAMP)),
(15, 'INBOUND', 11, 13, 8,  70, '저온 구역 입고', 2, '이사원', DATEADD('DAY', -20, CURRENT_TIMESTAMP)),
(16, 'INBOUND', 12, 14, 7,  10, '단종 품목 잔여 재고', 1, '김책임', DATEADD('DAY', -140, CURRENT_TIMESTAMP)),
(17, 'INBOUND',  3, 15, 3,  20, '유통기한 경과 재고(폐기 대기)', 2, '이사원', DATEADD('DAY', -95, CURRENT_TIMESTAMP)),
(18, 'INBOUND', 13, 16, 8,  30, '영양제 저온 구역 입고', 2, '이사원', DATEADD('DAY', -30, CURRENT_TIMESTAMP)),
-- 2D 도면 적재 현황용 추가 입고 이력
(19, 'INBOUND',  4, 17, 14, 180, '정기 발주 입고',       2, '이사원', DATEADD('DAY',  -60, CURRENT_TIMESTAMP)),
(20, 'INBOUND',  5, 18, 15, 200, '정기 발주 입고',       2, '이사원', DATEADD('DAY',  -40, CURRENT_TIMESTAMP)),
(21, 'INBOUND',  6, 19, 16, 150, '이월 재고 이관',       1, '김책임', DATEADD('DAY', -170, CURRENT_TIMESTAMP)),
(22, 'INBOUND',  7, 20, 17, 120, '정기 발주 입고',       2, '이사원', DATEADD('DAY',  -30, CURRENT_TIMESTAMP)),
(23, 'INBOUND',  8, 21, 11, 100, '대형 파렛트 입고',     2, '이사원', DATEADD('DAY',  -75, CURRENT_TIMESTAMP)),
(24, 'INBOUND',  9, 22, 12, 230, '대형 파렛트 입고',     2, '이사원', DATEADD('DAY',  -20, CURRENT_TIMESTAMP)),
(25, 'INBOUND', 10, 23, 18,  60, '소량 보충 입고',       2, '이사원', DATEADD('DAY', -100, CURRENT_TIMESTAMP)),
(26, 'INBOUND', 11, 24, 25, 140, '제2창고 상온 병행 보관', 2, '이사원', DATEADD('DAY',  -30, CURRENT_TIMESTAMP)),
(27, 'INBOUND', 13, 25, 28, 120, '영양제 저온 구역 입고', 2, '이사원', DATEADD('DAY',  -40, CURRENT_TIMESTAMP)),
(28, 'INBOUND',  2, 26, 10, 250, '입고 검수 대기',        2, '이사원', DATEADD('DAY',  -15, CURRENT_TIMESTAMP)),
(29, 'INBOUND',  6, 27, 13, 270, '대량 발주 입고',        1, '김책임', DATEADD('DAY',  -50, CURRENT_TIMESTAMP)),
(30, 'INBOUND', 11, 28, 29, 190, '영양제 저온 구역 입고', 2, '이사원', DATEADD('DAY',  -60, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 9. IDENTITY 시퀀스 재시작
--    (명시적 ID 로 INSERT 했으므로, 이후 JPA 저장 시 PK 충돌을 막는다)
-- ---------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN userId RESTART WITH 6;
ALTER TABLE products ALTER COLUMN productId RESTART WITH 14;
ALTER TABLE productLots ALTER COLUMN lotId RESTART WITH 29;
ALTER TABLE orders ALTER COLUMN orderId RESTART WITH 16;
ALTER TABLE orderItems ALTER COLUMN orderItemId RESTART WITH 17;
ALTER TABLE warehouseBins ALTER COLUMN binId RESTART WITH 41;
ALTER TABLE inventories ALTER COLUMN inventoryId RESTART WITH 31;
ALTER TABLE stockMovements ALTER COLUMN movementId RESTART WITH 31;

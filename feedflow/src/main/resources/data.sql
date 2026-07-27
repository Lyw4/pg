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
-- 2. 품목(사료) 12종  ※ 기준 정보(Master Data)
--    · productId 1, 3 은 안전재고 미달 → 대시보드 '안전재고 알림' 노출
--    · productId 12 는 사용 중지(단종) → 미달이지만 알림에서 제외
--    · 10건 초과라서 품목 목록 화면의 페이징을 바로 확인할 수 있다
-- ---------------------------------------------------------------------
INSERT INTO products (productId, productCode, name, animalType, weightKg, price, totalStock, safetyStock, shelfLifeDays, active, imageUrl, description) VALUES
(1,  'FD-CT-001', '프리미엄 육성우 배합사료', '소',   25, 32000,  40,  50, 180, TRUE,  '/images/feed-cattle.png',  '육성기 한우의 골격 형성을 돕는 고단백 배합사료입니다.'),
(2,  'FD-PG-001', '자돈용 배합사료',         '돼지', 20, 28000, 300, 100, 180, TRUE,  '/images/feed-pig.png',     '이유 후 자돈의 소화 흡수율을 높인 프리스타터 사료입니다.'),
(3,  'FD-CK-001', '산란계 전용 배합사료',     '닭',   25, 24000,  80, 120,  90, TRUE,  '/images/feed-chicken.png', '산란율 향상을 위한 칼슘 강화 배합사료입니다.'),
(4,  'FD-CT-002', '번식우 유지 배합사료',     '소',   25, 30000, 220,  80, 180, TRUE,  NULL, NULL),
(5,  'FD-CT-003', '비육후기 고에너지 사료',   '소',   25, 34000, 150,  60, 150, TRUE,  NULL, NULL),
(6,  'FD-PG-002', '육성돈 배합사료',         '돼지', 25, 26000, 260,  90, 180, TRUE,  NULL, NULL),
(7,  'FD-PG-003', '임신돈 전용 사료',        '돼지', 25, 27000, 180,  70, 180, TRUE,  NULL, NULL),
(8,  'FD-CK-002', '육계 초기 사료',          '닭',   20, 25000, 140,  50,  90, TRUE,  NULL, NULL),
(9,  'FD-CK-003', '육계 후기 사료',          '닭',   20, 23000, 190,  60,  90, TRUE,  NULL, NULL),
(10, 'FD-DK-001', '산란오리 배합사료',       '오리', 25, 26000,  90,  40, 120, TRUE,  NULL, NULL),
(11, 'FD-GT-001', '산양 성장기 사료',        '염소', 20, 31000,  70,  30, 120, TRUE,  NULL, NULL),
-- 단종(사용 중지) 품목: 재고가 안전재고보다 적지만 대시보드 알림에서 제외된다
(12, 'FD-CT-900', '구형 육성우 사료(단종)',  '소',   25, 29000,  10,  50, 180, FALSE, NULL, NULL);

-- ---------------------------------------------------------------------
-- 3. 로트 5건
--    lotId 1(D-5), 2(D-25), 5(D-18) → 대시보드 '유통기한 임박 알림'(30일 이내) 노출
-- ---------------------------------------------------------------------
--    ※ manufacturedDate = expirationDate - 품목의 shelfLifeDays 로 맞춰 두었다
--      (입고 시 자동 계산되는 값과 동일한 규칙)
INSERT INTO productLots (lotId, productId, lotNo, manufacturedDate, expirationDate, lotQuantity) VALUES
(1, 1, 'LOT-CT-2601', DATEADD('DAY', -175, CURRENT_DATE), DATEADD('DAY',   5, CURRENT_DATE),  20),
(2, 1, 'LOT-CT-2602', DATEADD('DAY', -155, CURRENT_DATE), DATEADD('DAY',  25, CURRENT_DATE),  20),
(3, 2, 'LOT-PG-2611', DATEADD('DAY',  -20, CURRENT_DATE), DATEADD('DAY', 160, CURRENT_DATE), 150),
(4, 2, 'LOT-PG-2612', DATEADD('DAY',  -10, CURRENT_DATE), DATEADD('DAY', 170, CURRENT_DATE), 150),
(5, 3, 'LOT-CK-2621', DATEADD('DAY',  -72, CURRENT_DATE), DATEADD('DAY',  18, CURRENT_DATE),  80);

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
-- 6. 창고 구역(Bin) 9건  ※ 기준 정보(Master Data)
--    · A / B / COLD 3개 구역, binId 9 는 사용 중지 상태
-- ---------------------------------------------------------------------
INSERT INTO warehouseBins (binId, binCode, zone, rack, binLevel, maxCapacity, active, memo, createdAt) VALUES
(1, 'A-01-01', 'A',    '01', 1, 500, TRUE,  '입고 대기 우선 구역',   DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(2, 'A-01-02', 'A',    '01', 2, 500, TRUE,  NULL,                    DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(3, 'A-02-01', 'A',    '02', 1, 400, TRUE,  NULL,                    DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(4, 'A-02-02', 'A',    '02', 2, 400, TRUE,  NULL,                    DATEADD('DAY', -300, CURRENT_TIMESTAMP)),
(5, 'B-01-01', 'B',    '01', 1, 600, TRUE,  '대량 파렛트 적재 구역', DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(6, 'B-01-02', 'B',    '01', 2, 600, TRUE,  NULL,                    DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(7, 'B-02-01', 'B',    '02', 1, 300, TRUE,  NULL,                    DATEADD('DAY', -200, CURRENT_TIMESTAMP)),
(8, 'COLD-01', 'COLD', '01', 1, 200, TRUE,  '저온 보관(첨가제) 구역', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(9, 'A-03-01', 'A',    '03', 1, 400, FALSE, '천장 누수 보수 중 사용 중지', DATEADD('DAY', -90, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 7. 재고 (로트 × 구역) 7건
--    · lotQuantity 와 구역별 보관 수량 합계가 일치해야 한다
--      lot1=20, lot2=20, lot3=150, lot4=150, lot5=80
--    · products.totalStock 과도 일치 (product1=40, product2=300, product3=80)
-- ---------------------------------------------------------------------
INSERT INTO inventories (inventoryId, lotId, binId, quantity, updatedAt) VALUES
(1, 1, 1,  20, DATEADD('DAY', -50, CURRENT_TIMESTAMP)),
(2, 2, 2,  20, DATEADD('DAY', -20, CURRENT_TIMESTAMP)),
(3, 3, 5, 100, DATEADD('DAY', -10, CURRENT_TIMESTAMP)),
(4, 3, 6,  50, DATEADD('DAY', -10, CURRENT_TIMESTAMP)),
(5, 4, 7, 150, DATEADD('DAY',  -5, CURRENT_TIMESTAMP)),
(6, 5, 3,  50, DATEADD('DAY', -35, CURRENT_TIMESTAMP)),
(7, 5, 4,  30, DATEADD('DAY', -35, CURRENT_TIMESTAMP));

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
(7, 'INBOUND', 3, 5, 4,  30, '산란계 사료 입고(2/2)', 2, '이사원', DATEADD('DAY', -35, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 9. IDENTITY 시퀀스 재시작
--    (명시적 ID 로 INSERT 했으므로, 이후 JPA 저장 시 PK 충돌을 막는다)
-- ---------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN userId RESTART WITH 6;
ALTER TABLE products ALTER COLUMN productId RESTART WITH 13;
ALTER TABLE productLots ALTER COLUMN lotId RESTART WITH 6;
ALTER TABLE orders ALTER COLUMN orderId RESTART WITH 16;
ALTER TABLE orderItems ALTER COLUMN orderItemId RESTART WITH 17;
ALTER TABLE warehouseBins ALTER COLUMN binId RESTART WITH 10;
ALTER TABLE inventories ALTER COLUMN inventoryId RESTART WITH 8;
ALTER TABLE stockMovements ALTER COLUMN movementId RESTART WITH 8;

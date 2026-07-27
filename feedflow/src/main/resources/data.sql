-- =====================================================================
-- FeedFlow 초기 데이터 (H2 In-Memory)
--  · 서버 시작 시 자동 실행 (spring.jpa.defer-datasource-initialization=true)
--  · 비밀번호는 DelegatingPasswordEncoder 의 {noop}(평문) prefix 사용
--    → 실제 운영/회원가입 시에는 {bcrypt} 해시가 저장된다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 사용자 : 사원(ADMIN 1, STAFF 1) + 고객(USER 3)
-- ---------------------------------------------------------------------
INSERT INTO users (user_id, email, password, name, phone, role, created_at) VALUES
(1, 'admin@feedflow.co.kr', '{noop}admin123', '김책임', '010-1111-1001', 'ADMIN', DATEADD('DAY', -400, CURRENT_TIMESTAMP)),
(2, 'staff@feedflow.co.kr', '{noop}staff123', '이사원', '010-2222-2002', 'STAFF', DATEADD('DAY', -180, CURRENT_TIMESTAMP)),
(3, 'farm1@example.com',    '{noop}user123',  '정한우목장', '010-3333-3003', 'USER', DATEADD('DAY', -120, CURRENT_TIMESTAMP)),
(4, 'farm2@example.com',    '{noop}user123',  '대성양돈',   '010-4444-4004', 'USER', DATEADD('DAY', -90,  CURRENT_TIMESTAMP)),
(5, 'farm3@example.com',    '{noop}user123',  '행복한계농장', '010-5555-5005', 'USER', DATEADD('DAY', -60, CURRENT_TIMESTAMP));

-- ---------------------------------------------------------------------
-- 2. 상품(사료) 3종
--    product_id 1, 3 은 안전재고 미달 → 대시보드 '안전재고 알림' 노출
-- ---------------------------------------------------------------------
INSERT INTO products (product_id, name, animal_type, weight_kg, price, total_stock, safety_stock, image_url, description) VALUES
(1, '프리미엄 육성우 배합사료', '소',   25, 32000,  40,  50, '/images/feed-cattle.png', '육성기 한우의 골격 형성을 돕는 고단백 배합사료입니다.'),
(2, '자돈용 배합사료',         '돼지', 20, 28000, 300, 100, '/images/feed-pig.png',    '이유 후 자돈의 소화 흡수율을 높인 프리스타터 사료입니다.'),
(3, '산란계 전용 배합사료',     '닭',   25, 24000,  80, 120, '/images/feed-chicken.png', '산란율 향상을 위한 칼슘 강화 배합사료입니다.');

-- ---------------------------------------------------------------------
-- 3. 로트 5건
--    lot 1(D-5), lot 5(D-18) → 대시보드 '유통기한 임박 알림'(30일 이내) 노출
-- ---------------------------------------------------------------------
INSERT INTO product_lots (lot_id, product_id, lot_no, manufactured_date, expiration_date, lot_quantity) VALUES
(1, 1, 'LOT-CT-2601', DATEADD('DAY', -50, CURRENT_DATE), DATEADD('DAY',   5, CURRENT_DATE),  20),
(2, 1, 'LOT-CT-2602', DATEADD('DAY', -20, CURRENT_DATE), DATEADD('DAY',  25, CURRENT_DATE),  20),
(3, 2, 'LOT-PG-2611', DATEADD('DAY', -10, CURRENT_DATE), DATEADD('DAY', 160, CURRENT_DATE), 150),
(4, 2, 'LOT-PG-2612', DATEADD('DAY',  -5, CURRENT_DATE), DATEADD('DAY', 170, CURRENT_DATE), 150),
(5, 3, 'LOT-CK-2621', DATEADD('DAY', -35, CURRENT_DATE), DATEADD('DAY',  18, CURRENT_DATE),  80);

-- ---------------------------------------------------------------------
-- 4. 최근 7일치 주문 15건
--    · PAID(오늘 2건)   → '신규 주문'
--    · READY(2건)       → '출고 대기'
--    · CANCELED(1건)    → 매출 집계에서 제외됨
-- ---------------------------------------------------------------------
INSERT INTO orders (order_id, user_id, total_price, discount_price, final_price, shipping_address, status, created_at) VALUES
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
-- 5. 주문 상세 (order_price = 주문 당시 단가)
-- ---------------------------------------------------------------------
INSERT INTO order_items (order_item_id, order_id, product_id, lot_id, quantity, order_price) VALUES
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
-- 6. IDENTITY 시퀀스 재시작
--    (명시적 ID 로 INSERT 했으므로, 이후 JPA 저장 시 PK 충돌을 막는다)
-- ---------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 6;
ALTER TABLE products ALTER COLUMN product_id RESTART WITH 4;
ALTER TABLE product_lots ALTER COLUMN lot_id RESTART WITH 6;
ALTER TABLE orders ALTER COLUMN order_id RESTART WITH 16;
ALTER TABLE order_items ALTER COLUMN order_item_id RESTART WITH 17;

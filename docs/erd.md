# FeedFlow ERD

배합사료 유통 관리 플랫폼 데이터 모델. B2C 쇼핑몰과 WMS(관리자 창고 시스템)가 하나의 DB를 공유한다.

- 테이블 8개 / 컬럼 68개 / 관계 10건
- 물리 명명 규칙: `PhysicalNamingStrategyStandardImpl` 적용 → **DB 컬럼도 camelCase 유지**
- 예약어 회피를 위해 `users`, `orders`, `binLevel` 만 이름을 변경

상세 정의서는 [erd-columns.tsv](erd-columns.tsv) · [erd-relations.tsv](erd-relations.tsv) · [erd-enums.tsv](erd-enums.tsv) 참고.

## 전체 ERD

```mermaid
erDiagram
    users          ||--o{ orders         : "주문한다"
    orders         ||--|{ orderItems     : "항목을 가진다"
    products       ||--o{ orderItems     : "주문된다"
    products       ||--o{ productLots    : "로트로 입고된다"
    products       ||--o{ stockMovements : "이력이 쌓인다"
    productLots    ||--o{ inventories    : "구역에 적재된다"
    productLots    |o--o{ orderItems     : "FEFO 대표 로트"
    productLots    ||--o{ stockMovements : "이력이 쌓인다"
    warehouseBins  ||--o{ inventories    : "재고를 보관한다"
    warehouseBins  |o--o{ stockMovements : "이력의 대상 구역"

    users {
        bigint    userId    PK "IDENTITY"
        varchar   email     UK "로그인 ID"
        varchar   password     "BCrypt 해시"
        varchar   name         "NOT NULL"
        varchar   phone        "nullable"
        varchar   role         "USER / STAFF / ADMIN"
        timestamp createdAt    "NOT NULL"
    }

    products {
        bigint    productId     PK "IDENTITY"
        varchar   productCode   UK "업무 식별자"
        varchar   name             "NOT NULL"
        varchar   animalType       "CATTLE / PIG / POULTRY"
        varchar   productType      "FEED / SUPPLEMENT"
        int       weightKg         "포장 무게"
        bigint    price            "NOT NULL"
        int       totalStock       "비정규화 캐시"
        int       safetyStock      "안전재고 알림 기준"
        int       shelfLifeDays    "유통기한 자동계산용"
        boolean   active           "soft delete"
        bigint    version          "낙관적 락"
        varchar   imageUrl         "B2C 전용"
        varchar   description      "B2C 전용"
    }

    productLots {
        bigint    lotId            PK "IDENTITY"
        bigint    productId        FK "NOT NULL"
        varchar   lotNo               "복합 UK productId lotNo"
        date      manufacturedDate    "NOT NULL"
        date      expirationDate      "제조일 + shelfLifeDays"
        int       lotQuantity         "로트 잔여 수량"
        bigint    version             "낙관적 락"
    }

    warehouseBins {
        bigint    binId       PK "IDENTITY"
        varchar   binCode     UK "예 A-01-02"
        varchar   zone           "A / B / COLD"
        varchar   rack           "nullable"
        int       binLevel       "level 예약어 회피"
        int       maxCapacity    "적재 한도"
        boolean   active         "soft delete"
        varchar   memo           "nullable"
        timestamp createdAt      "NOT NULL"
    }

    inventories {
        bigint    inventoryId PK "IDENTITY"
        bigint    lotId       FK "복합 UK lotId binId"
        bigint    binId       FK "복합 UK lotId binId"
        int       quantity       "구역별 실물 수량"
        timestamp updatedAt      "자동 갱신"
        bigint    version        "낙관적 락"
    }

    orders {
        bigint    orderId         PK "IDENTITY"
        bigint    userId          FK "주문 고객"
        bigint    totalPrice         "할인 전"
        bigint    discountPrice      "할인액"
        bigint    finalPrice         "매출 집계 기준"
        varchar   shippingAddress    "NOT NULL"
        varchar   status             "PAID / READY / SHIPPED / DELIVERED / CANCELED"
        timestamp createdAt          "NOT NULL"
    }

    orderItems {
        bigint orderItemId PK "IDENTITY"
        bigint orderId     FK "cascade ALL"
        bigint productId   FK "NOT NULL"
        bigint lotId       FK "nullable 대표 로트"
        int    quantity       "NOT NULL"
        bigint orderPrice     "주문 당시 단가"
    }

    stockMovements {
        bigint    movementId   PK "IDENTITY"
        varchar   movementType    "INBOUND / OUTBOUND / DISPOSAL / MOVE / ADJUST"
        bigint    productId    FK "NOT NULL"
        bigint    lotId        FK "NOT NULL"
        bigint    binId        FK "nullable"
        int       quantity        "항상 양수"
        varchar   memo            "nullable"
        varchar   reason          "폐기 사유 enum"
        bigint    userId          "FK 아님 처리자 스냅샷"
        varchar   userName        "FK 아님 이력 보존"
        timestamp createdAt       "NOT NULL"
    }
```

## 재고가 3단으로 관리되는 구조

재고 수량은 조회 성능을 위해 세 계층에 중복 저장된다. 세 값은 항상 같아야 한다.

```mermaid
flowchart TD
    A["products.totalStock<br/>품목 총 재고<br/>(비정규화 캐시)"]
    B["productLots.lotQuantity<br/>로트별 잔여<br/>(유통기한 관리 단위)"]
    C["inventories.quantity<br/>구역별 실물<br/>(실제 적재 위치)"]

    A -->|"= SUM"| B
    B -->|"= SUM"| C

    D["정합성 재계산 화면<br/>/admin/inventory/sync"]
    D -.->|"로트 합계를 정답으로<br/>totalStock 보정"| A

    style A fill:#fff3cd,stroke:#856404
    style B fill:#d1e7dd,stroke:#0f5132
    style C fill:#cfe2ff,stroke:#084298
    style D fill:#f8d7da,stroke:#842029
```

세 계층은 입고 · 출고 · 폐기 시 **한 트랜잭션에서 함께 갱신**되고, `products` · `productLots` · `inventories` 세 엔티티에 `@Version` 낙관적 락이 걸려 있다.

## FEFO 출고 흐름

유통기한이 가장 빠른 로트부터 차감한다.

```mermaid
flowchart LR
    O["주문<br/>orders<br/>status=PAID"] --> I["주문 항목<br/>orderItems"]
    I --> P{"품목별<br/>가용 로트 조회"}
    P --> L1["로트 A<br/>D-5"]
    P --> L2["로트 B<br/>D-25"]
    P --> L3["로트 C<br/>D-160"]
    L1 --> Q["유통기한 오름차순<br/>순차 차감"]
    L2 --> Q
    L3 --> Q
    Q --> M["stockMovements<br/>OUTBOUND 이력<br/>로트별 1건씩"]
    Q --> S["orders<br/>status=SHIPPED"]

    style L1 fill:#f8d7da,stroke:#842029
    style L2 fill:#fff3cd,stroke:#856404
    style L3 fill:#d1e7dd,stroke:#0f5132
```

한 주문 항목이 여러 로트에 걸쳐 차감될 수 있으나 `orderItems.lotId` 는 로트를 하나만 저장할 수 있다.
그래서 **가장 먼저 만료되는 로트를 대표로 기록**하고, 로트별 실제 차감 내역은 `stockMovements` 에 남긴다.
B2C 와 공유하는 스키마를 바꾸지 않기 위한 설계다.

## 권한 구조

`users` 한 테이블에 B2C 고객과 사원이 함께 저장되고 `role` 로 구분한다.

```mermaid
flowchart TD
    U["users.role"]
    U --> R1["USER<br/>고객"]
    U --> R2["STAFF<br/>사원"]
    U --> R3["ADMIN<br/>책임자"]

    R1 --> S1["/shop/**<br/>B2C 쇼핑몰"]
    R2 --> S2["/admin/**<br/>재고 · 입출고 · 스캔"]
    R3 --> S2
    R3 --> S3["매출 통계<br/>사원 권한 관리<br/>재고 정합성 보정"]

    style R1 fill:#e2e3e5,stroke:#41464b
    style R2 fill:#cfe2ff,stroke:#084298
    style R3 fill:#f8d7da,stroke:#842029
    style S3 fill:#f8d7da,stroke:#842029
```

`/admin/**` 은 `hasAnyRole("STAFF","ADMIN")`, 책임자 전용 기능은 추가로 `@PreAuthorize("hasRole('ADMIN')")` 와
Thymeleaf `sec:authorize` 로 이중 차단한다.

# DB 명명 규칙 (Naming Convention)

## 핵심 규칙

**모든 테이블명과 컬럼명은 카멜 표기법(camelCase)으로 선언한다. snake_case 를 사용하지 않는다.**

| 구분 | 올바른 예 | 잘못된 예 |
|---|---|---|
| 컬럼 | `productId`, `productCode`, `animalType`, `createdAt` | `product_id`, `product_code`, `animal_type` |
| 테이블 | `products`, `productLots`, `orderItems`, `warehouseBins` | `product_lots`, `order_items`, `warehouse_bins` |

## 적용 방법

### 1. Hibernate 물리 네이밍 전략 (필수)

Spring Boot 기본 전략(`CamelCaseToUnderscoresNamingStrategy`)은 필드명을 자동으로
snake_case 로 변환하므로, 반드시 표준 전략으로 고정한다.

```properties
# application.properties
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

이 설정을 제거하면 전체 스키마가 snake_case 로 되돌아간다.

### 2. 엔티티 선언

`@Column` / `@JoinColumn` / `@Table` 의 `name` 속성을 **항상 명시적으로** 카멜 표기법으로 작성한다.
(네이밍 전략이 바뀌어도 스키마가 흔들리지 않게 하기 위함)

```java
@Entity
@Table(name = "productLots")
public class ProductLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lotId")
    private Long lotId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    @Column(name = "expirationDate", nullable = false)
    private LocalDate expirationDate;
}
```

### 3. SQL 스크립트 (data.sql, schema.sql, 네이티브 쿼리)

컬럼/테이블명을 카멜 표기법으로 작성하고 **따옴표로 감싸지 않는다.**

```sql
INSERT INTO productLots (lotId, productId, lotNo, expirationDate, lotQuantity) VALUES (...);
ALTER TABLE productLots ALTER COLUMN lotId RESTART WITH 6;
```

## 예약어 예외

SQL 예약어와 충돌하는 이름은 그대로 쓸 수 없으므로 아래 규칙을 따른다.

| 원래 이름 | 실제 사용 | 이유 |
|---|---|---|
| `user` | `users` (테이블) | USER 는 예약어 |
| `order` | `orders` (테이블) | ORDER 는 예약어 |
| `level` | `binLevel` (컬럼) | LEVEL 은 예약어와 혼동 |

## 주의사항

- **H2 / Oracle 등은 따옴표 없는 식별자를 대문자로 저장**한다. 따라서 DB 내부 저장명은
  `PRODUCTID` 가 되고, 조회 시 대소문자를 구분하지 않는다. 이는 정상 동작이다.
- 반대로 **PostgreSQL 은 소문자로 폴딩**한다. 향후 PostgreSQL 로 전환할 경우
  `productid` 로 저장되므로, 네이티브 쿼리에서 컬럼명을 따옴표로 감싸지 않도록 주의한다.
- `globally_quoted_identifiers` 옵션은 사용하지 않는다. (따옴표가 붙으면 대소문자를
  엄격히 구분해 `data.sql` 과 불일치가 발생한다)
- 통합 DB(B2C 쇼핑몰 + WMS)를 공유하므로, 스키마 변경 시 반드시 상대 팀에 공유한다.

# FeedFlow WMS 전달본 — 먼저 읽어 주세요

병래님, 고객 농장 모듈 잘 받았습니다. 구조가 깔끔해서 바로 파악할 수 있었습니다.
특히 `integration/` 의 코드 조각을 `.java` 가 아니라 `.java.txt` 로 두신 것,
`backend/dependencies/` 에 "이미 있으면 덮어쓰지 말 것" 을 표시해 두신 것이 좋았습니다.

그 모듈을 검토하다가 **미리 이야기해야 할 것을 발견해서** 제 작업물 전체를 함께 올렸습니다.

---

## 이 폴더가 무엇인가

`feedflow-wms/` 는 제가 만든 **관리자용 창고 관리 시스템(WMS)** 저장소 전체입니다.
`Final-Project` 의 파일은 **하나도 건드리지 않았습니다.** 이 폴더만 추가되었습니다.

```
feedflow-wms/
├── README.md              ← 프로젝트 소개 · 아키텍처 · 주요 화면
├── docs/
│   ├── retrospective.md   ← 기술적 난제와 해결 과정
│   ├── architecture-notes.md
│   ├── requirements.md · erd.md · epic-p3-design.md
│   └── unit-work-report.tsv · sql-definition.tsv
├── .kiro/steering/db-naming-convention.md   ← DB 명명 규칙 (중요, 아래 ②)
└── feedflow/              ← Spring Boot 프로젝트 (여기서 gradlew 실행)
```

### 실행

```bash
cd feedflow-wms/feedflow
gradlew.bat bootRun
```

`http://localhost:8080` — **DB 설치나 API 키 발급이 필요 없습니다.**
H2 인메모리 + `data.sql` 시드가 자동으로 올라옵니다.

| 계정 | 비밀번호 | 권한 |
|---|---|---|
| `admin@feedflow.co.kr` | `admin123` | ADMIN (매출 · 사원 관리 · 폐기 · 출고 취소) |
| `staff@feedflow.co.kr` | `staff123` | STAFF (재고 · 입출고 · 도면) |

`Final-Project` 와 **완전히 독립적으로 실행**됩니다. 포트만 겹치니 동시에 띄우실 때는
한쪽을 `--server.port=8081` 로 주세요.

---

## 먼저 이야기해야 할 것 — 우리 둘이 같은 도메인을 각자 만들었습니다

저는 "병래님이 B2C 쇼핑몰을 담당한다" 고 알고 작업했는데, `Final-Project` 를 열어 보니
`/inventory` 에 상품 · LOT 재고 · 입고 · FIFO 출고 · 재고 조정 · 이력이 이미 있었습니다.

| 개념 | 제 쪽 (`com.feedflow`) | 병래님 쪽 (`com.ex`) |
|---|---|---|
| 품목 | `products` · `productCode` · `animalType` **enum** | `product` · `manufacturer` FK · `animalType` String · `shelfLifeMonths` |
| 로트 | `productLots` · unique(`productId`, `lotNo`) | `product_lot` · `lot_no` 전역 unique |
| **재고 위치** | `Inventory` + `WarehouseBin` — **구역 단위 3계층** | `ProductLot.warehouseLocation` — 문자열 |
| 이력 | `StockMovement` 8종 · 구역 단위 | `StockLog` · 로트 단위 |
| 거점 | `centers` (`Center`) | `warehouse` (`Warehouse`) |
| 출고 정책 | **FEFO** (유통기한 우선) | **FIFO** (입고순) |

**서로에게만 있는 것**

| 제 쪽에만 | 병래님 쪽에만 |
|---|---|
| 구역(Bin) 3계층 · 2D 평면도 · 좌표 | 배송 · 운송장 추적 (`Delivery` · `Shipment`) |
| 센터 간 이관 (`IN_TRANSIT` 가상 구역) | 정기 배송 (`RecurringDelivery`) |
| 재고 정합성 점검 · 보정 | 제조사 (`Manufacturer`) · 불량 기록 (`DefectRecord`) |
| 적재율 · 안전재고 · 유통기한 경보 | 창고×품목 월 계획 (`WarehouseAllocation`) |
| 바코드 스캔 · QR 라벨 | 고객 농장 (`FarmCustomer`) |

겹치는 것보다 **서로 없는 것이 더 많습니다.** 합치면 꽤 완성도 있는 시스템이 될 것 같습니다.
다만 그 전에 아래 네 가지를 정해야 합니다.

---

## 결정할 것 ① 물류 거점의 정본 — `warehouse` 냐 `centers` 냐

주소와 운영 방향이 **글자까지 같습니다.** 같은 기획 원본을 쓴 것 같습니다.

```
제 쪽    C1-YS  충남 예산 센터   충남 예산군 고덕면 몽곡리 667 일대  양계 · 양돈 중심
병래님   W01    예산 고덕 창고   충남 예산군 고덕면 몽곡리 667 일대  양계·양돈 중심
```

**그런데 좌표가 다릅니다.**

| 거점 | 제 좌표 | 병래님 좌표 | 차이 |
|---|---|---|---|
| 예산 | 36.772 / 126.771 | 36.7339 / 126.6995 | 약 7km |
| 김제 | 35.812 / 126.873 | 35.8289 / 126.8795 | 약 2km |
| 의성 | 36.418 / 128.635 | 36.4247 / 128.6886 | 약 5km |
| 안성 | 37.001 / 127.225 | 36.9684 / 127.2154 | 약 4km |
| **나주** | 35.098 / 126.662 | 35.0459 / 126.8447 | **약 17km** |

그대로 합치면 한 DB 에 거점이 두 벌 생기고, 지도에 핀도 두 위치에 찍힙니다.
그리고 `FarmCustomer.distanceKm` 는 병래님 좌표 기준으로 계산된 값이라
제 센터 좌표를 쓰면 그 숫자가 맞지 않게 됩니다.

**제 의견**: `centers` 를 정본으로 하는 것이 비용이 적습니다. 구역 · 재고 · 이력 · 이관 ·
2D 도면이 모두 `centers.centerId` 에 매달려 있어서, 이쪽을 옮기려면 그 전체를 다시 짜야 합니다.
`FarmCustomer.assignedWarehouse` 가 `Center` 를 참조하도록 바꾸는 편이 훨씬 작은 변경입니다.
**좌표는 어느 쪽이 정확한지 병래님이 판단해 주세요** — 저는 기획 주소를 보고 직접 찍었고,
`"고덕면 몽곡리 667 일대"` 처럼 범위로 적힌 주소라 서로 다르게 해석한 것 같습니다.

## 결정할 것 ② DB 컬럼 명명 — 이건 합치는 순간 터집니다

두 프로젝트의 Hibernate 물리 네이밍 전략이 **정반대**입니다.

| | 설정 | 만들어지는 컬럼 |
|---|---|---|
| 제 쪽 | `physical-strategy=PhysicalNamingStrategyStandardImpl` **고정** | `centerCode` · `createdAt` |
| 병래님 쪽 | 설정 없음 → Spring Boot 기본값 | `warehouse_code` · `created_at` |

제 쪽이 camelCase 를 쓰는 이유는 `.kiro/steering/db-naming-convention.md` 에 적어 두었습니다.

**가장 위험한 건 "절반만 맞는" 상태입니다.** 예를 들어 `Warehouse` 엔티티를 제 설정 아래
그대로 두면

- `@Column(name = "warehouse_code")` — 이름이 박혀 있어 snake 그대로 생성
- `serviceArea` · `operationFocus` · `displayOrder` — 이름이 없어 **camelCase 로 생성**
- 그런데 `farm_customer_upsert.sql` 은 `SERVICE_AREA` · `OPERATION_FOCUS` 로 넣음

전부 깨지면 바로 알아채는데, 절반만 어긋나면 원인을 찾는 데 오래 걸립니다.
`FarmCustomer` 는 컬럼이 20개가 넘어서 더 심합니다.

**어느 쪽으로 통일하든 괜찮습니다.** 정하기만 하면 됩니다. 다만 정한 뒤에는
`@Column` / `@JoinColumn` / `@Table` 의 `name` 을 **모두 명시**하는 편이 안전합니다.
전략 설정 한 줄이 지워지면 스키마 전체가 뒤집히기 때문입니다.

## 결정할 것 ③ 축종 값 — 지금은 조인이 안 됩니다

| | 값 | 형태 |
|---|---|---|
| 제 `Product.animalType` | `CATTLE` · `PIG` · `POULTRY` | enum (`EnumType.STRING`) |
| 병래님 `FarmCustomer.animalType` | `소` · `돼지` · `조류(닭/오리)` | 자유 문자열 |

"이 센터가 담당하는 농장의 축종" 과 "이 센터가 보유한 사료의 축종" 을 비교하는 것이
이 데이터의 가장 큰 활용처인데, 지금은 값이 달라 맞물리지 않습니다.

제 `AnimalType` enum 이 이미 한글 라벨을 갖고 있습니다
(`CATTLE("소")` · `PIG("돼지")` · `POULTRY("조류")`).
다만 `조류(닭/오리)` 는 라벨 `조류` 와 달라서 그대로는 매칭되지 않습니다.

## 결정할 것 ④ DB 종류와 시드 방식

| | 제 쪽 | 병래님 쪽 |
|---|---|---|
| DB | H2 **인메모리** `mem:feedflow` | H2 **파일** `file:./data/finalproject` |
| `ddl-auto` | `create` (매번 새로) | `update` (기존 유지) |
| 시드 | `data.sql` — 부팅 시 자동 | Seeder 클래스 3개 |

지금은 **DB 파일 자체가 다릅니다.** "통합 DB 공유" 가 아직 아닌 상태입니다.

그리고 `farm_customer_upsert.sql`(방법 A)은 H2 Shell 로 수동 실행하는 방식인데,
**인메모리에서는 재시작하면 사라지므로 의미가 없습니다.** 제 쪽 구성에 넣으려면
`data.sql` 에 병합하거나 Seeder(방법 C)를 쓰는 편이 맞습니다.
`fresh_database_import.sql`(방법 B)은 README 에 적힌 대로 기존 테이블이 있는 DB 에는
쓰지 않겠습니다.

---

## 고객 농장 모듈에 대한 제안 하나

`farm_customers.csv` 의 20곳이 **전부 `ACTIVE`** 입니다.
그런데 확인 항목에는 이런 것이 있습니다.

- 거래 중 / 거래 보류 변경이 동작하는지
- 월 예상 사료량이 **거래 중 고객사만** 합산하는지

`PAUSED` 가 하나도 없으면 **거래 상태 필터가 무엇도 걸러내지 못하고**,
`totalMonthlyFeedQuantity()` 의 `filter(status == ACTIVE)` 가 실제로 동작하는지
확인할 수 없습니다(전부 ACTIVE 라 필터가 있으나 없으나 결과가 같습니다).

제가 이번에 똑같은 일을 겪었습니다. 시드에 입고 대기 구역 재고가 0 이라
`+ 대기 구역 N포대 별도` UI 가 **한 번도 렌더링된 적이 없었습니다.**
그걸 채우려다 검수 전 재고가 출고 가능 상태로 잡히는 버그를 찾았습니다
(`docs/retrospective.md` 1장에 과정을 적어 두었습니다).

**CSV 두 줄만 `PAUSED` 로 바꾸시면** 필터와 합산이 동작하는 증거가 생깁니다. 1분이면 됩니다.

---

## 제안하는 순서

1. 위 ①~④ 에 대한 병래님 의견을 듣습니다 (특히 ① 좌표와 ② 명명)
2. 정해진 방향으로 스키마 매핑 작업을 제가 맡겠습니다
   (`FarmCustomer` → `Center` 참조 변경 · 축종 enum 변환 · 컬럼명 통일)
3. 그 다음에 기능을 옮깁니다. 서로 없는 것이 많으니 이쪽이 실제 이득입니다

바로 코드를 합치기보다 ①~④ 를 먼저 정하는 편이 좋겠습니다.
정하지 않고 붙이면 나중에 FK 를 다 손봐야 해서 되돌리기 어렵습니다.

궁금한 점은 `docs/` 를 봐 주세요. `README.md` 에 아키텍처와 화면 목록이 있고,
`docs/retrospective.md` 에 왜 이렇게 만들었는지를 적어 두었습니다.

— 용우

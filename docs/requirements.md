# FeedFlow 요구사항 정의서

배합사료 유통 관리 플랫폼의 **관리자 시스템(Admin) 및 대시보드 모듈** 요구사항과 구현 현황.

- **개발 범위**: 관리자/창고(WMS) 기능만. B2C 농가용 쇼핑몰 기능은 이 저장소의 범위가 아니다.
- **기술 스택**: Java 21 / Spring Boot 3.5.16 / Gradle / H2(in-memory) / Spring Data JPA / Thymeleaf / Bootstrap 5 / Chart.js
- **DB 명명 규칙**: 테이블·컬럼 모두 camelCase (`.kiro/steering/db-naming-convention.md` 참고)
- **ERD**: [docs/erd.md](erd.md) (Mermaid), `docs/erd-columns.tsv` · `erd-relations.tsv` · `erd-enums.tsv`

## 표기 규칙

| 표기 | 뜻 |
|---|---|
| 완료 | 기능이 구현되어 화면/API로 동작하며 단위 테스트가 있다 |
| 부분 | 도메인·서비스는 있으나 화면 또는 실행 경로가 없다 |
| 미착수 | 코드가 존재하지 않는다 |

커밋 해시는 해당 기능이 **처음 도입된** 커밋이다. 이후 수정·개선 커밋은 별도로 적지 않았다.

> 로컬 저장소는 shallow clone(depth=1) 상태라 `001d65d` 이전 이력이 없다. 초기 구축 단계에서
> 한꺼번에 들어온 기능들은 모두 `001d65d`(128개 파일)로 표기한다. 정확한 최초 커밋이 필요하면
> GitHub 저장소에서 전체 이력을 확인해야 한다.

## Must Have (필수)

| # | 기능 | 상태 | 커밋 | 주요 경로 |
|---|---|---|---|---|
| M1 | 관리자 로그인 / 권한 체계 (`ADMIN` · `STAFF` · `USER`) | 완료 | `001d65d` | `/login`, Spring Security, `LoginUser` |
| M2 | 대시보드 — 권한별 뷰 분리 | 완료 | `001d65d` | `/admin/dashboard` |
| M2-1 | └ 안전재고 미달 알림 (STAFF·ADMIN) | 완료 | `001d65d` | `DashboardService` |
| M2-2 | └ 유통기한 임박 알림 (30일 기준, STAFF·ADMIN) | 완료 | `001d65d` | `feedflow.dashboard.expiration-alert-days` |
| M2-3 | └ 오늘의 할 일 — 신규 주문 · 출고 대기 건수 | 완료 | `001d65d` | `DashboardService` |
| M2-4 | └ 매출 통계 요약 (ADMIN 전용) | 완료 | `001d65d` | `sec:authorize="hasRole('ADMIN')"` |
| M2-5 | └ 최근 7일 매출 추이 차트 (ADMIN 전용, 비동기) | 완료 | `001d65d` | `AdminRestController`, Chart.js |
| M3 | 사원 계정 · 권한 변경 (ADMIN 전용) | 완료 | `001d65d` | `/admin/employees` |
| M4 | 품목 관리 (등록 · 수정 · 사용중지) | 완료 | `001d65d` | `/admin/products` |
| M4-1 | └ 축종 3종(소·돼지·조류) · 품목구분(사료·영양제) enum 전환 | 완료 | `6343f60` | `AnimalType`, `ProductType` |
| M5 | 창고 구역(Bin) 관리 | 완료 | `001d65d` | `/admin/bins` |
| M6 | 재고 현황 조회 (품목 · 구역 · 존 필터) | 완료 | `001d65d` | `/admin/inventory` |
| M7 | **입고 등록** — 로트 생성/합산, 유통기한 자동 계산, 구역 한도 검증 | 완료 | `001d65d` | `/admin/inventory/inbound`, `InventoryService.receive()` |
| M8 | 재고 폐기 (ADMIN 전용, 사유 필수) | 완료 | `001d65d` | `/admin/inventory/disposal` |
| M9 | 입출고 이력 조회 (유형 · 품목 필터, 페이징) | 완료 | `001d65d` | `/admin/inventory/movements` |
| M10 | FEFO(선입선출) 주문 출고 — 할당 미리보기 → 처리 | 완료 | `001d65d` | `/admin/outbound`, `OutboundService` |
| M10-1 | └ 주문 목록 상태 필터 (출고대기 · 출고완료 · 배송완료 · 취소 · 전체) | 완료 | `e83f8ac` | `OrderListFilter` |
| M11 | 직접 출고 (주문과 무관한 출고) | 완료 | `001d65d` | `/admin/outbound/direct` |
| M12 | 출고(주문) 취소 + 재고 원상 복구 (ADMIN 전용) | 완료 | `f47db83` | `OrderCancellationService` |
| M12-1 | └ 취소 사유 · 일시 · 처리자 기록 및 화면 표시 | 완료 | `e058e22` | `Order.cancel(reason, userId, userName)` |
| M13 | 바코드/QR 스캔 입출고 | 완료 | `001d65d` | `/admin/scan` |
| M14 | QR 라벨 출력 | 완료 | `001d65d` | `/admin/scan/labels` |
| M15 | 재고 정합성 점검 · 보정 (진단 STAFF·ADMIN / 보정 ADMIN) | 완료 | `79d69ef` | `/admin/inventory/sync` |
| M16 | 낙관적 락(`@Version`) 동시성 제어 | 완료 | `001d65d` | `Product` · `ProductLot` · `Inventory` |
| M17 | 예외 처리 화면 (404 · 403 · 409 · 4xx · 5xx) | 완료 | `001d65d` | `AdminViewExceptionHandler`, `templates/error/` |
| M18 | ERD 문서화 | 완료 | `928caf4`, `0959196` | `docs/erd*.tsv`, `docs/erd.md` |

## Should Have (권장)

| # | 기능 | 상태 | 커밋 | 주요 경로 |
|---|---|---|---|---|
| S1 | **창고 2D 도면 맵** | 완료 | `eef3f0f` | `/admin/warehouse-map` |
| S1-1 | └ 좌표 기반 자유 배치 평면도 (26×14 격자), 창고 2동 탭 | 완료 | `54a9f34` | `Warehouse`, `posX/posY/posWidth/posHeight` |
| S1-2 | └ 구역 용도 구분 (보관 · 입고대기 · 출고대기 · 검수) | 완료 | `54a9f34` | `BinPurpose` |
| S1-3 | └ 저온(COLD) / 상온 구역 경계 표시 | 완료 | `4ed837d` | `warehouse/map.html` |
| S1-4 | └ 적재율 색상 구분 · 타일 클릭 상세 모달 | 완료 | `eef3f0f` | `WarehouseMapApiController` |
| S1-5 | └ 사용률 산정 정책 확정 (보관 구역만 분모) | 완료 | `fb0667c` | `WarehouseMapSummaryDto` |
| S2 | **제품 이력 추적 뷰어** | 완료 | `311a9a1` | `/admin/traceability` |
| S2-1 | └ 로트번호 검색 → 생애주기 수직 타임라인 | 완료 | `311a9a1` | `TraceabilityService` |
| S2-2 | └ 시점별 잔여 수량 누적 (`MovementType.sign` 활용) | 완료 | `311a9a1` | `TraceEventDto` |
| S2-3 | └ 이력–재고 불일치 경고 | 완료 | `311a9a1` | `TraceabilityDto` |
| S2-4 | └ JSON API (타 화면 팝업용) | 완료 | `311a9a1` | `/api/admin/traceability/lots/{lotId}` |
| S3 | **구역 간 재고 이동 (MOVE)** | 완료 | `a48bb5e` | `/admin/inventory/move` |
| S3-1 | └ 총 재고 불변 (위치만 변경) — `lotQuantity`·`totalStock` 미변경 | 완료 | `a48bb5e` | `InventoryMoveService` |
| S3-2 | └ 출발/도착 구역 검증 (같은 구역·수량 초과·사용중지·적재 한도) | 완료 | `a48bb5e` | `WarehouseBin.canAccept()` 재사용 |
| S3-3 | └ 이력에 출발지+도착지 기록 | 완료 | `a48bb5e` | `StockMovement.fromBin` |
| S3-4 | └ 2D 도면 모달에서 이동 화면 진입 | 완료 | `a48bb5e` | `warehouse-map.js` |
| S3-5 | └ 이력 추적 타임라인에 `A-01 → B-02` 표시 | 완료 | `a48bb5e` | `TraceEventDto.isRelocation()` |

## Epic: 전국 다중 창고 기반 물류망 확장 (진행 중)

WMS 1.0 은 **단일 물류센터 안에 창고 건물 2동(`WH1` · `WH2`)** 을 전제로 만들었다.
이를 **전국 여러 센터(Center)** 구조로 확장한다. 덩치가 커서 4단계로 나눈다.

| Phase | 범위 | 상태 |
|---|---|---|
| **P1** | 기초 공사 — `Warehouse` enum → `Center` 엔티티 승격, 구역(Bin) 매핑 전환 | 진행 중 |
| **P2** | 검색 및 조회 — 재고·이력 조회에 센터 필터 조건 추가 | 대기 |
| **P3** | 센터 간 이동 — `TRANSFER_OUT` · `TRANSFER_IN` 신설, 운송 중(In-Transit) 처리 | 대기 |
| **P4** | 대시보드 및 라우팅 — 센터별 수요 시각화, 스마트 주문 할당 | 대기 |

### 확정된 정책

| 항목 | 결정 | 근거 |
|---|---|---|
| **`Product.totalStock`** | **전국 합계로 유지** | B2C 쇼핑몰이 이 컬럼을 '판매 가능 수량' 으로 쓰고 있다. 센터별로 쪼개면 연동이 깨진다. 센터별 가용 재고는 **동적 쿼리 · 별도 집계**로 푼다. |
| 재고 3계층 불변식 | 유지 | `totalStock` = Σ`lotQuantity` = Σ`Inventory.quantity`. 센터가 늘어도 성립한다(전국 합계 기준). |
| 센터 간 이동에 `MOVE` 재사용 | **금지** | `MOVE` 는 sign 0(총량 불변)이지만 센터 간에는 한쪽 센터의 재고가 실제로 줄어든다. P3 에서 별도 유형을 만든다. |

### Phase 1 상세

| # | 항목 | 상태 | 비고 |
|---|---|---|---|
| P1-1 | `Center` 엔티티 신규 (센터ID · 코드 · 센터명 · 지역 · 주소 · 활성 여부) | 진행 중 | `Warehouse` enum 제거 |
| P1-2 | `WarehouseBin` → `Center` `@ManyToOne` 참조 전환 | 진행 중 | `warehouse` enum 컬럼 → `centerId` FK |
| P1-3 | 조회 쿼리에 `join fetch center` 적용 (N+1 방지) | 진행 중 | DTO 가 센터명을 표시하므로 필수 |
| P1-4 | `data.sql` 전면 수정 (`centers` 시드 + 구역 40행 매핑) | 진행 중 | 정합성 4규칙 유지 |
| P1-5 | 2D 도면 · 구역 관리 · 선택 목록 화면의 센터 참조 전환 | 진행 중 | 창고 탭 → 센터 탭 |

> **Phase 1 은 '구조 전환' 이므로 데이터의 의미는 바꾸지 않는다.**
> 센터 코드(`WH1`/`WH2`)와 이름(제1창고/제2창고)을 그대로 유지해, 화면 결과가
> 이전과 동일한지로 회귀를 판정할 수 있게 한다. 전국 센터 코드 체계 재정의는
> P2 이후에 데이터만 교체하면 된다.

### Phase 1 에서 의도적으로 미루는 것

| 항목 | 이유 |
|---|---|
| 도면 격자 크기를 센터별 속성으로 이관 | `WarehouseBinForm` 의 `@Max(GRID_COLUMNS)` 가 컴파일 상수를 요구해 폼 검증 방식까지 바꿔야 한다. 센터마다 건물 크기가 다른 것은 사실이지만 P1 범위를 넘는다. |
| 부대시설(출입구·벽·검수실) 테이블 이관 | 현재 `WarehouseFacilityDto` 의 코드 기반 분기로 동작한다. 센터가 늘면 `centerFacilities` 테이블이 필요하지만 P1 에서는 센터 코드로 분기만 바꾼다. |

## Could Have (선택)

우선순위 순. 아직 착수하지 않았다.

| # | 기능 | 상태 | 설명 |
|---|---|---|---|
| C1 | **발주(Purchase Order) 관리** | 미착수 | 입고 처리(M7) 자체는 이미 구현되어 있다. 미구현인 것은 그 **앞단**이다: 공급업체(Supplier) 관리, 발주서 생성·승인, 발주 대비 입고 대조(부분 입고·초과 입고 판정), 매입 단가/금액 집계. `Supplier`·`PurchaseOrder` 엔티티가 없어 신규 도메인 추가가 필요하다. |
| C2 | 재고 실사(Stocktaking) 및 차이 조정 | 미착수 | M15는 장부끼리(`Product.totalStock` vs `ProductLot.lotQuantity` 합계) 비교다. 실물 카운트를 입력해 장부와의 차이를 조정하는 절차는 없다. `MovementType.ADJUST`(sign 0)가 이미 정의되어 있으나 사용처가 없다. |
| C3 | ~~구역 간 재고 이동~~ | **완료** | Should Have 표의 S3 항목으로 이동했다. |
| C4 | 매출/출고 리포트 확장 | 부분 | 대시보드 차트는 최근 7일 고정(`feedflow.dashboard.chart-default-days`)이다. 기간 지정, 축종별·품목별·고객별 집계, CSV 내보내기가 없다. |

## 명시적 제외 범위

| 항목 | 이유 |
|---|---|
| B2C 농가용 쇼핑몰 (상품 목록, 장바구니, 주문/결제) | 별도 모듈 담당. 통합 DB만 공유한다. `Product.imageUrl`·`description`은 쇼핑몰 연동용으로 엔티티에는 두되 관리자 화면에는 노출하지 않는다. |
| 반품(Return) 절차 | 배송 완료(`DELIVERED`) 주문은 취소 대상이 아니다. 실물이 고객에게 있어 창고 재고를 되돌리면 실물과 장부가 어긋난다. |
| 주문 상태 전이 전체 이력 | 취소는 주문당 한 번뿐이라 `Order`의 컬럼으로 충분하다고 판단했다. 모든 상태 전이 기록이 필요해지면 `OrderStatusHistory` 신설을 검토한다. |
| 재고 정합성 드리프트 누적 기록 | H2 in-memory + `ddl-auto=create`라 재시작마다 초기화되어 누적 자체가 불가능하다. |

## 검증 상태

- 단위/통합 테스트 **181건** (`src/test`, 16개 테스트 클래스)
- 시드 데이터(`data.sql`)는 4가지 정합성을 만족한다. 변경 시 함께 검증해야 한다.
  1. 이력 누적(`SUM(StockMovement × sign)`) = `ProductLot.lotQuantity`
  2. 구역 재고 합계(`SUM(Inventory.quantity)`) = `ProductLot.lotQuantity`
  3. 품목 장부(`Product.totalStock`) = 품목별 로트 수량 합계
  4. 구역별 적재량 ≤ `WarehouseBin.maxCapacity`
- 개발 샌드박스는 외부 네트워크가 차단(`INTEGRATIONS_ONLY`)되어 Gradle 의존성을 받을 수 없다.
  **컴파일·테스트 실행은 로컬 환경에서만 가능**하며, 샌드박스에서는 정적 검사(import 누락/미사용,
  중괄호 균형, 템플릿–DTO 프로퍼티 대조, 태그 중첩)로 대체한다.

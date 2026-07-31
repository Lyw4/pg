# 고객 농장 관리 모듈 전달본

이 압축본은 유통 관리 화면의 **고객 농장 탭**만 팀원 프로젝트에 합칠 수 있도록
분리한 자료입니다. 현재 프로젝트의 실제 H2 데이터베이스에서 추출한 창고 5곳과
농장 20곳 데이터도 함께 들어 있습니다.

## 포함 범위

- 농장 고객사 엔티티·Repository·Service·초기 데이터 Seeder
- 담당 창고 엔티티·Repository 의존 파일
- 유통 관리 Controller에 추가할 코드 조각
- 고객 농장 KPI 버튼, 관리 패널, 검색·필터·상태 변경 JavaScript, CSS
- H2용 현재 데이터 SQL과 검토용 CSV
- 선택 기능: 고객 농장을 선택해 테스트 주문 폼을 자동 입력하는 연결 코드 안내

## 폴더 구성

```text
backend/src/main/java/com/ex/
  entity/FarmCustomer.java
  repository/FarmCustomerRepository.java
  service/FarmCustomerService.java
  service/FarmCustomerSeeder.java
backend/dependencies/
  Warehouse.java
  WarehouseRepository.java
integration/
  ManagementController-farm-snippet.java.txt
  DataInitializer-farm-snippet.java.txt
  delivery-view-integration.js.txt
  optional-farm-order-integration.md
frontend/
  farm-kpi-button.html
  farm-customer-panel.html
  farm-customer.js
  farm-customer.css
  optional-demo-order-fields.html
  optional-farm-order.js
data/
  warehouses.csv
  farm_customers.csv
  farm_customer_upsert.sql
  fresh_database_import.sql
```

## 가장 안전한 적용 순서

1. `backend/src/main/java/com/ex/` 아래 4개 파일을 팀원 프로젝트의 같은 패키지에
   복사합니다.
2. 팀원 프로젝트에 `Warehouse`와 `WarehouseRepository`가 이미 있으면
   `backend/dependencies/` 파일로 덮어쓰지 말고 메서드·필드가 호환되는지만
   확인합니다. 없을 때만 복사합니다.
3. `ManagementController-farm-snippet.java.txt`를 보고 기존 Controller에 import,
   필드, 생성자 주입, Model 속성, 상태 변경 POST 메서드를 합칩니다.
4. `farm-kpi-button.html`은 유통 현황 KPI 영역 안에 넣고,
   `farm-customer-panel.html`은 다른 `data-delivery-panel`들과 같은 위치에 넣습니다.
5. `farm-customer.css`를 기존 `app.css` 마지막에 추가합니다.
6. `farm-customer.js`를 정적 JS로 등록해 `defer`로 불러오거나 기존 `app.js`에
   내용을 합칩니다.
7. `delivery-view-integration.js.txt`를 보고 기존 유통 탭 전환 코드에 `farms`
   패널 처리를 추가합니다.
8. 아래 데이터 이관 방법 중 하나를 선택한 뒤 애플리케이션을 실행합니다.

필요한 기본 라이브러리는 Spring Web, Spring Data JPA, Thymeleaf, Lombok,
Jakarta Persistence입니다. 원본 프로젝트와 같은 Spring Boot 구성이라면
추가 의존성 설치는 필요하지 않습니다.

## 데이터 이관

### 방법 A: 기존 DB에 병합 — 권장

`farm_customer_upsert.sql`은 `warehouse_code`와 `farm_code`를 기준으로
현재 데이터를 추가하거나 갱신합니다. 다른 테이블은 삭제하지 않습니다.

H2 Shell을 모듈 폴더에서 실행하는 예:

```powershell
java -cp h2-2.x.x.jar org.h2.tools.Shell `
  -url "jdbc:h2:file:팀원DB경로" `
  -user sa `
  -sql "RUNSCRIPT FROM 'data/farm_customer_upsert.sql' CHARSET 'UTF-8'"
```

SQL 안의 CSV 경로는 모듈 폴더를 현재 작업 폴더로 실행한다는 기준입니다.
다른 위치에서 실행하면 SQL 파일 안의 `data/warehouses.csv`,
`data/farm_customers.csv` 경로를 맞게 수정합니다.

### 방법 B: 완전히 새 DB에 원본 그대로 생성

`fresh_database_import.sql`은 H2 테이블 정의, PK·FK, 원본 ID, 생성 시각까지
포함한 전체 스크립트입니다. **동일한 테이블이 이미 존재하는 DB에는 실행하지
마세요.** 새 빈 H2 DB에만 사용합니다.

### 방법 C: 애플리케이션 Seeder 사용

`DataInitializer-farm-snippet.java.txt`처럼 창고 데이터가 만들어진 다음
`farmCustomerSeeder.seed()`를 호출합니다. 다만 현재 DB에서는 20곳이 모두
`ACTIVE`이고, 코드의 최초 시드 기본값은 시연을 위해 18곳 `ACTIVE`,
2곳 `PAUSED`입니다. 현재 상태까지 똑같이 옮기려면 방법 A 또는 B를 사용합니다.

## 화면 의존성

HTML은 원본 유통 관리 화면의 공통 스타일 클래스인 `panel`, `panel-title`,
`summary-eyebrow`, `summary-count`, `category-tabs`, `category-tab`, `table-wrap`,
`ghost`를 사용합니다. 팀원이 같은 프로젝트 코드를 기반으로 작업한다면 이미
존재합니다.

`farm-customer-panel.html`의 **이 농장으로 주문** 버튼은 선택 기능입니다.
팀원 쪽에 테스트 주문 모달이 없으면 해당 버튼 블록을 제거해도 고객 농장 조회,
검색, 필터, 거래 상태 변경에는 영향이 없습니다.
주문 모달도 함께 연결할 때는 `optional-demo-order-fields.html`과
`optional-farm-order.js`, `integration/optional-farm-order-integration.md`를
적용합니다.

## 확인 항목

- `/distribution?view=farms`에서 고객 농장 패널이 열리는지
- 전체 20곳과 담당 창고 5곳이 표시되는지
- 창고·축종·거래 상태·키워드 필터가 함께 동작하는지
- 거래 중/거래 보류 변경 후 다시 `view=farms`로 돌아오는지
- 월 예상 사료량이 거래 중 고객사만 합산하는지

농장명, 담당자, 전화번호, 주소는 실제 업체 정보가 아닌 발표·기능 검증용
가상 데이터입니다.

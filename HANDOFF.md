# 인계 문서 (HANDOFF)

> 이 문서만 읽으면 작업을 이어받을 수 있도록 썼습니다.
> 마지막 갱신 : 2026-07-31 · 브랜치 `feat/farm-customer-integration` · 커밋 `6bbd0d3`

---

## 1. 무엇을 만드는 프로젝트인가

**FeedFlow** — 배합사료 유통 관리 플랫폼의 **관리자 시스템(WMS)** 모듈입니다.
고객 주문 화면(B2C)은 팀원이 담당하며 이 저장소에 없습니다.

- 저장소 : `Lyw4/pg`
- 프로젝트 루트 : `<repo>/feedflow` (Gradle 프로젝트는 여기입니다. 저장소 루트가 아닙니다)
- 스택 : Java 21 · Spring Boot 3.5.16 · Gradle · H2(in-memory) · Spring Data JPA ·
  Thymeleaf · Bootstrap 5 · Chart.js · Leaflet

```
<repo>/
├── feedflow/          ← Gradle 프로젝트 (gradlew 는 여기)
│   └── src/main/java/com/feedflow/
│       ├── domain/            엔티티 · enum (26)
│       ├── repository/        Spring Data JPA (11)
│       ├── admin/dto/         DTO · Form (77)
│       ├── admin/service/     서비스 (16)
│       ├── admin/controller/  컨트롤러 (23)
│       ├── common/            예외 · 유틸
│       ├── config/
│       └── security/
├── docs/              설계 · ERD · 회고 · 산출물 TSV
├── tools/             정적 검증 스크립트 7종 (Python 3)
├── README.md          기능 · 어필 포인트
├── INTEGRATION-NOTES.md   팀원 모듈 통합 판단 기록
└── HANDOFF.md         ← 이 문서
```

**현재 규모** : 프로덕션 클래스 165 · 템플릿 29 · 테스트 315(24클래스) ·
테이블 12 / 컬럼 132 · 검증 스크립트 7종

---

## 2. 환경 제약 — 먼저 읽으세요

이 세 가지를 모르면 시간을 크게 낭비합니다.

### ① 샌드박스에서 빌드할 수 없습니다

네트워크가 `INTEGRATIONS_ONLY` 로 차단되어 Gradle 이 의존성을 받지 못합니다.
`gradlew build` 는 **사용자의 로컬 PC 에서만** 가능합니다.

> 대신 `tools/` 의 정적 검사로 컴파일 오류를 최대한 걸러냅니다.
> **작업 후 반드시 검증 7종을 돌리고, 사용자에게 로컬 빌드를 요청하세요.**

사용자의 로컬 경로 (참고) :
`C:\Users\dl_dy\Desktop\blid\pg-feat-farm-customer-integration\feedflow`

### ② bash 로 `git push` · `git fetch` 가 불가능합니다 (인증 실패)

반드시 `kiro_powers` 의 github 도구를 쓰세요.

```
kiro_powers action=use powerName=github serverName=github toolName=push_to_remote
  arguments={"owner":"Lyw4","repository_name":"pg",
             "path":"/projects/sandbox/pg",
             "remote_branch_name":"feat/farm-customer-integration"}
```

`git add` · `git commit` · `git log` · `git status` 는 bash 로 정상 동작합니다.

### ③ `/tmp` 가 세션 중에 비워집니다

백업을 `/tmp` 에 두면 사라집니다. 파일을 실험적으로 고칠 때는
**되돌리는 방법(sed 명령 등)을 미리 확보**하고 시작하세요.

---

## 3. 작업을 시작할 때 실행할 명령어

```bash
cd /projects/sandbox/pg/feedflow

python3 ../tools/compile_risk_check.py      # 컴파일 리스크 (타입·빌더·record·enum·JPQL·메서드·템플릿)
python3 ../tools/static_check.py            # 정적 검사 14종
python3 ../tools/stub_check.py              # Mockito STRICT_STUBS
python3 ../tools/verify_seed.py             # 시드 정합성 5규칙 + 표시 품질
python3 ../tools/verify_farm_seed.py        # 농장 시드
python3 ../tools/verify_defect_seed.py      # 불량 · 제조사 시드
python3 ../tools/verify_test_expectations.py  # 테스트 기대값 ↔ 프로덕션 계산
python3 ../tools/deadcode_scan.py           # 미사용 메서드 (실패 아님, 보고용)
```

전부 통과해야 정상입니다. 기대 출력은 각 스크립트 마지막 줄입니다
(예: `컴파일 리스크 검사 통과`, `불량 시드 검증 통과`).

### 검사기 수정 시 반드시 지킬 것

스크립트 원본은 `/projects/scratch/` 에서 편집하고 **저장소로 복사**해야 합니다.
복사를 잊으면 저장소 버전이 낡은 상태로 커밋됩니다.

```bash
cp /projects/scratch/compile_risk_check.py /projects/sandbox/pg/tools/
```

### 새 클래스 · 템플릿을 추가하면 검사기에 등록하세요

`tools/compile_risk_check.py` 안의 두 곳입니다. 등록하지 않으면 **검사 대상에서 빠지고
"통과" 라고 출력됩니다.**

- `TARGET_PREFIXES` — 파일명 접두어 (현재 `'Manufacturer'`, `'Defect'` 등)
- `TEMPLATE_VARS` — 템플릿별 `{변수명: DTO클래스명}` 매핑

---

## 4. 현재 상태와 남은 일

### Git

- 작업 브랜치 : `feat/farm-customer-integration`
- base 브랜치 : `feat/feedflow-admin-dashboard` (main 이 아닙니다)
- **PR #20 open** — 커밋 8개. 로컬 빌드 성공 확인됨 (`BUILD SUCCESSFUL`, 테스트 315개)
- **PR #19 는 닫아야 합니다** — 내용이 #20 에 cherry-pick 으로 흡수됐습니다.
  열어둔 채 머지하면 같은 변경이 두 번 들어갑니다
- PR #16 · #18 의 **제목**에 한글이 깨져 있습니다(`테이붔`, `산우물`).
  파일과 커밋 메시지는 정상입니다. 웹에서 제목만 수정하면 됩니다

> **PR 제목 · 본문에 한글을 유니코드 이스케이프(`\uXXXX`)로 변환하지 마세요.**
> #16 · #18 이 그렇게 깨졌습니다. 한글을 그대로 넣으면 정상입니다.

### 완료된 기능 (전부 검증 통과)

| 영역 | 내용 |
|---|---|
| 대시보드 | 권한별 분기 · 안전재고 · 유통기한 · 매출 추이 · 전국 물류망 |
| 재고 | 3계층(품목·로트·구역) · FEFO 출고 · 구역 간 이동 · 센터 간 이관 · 폐기 · 정합성 점검 |
| 창고 | 전국 지도(Leaflet) + 센터별 2D 평면도 양방향 연동 |
| 추적 | 로트 생애주기 타임라인 |
| 스캔 | 바코드 입출고 · QR 라벨 |
| **농장 고객사** | 팀원 모듈 통합 ① — 센터별 담당 농장 · 월 예상 사료량 |
| **수요 계획** | 농장 수요 ↔ 센터 재고 축종별 대조 |
| **불량 관리** | 팀원 모듈 통합 ② — 격리 → 검사 → 처리, 제조사별 집계 |

### 팀원 모듈 통합 현황

- 통합 완료 : 농장 고객사(`farmCustomers`), 불량 관리(`manufacturers` + `defectRecords`)
- **통합 거부 : `byeongrae-kim/Final-Project` 의 `member,-homepage` 브랜치(B2C 전체)**
  근거는 `INTEGRATION-NOTES.md` 에 상세히 적혀 있습니다. 요약하면
  `AnimalType` 값 체계 불일치(우리 3 vs 팀원 7), H2 `MODE=PostgreSQL` 의 식별자
  소문자 폴딩이 우리 camelCase 컬럼과 충돌, `MemberRole` 에 `STAFF` 없음,
  `Member` 가 농장 정보를 포함해 `FarmCustomer` 와 중복.
  **다시 통합을 시도하기 전에 그 문서를 읽으세요.**
- 팀원 저장소는 **읽기만 가능**합니다 (push · PR 생성 403)

### 남은 작업 후보 (우선순위 없음)

1. 제조사 관리 화면 (`/admin/manufacturers`) — 현재 제조사는 시드로만 존재하고
   등록 · 수정 화면이 없습니다. 품목 등록 폼에 제조사 선택도 아직 없습니다
2. 불량 → 폐기 연결 버튼 — 지금은 안내 문장만 있고, 폐기 화면으로 값을 넘기지 않습니다
3. 센터 간 이관 전표(`P3b`) — 두 이력을 잇는 전표 번호가 없어 메모로 경로를 남기는 상태
4. 반품 절차 — 의도적으로 만들지 않았습니다. 만들 거면 `MovementType` 을 늘리지 말고
   기존 폐기를 쓰는 현재 방식을 먼저 검토하세요 (근거는 회고 7장)

---

## 5. 되돌리면 안 되는 설계 결정

"중복 제거" 나 "편의성" 을 이유로 되돌리기 쉬운 것들입니다. 각각 이유가 있습니다.
상세 근거는 `docs/retrospective.md` 와 각 클래스 JavaDoc 에 있습니다.

| 결정 | 되돌리면 생기는 일 |
|---|---|
| **재고를 줄이는 코드는 폐기 기능 하나뿐** (불량 처리는 재고를 안 건드림) | 두 곳이 되면 한쪽만 고쳤을 때 재고는 줄었는데 이력이 없거나 그 반대가 되고, 어느 쪽이 맞는지 알 수 없음 |
| **출고 차단은 구역 용도(`BinPurpose`)만 한다** (불량 상태로 막지 않음) | 규칙이 두 곳에 생겨 구역만 옮겼는데 여전히 막히거나 그 반대가 됨 |
| `DefectType` 과 `DisposalReason` 을 **합치지 않음** | "재고 실사 손실" 이라는 불량 유형이 생김. 불량은 폐기로 안 갈 수 있고(특채·재작업) 폐기 사유엔 불량 무관 값이 있음 |
| `DefectStatus` 는 **역행 불가** | 재발을 한 건에 덮어쓰면 "이 로트에서 몇 번 나왔나" 를 셀 수 없어 공급업체 평가 근거가 뭉개짐 |
| 불량 목록 정렬 = **미처리 우선 + 오래된 것부터** | 최신순이면 가장 오래 방치된 건이 목록 끝으로 밀려 영원히 안 보임 |
| `Product.manufacturer` **nullable** + 집계에서 `coalesce '미등록'` | 필수로 하면 기존 품목 13개를 넣을 수 없음. 집계에서 제외하면 합계가 전체와 안 맞고 등록 필요성도 안 드러남 |
| `MovementType` 에 `RETURN` 같은 유형 **추가 안 함** | 이미 8종이고 각각 세는 곳이 여러 군데라 매입 실적 집계에서 빠뜨릴 위험 |
| `BinPurpose` 에 '출고 가능' 축 **추가 안 함** | JPQL 이 enum 메서드를 호출할 수 없어 진실이 두 곳으로 갈라짐 |
| 이력 스냅샷은 **FK 가 아님** (`userName`, `reportedByName` 등) | 담당자가 퇴사하면 이력이 사라짐 |
| `IN_TRANSIT` **가상 구역**으로 운송 중 재고 보관 | 전표 테이블만 쓰면 3계층 불변식이 운송 중에 깨져 정합성 점검에 예외를 넣어야 하고, 그 예외가 진짜 어긋남을 가림 |

---

## 6. 코드 작성 규칙 (기존 코드와 맞추세요)

### 계층

- 엔티티를 템플릿으로 직접 넘기지 않습니다. **반드시 DTO 변환**
- HTML 렌더링은 `@Controller`, JSON 은 별도 `@RestController`
- 통계 · 집계는 자바 반복문이 아니라 **JPQL `@Query` 로 DB 에서** 계산

### 권한

- `/admin/**` 은 `hasAnyRole("STAFF","ADMIN")`
- 책임자 전용은 `@PreAuthorize("hasRole('ADMIN')")` + 템플릿 `sec:authorize` **이중 차단**
  (화면에서 숨기는 것만으로는 주소 직접 호출을 막지 못함)
- 판단 기준 : **기록하는 행위는 STAFF 도 가능하게, 되돌릴 수 없거나 비용이 나가는
  결정은 ADMIN.** 등록을 막으면 담당자가 기록을 아예 남기지 않게 되고 데이터가 비어버립니다

### 컨트롤러

```java
@PostMapping
public String register(@Valid @ModelAttribute("xxxForm") XxxForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal LoginUser loginUser,
                       RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
        redirectAttributes.addFlashAttribute(FlashAttr.ERROR, firstError(bindingResult));
        return "redirect:/admin/xxx";
    }
    String userName = LoginUser.nameOf(loginUser);   // Long userId = LoginUser.idOf(loginUser)
    try {
        ...
        redirectAttributes.addFlashAttribute(FlashAttr.SUCCESS, 결과메시지);
    } catch (BusinessRuleException | ResourceNotFoundException e) {
        redirectAttributes.addFlashAttribute(FlashAttr.ERROR, e.getMessage());
    }
    return "redirect:/admin/xxx";   // PRG 패턴
}
```

- 플래시 키는 문자열 직접 사용 금지 → `FlashAttr.SUCCESS / ERROR / INFO`
- `model.addAttribute("menu", "camelCase키")` — 사이드바 활성 표시용

### 템플릿

```html
<head th:replace="~{fragments/layout :: head('제목')}"></head>
<div th:replace="~{fragments/layout :: sidebar}"></div>
<div th:replace="~{fragments/layout :: topbar('상위메뉴', '현재메뉴')}"></div>
<div th:replace="~{fragments/layout :: scripts}"></div>
```

메뉴 추가는 `templates/fragments/layout.html` 을 수정합니다.

> **함정 : record DTO 는 `${stat.label()}` 처럼 메서드 호출 문법으로 써야 합니다.**
> record 접근자는 `getLabel()` 이 아니라 `label()` 이라서 `${stat.label}` 은 실패합니다.
> `#numbers.formatInteger(stat.amount(), 1, 'COMMA')` 안에서도 마찬가지입니다.

### 폼 · DTO

- Form : `@Getter @Setter @NoArgsConstructor` + `@NotNull(message="...")` `@Min` `@Size`
- 조회 DTO : `@Getter @Builder` + 정적 팩토리 `of(Entity)`
- 검증 메시지는 담당자가 **무엇을 해야 하는지** 알 수 있게 씁니다
  (예: "입고 대기 구역의 재고는 구역 간 이동으로 보관 구역에 넣어야 출고할 수 있습니다")

### 네이밍

- Java 필드 · 메서드 · JSON 키 : **camelCase** 엄수
- DB 테이블 · 컬럼 : 이 프로젝트는 **camelCase 컬럼명을 명시**합니다
  (`@Column(name = "createdAt")`). 스네이크로 바꾸지 마세요 — `data.sql` 과 어긋납니다

### 주석 · JavaDoc

이 프로젝트는 **왜 그렇게 했는지**를 코드에 남깁니다. 특히
① 거부한 대안과 그 이유 ② 이 규칙이 없으면 생기는 구체적 사고
③ 다른 곳과 중복되어 보이는데 분리한 근거.
새 코드도 같은 밀도로 써야 리뷰에서 일관성이 깨지지 않습니다.

---

## 7. `data.sql` 수정 시 주의

`feedflow/src/main/resources/data.sql` (642줄, 섹션 0~12)

```
0 centers          5개
1 users            5개 (ADMIN 김책임 / STAFF 이사원 / USER 3)
2 manufacturers    5개 (id 5 는 거래 중지)
3 products        13개 (id 10 은 manufacturerId 일부러 NULL)
4 productLots     34개
5 orders          15개
6 orderItems      16개
7 warehouseBins   51개 (47~51 은 IN_TRANSIT 가상 구역)
8 inventories     59개
9 stockMovements  85개
10 farmCustomers  20개
11 defectRecords   7개
12 IDENTITY RESTART
```

### 규칙

1. **FK 부모가 먼저** 와야 합니다 (제조사 → 품목, 센터 → 구역)
2. 행을 추가하면 **12번 섹션의 `RESTART WITH` 값**을 `최대 ID + 1` 로 갱신하세요.
   빠뜨리면 JPA 저장 시 PK 충돌이 납니다
3. 날짜는 상대 표현 : `DATEADD('DAY', -12, CURRENT_TIMESTAMP)`
4. **업무 번호(`LOT-CT-2601`, `DF-2607-001`)는 고정 문자열**입니다.
   상대 날짜로 만들면 실행 월마다 바뀌어 문서 · 테스트에서 가리킬 수 없습니다
5. 시드의 목적은 "돌아간다" 가 아니라 **"화면의 모든 분기를 눌러 볼 수 있다"** 입니다.
   상태 · 예외 케이스 · 빈 값을 골고루 섞으세요. 검사기가 이것을 확인합니다
6. 수정 후 `verify_seed.py` + `verify_farm_seed.py` + `verify_defect_seed.py` 3종을 돌리세요

정합성 5규칙 (검사기가 검증) :

```
1  이력 누적(Σ movement × sign)        = ProductLot.lotQuantity
2  구역 재고 합계(Σ Inventory.quantity) = ProductLot.lotQuantity
3  Product.totalStock                  = 품목별 로트 수량 합계
4  구역별 적재량                        ≤ WarehouseBin.maxCapacity
5  로트 × 구역 단위 이력 누적           = 해당 구역의 재고
```

---

## 8. 검사기를 신뢰하는 방법 (과거 7번 틀렸습니다)

`tools/` 의 스크립트는 컴파일러가 아닙니다. 지금까지 **7차례 수정**했고
그중 다섯 번은 오탐, 두 번은 **미탐(조용히 통과)** 이었습니다.

> **검사기를 만들거나 고쳤으면, 일부러 오류를 심어 검출되는지 확인한 뒤 원복하세요.**
> 통과하는 경우만 확인하면 검사기가 아무것도 안 보고 있어도 알 수 없습니다.
> 실패할 때 **어떻게** 실패하는지도 봐야 합니다 (한 번은 검사기가 `KeyError` 로 죽어
> 스택 트레이스만 남겼습니다).

검사기가 보지 못하는 것 : 제네릭 타입 불일치, 오버로딩 해석, 상속 관계, 로직의 정당성.
**`gradlew build` 를 대체하지 못합니다.**

과거 사례는 `docs/retrospective.md` 6장에 전부 적혀 있습니다.

---

## 9. 새 세션 시작 프롬프트 (복사해서 사용)

```
FeedFlow 관리자 WMS 프로젝트를 이어서 작업합니다.
저장소 Lyw4/pg, 프로젝트 루트 /projects/sandbox/pg/feedflow.

먼저 /projects/sandbox/pg/HANDOFF.md 를 읽고 제약과 규칙을 파악하세요.
특히 이 세 가지를 확인하세요.
  - 샌드박스에서 gradlew build 불가 (빌드는 내가 로컬에서 실행)
  - bash git push 불가 (kiro_powers github 도구 사용)
  - 작업 후 tools/ 검증 7종을 전부 통과시킬 것

그다음 필요하면 docs/retrospective.md 의 되돌리면 안 되는 결정들을 확인하세요.

이번에 할 일 : (여기에 작업 내용)
```

---

## 10. 문서 지도

| 파일 | 언제 읽나 |
|---|---|
| `HANDOFF.md` | **작업 시작 시 (이 문서)** |
| `README.md` | 기능 전체 목록 · 어필 포인트 · 화면 경로 |
| `docs/retrospective.md` | **설계 결정을 바꾸려 할 때 (10장, 가장 중요)** |
| `INTEGRATION-NOTES.md` | 팀원 모듈을 통합하려 할 때 |
| `docs/erd.md` | 테이블 구조 · 관계 · 재고 3계층 · FEFO 흐름 |
| `docs/architecture-notes.md` | 계층 구조 · 예외 처리 방식 |
| `docs/requirements.md` | 원래 요구사항 |
| `docs/epic-p3-design.md` | 센터 간 이관 설계 |
| `tools/README.md` | 검사기 각각이 무엇을 보는가 · 한계 |
| `docs/*.tsv` | 제출용 산출물 (구글 시트에 붙여넣는 용도) |

산출물 TSV 는 **코드를 바꾸면 함께 갱신**해야 합니다. 열 수가 정해져 있습니다 —
`table-catalog` 7 · `erd-relations` 7 · `erd-columns` 9 · `erd-enums` 6 ·
`table-definition` 9 · `sql-definition` 4 · `unit-work-report` 13.

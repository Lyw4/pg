# 검증 스크립트

**이 프로젝트는 개발 환경에서 `gradlew build` 를 돌릴 수 없는 제약이 있었습니다.**
샌드박스가 외부 네트워크를 차단해 Gradle 의존성을 받을 수 없었고, 로컬 빌드도
매번 확인할 수 없는 상황이 있었습니다.

컴파일러와 테스트가 해 주는 일 중 **자동화할 수 있는 것을 스크립트로 대신**했습니다.
빌드를 대체하지는 못하지만, 빌드 없이도 잡을 수 있는 오류를 미리 걸러냅니다.

## 실행

프로젝트 루트(`feedflow/`)에서 실행합니다.

```bash
cd feedflow

python3 ../tools/static_check.py          # 정적 검사 14종
python3 ../tools/compile_risk_check.py    # 컴파일 리스크 (타입 · 빌더 · record · enum · JPQL · 템플릿)
python3 ../tools/stub_check.py            # Mockito STRICT_STUBS 안전성
python3 ../tools/deadcode_scan.py         # 호출부가 없는 코드
python3 ../tools/verify_seed.py           # data.sql 정합성 5규칙 + 표시 품질
python3 ../tools/verify_farm_seed.py      # farmCustomers 시드 검증
```

Python 3 만 있으면 됩니다. 외부 패키지를 쓰지 않습니다.

## 각 스크립트가 보는 것

| 스크립트 | 검사 내용 |
|---|---|
| `static_check.py` | import 누락/미사용 · 괄호 균형 · enum `switch` 망라성 · JPQL `select new` 인자 수 ↔ record 컴포넌트 수 · 템플릿 `${}` ↔ 컨트롤러 모델 키 · 템플릿 프로퍼티 ↔ DTO 게터 · JS 가 읽는 JSON 필드 ↔ record 컴포넌트 · `node --check` · CSS 괄호 균형 · 같은 파일 내 메서드 호출 인자 수 |
| `compile_risk_check.py` | 타입 해석(import·같은 패키지·`java.lang`) · Lombok 빌더 체인 ↔ 실제 필드 · `new Record(...)` 인자 수 · enum 상수 참조(JPQL 문자열 안까지) · JPQL `:param` ↔ `@Param` · 우리 클래스 메서드 존재 여부 · Thymeleaf 프로퍼티 ↔ DTO 게터 |
| `stub_check.py` | Mockito 스텁이 실제로 호출되는지 (`STRICT_STUBS` 위반은 테스트를 실패시킨다) |
| `deadcode_scan.py` | 호출부가 없는 메서드 · 필드 |
| `verify_seed.py` | 재고 3계층 불변식 등 정합성 5규칙 + FK/PK/RESTART + **표시 품질 6항목**(기능을 보여줄 수 있는 데이터인지) |
| `verify_farm_seed.py` | 농장 20곳의 PK 연속성 · FK 유효성 · enum 값 · 정기 배송일 범위 · 거래 보류가 2곳 이상인지 · RESTART 값 |
| `verify_test_expectations.py` | **수요 계획 테스트의 기대값**을 프로덕션 계산으로 재계산해 대조 (충족률 · 상태 판정 · 조치 건수 · 부족 수량 · 센터/전국 합계) |

## 검사기를 만들 때 지킨 것

**만들 때마다 일부러 오류를 심어 검출되는지 확인한 뒤 원복했습니다.**
검사기가 조용히 통과만 하는 것과 실제로 잡는 것은 다릅니다.
검증되지 않은 검사기는 **틀린 안심**을 줍니다.

실제로 `compile_risk_check.py` 는 처음 실행에서 **503건을 실패로 보고했는데 전부
오탐**이었습니다. 빌더 체인을 파싱할 때 괄호 깊이를 세지 않아
`.farmCode(farm.getFarmCode())` 의 인자 안에 있는 `getFarmCode(` 를 빌더 메서드로
오인한 것입니다. 그 503건을 그대로 믿었다면 정상 코드를 잘못 고쳤을 것입니다.

그래서 이 검사기들도 **결과를 그대로 신뢰하지 말고 한 건씩 근거를 확인**해야 합니다.
검사기는 의심할 지점을 좁혀 주는 도구이고, 판단은 사람이 합니다.

## 한계

- **컴파일을 대체하지 못합니다.** 제네릭 타입 불일치, 오버로딩 해석, 상속 관계
  검증은 하지 않습니다.
- **테스트 실행을 대체하지 못합니다.** 로직이 맞는지는 알 수 없습니다.
- 정규식 기반이라 문법이 특이한 코드에서 오탐·미탐이 날 수 있습니다.

머지 전에 로컬에서 `gradlew build` 를 한 번 돌리는 것이 여전히 가장 확실합니다.

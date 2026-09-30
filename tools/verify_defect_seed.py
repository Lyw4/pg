#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""불량 · 제조사 시드 정합성 검증.

data.sql 을 읽어 다음을 확인한다. 여기서 걸리는 것들은 애플리케이션이 뜬 뒤
화면을 눌러 보기 전까지 드러나지 않는 종류의 오류다.

  1. FK 가 실재하는가            — lotId · binId · manufacturerId
  2. 발견 단계와 구역 용도가 맞는가 — RECEIVING 구역인데 stage 가 SHIPPING 이면 모순
  3. 상태와 처리 결과가 맞는가    — 처리 완료인데 resolution 이 없거나 그 반대
  4. 처리 일시의 앞뒤가 맞는가    — resolvedAt 이 createdAt 보다 앞
  5. 화면에 필요한 데이터가 있는가 — 상태 3종 · 방치 건 · 제조사 미등록 · 구역 미지정
  6. RESTART 값이 최대 ID + 1 인가
  7. 관리번호가 형식에 맞고 중복이 없는가
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SQL = None
for cand in (
        os.path.join(ROOT, 'src/main/resources/data.sql'),
        os.path.join(ROOT, '../feedflow/src/main/resources/data.sql'),
        'src/main/resources/data.sql',
):
    if os.path.exists(cand):
        SQL = cand
        break
if SQL is None:
    print('data.sql 을 찾지 못했습니다.')
    sys.exit(1)

src = open(SQL, encoding='utf-8').read()

fails = []


def fail(msg):
    fails.append(msg)


def section(table):
    """INSERT INTO <table> ... VALUES 이후 첫 세미콜론까지."""
    m = re.search(r'INSERT INTO ' + table + r'\s*\([^)]*\)\s*VALUES(.*?);\s*$',
                  src, re.S | re.M)
    if not m:
        m = re.search(r'INSERT INTO ' + table + r'\s*\([^)]*\)\s*VALUES(.*?);',
                      src, re.S)
    return m.group(1) if m else None


def rows(body):
    """괄호 깊이를 세어 최상위 (...) 행만 끊어 낸다.

    문자열 안의 괄호와 DATEADD('DAY', -1, X) 의 괄호를 구분해야 한다.
    """
    out = []
    depth = 0
    buf = ''
    in_str = False
    i = 0
    while i < len(body):
        ch = body[i]
        if in_str:
            if ch == "'":
                # '' 는 문자열 안의 따옴표
                if i + 1 < len(body) and body[i + 1] == "'":
                    buf += "''"
                    i += 2
                    continue
                in_str = False
            buf += ch
            i += 1
            continue
        if ch == "'":
            in_str = True
            buf += ch
            i += 1
            continue
        if ch == '-' and i + 1 < len(body) and body[i + 1] == '-' and depth == 0:
            # 행 사이 주석
            j = body.find('\n', i)
            i = len(body) if j < 0 else j + 1
            continue
        if ch == '(':
            depth += 1
            if depth == 1:
                buf = ''
                i += 1
                continue
        elif ch == ')':
            depth -= 1
            if depth == 0:
                out.append(buf)
                buf = ''
                i += 1
                continue
        if depth >= 1:
            buf += ch
        i += 1
    return out


def split_cols(row):
    """최상위 콤마로 자른다 (문자열 · 함수 인자 콤마 제외)."""
    out = []
    depth = 0
    in_str = False
    buf = ''
    i = 0
    while i < len(row):
        ch = row[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(row) and row[i + 1] == "'":
                    buf += "''"
                    i += 2
                    continue
                in_str = False
            buf += ch
            i += 1
            continue
        if ch == "'":
            in_str = True
            buf += ch
            i += 1
            continue
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        if ch == ',' and depth == 0:
            out.append(buf.strip())
            buf = ''
            i += 1
            continue
        buf += ch
        i += 1
    if buf.strip():
        out.append(buf.strip())
    return out


def unq(v):
    v = v.strip()
    if v.upper() == 'NULL':
        return None
    if v.startswith("'") and v.endswith("'"):
        return v[1:-1].replace("''", "'")
    return v


def days_of(expr):
    """DATEADD('DAY', -12, CURRENT_TIMESTAMP) → -12 / HOUR 는 일수로 환산."""
    if expr is None:
        return None
    m = re.search(r"DATEADD\('(\w+)',\s*(-?\d+)", expr)
    if not m:
        return 0
    unit, n = m.group(1).upper(), int(m.group(2))
    if unit == 'DAY':
        return n
    if unit == 'HOUR':
        return n / 24.0
    if unit == 'MONTH':
        return n * 30
    return n


# ------------------------------------------------------------------
# 표 읽기
# ------------------------------------------------------------------
manufacturers = {}
body = section('manufacturers')
if body is None:
    fail('manufacturers INSERT 를 찾지 못했다')
else:
    for r in rows(body):
        c = split_cols(r)
        manufacturers[int(c[0])] = {'name': unq(c[1]), 'active': unq(c[5]).upper() == 'TRUE'}

products = {}
body = section('products')
if body is None:
    fail('products INSERT 를 찾지 못했다')
else:
    for r in rows(body):
        c = split_cols(r)
        products[int(c[0])] = {'code': unq(c[1]), 'name': unq(c[2]),
                               'manufacturerId': None if unq(c[3]) is None else int(c[3])}

lots = {}
body = section('productLots')
if body is None:
    fail('productLots INSERT 를 찾지 못했다')
else:
    for r in rows(body):
        c = split_cols(r)
        lots[int(c[0])] = {'productId': int(c[1]), 'lotNo': unq(c[2])}

bins = {}
body = section('warehouseBins')
if body is None:
    fail('warehouseBins INSERT 를 찾지 못했다')
else:
    for r in rows(body):
        c = split_cols(r)
        bins[int(c[0])] = {'code': unq(c[1]), 'centerId': int(c[2]),
                           'purpose': unq(c[4])}

defects = []
body = section('defectRecords')
if body is None:
    fail('defectRecords INSERT 를 찾지 못했다')
else:
    for r in rows(body):
        c = split_cols(r)
        defects.append({
            'defectId': int(c[0]),
            'defectNo': unq(c[1]),
            'lotId': int(c[2]),
            'binId': None if unq(c[3]) is None else int(c[3]),
            'quantity': int(c[4]),
            'defectType': unq(c[5]),
            'stage': unq(c[6]),
            'status': unq(c[7]),
            'resolution': unq(c[8]),
            'memo': unq(c[9]),
            'resolutionMemo': unq(c[10]),
            'reportedByName': unq(c[11]),
            'resolvedByName': unq(c[12]),
            'createdDays': days_of(c[13]),
            'resolvedDays': None if unq(c[14]) is None else days_of(c[14]),
        })

if fails:
    for f in fails:
        print('  - ' + f)
    sys.exit(1)

# ------------------------------------------------------------------
# 1. FK 실재 여부
# ------------------------------------------------------------------
for pid, p in products.items():
    mid = p['manufacturerId']
    if mid is not None and mid not in manufacturers:
        fail('products %d(%s) 의 manufacturerId %s 가 manufacturers 에 없다'
             % (pid, p['code'], mid))

for d in defects:
    if d['lotId'] not in lots:
        fail('%s 의 lotId %d 가 productLots 에 없다' % (d['defectNo'], d['lotId']))
    if d['binId'] is not None and d['binId'] not in bins:
        fail('%s 의 binId %d 가 warehouseBins 에 없다' % (d['defectNo'], d['binId']))
    if d['quantity'] <= 0:
        fail('%s 의 수량이 %d 다 (1 이상이어야 한다)' % (d['defectNo'], d['quantity']))

# FK 가 깨진 상태에서 나머지를 검사하면 의미가 없고, 없는 키를 뒤지다
# 검사기 자체가 죽어 "무엇이 틀렸는지" 대신 스택 트레이스만 남는다.
if fails:
    print('실패 %d건 (참조 무결성)' % len(fails))
    for f in fails:
        print('  - ' + f)
    sys.exit(1)

# ------------------------------------------------------------------
# 2. 발견 단계 ↔ 구역 용도
#    DefectStage 가 BinPurpose 와 1:1 로 대응하도록 만들었으므로
#    시드도 그 대응을 지켜야 한다. 어긋나면 화면의 단계별 집계가
#    "입고 구역에서 출고 검사 불량이 났다" 는 모순을 보여준다.
# ------------------------------------------------------------------
STAGE_OF_PURPOSE = {
    'RECEIVING': 'RECEIVING',
    'INSPECTION': 'RECEIVING',
    'STORAGE': 'STORAGE',
    'SHIPPING': 'SHIPPING',
    'IN_TRANSIT': 'TRANSFER',
}

for d in defects:
    if d['binId'] is None:
        # 구역을 특정할 수 없는 경우는 이관 중이어야 한다
        if d['stage'] != 'TRANSFER':
            fail('%s 는 구역이 없는데 단계가 %s 다 '
                 '(구역을 특정할 수 없는 것은 센터 간 이관뿐이다)'
                 % (d['defectNo'], d['stage']))
        continue
    purpose = bins[d['binId']]['purpose']
    expected = STAGE_OF_PURPOSE.get(purpose)
    if expected is None:
        fail('%s 의 구역 용도 %s 를 단계로 대응시킬 수 없다' % (d['defectNo'], purpose))
    elif d['stage'] != expected:
        fail('%s : 구역 %s 의 용도는 %s 인데 단계가 %s 다 (기대: %s)'
             % (d['defectNo'], bins[d['binId']]['code'], purpose, d['stage'], expected))

# ------------------------------------------------------------------
# 3. 상태 ↔ 처리 결과 ↔ 처리자
# ------------------------------------------------------------------
for d in defects:
    if d['status'] == 'RESOLVED':
        if d['resolution'] is None:
            fail('%s 는 처리 완료인데 resolution 이 NULL 이다' % d['defectNo'])
        if d['resolvedByName'] is None:
            fail('%s 는 처리 완료인데 처리자가 없다' % d['defectNo'])
        if d['resolvedDays'] is None:
            fail('%s 는 처리 완료인데 resolvedAt 이 NULL 이다' % d['defectNo'])
    else:
        if d['resolution'] is not None:
            fail('%s 는 미처리(%s)인데 resolution 이 %s 다'
                 % (d['defectNo'], d['status'], d['resolution']))
        if d['resolvedByName'] is not None:
            fail('%s 는 미처리인데 처리자가 있다' % d['defectNo'])
        if d['resolvedDays'] is not None:
            fail('%s 는 미처리인데 resolvedAt 이 있다' % d['defectNo'])

VALID_STATUS = {'QUARANTINED', 'INSPECTING', 'RESOLVED'}
VALID_TYPE = {'DAMAGE', 'CONTAMINATION', 'WET', 'SPECIFICATION',
              'FOREIGN_MATTER', 'EXPIRED', 'OTHER'}
VALID_STAGE = {'RECEIVING', 'STORAGE', 'SHIPPING', 'TRANSFER'}
VALID_RESOLUTION = {'REWORK', 'CONCESSION', 'SUPPLIER_RETURN', 'DISPOSAL'}

for d in defects:
    if d['status'] not in VALID_STATUS:
        fail('%s 의 상태 %s 는 DefectStatus 에 없다' % (d['defectNo'], d['status']))
    if d['defectType'] not in VALID_TYPE:
        fail('%s 의 유형 %s 는 DefectType 에 없다' % (d['defectNo'], d['defectType']))
    if d['stage'] not in VALID_STAGE:
        fail('%s 의 단계 %s 는 DefectStage 에 없다' % (d['defectNo'], d['stage']))
    if d['resolution'] is not None and d['resolution'] not in VALID_RESOLUTION:
        fail('%s 의 처리 %s 는 DefectResolution 에 없다'
             % (d['defectNo'], d['resolution']))

# ------------------------------------------------------------------
# 4. 시간 순서
# ------------------------------------------------------------------
for d in defects:
    if d['resolvedDays'] is not None and d['resolvedDays'] < d['createdDays']:
        fail('%s : 처리 일시가 등록 일시보다 앞이다 (등록 %s일, 처리 %s일)'
             % (d['defectNo'], d['createdDays'], d['resolvedDays']))

# ------------------------------------------------------------------
# 5. 관리번호 형식 · 중복
# ------------------------------------------------------------------
seen = set()
for d in defects:
    if not re.match(r'^DF-\d{4}-\d{3}$', d['defectNo'] or ''):
        fail('%s 는 DF-yyMM-NNN 형식이 아니다' % d['defectNo'])
    if d['defectNo'] in seen:
        fail('관리번호 %s 가 중복이다 (UK ukDefectNo 위반)' % d['defectNo'])
    seen.add(d['defectNo'])

# ------------------------------------------------------------------
# 6. 화면 확인에 필요한 데이터가 갖춰졌는가
#    시드의 목적은 "돌아간다" 가 아니라 "화면의 모든 분기를 눌러 볼 수 있다" 다.
# ------------------------------------------------------------------
by_status = {}
for d in defects:
    by_status[d['status']] = by_status.get(d['status'], 0) + 1

for st in VALID_STATUS:
    if by_status.get(st, 0) == 0:
        fail('상태 %s 인 불량이 없다 — 그 상태의 화면 분기를 확인할 수 없다' % st)

STALE_DAYS = 7
stale = [d for d in defects
         if d['status'] != 'RESOLVED' and -d['createdDays'] >= STALE_DAYS]
if not stale:
    fail('7일 넘게 방치된 미처리 건이 없다 — 방치 경고 배너를 확인할 수 없다')

unknown = [d for d in defects
           if products[lots[d['lotId']]['productId']]['manufacturerId'] is None]
if not unknown:
    fail("제조사가 없는 품목의 불량이 없다 — 제조사별 집계의 '미등록' 을 확인할 수 없다")

if not [d for d in defects if d['binId'] is None]:
    fail("구역이 지정되지 않은 불량이 없다 — '구역 미지정' 표시를 확인할 수 없다")

STOCK_REMOVED = {'SUPPLIER_RETURN', 'DISPOSAL'}
if not [d for d in defects if d['resolution'] in STOCK_REMOVED]:
    fail("재고 차감이 남는 처리(반품 · 폐기)가 없다 — '재고 차감 대기' 카드를 확인할 수 없다")

if not [d for d in defects if d['resolution'] in ('REWORK', 'CONCESSION')]:
    fail('재고로 복귀하는 처리(재작업 · 특채)가 없다 — 처리 분기 절반을 확인할 수 없다')

if not [m for m in manufacturers.values() if not m['active']]:
    fail('거래 중지된 제조사가 없다 — active 필터가 걸러 내는지 확인할 수 없다')

# ------------------------------------------------------------------
# 7. IDENTITY RESTART 값
# ------------------------------------------------------------------
def restart_of(table, col):
    m = re.search(r'ALTER TABLE ' + table + r' ALTER COLUMN ' + col
                  + r' RESTART WITH (\d+)', src)
    return int(m.group(1)) if m else None


for table, col, ids in (
        ('manufacturers', 'manufacturerId', manufacturers.keys()),
        ('defectRecords', 'defectId', [d['defectId'] for d in defects]),
):
    got = restart_of(table, col)
    want = max(ids) + 1
    if got is None:
        fail('%s 의 IDENTITY RESTART 구문이 없다 — JPA 저장 시 PK 충돌이 난다' % table)
    elif got != want:
        fail('%s RESTART 가 %d 인데 최대 ID + 1 은 %d 다' % (table, got, want))

# ------------------------------------------------------------------
# 결과
# ------------------------------------------------------------------
if fails:
    print('실패 %d건' % len(fails))
    for f in fails:
        print('  - ' + f)
    sys.exit(1)

stage_count = {}
for d in defects:
    stage_count[d['stage']] = stage_count.get(d['stage'], 0) + 1
receiving_rate = stage_count.get('RECEIVING', 0) * 100 // len(defects)

mfr_count = {}
for d in defects:
    mid = products[lots[d['lotId']]['productId']]['manufacturerId']
    label = manufacturers[mid]['name'] if mid else '미등록'
    mfr_count[label] = mfr_count.get(label, 0) + 1

print('    제조사 %d곳 (거래 중지 %d) · 불량 %d건 · 총 %d포대'
      % (len(manufacturers),
         len([m for m in manufacturers.values() if not m['active']]),
         len(defects),
         sum(d['quantity'] for d in defects)))
print('    상태 ' + ' · '.join('%s %d' % (k, v) for k, v in sorted(by_status.items())))
print('    단계 ' + ' · '.join('%s %d' % (k, v) for k, v in sorted(stage_count.items()))
      + ' → 입고 적발률 %d%%' % receiving_rate)
print('    제조사별 ' + ' · '.join('%s %d' % (k, v) for k, v in sorted(mfr_count.items())))
print('    방치(7일+) %d건 · 제조사 미등록 %d건 · 구역 미지정 %d건'
      % (len(stale), len(unknown), len([d for d in defects if d['binId'] is None])))
print()
print('불량 시드 검증 통과')

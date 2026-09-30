#!/usr/bin/env python3
"""data.sql 정합성 · 표시 품질 검증."""
import re, sys, collections

SQL = 'src/main/resources/data.sql'
raw = open(SQL, encoding='utf-8').read()

def strip_comment(line):
    out, in_str = [], False
    i = 0
    while i < len(line):
        c = line[i]
        if c == "'":
            in_str = not in_str
            out.append(c)
        elif c == '-' and not in_str and i + 1 < len(line) and line[i+1] == '-':
            break
        else:
            out.append(c)
        i += 1
    return ''.join(out)

text = '\n'.join(strip_comment(l) for l in raw.splitlines())

def split_top(s):
    parts, buf, depth, in_str = [], [], 0, False
    for c in s:
        if c == "'":
            in_str = not in_str
        if not in_str:
            if c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
            elif c == ',' and depth == 0:
                parts.append(''.join(buf).strip()); buf = []; continue
        buf.append(c)
    if ''.join(buf).strip():
        parts.append(''.join(buf).strip())
    return parts

def parse(table):
    m = re.search(r'INSERT INTO ' + table + r'\s*\(([^)]*)\)\s*VALUES(.*?);\s*$',
                  text, re.S | re.M | re.I)
    if not m:
        sys.exit('!! INSERT 문을 찾지 못함: ' + table)
    cols = [c.strip() for c in m.group(1).split(',')]
    body = m.group(2)
    rows = []
    for tup in split_top(body.strip()):
        tup = tup.strip()
        assert tup.startswith('(') and tup.endswith(')'), tup[:60]
        vals = split_top(tup[1:-1])
        if len(vals) != len(cols):
            sys.exit('!! %s 컬럼 수 불일치 (%d != %d): %s' % (table, len(vals), len(cols), tup[:80]))
        rows.append(dict(zip(cols, vals)))
    return rows

def num(v):
    v = v.strip()
    if v.upper() == 'NULL':
        return None
    return float(v) if '.' in v else int(v)

def days(v):
    m = re.search(r"DATEADD\('DAY',\s*(-?\d+)", v)
    return int(m.group(1)) if m else 0

centers  = parse('centers')
products = parse('products')
lots     = parse('productLots')
orders   = parse('orders')
items    = parse('orderItems')
bins     = parse('warehouseBins')
invs     = parse('inventories')
moves    = parse('stockMovements')

errors, warns, notes = [], [], []

notes.append('센터 %d / 품목 %d / 로트 %d / 주문 %d / 주문상세 %d / 구역 %d / 재고 %d / 이력 %d'
             % (len(centers), len(products), len(lots), len(orders), len(items),
                len(bins), len(invs), len(moves)))

for name, rows, pk in [('centers', centers, 'centerId'), ('products', products, 'productId'),
                       ('productLots', lots, 'lotId'), ('orders', orders, 'orderId'),
                       ('orderItems', items, 'orderItemId'), ('warehouseBins', bins, 'binId'),
                       ('inventories', invs, 'inventoryId'), ('stockMovements', moves, 'movementId')]:
    ids = [num(r[pk]) for r in rows]
    dup = [k for k, v in collections.Counter(ids).items() if v > 1]
    if dup:
        errors.append('%s.%s 중복: %s' % (name, pk, dup))

binById  = {num(b['binId']): b for b in bins}
lotById  = {num(l['lotId']): l for l in lots}
prodById = {num(p['productId']): p for p in products}
ctrById  = {num(c['centerId']): c for c in centers}

for b in bins:
    if num(b['centerId']) not in ctrById:
        errors.append('warehouseBins %s → 없는 centerId %s' % (b['binCode'], b['centerId']))
for l in lots:
    if num(l['productId']) not in prodById:
        errors.append('productLots %s → 없는 productId' % l['lotNo'])
for iv in invs:
    if num(iv['lotId']) not in lotById:
        errors.append('inventories %s → 없는 lotId %s' % (iv['inventoryId'], iv['lotId']))
    if num(iv['binId']) not in binById:
        errors.append('inventories %s → 없는 binId %s' % (iv['inventoryId'], iv['binId']))
for mv in moves:
    for col in ('binId', 'fromBinId'):
        v = num(mv[col])
        if v is not None and v not in binById:
            errors.append('stockMovements %s.%s → 없는 binId %s' % (mv['movementId'], col, v))
    if num(mv['lotId']) not in lotById:
        errors.append('stockMovements %s → 없는 lotId' % mv['movementId'])
    elif num(mv['productId']) != num(lotById[num(mv['lotId'])]['productId']):
        errors.append('stockMovements %s → productId 가 로트의 품목과 다름' % mv['movementId'])

SIGN_IN  = {'INBOUND', 'ADJUST_PLUS', 'RETURN', 'TRANSFER_IN'}
SIGN_OUT = {'OUTBOUND', 'DISPOSAL', 'ADJUST_MINUS', 'TRANSFER_OUT'}
lotLedger = collections.Counter()
binLedger = collections.Counter()
for mv in moves:
    t, lot, q = mv['movementType'].strip().strip("'"), num(mv['lotId']), num(mv['quantity'])
    to, fr = num(mv['binId']), num(mv['fromBinId'])
    if t == 'MOVE':
        binLedger[(lot, fr)] -= q
        binLedger[(lot, to)] += q
    elif t in SIGN_IN:
        lotLedger[lot] += q
        binLedger[(lot, to)] += q
        if fr is not None:
            binLedger[(lot, fr)] -= q
            lotLedger[lot] -= q
    elif t in SIGN_OUT:
        lotLedger[lot] -= q
        if fr is not None:
            binLedger[(lot, fr)] -= q
            binLedger[(lot, to)] += q
            lotLedger[lot] += q
        else:
            binLedger[(lot, to)] -= q
    else:
        errors.append('알 수 없는 movementType: %s' % t)

invByLot = collections.Counter()
invByBin = collections.Counter()
for iv in invs:
    invByLot[num(iv['lotId'])] += num(iv['quantity'])
    invByBin[(num(iv['lotId']), num(iv['binId']))] += num(iv['quantity'])

for lot in sorted(set(lotLedger) | set(invByLot)):
    if lotLedger[lot] != invByLot[lot]:
        errors.append('[규칙1-로트] lot %s (%s) 이력누적 %s != 재고합 %s'
                      % (lot, lotById[lot]['lotNo'].strip("'"), lotLedger[lot], invByLot[lot]))

binMismatch = []
for key in sorted(set(binLedger) | set(invByBin)):
    if binLedger[key] != invByBin[key]:
        lot, bid = key
        binMismatch.append('  lot %s × %s : 이력누적 %s != 재고 %s'
                           % (lot, binById[bid]['binCode'].strip("'") if bid in binById else bid,
                              binLedger[key], invByBin[key]))

for l in lots:
    lid = num(l['lotId'])
    if num(l['lotQuantity']) != invByLot[lid]:
        errors.append('[규칙2] lot %s lotQuantity %s != 재고합 %s'
                      % (l['lotNo'], l['lotQuantity'], invByLot[lid]))

stockByProd = collections.Counter()
for l in lots:
    stockByProd[num(l['productId'])] += invByLot[num(l['lotId'])]
for p in products:
    pid = num(p['productId'])
    if num(p['totalStock']) != stockByProd[pid]:
        errors.append('[규칙3] product %s(%s) totalStock %s != 로트합 %s'
                      % (pid, p['name'], p['totalStock'], stockByProd[pid]))

loadByBin = collections.Counter()
for iv in invs:
    loadByBin[num(iv['binId'])] += num(iv['quantity'])
for bid, load in loadByBin.items():
    b = binById[bid]
    if b['binPurpose'].strip("'") == 'IN_TRANSIT':
        continue
    if load > num(b['maxCapacity']):
        errors.append('[규칙4] %s 적재 %s > 한도 %s' % (b['binCode'], load, b['maxCapacity']))

transit = [b for b in bins if b['binPurpose'].strip("'") == 'IN_TRANSIT']
byCenter = collections.Counter(num(b['centerId']) for b in transit)
for cid in ctrById:
    if byCenter[cid] != 1:
        errors.append('[P3] centerId %s 의 IN_TRANSIT 가상 구역이 %d 개' % (cid, byCenter[cid]))
for b in transit:
    if num(b['maxCapacity']) != 0:
        errors.append('[P3] %s maxCapacity 가 0 이 아님' % b['binCode'])

for mv in moves:
    t = mv['movementType'].strip("'")
    if t == 'TRANSFER_OUT':
        to, fr = num(mv['binId']), num(mv['fromBinId'])
        if binById[to]['binPurpose'].strip("'") != 'IN_TRANSIT':
            errors.append('[P3] TRANSFER_OUT %s 도착이 IN_TRANSIT 구역이 아님' % mv['movementId'])
        elif num(binById[to]['centerId']) != num(binById[fr]['centerId']):
            errors.append('[P3] TRANSFER_OUT %s 의 가상 구역이 출발 센터 소속이 아님' % mv['movementId'])
    if t == 'TRANSFER_IN':
        fr = num(mv['fromBinId'])
        if binById[fr]['binPurpose'].strip("'") != 'IN_TRANSIT':
            errors.append('[P3] TRANSFER_IN %s 출발이 IN_TRANSIT 구역이 아님' % mv['movementId'])
    if t == 'MOVE':
        to, fr = num(mv['binId']), num(mv['fromBinId'])
        if num(binById[to]['centerId']) != num(binById[fr]['centerId']):
            errors.append('[P3] MOVE %s 가 센터를 넘나든다 (TRANSFER 여야 함)' % mv['movementId'])

tout = collections.Counter((num(m['lotId']), num(m['quantity'])) for m in moves
                           if m['movementType'].strip("'") == 'TRANSFER_OUT')
tin = collections.Counter((num(m['lotId']), num(m['quantity'])) for m in moves
                          if m['movementType'].strip("'") == 'TRANSFER_IN')
if tout != tin:
    errors.append('[P3] TRANSFER_OUT / TRANSFER_IN 짝이 맞지 않음 %s vs %s' % (tout, tin))

GRID_W, GRID_H = 26, 14
byCenterBins = collections.defaultdict(list)
for b in bins:
    if b['binPurpose'].strip("'") == 'IN_TRANSIT':
        continue
    byCenterBins[num(b['centerId'])].append(b)

for cid, bs in sorted(byCenterBins.items()):
    occupied = {}
    for b in bs:
        x, y, w, h = num(b['posX']), num(b['posY']), num(b['posWidth']), num(b['posHeight'])
        code = b['binCode'].strip("'")
        if x < 1 or y < 1 or x + w - 1 > GRID_W or y + h - 1 > GRID_H:
            errors.append('[좌표] %s 가 %dx%d 격자를 벗어남 (x%s y%s w%s h%s)'
                          % (code, GRID_W, GRID_H, x, y, w, h))
        for dx in range(w):
            for dy in range(h):
                cell = (x + dx, y + dy)
                if cell in occupied:
                    errors.append('[좌표] centerId %s : %s 와 %s 가 %s 에서 겹침'
                                  % (cid, occupied[cell], code, cell))
                occupied[cell] = code

restart = dict(re.findall(r'ALTER TABLE (\w+) ALTER COLUMN \w+ RESTART WITH (\d+)', text))
expected = {'centers': max(num(c['centerId']) for c in centers) + 1,
            'products': max(num(p['productId']) for p in products) + 1,
            'productLots': max(num(l['lotId']) for l in lots) + 1,
            'orders': max(num(o['orderId']) for o in orders) + 1,
            'orderItems': max(num(i['orderItemId']) for i in items) + 1,
            'warehouseBins': max(num(b['binId']) for b in bins) + 1,
            'inventories': max(num(i['inventoryId']) for i in invs) + 1,
            'stockMovements': max(num(m['movementId']) for m in moves) + 1}
for t, exp in expected.items():
    if t not in restart:
        errors.append('[RESTART] %s 누락' % t)
    elif int(restart[t]) != exp:
        errors.append('[RESTART] %s = %s 인데 %s 이어야 함' % (t, restart[t], exp))

empty = [binById[bid]['binCode'].strip("'") for bid in sorted(binById)
         if binById[bid]['binPurpose'].strip("'") == 'STORAGE'
         and binById[bid]['active'].strip().upper() == 'TRUE'
         and loadByBin[bid] == 0]
if empty:
    warns.append('[표시] 재고가 없는 활성 보관 구역 %d 곳: %s' % (len(empty), empty))

full = []
for bid in sorted(binById):
    b = binById[bid]
    if b['binPurpose'].strip("'") != 'STORAGE':
        continue
    cap = num(b['maxCapacity'])
    if cap and loadByBin[bid] / cap >= 0.8:
        full.append('%s %d%%' % (b['binCode'].strip("'"), round(loadByBin[bid] / cap * 100)))
if full:
    warns.append('[표시] 시드 상태에서 이미 포화(80%%↑)인 구역: %s' % full)

buckets = collections.Counter()
for bid in sorted(binById):
    b = binById[bid]
    if b['binPurpose'].strip("'") != 'STORAGE':
        continue
    cap = num(b['maxCapacity'])
    if not cap:
        continue
    r = loadByBin[bid] / cap
    buckets['비었음' if r == 0 else '여유(<60%)' if r < 0.6 else '보통(60~80%)' if r < 0.8 else '포화(80%↑)'] += 1
notes.append('보관 구역 적재율 분포: ' + ', '.join('%s %d' % kv for kv in buckets.items()))

recIn = sum(num(m['quantity']) for m in moves
            if m['movementType'].strip("'") in ('INBOUND', 'RETURN', 'ADJUST_PLUS') and days(m['createdAt']) >= -7)
recOut = sum(num(m['quantity']) for m in moves
             if m['movementType'].strip("'") in ('OUTBOUND', 'DISPOSAL', 'ADJUST_MINUS') and days(m['createdAt']) >= -7)
notes.append('최근 7일 입고 +%d / 출고 -%d  (출고/입고 %.2f)' % (recIn, recOut, (recOut / recIn) if recIn else 0))
if recIn == 0 or recOut == 0:
    errors.append('[표시] 최근 7일 입고 또는 출고가 0 → 대시보드 기간 카드가 비어 보인다')
if recIn and recOut / recIn < 0.2:
    warns.append('[표시] 최근 7일 입고 대비 출고가 20%% 미만 (%d vs %d)' % (recIn, recOut))

waitByPurpose = collections.Counter()
for bid, load in loadByBin.items():
    waitByPurpose[binById[bid]['binPurpose'].strip("'")] += load
notes.append('구역 용도별 재고: ' + ', '.join(
    '%s %d' % (p, waitByPurpose[p]) for p in ('STORAGE', 'RECEIVING', 'SHIPPING', 'IN_TRANSIT')))
if waitByPurpose['RECEIVING'] + waitByPurpose['SHIPPING'] == 0:
    errors.append('[표시] 대기 구역(입고/출고) 재고가 0 → 적재율에서 대기분을 분리한 설계가 '
                  '화면에 한 번도 나타나지 않는다 (2D 도면 "+ 대기 구역 N포대 별도", '
                  '센터 카드 "보관 N + 대기 K", 전국 적재율 안내가 모두 숨는다)')

actByCenter = collections.Counter()
for m in moves:
    if days(m['createdAt']) < -30:
        continue
    bid = num(m['binId'])
    if bid in binById:
        actByCenter[num(binById[bid]['centerId'])] += 1
dead = [ctrById[c]['name'].strip("'") for c in sorted(ctrById) if actByCenter[c] == 0]
if dead:
    warns.append('[표시] 최근 30일 이력이 없는 센터: %s' % dead)
notes.append('최근 30일 센터별 이력 건수: ' + ', '.join(
    '%s %d' % (ctrById[c]['name'].strip("'"), actByCenter[c]) for c in sorted(ctrById)))

noGeo = [ctrById[c]['name'].strip("'") for c in sorted(ctrById)
         if num(ctrById[c].get('latitude', 'NULL')) is None or num(ctrById[c].get('longitude', 'NULL')) is None]
if noGeo:
    warns.append('[지도] 좌표가 없는 센터: %s' % noGeo)
else:
    notes.append('센터 좌표 %d/%d 등록 (지도 핀)' % (len(centers), len(centers)))
for c in centers:
    lat, lon = num(c.get('latitude', 'NULL')), num(c.get('longitude', 'NULL'))
    if lat is not None and not (33.0 <= lat <= 38.7):
        errors.append('[지도] %s 위도 %s 가 대한민국 범위를 벗어남' % (c['name'], lat))
    if lon is not None and not (124.5 <= lon <= 131.0):
        errors.append('[지도] %s 경도 %s 가 대한민국 범위를 벗어남' % (c['name'], lon))

statusOf = {num(o['orderId']): o['status'].strip("'") for o in orders}
outOrders = set(num(m['orderId']) for m in moves
                if m['movementType'].strip("'") == 'OUTBOUND' and num(m['orderId']) is not None)
shipped = set(oid for oid, s in statusOf.items() if s in ('SHIPPED', 'DELIVERED'))
if shipped - outOrders:
    warns.append('[표시] 출고/배송 완료인데 출고 이력이 없는 주문: %s' % sorted(shipped - outOrders))
if outOrders - shipped:
    errors.append('[정합] 출고 이력이 있는데 상태가 출고 전인 주문: %s'
                  % sorted((oid, statusOf.get(oid)) for oid in outOrders - shipped))
notes.append('출고 이력이 붙은 주문 %d건 / SHIPPED·DELIVERED %d건' % (len(outOrders), len(shipped)))

itemQty = collections.Counter()
for it in items:
    itemQty[num(it['orderId'])] += num(it['quantity'])
outQty = collections.Counter()
for m in moves:
    if m['movementType'].strip("'") == 'OUTBOUND' and num(m['orderId']) is not None:
        outQty[num(m['orderId'])] += num(m['quantity'])
for oid in sorted(outOrders):
    if itemQty[oid] != outQty[oid]:
        errors.append('[정합] 주문 #%d 상세 수량 %d != 출고 이력 수량 %d' % (oid, itemQty[oid], outQty[oid]))

seq = [days(m['createdAt']) for m in moves]
if seq != sorted(seq):
    warns.append('[표시] stockMovements 가 시간순으로 정렬되어 있지 않다')

for n in notes:
    print('   ', n)
if binMismatch:
    print('\n[규칙1-구역] 로트x구역 단위 이력 누적이 재고와 다른 곳 %d:' % len(binMismatch))
    for m in binMismatch:
        print(m)
if warns:
    print('\n경고 %d:' % len(warns))
    for w in warns:
        print('  -', w)
if errors:
    print('\n오류 %d:' % len(errors))
    for e in errors:
        print('  x', e)
    sys.exit(1)
print('\n정합성 검증 통과' + (' (경고 있음)' if warns or binMismatch else ''))

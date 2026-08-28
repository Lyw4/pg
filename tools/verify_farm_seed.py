"""farmCustomers 시드 검증 — 기존 verify_seed.py 가 모르는 새 테이블을 따로 본다."""
import re, sys, collections

sql = open('src/main/resources/data.sql', encoding='utf-8').read()

block = re.search(r'INSERT INTO farmCustomers \((.*?)\) VALUES(.*?);\s*\n', sql, re.S)
assert block, 'farmCustomers INSERT 를 찾지 못했습니다'
cols = [c.strip() for c in block.group(1).replace('\n', ' ').split(',')]
body = block.group(2)

# 주석 줄 제거 후, 각 행은 (숫자, 'F-... ~ CURRENT_TIMESTAMP)) 형태다.
# DATEADD('DAY', -N, CURRENT_TIMESTAMP) 안에 괄호가 있어 단순 괄호 매칭으로는 잘린다.
body = re.sub(r'--[^\n]*', '', body)
rows = re.findall(r"\((\d+,\s*'F-.*?CURRENT_TIMESTAMP\))\)", body, re.S)

def split_values(s):
    out, cur, depth, q = [], '', 0, False
    for ch in s:
        if ch == "'" :
            q = not q; cur += ch
        elif q: cur += ch
        elif ch == '(': depth += 1; cur += ch
        elif ch == ')': depth -= 1; cur += ch
        elif ch == ',' and depth == 0: out.append(cur.strip()); cur = ''
        else: cur += ch
    out.append(cur.strip())
    return out

fails = []
def check(ok, msg):
    if not ok: fails.append(msg)

parsed = []
for r in rows:
    vals = split_values(r)
    check(len(vals) == len(cols), f'컬럼 수 불일치: {len(vals)} != {len(cols)}')
    parsed.append(dict(zip(cols, vals)))

unq = lambda v: v.strip().strip("'")

check(len(parsed) == 20, f'농장 수가 20이 아님: {len(parsed)}')

ids = [int(p['farmCustomerId']) for p in parsed]
check(ids == list(range(1, 21)), f'PK 가 1~20 연속이 아님: {ids}')

codes = [unq(p['farmCode']) for p in parsed]
check(len(set(codes)) == 20, 'farmCode 중복 있음')

center_ids = {int(p['centerId']) for p in parsed}
check(center_ids <= {1, 2, 3, 4, 5}, f'존재하지 않는 centerId 참조: {center_ids}')

animals = collections.Counter(unq(p['animalType']) for p in parsed)
check(set(animals) <= {'CATTLE', 'PIG', 'POULTRY'}, f'AnimalType enum 밖의 값: {set(animals)}')

status = collections.Counter(unq(p['status']) for p in parsed)
check(set(status) <= {'ACTIVE', 'PAUSED'}, f'CustomerStatus enum 밖의 값: {set(status)}')
check(status['PAUSED'] >= 2, f'거래 보류가 2곳 미만 → 상태 필터를 검증할 수 없다: {status}')

days = [int(p['recurringDeliveryDay']) for p in parsed]
check(all(1 <= d <= 28 for d in days), f'정기 배송일이 1~28 범위를 벗어남: {sorted(set(days))}')

check(all(float(p['distanceKm']) >= 0 for p in parsed), 'distanceKm 이 음수')
check(all(p['latitude'] != 'NULL' and p['longitude'] != 'NULL' for p in parsed),
      '좌표가 비어 있는 농장이 있음')

restart = re.search(r'ALTER TABLE farmCustomers ALTER COLUMN farmCustomerId RESTART WITH (\d+)', sql)
check(restart is not None, 'farmCustomers RESTART 구문 없음')
if restart:
    check(int(restart.group(1)) == max(ids) + 1,
          f'RESTART 값이 {restart.group(1)} 인데 최대 PK 는 {max(ids)}')

total = sum(int(p['monthlyFeedQuantity']) for p in parsed)
active = sum(int(p['monthlyFeedQuantity']) for p in parsed if unq(p['status']) == 'ACTIVE')
livestock = sum(int(p['livestockCount']) for p in parsed)
check(total != active, '전체와 거래 중 월 사료량이 같다 → 합산 기준을 검증할 수 없다')

by_center = collections.Counter(int(p['centerId']) for p in parsed)
check(len(by_center) == 5, f'담당 농장이 배정된 센터가 5곳이 아님: {sorted(by_center)}')

print(f'    농장 {len(parsed)}곳 / 거래 중 {status["ACTIVE"]} · 보류 {status["PAUSED"]}')
print(f'    축종 분포: ' + ', '.join(f'{k} {v}' for k, v in sorted(animals.items())))
print(f'    센터별 농장 수: ' + ', '.join(f'C{k} {v}곳' for k, v in sorted(by_center.items())))
print(f'    월 예상 사료량: 전체 {total:,} · 거래 중만 {active:,} (차이 {total-active:,})')
print(f'    사육 규모 합계: {livestock:,} 마리')
print(f'    거리 범위: {min(float(p["distanceKm"]) for p in parsed)} ~ {max(float(p["distanceKm"]) for p in parsed)} km')
print(f'    좌표 {len(parsed)}/{len(parsed)} 등록 · RESTART {restart.group(1) if restart else "?"}')

if fails:
    print('\n실패:')
    for f in fails: print('  -', f)
    sys.exit(1)
print('\n농장 시드 검증 통과')

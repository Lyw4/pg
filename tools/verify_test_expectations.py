"""
수요 계획 테스트의 기대값을 프로덕션 로직으로 재계산해 대조한다.

빌드 사이클이 느린 환경에서 테스트 기대값 오류는 비싸다. 정적 검사기는
타입과 참조만 보므로 "숫자가 맞는가" 는 잡지 못한다. 실제로
totalsAreSumOfAnimals 에서 111% 가 TIGHT 구간에 들어가는 것을 놓쳐
테스트 1건이 실패했다.

CoverageStatus 와 AnimalCoverageDto 의 계산을 파이썬으로 옮겨
테스트에 적어 둔 기대값과 맞는지 확인한다.
"""
import sys

# --- 프로덕션 로직 재현 (CoverageStatus · AnimalCoverageDto) -------------
TIGHT_THRESHOLD = 100
ADEQUATE_THRESHOLD = 120
SURPLUS_THRESHOLD = 300


def rate(demand, supply):
    """AnimalCoverageDto.rate — 분모 0 이면 0"""
    if demand <= 0:
        return 0
    return round(supply * 100 / demand)


def status(demand, coverage_rate):
    """CoverageStatus.of — 수요 0 을 비율보다 먼저 본다"""
    if demand <= 0:
        return 'NO_DEMAND'
    if coverage_rate >= SURPLUS_THRESHOLD:
        return 'SURPLUS'
    if coverage_rate >= ADEQUATE_THRESHOLD:
        return 'ADEQUATE'
    if coverage_rate >= TIGHT_THRESHOLD:
        return 'TIGHT'
    return 'SHORTAGE'


def needs_action(st):
    return st in ('SHORTAGE', 'TIGHT')


def shortage(demand, supply):
    return max(0, demand - supply)


def bar_width(coverage_rate):
    return min(100, coverage_rate)


# --- 테스트에 적어 둔 기대값 --------------------------------------------
# (테스트명, [(센터, 수요, 재고)], 기대값 dict)
CASES = [
    ('includesAnimalWithDemandButNoStock',
     [(1, 720, 0)],
     {'rate': [0], 'status': ['SHORTAGE'], 'shortage': [720]}),

    ('includesAnimalWithStockButNoDemand',
     [(1, 0, 500)],
     {'rate': [0], 'status': ['NO_DEMAND'], 'shortage': [0]}),

    ('coverageRateIsSupplyOverDemand',
     [(1, 720, 900)],
     {'rate': [125], 'status': ['ADEQUATE']}),

    ('surplusIsAlsoAWarning',
     [(1, 900, 3000)],
     {'rate': [333], 'status': ['SURPLUS'], 'action': [False], 'bar': [100]}),

    ('tightNeedsAction',
     [(1, 720, 750)],
     {'rate': [104], 'status': ['TIGHT'], 'action': [True], 'shortage': [0]}),

    ('totalsAreSumOfAnimals',
     [(1, 720, 500), (1, 200, 300), (2, 900, 1000)],
     {'centerTotals': {1: (920, 800, 1, 220)},
      'totalDemand': 1820, 'totalSupply': 1800,
      'animalsNeedingAction': 2, 'centersNeedingAction': 2, 'totalShortage': 220}),

    ('centerTotalCanHideShortage',
     [(1, 700, 100), (1, 700, 2000)],
     {'centerRate': {1: 150}, 'centerAction': {1: True}, 'centerShortage': {1: 600}}),
]

fails = []


def check(name, label, actual, expected):
    if actual != expected:
        fails.append(f'{name} · {label} : 기대 {expected} 인데 실제 {actual}')


for name, rows, exp in CASES:
    rates = [rate(d, s) for _, d, s in rows]
    sts = [status(d, rates[i]) for i, (_, d, s) in enumerate(rows)]
    acts = [needs_action(st) for st in sts]
    shorts = [shortage(d, s) for _, d, s in rows]
    bars = [bar_width(r) for r in rates]

    if 'rate' in exp:
        check(name, 'coverageRate', rates, exp['rate'])
    if 'status' in exp:
        check(name, 'status', sts, exp['status'])
    if 'action' in exp:
        check(name, 'needsAction', acts, exp['action'])
    if 'shortage' in exp:
        check(name, 'shortageQuantity', shorts, exp['shortage'])
    if 'bar' in exp:
        check(name, 'barWidth', bars, exp['bar'])

    # 센터 합계
    centers = {}
    for i, (c, d, s) in enumerate(rows):
        agg = centers.setdefault(c, [0, 0, 0, 0])
        agg[0] += d
        agg[1] += s
        agg[2] += 1 if acts[i] else 0
        agg[3] += shorts[i]

    if 'centerTotals' in exp:
        for c, want in exp['centerTotals'].items():
            check(name, f'센터{c} (수요,재고,조치,부족)', tuple(centers[c]), want)
    if 'centerRate' in exp:
        for c, want in exp['centerRate'].items():
            d, s = centers[c][0], centers[c][1]
            check(name, f'센터{c} totalCoverageRate', rate(d, s), want)
    if 'centerAction' in exp:
        for c, want in exp['centerAction'].items():
            check(name, f'센터{c} needsAction', centers[c][2] > 0, want)
    if 'centerShortage' in exp:
        for c, want in exp['centerShortage'].items():
            check(name, f'센터{c} shortageQuantity', centers[c][3], want)

    # 전국 합계
    if 'totalDemand' in exp:
        check(name, 'totalDemand', sum(c[0] for c in centers.values()), exp['totalDemand'])
    if 'totalSupply' in exp:
        check(name, 'totalSupply', sum(c[1] for c in centers.values()), exp['totalSupply'])
    if 'animalsNeedingAction' in exp:
        check(name, 'animalsNeedingAction', sum(acts), exp['animalsNeedingAction'])
    if 'centersNeedingAction' in exp:
        check(name, 'centersNeedingAction',
              sum(1 for c in centers.values() if c[2] > 0), exp['centersNeedingAction'])
    if 'totalShortage' in exp:
        check(name, 'totalShortage', sum(shorts), exp['totalShortage'])

print(f'검사한 테스트 케이스: {len(CASES)}개')
if fails:
    print(f'\n기대값 불일치 {len(fails)}건')
    for f in fails:
        print('  -', f)
    sys.exit(1)
print('\n테스트 기대값이 프로덕션 계산과 모두 일치한다')

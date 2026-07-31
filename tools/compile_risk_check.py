"""
컴파일 리스크 정밀 검사 — 빌드를 돌릴 수 없는 환경에서 컴파일러 역할을 일부 대신한다.

기존 static_check.py 가 보는 것(import 누락/미사용, 괄호 균형, JPQL select new 인자 수 등)에
더해, 이번에 추가한 코드에서 실제로 컴파일을 깨뜨릴 수 있는 것들을 본다.

1. 타입 해석      : 코드에 등장하는 대문자 시작 식별자가 import·같은 패키지·java.lang 중 하나로 해석되는가
2. 빌더 체인      : X.builder().a().b() 의 a·b 가 그 클래스의 필드인가
3. 메서드 존재    : 우리 클래스의 인스턴스 메서드를 호출할 때 그 메서드가 실제 있는가
4. enum 상수      : Enum.CONSTANT 참조가 실제 상수인가 (JPQL 문자열 안까지)
5. record 컴포넌트: new Record(...) 인자 수가 컴포넌트 수와 같은가
6. JPQL 파라미터  : :name 과 @Param("name") 이 1:1인가
7. Thymeleaf      : ${obj.prop} 의 prop 이 그 DTO 의 게터로 존재하는가
"""
import os
import re
import sys
from collections import defaultdict

MAIN = 'src/main/java'
TEST = 'src/test/java'
TEMPLATES = 'src/main/resources/templates'

JAVA_LANG = {
    'String', 'Long', 'Integer', 'Double', 'Boolean', 'Object', 'Math', 'Override',
    'Exception', 'RuntimeException', 'IllegalArgumentException', 'IllegalStateException',
    'Deprecated', 'SuppressWarnings', 'FunctionalInterface', 'Number', 'Comparable',
    'CharSequence', 'Iterable', 'Class', 'Thread', 'System', 'Character', 'Byte',
    'Short', 'Float', 'Void', 'Record', 'Enum', 'StringBuilder',
}

fails = []
warns = []


def fail(msg):
    fails.append(msg)


def warn(msg):
    warns.append(msg)


# ------------------------------------------------------------------
# 소스 수집
# ------------------------------------------------------------------
def collect(root):
    out = {}
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith('.java'):
                p = os.path.join(dirpath, f)
                out[p] = open(p, encoding='utf-8').read()
    return out


sources = {}
sources.update(collect(MAIN))
sources.update(collect(TEST))

# 클래스 정보 수집
#   name -> dict(kind, package, fields, methods, constants, components, path)
classes = {}

for path, src in sources.items():
    pkg = re.search(r'^package\s+([\w.]+);', src, re.M)
    pkg = pkg.group(1) if pkg else ''
    body_types = re.finditer(
        r'^(?:@[\w.]+(?:\([^)]*\))?\s*)*'
        r'(?:public\s+|final\s+|abstract\s+)*'
        r'(class|interface|enum|record)\s+(\w+)',
        src, re.M)
    for m in body_types:
        kind, name = m.group(1), m.group(2)
        info = classes.setdefault(name, {
            'kind': kind, 'package': pkg, 'path': path,
            'fields': set(), 'methods': set(), 'constants': set(), 'components': None,
            'src': src,
        })
        info['kind'] = kind

    # 필드 (enum 안의 public static final 상수까지 포함해야 오탐이 나지 않는다)
    for fm in re.finditer(r'^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?'
                          r'[\w.<>\[\],\s?]+?\s+(\w+)\s*[;=]', src, re.M):
        for name, info in classes.items():
            if info['path'] == path:
                info['fields'].add(fm.group(1))

    # 메서드 (public/protected/package)
    for mm in re.finditer(r'^\s*(?:public|protected)\s+(?:static\s+)?(?:<[^>]+>\s+)?'
                          r'[\w.<>\[\],\s?]+?\s+(\w+)\s*\(', src, re.M):
        for name, info in classes.items():
            if info['path'] == path:
                info['methods'].add(mm.group(1))

    # 인터페이스(Repository) 의 메서드 — 접근자 없이 선언된다
    for mm in re.finditer(r'^\s{4}(?:[\w.<>\[\],\s?]+?)\s+(\w+)\s*\([^;{]*\)\s*;', src, re.M):
        for name, info in classes.items():
            if info['path'] == path and info['kind'] == 'interface':
                info['methods'].add(mm.group(1))

# enum 상수 · record 컴포넌트
for name, info in classes.items():
    src = info['src']
    if info['kind'] == 'enum':
        m = re.search(r'enum\s+' + name + r'\s*\{(.*?)(?:;|\n\s*(?:private|public|protected|\}))',
                      src, re.S)
        if m:
            for c in re.finditer(r'\b([A-Z][A-Z0-9_]{1,})\s*(?:\(|,|;|\n)', m.group(1)):
                info['constants'].add(c.group(1))
    if info['kind'] == 'record':
        m = re.search(r'record\s+' + name + r'\s*\((.*?)\)\s*\{', src, re.S)
        if m:
            params = [p for p in re.split(r',(?![^<>]*>)', m.group(1)) if p.strip()]
            info['components'] = [p.strip().split()[-1] for p in params]

TARGET_PREFIXES = ('FarmCustomer', 'CustomerStatus', 'CenterFarm', 'FarmSearch', 'FarmNetwork',
                   'AdminFarmCustomerController')


def is_target(path):
    return any(os.path.basename(path).startswith(p) for p in TARGET_PREFIXES)


target_files = {p: s for p, s in sources.items() if is_target(p)}
if not target_files:
    print('검사 대상 파일을 찾지 못했습니다.')
    sys.exit(1)

# ------------------------------------------------------------------
# 1. 타입 해석
# ------------------------------------------------------------------
for path, src in target_files.items():
    pkg = re.search(r'^package\s+([\w.]+);', src, re.M).group(1)
    imports = set(re.findall(r'^import\s+(?:static\s+)?([\w.]+);', src, re.M))
    imported_simple = {i.split('.')[-1] for i in imports}
    same_pkg = {n for n, i in classes.items() if i['package'] == pkg}
    self_name = os.path.basename(path)[:-5]

    body = re.sub(r'^package[^\n]*\n|^import[^\n]*\n', '', src, flags=re.M)
    body = re.sub(r'"""(.*?)"""', ' ', body, flags=re.S)   # 텍스트 블록(JPQL) 제외
    body = re.sub(r'"(?:[^"\\]|\\.)*"', ' ', body)          # 문자열 제외
    body = re.sub(r'//[^\n]*|/\*.*?\*/', ' ', body, flags=re.S)  # 주석 제외

    for tm in re.finditer(r'\b([A-Z][A-Za-z0-9]*)\b', body):
        t = tm.group(1)
        if t in JAVA_LANG or t == self_name:
            continue
        if t in imported_simple or t in same_pkg:
            continue
        if re.fullmatch(r'[A-Z][A-Z0-9_]*', t):   # 상수 표기
            continue
        fail(f'[타입해석] {os.path.basename(path)} : {t} 를 import 하지 않았고 같은 패키지에도 없다')

# ------------------------------------------------------------------
# 2. 빌더 체인
# ------------------------------------------------------------------
def top_level_calls(chain):
    """빌더 체인에서 최상위 호출만 뽑는다.

    .farmCode(farm.getFarmCode()) 에서 farmCode 만 가져와야 한다.
    괄호 깊이를 세지 않으면 인자 안의 getFarmCode 까지 빌더 메서드로 오인한다.
    """
    out, i, depth = [], 0, 0
    while i < len(chain):
        ch = chain[i]
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        elif ch == '.' and depth == 0:
            m = re.match(r'\.(\w+)\s*\(', chain[i:])
            if m:
                out.append(m.group(1))
        i += 1
    return out


for path, src in sources.items():
    for bm in re.finditer(r'\b([A-Z]\w*)\.builder\(\)(.*?)\.build\(\)', src, re.S):
        cls, chain = bm.group(1), bm.group(2)
        if cls not in classes:
            continue
        info = classes[cls]
        if not info['fields']:
            continue
        for prop in top_level_calls(chain):
            if prop in info['fields'] or prop in info['methods']:
                continue
            fail(f'[빌더] {os.path.basename(path)} : {cls}.builder().{prop}() — '
                 f'{cls} 에 {prop} 필드가 없다')

# ------------------------------------------------------------------
# 3. record 생성자 인자 수
# ------------------------------------------------------------------
for path, src in sources.items():
    for nm in re.finditer(r'new\s+([A-Z]\w*)\s*\(', src):
        cls = nm.group(1)
        info = classes.get(cls)
        if not info or info['components'] is None:
            continue
        start = nm.end()
        depth, i, arg, args = 1, start, '', []
        while i < len(src) and depth > 0:
            ch = src[i]
            if ch in '([': depth += 1
            elif ch in ')]':
                depth -= 1
                if depth == 0:
                    break
            if depth == 1 and ch == ',':
                args.append(arg); arg = ''
            else:
                arg += ch
            i += 1
        args.append(arg)
        args = [a for a in args if a.strip()]
        if len(args) != len(info['components']):
            fail(f'[record] {os.path.basename(path)} : new {cls}(...) 인자 {len(args)}개 != '
                 f'컴포넌트 {len(info["components"])}개')

# ------------------------------------------------------------------
# 4. enum 상수 참조 (JPQL 문자열 안까지)
# ------------------------------------------------------------------
for path, src in sources.items():
    for em in re.finditer(r'\b(?:com\.feedflow\.domain\.)?([A-Z]\w*)\.([A-Z][A-Z0-9_]+)\b', src):
        cls, const = em.group(1), em.group(2)
        info = classes.get(cls)
        if not info or info['kind'] != 'enum' or not info['constants']:
            continue
        if const in info['constants']:
            continue
        if const in info['fields'] or const in info['methods']:
            continue
        fail(f'[enum] {os.path.basename(path)} : {cls}.{const} — 그런 상수가 없다 '
             f'(있는 것: {sorted(info["constants"])})')

# ------------------------------------------------------------------
# 5. JPQL 파라미터 ↔ @Param
# ------------------------------------------------------------------
for path, src in sources.items():
    if 'Repository' not in os.path.basename(path):
        continue
    for qm in re.finditer(r'@Query\(\s*"""(.*?)"""\s*\)\s*([^;]+);', src, re.S):
        jpql, sig = qm.group(1), qm.group(2)
        used = set(re.findall(r':(\w+)', jpql))
        declared = set(re.findall(r'@Param\("(\w+)"\)', sig))
        method = re.search(r'(\w+)\s*\(', sig)
        method = method.group(1) if method else '?'
        if used - declared:
            fail(f'[JPQL] {os.path.basename(path)}#{method} : 쿼리의 :{sorted(used - declared)} '
                 f'에 대응하는 @Param 이 없다')
        if declared - used:
            fail(f'[JPQL] {os.path.basename(path)}#{method} : @Param {sorted(declared - used)} '
                 f'을 쿼리가 쓰지 않는다')

# ------------------------------------------------------------------
# 6. 우리 클래스 메서드 호출 존재 여부 (정적 팩토리 · 유틸)
# ------------------------------------------------------------------
for path, src in target_files.items():
    for cm in re.finditer(r'\b([A-Z]\w*)\.(\w+)\s*\(', src):
        cls, method = cm.group(1), cm.group(2)
        info = classes.get(cls)
        if not info or info['kind'] == 'enum':
            continue
        if method in ('builder', 'valueOf', 'values', 'class', 'of', 'forClass'):
            continue
        if not info['methods']:
            continue
        if method in info['methods'] or method in info['fields']:
            continue
        fail(f'[메서드] {os.path.basename(path)} : {cls}.{method}() — {cls} 에 그런 메서드가 없다')

# ------------------------------------------------------------------
# 7. Thymeleaf 프로퍼티 ↔ DTO 게터
# ------------------------------------------------------------------
DTO_OF_VAR = {
    'f': 'FarmCustomerDto',
    'search': 'FarmSearchDto',
    'farmNetwork': 'FarmNetworkDto',
    'c': 'CenterFarmSummaryDto',
}

tpl = os.path.join(TEMPLATES, 'admin/farm-customers.html')
if os.path.exists(tpl):
    html = open(tpl, encoding='utf-8').read()
    for pm in re.finditer(r'\$\{(\w+)\.(\w+)', html):
        var, prop = pm.group(1), pm.group(2)
        cls = DTO_OF_VAR.get(var)
        if not cls:
            continue
        info = classes.get(cls)
        if not info:
            fail(f'[템플릿] {cls} 클래스를 찾을 수 없다')
            continue
        cands = {prop,
                 'get' + prop[0].upper() + prop[1:],
                 'is' + prop[0].upper() + prop[1:]}
        if cands & (info['methods'] | info['fields']):
            continue
        fail(f'[템플릿] farm-customers.html : ${{{var}.{prop}}} — '
             f'{cls} 에 {prop} 게터가 없다')

# ------------------------------------------------------------------
# 결과
# ------------------------------------------------------------------
print(f'검사 대상: {len(target_files)}개 파일 / 수집한 타입 {len(classes)}개')
if warns:
    print(f'\n경고 {len(warns)}건')
    for w in warns:
        print('  -', w)
if fails:
    print(f'\n실패 {len(fails)}건')
    for f in fails:
        print('  -', f)
    sys.exit(1)
print('\n컴파일 리스크 검사 통과 (타입 · 빌더 · record · enum · JPQL · 메서드 · 템플릿)')

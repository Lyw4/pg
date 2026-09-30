#!/usr/bin/env python3
"""빌드를 돌릴 수 없는 환경에서의 정적 전수 검사."""
import re, os, sys, glob, json, subprocess, collections
from html.parser import HTMLParser

ROOT = 'src'
problems = []
def bad(cat, msg):
    problems.append((cat, msg))

java_files = sorted(glob.glob(ROOT + '/**/*.java', recursive=True))
src = {f: open(f, encoding='utf-8').read() for f in java_files}

# 프로젝트에 정의된 최상위 타입
declared = {}
for f, t in src.items():
    declared[os.path.basename(f)[:-5]] = f

# 중첩 타입 (record / class / enum / interface)
nested = collections.defaultdict(set)
for f, t in src.items():
    outer = os.path.basename(f)[:-5]
    for m in re.finditer(r'\b(?:record|class|enum|interface)\s+([A-Z]\w*)', t):
        if m.group(1) != outer:
            nested[outer].add(m.group(1))

def strip_java(t):
    """문자열 · 문자 · 주석 제거"""
    out, i, n = [], 0, len(t)
    while i < n:
        c = t[i]
        if c == '"':
            if t[i:i+3] == '"""':
                j = t.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            i += 1
            while i < n and t[i] != '"':
                i += 2 if t[i] == '\\' else 1
            i += 1
            out.append(' ')
            continue
        if c == "'":
            i += 1
            while i < n and t[i] != "'":
                i += 2 if t[i] == '\\' else 1
            i += 1
            out.append(' ')
            continue
        if t[i:i+2] == '//':
            j = t.find('\n', i)
            i = n if j < 0 else j
            continue
        if t[i:i+2] == '/*':
            j = t.find('*/', i + 2)
            i = n if j < 0 else j + 2
            out.append(' ')
            continue
        out.append(c)
        i += 1
    return ''.join(out)

code = {f: strip_java(t) for f, t in src.items()}

# ── 1. 중괄호 / 괄호 균형 ─────────────────────────────────────
for f, t in code.items():
    for open_c, close_c, name in (('{', '}', '중괄호'), ('(', ')', '괄호'), ('[', ']', '대괄호')):
        d = t.count(open_c) - t.count(close_c)
        if d:
            bad('괄호', '%s : %s 불균형 %+d' % (f, name, d))

# ── 2. import 누락 (프로젝트 클래스) ─────────────────────────
JDK = set('''String Integer Long Double Boolean Object Math System Override Deprecated
SuppressWarnings FunctionalInterface SafeVarargs Exception RuntimeException Error Throwable
IllegalArgumentException IllegalStateException NullPointerException UnsupportedOperationException
Iterable Comparable Runnable Thread Number Character Byte Short Float Void Class Enum Record
StringBuilder CharSequence Cloneable AutoCloseable Comparator Iterator'''.split())

for f, t in code.items():
    pkgm = re.search(r'^\s*package\s+([\w.]+)\s*;', t, re.M)
    pkg = pkgm.group(1) if pkgm else ''
    imports = set()
    for m in re.finditer(r'^\s*import\s+(?:static\s+)?([\w.]+)\s*;', t, re.M):
        imports.add(m.group(1).rsplit('.', 1)[-1])
        imports.add(m.group(1))
    star = set(m.group(1) for m in re.finditer(r'^\s*import\s+([\w.]+)\.\*\s*;', t, re.M))
    outer = os.path.basename(f)[:-5]
    same_pkg = set(os.path.basename(p)[:-5] for p in java_files
                   if (re.search(r'^\s*package\s+([\w.]+)\s*;', code[p], re.M) or [None])
                   and re.search(r'^\s*package\s+([\w.]+)\s*;', code[p], re.M).group(1) == pkg)
    known = imports | same_pkg | nested[outer] | {outer} | JDK
    # 이 파일 안에서 참조하는 프로젝트 타입
    body = re.sub(r'^\s*(package|import).*$', '', t, flags=re.M)
    for m in re.finditer(r'\b([A-Z]\w*)\b', body):
        name = m.group(1)
        if name in known:
            continue
        if name in declared:
            bad('import', '%s : %s 를 쓰는데 import 가 없다' % (f, name))
            known.add(name)

# ── 3. 미사용 import ────────────────────────────────────────
for f, t in code.items():
    body = re.sub(r'^\s*import\s+.*$', '', t, flags=re.M)
    for m in re.finditer(r'^\s*import\s+(?:static\s+)?([\w.]+)\s*;', t, re.M):
        fq = m.group(1)
        if fq.endswith('.*'):
            continue
        simple = fq.rsplit('.', 1)[-1]
        doc_ok = re.search(r'\{@link[s]?\s+' + re.escape(simple) + r'\b', src[f]) \
                 or re.search(r'@see\s+' + re.escape(simple) + r'\b', src[f])
        if not re.search(r'\b' + re.escape(simple) + r'\b', body) and not doc_ok:
            bad('미사용import', '%s : %s' % (f, fq))

# ── 4. enum switch 망라성 ───────────────────────────────────
enum_consts = {}
for f, t in src.items():
    m = re.search(r'\benum\s+(\w+)\s*\{(.*)', t, re.S)
    if not m:
        continue
    name, rest = m.group(1), m.group(2)
    # 상수 목록은 첫 세미콜론 전까지
    head = rest.split(';')[0]
    consts = []
    depth = 0
    token = ''
    for ch in head:
        if ch in '(':
            depth += 1
        elif ch in ')':
            depth -= 1
        if depth == 0 and ch == ',':
            consts.append(token); token = ''
        else:
            token += ch
    consts.append(token)
    got = []
    for c in consts:
        mm = re.match(r'\s*([A-Z][A-Z0-9_]*)\b', c)
        if mm:
            got.append(mm.group(1))
    if got:
        enum_consts[name] = got

for f, t in code.items():
    for m in re.finditer(r'switch\s*\(([^)]*)\)\s*\{', t):
        start = t.index('{', m.end() - 1)
        depth, i = 0, start
        while i < len(t):
            if t[i] == '{':
                depth += 1
            elif t[i] == '}':
                depth -= 1
                if depth == 0:
                    break
            i += 1
        block = t[start:i]
        labels = set(re.findall(r'\bcase\s+([A-Z][A-Z0-9_]*)\b', block))
        has_default = re.search(r'\bdefault\s*(->|:)', block) is not None
        if not labels or has_default:
            continue
        for en, consts in enum_consts.items():
            if labels and labels <= set(consts) and len(labels) >= 2:
                missing = set(consts) - labels
                if missing and len(labels) > len(consts) / 2:
                    bad('switch', '%s : %s switch 에 %s 가 빠졌고 default 도 없다'
                        % (f, en, sorted(missing)))
                break

# ── 5. JPQL select new 인자 수 ↔ record 컴포넌트 수 ───────────
def record_components(name):
    path = declared.get(name)
    comps = None
    if path:
        m = re.search(r'\brecord\s+' + name + r'\s*\(', code[path])
        if m:
            i = m.end() - 1
            depth, j = 0, i
            while j < len(code[path]):
                if code[path][j] == '(':
                    depth += 1
                elif code[path][j] == ')':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            inner = code[path][i+1:j].strip()
            comps = 0 if not inner else len(split_top(inner))
    return comps

def split_top(s, sep=',', pairs='(<['):
    parts, buf, depth = [], [], 0
    closing = {'(': ')', '<': '>', '[': ']'}
    opens = pairs
    closes = ''.join(closing[c] for c in pairs)
    for c in s:
        if c in opens:
            depth += 1
        elif c in closes:
            depth -= 1
        if c == sep and depth == 0:
            parts.append(''.join(buf)); buf = []
        else:
            buf.append(c)
    parts.append(''.join(buf))
    return [p.strip() for p in parts if p.strip()]

for f, t in src.items():
    for m in re.finditer(r'new\s+((?:\w+\.)*\w+)\s*\(', t):
        # JPQL 문자열 안의 것만 (=@Query 안). 대충 잡되 select new 형태만
        pre = t[max(0, m.start() - 200):m.start()]
        if 'select' not in pre.lower():
            continue
        cls = m.group(1).rsplit('.', 1)[-1]
        comps = record_components(cls)
        if comps is None:
            continue
        i = m.end() - 1
        depth, j = 0, i
        while j < len(t):
            if t[j] == '(':
                depth += 1
            elif t[j] == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        args = split_top(t[i+1:j], pairs='(')
        if len(args) != comps:
            bad('JPQL', '%s : select new %s 인자 %d 개인데 record 컴포넌트는 %d 개'
                % (f, cls, len(args), comps))

# ── 6. 스텁 ↔ 프로덕션 리포지토리 메서드 정합성 ──────────────
JPA_INHERITED = set('''save saveAll saveAndFlush saveAllAndFlush findById findAll findAllById
existsById deleteById delete deleteAll deleteAllById deleteAllInBatch deleteInBatch count flush
getById getOne getReferenceById findOne findBy exists'''.split())
repo_methods = {}
for f, t in src.items():
    if '/repository/' not in f:
        continue
    name = os.path.basename(f)[:-5]
    ms = set(re.findall(r'\b(\w+)\s*\(', re.sub(r'@\w+(\([^)]*\))?', '', code[f])))
    repo_methods[name] = ms | JPA_INHERITED

# 프로덕션 코드에서 호출하는 리포지토리 메서드
prod_calls = collections.defaultdict(set)
for f, t in code.items():
    if '/test/' in f:
        continue
    for m in re.finditer(r'\b(\w*[Rr]epository)\s*\.\s*(\w+)\s*\(', t):
        prod_calls[m.group(1)[0].upper() + m.group(1)[1:]].add(m.group(2))

test_files = [f for f in java_files if '/test/' in f]
for f in test_files:
    t = code[f]
    for m in re.finditer(r'(?:given|when)\s*\(\s*(\w*[Rr]epository)\s*\.\s*(\w+)\s*\(', t):
        repo, meth = m.group(1)[0].upper() + m.group(1)[1:], m.group(2)
        if repo in repo_methods and meth not in repo_methods[repo]:
            bad('스텁', '%s : %s.%s() 를 스텁하는데 리포지토리에 그런 메서드가 없다' % (f, repo, meth))
    for m in re.finditer(r'verify\s*\([^)]*?(\w*[Rr]epository)[^)]*?\)\s*\.\s*(\w+)\s*\(', t):
        repo, meth = m.group(1)[0].upper() + m.group(1)[1:], m.group(2)
        if repo in repo_methods and meth not in repo_methods[repo]:
            bad('스텁', '%s : verify %s.%s() 인데 리포지토리에 그런 메서드가 없다' % (f, repo, meth))

# ── 7. CSS 중괄호 ──────────────────────────────────────────
for f in glob.glob(ROOT + '/main/resources/static/css/*.css'):
    t = open(f, encoding='utf-8').read()
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    d = t.count('{') - t.count('}')
    if d:
        bad('CSS', '%s : 중괄호 불균형 %+d' % (f, d))

# ── 8. 템플릿 태그 중첩 ─────────────────────────────────────
VOID = {'area','base','br','col','embed','hr','img','input','link','meta','param',
        'source','track','wbr'}
class TagCheck(HTMLParser):
    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path, self.stack = path, []
    def handle_starttag(self, tag, attrs):
        if tag not in VOID:
            self.stack.append((tag, self.getpos()[0]))
    def handle_startendtag(self, tag, attrs):
        pass
    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if not self.stack:
            bad('템플릿', '%s:%d : </%s> 가 여는 태그 없이 닫힌다' % (self.path, self.getpos()[0], tag))
            return
        if self.stack[-1][0] == tag:
            self.stack.pop(); return
        for k in range(len(self.stack) - 1, -1, -1):
            if self.stack[k][0] == tag:
                unclosed = [s for s in self.stack[k+1:]]
                bad('템플릿', '%s:%d : </%s> 앞에서 %s 가 닫히지 않았다'
                    % (self.path, self.getpos()[0], tag,
                       ['%s(%d행)' % s for s in unclosed]))
                del self.stack[k:]
                return
        bad('템플릿', '%s:%d : </%s> 에 대응하는 여는 태그가 없다' % (self.path, self.getpos()[0], tag))

templates = sorted(glob.glob(ROOT + '/main/resources/templates/**/*.html', recursive=True))
for f in templates:
    p = TagCheck(f)
    p.feed(open(f, encoding='utf-8').read())
    p.close()
    if p.stack:
        bad('템플릿', '%s : 닫히지 않은 태그 %s' % (f, ['%s(%d행)' % s for s in p.stack]))

# ── 9. 인라인 JS 문법 ───────────────────────────────────────
for f in templates:
    t = open(f, encoding='utf-8').read()
    for idx, m in enumerate(re.finditer(r'<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>', t, re.S)):
        js = m.group(1)
        if 'th:inline="javascript"' in m.group(0) or '[[' in js:
            continue
        tmp = '/projects/scratch/_inline_%s_%d.js' % (os.path.basename(f).replace('.', '_'), idx)
        open(tmp, 'w', encoding='utf-8').write(js)
        r = subprocess.run(['node', '--check', tmp], capture_output=True, text=True,
                           env=dict(os.environ, NODE_OPTIONS=''))
        if r.returncode:
            bad('인라인JS', '%s (script #%d) : %s' % (f, idx, r.stderr.strip().splitlines()[-3:]))
        os.remove(tmp)

for f in glob.glob(ROOT + '/main/resources/static/js/*.js'):
    r = subprocess.run(['node', '--check', f], capture_output=True, text=True,
                       env=dict(os.environ, NODE_OPTIONS=''))
    if r.returncode:
        bad('JS', '%s : %s' % (f, r.stderr.strip().splitlines()[-3:]))

# ── 10. 컨트롤러 모델 키 ↔ 템플릿 ${} ────────────────────────
# 컨트롤러가 반환하는 뷰 이름 → 그 컨트롤러가 넣는 model 키
# 플래시 속성 · 예외 핸들러가 넣는 공용 키 (FlashAttr 상수)
FLASH_KEYS = set()
_fa = 'src/main/java/com/feedflow/admin/controller/FlashAttr.java'
if os.path.exists(_fa):
    FLASH_KEYS = set(re.findall(r'=\s*"(\w+)"', open(_fa, encoding='utf-8').read()))
for _f, _t in list(src.items()):
    if 'ExceptionHandler' in _f:
        FLASH_KEYS |= set(re.findall(r'add(?:Flash)?Attribute\s*\(\s*"?(\w+)"?', _t))
view_keys = collections.defaultdict(set)
for f, t in code.items():
    if '/controller/' not in f:
        continue
    keys = set(re.findall(r'add(?:Flash)?Attribute\s*\(\s*"?(\w+)"?', src[f]))
    keys |= set(re.findall(r'@ModelAttribute\s*\(\s*"(\w+)"', src[f]))
    keys |= set(re.findall(r'\.addAttribute\s*\(\s*(\w+)', t))
    consts = dict(re.findall(r'static\s+final\s+String\s+(\w+)\s*=\s*"([\w/\-]+)"', src[f]))
    views = set(re.findall(r'return\s+"([\w/\-]+)"\s*;', src[f]))
    for ident in re.findall(r'return\s+([A-Z_][A-Z0-9_]*)\s*;', src[f]):
        if ident in consts:
            views.add(consts[ident])
    for v in views:
        if '/' in v or v in ('login',):
            view_keys[v] |= keys | FLASH_KEYS
for f in templates:
    rel = f[len(ROOT + '/main/resources/templates/'):-5]
    if rel not in view_keys:
        continue
    t = open(f, encoding='utf-8').read()
    refs = set()
    for m in re.finditer(r'\$\{\s*(\w+)', t):
        refs.add(m.group(1))
    THY = {'param', 'session', 'request', 'response', 'servletContext', 'locale',
           'T', '_csrf', 'stat', 'i', 'e', 'c', 'b', 'p', 'r', 'z', 'l', 'o', 'it',
           'row', 'item', 'true', 'false', 'null', '__',
           # SpEL · Thymeleaf 논리 연산자. ${not #lists.isEmpty(x)} 처럼 표현식
           # 맨 앞에 오면 모델 키로 잘못 읽힌다.
           'not', 'and', 'or'}
    unknown = sorted(r for r in refs - view_keys[rel] - THY
                     if not re.match(r'^(th|sec)$', r))
    # 반복 변수 등 지역 변수를 걸러내기 위해 th:each 선언 변수 제거
    local = set(re.findall(r'th:each="\s*(\w+)', t)) | set(re.findall(r',\s*(\w+)\s*:', t))
    local |= set(re.findall(r'th:with="\s*(\w+)\s*=', t))
    local |= set(re.findall(r'th:object="\$\{(\w+)\}"', t))
    unknown = [u for u in unknown if u not in local]
    if unknown:
        bad('모델키', '%s : 컨트롤러가 넣지 않는 키를 참조 → %s' % (f, unknown))

# ── 출력 ──────────────────────────────────────────────────


# ══════════════════════════════════════════════════════════
# 11. 템플릿 ${x.prop} ↔ DTO/도메인 프로퍼티 존재 여부
# ══════════════════════════════════════════════════════════
props = set()
for f, t in src.items():
    c = code[f]
    # record 컴포넌트
    for m in re.finditer(r'\brecord\s+\w+\s*\(', c):
        i = m.end() - 1
        depth, j = 0, i
        while j < len(c):
            if c[j] == '(':
                depth += 1
            elif c[j] == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        for comp in split_top(c[i+1:j], pairs='(<['):
            mm = re.search(r'(\w+)\s*$', comp)
            if mm:
                props.add(mm.group(1))
    # getter / boolean is-getter / 일반 public 메서드
    for m in re.finditer(r'\bpublic\s+(?:static\s+)?[\w<>\[\],.\s?]+?\s+(\w+)\s*\(', c):
        name = m.group(1)
        if name.startswith('get') and len(name) > 3:
            props.add(name[3].lower() + name[4:])
        elif name.startswith('is') and len(name) > 2 and name[2].isupper():
            props.add(name[2].lower() + name[3:])
        props.add(name)
    # 모든 필드 (Lombok @Getter 로 게터가 생성되는 경우를 포함한다)
    for m in re.finditer(r'\b(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?\s]*?\s(\w+)\s*[;=]', c):
        props.add(m.group(1))
    # enum 상수도 ${T(...).X} 형태로 쓰일 수 있다
    for m in re.finditer(r'^\s*([A-Z][A-Z0-9_]*)\s*[(,;]', c, re.M):
        props.add(m.group(1))

# Map 기반 접근 · Thymeleaf 내장 객체 · 반복 상태 변수는 제외
MAP_LIKE = set('''size isEmpty containsKey keySet values entrySet length empty
key value index count first last even odd current
description label name code title text
totalElements totalPages number numberOfElements content sort pageable
param session request logout error'''.split())

for f in templates:
    t = open(f, encoding='utf-8').read()
    for m in re.finditer(r'[\$\*]\{([^}]*)\}', t):
        expr = m.group(1)
        for chain in re.finditer(r'(?<![#\w.])([a-z]\w*)((?:\.[a-zA-Z_]\w*)+)', expr):
            for prop in chain.group(2).lstrip('.').split('.'):
                if prop in props or prop in MAP_LIKE:
                    continue
                # #numbers 같은 유틸 호출, T(...) 는 위 정규식에 걸리지 않는다
                bad('DTO프로퍼티', '%s : ${%s} 의 .%s 에 해당하는 프로퍼티/게터가 없다'
                    % (f, expr.strip()[:60], prop))

# ══════════════════════════════════════════════════════════
# 12. JS 가 읽는 JSON 필드 ↔ record 컴포넌트
# ══════════════════════════════════════════════════════════
def components_of(path, record_name):
    t = code[path]
    m = re.search(r'\brecord\s+' + record_name + r'\s*\(', t)
    if not m:
        return None
    i = m.end() - 1
    depth, j = 0, i
    while j < len(t):
        if t[j] == '(':
            depth += 1
        elif t[j] == ')':
            depth -= 1
            if depth == 0:
                break
        j += 1
    out = []
    for comp in split_top(t[i+1:j], pairs='(<['):
        mm = re.search(r'(\w+)\s*$', comp)
        if mm:
            out.append(mm.group(1))
    return out

JS_JSON_CONTRACT = [
    ('src/main/resources/static/js/center-map.js', r'\bpin\.(\w+)',
     'src/main/java/com/feedflow/admin/dto/CenterMapPinDto.java', 'CenterMapPinDto'),
    ('src/main/resources/static/js/center-map.js', r'\bdata\.(\w+)',
     'src/main/java/com/feedflow/admin/dto/CenterMapPinDto.java', 'Response'),
]
for js, pat, dto, rec in JS_JSON_CONTRACT:
    if not os.path.exists(js) or dto not in code:
        continue
    comps = components_of(dto, rec)
    if comps is None:
        bad('JSON계약', '%s : record %s 를 찾지 못했다' % (dto, rec))
        continue
    body = open(js, encoding='utf-8').read()
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    body = re.sub(r'//.*$', '', body, flags=re.M)
    used = set(re.findall(pat, body))
    for u in sorted(used):
        if u not in comps and u not in ('length', 'toLocaleString'):
            bad('JSON계약', '%s : JSON 필드 .%s 를 읽는데 %s 에는 없다 (있는 것: %s)'
                % (js, u, rec, comps))



# ══════════════════════════════════════════════════════════
# 13. 모델 키 ↔ 특정 DTO 프로퍼티 (1단계) 정밀 대조
#
# 11번 검사는 프로젝트 전체 프로퍼티 이름을 한 집합으로 모아 비교한다. 그래서
# ${inboundResult.binId} 처럼 "객체는 맞고 그 객체에 없는 필드" 는 통과한다.
# binId 가 다른 DTO 에 있으면 집합에는 존재하기 때문이다.
# 실제로 그 실수를 했으므로, 자주 손대는 모델 키만 대상 DTO 를 명시해 정밀 대조한다.
# ══════════════════════════════════════════════════════════
MODEL_KEY_DTO = {
    'inboundResult': 'InboundResultDto',
    'network': 'CenterNetworkDto',
    'outboundResult': 'OutboundResultDto',
    'moveResult': 'StockMoveResultDto',
    'disposalResult': 'DisposalResultDto',
    'traceability': 'TraceabilityDto',
    'syncResult': 'StockSyncResultDto',
    'floorPlan': 'WarehouseFloorPlanDto',
}

def props_of_class(cls_name):
    """그 클래스가 노출하는 1단계 프로퍼티 이름 (record 컴포넌트 · 게터 · 필드)"""
    path = declared.get(cls_name)
    if not path:
        return None
    c = code[path]
    outer = cls_name
    # 중첩 타입의 본문은 제외해야 정확하지만, 여기서는 상위 클래스 본문 전체를 본다.
    # 중첩 타입 프로퍼티까지 허용되어 느슨해질 뿐 오탐은 만들지 않는다.
    out = set()
    m = re.search(r'\brecord\s+' + outer + r'\s*\(', c)
    if m:
        i = m.end() - 1
        depth, j = 0, i
        while j < len(c):
            if c[j] == '(':
                depth += 1
            elif c[j] == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        for comp in split_top(c[i+1:j], pairs='(<['):
            mm = re.search(r'(\w+)\s*$', comp)
            if mm:
                out.add(mm.group(1))
    for m in re.finditer(r'\bpublic\s+(?:static\s+)?[\w<>\[\],.?\s]+?\s+(\w+)\s*\(', c):
        name = m.group(1)
        if name.startswith('get') and len(name) > 3:
            out.add(name[3].lower() + name[4:])
        elif name.startswith('is') and len(name) > 2 and name[2].isupper():
            out.add(name[2].lower() + name[3:])
        out.add(name)
    for m in re.finditer(r'\b(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?'
                         r'[\w<>\[\],.?\s]*?\s(\w+)\s*[;=]', c):
        out.add(m.group(1))
    return out

for f in templates:
    t = open(f, encoding='utf-8').read()
    for key, cls in MODEL_KEY_DTO.items():
        allowed = props_of_class(cls)
        if allowed is None:
            bad('DTO계약', '%s 클래스를 찾지 못했다 (검사 표를 고쳐야 한다)' % cls)
            continue
        for m in re.finditer(r'[\$\*]\{\s*' + key + r'\.(\w+)', t):
            prop = m.group(1)
            if prop not in allowed:
                bad('DTO계약', '%s : ${%s.%s} — %s 에 그런 프로퍼티가 없다'
                    % (os.path.basename(f), key, prop, cls))



# ══════════════════════════════════════════════════════════
# 14. 같은 파일 안에서 정의한 메서드 호출의 인자 수
#
# lot(LOT_ID, product, LOT_NO, 0) 을 썼는데 헬퍼는 lot(Product, int) 였다.
# 테스트 픽스처 헬퍼는 파일 안에만 있어 IDE 없이 고치다 보면 자주 어긋난다.
# 오버로드가 있으면 허용 인자 수가 여러 개이므로 집합으로 모아 비교한다.
# ══════════════════════════════════════════════════════════
def arg_count(argstr):
    """
    최상위 콤마 개수 + 1.

    split_top() 을 쓰면 안 된다. 그 함수는 빈 조각을 버리는데, strip_java() 가
    문자열 리터럴을 지운 뒤에는 인자가 빈 문자열이 되어 통째로 사라진다.
    center(1L, "C1", "예산") 이 인자 1개로 세졌다.
    """
    # strip_java() 는 문자열 리터럴을 공백 하나로 바꾼다. 그래서 bin("A-01") 의
    # 인자 목록이 ' ' 가 되는데, 이것을 "인자 없음" 으로 보면 0개로 세진다.
    # 진짜 인자가 없는 foo() 는 길이 0 이므로 길이로 구분한다.
    if len(argstr) == 0:
        return 0
    depth, n = 0, 1
    for c in argstr:
        if c in '([{<':
            depth += 1
        elif c in ')]}>':
            depth -= 1
        elif c == ',' and depth == 0:
            n += 1
    return n

for f, t in code.items():
    # 선언: 이름 -> 허용 인자 수 집합
    arity = collections.defaultdict(set)
    for m in re.finditer(r'\b(?:public|private|protected|static)\s+[\w<>\[\],.?\s]+?\s(\w+)\s*\(([^;{)]*(?:\([^)]*\)[^;{)]*)*)\)\s*(?:throws [\w,\s.]+)?\{', t):
        name, params = m.group(1), m.group(2)
        if name in ('if', 'for', 'while', 'switch', 'catch', 'return', 'new'):
            continue
        if '...' in params:
            continue                     # 가변 인자는 검사하지 않는다
        arity[name].add(arg_count(params))

    for name, allowed in arity.items():
        for m in re.finditer(r'(?<![.\w])' + re.escape(name) + r'\s*\(', t):
            # 선언 자체는 건너뛴다
            pre = t[max(0, m.start() - 120):m.start()]
            if re.search(r'(?:public|private|protected|static)\s+[\w<>\[\],.?\s]+\s$', pre):
                continue
            i = m.end() - 1
            depth, j = 0, i
            while j < len(t):
                if t[j] == '(':
                    depth += 1
                elif t[j] == ')':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            n = arg_count(t[i+1:j])
            if n not in allowed:
                bad('인자수', '%s : %s(...) 를 인자 %d개로 부르는데 선언은 %s개다'
                    % (os.path.basename(f), name, n, sorted(allowed)))

if not problems:
    print('14 인자 수 검사까지 통과')
else:
    byCat4 = collections.defaultdict(list)
    for cat, msg in problems:
        byCat4[cat].append(msg)
    for cat in sorted(byCat4):
        print('\n[%s] %d건' % (cat, len(byCat4[cat])))
        for m in byCat4[cat][:30]:
            print('  -', m)
    sys.exit(1)

#!/usr/bin/env python3
"""
Mockito STRICT_STUBS 안전성 검사.

@ExtendWith(MockitoExtension.class) 는 쓰이지 않은 스텁을 UnnecessaryStubbingException
으로 실패시킨다. 반대로 스텁하지 않은 호출은 null 을 돌려줘 NullPointerException 이 된다.
빌드를 못 돌리는 환경에서 이 두 가지를 정적으로 잡는다.

테스트 메서드마다:
  스텁한 리포지토리 메서드 집합  vs  호출한 서비스 메서드가 실제로 쓰는 집합
"""
import re, sys, glob, os, collections

def strip_java(t):
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

def body_of(text, start):
    """start 위치의 '{' 부터 짝이 맞는 '}' 까지"""
    depth, i = 0, start
    while i < len(text):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return text[start:i]
        i += 1
    return text[start:]

REPO = r'(\w*[Rr]epository)\s*\.\s*(\w+)\s*\('

# ── 프로덕션: 서비스 메서드가 쓰는 리포지토리 메서드 ────────────────
service_uses = {}          # (서비스클래스, 메서드) -> set((repo, method))
helper_uses = {}           # (서비스클래스, private 메서드) -> set

for f in glob.glob('src/main/java/**/service/*.java', recursive=True):
    cls = os.path.basename(f)[:-5]
    t = strip_java(open(f, encoding='utf-8').read())
    methods = {}
    for m in re.finditer(r'\b(?:public|private|protected)\s+[\w<>\[\],.?\s]+?\s(\w+)\s*\([^;{]*?\)\s*\{', t):
        name = m.group(1)
        if name == cls:
            continue
        start = t.index('{', m.end() - 1)
        methods[name] = methods.get(name, '') + '\n' + body_of(t, start)
    for name, body in methods.items():
        direct = set((r[0][0].upper() + r[0][1:], r[1]) for r in re.findall(REPO, body))
        # 같은 클래스의 다른 메서드를 부르면 그 메서드의 호출도 전이적으로 포함한다
        seen, queue = set(), [name]
        while queue:
            cur = queue.pop()
            if cur in seen or cur not in methods:
                continue
            seen.add(cur)
            b = methods[cur]
            direct |= set((r[0][0].upper() + r[0][1:], r[1]) for r in re.findall(REPO, b))
            for callee in re.findall(r'(?<![.\w])(\w+)\s*\(', b):
                if callee in methods and callee not in seen:
                    queue.append(callee)
        service_uses[(cls, name)] = direct

# 협력 객체(BinCapacityChecker 등)가 부르는 리포지토리 메서드.
# 테스트가 new X(repo) 로 조립해 서비스에 넣어주므로, 그 호출도 서비스 호출로 봐야 한다.
collaborator_uses = {}
for f in glob.glob('src/main/java/**/*.java', recursive=True):
    cls = os.path.basename(f)[:-5]
    body = strip_java(open(f, encoding='utf-8').read())
    calls = set((r[0][0].upper() + r[0][1:], r[1]) for r in re.findall(REPO, body))
    if calls:
        collaborator_uses[cls] = calls

# ── 테스트: 메서드별 스텁 · 호출한 서비스 메서드 ────────────────────
problems = []
for f in sorted(glob.glob('src/test/java/**/*Test.java', recursive=True)):
    raw = open(f, encoding='utf-8').read()
    t = strip_java(raw)
    # 이 테스트가 검사하는 서비스 (필드 @InjectMocks 타입)
    im = re.search(r'@InjectMocks\s+(?:private\s+)?(\w+)\s+(\w+)\s*;', t)
    if im:
        svc_cls, svc_var = im.group(1), im.group(2)
    else:
        # @BeforeEach 에서 손으로 조립하는 형태:
        #     service = new CenterDashboardService(repoA, repoB, ...);
        mk = re.search(r'(\w+)\s*=\s*new\s+(\w+Service)\s*\(', t)
        if not mk:
            continue
        svc_var, svc_cls = mk.group(1), mk.group(2)

    # 이 테스트가 손으로 조립하는 협력 객체들
    collaborators = set()
    for c in re.findall(r'new\s+(\w+)\s*\(', t):
        if c != svc_cls and c in collaborator_uses:
            collaborators |= collaborator_uses[c]

    for m in re.finditer(r'@Test\b', t):
        # 이 @Test 다음의 첫 메서드 본문
        mm = re.search(r'\bvoid\s+(\w+)\s*\([^)]*\)\s*(?:throws [\w,\s.]+)?\{', t[m.end():])
        if not mm:
            continue
        abs_start = m.end() + mm.end() - 1
        body = body_of(t, abs_start)
        test_name = mm.group(1)

        stubs = set()
        for g in re.finditer(r'given\s*\(\s*' + REPO, body):
            stubs.add((g.group(1)[0].upper() + g.group(1)[1:], g.group(2)))

        called = set(re.findall(re.escape(svc_var) + r'\s*\.\s*(\w+)\s*\(', body))
        if not stubs or not called:
            continue

        used = set()
        unknown = False
        for c in called:
            if (svc_cls, c) in service_uses:
                used |= service_uses[(svc_cls, c)]
            else:
                unknown = True
        if unknown:
            continue

        used |= collaborators
        extra = stubs - used
        if extra:
            problems.append('%s :: %s\n      쓰이지 않는 스텁 %s\n      → Mockito STRICT_STUBS 가 '
                            'UnnecessaryStubbingException 으로 실패시킨다'
                            % (os.path.basename(f), test_name, sorted(extra)))

if problems:
    print('의심 %d건:\n' % len(problems))
    for p in problems:
        print('  -', p)
    sys.exit(1)
print('스텁 정합성 검사 통과 (STRICT_STUBS 위반 없음)')

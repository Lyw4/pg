#!/usr/bin/env python3
"""
리팩토링 대상 탐색 — 미사용 import · 미사용 private 멤버 · 주석 처리된 코드 등.

오탐을 줄이는 것이 목적이다. "미사용" 판정은 근거가 약하면 그대로 두는 편이
낫다. 지우면 컴파일이 깨지는데 이 환경에서는 확인할 방법이 없다.
"""
import re, os, glob, collections

ROOT = 'src'
java = sorted(glob.glob(ROOT + '/**/*.java', recursive=True))
raw = {f: open(f, encoding='utf-8').read() for f in java}


def strip_java(t):
    """문자열 · 문자 · 주석 제거 (주석 내용은 따로 모은다)"""
    out, comments, i, n = [], [], 0, len(t)
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
            j = n if j < 0 else j
            comments.append(t[i:j])
            i = j
            continue
        if t[i:i+2] == '/*':
            j = t.find('*/', i + 2)
            j = n if j < 0 else j + 2
            comments.append(t[i:j])
            i = j
            out.append(' ')
            continue
        out.append(c)
        i += 1
    return ''.join(out), comments


code, comments = {}, {}
for f, t in raw.items():
    code[f], comments[f] = strip_java(t)

print('=' * 70)
print('1. 미사용 import')
print('=' * 70)
found = 0
for f, t in code.items():
    body = re.sub(r'^\s*import\s+.*$', '', t, flags=re.M)
    for m in re.finditer(r'^\s*import\s+(?:static\s+)?([\w.]+)\s*;', t, re.M):
        fq = m.group(1)
        if fq.endswith('.*'):
            continue
        simple = fq.rsplit('.', 1)[-1]
        used_in_code = re.search(r'\b' + re.escape(simple) + r'\b', body)
        used_in_doc = re.search(r'\{@link[s]?\s+' + re.escape(simple) + r'\b', raw[f]) \
            or re.search(r'@see\s+' + re.escape(simple) + r'\b', raw[f]) \
            or re.search(r'\{@code\s+' + re.escape(simple) + r'\b', raw[f])
        if not used_in_code:
            tag = '(javadoc 참조만)' if used_in_doc else '*** 완전 미사용 ***'
            print('  %-58s %s  %s' % (os.path.basename(f), simple, tag))
            found += 1
if not found:
    print('  없음')

print()
print('=' * 70)
print('2. 미사용 private 메서드 / 필드')
print('=' * 70)
found = 0
for f, t in code.items():
    cls = os.path.basename(f)[:-5]
    for m in re.finditer(r'\bprivate\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?\s]+?\s(\w+)\s*\(', t):
        name = m.group(1)
        if name == cls:
            continue
        # 선언을 제외한 호출 횟수
        calls = len(re.findall(r'(?<![.\w])' + re.escape(name) + r'\s*\(', t))
        refs = len(re.findall(r'::\s*' + re.escape(name) + r'\b', t))
        if calls <= 1 and refs == 0:
            print('  %-50s private 메서드  %s()' % (os.path.basename(f), name))
            found += 1
    for m in re.finditer(r'\bprivate\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?]+\s+(\w+)\s*[;=]', t):
        name = m.group(1)
        uses = len(re.findall(r'\b' + re.escape(name) + r'\b', t))
        # 선언 1회 + Lombok @Getter 로 노출될 수 있으므로 클래스 애너테이션 확인
        lombok = re.search(r'@(Getter|Data|Value|Builder)', raw[f])
        if uses <= 1 and not lombok:
            print('  %-50s private 필드    %s' % (os.path.basename(f), name))
            found += 1
if not found:
    print('  없음')

print()
print('=' * 70)
print('3. 주석 처리된 코드 (Dead code) 의심')
print('=' * 70)
found = 0
CODEISH = re.compile(
    r'^\s*(//+)\s*('
    r'(?:public|private|protected|return|if|for|while|import|new|final|@\w+)\b'
    r'|\w+\s*\([^)]*\)\s*;'
    r'|\w+\s*=\s*[^=]'
    r'|System\.out'
    r'|log\.'
    r')')
for f, t in raw.items():
    for i, line in enumerate(t.splitlines(), 1):
        if CODEISH.match(line):
            print('  %s:%d  %s' % (os.path.basename(f), i, line.strip()[:80]))
            found += 1
if not found:
    print('  없음')

print()
print('=' * 70)
print('4. TODO / FIXME / XXX / console.log / System.out')
print('=' * 70)
found = 0
for f, t in raw.items():
    for i, line in enumerate(t.splitlines(), 1):
        if re.search(r'\b(TODO|FIXME|XXX|HACK)\b', line) or 'System.out' in line or 'printStackTrace' in line:
            print('  %s:%d  %s' % (os.path.basename(f), i, line.strip()[:80]))
            found += 1
for f in glob.glob(ROOT + '/main/resources/static/js/*.js'):
    t = open(f, encoding='utf-8').read()
    for i, line in enumerate(t.splitlines(), 1):
        if re.search(r'console\.(log|debug)|\b(TODO|FIXME)\b', line):
            print('  %s:%d  %s' % (os.path.basename(f), i, line.strip()[:80]))
            found += 1
if not found:
    print('  없음')

print()
print('=' * 70)
print('5. 아무도 호출하지 않는 public 메서드 (전 저장소 + 템플릿 대조)')
print('=' * 70)
templates = ''.join(open(p, encoding='utf-8').read()
                    for p in glob.glob(ROOT + '/main/resources/templates/**/*.html', recursive=True))
all_code = '\n'.join(code.values())
found = 0
SPRING = re.compile(r'@(Override|Bean|GetMapping|PostMapping|PutMapping|DeleteMapping|'
                    r'RequestMapping|ModelAttribute|ExceptionHandler|EventListener|'
                    r'PrePersist|PreUpdate|PostConstruct|Test|BeforeEach|AfterEach|Query)')
for f, t in code.items():
    if '/test/' in f:
        continue
    cls = os.path.basename(f)[:-5]
    for m in re.finditer(r'\bpublic\s+(?:static\s+)?[\w<>\[\],.?\s]+?\s(\w+)\s*\(([^;{)]*)\)', t):
        name = m.group(1)
        if name == cls or name in ('main', 'equals', 'hashCode', 'toString'):
            continue
        # 스프링/JPA 가 리플렉션으로 부르는 메서드는 제외
        pre = t[max(0, m.start() - 400):m.start()]
        if SPRING.search(pre.split('public')[-1] if 'public' in pre else pre):
            continue
        calls = len(re.findall(r'[.:]\s*' + re.escape(name) + r'\s*\(', all_code))
        selfcalls = len(re.findall(r'(?<![.\w])' + re.escape(name) + r'\s*\(', t))
        # 템플릿에서 getX() -> ${x} / isX() -> ${x} 로 접근
        prop = None
        if name.startswith('get') and len(name) > 3:
            prop = name[3].lower() + name[4:]
        elif name.startswith('is') and len(name) > 2 and name[2].isupper():
            prop = name[2].lower() + name[3:]
        in_tpl = bool(prop and re.search(r'[\$\*]\{[^}]*\b' + re.escape(prop) + r'\b', templates))
        in_tpl = in_tpl or bool(re.search(r'\b' + re.escape(name) + r'\s*\(', templates))
        if calls == 0 and selfcalls <= 1 and not in_tpl:
            print('  %-46s %s()' % (os.path.basename(f), name))
            found += 1
if not found:
    print('  없음')

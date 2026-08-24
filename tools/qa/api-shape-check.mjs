/**
 * 두 서버의 API 응답 "구조"를 비교한다.
 *
 * 값은 환경마다 다르므로 비교하지 않는다. 대신 JSON 의 키 경로 집합과
 * 각 키의 타입을 뽑아 비교한다. 필드가 빠졌거나 타입이 바뀐 경우만 잡힌다.
 *
 * 실행: node api-shape-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const SERVERS = [
  {
    key: 'A',
    label: '팀원 서버',
    base: process.env.PEER_URL ?? 'http://192.168.219.48:8080',
    id: process.env.PEER_ID ?? 'admin',
    pw: process.env.PEER_PW ?? '1234',
  },
  {
    key: 'B',
    label: '내 로컬',
    base: process.env.LOCAL_URL ?? 'http://localhost:8080',
    id: process.env.LOCAL_ID ?? 'admin',
    pw: process.env.LOCAL_PW ?? 'admin1234',
  },
];

const ENDPOINTS = [
  { name: '재고 요약', url: '/api/inventory/summary' },
  { name: '관리자 활동 로그', url: '/api/admin/activities' },
  { name: '결제 설정', url: '/api/payments/config' },
  { name: '회원 본인 정보', url: '/api/members/me' },
  { name: '아이디 중복 확인', url: '/api/members/check-username?username=zzqq-not-exist' },
  { name: '이메일 중복 확인', url: '/api/members/check-email?email=zzqq-not-exist@example.com' },
  { name: '상품 상세(1번)', url: '/api/products/1' },
];

/**
 * JSON 을 "키경로:타입" 집합으로 평탄화한다. 배열은 첫 요소만 대표로 본다.
 *
 * 빈 배열은 요소 타입을 알 수 없다. 그 사실을 `:empty` 로 따로 표시해,
 * 나중에 "필드 누락"과 "표본 없음"을 구분할 수 있게 한다.
 */
function shapeOf(value, prefix = '', out = new Set()) {
  if (value === null) {
    out.add(`${prefix}:null`);
    return out;
  }
  if (Array.isArray(value)) {
    out.add(`${prefix}:array`);
    if (value.length > 0) shapeOf(value[0], `${prefix}[]`, out);
    else out.add(`${prefix}[]:empty`);
    return out;
  }
  if (typeof value === 'object') {
    for (const key of Object.keys(value).sort()) {
      shapeOf(value[key], prefix ? `${prefix}.${key}` : key, out);
    }
    return out;
  }
  out.add(`${prefix}:${typeof value}`);
  return out;
}

/**
 * 한쪽 배열이 비어 있으면 그 아래 필드 차이는 판단 근거가 없다.
 * 그런 경로를 골라내 "표본 없음"으로 분류한다.
 */
function emptyPrefixes(shape) {
  return shape
    .filter((s) => s.endsWith('[]:empty'))
    .map((s) => s.slice(0, -':empty'.length));
}

async function loginCookie(server) {
  const res = await fetch(`${server.base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identifier: server.id, password: server.pw }),
  });
  const setCookie = res.headers.getSetCookie?.() ?? [];
  const jar = setCookie
    .map((c) => c.split(';')[0])
    .filter((c) => c.startsWith('JSESSIONID'))
    .join('; ');
  return { status: res.status, jar };
}

async function probe(server) {
  const { status: loginStatus, jar } = await loginCookie(server);
  const results = {};
  for (const ep of ENDPOINTS) {
    try {
      const res = await fetch(server.base + ep.url, {
        headers: jar ? { Cookie: jar } : {},
      });
      const contentType = res.headers.get('content-type') ?? '';
      let shape = [];
      let parsed = true;
      if (contentType.includes('json')) {
        const body = await res.json();
        shape = [...shapeOf(body)].sort();
      } else {
        parsed = false;
        await res.text();
      }
      results[ep.url] = {
        name: ep.name,
        status: res.status,
        contentType: contentType.split(';')[0],
        json: parsed,
        shape,
      };
    } catch (exception) {
      results[ep.url] = {
        name: ep.name,
        status: -1,
        error: String(exception.message).slice(0, 200),
        shape: [],
      };
    }
  }
  return { server, loginStatus, results };
}

const diff = (a, b) => a.filter((x) => !b.includes(x));

const [ra, rb] = [await probe(SERVERS[0]), await probe(SERVERS[1])];

const lines = [];
lines.push('# API 응답 구조 비교');
lines.push('');
lines.push(`- A ${ra.server.label} : ${ra.server.base} (로그인 ${ra.loginStatus})`);
lines.push(`- B ${rb.server.label} : ${rb.server.base} (로그인 ${rb.loginStatus})`);
lines.push(`- 실행: ${new Date().toLocaleString('ko-KR')}`);
lines.push('');

let problems = 0;
for (const ep of ENDPOINTS) {
  const a = ra.results[ep.url];
  const b = rb.results[ep.url];
  lines.push(`## ${ep.name}  \`${ep.url}\``);
  if (a.status !== b.status) {
    problems += 1;
    lines.push(`- ❗ 상태 다름 — A=${a.status} / B=${b.status}`);
  } else {
    lines.push(`- HTTP ${a.status} (동일)`);
  }
  if (a.contentType !== b.contentType) {
    problems += 1;
    lines.push(`- ❗ Content-Type 다름 — A=${a.contentType} / B=${b.contentType}`);
  }
  // 한쪽이 빈 배열이면 그 아래 경로는 비교 자체가 불가능하다.
  const blindA = emptyPrefixes(a.shape);
  const blindB = emptyPrefixes(b.shape);
  const blind = [...new Set([...blindA, ...blindB])];
  const isBlind = (key) =>
    blind.some((p) => key.startsWith(p)) || key.endsWith('[]:empty');

  const rawOnlyA = diff(a.shape, b.shape);
  const rawOnlyB = diff(b.shape, a.shape);
  const onlyA = rawOnlyA.filter((x) => !isBlind(x));
  const onlyB = rawOnlyB.filter((x) => !isBlind(x));
  const skipped = [...rawOnlyA, ...rawOnlyB].filter((x) => isBlind(x));

  if (onlyA.length || onlyB.length) {
    lines.push('- 필드 구조 차이');
    for (const x of onlyA) {
      problems += 1;
      lines.push(`    - ❗ A에만 (내 로컬 누락): ${x}`);
    }
    for (const x of onlyB) {
      lines.push(`    - B에만 (내 로컬 추가): ${x}`);
    }
  } else if (a.shape.length) {
    lines.push(`- 필드 구조 동일 (비교 가능한 키 ${a.shape.length - blindA.length}개)`);
  }

  if (skipped.length) {
    const side = blindB.length ? 'B' : 'A';
    lines.push(
      `- (참고) ${side} 쪽 컬렉션이 비어 있어 요소 필드 ${skipped.length}건은 비교 불가` +
        ' — 데이터가 없을 뿐이며 누락이 아니다.',
    );
  }
  lines.push('');
}

lines.push('## 요약');
lines.push(`- 확인 필요 ${problems}건`);

const outDir = path.resolve('report');
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'api-shape-check.md'), lines.join('\n') + '\n', 'utf8');
console.log(`문제 ${problems}건 / 리포트: report/api-shape-check.md`);

/**
 * 두 서버의 UI 렌더링과 API 동작을 비교한다.
 *
 *   A: 팀원 개발 서버      http://192.168.219.48:8080
 *   B: 내 로컬 서버        http://localhost:8080
 *
 * 데이터 양이 다른 환경이라 숫자를 그대로 비교하면 노이즈가 압도한다.
 * 그래서 텍스트의 숫자를 #으로 치환한 뒤 "구조"만 비교한다.
 *   - 상호작용 요소(버튼·링크·탭·입력) 목록
 *   - 엘리먼트 id 목록
 *   - data-* 속성 종류
 *   - 표 머리글
 *   - 콘솔 에러 / 페이지 에러 / 실패한 요청
 *
 * 실행: node cross-check.mjs [--headed]
 */
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const HEADED = process.argv.includes('--headed');

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

const PAGES = [
  { name: '메인 홈', url: '/' },
  { name: '유통 화면', url: '/distribution' },
  { name: '유통 - 입금 대기', url: '/distribution?view=payment_pending' },
  { name: '유통 - 출고 준비', url: '/distribution?view=ready' },
  { name: '유통 - 농장 고객사', url: '/distribution?view=farms' },
  { name: '재고 화면', url: '/inventory' },
  { name: 'WMS - 구역 이관', url: '/admin/wms?view=move' },
  { name: 'WMS - 스캔', url: '/admin/scan' },
  { name: '수요 계획', url: '/admin/demand-plan' },
  { name: '마이페이지(비로그인)', url: '/mypage' },
];

/** 브라우저에서 페이지 구조를 수집한다. */
const COLLECT = () => {
  const norm = (s) => (s || '').replace(/\s+/g, ' ').trim();
  const mask = (s) => norm(s).replace(/[\d][\d,.]*/g, '#');

  /*
   * 데이터 개수에 따라 달라지는 식별자는 숫자를 #으로 접어 한 항목으로 묶는다.
   * 예: distribution-order-104 / distribution-order-92 → distribution-order-#
   * 이렇게 하지 않으면 주문이 100건인 서버와 1건인 서버를 비교할 때
   * "누락 99건"이라는 잘못된 결론이 난다. 개수 차이는 표 행 수로 따로 본다.
   */
  const foldId = (s) => (s || '').replace(/\d+/g, '#');

  /* 세션마다 값이 바뀌는 필드는 값을 비교하지 않는다. */
  const VOLATILE_NAMES = ['_csrf'];

  const controls = [];
  document
    .querySelectorAll('button, a[href], input, select, textarea, [role="tab"]')
    .forEach((el) => {
      const name = el.getAttribute('name');
      const volatile = VOLATILE_NAMES.includes(name);
      const label = volatile
        ? '(값 비교 제외)'
        : mask(
            el.getAttribute('aria-label') || el.value || el.textContent || '',
          ).slice(0, 44);
      const parts = [el.tagName.toLowerCase()];
      if (el.id) parts.push('#' + foldId(el.id));
      if (name) parts.push('[name=' + name + ']');
      for (const attr of [
        'data-delivery-view',
        'data-delivery-panel',
        'data-location-warehouse',
        'data-summary-panel',
        'type',
      ]) {
        const v = el.getAttribute(attr);
        if (v) parts.push('[' + attr + '=' + v + ']');
      }
      if (label) parts.push('"' + label + '"');
      controls.push(parts.join(''));
    });

  const ids = [...document.querySelectorAll('[id]')].map((e) => foldId(e.id));

  const dataAttrs = new Set();
  document.querySelectorAll('*').forEach((el) => {
    for (const a of el.attributes) {
      if (a.name.startsWith('data-')) dataAttrs.add(a.name);
    }
  });

  const headers = [];
  document.querySelectorAll('table').forEach((t) => {
    const ths = [...t.querySelectorAll('thead th')].map((th) => mask(th.textContent));
    if (ths.length) headers.push((t.id || '(noid)') + ' :: ' + ths.join(' | '));
  });

  const tables = [...document.querySelectorAll('table')].map((t) => ({
    id: t.id || '(noid)',
    rows: t.querySelectorAll('tbody tr').length,
  }));

  const forms = [...document.querySelectorAll('form')].map((f) => {
    const action = foldId(f.getAttribute('action') || '');
    const method = (f.getAttribute('method') || 'get').toLowerCase();
    const fields = [...f.querySelectorAll('[name]')]
      .map((i) => i.getAttribute('name'))
      .sort();
    return method + ' ' + action + ' (' + fields.join(',') + ')';
  });

  return {
    controls,
    ids,
    dataAttrs: [...dataAttrs].sort(),
    headers,
    tables,
    forms,
    textLength: (document.body.innerText || '').length,
  };
};

async function login(context, server) {
  const res = await context.request.post(`${server.base}/api/auth/login`, {
    data: { identifier: server.id, password: server.pw },
    failOnStatusCode: false,
  });
  let body = '';
  try {
    body = await res.text();
  } catch {
    body = '(본문 없음)';
  }
  return { status: res.status(), body: body.slice(0, 200) };
}

async function capture(browser, server) {
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1600, height: 1000 },
  });
  const auth = await login(context, server);

  const page = await context.newPage();
  const result = { server, auth, pages: {} };

  for (const target of PAGES) {
    const consoleErrors = [];
    const pageErrors = [];
    const failedRequests = [];

    const onConsole = (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 200));
    };
    const onPageError = (err) => pageErrors.push(String(err.message).slice(0, 200));
    const onRequestFailed = (req) =>
      failedRequests.push(`${req.method()} ${req.url().replace(server.base, '')}`);
    const onResponse = (res) => {
      if (res.status() >= 400) {
        failedRequests.push(`${res.status()} ${res.url().replace(server.base, '')}`);
      }
    };

    page.on('console', onConsole);
    page.on('pageerror', onPageError);
    page.on('requestfailed', onRequestFailed);
    page.on('response', onResponse);

    let status = 0;
    let structure = null;
    let error = null;
    try {
      const res = await page.goto(server.base + target.url, {
        waitUntil: 'domcontentloaded',
        timeout: 45000,
      });
      status = res ? res.status() : 0;
      // 클라이언트 렌더링이 끝날 시간을 준다.
      await page.waitForTimeout(1200);
      structure = await page.evaluate(COLLECT);
    } catch (exception) {
      error = String(exception.message).slice(0, 300);
    }

    page.off('console', onConsole);
    page.off('pageerror', onPageError);
    page.off('requestfailed', onRequestFailed);
    page.off('response', onResponse);

    result.pages[target.url] = {
      name: target.name,
      status,
      error,
      consoleErrors,
      pageErrors,
      failedRequests: [...new Set(failedRequests)],
      structure,
    };
  }

  await context.close();
  return result;
}

const setDiff = (a, b) => a.filter((x) => !b.includes(x));
const uniq = (a) => [...new Set(a)];

function compare(a, b) {
  const lines = [];
  lines.push('# 두 서버 크로스 체크 리포트');
  lines.push('');
  lines.push(`- A ${a.server.label} : ${a.server.base}`);
  lines.push(`- B ${b.server.label} : ${b.server.base}`);
  lines.push(`- 실행: ${new Date().toLocaleString('ko-KR')}`);
  lines.push('');
  lines.push('## 로그인');
  lines.push(`- A: HTTP ${a.auth.status} ${a.auth.body}`);
  lines.push(`- B: HTTP ${b.auth.status} ${b.auth.body}`);
  lines.push('');

  let diffCount = 0;

  for (const target of PAGES) {
    const pa = a.pages[target.url];
    const pb = b.pages[target.url];
    lines.push(`## ${target.name}  \`${target.url}\``);

    if (pa.status !== pb.status) {
      diffCount += 1;
      lines.push(`- ❗ **HTTP 상태 다름** — A=${pa.status} / B=${pb.status}`);
    } else {
      lines.push(`- HTTP ${pa.status} (양쪽 동일)`);
    }

    if (pa.error || pb.error) {
      diffCount += 1;
      lines.push(`- ❗ 탐색 오류 — A: ${pa.error ?? '없음'} / B: ${pb.error ?? '없음'}`);
    }

    const errA = uniq([...pa.consoleErrors, ...pa.pageErrors]);
    const errB = uniq([...pb.consoleErrors, ...pb.pageErrors]);
    if (errA.length || errB.length) {
      lines.push(`- 콘솔/페이지 에러 — A ${errA.length}건 / B ${errB.length}건`);
      for (const e of setDiff(errB, errA)) {
        diffCount += 1;
        lines.push(`    - ❗ B에만: ${e}`);
      }
      for (const e of setDiff(errA, errB)) {
        lines.push(`    - A에만: ${e}`);
      }
    }

    const reqA = pa.failedRequests ?? [];
    const reqB = pb.failedRequests ?? [];
    for (const r of setDiff(reqB, reqA)) {
      diffCount += 1;
      lines.push(`- ❗ B에만 실패 요청: ${r}`);
    }
    for (const r of setDiff(reqA, reqB)) {
      lines.push(`- A에만 실패 요청: ${r}`);
    }

    if (!pa.structure || !pb.structure) {
      lines.push('- 구조 수집 실패로 비교 생략');
      lines.push('');
      continue;
    }

    const blocks = [
      ['상호작용 요소', 'controls'],
      ['엘리먼트 id', 'ids'],
      ['data-* 속성', 'dataAttrs'],
      ['표 머리글', 'headers'],
      ['폼', 'forms'],
    ];
    for (const [title, key] of blocks) {
      const onlyA = setDiff(uniq(pa.structure[key]), uniq(pb.structure[key]));
      const onlyB = setDiff(uniq(pb.structure[key]), uniq(pa.structure[key]));
      if (!onlyA.length && !onlyB.length) continue;
      lines.push(`- **${title}** 차이`);
      for (const x of onlyA.slice(0, 25)) {
        diffCount += 1;
        lines.push(`    - A에만 (내 로컬 누락 가능): ${x}`);
      }
      if (onlyA.length > 25) lines.push(`    - ... A에만 ${onlyA.length - 25}건 더`);
      for (const x of onlyB.slice(0, 25)) {
        lines.push(`    - B에만 (내 로컬 추가): ${x}`);
      }
      if (onlyB.length > 25) lines.push(`    - ... B에만 ${onlyB.length - 25}건 더`);
    }

    // 행 수는 환경의 데이터 양이므로 차이 건수에 넣지 않고 참고로만 적는다.
    const ta = pa.structure.tables;
    const tb = pb.structure.tables;
    const rowInfo = [];
    for (const t of ta) {
      const m = tb.find((x) => x.id === t.id);
      if (m && m.rows !== t.rows) rowInfo.push(`${t.id}: A ${t.rows}행 / B ${m.rows}행`);
    }
    if (rowInfo.length) {
      lines.push(`- (참고) 표 행 수 — 데이터 양 차이: ${rowInfo.join(' · ')}`);
    }

    lines.push('');
  }

  lines.push('## 요약');
  lines.push(`- 주의가 필요한 차이 ${diffCount}건`);
  lines.push('- "A에만" 항목이 내 로컬에서 빠진 것이고, "B에만" 항목은 내 로컬에 추가된 것이다.');
  lines.push('');
  lines.push('### 비교에서 제외하는 것');
  lines.push('- CSRF 토큰 값: 세션마다 달라 의미가 없다.');
  lines.push('- 식별자 안의 숫자: `distribution-order-104` 는 `distribution-order-#` 로 접는다.');
  lines.push('- 표 행 수: 데이터 양이라 참고로만 적고 차이 건수에 넣지 않는다.');
  lines.push('');
  lines.push('### 주의');
  lines.push('- 관리자로 로그인하므로 `/mypage` 는 컨트롤러가 대시보드로 보낸다.');
  lines.push('  회원 화면을 비교하려면 회원 세션이 필요한데, 그러려면 두 서버에');
  lines.push('  계정을 만들어야 해서 이 도구는 읽기 전용을 유지한다.');
  return lines.join('\n');
}

const browser = await chromium.launch({ headless: !HEADED });
const results = [];
for (const server of SERVERS) {
  process.stdout.write(`수집 중: ${server.label} ${server.base}\n`);
  results.push(await capture(browser, server));
}
await browser.close();

const report = compare(results[0], results[1]);
const outDir = path.resolve('report');
fs.mkdirSync(outDir, { recursive: true });
const outFile = path.join(outDir, 'cross-check.md');
fs.writeFileSync(outFile, report + '\n', 'utf8');
fs.writeFileSync(
  path.join(outDir, 'cross-check-raw.json'),
  JSON.stringify(results, null, 2),
  'utf8',
);
process.stdout.write(`리포트: ${outFile}\n`);

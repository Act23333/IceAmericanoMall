#!/usr/bin/env node
/**
 * sync-doc-counts.mjs — 根文档「可漂计数」的**唯一写入口**（--write）与**校验器**（--check）
 *
 * 背景：根文档（CLAUDE.md / AGENTS.md / project-docs/00-README.md）里的计数类事实
 * （端点数 / 测试数 / 表数 / 模块数 / ADR 区间）此前靠手工抄写，改一次漂一次。
 * 本脚本把它们变成「从事实源测量 → 定点回填 → 可校验」，人工不再手写这些数字。
 *
 * ── 每个计数的唯一真源（改数字前先看这张表）─────────────────────────────────
 *
 * | metric             | 唯一真源（文件 / 命令）                                                            | 口径说明 |
 * | :----------------- | :-------------------------------------------------------------------------------- | :------- |
 * | endpoints_total    | `Implementation/back-end/**\/adapter/**\/*Controller.java` 的 Spring 映射注解，经           | **代码口径**：方法+路径去重后的映射数。 |
 * | endpoints_public   | `Implementation/back-end/scripts/extract-endpoints.mjs` 的 `code.distinct` /            | 与契约口径（05-API-Specification.md |
 * | endpoints_internal | `code.publicNonInternal` / `code.internal`（脚本内调用，不重写解析逻辑）                 | 表格 distinct，当前 332）是**两个不同 |
 * |                    |                                                                                   | 口径**，不得互相覆盖；契约数不进文档。 |
 * | tests_total        | `Implementation/back-end/**\/target/surefire-reports/*.txt` 首行                        | 只认 surefire 实测聚合。**无报告 = 不 |
 * |                    | `Tests run: N, Failures: F, Errors: E, Skipped: S` 的 N 求和                        | 度量、不写数字**（保留原值并告警）； |
 * |                    |                                                                                   | 除非显式 `--tests=N`（与实测冲突时 |
 * |                    |                                                                                   | 拒绝执行，禁止手填冒充实测）。 |
 * | tables             | `Implementation/back-end/database/Initialize.sql` 的 `CREATE TABLE` 语句               | 计入缩进 / 反引号 / `IF NOT EXISTS` / |
 * |                    |                                                                                   | 大小写变体；`--`、`#`、块注释内的 |
 * |                    |                                                                                   | 语句不计。逐表名清单见 `--list-tables`。 |
 * | modules            | `Implementation/back-end/pom.xml`（根 POM）的 `<module>` 元素数                         | 只数根 POM；子 POM 的 `<module>` 不计。 |
 * | adr_max（写 001–NNN）| `project-docs/adr/0NN-*.md`（排除 `README.md`）                                     | 要求编号连续无缺号；有缺号则无法用 |
 * |                    |                                                                                   | 「区间」表达，脚本报错而不是猜。 |
 *
 * ── 定点替换的纪律（为什么不做全局数字替换）───────────────────────────────
 *
 * 文档里同一个数字可能同时是版本号 / 端口 / 日期 / 码值 / **历史引用的口号**
 * （例：`「57 张表 vs 62 tables」` 是引用一次历史矛盾的原文，改了就把证据毁了）。
 * 因此：
 *   1. 只有命中 RULES 里**带语境锚点**的正则的片段才参与替换（如 `N HTTP endpoints`、
 *      `N 张表`、`N 为…全量实测基线`），不做「把 62 全换掉」这种事；
 *   2. 每条规则声明 `expect[文件]=命中数`；命中数多于或少于声明时**直接失败**
 *      （退出码 3）—— 命中面缩小 = 措辞变了 / 规则失效，静默漏改是本项目踩过的坑；
 *   3. 明确豁免的语境（EXEMPT）打印「跳过」并说明理由，绝不静默吞掉；
 *   4. 替换前把每一处命中打印出来（文件:行 旧值 → 新值），`--write` 也先打印再写；
 *   5. 写回后立刻内部复检，残留差异 → 退出码 5；行尾统一 LF，写出含 CR 即报错。
 *
 * ── 用法 ──────────────────────────────────────────────────────────────────
 *
 *   node Implementation/back-end/scripts/sync-doc-counts.mjs --check        # 只比对，不一致退 1
 *   node Implementation/back-end/scripts/sync-doc-counts.mjs --write        # 定点回填并打印每处
 *   node ... --check --verbose            # 附带每模块测试数、未命中规则的同值数字清单
 *   node ... --list-tables                # 打印 Initialize.sql 逐表名 + 行号（核对建表口径）
 *   node ... --tests=2184                 # **仅在 surefire 报告缺失时**允许的显式传入
 *   node ... --check --strict-tests       # 测试数无法度量时也判失败（CI 已先跑 mvn test 时用）
 *
 * 退出码：0 一致 / 1 计数不一致（--check 期望） / 2 用法错误 / 3 覆盖漂移（规则命中面变化）
 *        4 度量失败（事实源缺文件或输出不可解析） / 5 写回后复检失败
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// 仓库根：脚本位于 <root>/Implementation/back-end/scripts/，故上溯 3 层（与 extract-endpoints.mjs 一致）
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const BE = path.join(ROOT, 'Implementation', 'back-end');
const SQL_FILE = path.join(BE, 'database', 'Initialize.sql');
const POM_FILE = path.join(BE, 'pom.xml');
const ADR_DIR = path.join(ROOT, 'project-docs', 'adr');
const ENDPOINT_SCRIPT = path.join(BE, 'scripts', 'extract-endpoints.mjs');

const DOCS = ['CLAUDE.md', 'AGENTS.md', path.join('project-docs', '00-README.md')];

/* ─────────────────────────── 0. CLI ─────────────────────────── */

const argv = process.argv.slice(2);
const has = (f) => argv.includes(f);
const opt = (name) => {
  const a = argv.find((x) => x.startsWith(`--${name}=`));
  return a === undefined ? null : a.slice(name.length + 3);
};

const MODE = has('--check') ? 'check' : has('--write') ? 'write' : null;
const VERBOSE = has('--verbose') || has('-v');
const LIST_TABLES = has('--list-tables');
const STRICT_TESTS = has('--strict-tests');
const TESTS_ARG = opt('tests');

if (!MODE && !LIST_TABLES) {
  console.error('用法: node sync-doc-counts.mjs --check | --write | --list-tables  [--verbose] [--tests=N] [--strict-tests]');
  process.exit(2);
}
if (MODE && LIST_TABLES) {
  console.error('--list-tables 是独立的核对命令，请与 --check / --write 分开执行。');
  process.exit(2);
}
if (TESTS_ARG !== null && !/^\d+$/.test(TESTS_ARG)) {
  console.error(`--tests=${TESTS_ARG} 非法：必须是整数。`);
  process.exit(2);
}

const die = (code, msg) => {
  console.error(`\n✗ ${msg}`);
  process.exit(code);
};

/* ─────────────────────── 1. 度量（只测量，不手填）─────────────────────── */

/** 通用递归收集文件（跳过 target 内的无关目录由调用方控制） */
function walkFiles(dir, hit, out = []) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (['node_modules', '.git', '.idea'].includes(e.name)) continue;
      walkFiles(p, hit, out);
    } else if (hit(p)) out.push(p);
  }
  return out;
}

/* ---- 1.1 端点数：复用 extract-endpoints.mjs（不重写解析逻辑） ---- */
function measureEndpoints() {
  if (!fs.existsSync(ENDPOINT_SCRIPT)) die(4, `端点真源脚本缺失：${ENDPOINT_SCRIPT}`);
  let out;
  try {
    out = execFileSync(process.execPath, [ENDPOINT_SCRIPT], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  } catch (e) {
    die(4, `extract-endpoints.mjs 执行失败：${e.message}`);
  }
  const a = out.indexOf('=== SUMMARY ===');
  const b = a < 0 ? -1 : out.indexOf('{', a);
  const c = b < 0 ? -1 : out.indexOf('\n=== ', b);
  if (a < 0 || b < 0) die(4, 'extract-endpoints.mjs 输出缺少 === SUMMARY === 段，端点度量失败');
  let summary;
  try {
    summary = JSON.parse(out.slice(b, c < 0 ? out.length : c));
  } catch (e) {
    die(4, `extract-endpoints.mjs 的 SUMMARY 不是合法 JSON：${e.message}`);
  }
  const code = summary.code || {};
  for (const k of ['distinct', 'internal', 'publicNonInternal']) {
    if (typeof code[k] !== 'number') die(4, `extract-endpoints.mjs SUMMARY.code.${k} 缺失，端点度量失败`);
  }
  return {
    total: code.distinct,
    public: code.publicNonInternal,
    internal: code.internal,
    // 契约口径：仅用于报告里标注「两个口径」，**不写入文档**
    contractDistinct: summary.doc && typeof summary.doc.distinct === 'number' ? summary.doc.distinct : null,
  };
}

/* ---- 1.2 测试数：surefire 报告聚合（无报告 → null，绝不猜） ---- */
function measureTests() {
  const reportsDir = (p) => path.basename(path.dirname(p)) === 'surefire-reports' && p.endsWith('.txt');
  const files = walkFiles(BE, reportsDir);
  const perModule = new Map();
  let total = 0;
  let failures = 0;
  let errors = 0;
  let skipped = 0;
  let unparsed = 0;
  let newest = 0;
  for (const f of files) {
    const txt = fs.readFileSync(f, 'utf8');
    const m = txt.match(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/);
    if (!m) {
      unparsed++;
      continue;
    }
    total += Number(m[1]);
    failures += Number(m[2]);
    errors += Number(m[3]);
    skipped += Number(m[4]);
    const mod = path.relative(BE, f).split(path.sep)[0];
    perModule.set(mod, (perModule.get(mod) || 0) + Number(m[1]));
    newest = Math.max(newest, fs.statSync(f).mtimeMs);
  }
  // 代码是否比报告更新 —— 报告可能过期（只告警，不改数字）
  let newestJava = 0;
  let newestJavaFile = '';
  for (const f of walkFiles(BE, (p) => p.endsWith('.java'))) {
    if (f.includes(`${path.sep}target${path.sep}`)) continue;
    const t = fs.statSync(f).mtimeMs;
    if (t > newestJava) {
      newestJava = t;
      newestJavaFile = f;
    }
  }
  return {
    reportCount: files.length,
    unparsed,
    total: files.length === 0 ? null : total,
    failures,
    errors,
    skipped,
    perModule: [...perModule.entries()].sort((x, y) => x[0].localeCompare(y[0])),
    newestReport: newest,
    newestJava,
    newestJavaFile,
  };
}

/* ---- 1.3 表数：Initialize.sql 的 CREATE TABLE（含缩进/IF NOT EXISTS 等变体）---- */
function measureTables() {
  if (!fs.existsSync(SQL_FILE)) die(4, `建表真源缺失：${SQL_FILE}`);
  const lines = fs.readFileSync(SQL_FILE, 'utf8').split(/\r?\n/);
  const tables = [];
  const commented = [];
  let inBlockComment = false;
  // 逐行匹配；若 `CREATE TABLE` 与表名不在同一行，向后最多看 2 行（稳妥，不猜跨越多行语句）
  const RE = /^\s*create\s+(?:or\s+replace\s+)?(?:temporary\s+)?table\s+(?:if\s+not\s+exists\s+)?[`"']?([A-Za-z0-9_$]+)[`"']?/i;
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];
    const l = raw.trim();
    if (inBlockComment) {
      if (l.includes('*/')) inBlockComment = false;
      if (/create\s+table/i.test(raw)) commented.push({ line: i + 1, text: l.slice(0, 120) });
      continue;
    }
    if (l.startsWith('/*')) {
      if (!l.includes('*/')) inBlockComment = true;
      if (/create\s+table/i.test(raw)) commented.push({ line: i + 1, text: l.slice(0, 120) });
      continue;
    }
    if (l.startsWith('--') || l.startsWith('#')) {
      if (/create\s+table/i.test(raw)) commented.push({ line: i + 1, text: l.slice(0, 120) });
      continue;
    }
    let m = raw.match(RE);
    if (!m && /^\s*create\s+(?:temporary\s+)?table\s*$/i.test(raw)) {
      for (let k = 1; k <= 2 && i + k < lines.length; k++) {
        m = (lines[i + k] || '').match(/^\s*[`"']?([A-Za-z0-9_$]+)[`"']?/);
        if (m) break;
      }
    }
    if (m) tables.push({ name: m[1], line: i + 1 });
  }
  const names = tables.map((t) => t.name);
  const dup = names.filter((n, i) => names.indexOf(n) !== i);
  return { count: tables.length, tables, duplicates: [...new Set(dup)], commented };
}

/* ---- 1.4 模块数：根 POM 的 <module> ---- */
function measureModules() {
  if (!fs.existsSync(POM_FILE)) die(4, `模块真源缺失：${POM_FILE}`);
  const xml = fs.readFileSync(POM_FILE, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const mods = [...xml.matchAll(/<module>\s*([^<]+?)\s*<\/module>/g)].map((m) => m[1]);
  return { count: mods.length, modules: mods };
}

/* ---- 1.5 ADR：project-docs/adr/0NN-*.md（排除 README）---- */
function measureAdr() {
  if (!fs.existsSync(ADR_DIR)) die(4, `ADR 目录缺失：${ADR_DIR}`);
  const nums = [];
  for (const f of fs.readdirSync(ADR_DIR)) {
    if (f.toLowerCase() === 'readme.md') continue;
    const m = f.match(/^(\d{3})-.+\.md$/i);
    if (m) nums.push(Number(m[1]));
  }
  nums.sort((a, b) => a - b);
  const max = nums.length ? nums[nums.length - 1] : 0;
  const gaps = [];
  for (let n = nums.length ? nums[0] : 0; n <= max; n++) if (!nums.includes(n)) gaps.push(n);
  return { count: nums.length, min: nums[0] || 0, max, gaps };
}

/* ─────────────────────── 2. 度量汇总 ─────────────────────── */

const ep = measureEndpoints();
const tests = measureTests();

// 测试数的取值规则：surefire 实测 > --tests=N（仅在无报告时允许）> null（不度量）
let testsValue = tests.total;
let testsSource;
if (tests.total !== null) {
  testsSource = `surefire 实测（${tests.reportCount} 份报告，failures=${tests.failures} errors=${tests.errors} skipped=${tests.skipped}）`;
  if (TESTS_ARG !== null && Number(TESTS_ARG) !== tests.total) {
    die(
      2,
      `拒绝执行：--tests=${TESTS_ARG} 与 surefire 实测 ${tests.total} 不一致。\n` +
        '  手填数字不得冒充实测。若确需覆盖，先说明报告为何不可信（这属于口径决策，不属于本脚本）。',
    );
  }
} else if (TESTS_ARG !== null) {
  testsValue = Number(TESTS_ARG);
  testsSource = `--tests=${TESTS_ARG}（无 surefire 报告，显式传入）`;
} else {
  testsValue = null;
  testsSource = '无法度量（未找到 surefire 报告；先跑 mvn -f Implementation/back-end/pom.xml test）';
}

const tablesM = measureTables();
const modulesM = measureModules();
const adrM = measureAdr();

if (adrM.gaps.length) {
  die(
    3,
    `ADR 编号不连续（缺号：${adrM.gaps.map((n) => String(n).padStart(3, '0')).join(', ')}）：` +
      '「001–NNN」的区间写法无法表达缺号，请先修 ADR 编号/索引再回填。',
  );
}

const VALUES = {
  endpoints_total: ep.total,
  endpoints_public: ep.public,
  endpoints_internal: ep.internal,
  tests_total: testsValue,
  tables: tablesM.count,
  modules: modulesM.count,
  adr_max: adrM.max,
};

/** 写回格式：adr_max 在文档里是三位补零（001–063），其余十进制原样 */
const NUMFMT = { adr_max: (v) => String(v).padStart(3, '0') };
const fmtVal = (metric, v) => (v === null || v === undefined ? '?' : (NUMFMT[metric] || String)(v));

const METRIC_LABEL = {
  endpoints_total: '端点总数（代码口径）',
  endpoints_public: '公开端点数',
  endpoints_internal: '/internal/** 端点数',
  tests_total: '测试数',
  tables: '建表数',
  modules: 'Maven 模块数',
  adr_max: 'ADR 最大编号（写 001–NNN）',
};

/* ─────────────────── 3. 定点替换规则（带语境锚点）─────────────────── */// groups：正则里扮演数字的捕获组（顺序与 metrics 对齐）；expect：各文件应有的命中数。
// 命中数不符 → 覆盖漂移，退出码 3（宁可报错，不静默漏改）。
const ALL3 = (a, b, c) => ({ 'CLAUDE.md': a, 'AGENTS.md': b, [path.join('project-docs', '00-README.md')]: c });

const RULES = [
  {
    id: 'tests-en',
    note: '`N tests` / 加粗 `**N tests**`',
    re: /(\d+)\s+tests\b/gd,
    metrics: ['tests_total'],
    expect: ALL3(3, 3, 0),
  },
  {
    id: 'tests-baseline-en',
    note: '`the YYYY-MM-DD baseline of **N** is authoritative`',
    re: /baseline of \*\*(\d+)\*\* is authoritative/gd,
    metrics: ['tests_total'],
    expect: ALL3(1, 0, 0),
  },
  {
    id: 'tests-baseline-cn',
    note: '`**N 为 YYYY-MM-DD 全量实测基线**`',
    re: /\*\*(\d+)\s*为\s*\d{4}-\d{2}-\d{2}\s*全量实测基线/gd,
    metrics: ['tests_total'],
    expect: ALL3(0, 1, 0),
  },
  {
    id: 'tests-cn',
    note: '`N 测试`',
    re: /(\d+)\s+测试/gd,
    metrics: ['tests_total'],
    expect: ALL3(0, 0, 1),
  },
  {
    id: 'modules-plain',
    note: '`N modules`（标题/正文里的模块数）',
    re: /(\d+)\s+modules\b/gd,
    metrics: ['modules'],
    expect: ALL3(2, 2, 0),
  },
  {
    id: 'modules-maven-en',
    note: '`N Maven modules`',
    re: /(\d+)\s+Maven modules/gd,
    metrics: ['modules'],
    expect: ALL3(2, 2, 0),
  },
  {
    id: 'modules-maven-cn',
    note: '`N Maven 模块`',
    re: /(\d+)\s+Maven 模块/gd,
    metrics: ['modules'],
    expect: ALL3(0, 0, 1),
  },
  {
    id: 'tables-en',
    note: '`N tables`',
    re: /(\d+)\s+tables\b/gd,
    metrics: ['tables'],
    expect: ALL3(3, 3, 0),
  },
  {
    id: 'tables-cn',
    note: '`N 张表`',
    re: /(\d+)\s*张表/gd,
    metrics: ['tables'],
    expect: ALL3(2, 2, 0),
  },
  {
    id: 'tables-schema-cn',
    note: '`表结构 N 张`',
    re: /表结构\s*(\d+)\s*张/gd,
    metrics: ['tables'],
    expect: ALL3(0, 0, 1),
  },
  {
    id: 'endpoints-en',
    note: '`N HTTP endpoints`',
    re: /(\d+)\s+HTTP endpoints/gd,
    metrics: ['endpoints_total'],
    expect: ALL3(1, 1, 0),
  },
  {
    id: 'endpoints-split',
    note: '`(N public + **M** `/internal/**`)` —— 一次替换两个数，保证 225+62=287 这类算术仍然成立',
    re: /\((\d+) public \+ \*\*(\d+)\*\* `\/internal\/\*\*`\)/gd,
    metrics: ['endpoints_public', 'endpoints_internal'],
    expect: ALL3(1, 1, 0),
  },
  {
    id: 'endpoints-cn',
    note: '`端点 N`',
    re: /端点\s*(\d+)/gd,
    metrics: ['endpoints_total'],
    expect: ALL3(0, 0, 1),
  },
  {
    id: 'endpoints-verify-cmd',
    note: '`extract-endpoints.mjs`（N）—— 可核验命令括号里的值',
    re: /extract-endpoints\.mjs`\s*（(\d+)）/gd,
    metrics: ['endpoints_total'],
    expect: ALL3(0, 0, 1),
  },
  {
    id: 'adr-range',
    note: '`001–NNN` ADR 区间右端',
    re: /(?<!\d)001\s*[–—]\s*(\d{3})/gd,
    metrics: ['adr_max'],
    expect: ALL3(2, 2, 0),
  },
];

// 「删断言」防线：每个指标在**全部文档**里至少要有这么多处命中。
// 光有逐规则的 expect 不够 —— 有人把整条 RULE 删掉，规则消失就不会再报差异。
// 因此再按指标设硬下限：命中数低于下限（例如删了某条规则）→ 退出码 3。
// 下限取「当前真实命中数」（只可上抬、不可下调；确需下调必须在提交说明里写清为什么）。
const MIN_HITS = {
  endpoints_total: 4,
  endpoints_public: 2,
  endpoints_internal: 2,
  tests_total: 9,
  tables: 11,
  modules: 9,
  adr_max: 4,
};

// 明确豁免：这些命中是「引用」而不是「计数声明」，改了就毁证据。
const EXEMPT = [
  {
    file: 'CLAUDE.md',
    rules: ['tables-cn', 'tables-en'],
    contextRe: /「57 张表 vs 62 tables」/,
    reason: '引用历史矛盾口号「57 张表 vs 62 tables」，是引用而非计数声明',
  },
  {
    file: 'AGENTS.md',
    rules: ['tables-cn', 'tables-en'],
    contextRe: /「57 张表 vs 62 tables」/,
    reason: '引用历史矛盾口号「57 张表 vs 62 tables」，是引用而非计数声明',
  },
];

/* ─────────────────────── 4. 扫描 + 判定 ─────────────────────── */

const pad = (n, w) => String(n).padStart(w, ' ');
const fmtTime = (ms) => (ms ? new Date(ms).toISOString().replace('T', ' ').slice(0, 16) + 'Z' : '—');

function scanDoc(rel) {
  const abs = path.join(ROOT, rel);
  if (!fs.existsSync(abs)) die(4, `文档缺失：${rel}`);
  const buf = fs.readFileSync(abs);
  const text = buf.toString('utf8');
  const hadCrlf = text.includes('\r\n');
  const lines = text.split(/\r?\n/);
  const hits = [];
  const perRule = new Map();

  for (const rule of RULES) {
    let count = 0;
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (!line) continue;
      const re = new RegExp(rule.re.source, rule.re.flags);
      let m;
      while ((m = re.exec(line)) !== null) {
        count++;
        const groups = rule.metrics.map((metric, gi) => {
          const g = gi + 1;
          // 注意：`d` 标志的 m.indices[g] 是**整行内的绝对位置**（不是相对 m[0]），
          // 所以这里**不能**再叠加 m.index —— 叠加会位移，把数字插进词中间。
          const [s, e] = m.indices[g];
          return { metric, text: m[g], old: Number(m[g]), start: s, end: e };
        });
        const exempt = EXEMPT.find((x) => x.file === rel && x.rules.includes(rule.id) && x.contextRe.test(line));
        const unmeasured = groups.some((g) => VALUES[g.metric] === null);
        const changed = groups.filter((g) => VALUES[g.metric] !== null && VALUES[g.metric] !== g.old);
        hits.push({
          file: rel,
          line: i + 1,
          rule: rule.id,
          note: rule.note,
          match: m[0],
          matchStart: m.index,
          matchEnd: m.index + m[0].length,
          groups,
          exempt: exempt ? exempt.reason : null,
          unmeasured,
          needsChange: !exempt && !unmeasured && changed.length > 0,
          oldText: groups.map((g) => g.text).join(' + '),
          newText: groups.map((g) => fmtVal(g.metric, VALUES[g.metric])).join(' + '),
        });
        if (m[0].length === 0) re.lastIndex++;
      }
    }
    perRule.set(rule.id, count);
  }
  // 覆盖自证：逐规则比对声明命中数
  const drift = [];
  for (const rule of RULES) {
    const actual = perRule.get(rule.id) || 0;
    const expected = rule.expect[rel] ?? 0;
    if (actual !== expected) drift.push({ rule: rule.id, note: rule.note, expected, actual });
  }
  // 指标覆盖自证：每个 metric 至少要有一条规则引用
  for (const rule of RULES) for (const mt of rule.metrics) if (!VALUES.hasOwnProperty(mt)) die(3, `规则 ${rule.id} 引用了未知指标 ${mt}`);
  return { rel, abs, text, lines, hadCrlf, hits, drift, trailingNewline: /\r?\n$/.test(text) };
}

const scans = DOCS.map(scanDoc);

/* ─────────────────────── 5. 输出 ─────────────────────── */

console.log('════════ sync-doc-counts ════════');
console.log(`mode: ${MODE || (LIST_TABLES ? 'list-tables' : '?')}   root: ${ROOT}`);
console.log('\n── 1. 度量（事实源 → 数值）──');
const rows = [
  ['endpoints_total', `${ep.total}`, 'extract-endpoints.mjs code.distinct（代码口径）'],
  ['endpoints_public', `${ep.public}`, 'extract-endpoints.mjs code.publicNonInternal'],
  ['endpoints_internal', `${ep.internal}`, 'extract-endpoints.mjs code.internal'],
  ['tests_total', testsValue === null ? '（无法度量）' : `${testsValue}`, testsSource],
  ['tables', `${tablesM.count}`, `Initialize.sql CREATE TABLE 语句（含缩进变体；注释内不计）`],
  ['modules', `${modulesM.count}`, '根 pom.xml <module>'],
  ['adr_max', `${String(adrM.max).padStart(3, '0')}`, `project-docs/adr/0NN-*.md（${adrM.count} 篇，排除 README）`],
];
for (const [k, v, src] of rows) console.log(`  ${k.padEnd(19)} = ${String(v).padEnd(12)} ← ${src}`);
if (ep.contractDistinct !== null) {
  console.log(`  ${'（契约口径）'.padEnd(17)} = ${String(ep.contractDistinct).padEnd(12)} ← 05-API-Specification.md 表格 distinct —— **与代码口径不同，仅报告，不写文档**`);
}
if (tablesM.duplicates.length) console.log(`  ⚠ 建表语句出现重名表：${tablesM.duplicates.join(', ')}`);
if (tablesM.commented.length) {
  console.log(`  ℹ 注释内的 CREATE TABLE（不计入）= ${tablesM.commented.length} 处：${tablesM.commented.map((c) => `L${c.line}`).join(', ')}`);
}
if (TESTS_ARG !== null && tests.total === null) console.log(`  ℹ 测试数来自命令行显式传入（无 surefire 报告）：--tests=${TESTS_ARG}`);
if (tests.total !== null && tests.unparsed) console.log(`  ⚠ ${tests.unparsed} 份 surefire 报告无法解析（未计入）`);
if (tests.total === null) {
  console.log('  ⚠ 测试数无法度量：不写入、不臆测。请先跑 mvn -f Implementation/back-end/pom.xml test，或用 --tests=N 显式传入。');
}
if (tests.total !== null && tests.newestJava > tests.newestReport) {
  console.log(
    `  ⚠ surefire 报告可能过期：最新报告 ${fmtTime(tests.newestReport)}，而源码 ${fmtTime(tests.newestJava)} 更新` +
      `（${path.relative(ROOT, tests.newestJavaFile)}）—— 建议先 mvn test 再回填。`,
  );
}
if (VERBOSE) {
  console.log('\n  [verbose] 每模块测试数（surefire 聚合）：');
  for (const [m, n] of tests.perModule) console.log(`    ${m.padEnd(24)} ${pad(n, 5)}`);
  console.log(`    ${'（合计）'.padEnd(24)} ${pad(tests.total ?? 0, 5)}`);
  console.log(`\n  [verbose] 模块清单（根 pom.xml）：${modulesM.modules.join(', ')}`);
}

if (LIST_TABLES) {
  console.log(`\n── Initialize.sql 建表清单（${tablesM.count} 张）──`);
  tablesM.tables.forEach((t, i) => console.log(`  ${pad(i + 1, 3)}. ${t.name.padEnd(28)} L${t.line}`));
  console.log(`\n合计 ${tablesM.count} 张。`);
  process.exit(0);
}

/* ── 2. 逐处命中判定 ── */
console.log('\n── 2. 逐处命中判定（文件:行  旧值 → 新值  判定）──');
let mismatch = 0;
let unmeasuredHits = 0;
for (const s of scans) {
  console.log(`\n  ${s.rel}${s.hadCrlf ? '   （当前为 CRLF，写回将归一为 LF）' : ''}`);
  if (!s.hits.length) console.log('    ✗ 该文档 0 命中 —— 规则与文档脱节，拒绝「静默通过」');
  for (const h of s.hits.sort((x, y) => x.line - y.line || x.matchStart - y.matchStart)) {
    let verdict;
    if (h.exempt) verdict = `跳过（豁免：${h.exempt}）`;
    else if (h.unmeasured) {
      verdict = '跳过（无法度量：保留原值，不臆测）';
      unmeasuredHits++;
    } else if (h.needsChange) {
      verdict = '改';
      mismatch++;
    } else verdict = '保持（已与实测一致）';
    console.log(
      `    ${(s.rel + ':' + h.line).padEnd(38)} ${h.oldText.padEnd(10)} → ${h.newText.padEnd(10)}  [${h.rule}] ${verdict}`,
    );
    if (h.needsChange || (VERBOSE && h.match)) console.log(`       匹配片段: ${JSON.stringify(h.match)}`);
  }
}

/* ── 3. 覆盖自证 ── */
console.log('\n── 3. 覆盖自证（规则声明命中数 vs 实际）──');
let driftTotal = 0;
for (const s of scans) {
  if (!s.drift.length) {
    console.log(`  ✓ ${s.rel}：${s.hits.length} 处命中，逐规则与声明一致`);
  } else {
    for (const d of s.drift) {
      driftTotal++;
      console.log(`  ✗ ${s.rel} 规则 ${d.rule}（${d.note}）：声明 ${d.expected} 处，实际 ${d.actual} 处`);
    }
  }
}
for (const mt of Object.keys(VALUES)) {
  if (!RULES.some((r) => r.metrics.includes(mt))) console.log(`  ✗ 指标 ${mt} 没有任何规则覆盖`);
}
// 指标级命中下限：防「整条规则被删掉 → 规则消失 → 差异不再上报」这种静默失守。
const metricHits = {};
for (const s of scans) for (const h of s.hits) for (const g of h.groups) metricHits[g.metric] = (metricHits[g.metric] || 0) + 1;
for (const mt of Object.keys(VALUES)) {
  const actual = metricHits[mt] || 0;
  const floor = MIN_HITS[mt] ?? 0;
  if (actual < floor) {
    driftTotal++;
    console.log(`  ✗ 指标 ${mt} 命中 ${actual} 处 < 下限 ${floor} 处（疑似删规则或措辞被改）`);
  } else {
    console.log(`  · 指标 ${mt}：命中 ${actual} 处（下限 ${floor}）`);
  }
}
if (driftTotal) {
  console.log(
    '\n  覆盖漂移的含义：文档措辞变了、或规则锚点失效、或有人新增了一处计数表述。\n' +
      '  这**不是**计数错误 —— 请复核 RULES 的 expect 与锚点后再决定是否调整（调整前先确认新命中该不该改）。',
  );
}

/* ── 4. 未纳入规则的同值数字（防「漏改」也防「误改」的证据清单）── */
if (VERBOSE) {
  const watch = new Set();
  for (const s of scans) for (const h of s.hits) for (const g of h.groups) watch.add(g.old);
  for (const v of Object.values(VALUES)) if (typeof v === 'number') watch.add(v);
  console.log('\n── 4. [verbose] 同值但未命中任何规则的数字（不会被本脚本修改）──');
  for (const s of scans) {
    const covered = new Set();
    for (const h of s.hits) for (let i = h.matchStart; i < h.matchEnd; i++) covered.add(`${h.line}:${i}`);
    const loose = [];
    s.lines.forEach((line, idx) => {
      const re = /(?<![\d\-–—.:/@])(\d+)(?![\d\-–—.:/%@])/g;
      let m;
      while ((m = re.exec(line)) !== null) {
        if (!watch.has(Number(m[1]))) continue;
        if (covered.has(`${idx + 1}:${m.index}`)) continue;
        loose.push(`L${idx + 1}:${m[1]}`);
      }
    });
    console.log(`  ${s.rel}: ${loose.length ? loose.join('  ') : '（无）'}`);
  }
  console.log('  （这些位置若是版本号/端口/日期/码值/历史引述，就应继续忽略；若其实是计数声明，请为其补一条带锚点的规则。）');
}

/* ── 5. 结论 / 写回 ── */
console.log('\n── 5. 结论 ──');
if (driftTotal) {
  console.log(`  覆盖漂移 ${driftTotal} 项、计数差异 ${mismatch} 处 —— 拒绝写回（先复核规则）。`);
  process.exit(3);
}

if (MODE === 'check') {
  if (unmeasuredHits) {
    console.log(`  测试数相关命中 ${unmeasuredHits} 处无法度量（保留原值，未参与比对）。`);
    if (STRICT_TESTS) {
      console.log('  ✗ --strict-tests：测试数无法度量即判失败。');
      process.exit(1);
    }
    console.log('  ⚠ 已跳过测试数比对（未加 --strict-tests）。这不是「通过」，是「未度量」。');
  }
  if (mismatch === 0) {
    console.log('  ✓ 文档计数与实测一致（0 处差异）');
    process.exit(0);
  }
  console.log(`  ✗ 发现 ${mismatch} 处计数与实测不一致：`);
  for (const s of scans)
    for (const h of s.hits.filter((x) => x.needsChange))
      console.log(`      ${s.rel}:${h.line}  ${h.oldText} → ${h.newText}   [${h.rule}]`);
  console.log('  修复命令（一条）：node Implementation/back-end/scripts/sync-doc-counts.mjs --write');
  process.exit(1);
}

/* ---- --write ----
 * 关键：**先按行汇总所有待替换的数字片段，再一次性从右往左切**。
 * 逐条命中各自替换会让同一行的第二个命中用上过期偏移 —— 这正是本脚本 v1 的事故
 * （1775→2184 长度变化后，同行后续命中把数字插到了词中间）。所以：
 *   1. 每行收集 spans（含所有命中、所有捕获组），按 start 降序一次性替换；
 *   2. 重叠即失败（宁可报错也不产出半对半错的行）；
 *   3. 替换后校验「去掉所有数字后文本不变」，从机制上排除误伤非数字字符；
 *   4. 全部文档在内存里算完并校验通过后才落盘，避免半个仓库被写坏。
 */
console.log(`  就地定点回填 ${mismatch} 处……`);
const pending = [];
for (const s of scans) {
  const changed = s.hits.filter((h) => h.needsChange);
  if (!changed.length) {
    console.log(`  = ${s.rel}: 无需改动`);
    continue;
  }
  const byLine = new Map();
  for (const h of changed) {
    if (!byLine.has(h.line)) byLine.set(h.line, []);
    byLine.get(h.line).push(h);
  }
  const lines = s.lines.slice();
  for (const ln of [...byLine.keys()].sort((a, b) => a - b)) {
    const orig = s.lines[ln - 1];
    const hits = byLine.get(ln);
    const spans = [];
    for (const h of hits) for (const g of h.groups) spans.push({ ...g, value: fmtVal(g.metric, VALUES[g.metric]) });
    spans.sort((a, b) => b.start - a.start);
    let rightBound = Infinity;
    let out = orig;
    for (const sp of spans) {
      if (sp.end > rightBound) die(5, `${s.rel}:${ln} 替换区间重叠（${JSON.stringify(sp)}），拒绝写入`);
      out = out.slice(0, sp.start) + sp.value + out.slice(sp.end);
      rightBound = sp.start;
    }
    if (out.replace(/\d/g, '') !== orig.replace(/\d/g, '')) {
      die(5, `${s.rel}:${ln} 定点替换改动了非数字内容，拒绝写入：\n    旧: ${orig}\n    新: ${out}`);
    }
    lines[ln - 1] = out;
    for (const h of hits) console.log(`  ✎ ${s.rel}:${h.line}  ${h.oldText} → ${h.newText}   [${h.rule}]`);
  }
  // split 会保留「结尾换行」产生的空尾元素，因此 join('\n') 本身就已忠实还原原文件；
  // 再无条件 += '\n' 会多出一个空行（v2 事故：行数 352→353）。只在确实缺失时补。
  let body = lines.join('\n');
  if (s.trailingNewline && !body.endsWith('\n')) body += '\n';
  if (body.includes('\r')) die(5, `${s.rel} 写回内容含 CR，拒绝写入（必须 LF）`);
  pending.push({ s, body });
}
for (const { s, body } of pending) fs.writeFileSync(s.abs, body, 'utf8');

/* 写回后内部复检：残留差异 / CRLF / 命中面变化 → 一律失败 */
let residual = 0;
for (const s of scans) {
  const after = scanDoc(s.rel);
  if (after.hadCrlf) die(5, `${s.rel} 写回后仍为 CRLF`);
  if (after.drift.length) die(5, `${s.rel} 写回后覆盖漂移：${after.drift.map((d) => d.rule).join(', ')}`);
  if (after.lines.length !== s.lines.length) die(5, `${s.rel} 行数变化（${s.lines.length} → ${after.lines.length}），写回越界`);
  const left = after.hits.filter((h) => h.needsChange);
  residual += left.length;
  for (const h of left) console.log(`  ✗ 残留差异 ${s.rel}:${h.line} ${h.oldText} → ${h.newText}`);
}
if (residual) die(5, `写回后仍有 ${residual} 处差异`);
console.log('  ✓ 写回完成，内部复检：0 处残留差异，行尾 LF，命中面不变');
process.exit(0);

#!/usr/bin/env node
/**
 * 只读静态守卫：查询参数「线上名」三方一致性
 *
 *   后端**实际绑定方式** × 契约 Query 列 × 前端实际发出的参数名
 *
 * ── 为什么需要它（2026-10-15 线上活缺陷）─────────────────────────────
 * 后端 `SpuPageReq.categoryId` / `SearchPageReq.categoryId` / `ReviewFilterReq.hasMedia`
 * 经 **`@QueryModel`** 交给 Jackson 反序列化（全局 `property-naming-strategy: SNAKE_CASE`），
 * 线上名因此是 `category_id` / `has_media`；而前端发的是 camelCase。
 * Spring Boot 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false` → 未知参数名**静默忽略**
 * → 字段恒 null → 品类/带图筛选整体失效且**不报错**。
 *
 * 既有守卫都覆盖不到这一类：`architecture-tests/WireNamingConventionTest` 只查
 * `@RequestParam`（`@QueryModel` 不在其扫描面）；`check-frontend-api-paths.mjs` /
 * `crosscheck-api-paths.mjs` 只比对**路径**、不看参数名。本脚本补上这一层。
 *
 * ── 判据（“POJO 绑定”必须分两类，不可混为一谈）────────────────────
 *   ① `@QueryModel XxxReq req` → Jackson + 全局 SNAKE_CASE → 线上名 = snake_case(字段名)
 *   ② 裸 POJO `XxxReq req`（含 `@Valid XxxReq req`）= 隐式 `@ModelAttribute`
 *      → Spring `WebDataBinder`/`BeanWrapper` 按 **Java 属性名**取值 → 线上名 = camelCase
 *      （Jackson 命名策略**管不到**它 —— 这正是 `@QueryModel` 被引入的原因）
 * 两类都按「后端实际绑定」推导线上名，不照抄契约、不照抄前端。
 *
 * ── 断言 ────────────────────────────────────────────────────────
 *   硬断言（失败 → exit 1）
 *     A1 每个 `@QueryModel` 绑定的 Req 类：每个字段的 snake_case 线上名 ∈ 契约 Query 列
 *     A2 每个 `@QueryModel` 绑定的端点：契约声明的每个 Query 名 ∈ 后端可绑定名（反向）
 *     A3 每个 POJO 绑定端点：前端发出的每个参数名 ∈ 后端可绑定名（抓上面那个缺陷）
 *     A4 使用 `@QueryModel` 的服务：`application.yml` 必须声明 SNAKE_CASE（snake 结论的证据）
 *   报告项（默认只报不失败；`--strict` 下升级为失败）
 *     A5 裸 POJO（camelCase 线上名）端点上「契约写 snake、后端只能绑 camel」的既有不一致
 *        —— 属**后端/契约侧**的修复范围，前端无法就地兜底，故默认不阻塞前端 CI
 *   范围外报告（不影响退出码）
 *     A6 `apps/miniprogram/**`（跨平台同一份契约，本仓当前任务不含小程序改动）命中明细
 *
 * 用法: node scripts/check-query-param-naming.mjs [--strict] [--contract <path>]
 * 路径按脚本位置推导，**与 cwd 无关**（`--contract` 是给「拿契约副本做负向测试」用的显式覆盖）。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const FE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const REPO_ROOT = resolve(FE_ROOT, '..', '..');
const BACKEND_ROOT = resolve(REPO_ROOT, 'Implementation', 'back-end');
const STRICT = process.argv.includes('--strict');
const contractArg = process.argv.indexOf('--contract');
const CONTRACT = contractArg >= 0 && process.argv[contractArg + 1]
  ? resolve(process.argv[contractArg + 1])
  : resolve(REPO_ROOT, 'project-docs', 'contract', '05-API-Specification.md');

const BACKEND_SKIP = new Set(['node_modules', 'target', 'test', 'dist', '.git', '.idea', '.mvn']);
const FE_SKIP = new Set(['node_modules', '.next', 'dist', '.turbo', '.git', '.idea']);

const BINDABLE_ANNOS = ['RequestParam', 'PathVariable', 'RequestBody', 'RequestHeader',
  'CookieValue', 'RequestPart', 'RequestAttribute', 'SessionAttribute', 'MatrixVariable'];

/* ══════════════════════════ 通用工具 ══════════════════════════ */

function walk(dir, exts, skip, out = []) {
  let names;
  try { names = readdirSync(dir); } catch { return out; }
  for (const n of names) {
    if (skip.has(n)) continue;
    const p = join(dir, n);
    let st;
    try { st = statSync(p); } catch { continue; }
    if (st.isDirectory()) walk(p, exts, skip, out);
    else if (exts.some((e) => n.endsWith(e))) out.push(p);
  }
  return out;
}

/** 剥掉注释（**保留换行与列位置**，便于报行号；不影响字符串内容）。 */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:'"`\\])\/\/[^\n]*/g, (m, p1) => p1 + ' '.repeat(m.length - p1.length));
}

/** 从 openIndex 处的 '(' 找配对 ')'，返回 ')' 之后的下标。 */
function matchingParen(text, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < text.length; i++) {
    const c = text[i];
    if (c === '"' || c === "'" || c === '`') {
      const quote = c;
      i++;
      while (i < text.length && text[i] !== quote) {
        if (text[i] === '\\') i++;
        i++;
      }
    } else if (c === '(') depth++;
    else if (c === ')') { depth--; if (depth === 0) return i + 1; }
  }
  return text.length;
}

/** 顶层切分（忽略括号/尖括号/引号内的分隔符）。 */
function splitTop(s, sep = ',') {
  const out = [];
  let depth = 0, quote = null, cur = '';
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (quote) {
      cur += c;
      if (c === '\\') { cur += s[++i] ?? ''; continue; }
      if (c === quote) quote = null;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') { quote = c; cur += c; continue; }
    if (c === '(' || c === '[' || c === '{' || c === '<') depth++;
    if (c === ')' || c === ']' || c === '}' || c === '>') depth--;
    if (c === sep && depth === 0) { out.push(cur); cur = ''; continue; }
    cur += c;
  }
  out.push(cur);
  return out;
}

const toSnake = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const normPath = (p) => p.replace(/\$\{[^}]*\}/g, '{v}').replace(/\{[^}]+\}/g, '{v}').replace(/\/+$/, '');
const lineOf = (text, index) => text.slice(0, index).split('\n').length;
const pad = (s, n) => (s.length >= n ? s : s + ' '.repeat(n - s.length));

/* ══════════════════════════ ① 后端：类索引 + Req 字段 ══════════════════════════ */

const javaFiles = walk(BACKEND_ROOT, ['.java'], BACKEND_SKIP);
const classIndex = new Map(); // 简单类名 -> 文件（同名优先 adapter/ 下的那个）
for (const f of javaFiles) {
  const src = readFileSync(f, 'utf8');
  for (const m of src.matchAll(/\b(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+)?(?:class|interface|enum|record)\s+([A-Za-z_]\w*)/g)) {
    const name = m[1];
    const prev = classIndex.get(name);
    if (!prev || (!prev.includes(`${sep}adapter${sep}`) && f.includes(`${sep}adapter${sep}`))) classIndex.set(name, f);
  }
}
if (javaFiles.length === 0) {
  console.error(`后端源码不可达：${BACKEND_ROOT}`);
  process.exit(2);
}

/** 一个类里的字段（支持 `@JsonProperty("x")` 显式钉名）。 */
function parseFields(file) {
  const text = stripComments(readFileSync(file, 'utf8'));
  const FIELD_RE = /^\s+(?:private|protected|public)\s+([\w$.<>,\[\]\s]+?)\s+(\w+)\s*(?:=[^;]*)?;\s*$/;
  const fields = [];
  let pendingJson = null;
  for (const line of text.split('\n')) {
    const jm = line.match(/@JsonProperty\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/);
    if (jm) { pendingJson = jm[1]; continue; }
    if (/^\s*@/.test(line)) continue; // 其它注解不重置
    const fm = line.match(FIELD_RE);
    if (fm) {
      if (/\b(?:static|final)\b/.test(fm[1])) { pendingJson = null; continue; }
      fields.push({ java: fm[2], json: pendingJson });
      pendingJson = null;
      continue;
    }
    if (line.trim() !== '') pendingJson = null;
  }
  return fields;
}

const fieldCache = new Map();
function fieldsOf(type) {
  if (!fieldCache.has(type)) fieldCache.set(type, parseFields(classIndex.get(type)));
  return fieldCache.get(type);
}

/** 线上名：Jackson（@QueryModel）→ snake_case；WebDataBinder → Java 属性名；@JsonProperty 覆盖两者。 */
function wireNames(type, binding) {
  return fieldsOf(type).map((f) => (f.json ?? (binding === 'query-model' ? toSnake(f.java) : f.java)));
}

/* ══════════════════════════ ② 后端：控制器端点解析 ══════════════════════════ */

/** 拆出「注解列表」与「其余签名文本」。 */
function decomposeParam(p) {
  const anns = [];
  let rest = '';
  let i = 0;
  while (i < p.length) {
    if (p[i] === '@') {
      i++;
      const start = i;
      while (i < p.length && /[\w$.]/.test(p[i])) i++;
      const name = p.slice(start, i);
      let j = i;
      while (j < p.length && /\s/.test(p[j])) j++;
      let args = null;
      if (p[j] === '(') { const end = matchingParen(p, j); args = p.slice(j + 1, end - 1); i = end; } else i = j;
      anns.push({ name, args });
    } else rest += p[i++];
  }
  return { anns, rest: rest.trim() };
}

/** 从注解实参取显式线上名：位置式 `"x"` 或 `name = "x"` / `value = "x"`。 */
function explicitName(args) {
  if (!args) return null;
  const positional = args.match(/^\s*["']([^"']+)["']/);
  if (positional) return positional[1];
  const named = args.match(/\b(?:name|value)\s*=\s*["']([^"']+)["']/);
  return named ? named[1] : null;
}

/** 形参 → 绑定信息；非「查询参数载体」返回 null。 */
function classifyParam(raw) {
  const { anns, rest } = decomposeParam(raw);
  const parts = rest.split(/\s+/).filter(Boolean);
  if (parts.length < 2) return null;
  const name = parts[parts.length - 1].replace(/\[\]$/, '').replace(/\.\.\.$/, '');
  if (!/^[A-Za-z_]\w*$/.test(name)) return null;
  const type = parts[parts.length - 2].replace(/<.*$/, '').split('.').pop();

  const anno = (n) => anns.find((a) => a.name === n);
  if (anno('RequestParam')) {
    return { kind: 'request-param', wire: explicitName(anno('RequestParam').args) ?? name, java: name };
  }
  if (anns.some((a) => BINDABLE_ANNOS.includes(a.name))) return null; // body / path / header …
  if (!classIndex.has(type)) return null; // HttpServletRequest / Principal …（JDK 类型，无源码）
  const binding = anno('QueryModel') ? 'query-model' : 'model-attribute';
  return { kind: 'pojo', type, java: name, binding };
}

/** 跳到方法形参表：先跳过后续注解，再取第一个 '(' … ')'。 */
function findMethodParams(text, from) {
  let i = from;
  for (;;) {
    while (i < text.length && /\s/.test(text[i])) i++;
    if (text[i] !== '@') break;
    i++;
    while (i < text.length && /[\w$.]/.test(text[i])) i++;
    let j = i;
    while (j < text.length && /\s/.test(text[j])) j++;
    i = text[j] === '(' ? matchingParen(text, j) : j;
  }
  const open = text.indexOf('(', i);
  if (open < 0) return null;
  const semi = text.indexOf(';', i);
  if (semi >= 0 && semi < open) return null; // 已跨出该方法（防御）
  const close = matchingParen(text, open);
  return { raw: text.slice(open + 1, close - 1), end: close };
}

const endpoints = [];
for (const f of javaFiles) {
  const raw = readFileSync(f, 'utf8');
  if (!raw.includes('@RestController')) continue;
  const text = stripComments(raw);
  const baseM = text.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?(?:\{[^}]*\}\s*,\s*)?["']([^"']+)["']/);
  const base = baseM ? baseM[1] : '';
  const module = relative(BACKEND_ROOT, f).split(sep)[0];
  const annRe = /@(Get|Post|Put|Patch|Delete)Mapping\b/g;
  let m;
  while ((m = annRe.exec(text)) !== null) {
    const verb = m[1].toUpperCase();
    let cursor = m.index + m[0].length;
    let sub = '';
    let c = cursor;
    while (c < text.length && /\s/.test(text[c])) c++;
    if (text[c] === '(') {
      const end = matchingParen(text, c);
      const pm = text.slice(c + 1, end - 1).match(/["']([^"']*)["']/);
      sub = pm ? pm[1] : '';
      cursor = end;
    }
    const sig = findMethodParams(text, cursor);
    if (!sig) continue;
    const params = splitTop(sig.raw, ',').map(classifyParam).filter(Boolean);
    const path = (base + sub).replace(/\/+/g, '/').replace(/\/$/, '') || '/';
    endpoints.push({
      file: relative(REPO_ROOT, f).split(sep).join('/'),
      module, verb, path,
      norm: normPath(path),
      line: lineOf(text, m.index),
      params,
      pojos: params.filter((p) => p.kind === 'pojo'),
      bindable: new Set(params.flatMap((p) => (p.kind === 'pojo' ? wireNames(p.type, p.binding) : [p.wire]))),
    });
  }
}

const pojoEndpoints = endpoints.filter((e) => e.pojos.length > 0);

/* ══════════════════════════ ③ 契约：Query 列 ══════════════════════════ */

function parseQueryNames(segment) {
  const out = [];
  for (const token of splitTop(segment, ',')) {
    const t = token.trim();
    if (!t) continue;
    let nm = null;
    const bt = t.match(/^`([^`]+)`/);
    if (bt) nm = bt[1];
    else {
      const w = t.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*\??/);
      if (w) nm = w[1];
    }
    if (!nm) continue;
    nm = nm.replace(/\?$/, '').trim();
    if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(nm) && !out.includes(nm)) out.push(nm);
  }
  return out;
}

const contractRows = [];
let contractReadable = true;
try {
  readFileSync(CONTRACT, 'utf8').split('\n').forEach((line, idx) => {
    if (!line.trimStart().startsWith('|')) return;
    const cells = line.split(/(?<!\\)\|/).map((c) => c.trim());
    const qi = cells.findIndex((c) => c.includes('Query:'));
    if (qi < 0) return;
    const pathCell = cells.find((c) => /^`?\/api\//.test(c));
    if (!pathCell) return;
    const pm = pathCell.match(/(\/api\/[A-Za-z0-9_/{}$.\-?=&]*)/);
    if (!pm) return;
    const path = pm[1].split('?')[0].replace(/\/+$/, '');
    contractRows.push({
      id: cells[1],
      verb: cells[2],
      path,
      norm: normPath(path),
      names: parseQueryNames(cells[qi].slice(cells[qi].indexOf('Query:') + 6)),
      line: idx + 1,
    });
  });
} catch {
  contractReadable = false;
}
const contractByNorm = new Map(); // norm -> rows[]
for (const r of contractRows) {
  if (!contractByNorm.has(r.norm)) contractByNorm.set(r.norm, []);
  contractByNorm.get(r.norm).push(r);
}

/* ══════════════════════════ ④ 前端：实际发出的参数名 ══════════════════════════ */

/**
 * 取下标所在的**最内层** `{ ... }` 块范围（模板串里的 `${}` 成对，不影响计数）；
 * 若下标不在任何块内（模块顶层）→ 返回整个文件。
 */
function enclosingBlock(text, index) {
  const stack = [];
  for (let i = 0; i < index; i++) {
    if (text[i] === '{') stack.push(i);
    else if (text[i] === '}') stack.pop();
  }
  if (stack.length === 0) return { start: 0, end: text.length };
  const start = stack[stack.length - 1];
  let depth = 0;
  for (let i = start; i < text.length; i++) {
    if (text[i] === '{') depth++;
    else if (text[i] === '}') { depth--; if (depth === 0) return { start, end: i }; }
  }
  return { start, end: text.length };
}

const PATH_RE = /\/api\/[A-Za-z0-9_/{}$.?=&%-]*/g;

/** 扫一个前端目录，返回调用点：{ file, line, norm, keys:[{key,how}] } */
function scanFrontend(dir) {
  const sites = [];
  for (const f of walk(join(FE_ROOT, dir), ['.ts', '.tsx'], FE_SKIP)) {
    const text = stripComments(readFileSync(f, 'utf8'));
    for (const m of text.matchAll(PATH_RE)) {
      const literal = m[0];
      const qIdx = literal.indexOf('?');
      const pathPart = qIdx >= 0 ? literal.slice(0, qIdx) : literal;
      if (!/\/api\/[a-z]/i.test(pathPart) || pathPart === '/api') continue;
      const queryExpr = qIdx >= 0 ? literal.slice(qIdx) : '';
      const keys = [];
      for (const km of queryExpr.matchAll(/[?&]([A-Za-z_][A-Za-z0-9_]*)=/g)) keys.push({ key: km[1], how: '内联' });
      for (const vm of queryExpr.matchAll(/\$\{\s*([A-Za-z_$][\w$]*)/g)) {
        const v = vm[1];
        const declRe = new RegExp(`(?:const|let|var)\\s+${escapeRe(v)}\\s*=\\s*new\\s+URLSearchParams\\s*\\(`, 'g');
        let dm;
        while ((dm = declRe.exec(text)) !== null) {
          const open = text.indexOf('(', dm.index + dm[0].length - 1);
          const close = matchingParen(text, open);
          const args = text.slice(open + 1, close - 1).trim();
          if (args.startsWith('{')) {
            const inner = args.slice(1, args.lastIndexOf('}'));
            for (const part of splitTop(inner, ',')) {
              const t = part.trim();
              if (!t) continue;
              const colon = t.indexOf(':');
              const k = (colon >= 0 ? t.slice(0, colon) : t).trim();
              if (/^[A-Za-z_$][\w$]*$/.test(k)) keys.push({ key: k, how: `URLSearchParams(${v})` });
            }
          }
          const scope = enclosingBlock(text, dm.index);
          const region = text.slice(scope.start, scope.end);
          const setRe = new RegExp(`\\b${escapeRe(v)}\\.(?:set|append)\\(\\s*["']([^"']+)["']`, 'g');
          let sm;
          let anySet = false;
          while ((sm = setRe.exec(region)) !== null) { anySet = true; keys.push({ key: sm[1], how: `${v}.set()` }); }
          if (!anySet && !args.startsWith('{')) {
            keys.push({ key: null, how: `未解析的 ${v}（${pathPart}）` });
          }
        }
      }
      sites.push({
        file: relative(REPO_ROOT, f).split(sep).join('/'),
        line: lineOf(text, m.index),
        raw: literal,
        norm: normPath(pathPart),
        keys,
      });
    }
  }
  return sites;
}

/** Web 侧调用点扫描面：`packages/**` + `apps/` 下除小程序外的全部应用（自动发现，新增 Web app 自动纳入）。 */
const WEB_APP_DIRS = (() => {
  try {
    return readdirSync(join(FE_ROOT, 'apps'))
      .filter((n) => n !== 'miniprogram')
      .filter((n) => { try { return statSync(join(FE_ROOT, 'apps', n)).isDirectory(); } catch { return false; } })
      .map((n) => join('apps', n));
  } catch { return []; }
})();
const feSites = WEB_APP_DIRS.flatMap((d) => scanFrontend(d)).concat(scanFrontend('packages'));
const miniSites = scanFrontend(join('apps', 'miniprogram'));

/* ══════════════════════════ ⑤ 断言执行 ══════════════════════════ */

let hardFail = 0;
let reportOnly = 0;
const say = (s = '') => console.log(s);

/* —— [1] 后端 POJO 绑定端点清单 —— */
say('=== [1] 后端 POJO 绑定的查询参数载体（按源码推导线上名）===');
for (const e of pojoEndpoints) {
  for (const p of e.pojos) {
    const kind = p.binding === 'query-model' ? '@QueryModel→Jackson(snake_case)' : '裸POJO→WebDataBinder(Java 属性名)';
    say(`  ${pad(`${e.verb} ${e.path}`, 62)} ${pad(p.type, 22)} ${kind}`);
    say(`      ${pad('', 62)} 线上名: ${wireNames(p.type, p.binding).join(', ')}`);
  }
}
say(`  合计 POJO 载体端点: ${pojoEndpoints.length}`);

/* —— A4：命名策略证据 —— */
say('\n=== [2] A4 证据 — 使用 @QueryModel 的服务必须声明 SNAKE_CASE ===');
const qmModules = [...new Set(pojoEndpoints.filter((e) => e.pojos.some((p) => p.binding === 'query-model')).map((e) => e.module))];
for (const mod of qmModules) {
  const yml = join(BACKEND_ROOT, mod, 'src', 'main', 'resources', 'application.yml');
  let ok = false;
  let seen = '(读取失败)';
  try {
    const t = readFileSync(yml, 'utf8');
    const m = t.match(/property-naming-strategy\s*:\s*([A-Za-z_]+)/);
    seen = m ? m[1] : '(未声明)';
    ok = seen === 'SNAKE_CASE';
  } catch { /* 保持 ok=false */ }
  if (!ok) hardFail++;
  say(`  ${ok ? 'PASS' : 'FAIL'}  ${pad(mod, 20)} property-naming-strategy: ${seen}`);
}

/* —— A1 / A2：@QueryModel 载体 × 契约 —— */
say('\n=== [3] A1/A2 — @QueryModel 载体字段(snake_case) × 契约 Query 列 ===');
const qmClasses = new Map(); // type -> { modules, endpoints:[], wires:Set }
for (const e of pojoEndpoints) {
  for (const p of e.pojos) {
    if (p.binding !== 'query-model') continue;
    if (!qmClasses.has(p.type)) qmClasses.set(p.type, { endpoints: [], wires: new Set() });
    const rec = qmClasses.get(p.type);
    rec.endpoints.push(e);
    for (const w of wireNames(p.type, p.binding)) rec.wires.add(w);
  }
}
for (const [type, rec] of qmClasses) {
  const contractNames = new Set();
  let matchedRows = 0;
  for (const e of rec.endpoints) {
    const rows = contractByNorm.get(e.norm) ?? [];
    if (rows.length) matchedRows++;
    for (const r of rows) for (const n of r.names) contractNames.add(n);
    // A2：本端点契约声明的每个名必须可绑定
    for (const r of rows) {
      for (const n of r.names) {
        if (!rec.wires.has(n)) {
          hardFail++;
          say(`  FAIL  ${e.verb} ${e.path}（${r.id}）契约声明 \`${n}\`，但 ${type} 的线上名里没有`
            + `（实际可绑定: ${[...rec.wires].join(', ')}）`);
        }
      }
    }
  }
  if (matchedRows < rec.endpoints.length) {
    say(`  WARN  ${type}: ${rec.endpoints.length - matchedRows} 个端点未在契约里匹配到 Query 行`
      + `（契约路径与 @RequestMapping 拼接结果不一致？）`);
  }
  const missing = [...rec.wires].filter((w) => !contractNames.has(w));
  // 「前兆」= 契约里出现的、与该 snake 线上名同形的 camelCase 写法（仅对多词名有意义）
  const camelButSnake = [...rec.wires]
    .filter((w) => w.includes('_') && contractNames.has(w.replace(/_([a-z])/g, (_, c) => c.toUpperCase())));
  const ok = missing.length === 0;
  if (!ok) hardFail++;
  say(`  ${ok ? 'PASS' : 'FAIL'}  ${pad(type, 22)} 字段线上名 ${rec.wires.size} 个 ∈ 契约 Query 列`
    + `（契约名 ${contractNames.size} 个）${ok ? '' : `；契约缺: ${missing.join(', ')}`}`);
  if (camelButSnake.length) {
    say(`        提示：契约里出现的 camelCase 同形名（正是本类缺陷的前兆）: ${camelButSnake.join(', ')}`);
  }
  for (const e of rec.endpoints) {
    const rows = contractByNorm.get(e.norm) ?? [];
    const declared = new Set(rows.flatMap((r) => r.names));
    const notDeclared = [...rec.wires].filter((w) => !declared.has(w));
    say(`        · ${pad(`${e.verb} ${e.path}`, 56)} ${rows.length ? `契约行 ${rows.map((r) => r.id).join('/')}` : '契约无 Query 行'}`
      + (notDeclared.length ? `；该端点未声明 ${notDeclared.join(', ')}（复用类余下字段，非必错）` : ''));
  }
}

/* —— A3：前端发送名 × 后端可绑定名 —— */
say('\n=== [4] A3 — 前端实际发出的参数名 × 后端可绑定名（POJO 端点）===');
const byNorm = new Map();
for (const e of endpoints) {
  if (!byNorm.has(e.norm)) byNorm.set(e.norm, []);
  byNorm.get(e.norm).push(e);
}
let feChecked = 0;
function checkSites(sites, { reportOnlyMode = false } = {}) {
  const lines = [];
  for (const s of sites) {
    const eps = byNorm.get(s.norm);
    if (!eps) continue; // 路径本身不存在 → 由 crosscheck-api-paths.mjs 负责
    const pojoEps = eps.filter((e) => e.pojos.length > 0);
    if (pojoEps.length === 0) continue;
    if (!reportOnlyMode) feChecked++;
    const bindable = new Set(pojoEps.flatMap((e) => [...e.bindable]));
    const keyList = s.keys.filter((k) => k.key);
    for (const k of keyList) {
      if (bindable.has(k.key)) continue;
      const snake = toSnake(k.key);
      const hint = bindable.has(snake) ? `；后端线上名为 \`${snake}\`` : '';
      const msg = `参数 \`${k.key}\` (${k.how}) 后端无法绑定${hint}`;
      if (reportOnlyMode) lines.push(`  WARN  ${s.file}:${s.line}  ${msg}`);
      else { hardFail++; lines.push(`  FAIL  ${s.file}:${s.line}  ${msg}`); }
    }
    if (keyList.length === 0 && s.keys.some((k) => !k.key)) {
      lines.push(`  WARN  ${s.file}:${s.line}  ${s.raw} — 查询串变量未解析，无法核对`);
    }
    if (!reportOnlyMode) {
      const shown = keyList.length
        ? keyList.map((k) => k.key).filter((v, i, a) => a.indexOf(v) === i).join(', ')
        : '(无显式参数名)';
      lines.push(`  SITE  ${pad(s.norm, 48)} <- ${s.file}:${s.line}  [${shown}]`);
    }
  }
  return lines;
}
for (const l of checkSites(feSites)) say(l);
{
  const unresolved = feSites.filter((s) => byNorm.has(s.norm)
    && byNorm.get(s.norm).some((e) => e.pojos.length > 0) && s.keys.filter((k) => k.key).length === 0);
  for (const s of unresolved) say(`  INFO  ${s.file}:${s.line}  ${s.raw} — 无显式参数名（纯字面/未解析），已跳过`);
}
say(`  已核对前端调用点（Web：packages + apps/{${WEB_APP_DIRS.map((d) => d.split(sep).pop()).join(',')}}，命中 POJO 端点）: ${feChecked}`);

/* —— A5：裸 POJO 端点的「契约 snake ↔ 后端 camel」既有不一致（报告项）—— */
say('\n=== [5] A5 — 裸 POJO（WebDataBinder/camelCase）端点的契约↔后端不一致【需后端/契约侧处理】===');
const seenGap = new Set();
for (const e of pojoEndpoints) {
  const bare = e.pojos.filter((p) => p.binding === 'model-attribute');
  if (!bare.length) continue;
  const rows = contractByNorm.get(e.norm) ?? [];
  const declared = new Set(rows.flatMap((r) => r.names));
  const camelWires = new Set(bare.flatMap((p) => wireNames(p.type, p.binding)));
  const contractUnbindable = [...declared].filter((n) => !camelWires.has(n));
  const wireUndocumented = [...camelWires].filter((n) => !declared.has(n));
  for (const n of contractUnbindable) {
    const key = `${e.norm}|${n}`;
    if (seenGap.has(key)) continue;
    seenGap.add(key);
    reportOnly++;
    const snakePartner = [...camelWires].find((w) => toSnake(w) === n);
    say(`  GAP   ${e.verb} ${e.path}${rows.length ? `（${rows.map((r) => r.id).join('/')}）` : ''}`
      + ` 契约声明 \`${n}\`，后端只能绑 ${[...camelWires].join(', ')}`
      + (snakePartner ? `  → 契约写法会静默失效（后端读 \`${snakePartner}\`）` : '  → 后端无此参数'));
  }
  for (const n of wireUndocumented) {
    const key = `${e.norm}|undoc|${n}`;
    if (seenGap.has(key)) continue;
    seenGap.add(key);
    say(`  INFO  ${e.verb} ${e.path} 后端可绑 \`${n}\` 但契约 Query 列未声明`);
  }
}
if (reportOnly === 0) say('  （无）');
say(`  报告项合计: ${reportOnly}${STRICT ? '（--strict：计入失败）' : '（默认不阻塞，修后端/契约后用 --strict 收紧）'}`);

/* —— A6：范围外（小程序）—— */
say('\n=== [6] A6 — 范围外报告：apps/miniprogram（本任务不改，仅报告同一类命中）===');
const miniLines = checkSites(miniSites, { reportOnlyMode: true });
if (miniLines.length === 0) say('  （无命中）'); else for (const l of miniLines) say(l);

/* ══════════════════════════ 汇总 ══════════════════════════ */
say('\n=== 汇总 ===');
say(`  后端 POJO 载体端点: ${pojoEndpoints.length} | 扫描 java: ${javaFiles.length}`
  + ` | 前端 Web 调用点(命中 POJO): ${feChecked}`);
say(`  契约: ${contractReadable ? `${contractRows.length} 行含 Query 列（${CONTRACT.replace(REPO_ROOT + sep, '').split(sep).join('/')}）` : `不可读: ${CONTRACT}`}`);
say(`  硬断言失败(A1-A4): ${hardFail}`);
say(`  需后端/契约侧处理(A5): ${reportOnly}`);
const failed = hardFail > 0 || (STRICT && reportOnly > 0);
say(failed ? '  结果: FAIL' : '  结果: PASS');
process.exit(failed ? 1 : 0);

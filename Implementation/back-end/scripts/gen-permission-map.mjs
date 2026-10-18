#!/usr/bin/env node
/**
 * 生成「控制器 → 方法 → HTTP 路径 → 权限位」映射表（TSV），供投影比对使用。
 *
 * 为什么脚本产出而不是手工维护：手工表**必漂**，漂了之后比对结果看起来仍然"通过"。
 *
 * ── 注解归属规则（Bug 1 / Bug 3 的边界，2026-10-18 定稿）──────────────────────
 * 对第 i 个映射注解 A_i，取两段文本：
 *   before = 上一个映射注解的**结束位置** → A_i 开始
 *   after  = A_i 结束 → 下一个映射注解开始
 * 判定：
 *   1) before 里有 @PreAuthorize → 取**最后一个**；
 *   2) 否则 after 里有 → 取**第一个**；
 *   3) 都没有 → 未覆盖。
 *
 * 为什么 before 取最后一个、after 取第一个（**就近**原则）：
 *   - 注解在映射**下方**（`@PostMapping` 换行 `@PreAuthorize(...)`）是合法 Java —— 必须能认出（Bug 1）；
 *   - 若把"整段里最后一个"当答案，会把**下一个方法**写在它自己映射上方的注解算进来（Bug 3）。
 *
 * **已知盲区（不假装守住）**：若方法 N 的注解写在 N 的映射**下方**、而方法 N+1 **裸奔**，
 * 则 N+1 的 before 段里存在 N 的后置注解 → N+1 会被误判成"已覆盖"。
 * 根因是**按注解位置切段**而不是**按方法签名切段**；彻底修法要解析方法签名的括号做边界。
 * 同源盲区同样存在于 `AdminEndpointAuthorizationGuardTest.everyMappingCovered`（同一套切段逻辑）。
 * 已登记 open-items，**不允许**因为本脚本或门禁绿了就宣称"没有漏注解的端点"。
 * `--self-test` 里有一条用例专门锁住这个盲区（断言当前行为，不是认可它）。
 *
 * ── expected 列（区分"公开端点"与"漏加注解"）──────────────────────────────────
 *   public      —— 命中 PUBLIC_PREFIXES，本就该没有注解；
 *   guarded     —— 有权限位；
 *   UNANNOTATED —— 受管前缀但**没有任何注解** ⇒ 真正的拆位遗漏，投影比对只报这一类。
 * PUBLIC_PREFIXES 是"哪些端点可以不鉴权"的唯一清单，改动必须人工复核。
 *
 * 用法：
 *   node scripts/gen-permission-map.mjs                 # 打印 TSV
 *   node scripts/gen-permission-map.mjs --self-test     # 边界用例
 *   node scripts/gen-permission-map.mjs --project       # 投影比对预演（只报 UNANNOTATED）
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BACKEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const MAPPING = /@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?/g;
const PREAUTH = /@PreAuthorize\s*\(\s*"@ss\.hasPermi\('([^']+)'\)"\s*\)/g;
const CLASS_MAPPING = /@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/;

/** 受管前缀（与 AdminEndpointAuthorizationGuardTest 同源）：这些端点**必须**有权限位。 */
const MANAGED_PREFIXES = [
  '/api/admin', '/api/seller', '/api/trade/admin', '/api/trade/seller',
  '/api/item/category', '/api/item/brand', '/api/item/attr',
];

/** 匿名可访问（连登录都不要求）；改动必须人工复核。 */
const PUBLIC_PREFIXES = ['/api/search', '/api/home', '/api/shops', '/api/shop/'];

function isManaged(p) {
  return MANAGED_PREFIXES.some((prefix) => p.startsWith(prefix));
}

function isPublic(p) {
  return PUBLIC_PREFIXES.some((prefix) => p.startsWith(prefix));
}

function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/.*$/gm, ' ');
}

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (['target', 'node_modules', '.git'].includes(entry.name)) continue;
      walk(full, out);
    } else if (entry.name.endsWith('Controller.java')) {
      out.push(full);
    }
  }
  return out;
}

/** 就近取注解：before 取最后一个，否则 after 取第一个。 */
function nearestBit(clean, mappingStart, mappingEnd, previousMappingEnd, nextMappingStart) {
  PREAUTH.lastIndex = 0;
  const before = [...clean.slice(previousMappingEnd, mappingStart).matchAll(PREAUTH)];
  if (before.length > 0) return before[before.length - 1][1];
  PREAUTH.lastIndex = 0;
  const after = [...clean.slice(mappingEnd, nextMappingStart).matchAll(PREAUTH)];
  return after.length > 0 ? after[0][1] : '';
}

function classHeader(clean) {
  const index = clean.indexOf('public class');
  return index > 0 ? clean.slice(0, index) : '';
}

function analyze(clean) {
  const header = classHeader(clean);
  const classMapping = CLASS_MAPPING.exec(header);
  if (!classMapping) return null;
  const basePath = classMapping[1];
  PREAUTH.lastIndex = 0;
  const classBitMatch = PREAUTH.exec(header);
  const classBit = classBitMatch ? classBitMatch[1] : '';
  const marks = [...clean.matchAll(MAPPING)];
  const classBodyStart = clean.indexOf('public class');
  const rows = [];
  for (let i = 0; i < marks.length; i += 1) {
    const start = marks[i].index;
    const end = start + marks[i][0].length;
    const previousEnd = i === 0 ? classBodyStart : marks[i - 1].index + marks[i - 1][0].length;
    const nextStart = i + 1 < marks.length ? marks[i + 1].index : clean.length;
    const methodBit = nearestBit(clean, start, end, previousEnd, nextStart);
    const bit = methodBit || classBit;
    const fullPath = basePath + (marks[i][2] || '');
    // 三态：public（匿名）/ buyer（需登录但不需要权限位）/ guarded|UNANNOTATED（受管前缀）
    let expected;
    if (isManaged(fullPath)) {
      expected = bit ? 'guarded' : 'UNANNOTATED';
    } else {
      expected = isPublic(fullPath) ? 'public' : 'buyer';
    }
    rows.push({
      path: fullPath,
      httpMethod: marks[i][1].toUpperCase(),
      bit: bit || '(未覆盖)',
      expected,
      source: methodBit ? 'method' : (classBit ? 'class' : 'MISSING'),
    });
  }
  return rows;
}

function collectRows() {
  const rows = [];
  for (const file of walk(BACKEND_ROOT)) {
    const relative = path.relative(BACKEND_ROOT, file).split(path.sep).join('/');
    if (!relative.includes('/src/main/java/')) continue;
    const analyzed = analyze(stripComments(fs.readFileSync(file, 'utf8')));
    if (!analyzed) continue;
    for (const row of analyzed) rows.push({ ...row, controller: path.basename(file, '.java') });
  }
  return rows.sort((a, b) => (a.path + a.httpMethod).localeCompare(b.path + b.httpMethod));
}

function selfTest() {
  const q = String.fromCharCode(34);
  const cases = [
    {
      name: '注解在映射上方（常规）→ method',
      src: '@RequestMapping(' + q + '/api/admin/a' + q + ') public class C { '
        + '@PreAuthorize(' + q + "@ss.hasPermi('user:read')" + q + ') @GetMapping(' + q + '/x' + q + ') void x() { } }',
      expectBits: ['user:read'],
    },
    {
      name: '注解在映射下方（Bug 1）→ 仍归本方法',
      src: '@RequestMapping(' + q + '/api/admin/b' + q + ') public class C { '
        + '@PostMapping(' + q + '/y' + q + ') @PreAuthorize(' + q + "@ss.hasPermi('user:write')" + q + ') void y() { } }',
      expectBits: ['user:write'],
    },
    {
      name: '上一方法有注解 + 下一方法裸奔（Bug 3 正确解）→ 裸奔者必须为空',
      src: '@RequestMapping(' + q + '/api/admin/c' + q + ') public class C { '
        + '@PreAuthorize(' + q + "@ss.hasPermi('user:read')" + q + ') @GetMapping(' + q + '/a' + q + ') void a() { } '
        + '@GetMapping(' + q + '/b' + q + ') void b() { } }',
      expectBits: ['user:read', ''],
    },
    {
      // 已知盲区：N 的注解在映射下方 + N+1 裸奔 ⇒ N+1 被误判为已覆盖。
      // 断言当前行为，是为了让它成为"有记录的行为"而不是"偶发"。
      name: '★ 已知盲区（上一方法后置注解 + 本方法裸奔）→ 当前被误判为已覆盖',
      src: '@RequestMapping(' + q + '/api/admin/d' + q + ') public class C { '
        + '@PostMapping(' + q + '/a' + q + ') @PreAuthorize(' + q + "@ss.hasPermi('user:write')" + q + ') void a() { } '
        + '@GetMapping(' + q + '/b' + q + ') void b() { } }',
      expectBits: ['user:write', 'user:write'],
      knownBlindSpot: true,
    },
  ];

  let failed = 0;
  for (const item of cases) {
    const rows = analyze(stripComments(item.src));
    const bits = rows.map((r) => (r.bit === '(未覆盖)' ? '' : r.bit));
    const ok = JSON.stringify(bits) === JSON.stringify(item.expectBits);
    if (!ok) failed += 1;
    const tag = item.knownBlindSpot ? '[已知盲区]' : '[用例]';
    process.stdout.write(tag + ' ' + (ok ? 'PASS' : 'FAIL') + ' ' + item.name + ' -> ' + JSON.stringify(bits) + '\n');
  }
  process.stdout.write(failed === 0 ? '\n自测通过（含 1 条已知盲区用例，见脚本注释）\n' : `\n自测失败 ${failed} 条\n`);
  return failed === 0 ? 0 : 1;
}

function main() {
  const mode = process.argv[2];
  if (mode === '--self-test') return selfTest();

  const rows = collectRows();
  if (mode === '--project') {
    const gaps = rows.filter((r) => r.expected === 'UNANNOTATED');
    const pub = rows.filter((r) => r.expected === 'public').length;
    const guarded = rows.filter((r) => r.expected === 'guarded').length;
    process.stdout.write(`投影比对预演：共 ${rows.length} 个映射（public ${pub} / guarded ${guarded} / UNANNOTATED ${gaps.length}）\n`);
    for (const gap of gaps) {
      process.stdout.write(`  ${gap.httpMethod.padEnd(6)} ${gap.path.padEnd(56)} ${gap.controller}\n`);
    }
    process.stdout.write(gaps.length === 0 ? '\n受管端点全部挂位，无拆位遗漏。\n' : '\n以上为受管前缀但无注解的端点。\n');
    return gaps.length === 0 ? 0 : 1;
  }

  process.stdout.write('# path\thttp_method\tpermission\texpected\tcontroller\tsource\n');
  for (const row of rows) {
    process.stdout.write([row.path, row.httpMethod, row.bit, row.expected, row.controller, row.source].join('\t') + '\n');
  }
  process.stderr.write(`[gen-permission-map] ${rows.length} 个映射，${new Set(rows.map((r) => r.controller)).size} 个控制器\n`);
  return 0;
}

process.exit(main());

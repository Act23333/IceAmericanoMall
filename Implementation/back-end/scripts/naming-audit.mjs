// 契约字段命名一致性分析：统计 05-API-Specification.md 中出现的字段标识符
// 区分 lowerCamelCase / snake_case / UPPER_SNAKE / kebab
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
// 仓库根：脚本位于 <root>/Implementation/back-end/scripts/，故上溯 3 层
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

const md = fs.readFileSync(path.join(ROOT, 'project-docs/contract/05-API-Specification.md'), 'utf8');

// 归一化全角引号/破折号
let text = md.replace(/[‘’]/g, "'").replace(/[“”]/g, '"');

// 归一化：屏蔽不属于「线上字段」的两类标识符，避免误报「同名异形」
//   ① URL path 变量 {orderNo} 与 `Path: orderNo` —— 跟随 Java @PathVariable 命名（camelCase），
//      不属字段命名范围；② DB 表名（order_item 等），非线上字段。
text = text.replace(/\{[A-Za-z][A-Za-z0-9]*\}/g, '{}')
           .replace(/(Path:\s*)`?[A-Za-z][A-Za-z0-9]*`?/g, '$1<pathvar>');
const dbTables = new Set();
try {
  const sql = fs.readFileSync(path.join(ROOT, 'Implementation/back-end/database/Initialize.sql'), 'utf8');
  for (const m of sql.matchAll(/CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?([a-z_][a-z0-9_]*)`?/gi)) dbTables.add(m[1]);
  // 表名的 camel 形式（order_item -> orderItem）也一并不计
  for (const t of [...dbTables]) dbTables.add(t.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase()));
} catch { /* SQL 缺失时退化为不排除表名 */ }

// 抓反引号内的标识符、JSON 示例中的 "key":、以及 bare 标识符
const idents = new Map();
function add(name, line, ctx) {
  if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(name)) return;
  if (name.length < 2) return;
  if (dbTables.has(name)) return; // DB 表名不算线上字段
  // 排除明显的非字段：类名（大写开头且非全大写-下划线）、路径片段
  const isSnake = /^[a-z][a-z0-9]*(_[a-z0-9]+)+$/.test(name);
  const isCamel = /^[a-z][a-zA-Z0-9]*$/.test(name) && /[A-Z]/.test(name);
  const isUpper = /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$/.test(name);
  const isKebab = /^[a-z][a-z0-9]*(-[a-z0-9]+)+$/.test(name);
  let kind = null;
  if (isSnake) kind = 'snake_case';
  else if (isKebab) kind = 'kebab-case';
  else if (isCamel) kind = 'lowerCamelCase';
  else if (isUpper) kind = 'UPPER/枚举';
  else kind = 'single-word';
  const k = name;
  if (!idents.has(k)) idents.set(k, { name, kind, lines: [] });
  idents.get(k).lines.push(line);
}

const lines = text.split(/\r?\n/);
for (let i = 0; i < lines.length; i++) {
  const L = lines[i];
  // JSON 示例的 key
  for (const m of L.matchAll(/"([A-Za-z_][A-Za-z0-9_-]*)"\s*:/g)) add(m[1], i + 1, 'json');
  // 反引号内的标识符（截断到第一个非标识符字符）
  for (const m of L.matchAll(/`([A-Za-z_][A-Za-z0-9_-]*)/g)) add(m[1], i + 1, 'code');
  // 表格中的 bare 字段名（如 `LoginResp { access_token, ... }` 已在 code 覆盖）
}

const groups = {};
for (const v of idents.values()) {
  (groups[v.kind] ||= []).push(v);
}

console.log('=== 契约中字段标识符按命名风格分布（去重）===');
const order = ['snake_case', 'lowerCamelCase', 'kebab-case', 'single-word', 'UPPER/枚举'];
for (const k of order) {
  const g = groups[k] || [];
  console.log(`  ${k.padEnd(15)} ${String(g.length).padStart(4)}  ${g.slice(0, 14).map((x) => x.name).join(', ')}${g.length > 14 ? ' …' : ''}`);
}

// 高价值：同一文档内既出现 XxxYyy 又出现 xxx_yyy 的"概念"
const camelNames = (groups['lowerCamelCase'] || []).map((x) => x.name);
const snakeNames = (groups['snake_case'] || []).map((x) => x.name);
const toSnake = (s) => s.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase());
const toCamel = (s) => s.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());

const pairs = [];
for (const c of camelNames) {
  const s = toSnake(c);
  if (snakeNames.includes(s)) pairs.push([c, s]);
}
for (const s of snakeNames) {
  const c = toCamel(s);
  if (camelNames.includes(c) && !pairs.some((p) => p[0] === c)) pairs.push([c, s]);
}

console.log('\n=== 同一契约内"同一概念两种写法"（camelCase vs snake_case 并存）===');
if (!pairs.length) console.log('  （无）');
for (const [c, s] of pairs) {
  const lc = idents.get(c).lines.slice(0, 3).join(',');
  const ls = idents.get(s).lines.slice(0, 3).join(',');
  console.log(`  ${c.padEnd(24)} :${lc.padEnd(18)} <->  ${s.padEnd(24)} :${ls}`);
}
console.log(`\n  共 ${pairs.length} 组同名异形`);

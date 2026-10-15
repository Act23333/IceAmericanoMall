// 校验契约 §七（前端页面 ↔ API 映射）中引用的每个端点是否真实存在于代码
import fs from 'node:fs';
import path from 'node:path';

import { fileURLToPath } from 'node:url';
// 仓库根：脚本位于 <root>/Implementation/back-end/scripts/，故上溯 3 层
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const code = JSON.parse(fs.readFileSync(path.join(ROOT, 'audit-out/endpoints-code.json'), 'utf8'));
const md = fs.readFileSync(path.join(ROOT, 'project-docs/contract/05-API-Specification.md'), 'utf8').split(/\r?\n/);

const norm = (p) => {
  let s = p.trim().replace(/\/+$/, '');
  if (s === '') s = '/';
  s = s.replace(/\{[^}]*\}/g, '{}').replace(/\?.*$/, '');
  return s;
};
const codeSet = new Set(code.map((e) => e.method + ' ' + norm(e.path)));
const codePaths = new Set(code.map((e) => norm(e.path)));

// §七 区间：动态定位（原先硬编码 2344-2429，2026-10-15 fix-doc 编辑使区间下移后失效，
//   且硬编码区间曾"越界到 §4.15/§五 仍报绿"，属静默失真 —— 改为按标题锚定）
const START = md.findIndex((l) => /^##\s*七、/.test(l));
let END = md.findIndex((l, i) => i > START && /^##\s*八、/.test(l));
if (START < 0) throw new Error('未找到 §七 标题（## 七、）——文档结构已变更，请核对本脚本锚点');
if (END < 0) END = md.length;
const refs = [];
for (let i = START; i < END; i++) {
  const line = md[i] || '';
  const re = /`(GET|POST|PUT|PATCH|DELETE|WS)\s+(\/[^`]+?)`/g;
  let m;
  while ((m = re.exec(line))) {
    refs.push({ method: m[1], path: m[2], line: i + 1, page: (line.match(/^\|\s*([^|]+?)\s*\|/) || [])[1] || '' });
  }
}

const seen = new Set();
const missing = [];
const wrongMethod = [];
const ok = [];
for (const r of refs) {
  const k = r.method + ' ' + norm(r.path);
  if (seen.has(k)) continue;
  seen.add(k);
  if (codeSet.has(k)) { ok.push(r); continue; }
  if (codePaths.has(norm(r.path))) { wrongMethod.push(r); continue; }
  missing.push(r);
}

console.log('§七 引用的端点（去重）: ' + seen.size);
console.log('  ✔ 代码中存在: ' + ok.length);
console.log('  ✘ 不存在: ' + missing.length);
console.log('  ⚠ 路径存在但 HTTP 方法不符: ' + wrongMethod.length);

console.log('\n=== 不存在（前端照 §七 编码会 404）===');
for (const r of missing) console.log(`  [:${r.line}] ${r.method} ${r.path}   (页面: ${r.page})`);

if (wrongMethod.length) {
  console.log('\n=== 方法不符 ===');
  for (const r of wrongMethod) {
    const cand = code.filter((e) => norm(e.path) === norm(r.path)).map((e) => e.method);
    console.log(`  [:${r.line}] ${r.method} ${r.path}  → 实际方法: ${[...new Set(cand)].join('/')}   (页面: ${r.page})`);
  }
}

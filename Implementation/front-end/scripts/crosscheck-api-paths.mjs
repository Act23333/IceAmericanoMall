#!/usr/bin/env node
/**
 * 只读：从后端 Java 源码提取「方法级 HTTP 路由表」，与前端真实调用路径做交叉核对。
 * 用法: node <this> <back-end-root> <front-end-root>
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// 两个位置参数均可省略：默认按脚本位置推导（scripts/ → front-end/），与 cwd 无关
const FE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [backendRoot = resolve(FE_ROOT, '..', 'back-end'), frontendRoot = FE_ROOT]
  = process.argv.slice(2);
const SKIP = new Set(['target', 'test', 'node_modules', '.next', 'dist', '.turbo', '.git']);

function walk(dir, ext, out = []) {
  for (const n of readdirSync(dir)) {
    if (SKIP.has(n)) continue;
    const p = join(dir, n);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, ext, out);
    else if (ext.some((e) => n.endsWith(e))) out.push(p);
  }
  return out;
}

function strip(src) {
  return src.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:"'`\\])\/\/[^\n]*/g, '$1');
}

/** 后端路由表：{ 'METHOD /path': 'File#method' } */
const routes = new Map();
for (const file of walk(backendRoot, ['.java'])) {
  const src = strip(readFileSync(file, 'utf8'));
  const cls = src.match(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)/);
  if (!cls) continue;
  const base = cls[1];
  if (base.includes('/internal')) continue;
  // 方法级注解：带路径参数 或 裸注解（裸 @GetMapping/@PostMapping ⇒ 路由即类级路径）
  const withPath = /@(Get|Post|Put|Patch|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\)/g;
  const bare = /@(Get|Post|Put|Patch|Delete)Mapping(?!\s*\()/g;
  const add = (verb, sub) => {
    const full = (base + sub).replace(/\/+$/, '') || '/';
    routes.set(`${verb} ${full}`, relative(backendRoot, file).split(sep).join('/'));
  };
  for (const m of src.matchAll(withPath)) add(m[1].toUpperCase(), m[2]);
  for (const m of src.matchAll(bare)) add(m[1].toUpperCase(), '');
}

/** 前端真实调用路径（剥注释、去模板变量） */
const feRaw = [];
for (const dir of ['apps', 'packages']) {
  for (const file of walk(join(frontendRoot, dir), ['.ts', '.tsx'])) {
    const src = strip(readFileSync(file, 'utf8'));
    src.split('\n').forEach((line, i) => {
      for (const m of line.matchAll(/['"`](\/api\/[A-Za-z0-9_/{}$.\-]*)/g)) {
        feRaw.push({ path: m[1], at: `${relative(frontendRoot, file).split(sep).join('/')}:${i + 1}` });
      }
    });
  }
}

/** 归一化：模板变量 → {v}；query 串（?a=b / ${q}）剔除 */
const norm = (p) => {
  let s = p.split('?')[0];
  s = s.replace(/\$\{[^}]*\}/g, '{v}').replace(/\{[^}]+\}/g, '{v}').replace(/\/+$/, '');
  return s;
};
/** 候选：原样 + 去掉尾随 {v}（模板串里 ${q} 实为 query 串的情况） */
const candidates = (n) => {
  const out = [n];
  const trimmed = n.replace(/(\{v\})+$/, '');
  if (trimmed && trimmed !== n) out.push(trimmed);
  return out;
};
const beNorm = new Set([...routes.keys()].map((k) => {
  const [verb, p] = k.split(' ');
  return `${verb} ${norm(p)}`;
}));
/** 前端路径（忽略 verb，前端路径本身不含 verb） */
const bePaths = new Set([...beNorm].map((k) => k.split(' ')[1]));

console.log('=== 前端真实路径 vs 后端路由表 ===');
const unmatched = [];
const seen = new Set();
for (const { path, at } of feRaw) {
  const n = norm(path);
  if (n === '/api' || n === '/api/') continue;
  if (seen.has(n)) continue;
  seen.add(n);
  const ok = candidates(n).some((c) => bePaths.has(c));
  if (!ok) unmatched.push({ n, at });
  console.log(`  ${ok ? 'OK  ' : 'MISS'}  ${n}${ok ? '' : `   <- ${at}`}`);
}

console.log(`\n前端去重路径: ${seen.size} | 后端路由路径: ${bePaths.size} | 未匹配: ${unmatched.length}`);
if (unmatched.length) {
  console.log('\n未匹配明细：');
  for (const u of unmatched) console.log(`  ${u.n}  <- ${u.at}`);
}

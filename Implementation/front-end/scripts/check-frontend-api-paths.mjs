#!/usr/bin/env node
/**
 * 只读静态自检（不依赖 pnpm build / tsc）：
 *   1. 剥掉 TS/TSX 注释后枚举**真实的** /api/ 调用路径
 *   2. 断言任务清单里的 9 条死路径在前端已 0 命中
 *   3. 断言不存在指向 /api/user/notifications|balance|points、/api/auth/login$、/api/user/sign 的调用
 *
 * 用法: node scripts/check-frontend-api-paths.mjs
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// 前端根 = 本脚本所在目录的上一级（scripts/ → front-end/）；与调用时的 cwd 无关
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const APPS = ['apps', 'packages'];
const SKIP_DIRS = new Set(['node_modules', '.next', 'dist', '.turbo', '.git']);

/** 剥掉 // 行注释与 /* *\/ 块注释（保留字符串内容，够用即可——本仓无含 // 的路径字面量）。 */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:'"`\\])\/\/[^\n]*/g, '$1');
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (SKIP_DIRS.has(name)) continue;
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (/\.(ts|tsx)$/.test(name)) out.push(p);
  }
  return out;
}

const files = APPS.flatMap((a) => walk(join(ROOT, a)));
const PATH_RE = /['"`](\/api\/[A-Za-z0-9_/{}$.\-]*)/g;

const livePaths = new Map(); // path -> [file:line]
for (const file of files) {
  const src = stripComments(readFileSync(file, 'utf8'));
  const lines = src.split('\n');
  lines.forEach((line, i) => {
    for (const m of line.matchAll(PATH_RE)) {
      const key = m[1];
      if (!livePaths.has(key)) livePaths.set(key, []);
      livePaths.get(key).push(`${relative(ROOT, file).split(sep).join('/')}:${i + 1}`);
    }
  });
}

const all = [...livePaths.keys()].sort();

/* ---------- 断言 ---------- */
const DEAD = [
  { label: 'POST /api/auth/login', re: /^\/api\/auth\/login$/ },
  { label: 'POST /api/auth/login/wechat', re: /^\/api\/auth\/login\/wechat/ },
  { label: 'POST /api/user/code', re: /^\/api\/user\/code/ },
  { label: 'POST /api/user/sign + GET /api/user/sign/status', re: /^\/api\/user\/sign/ },
  { label: 'GET /api/user/points/*', re: /^\/api\/user\/points/ },
  { label: 'GET|PUT /api/user/notifications/*', re: /^\/api\/user\/notifications/ },
  { label: 'GET|POST /api/user/balance/*', re: /^\/api\/user\/balance/ },
  { label: 'POST /api/coupon/available/filter', re: /^\/api\/coupon\/available\/filter/ },
];

let failed = 0;
console.log('=== [A] 死路径断言（前端源码剥注释后，必须 0 命中）===');
for (const d of DEAD) {
  const hits = all.filter((p) => d.re.test(p));
  const sites = hits.flatMap((h) => livePaths.get(h));
  const ok = hits.length === 0;
  if (!ok) failed++;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${d.label}${ok ? '' : `  -> ${sites.join(', ')}`}`);
}

console.log('\n=== [B] 现存真实 /api/ 调用清单（剥注释后）===');
for (const p of all) console.log(`  ${p}   (${livePaths.get(p).length} 处)`);

console.log('\n=== [C] 任务清单 9 条修正已落地断言 ===');
const EXPECTED = [
  '/api/auth/web/login',
  '/api/auth/web/wechat/login',
  '/api/auth/wechat/mini/login',
  '/api/verification/codes',
  '/api/sign',
  '/api/sign/status',
  '/api/points/balance',
  '/api/points/history',
  '/api/notifications',
  '/api/notifications/unread-count',
  '/api/notifications/read-all',
  '/api/wallet',
  '/api/wallet/recharge',
];
for (const e of EXPECTED) {
  const ok = all.includes(e);
  if (!ok) failed++;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${e}`);
}

console.log(`\n扫描文件数: ${files.length} | 真实路径去重: ${all.length} | 断言失败: ${failed}`);
process.exit(failed === 0 ? 0 : 1);

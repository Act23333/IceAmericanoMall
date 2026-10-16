#!/usr/bin/env node
/**
 * 设计 token 一致性检查
 * ---------------------------------------------------------------------------
 * 依据: project-docs/frontend/17-Frontend-Design-System.md §2.4（设计系统的 token 规格）
 *
 * 为什么需要它：本项目曾经有 3 份互相独立的 token 副本，其中一份是**死配置**
 * （Tailwind v4 不再自动读取 JS config，而全仓没有任何 `@config` 指令），
 * 且它的值与线上值**已经不同**。改那个文件不会生效，却会让人以为改了。
 * 这类漂移无法靠"读一遍设计系统"发现，只能靠机器比对。
 *
 * 四条规则：
 *   R1 死配置      —— 存在未被 `@config` 引用的 tailwind.config.*  → 失败
 *   R2 文档不撒谎  —— 17 §2.4 里每个 token 必须与线上 CSS 同值     → 失败
 *   R3 不裸写色值  —— app 源码（token 源文件除外）不得出现裸 hex   → 默认警告
 *                      （现状：小程序以 app.scss 充当事实上的 token 层，逐平台迁移中；
 *                        加 --strict 让它成为失败项）
 *   R4 平台覆盖    —— 每个 app 是否已有 token 层                   → 仅报告
 *
 * 豁免：同一行写 `design-token-exempt: <理由>` 即可豁免 R3 命中，
 *       豁免必须显式且能被人 review —— 这是刻意的。
 *
 * 用法: node scripts/check-design-tokens.mjs [--json]
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = path.resolve(here, '..');           // Implementation/front-end
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '..', '..'); // 仓库根
const APPS_DIR = path.join(FRONTEND_ROOT, 'apps');
const WEB_TOKENS = path.join(APPS_DIR, 'web', 'app', 'globals.css');
const DESIGN_DOC = path.join(REPO_ROOT, 'project-docs', 'frontend', '17-Frontend-Design-System.md');
const AS_JSON = process.argv.includes('--json');
const STRICT = process.argv.includes('--strict');

const failures = [];
const warnings = [];
const notes = [];
const fail = (rule, msg, detail) => failures.push({ rule, msg, detail });
const warn = (rule, msg, detail) => warnings.push({ rule, msg, detail });

// ── 工具 ────────────────────────────────────────────────────────────────────
const read = (p) => { try { return fs.readFileSync(p, 'utf8'); } catch { return null; } };

/** 取出第一个 ```<lang> 围栏块的内容 */
function fencedBlock(text, lang) {
  const m = new RegExp('```' + lang + '\\s*\\n([\\s\\S]*?)```').exec(text);
  return m ? m[1] : null;
}

/** 取出 @theme { ... } 的块体（按花括号配对，不依赖缩进） */
function themeBody(css) {
  const i = css.indexOf('@theme');
  if (i === -1) return null;
  const open = css.indexOf('{', i);
  if (open === -1) return null;
  let depth = 0;
  for (let j = open; j < css.length; j++) {
    if (css[j] === '{') depth++;
    else if (css[j] === '}') { depth--; if (depth === 0) return css.slice(open + 1, j); }
  }
  return null;
}

/** 解析 `--name: value;` 成 Map（忽略注释） */
function parseTokens(body) {
  const out = new Map();
  if (!body) return out;
  for (const raw of body.split('\n')) {
    const line = raw.replace(/\/\*[\s\S]*?\*\//g, '').trim();
    const m = /^(--[A-Za-z0-9-]+)\s*:\s*(.+?);\s*$/.exec(line);
    if (m) out.set(m[1], m[2].replace(/\s+/g, ' ').trim());
  }
  return out;
}

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (['node_modules', '.next', 'dist', 'build', '.turbo', 'coverage'].includes(e.name)) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

// ── R1: 死配置 ──────────────────────────────────────────────────────────────
const allFiles = fs.existsSync(FRONTEND_ROOT) ? walk(FRONTEND_ROOT) : [];
const tailwindConfigs = allFiles.filter((f) => /(^|[\\/])tailwind\.config\.[cm]?[jt]s$/.test(f));
for (const cfg of tailwindConfigs) {
  // 该 config 所属的 app 目录（config 在 app 根，或 packages/config 这类共享包）
  const ownerDir = path.dirname(cfg);
  const ownerName = path.basename(ownerDir);
  // 在该 app 的 CSS 里找 @config（Tailwind v4 唯一显式加载 JS config 的方式）
  let referenced = false;
  for (const css of allFiles.filter((f) => f.endsWith('.css'))) {
    const txt = read(css) ?? '';
    const m = /@config\s+["']([^"']+)["']/.exec(txt);
    if (m && path.resolve(path.dirname(css), m[1]) === path.resolve(cfg)) { referenced = true; break; }
  }
  if (!referenced) {
    fail('R1', `死配置：${path.relative(FRONTEND_ROOT, cfg)} 未被任何 @config 引用`,
      'Tailwind v4 是 CSS-first，不会自动读取 JS config。该文件里的 token 不会生效，' +
      '但它的旧值会误导改它的人 —— 请删除它，或在 CSS 中用 @config 显式加载（不建议：会造成两套 token 来源）。');
  } else if (ownerName) {
    notes.push(`R1 ${path.relative(FRONTEND_ROOT, cfg)} 已通过 @config 加载`);
  }
}

// ── R2: 文档不得撒谎 ────────────────────────────────────────────────────────
const docTxt = read(DESIGN_DOC);
const webTxt = read(WEB_TOKENS);
if (!docTxt) fail('R2', `找不到设计系统文档：${path.relative(REPO_ROOT, DESIGN_DOC)}`);
if (!webTxt) fail('R2', `找不到 Web token 源：${path.relative(FRONTEND_ROOT, WEB_TOKENS)}`);

if (docTxt && webTxt) {
  const docCss = fencedBlock(docTxt, 'css');
  const docTokens = parseTokens(themeBody(docCss ?? ''));
  const codeTokens = parseTokens(themeBody(webTxt));

  if (docTokens.size === 0) fail('R2', '17 §2.4 的 ```css 块里没有解析到任何 token（结构变了？）');
  if (codeTokens.size === 0) fail('R2', 'apps/web/app/globals.css 的 @theme 块里没有解析到任何 token');

  const missing = [], mismatched = [];
  for (const [k, v] of docTokens) {
    if (!codeTokens.has(k)) missing.push(k);
    else if (codeTokens.get(k) !== v) mismatched.push(`${k}: 文档=${v}  代码=${codeTokens.get(k)}`);
  }
  if (missing.length) fail('R2', `17 §2.4 声明了 ${missing.length} 个代码中不存在的 token`, missing.join(', '));
  if (mismatched.length) fail('R2', `${mismatched.length} 个 token 文档与代码值不同`, mismatched.join('\n      '));

  const undocumented = [...codeTokens.keys()].filter((k) => !docTokens.has(k));
  if (undocumented.length > 0) {
    warn('R2', `代码里有 ${undocumented.length} 个 token 未写进 17 §2.4（文档是精选子集，仅提示）`,
      undocumented.slice(0, 12).join(', ') + (undocumented.length > 12 ? ' …' : ''));
  }
  if (!missing.length && !mismatched.length) {
    notes.push(`R2 17 §2.4 的 ${docTokens.size} 个 token 与线上 CSS 全部同值`);
  }
}

// ── R3: 不裸写色值 ──────────────────────────────────────────────────────────
const HEX = /#[0-9A-Fa-f]{6}\b|#[0-9A-Fa-f]{3}\b/g;
const SRC_EXT = /\.(tsx|ts|jsx|js|vue|css|scss|less)$/;
const TOKEN_SOURCES = new Set([path.resolve(WEB_TOKENS)]);
const rawHexHits = [];
for (const f of allFiles) {
  if (!SRC_EXT.test(f)) continue;
  if (TOKEN_SOURCES.has(path.resolve(f))) continue;
  if (/[\\/](mock|fixtures?|__mocks__)[\\/]/.test(f)) continue; // 假数据允许
  const txt = read(f) ?? '';
  txt.split('\n').forEach((line, i) => {
    if (line.includes('design-token-exempt')) return;
    const m = line.match(HEX);
    if (m) rawHexHits.push({ file: path.relative(FRONTEND_ROOT, f), line: i + 1, hex: m.join(' '), text: line.trim().slice(0, 90) });
  });
}
if (rawHexHits.length) {
  const byFile = new Map();
  for (const h of rawHexHits) byFile.set(h.file, (byFile.get(h.file) ?? 0) + 1);
  (STRICT ? fail : warn)('R3', `app 源码里有 ${rawHexHits.length} 处裸 hex（${byFile.size} 个文件）`,
    [...byFile.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(([f, n]) => `${String(n).padStart(3)}  ${f}`).join('\n      ') +
    '\n      改用 token；确有例外时在该行写 `design-token-exempt: <理由>`。');
}

// ── R4: 平台覆盖（仅报告） ──────────────────────────────────────────────────
const apps = fs.existsSync(APPS_DIR) ? fs.readdirSync(APPS_DIR, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name) : [];
const coverage = [];
for (const app of apps) {
  const css = allFiles.filter((f) => f.startsWith(path.join(APPS_DIR, app)) && f.endsWith('.css'));
  let hasTheme = false;
  for (const c of css) if (themeBody(read(c) ?? '')) { hasTheme = true; break; }
  coverage.push({ app, hasTheme });
}
const noLayer = coverage.filter((c) => !c.hasTheme).map((c) => c.app);
if (noLayer.length) {
  warn('R4', `${noLayer.length}/${coverage.length} 个 app 尚未有 token 层`, noLayer.join(', ') +
    '\n      非 Web 平台不必用 @theme，但需要一个同源的 token 模块（见 17 §2.4 的平台映射要求）。');
}
notes.push(`R4 平台覆盖: ${coverage.map((c) => c.app + (c.hasTheme ? '✓' : '✗')).join('  ')}`);

// ── 输出 ────────────────────────────────────────────────────────────────────
if (AS_JSON) {
  console.log(JSON.stringify({ ok: failures.length === 0, failures, warnings, notes, coverage }, null, 2));
} else {
  console.log('\n设计 token 一致性检查  ·  依据 17-Frontend-Design-System.md §2.4\n' + '─'.repeat(70));
  for (const n of notes) console.log('  · ' + n);
  if (warnings.length) {
    console.log('\n警告');
    for (const w of warnings) console.log(`  [${w.rule}] ${w.msg}\n      ${w.detail ?? ''}`);
  }
  if (failures.length) {
    console.log('\n失败');
    for (const f of failures) console.log(`  [${f.rule}] ${f.msg}\n      ${f.detail ?? ''}`);
  }
  console.log('\n' + '─'.repeat(70));
  console.log(failures.length === 0 ? `通过（${warnings.length} 条警告）` : `未通过：${failures.length} 项`);
}
process.exit(failures.length === 0 ? 0 : 1);

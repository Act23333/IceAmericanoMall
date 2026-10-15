// 端点清单提取器 v2：代码侧（Spring 注解）vs 契约侧（05-API-Specification.md 表格）
// 用法: node audit-out/extract-endpoints.mjs
import fs from 'node:fs';
import path from 'node:path';

import { fileURLToPath } from 'node:url';
// 仓库根：脚本位于 <root>/Implementation/back-end/scripts/，故上溯 3 层
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const BE = path.join(ROOT, 'Implementation', 'back-end');
const CONTRACT = path.join(ROOT, 'project-docs', 'contract', '05-API-Specification.md');
// 生成物默认不落盘（避免在仓库里留下未登记产物）；需要时用 --out=<dir>
const OUT_ARG = process.argv.find((a) => a.startsWith('--out='));
const OUT = OUT_ARG ? path.resolve(OUT_ARG.slice('--out='.length)) : null;

/* ---------------- 1. 代码侧 ---------------- */

/** 取注解里的路径字面量。支持 "x" / value="x" / path="x" / {"/a","/b"} / 无参数 */
function extractPaths(annText) {
  const paren = annText.indexOf('(');
  if (paren === -1) return [''];
  let args = annText.slice(paren + 1).trim().replace(/\)\s*$/, '').trim();
  const named = args.match(/(?:^|,)\s*(?:value|path)\s*=\s*([\s\S]*)$/);
  if (named) args = named[1].trim();
  if (args.startsWith('{')) {
    return [...args.matchAll(/"([^"]*)"/g)].map((m) => m[1]);
  }
  const bare = args.match(/^"([^"]*)"/);
  if (bare) return [bare[1]];
  return ['']; // 无参 → 用类级路径；或引用常量（下面会标记）
}

function extractMethod(annText) {
  const m = annText.match(/method\s*=\s*(?:RequestMethod\.)?([A-Z]+)/);
  if (m) return m[1];
  const ann = annText.match(/@(Get|Post|Put|Patch|Delete)Mapping/);
  if (ann) return ann[1].toUpperCase();
  return 'ANY';
}

function parseController(file) {
  const src = fs.readFileSync(file, 'utf8');
  const lines = src.split(/\r?\n/);

  let classPaths = [''];
  let classLine = null;
  let classRaw = '';
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*(public\s+|final\s+|abstract\s+)*\s*(class|interface|record|enum)\s+\w/.test(lines[i])) break;
    if (/^\s*@RequestMapping\b/.test(lines[i])) {
      let text = lines[i];
      let j = i;
      let guard = 0;
      while (guard++ < 8 && text.includes('(') && !text.includes(')') && j + 1 < lines.length) {
        j++;
        text += ' ' + lines[j].trim();
      }
      classPaths = extractPaths(text);
      classLine = i + 1;
      classRaw = text.trim();
      break;
    }
  }

  const entries = [];
  let i = 0;
  while (i < lines.length) {
    const l = lines[i];
    if (/^\s*@(Get|Post|Put|Patch|Delete|Request)Mapping\b/.test(l)) {
      let text = l;
      let j = i;
      let guard = 0;
      while (guard++ < 8 && text.includes('(') && !text.includes(')') && j + 1 < lines.length) {
        j++;
        text += ' ' + lines[j].trim();
      }
      if (classLine !== i + 1) {
        // 判断是"无路径参数"（path = 类级路径）还是"引用了常量"（无法静态解析）
        const paren = text.indexOf('(');
        const argsRaw = paren === -1 ? '' : text.slice(paren + 1).trim().replace(/\)\s*$/, '').trim();
        const constant = argsRaw !== '' && !/"/.test(argsRaw);
        entries.push({
          method: extractMethod(text),
          paths: extractPaths(text),
          line: i + 1,
          ann: text.trim().replace(/\s+/g, ' ').slice(0, 160),
          constant,
        });
      }
      i = j;
    }
    i++;
  }
  return { classPaths, classLine, classRaw, entries, lineCount: lines.length };
}

function joinPath(base, sub) {
  const b = (base || '').replace(/\/+$/, '');
  const s = (sub || '').replace(/^\/+/, '');
  if (!b && !s) return '/';
  if (!s) return b || '/';
  return b + '/' + s;
}

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (['target', 'node_modules', '.git', '.idea'].includes(e.name)) continue;
      walk(p, out);
    } else if (e.name.endsWith('.java')) out.push(p);
  }
  return out;
}

const allJava = walk(BE);
const controllers = allJava.filter(
  (f) => /\/adapter\//.test(f.replace(/\\/g, '/')) && /Controller\.java$/.test(f)
);

const codeEndpoints = [];
const constantPathAnns = [];
for (const f of controllers) {
  const svc = path.relative(BE, f).split(path.sep)[0];
  const rel = path.relative(ROOT, f).replace(/\\/g, '/');
  const { classPaths, classLine, entries, lineCount } = parseController(f);
  for (const e of entries) {
    if (e.constant) {
      constantPathAnns.push(`${rel}:${e.line} ${e.ann}`);
      continue;
    }
    for (const sub of e.paths) {
      for (const cp of classPaths) {
        codeEndpoints.push({
          service: svc,
          controller: path.basename(f, '.java'),
          method: e.method,
          path: joinPath(cp, sub),
          classPath: cp,
          file: rel,
          line: e.line,
          fileLines: lineCount,
        });
      }
    }
  }
}

/* ---------------- 2. 契约侧 ---------------- */

const md = fs.readFileSync(CONTRACT, 'utf8');
const mdLines = md.split(/\r?\n/);
const docEndpoints = [];
const METHOD_RE = /^(GET|POST|PUT|PATCH|DELETE|WS|SSE|CONNECT)$/;
for (let i = 0; i < mdLines.length; i++) {
  const line = mdLines[i];
  if (!line.trim().startsWith('|')) continue;
  const cells = line.split('|').slice(1, -1).map((c) => c.trim());
  if (cells.length < 3) continue;
  const method = cells[1].toUpperCase();
  if (!METHOD_RE.test(method)) continue;
  const pathCell = cells[2];
  const m = pathCell.match(/^`([^`]+)`/);
  if (!m) continue;
  let p = m[1].trim();
  let query = '';
  const qi = p.indexOf('?');
  if (qi >= 0) {
    query = p.slice(qi);
    p = p.slice(0, qi);
  }
  docEndpoints.push({
    id: cells[0],
    method,
    path: p,
    query,
    stage: cells[cells.length - 1],
    line: i + 1,
    raw: line.slice(0, 200),
  });
}

/* ---------------- 3. 归一化与比对 ---------------- */

function norm(p) {
  let s = p.trim().replace(/\/+$/, '');
  if (s === '') s = '/';
  s = s.replace(/\{[^}]*\}/g, '{}');
  return s;
}
const key = (m, p) => m.toUpperCase() + ' ' + norm(p);

const codeMap = new Map();
for (const e of codeEndpoints) {
  const k = key(e.method, e.path);
  if (!codeMap.has(k)) codeMap.set(k, []);
  codeMap.get(k).push(e);
}
const docMap = new Map();
for (const e of docEndpoints) {
  const k = key(e.method, e.path);
  if (!docMap.has(k)) docMap.set(k, []);
  docMap.get(k).push(e);
}

const onlyCode = [...codeMap.keys()].filter((k) => !docMap.has(k)).sort();
const onlyDoc = [...docMap.keys()].filter((k) => !codeMap.has(k)).sort();
const both = [...codeMap.keys()].filter((k) => docMap.has(k)).sort();

const isInternal = (k) => k.includes(' /internal/');
const isAdmin = (k) => k.includes(' /api/admin/');

// per-service 统计
const svcCode = {};
for (const k of codeMap.keys()) {
  const e = codeMap.get(k)[0];
  svcCode[e.service] = (svcCode[e.service] || 0) + 1;
}

const summary = {
  code: {
    distinct: codeMap.size,
    rawMappings: codeEndpoints.length,
    controllers: controllers.length,
    internal: [...codeMap.keys()].filter(isInternal).length,
    admin: [...codeMap.keys()].filter(isAdmin).length,
    publicNonInternal: [...codeMap.keys()].filter((k) => !isInternal(k)).length,
  },
  doc: {
    rows: docEndpoints.length,
    distinct: docMap.size,
    internal: [...docMap.keys()].filter(isInternal).length,
    admin: [...docMap.keys()].filter(isAdmin).length,
    withQuery: docEndpoints.filter((e) => e.query).length,
  },
  matched: both.length,
  codeOnly: onlyCode.length,
  docOnly: onlyDoc.length,
  constantPathAnnCount: constantPathAnns.length,
  perServiceCodeEndpoints: svcCode,
};

if (OUT) {
  fs.mkdirSync(OUT, { recursive: true });
}
/** 仅在 --out=<dir> 时落盘；否则为 no-op（结果仍全部打到 stdout）。 */
const w = (name, data) => {
  if (!OUT) return;
  fs.writeFileSync(path.join(OUT, name), typeof data === 'string' ? data : JSON.stringify(data, null, 1));
};

w('endpoints-code.json', codeEndpoints);
w('endpoints-doc.json', docEndpoints);
w('endpoint-diff.json', {
  summary,
  codeOnly: onlyCode.map((k) => ({ key: k, occurrences: codeMap.get(k).map((e) => `${e.file}:${e.line}`) })),
  docOnly: onlyDoc.map((k) => ({
    key: k,
    occurrences: docMap.get(k).map((e) => `05-API-Specification.md:${e.line} (${e.id}) stage=${e.stage}`),
  })),
});

console.log('=== SUMMARY ===');
console.log(JSON.stringify(summary, null, 1));
console.log('\n=== 代码有 / 契约无 (' + onlyCode.length + ') ===');
for (const k of onlyCode)
  console.log('  ' + k + '   <- ' + codeMap.get(k).map((e) => e.file.replace('Implementation/back-end/', '') + ':' + e.line).join(', '));
console.log('\n=== 契约有 / 代码无 (' + onlyDoc.length + ') ===');
for (const k of onlyDoc)
  console.log('  ' + k + '   <- 05-API-Specification.md:' + docMap.get(k).map((e) => e.line + '(' + e.id + ')').join(', '));
if (constantPathAnns.length) {
  console.log('\n=== 路径引用常量（未能静态解析，需人工核对）(' + constantPathAnns.length + ') ===');
  for (const c of constantPathAnns) console.log('  ' + c);
}

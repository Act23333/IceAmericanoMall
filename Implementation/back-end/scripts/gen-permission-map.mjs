#!/usr/bin/env node
/**
 * 生成「控制器 → 方法 → HTTP 路径 → 权限位」映射表（TSV），供投影比对使用。
 *
 * 为什么要脚本产出而不是手工维护：手工表**必漂**，而漂了之后比对结果看起来仍然"通过"
 * （open-items 判据 #11 的降级方案反面教材）。这里复用与门禁相同的判定口径：
 *   · 类级 @PreAuthorize 覆盖全部方法；
 *   · 否则取**该映射注解之前**最近的一个 @PreAuthorize（逐方法覆盖）。
 *
 * 已知局限（与门禁一致，不假装守住）：只做**字面量**扫描，复杂 SpEL 表达式不支持。
 *
 * 用法：
 *   node scripts/gen-permission-map.mjs            # 打印到 stdout
 *   node scripts/gen-permission-map.mjs > /tmp/permission-map.tsv
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Windows 上 new URL(...).pathname 会给出 /C:/... 形态，拼出来是 C:\C:\... —— 用 fileURLToPath
const BACKEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const MAPPING = /@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*"([^"]*)"\s*\))?/g;
const PREAUTH = /@PreAuthorize\s*\(\s*"@ss\.hasPermi\('([^']+)'\)"\s*\)/g;
const CLASS_MAPPING = /@RequestMapping\s*\(\s*"([^"]+)"/;

function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/.*$/gm, ' ');
}

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'target' || entry.name === 'node_modules' || entry.name === '.git') continue;
      walk(full, out);
    } else if (entry.name.endsWith('Controller.java')) {
      out.push(full);
    }
  }
  return out;
}

function classHeader(clean) {
  const index = clean.indexOf('public class');
  return index > 0 ? clean.slice(0, index) : '';
}

function hasClassLevel(clean) {
  return /@PreAuthorize/.test(classHeader(clean));
}

const rows = [];
for (const file of walk(BACKEND_ROOT)) {
  const relative = path.relative(BACKEND_ROOT, file).split(path.sep).join('/');
  if (!relative.includes('/src/main/java/')) continue;

  const clean = stripComments(fs.readFileSync(file, 'utf8'));
  const mapping = CLASS_MAPPING.exec(classHeader(clean));
  if (!mapping) continue;
  const basePath = mapping[1];
  const classLevel = hasClassLevel(clean);
  PREAUTH.lastIndex = 0;   // 全局正则的 lastIndex 会被上一次 exec 推进，必须复位
  const classBit = classLevel ? (PREAUTH.exec(classHeader(clean))?.[1] ?? '(类级表达式非单参)') : '';
  const className = path.basename(file, '.java');

  // 逐方法：取该映射注解之前最近的 @PreAuthorize
  const marks = [...clean.matchAll(MAPPING)];
  for (let i = 0; i < marks.length; i += 1) {
    const start = marks[i].index;
    // 注解可能写在映射注解**之前**或**之后**（两者都是合法 Java），
    // 故取「上一个映射 → 下一个映射」之间的整段，而不是只往回找。
    // 抽验发现的 bug：只往回找时，CategoryController / AdminAfterSaleController 被误报未覆盖。
    const previousEnd = i === 0 ? (clean.indexOf('public class') + 'public class'.length) : marks[i - 1].index;
    const nextEnd = i + 1 < marks.length ? marks[i + 1].index : clean.length;
    const mappingEnd = start + marks[i][0].length;
    // 取**离本映射最近**的注解：先看它之前（就近取最后一个），没有再往后看（就近取第一个）。
    // 不能用"整段里最后一个"—— 下一个方法的注解写在它自己的映射之前，会被算进本段（抽验发现：
    // 售后 GET / 被后一个方法前置换来的 pay:admin 覆盖，本应是 aftersale:read）。
    PREAUTH.lastIndex = 0;
    const before = [...clean.slice(previousEnd, start).matchAll(PREAUTH)];
    PREAUTH.lastIndex = 0;
    const after = [...clean.slice(mappingEnd, nextEnd).matchAll(PREAUTH)];
    const methodBit = before.length > 0
      ? before[before.length - 1][1]
      : (after.length > 0 ? after[0][1] : '');
    const bit = methodBit || classBit || '(未覆盖)';
    const httpMethod = marks[i][1].toUpperCase();
    const suffix = marks[i][2] || '';
    rows.push([basePath + suffix, httpMethod, bit, className, methodBit ? 'method' : (classLevel ? 'class' : 'MISSING')]);
  }
}

rows.sort((a, b) => (a[0] + a[1]).localeCompare(b[0] + b[1]));
process.stdout.write('# path\thttp_method\tpermission\tcontroller\tsource\n');
for (const row of rows) {
  process.stdout.write(row.join('\t') + '\n');
}
process.stderr.write(`[gen-permission-map] ${rows.length} 个映射，${new Set(rows.map((r) => r[3])).size} 个控制器\n`);

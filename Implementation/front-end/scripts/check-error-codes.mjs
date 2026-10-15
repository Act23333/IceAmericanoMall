#!/usr/bin/env node
/**
 * 只读：核对 packages/api/error-codes.ts 的 ERROR_CODE 是否与后端 ErrorCode.java 逐条一致。
 * 用法: node scripts/check-error-codes.mjs ../back-end
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

// 默认后端根 = 脚本上两级（scripts/ → front-end/ → Implementation/back-end）；与 cwd 无关
const backend = process.argv[2]
  ?? resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'back-end');
const javaPath = `${backend}/ia-common/src/main/java/org/icedamericanomall/common/enums/ErrorCode.java`;
// 前端根同样按脚本位置推导，避免依赖 cwd
const FE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const tsPath = resolve(FE_ROOT, 'packages/api/error-codes.ts');

const java = readFileSync(javaPath, 'utf8');
const ts = readFileSync(tsPath, 'utf8');

/** Java 枚举常量：NAME(1234, */
const javaCodes = new Map();
for (const m of java.matchAll(/^\s{4}([A-Z][A-Z0-9_]*)\((\d+),\s*"([^"]*)"/gm)) {
  javaCodes.set(m[1], { code: Number(m[2]), msg: m[3] });
}

/** TS：ERROR_CODE 常量对象里的 NAME: 1234, */
const tsBlock = ts.match(/export const ERROR_CODE = \{([\s\S]*?)\} as const;/);
if (!tsBlock) throw new Error('ERROR_CODE 块未找到');
const tsCodes = new Map();
for (const m of tsBlock[1].matchAll(/^\s*([A-Z][A-Z0-9_]*):\s*(\d+),/gm)) {
  tsCodes.set(m[1], Number(m[2]));
}

/** TS：ERROR_MAP 的覆盖键（[ERROR_CODE.X] 形式） */
const errMap = new Set([...ts.matchAll(/\[ERROR_CODE\.([A-Z0-9_]+)\]/g)].map((m) => m[1]));

const missing = [...javaCodes.keys()].filter((k) => !tsCodes.has(k));
const extra = [...tsCodes.keys()].filter((k) => !javaCodes.has(k));
const mismatched = [...javaCodes.entries()]
  .filter(([k, v]) => tsCodes.has(k) && tsCodes.get(k) !== v.code)
  .map(([k, v]) => `${k}: java=${v.code} ts=${tsCodes.get(k)}`);
const unmapped = [...javaCodes.keys()].filter((k) => !errMap.has(k));

console.log(`Java 枚举码数: ${javaCodes.size} | TS ERROR_CODE 码数: ${tsCodes.size} | ERROR_MAP 覆盖: ${errMap.size}`);
console.log(`缺失(Java 有 TS 无): ${missing.length}${missing.length ? ' -> ' + missing.join(', ') : ''}`);
console.log(`多余(TS 有 Java 无): ${extra.length}${extra.length ? ' -> ' + extra.join(', ') : ''}`);
console.log(`码值不一致: ${mismatched.length}${mismatched.length ? ' -> ' + mismatched.join(', ') : ''}`);
console.log(`无文案(ERROR_MAP 未覆盖): ${unmapped.length}${unmapped.length ? ' -> ' + unmapped.join(', ') : ''}`);
console.log(`已废弃码 1004 残留: ${/\b1004\b/.test(ts) ? 'YES(FAIL)' : 'NO(PASS)'}`);

const fail = missing.length + extra.length + mismatched.length + unmapped.length + (/\b1004\b/.test(ts) ? 1 : 0);
process.exit(fail === 0 ? 0 : 1);

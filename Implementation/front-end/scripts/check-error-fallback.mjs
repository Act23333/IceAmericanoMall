#!/usr/bin/env node
/**
 * U19 用例：**未登记的 4xx 必须落「通用兜底 + 直显 msg」**，不得因为「状态码不在清单里」而改走
 * 「服务异常」话术（open-items U19 ①）。
 *
 * 背景（为什么必须有这条）：契约 §二 的「下游透传」规则生效后，`/api/**` 可能出现**不在
 * `§2.1a` 清单里的状态码**（下游 409/412/422/429、非标准 499 …），业务码却是下游原样的。
 * 前端若按「状态码集合」分支，就会把这类响应当成「未知故障」推到「服务异常/请稍后重试」——
 * 而用户真正需要看到的是后端给出的那句 `msg`。
 *
 * 因此本脚本锁两件事：
 *   ① **不给状态码开白名单**（那会变成与 `ERROR_MAP` 并列的第二套映射，必然漂）；
 *   ② 未登记状态码 → 返回后端 `msg`（有 msg）或**通用兜底**（无 msg），且**绝不**出现
 *      「服务异常」「第三方」这类把责任推给「平台挂了」的措辞。
 *
 * 实现方式：Node 22+ 的类型剥离直接跑**真实的** `packages/api/error-codes.ts`
 * （不复制一份判据 —— 复制出来的判据守的是它自己的想象，见 09 §五·11「门禁输入必须与运行时同源」）。
 *
 * 用法: node scripts/check-error-fallback.mjs
 */
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { spawnSync } from 'node:child_process';

const FE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const MODULE = resolve(FE_ROOT, 'packages/api/error-codes.ts');

/**
 * 自我重启：本脚本要 import 一个 `.ts`，需要 Node 的**类型剥离**开关。
 * 直接 `node scripts/check-error-fallback.mjs` 也应可用 —— 否则「要用一条不会有人记得加参数的命令」
 * 就等于没有这条门禁（判据：门槛必须便宜到顺手就做）。
 */
if (!process.execArgv.includes('--experimental-strip-types')) {
  const relaunch = spawnSync(
    process.execPath,
    ['--experimental-strip-types', '--no-warnings', fileURLToPath(import.meta.url)],
    { stdio: 'inherit' },
  );
  process.exit(relaunch.status ?? 3);
}

let mod;
try {
  mod = await import(`file://${MODULE.replace(/\\/g, '/')}`);
} catch (e) {
  console.error('[FAIL] 无法加载 error-codes.ts（需要 Node 22.6+ 的 --experimental-strip-types）');
  console.error('       ' + (e?.message ?? e));
  process.exit(3); // 3 = 环境不满足，**不是通过**
}

const { getUserMessage, isNotFound, isThirdPartyError, ERROR_CODE } = mod;

const failures = [];
const check = (name, actual, predicate, hint) => {
  const ok = predicate(actual);
  if (!ok) failures.push(`${name}: 实际=${JSON.stringify(actual)}${hint ? ' —— ' + hint : ''}`);
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}`);
};

/** 「把责任推给平台」的措辞 —— 未登记状态码**不得**落到这些文案上。 */
const BLAME_PLATFORM = /服务异常|第三方|系统繁忙|稍后再试|稍后重试/;

console.log('=== [0] 反空前提：模块与判据确实存在（否则下面全是空集通过）===');
if (typeof getUserMessage !== 'function') {
  console.error('[FAIL] getUserMessage 不存在 —— 本脚本守的东西不存在，不能算通过');
  process.exit(1);
}
const probeKnown = getUserMessage({ code: ERROR_CODE.PARAM_ERROR, message: '' });
if (!probeKnown || probeKnown === '') {
  console.error('[FAIL] 已知业务码未产出文案 —— 模块加载异常，不能算通过');
  process.exit(1);
}
console.log(`  PASS  ERROR_CODE.PARAM_ERROR(${ERROR_CODE.PARAM_ERROR}) -> ${probeKnown}`);

console.log('\n=== [1] 未登记 4xx + 后端 msg ⇒ 直显 msg（不改写、不降级成平台话术）===');
for (const status of [409, 412, 422, 429, 451, 499]) {
  const msg = `下游原话(${status})`;
  check(
    `HTTP ${status} + msg → 直显`,
    getUserMessage({ code: status, message: msg }),
    (v) => v === msg,
    '未登记状态码必须原样直显后端 msg（契约 §二 下游透传）',
  );
}

console.log('\n=== [2] 未登记 4xx 且无 msg ⇒ 通用兜底，且不得甩锅「服务异常」===');
for (const status of [409, 422, 429, 499]) {
  check(
    `HTTP ${status} 无 msg → 通用兜底`,
    getUserMessage({ code: status }),
    (v) => !!v && !BLAME_PLATFORM.test(v),
    '不得出现「服务异常/第三方/稍后重试」：那不是「状态码未知」，而是「平台不可用」的另一句话术',
  );
}
check(
  '形如 "HTTP 500" 的占位消息不被采用',
  getUserMessage({ code: 409, message: 'HTTP 500' }),
  (v) => v !== 'HTTP 500' && !BLAME_PLATFORM.test(v),
);

console.log('\n=== [3] 既有语义不回退（本批不得把它们改坏）===');
check('HTTP 404 → 资源不存在', getUserMessage({ code: 404 }), (v) => v.includes('不存在'));
check('1103(NO_RESOURCE_FOUND) → 走 ERROR_MAP', getUserMessage({ code: ERROR_CODE.NO_RESOURCE_FOUND, message: '不应出现' }),
  (v) => v !== '不应出现', '已知码的稳定文案优先于动态 msg');
check('已知业务码(1101) → ERROR_MAP 优先于 msg', getUserMessage({ code: ERROR_CODE.PARAM_ERROR, message: '不应出现' }),
  (v) => v !== '不应出现' && v !== '网络异常，请检查连接后重试', '已知码的稳定文案优先于动态 msg');
check('isNotFound 认 404', isNotFound({ code: 404 }), (v) => v === true);

console.log('\n=== [4] 反例：若有人给状态码开白名单（把 4xx 当第三方故障），本组必红 ===');
for (const status of [409, 422, 429, 499]) {
  check(
    `HTTP ${status} 不被判为第三方渠道错误`,
    isThirdPartyError(status),
    (v) => v === false,
    '第三方判定按 4xxx 业务码段；HTTP 状态码若混进来，下游透传的 4xx 会被误判成「渠道不可用」',
  );
}

console.log(`\n=== 汇总：断言 ${failures.length === 0 ? '全部通过' : failures.length + ' 条失败'} ===`);
if (failures.length) {
  failures.forEach((f) => console.error('  [FAIL] ' + f));
  process.exit(1);
}
process.exit(0);

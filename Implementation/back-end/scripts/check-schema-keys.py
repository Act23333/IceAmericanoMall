#!/usr/bin/env python3
"""
只读结构对账（U4）—— **不连库、不进 CI、不阻塞提交**。

守三件事（都是「靠人眼会漏、靠类型又不报错」的那一类）：

  ① **DROP 清单 ⊇ CREATE 清单**：文件头 (e) 明写「新增表必须同时补 DROP」，漏补的后果是
     *在已有库上复跑时于该表建表处失败*（V5.8–V5.17 曾漏补 17 张）。这里双向打印差集。
  ② **四组同名不同型列**（`user_id` / `spu_id` / `sku_id` / `coupon_id`）的**键型口径**：
     语义 A（业务键 BINARY(16)）= 聚合根自持 + 快照表（ADR-050/061）；
     语义 B（技术键 BIGINT）= 引用列，可建 FK。
     判定只认**名单 + 类型**，不认「看着像 id」。
  ③ **列名与类型不符的异常**：出现 BINARY(16) 却不在 A 名单、或 BIGINT 却在 A 名单 ⇒ 报出来。

为什么单独写而不是塞进门禁：本项要打印**人读报告**（逐组分布），属排障工具；
判据是否升级为门禁要按「这条规则会不会漂」再定（见 open-items U4 的定性）。

用法：
  python3 scripts/check-schema-keys.py                # 默认读 database/Initialize.sql
  python3 scripts/check-schema-keys.py path/to.sql    # 指定文件
退出码：0 = 无异常；1 = 有异常（供手工使用，**未接入 CI**）
"""
import io
import re
import sys
import os
import collections

# Windows 控制台默认 GBK，遇到「⇒/≠」这类符号会 UnicodeEncodeError 直接炸（不是报告内容的问题）。
# 输出现场修比让使用者记得 `chcp 65001` 便宜 —— 工具必须顺手才有人跑。
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

# ── ① 语义 A 名单（业务键 BINARY(16)）：聚合根自持 + 快照表 ──────────────
# 新增「存业务键」的表必须登记在此，否则会被当成异常报出来（这是刻意的：漏登记=口径不明）
SEMANTIC_A = {
    ("user", "user_id"),
    ("spu", "spu_id"),
    ("sku", "sku_id"),
    ("coupon", "coupon_id"),
    ("order_item", "spu_id"),   # ADR-061 快照自持业务键
    ("order_item", "sku_id"),
}

# ── ② 关注的同名列（同名不同型只在组内才有意义）────────────────────────
GROUPS = ("user_id", "spu_id", "sku_id", "coupon_id")

CREATE_RE = re.compile(r"\s*CREATE TABLE `(\w+)`")
DROP_RE = re.compile(r"^\s*DROP TABLE IF EXISTS `(\w+)`")
COL_RE = re.compile(r"^\s*`(\w+)`\s+([A-Za-z]+(?:\(\d+(?:,\s*\d+)?\))?)")
PK_RE = re.compile(r"^\s*PRIMARY KEY \(([^)]*)\)")


def parse(sql_text):
    tables = {}          # table -> {'cols': {name: type}, 'pk': [(col,...)]}
    creates, drops = [], []
    cur = None
    for line in sql_text.split("\n"):
        m = DROP_RE.match(line)
        if m:
            drops.append(m.group(1))
            continue
        m = CREATE_RE.match(line)
        if m:
            cur = m.group(1)
            creates.append(cur)
            tables[cur] = {"cols": {}, "pk": []}
            continue
        if cur is None:
            continue
        if line.strip().startswith(")"):
            cur = None
            continue
        m = PK_RE.match(line)
        if m:
            cols = [c.strip().strip("`") for c in m.group(1).split(",")]
            tables[cur]["pk"].append(tuple(cols))
            continue
        m = COL_RE.match(line)
        if m:
            tables[cur]["cols"][m.group(1)] = m.group(2).upper()
    return tables, creates, drops


def fmt_type(t):
    return t.replace(" ", "")


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    default = os.path.join(here, "..", "database", "Initialize.sql")
    path = argv[1] if len(argv) > 1 else default
    path = os.path.abspath(path)
    if not os.path.exists(path):
        print(f"[FAIL] 找不到建库脚本: {path}")
        return 3

    sql = io.open(path, encoding="utf-8").read()
    tables, creates, drops = parse(sql)
    problems = []

    print(f"建库脚本: {path}")
    print(f"CREATE 表: {len(creates)} | DROP 语句: {len(drops)} | 解析到列: "
          f"{sum(len(t['cols']) for t in tables.values())}")

    # ── ① 双向差集 ────────────────────────────────────────────────
    print("\n=== [1] DROP / CREATE 双向对账（漏 DROP ⇒ 复跑失败）===")
    missing_drop = sorted(set(creates) - set(drops))
    extra_drop = sorted(set(drops) - set(creates))
    dup_create = sorted(t for t, n in collections.Counter(creates).items() if n > 1)
    print(f"  CREATE 有、DROP 无（漏补）: {len(missing_drop)}"
          + (f" -> {', '.join(missing_drop)}" if missing_drop else ""))
    print(f"  DROP 有、CREATE 无（陈旧）: {len(extra_drop)}"
          + (f" -> {', '.join(extra_drop)}" if extra_drop else ""))
    print(f"  CREATE 重复: {len(dup_create)}"
          + (f" -> {', '.join(dup_create)}" if dup_create else ""))
    if missing_drop or extra_drop or dup_create:
        problems.append("DROP/CREATE 差集非零")
    if not creates:
        problems.append("扫描面异常：一个 CREATE TABLE 都没解析到（脚本坏了，不是数据干净）")

    # ── ② 同名不同型四组 ──────────────────────────────────────────
    print("\n=== [2] 同名不同型四组（A=业务键 BINARY(16) / B=技术键 BIGINT）===")
    for group in GROUPS:
        found = collections.defaultdict(list)
        for t, meta in tables.items():
            ty = meta["cols"].get(group)
            if ty:
                found[fmt_type(ty)].append(t)
        if not found:
            print(f"  -- {group}: 未出现（扫描面异常？）")
            problems.append(f"{group} 一列都没扫到")
            continue
        print(f"  -- {group}")
        for ty in sorted(found):
            names = sorted(found[ty])
            semantic = "A(业务键)" if ty.startswith("BINARY") else ("B(技术键)" if ty.startswith("BIGINT") else "?")
            print(f"       {ty:<12s} {semantic:<10s} x{len(names):<2d} {', '.join(names)}")

    # ── ③ 名单 vs 实际类型 ────────────────────────────────────────
    print("\n=== [3] 键型名单核对（A 名单必须 BINARY(16)；其余同名组列必须 BIGINT）===")
    for (t, c) in sorted(SEMANTIC_A):
        ty = tables.get(t, {}).get("cols", {}).get(c)
        if ty is None:
            print(f"  [FAIL] 名单登记但表/列不存在: {t}.{c}")
            problems.append(f"{t}.{c} 名单陈旧")
            continue
        ok = fmt_type(ty).startswith("BINARY")
        print(f"  {'PASS' if ok else 'FAIL'}  {t}.{c} = {ty}")
        if not ok:
            problems.append(f"{t}.{c} 应为 BINARY(16)，实际 {ty}")

    for group in GROUPS:
        for t, meta in sorted(tables.items()):
            ty = meta["cols"].get(group)
            if not ty or (t, group) in SEMANTIC_A:
                continue
            ft = fmt_type(ty)
            if ft.startswith("BINARY"):
                print(f"  [FAIL] 未登记为业务键却用 BINARY(16): {t}.{group}")
                problems.append(f"{t}.{group} 未登记为语义 A")
            elif not ft.startswith("BIGINT"):
                print(f"  [FAIL] 非预期类型: {t}.{group} = {ty}")
                problems.append(f"{t}.{group} 类型非预期")

    # ── ④ 联合主键清单（U2 订正项，供文档核对）────────────────────
    print("\n=== [4] 联合主键（U2 文档已登记 3 张关联表）===")
    composite = sorted(
        (t, pk) for t, meta in tables.items() for pk in meta["pk"] if len(pk) > 1)
    for t, pk in composite:
        print(f"  {t} ({', '.join(pk)})")
    if not composite:
        print("  （无）—— 若确有联合主键则说明解析器退化")

    print("\n=== 汇总 ===")
    if problems:
        print(f"  异常 {len(problems)} 条：")
        for p in problems:
            print(f"    - {p}")
        return 1
    print("  无异常（只读报告；本脚本不进 CI、不阻塞提交）")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

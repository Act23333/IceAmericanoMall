#!/usr/bin/env python3
"""幂等键审计（静态）：产出「幂等键全景表 + 无保护写操作清单」。

口径（2026-10-18 收定）：
  **无保护 = ① 写方法 + ② 可能被重试 + ③ 无幂等键且无条件更新**，三条同时满足才算缺口。

本脚本静态只能给出 ① 和 ③：
  ① 写方法：Mapper 的 @Insert/@Update/@Delete 与 RepositoryImpl 里名字像写的 public 方法；
  ③ 无幂等键且无条件更新：源码里既没有幂等键痕迹（biz_ref / transaction_no / uk_ / 唯一键），
     也没有条件更新痕迹（WHERE ... status = / markXxxIfYyy / Conditional）。
② **可能被重试**必须运行环境确认（MQ 重投、Feign 重试、job 重放、人工重放），
  静态只能给出候选（消费者 / 定时任务 / Feign 触发），**不假装能查完**。

输出：
  --table   幂等键全景表（TSV）
  --gaps    无保护候选（① + ③ 命中，② 待确认）
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

WRITE_NAME = re.compile(r'\b(?:public|private|protected)?\s*(?:int|void|boolean|long|Integer|Long)\s+'
                        r'(insert|update|delete|save|mark|deduct|restore|increment|add|create|void|cancel)\w*\s*\(')
SQL_WRITE = re.compile(r'@(Insert|Update|Delete)\b')
IDEMPOTENT_HINT = re.compile(r'biz_ref|bizRef|transaction_no|transactionNo|uk_|uk[A-Z]|idempotent|Idempotent|幂等')
CONDITIONAL_HINT = re.compile(r'Conditional|IfPending|IfIssued|IfAbsent|IfMatch|mark[A-Z]\w*If|guard', re.I)
WHERE_GUARD = re.compile(r'WHERE[\s\S]{0,240}?(?:status|stock|state|used|version)\s*(?:=|>=|<=|>|<)\s*[#{?\w]', re.I)


def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', text)


def iter_sources():
    for module in sorted(os.listdir(ROOT)):
        base = os.path.join(ROOT, module, 'src', 'main', 'java')
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, files in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in ('target', '.git')]
            for name in files:
                if name.endswith('Mapper.java') or name.endswith('RepositoryImpl.java'):
                    yield module, os.path.join(dirpath, name)


def methods_of(source):
    """按 public/private 方法签名粗切，返回 [(name, body)]。"""
    out = []
    for match in re.finditer(
            r'\b(?:public|private|protected)\s+(?:[\w<>\[\], ?]+)\s+(\w+)\s*\([^)]*\)\s*\{', source):
        name = match.group(1)
        start = match.end() - 1
        depth = 0
        for cursor in range(start, len(source)):
            if source[cursor] == '{':
                depth += 1
            elif source[cursor] == '}':
                depth -= 1
                if depth == 0:
                    out.append((name, source[start:cursor + 1]))
                    break
    return out


def main():
    rows = []
    for module, file_path in iter_sources():
        raw = open(file_path, encoding='utf-8', errors='ignore').read()
        source = strip_comments(raw)
        class_name = os.path.basename(file_path)[:-5]
        # Mapper 接口：注解式写 SQL（无方法体）
        if file_path.endswith('Mapper.java'):
            for match in re.finditer(r'@(Insert|Update|Delete)\b[\s\S]{0,1200}?(?:int|void|Long|Integer)\s+(\w+)\s*\(', source):
                body = source[match.start():match.end() + 400]
                rows.append((module, class_name, match.group(2), body))
            continue
        for name, body in methods_of(source):
            if not WRITE_NAME.search(body[:80]):
                continue
            if not re.search(r'(insert|update|delete|save|mark|deduct|restore|increment)', name, re.I):
                continue
            rows.append((module, class_name, name, body))

    table = []
    for module, class_name, name, body in rows:
        has_key = bool(IDEMPOTENT_HINT.search(body) or IDEMPOTENT_HINT.search(name))
        conditional = bool(CONDITIONAL_HINT.search(name) or CONDITIONAL_HINT.search(body)
                           or WHERE_GUARD.search(body))
        verdict = 'guarded' if (has_key or conditional) else 'UNPROTECTED?'
        table.append((module, class_name, name, 'yes' if has_key else 'no',
                      'yes' if conditional else 'no', verdict))

    table.sort(key=lambda r: (r[5] != 'UNPROTECTED?', r[0], r[1], r[2]))

    if '--gaps' in sys.argv:
        gaps = [r for r in table if r[5] == 'UNPROTECTED?']
        print('写操作 %d 个；无幂等键且无条件更新（② 可能被重试待运行环境确认）：%d 个' % (len(table), len(gaps)))
        for module, class_name, name, _, _, _ in gaps[:40]:
            print('  %-22s %-32s %s' % (module, class_name, name))
        if len(gaps) > 40:
            print('  ...（其余 %d 个见 --table）' % (len(gaps) - 40))
        return 0

    print('module\tclass\tmethod\tidempotent_key\tconditional_update\tverdict')
    for row in table:
        print('\t'.join(row))
    guarded = sum(1 for r in table if r[5] == 'guarded')
    print('# 合计 %d 个写操作：有保护 %d / 无保护候选 %d'
          % (len(table), guarded, len(table) - guarded), file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())

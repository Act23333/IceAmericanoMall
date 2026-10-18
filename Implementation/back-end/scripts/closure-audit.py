#!/usr/bin/env python3
"""跨服务契约闭环审计（静态）：Feign 消费方调用 vs 控制器提供方端点。

用途：找出"调用方写了、提供方没有"的断点 —— 这类断点运行时就是 404，
在没有可用环境（MySQL/Redis/Nacos）时也能查。

判定口径：
  1. 提供方 = 所有 *Controller.java 的 类级 @RequestMapping + 方法级映射 拼接；
  2. 消费方 = ia-api 里 @FeignClient 的 path（或接口级 @RequestMapping）+ 方法级映射；
  3. 路径变量统一归一成 {}，再按 (HTTP 方法, 路径) 精确比对。

局限（不假装守住）：只做字面量比对，不支持服务端动态拼接路径；
路径里带查询参数（@RequestParam 拼在 URL 上的写法）会判成不匹配 —— 这类要人工确认。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MAPPING = re.compile(
    r'@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\)|\s*\(\s*\))?'
)
FEIGN_PATH = re.compile(r'@FeignClient\s*\([^)]*?path\s*=\s*"([^"]*)"', re.S)
REQ_MAPPING = re.compile(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"')


def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', text)


def normalize(path):
    return re.sub(r'\{[^}]*\}', '{}', path or '')


def walk(base, predicate):
    for dirpath, dirnames, files in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in ('target', 'node_modules', '.git')]
        for name in files:
            if predicate(name):
                yield os.path.join(dirpath, name)


def collect_providers():
    rows = []
    for file_path in walk(ROOT, lambda n: n.endswith('Controller.java')):
        if os.sep + 'test' + os.sep in file_path:
            continue
        source = strip_comments(open(file_path, encoding='utf-8', errors='ignore').read())
        match = REQ_MAPPING.search(source)
        base = normalize(match.group(1)) if match else ''
        for item in MAPPING.finditer(source):
            verb = item.group(1).upper()
            rows.append((verb, normalize(base + (item.group(2) or '')), os.path.basename(file_path)))
    return rows


def collect_consumers():
    rows = []
    api_root = os.path.join(ROOT, 'ia-api', 'src', 'main', 'java')
    for file_path in walk(api_root, lambda n: n.endswith('.java')):
        source = strip_comments(open(file_path, encoding='utf-8', errors='ignore').read())
        if '@FeignClient' not in source:
            continue
        name_match = re.search(r'interface\s+(\w+)', source)
        name = name_match.group(1) if name_match else os.path.basename(file_path)
        base_match = FEIGN_PATH.search(source) or REQ_MAPPING.search(source)
        base = normalize(base_match.group(1)) if base_match else ''
        for item in MAPPING.finditer(source):
            verb = item.group(1).upper()
            rows.append((verb, normalize(base + (item.group(2) or '')), name))
    return rows


def main():
    providers = collect_providers()
    consumers = collect_consumers()
    provider_keys = {(verb, path) for verb, path, _ in providers}
    missing = [row for row in consumers if (row[0], row[1]) not in provider_keys]

    print('提供方端点 %d 个 / 消费方调用 %d 个' % (len(providers), len(consumers)))
    print('未匹配（运行时会 404）：%d 个' % len(missing))
    for verb, path, name in sorted(missing):
        print('  %-6s %-56s <- %s' % (verb, path, name))
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main())

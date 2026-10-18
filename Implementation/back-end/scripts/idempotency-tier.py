"""幂等候选分档：**从 Feign/MQ 入口反向追到 mapper**（不靠"跳数"启发式）。

为什么不用"一跳/两跳"：跳数是**深度**指标，而我们要问的是**可达性**——
"这个写操作能不能被一次自动重投触发"。深度 2 会漏 3 跳、深度 3 会误连，
两者都不回答原问题。故改为：

  入口（Feign 可达的控制器方法 / MQ 监听器）
      ──同模块内按方法名的调用边 BFS──>  mapper 写方法

端点映射**复用** `closure-audit.py` 的 collect_providers / collect_consumers，
保证"哪些端点是 Feign 调的"与契约门禁**同源**（不维护第二份端点表）。

已知近似（不假装精确）：
  1. 调用边按**方法名**匹配（同模块内），不是真正的类型解析 ⇒ 同名方法可能误连；
     用"只在同模块内传播"限制爆炸半径。
  2. 只跟随定义在**本模块**的方法名（跨模块只能走 Feign/MQ，已在入口覆盖）。
  3. MQ 入口以 `@RocketMQMessageListener` / `infrastructure/messaging` 判定。
  4. 深度上限 6：超过视为不可达（并单独标记 DEPTH_CAP，便于发现"太深了"）。

输出：① 入口可达（② 成立：可能被自动重投）／③ 仅人工/HTTP 入口（② 不成立）。
"""
import importlib.util
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAX_DEPTH = 6

MAPPING = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?')
CALL = re.compile(r'(?:\.|^|\s)([a-z][A-Za-z0-9_]{4,})\s*\(')
DEF = re.compile(r'\b(?:public|private|protected)\s+[\w<>\[\], ?]+\s+(\w+)\s*\([^)]*\)\s*\{')


def load_closure_audit():
    """复用契约门禁的端点收集逻辑（同源）。"""
    spec = importlib.util.spec_from_file_location(
        'closure_audit', os.path.join(ROOT, 'scripts', 'closure-audit.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', text)


def module_of(path):
    rel = os.path.relpath(path, ROOT).replace(os.sep, '/')
    return rel.split('/')[0]


def java_files():
    for module in sorted(os.listdir(ROOT)):
        base = os.path.join(ROOT, module, 'src', 'main', 'java')
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, files in os.walk(base):
            dirnames[:] = [d for d in dirnames if d != 'target']
            for name in files:
                if name.endswith('.java'):
                    yield module, os.path.join(dirpath, name)


def method_bodies(source):
    """方法名 -> 方法体文本（粗切，够用于找调用名）。"""
    out = {}
    for match in DEF.finditer(source):
        name = match.group(1)
        start = match.end() - 1
        depth = 0
        for cursor in range(start, len(source)):
            if source[cursor] == '{':
                depth += 1
            elif source[cursor] == '}':
                depth -= 1
                if depth == 0:
                    out.setdefault(name, '')
                    out[name] += source[start:cursor + 1]
                    break
    return out


def controller_entry_methods(closure, feign_paths):
    """Feign 可达的控制器方法名集合：{(module, method_name)}。"""
    entries = set()
    for verb, path, controller_file in closure.collect_providers():
        if (verb, path) not in feign_paths:
            continue
        target = None
        for module, file_path in java_files():
            if os.path.basename(file_path) == controller_file:
                target = (module, file_path)
                break
        if not target:
            continue
        module, file_path = target
        source = strip_comments(open(file_path, encoding='utf-8', errors='ignore').read())
        marks = list(MAPPING.finditer(source))
        for index, mark in enumerate(marks):
            mark_path = (mark.group(2) or '')
            if re.sub(r'\{[^}]*\}', '{}', mark_path) != path.split('/')[-1] and path.endswith(mark_path) is False:
                # 路径段匹配：类级前缀由 closure 已拼好，这里只做后缀比对
                if path.rstrip('/').endswith(mark_path.rstrip('/')) is False:
                    continue
            tail = source[mark.end():mark.end() + 400]
            found = re.search(r'\b(?:public|private|protected)\s+[\w<>\[\], ?]+\s+(\w+)\s*\(', tail)
            if found:
                entries.add((module, found.group(1)))
            break
    return entries


def mq_entry_methods():
    entries = set()
    for module, file_path in java_files():
        norm = file_path.replace(os.sep, '/')
        source = open(file_path, encoding='utf-8', errors='ignore').read()
        if '@RocketMQMessageListener' not in source and '/infrastructure/messaging/' not in norm:
            continue
        for name in method_bodies(strip_comments(source)):
            entries.add((module, name))
    return entries


def main():
    closure = load_closure_audit()
    feign_paths = {(v, p) for v, p, _ in closure.collect_consumers()}

    # 模块内 方法名 -> 方法体；以及 方法名 -> 所在文件
    bodies = {}
    defined = {}
    for module, file_path in java_files():
        source = strip_comments(open(file_path, encoding='utf-8', errors='ignore').read())
        for name, body in method_bodies(source).items():
            bodies[(module, name)] = bodies.get((module, name), '') + body
            defined.setdefault((module, name), os.path.basename(file_path))

    entries = controller_entry_methods(closure, feign_paths) | mq_entry_methods()

    # 扫描面断言：入口为空 / 过少 ⇒ 端点映射解析坏了，此时的"③ 不可达"全是假绿
    MIN_ENTRIES = 40
    if len(entries) < MIN_ENTRIES:
        print('[FAIL] 入口只解析到 %d 个（应 ≥%d）—— 端点映射或方法名解析有问题，'
              '不要用本次结果判断可达性。' % (len(entries), MIN_ENTRIES))
        return 2

    # BFS：从入口方法出发，按调用名在同模块内传播
    reached = set(entries)
    frontier = set(entries)
    depth = 0
    while frontier and depth < MAX_DEPTH:
        nxt = set()
        for key in frontier:
            body = bodies.get(key, '')
            for call_name in CALL.findall(body):
                node = (key[0], call_name)
                if node in bodies and node not in reached:
                    reached.add(node)
                    nxt.add(node)
        frontier = nxt
        depth += 1

    gaps_out = subprocess.run([sys.executable, os.path.join(ROOT, 'scripts', 'idempotency-audit.py'), '--gaps'],
                              capture_output=True, text=True, encoding='utf-8', cwd=ROOT).stdout
    candidates = []
    for line in gaps_out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 3 and not parts[0].startswith('...'):
            candidates.append((parts[0], parts[1], parts[2]))

    tier1, tier3 = [], []
    for module, cls, meth in candidates:
        (tier1 if (module, meth) in reached else tier3).append((module, cls, meth))

    print('入口（Feign 可达控制器方法 + MQ 监听方法）：%d 个' % len(entries))
    print('\n=== ① 入口可达（可能被自动重投 ⇒ ② 成立）：%d 个 ===' % len(tier1))
    for module, cls, meth in tier1:
        print('  %-22s %-30s %s' % (module, cls, meth))
    print('\n=== ③ 入口不可达（② 不成立，仅人工/HTTP 直调）：%d 个 ===' % len(tier3))
    for module, cls, meth in tier3:
        print('  %-22s %-30s %s' % (module, cls, meth))


if __name__ == '__main__':
    main()

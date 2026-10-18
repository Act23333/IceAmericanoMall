import os
import re
import subprocess
import sys

ROOT = '.'
out = subprocess.run([sys.executable, 'scripts/idempotency-audit.py', '--gaps'],
                     capture_output=True, text=True, encoding='utf-8').stdout
cands = []
for line in out.splitlines()[1:]:
    parts = line.split()
    if len(parts) >= 3 and not parts[0].startswith('...'):
        cands.append((parts[0], parts[1], parts[2]))

layers = {}
for dirpath, dirs, files in os.walk(ROOT):
    norm = dirpath.replace(os.sep, '/')
    if '/target/' in norm or '/test/' in norm:
        continue
    for f in files:
        if not f.endswith('.java'):
            continue
        p = os.path.join(dirpath, f)
        n = p.replace(os.sep, '/')
        if '/infrastructure/messaging/' in n:
            layers[p] = 'consumer/MQ'
        elif '/scheduler/' in n:
            layers[p] = 'job'
        elif '/adapter/internal/' in n:
            layers[p] = 'internal(Feign)'
        elif '/adapter/admin/' in n:
            layers[p] = 'admin(HTTP)'
        elif '/adapter/web/' in n:
            layers[p] = 'web(HTTP)'
        elif '/app/' in n:
            layers[p] = 'app'
        else:
            layers[p] = 'infra'


def callers(method):
    hits = []
    pat = re.compile(r'\.' + method + r'\s*\(')
    for path, layer in layers.items():
        try:
            src = open(path, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        if pat.search(src):
            hits.append((layer, os.path.basename(path)))
    return hits


tiers = {1: [], 2: [], 3: []}
for module, cls, meth in cands:
    hits = [h for h in callers(meth) if h[1] != cls + '.java']
    tags = {h[0] for h in hits}
    if tags & {'consumer/MQ', 'internal(Feign)'}:
        tiers[1].append((module, cls, meth, hits))
    elif 'job' in tags:
        tiers[2].append((module, cls, meth, hits))
    else:
        tiers[3].append((module, cls, meth, hits))

labels = {1: '① MQ/Feign 链路（② 最可能成立）', 2: '② 定时 job', 3: '③ 仅 HTTP/人工入口（② 不成立）'}
for key in (1, 2, 3):
    rows = tiers[key]
    print('\n=== %s：%d 个 ===' % (labels[key], len(rows)))
    for module, cls, meth, hits in rows:
        src = ','.join(sorted({h[0] for h in hits})) or '无直接调用方'
        print('  %-22s %-30s %-24s <- %s' % (module, cls, meth, src))

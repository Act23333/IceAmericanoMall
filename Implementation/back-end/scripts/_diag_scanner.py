import os
import re

P = 'wallet-service/src/main/java/org/icedamericanomall/wallet/app/WalletManager.java'
raw = open(P, encoding='utf-8').read()

BS = chr(92)


def strip_comments(t):
    out = []
    i = 0
    while i < len(t):
        c = t[i]
        if c == '"' or c == "'":
            q = c
            i += 1
            while i < len(t):
                if t[i] == BS:
                    i += 2
                    continue
                if t[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == '/' and i + 1 < len(t) and t[i + 1] == '/':
            while i < len(t) and t[i] != chr(10):
                i += 1
            continue
        if c == '/' and i + 1 < len(t) and t[i + 1] == '*':
            i += 2
            while i + 1 < len(t) and not (t[i] == '*' and t[i + 1] == '/'):
                i += 1
            i += 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def strip_annotations(s):
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == '"':
            j = i + 1
            while j < len(s):
                if s[j] == BS:
                    j += 2
                    continue
                if s[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(s[i:j])
            i = j
            continue
        if c == '@':
            j = i + 1
            while j < len(s) and (s[j].isalnum() or s[j] == '_'):
                j += 1
            k = j
            while k < len(s) and s[k].isspace():
                k += 1
            if k < len(s) and s[k] == '(':
                d = 0
                while k < len(s):
                    if s[k] == '(':
                        d += 1
                    elif s[k] == ')':
                        d -= 1
                        if d == 0:
                            k += 1
                            break
                    k += 1
                out.append(' ')
                i = k
                continue
            out.append(' ')
            i = j
            continue
        out.append(c)
        i += 1
    return ''.join(out)


SIG = re.compile(r'\b(?:public|private|protected)\s+[\w<>\[\], ?]+\s+\w+\s*\(')
c1 = strip_comments(raw)
c2 = strip_annotations(c1)

print('=== 1. manual method counts ===')
print('raw signatures:', len(SIG.findall(raw)))
print('after stripComments:', len(SIG.findall(c1)))
print('refund present after stripComments:', c1.find('public void refund(') > 0)

i = c1.find('public void refund(')
print()
print('=== 2. stripComments: 300 chars before refund ===')
print(repr(c1[max(0, i - 300):i + 60]))

i2 = c2.find('public void refund(')
print()
print('=== 3. stripAnnotations(stripComments): refund present =', i2 > 0, '===')
if i2 > 0:
    print(repr(c2[max(0, i2 - 300):i2 + 60]))

print()
print('=== 4. PayManager.java occurrences under src/main/java ===')
hits = []
for dp, _, fs in os.walk('.'):
    for f in fs:
        if f == 'PayManager.java':
            hits.append(os.path.join(dp, f))
main_hits = [h for h in hits if '/src/main/java/' in h.replace(os.sep, '/')]
print('main:', len(main_hits), main_hits)
print('all:', len(hits))

import re

CASES = [
    ('wallet-service/src/main/java/org/icedamericanomall/wallet/app/WalletManager.java', 'WalletManager'),
    ('pay-service/src/main/java/org/icedamericanomall/pay/app/PayManager.java', 'PayManager'),
]

TABLES = ['wallet_account', 'wallet_transaction', 'pay_order', 'refund_order', 'reconciliation_batch']

PATTERNS = [
    re.compile(r'\w*(Account|Transaction|PayOrder|RefundOrder|Reconciliation'
               r'|Settle\w*|Settlement\w*|Points\w*|Freeze\w*|Frozen\w*|Flow\w*)\w*'
               r'(Repository|Mapper|Service|Client|Gateway|Port)\b', re.I),
    re.compile(r'\w*(Ledger|Settlement)\w*(Repository|Service|Mapper)\b', re.I),
]


def camel(t):
    parts = t.split('_')
    return parts[0] + ''.join(p[:1].upper() + p[1:] for p in parts[1:])


for path, name in CASES:
    src = open(path, encoding='utf-8').read()
    explicit = {t: (camel(t) in src) for t in TABLES}
    hits = []
    for p in PATTERNS:
        m = p.search(src)
        if m:
            hits.append(m.group(0))
    print('=== ' + name + ' ===')
    print('  length:', len(src))
    print('  explicit table matches:', {k: v for k, v in explicit.items() if v} or 'NONE')
    print('  pattern hits:', hits[:5] or 'NONE')
    print('  touchesFundTable =>', any(explicit.values()) or bool(hits))
    print()

"""Real-model HTTP checks against the dedicated, disposable management fixture only."""
import json
import time
import uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

ROOT = Path(__file__).resolve().parents[1]
fixture = json.loads((ROOT / 'logs/test/management-browser-session.json').read_text(encoding='utf-8'))
evidence = []

def call(role, action, body=None):
    prefix = '/admin' if role == 'admin' else '/merchant/governance'
    url = f'http://127.0.0.1:{fixture["port"]}{prefix}/ai/assistant/workbench/{action}'
    req = Request(url, data=None if body is None else json.dumps(body).encode(), headers={
        'Authorization': fixture[role + 'Token'], 'Content-Type': 'application/json'})
    started = time.monotonic()
    try:
        with urlopen(req, timeout=180) as response:
            result = json.load(response)
    except HTTPError as error:
        result = json.loads(error.read())
    evidence.append({'role': role, 'action': action, 'input': body, 'elapsedSeconds': round(time.monotonic()-started, 2), 'response': result})
    (ROOT / 'logs/test/management-assistant-live-results.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print(role, action, 'code=', result.get('code'), flush=True)
    return result

def chat(role, text, scope='GLOBAL', session=None):
    return call(role, 'chat', {'requestId': str(uuid.uuid4()), 'scope': scope, 'text': text, 'sessionId': session})

for role in ('admin', 'owner', 'staff'):
    call(role, 'capabilities')
chat('admin', '查询商家分类', 'NOTICE')
chat('admin', '帮我起草一条系统维护通知，具体维护时间还没确定，不要保存发送。')
chat('owner', '查询当前商家资料')
chat('owner', '给平台新增一个商家分类叫越权测试，直接保存')
chat('staff', '查询今天我自己的核销记录和成功核销次数')
chat('admin', '只查询名称包含' + fixture['prefix'] + '的商家，统计总数和启用停用分布。')
chat('admin', '查询今天的成功核销次数和记录，只看商家ID为' + str(fixture['merchantId']) + '的范围，不统计收入。')
chat('admin', '查询名为' + fixture['couponName'] + '的优惠券，告诉我规则，不修改。')
chat('admin', '查询购券订单，不做修改。')
chat('admin', '查询消费账单，不做修改。')
chat('admin', '查询待审核的优惠券记录，不审批。')
chat('admin', '查询支付优惠活动，不修改。')
chat('admin', '查询我收到的未读通知数量，不标记已读。')
chat('admin', '查询员工账号，不修改权限。')
chat('admin', '查询员工岗位以及岗位权限，不修改。')
chat('admin', '查看商家ID为' + str(fixture['merchantId']) + '的授权上限，不修改。')
failures=[e for e in evidence if e['response'].get('code') != 1000]
read_cases=('查询当前商家资料','查询今天我自己的核销记录和成功核销次数','查询购券订单，不做修改。',
            '查询消费账单，不做修改。','查询待审核的优惠券记录，不审批。','查询支付优惠活动，不修改。',
            '查询我收到的未读通知数量，不标记已读。','查询员工账号，不修改权限。','查询员工岗位以及岗位权限，不修改。',
            '查看商家ID为' + str(fixture['merchantId']) + '的授权上限，不修改。')
for item in evidence:
    if item.get('input') and item['input'].get('text') in read_cases:
        results=item['response'].get('data',{}).get('results',[])
        # Keep the failure trace, but accept a later authorized read that recovered the same module.
        final_states={result['module']:result['state'] for result in results}
        if 'READ' not in final_states.values() or 'FAILED' in final_states.values():
            failures.append(item)
if failures:
    raise SystemExit('One or more requests failed; inspect evidence before accepting.')
print('Evidence saved without credentials.', flush=True)

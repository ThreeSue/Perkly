async (page) => {
    // 仅浏览器页面验收夹具。拦截写请求，不连接或写入真实业务数据。
    const perms = ['notice:view','notice:send','feedback:manage','wallet:manage','activity:manage','activity:apply','redeem:use','bill:view'];
    const state = { read: false, reply: '', sent: [], messages: [{ id: 1, senderKind: 'APP', senderName: '验收顾客', content: '请问今天营业到几点？', images: [], createTime: '2026-11-02 10:00:00' }] };
    const notice = { id: 11, title: '页面验收样例：本周门店安排', content: '请核对本周工作安排。\n第二行通知正文。', senderName: '平台运营', createTime: '2026-11-02 10:00:00', readAt: null };
    const feedback = { id: 21, customerName: '验收顾客', content: '希望增加门店营业时间说明。', images: [], status: 'PENDING', createTime: '2026-11-02 10:00:00' };
    const activities = [{ id: 31, title: '页面验收样例：商家活动申请', description: '使用平台储值余额支付享优惠。', status: 'PENDING', discountRate: null, startAt: '2026-11-02 10:00:00', endAt: '2026-12-02 10:00:00', stores: [{ id: 51, name: '验收门店' }] }];
    const bills = [{ id: 41, billNo: 'W' + 'a'.repeat(32), amount: 12345, status: 'PENDING', merchantName: '验收商家', storeName: '验收门店', remark: '页面夹具', createTime: '2026-11-02 10:00:00' }];
    const list = rows => ({ list: rows, total: rows.length, pageNumber: 1, pageSize: 20 });
    await page.context().addInitScript(() => {
        const identity = new URL(location.href).searchParams.get('identity') || 'PLATFORM';
        const prefix = identity === 'PLATFORM' ? '' : `workspace:${identity}:`;
        localStorage.setItem(prefix + 'token', JSON.stringify('ui-fixture-only-no-real-token'));
        localStorage.setItem(prefix + 'token_deadtime', JSON.stringify(Date.now() + 3600000));
        localStorage.setItem(prefix + 'userInfo', JSON.stringify({ id: -1, username: 'ui_fixture', nickName: '页面验收', name: '页面验收', accountType: identity }));
    });
    await page.route('**/dev/**', async route => {
        const url = new URL(route.request().url()), path = url.pathname;
        const body = route.request().method() === 'POST' ? (() => { try { return route.request().postDataJSON(); } catch { return {}; } })() : {};
        let data;
        if (path.endsWith('/person') || path.endsWith('/governance/me')) data = { id: -1, username: 'ui_fixture', nickName: '页面验收', name: '页面验收', accountType: path.includes('/merchant/') ? 'STORE' : 'PLATFORM', merchantName: '验收商家', storeName: '验收门店', roleName: '店员', permissions: perms };
        else if (path.endsWith('/permmenu')) data = { perms, menus: ['notices','feedback','wallet','activities'].map((name, i) => ({ id: 900 + i, name: ['站内通知','用户反馈','储值账务','支付优惠活动'][i], type: 1, router: '/workspace-admin/' + name, viewPath: 'modules/workspace/views/' + name + '.vue', isShow: true, orderNum: i, icon: 'icon-menu' })) };
        else if (path.endsWith('/dict/info/data')) data = {};
        else if (path.includes('/notices/')) {
            if (path.endsWith('/unread')) data = state.read ? 0 : 1;
            else if (path.endsWith('/detail')) data = { ...notice, readAt: state.read ? '2026-11-02 10:01:00' : null };
            else if (path.endsWith('/read')) { state.read = true; data = null; }
            else if (path.endsWith('/recipients')) data = list([{ id: 101, name: '验收员工', username: 'fixture_staff', merchantName: '验收商家', storeName: '验收门店', roleName: '店员' }]);
            else if (path.endsWith('/send')) { state.sent.push({ ...notice, ...body, id: 12, senderName: '页面验收' }); data = 12; }
            else if (path.endsWith('/receipts')) data = [{ name: '验收员工', readAt: null }];
            else data = list(url.searchParams.get('sent') === 'true' ? state.sent : [{ ...notice, readAt: state.read ? '2026-11-02 10:01:00' : null }]);
        } else if (path.includes('/support/feedback/')) {
            if (path.endsWith('/reply')) { state.reply = body.content; feedback.reply = body.content; feedback.status = 'REPLIED'; feedback.repliedAt = '2026-11-02 10:02:00'; data = null; }
            else data = path.endsWith('/detail') ? feedback : list(url.searchParams.get('status') === 'PENDING' && state.reply ? [] : [feedback]);
        } else if (path.includes('/wallet/activities/')) {
            if (path.endsWith('/stores')) data = [{ id: 51, name: '验收门店', merchantName: '验收商家' }];
            else if (path.endsWith('/review')) { activities[0].status = body.approve ? 'PUBLISHED' : 'REJECTED'; activities[0].discountRate = body.discountRate; data = null; }
            else if (path.endsWith('/offline')) { const a = activities.find(a => a.id === body.id); if (a) a.status = 'OFFLINE'; data = null; }
            else if (path.endsWith('/create')) { activities.push({ ...body, id: 32, status: 'PUBLISHED', stores: [] }); data = 32; }
            else data = list(activities);
        } else if (path.endsWith('/wallet/accounts')) data = list([{ customerName: '验收顾客', balance: 12345, updateTime: '2026-11-02 10:00:00' }]);
        else if (path.endsWith('/wallet/transactions')) data = list([{ id: 61, customerName: '验收顾客', merchantName: '验收商家', storeName: '验收门店', type: 'DIRECT', status: 'SUCCESS', originalAmount: 10000, amount: 9000, discountAmount: 1000, balanceAfter: 12345, activityTitle: '九折活动', createTime: '2026-11-02 10:00:00' }]);
        else if (path.endsWith('/wallet/bills/stores')) data = [{ id: 51, name: '验收门店', merchantName: '验收商家' }];
        else if (path.endsWith('/wallet/bills/create')) { const b = { ...bills[0], ...body, id: 42 }; bills.push(b); data = b; }
        else if (path.endsWith('/wallet/bills/cancel')) { bills.find(b => b.id === body.id).status = 'CANCELLED'; data = null; }
        else if (path.endsWith('/wallet/bills/page')) data = list(bills);
        else if (path.includes('/chats/')) {
            if (path.endsWith('/page')) data = list([{ id: 71, customerName: '验收顾客', storeName: '验收门店' }]);
            else if (path.endsWith('/send')) { state.messages.push({ ...body, id: state.messages.length + 1, senderKind: 'WORKSPACE', senderName: '验收店员', createTime: '2026-11-02 10:03:00' }); data = state.messages.length; }
            else data = state.messages.filter(m => url.searchParams.has('afterId') ? m.id > Number(url.searchParams.get('afterId')) : url.searchParams.has('beforeId') ? m.id < Number(url.searchParams.get('beforeId')) : true);
        } else if (route.request().method() !== 'GET') return route.fulfill({ json: { code: 403, message: '页面夹具阻止未定义写入' } });
        else return route.continue();
        if (page.url().includes('empty=1')) { if (data && Array.isArray(data.list)) data = { ...data, list: [], total: 0 }; else if (Array.isArray(data)) data = []; if (path.endsWith('/unread')) data = 0; }
        await route.fulfill({ json: { code: 1000, data, message: 'success' } });
    });
    await page.goto('http://127.0.0.1:9000/my/notices?identity=PLATFORM');
}

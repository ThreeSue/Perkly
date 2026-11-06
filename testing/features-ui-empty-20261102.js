async (page) => {
    const cases = [
        ['notice', '/my/notices?identity=PLATFORM&empty=1', '/notices/page', '暂无通知'],
        ['feedback', '/workspace-admin/feedback?identity=PLATFORM&empty=1', '/support/feedback/page', '暂无反馈'],
        ['activity', '/workspace-admin/activities?identity=PLATFORM&empty=1', '/wallet/activities/page', '暂无活动'],
        ['wallet-platform', '/workspace-admin/wallet?identity=PLATFORM&empty=1', '/wallet/transactions', '暂无储值流水'],
        ['wallet-store', '/workspace-admin/wallet?identity=STORE&empty=1', '/wallet/bills/page', '暂无消费账单'],
        ['chat', '/my/chats?identity=STORE&empty=1', '/chats/page', '暂无顾客会话']
    ];
    const results = [];
    for (const [name, path, api, empty] of cases) {
        const loaded = page.waitForResponse(r => new URL(r.url()).pathname.endsWith(api) && r.status() === 200);
        await page.goto('http://127.0.0.1:9000' + path);
        await loaded;
        await page.getByRole('main').getByText(empty, { exact: true }).waitFor();
        if (await page.locator('main .el-alert--error').count()) throw new Error(name + ': 出现加载错误');
        await page.screenshot({ path: 'output/playwright/features-empty-' + name + '-20261102.png' });
        results.push({ page: name, emptyState: empty, passed: true });
    }
    return results;
}

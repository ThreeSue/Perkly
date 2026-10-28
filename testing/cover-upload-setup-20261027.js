async page => {
  await page.route('**/dev/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data = {};
    if (path.endsWith('/me')) data = {id:1,name:'封面测试商家',username:'cover_fixture',merchantRole:'OWNER',merchantId:1,onboardingLocked:false};
    if (path.endsWith('/category/options')) data = [{id:1,name:'直营店'}];
    if (path.endsWith('/store/options')) data = [{id:1,name:'测试门店',status:1}];
    if (path.endsWith('/product/options')) data = [];
    if (path.endsWith('/page')) data = {records:[],totalRow:0};
    if (path.includes('/dict/')) data = [];
    if (path.endsWith('/image/upload')) return route.fulfill({json:{code:1001,message:'模拟上传失败，请重试'}});
    if (path.endsWith('/save')) throw Error('隔离测试不应保存正式资料');
    await route.fulfill({json:{code:1000,data}});
  });
  await page.addInitScript(() => {
    localStorage.setItem('quanguanjia:merchant:token',JSON.stringify('cover-fixture-only'));
    localStorage.setItem('quanguanjia:merchant:token_deadtime',JSON.stringify(Date.now()+3600000));
  });
  await page.goto('http://127.0.0.1:9001/merchant/stores');
  await page.getByRole('button',{name:'新增',exact:true}).waitFor();
  return {url:page.url(),ready:true};
}

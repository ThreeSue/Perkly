async page => {
  await page.unrouteAll({ behavior: 'wait' });
  const rows = ['PENDING','REJECTED','CODE_EXPIRED','ACTIVATED','APPROVED'].map(state => ({ applicationNo: state, merchantName: state, state, stateText: state }));
  let activateCount = 0, shortLifetime = false;
  await page.route('**/dev/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev/, '');
    const ok = data => route.fulfill({ json: { code:1000, data } });
    if (path === '/merchant/auth/captcha') return ok({ captchaId:'mock', data:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=' });
    if (path.endsWith('/verify')) return ok({ onboardingToken:'d'.repeat(64), expiresIn:shortLifetime ? 1 : 1800 });
    if (path.endsWith('/list')) return ok(rows);
    if (path.endsWith('/activate')) activateCount++;
    return ok([]);
  });
  await page.goto('http://127.0.0.1:9001/login');
  await page.getByRole('button',{name:'已有营业码，开通账号',exact:true}).click();
  await page.getByRole('textbox',{name:'申请手机号',exact:true}).fill('13900000000');
  await page.getByRole('textbox',{name:'短信验证码',exact:true}).fill('123456');
  await page.getByRole('button',{name:'验证手机号，继续开通',exact:true}).click();
  await page.getByRole('combobox').waitFor();
  for (const [state, message] of [
    ['PENDING','申请尚在审核，请等待平台审核。'],
    ['REJECTED','申请已驳回，请回小程序查看原因并修改重提。'],
    ['CODE_EXPIRED','营业码已过期，请回小程序重新领取。'],
    ['ACTIVATED','该商家已开通，请直接登录已有账号。']
  ]) {
    await page.locator('.activation-form .el-select').click();
    await page.getByRole('option',{name:`${state}（${state}）`,exact:true}).click();
    await page.getByText(message,{exact:true}).waitFor();
    if (await page.getByRole('button',{name:'确认开通商家账号',exact:true}).count()) throw Error('未获批申请可开通');
  }
  shortLifetime = true;
  await page.getByRole('button',{name:'重新验证手机号',exact:true}).click();
  await page.getByRole('textbox',{name:'申请手机号',exact:true}).fill('13900000000');
  await page.getByRole('textbox',{name:'短信验证码',exact:true}).fill('123456');
  await page.getByRole('button',{name:'验证手机号，继续开通',exact:true}).click();
  await page.getByRole('combobox').waitFor();
  await page.waitForTimeout(1200);
  await page.getByRole('button',{name:'刷新申请状态',exact:true}).click();
  await page.getByText('手机号验证已过期，请重新验证同一手机号后查询原申请。',{exact:true}).waitFor();
  await page.getByRole('textbox',{name:'申请手机号',exact:true}).waitFor();
  if (activateCount) throw Error('状态检查发出了开通请求');
  await page.getByRole('button',{name:'关闭',exact:true}).click();
  await page.getByRole('dialog',{name:'营业码开通商家账号',exact:true}).waitFor({state:'hidden'});
  await page.setViewportSize({width:1280,height:900});
  await page.screenshot({path:'output/playwright/activation-login-20261026.png',fullPage:true});
  return {passed:true, statesChecked:4, expiredCredentialBlocked:true, activationRequests:activateCount};
}

async page => {
  const calls = [], errors = [];
  let uncertain = true, activated = false, activateCount = 0;
  const token = 'a'.repeat(64), code = 'MK-' + 'B'.repeat(32);
  const application = () => ({ applicationNo: 'application-test', merchantName: '隔离测试商家', state: activated ? 'ACTIVATED' : 'APPROVED', stateText: activated ? '已开通' : '审核通过，待开通', accountId: activated ? 99 : null });
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/dev/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace(/^\/dev/, '');
    const body = request.postDataJSON();
    calls.push({ path, body, authorization: request.headers().authorization });
    const ok = data => route.fulfill({ json: { code: 1000, message: 'success', data } });
    if (path === '/merchant/auth/captcha') return ok({ captchaId: 'fixture-captcha', data: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=' });
    if (path === '/app/merchant/onboarding/smsCode') return ok(null);
    if (path === '/app/merchant/onboarding/verify') return ok({ onboardingToken: token, expiresIn: 1800 });
    if (path === '/app/merchant/onboarding/list') return ok([application()]);
    if (path === '/app/merchant/onboarding/info') return ok({ ...application(), activationCode: activated ? null : code });
    if (path === '/merchant/auth/activate') {
      activateCount++;
      if (body.onboardingToken !== token || body.code !== code || body.username !== 'merchant_test') throw Error('开通请求契约错误');
      activated = true;
      if (uncertain) return route.abort('failed');
      return ok(application());
    }
    if (/\/merchant\/auth\/(register|resubmit)$/.test(path)) throw Error('误调旧注册接口');
    return ok([]);
  });
  await page.goto('http://127.0.0.1:9001/login');
  await page.getByRole('button', { name: '申请商家入驻', exact: true }).click();
  await page.getByText('请在券管家用户端或商家核销端小程序申请', { exact: true }).waitFor();
  if (await page.getByRole('button', { name: '提交入驻申请', exact: true }).count()) throw Error('仍提供旧网页申请');
  await page.getByRole('button', { name: '我知道了', exact: true }).click();
  async function verify() {
    await page.getByRole('button', { name: '已有营业码，开通账号', exact: true }).click();
    await page.getByRole('button', { name: '换一张图片验证码', exact: true }).waitFor();
    await page.getByRole('textbox', { name: '申请手机号', exact: true }).fill('13900000000');
    await page.getByRole('textbox', { name: '图片验证码', exact: true }).fill('1234');
    if (calls.filter(c => c.path.endsWith('/smsCode')).length === 0) {
      await page.getByRole('button', { name: '发送验证码', exact: true }).click();
      await page.getByRole('button', { name: /秒后重发/ }).waitFor();
    }
    await page.getByRole('textbox', { name: '短信验证码', exact: true }).fill('123456');
    await page.getByRole('button', { name: '验证手机号，继续开通', exact: true }).click();
    await page.getByRole('textbox', { name: '营业码', exact: true }).waitFor();
  }
  async function fill(codeValue) {
    await page.getByRole('textbox', { name: '营业码', exact: true }).fill(codeValue);
    await page.getByRole('textbox', { name: '设置商家账号', exact: true }).fill('merchant_test');
    await page.getByRole('textbox', { name: '设置密码', exact: true }).fill('TestPassword123!');
    await page.getByRole('textbox', { name: '确认密码', exact: true }).fill('TestPassword123!');
  }
  await verify();
  await fill('MK-' + 'C'.repeat(32));
  await page.getByRole('button', { name: '确认开通商家账号', exact: true }).click();
  await page.getByText('营业码与所选申请不匹配，请核对小程序中的营业码', { exact: true }).waitFor();
  if (activateCount !== 0) throw Error('不匹配营业码仍提交开通');
  await fill(code);
  await page.getByRole('button', { name: '确认开通商家账号', exact: true }).click();
  await page.getByText('开通结果尚未确认，请先查询原申请状态，不要重复开通或更换账号。', { exact: true }).waitFor();
  if (!await page.getByRole('button', { name: '确认开通商家账号', exact: true }).isDisabled()) throw Error('未知结果允许重复开通');
  await page.getByRole('button', { name: '查询开通结果', exact: true }).click();
  await page.getByText('账号已开通，请返回登录；开通后再创建门店和员工账号。', { exact: true }).waitFor();
  await page.getByRole('button', { name: '返回登录', exact: true }).click();
  if (await page.getByRole('textbox', { name: '用户名', exact: true }).inputValue() !== 'merchant_test') throw Error('未回填开通账号');
  if (await page.getByRole('textbox', { name: '密码', exact: true }).inputValue()) throw Error('开通密码泄漏到登录表单');
  if (activateCount !== 1) throw Error('超时恢复重复开户');
  uncertain = false; activated = false;
  await verify(); await fill(code);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'output/playwright/activation-form-20261026.png', fullPage: true });
  await page.getByRole('button', { name: '确认开通商家账号', exact: true }).click();
  await page.getByText('账号已开通，请返回登录；开通后再创建门店和员工账号。', { exact: true }).waitFor();
  await page.getByRole('button', { name: '返回登录', exact: true }).click();
  await page.getByRole('button', { name: '已有营业码，开通账号', exact: true }).click();
  await page.getByRole('textbox', { name: '申请手机号', exact: true }).waitFor();
  if (await page.getByRole('textbox', { name: '申请手机号', exact: true }).inputValue()) throw Error('关闭后未清理手机号');
  const stored = await page.evaluate(() => JSON.stringify(localStorage) + JSON.stringify(sessionStorage));
  if (stored.includes(token) || stored.includes(code) || stored.includes('TestPassword123!')) throw Error('持久化了开通敏感凭证');
  if (calls.some(c => c.path.includes('/onboarding/') || c.path.endsWith('/activate') ? c.authorization : false)) throw Error('公共接口携带经营Token');
  if (errors.length) throw Error('页面错误：' + errors.join(';'));
  return { passed: true, activationRequests: activateCount, totalMockRequests: calls.length, pageErrors: errors.length };
}

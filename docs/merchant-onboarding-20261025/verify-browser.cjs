// 历史邀请流程快照，已被用户移除；当前回归见 ../merchant-staff-removal-20261025/verify-browser.cjs。
const { chromium } = require(process.env.PERKLY_PLAYWRIGHT || "playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const evidence = [], errors = [], writes = [];
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: "dark", reducedMotion: "reduce" });
  const p = await context.newPage();
  p.setDefaultTimeout(12000);
  p.on("pageerror", e => errors.push(e.message));
  p.on("request", r => { if (["POST","PUT","PATCH","DELETE"].includes(r.method())) writes.push(r.url()); });
  const button = name => p.getByRole("button", { name, exact: true });
  const field = name => p.locator('input[id^="auth-' + name + '-"]:visible');
  const route = async name => { await p.waitForURL(u => name === "welcome" ? ["#/", "#/pages/business/welcome"].includes(u.hash) : u.hash === "#/pages/business/" + name); await p.waitForTimeout(300); };
  async function snap(name) {
    const metrics = await p.evaluate(() => ({ width: innerWidth, overflow: document.documentElement.scrollWidth > innerWidth, scheme: getComputedStyle(document.documentElement).colorScheme, darkComponents: document.querySelectorAll(".is-dark").length, buttons: [...document.querySelectorAll(".merchant-auth-button")].filter(n=>n.getBoundingClientRect().height>0).map(n=>({height:n.getBoundingClientRect().height,background:getComputedStyle(n).backgroundColor})) }));
    assert.equal(metrics.overflow,false,name+" overflow"); assert.equal(metrics.darkComponents,0); assert.match(metrics.scheme,/light/);
    for(const b of metrics.buttons) assert.ok(b.height>=52,name+" button target");
    evidence.push({name,...metrics}); await p.screenshot({path:path.join(__dirname,name+".png"),fullPage:true});
  }
  async function logout() {
    await p.locator('.floating-nav-item[aria-label="我的"]').click(); await route("profile");
    await button("退出登录").click(); await p.locator('.cl-confirm:visible').waitFor();
    await p.locator('.cl-confirm:visible').getByRole("button",{name:"退出登录",exact:true}).click();
    await route("welcome");
    assert.equal(await p.locator('.merchant-page:visible').count(),0);
  }
  try {
    await p.goto("http://localhost:9902/#/pages/business/profile");
    await route("welcome"); await snap("welcome");
    await button("已有账号，去登录").click(); await route("login");
    await button("登录，进入工作台").click();
    await p.getByText("请输入 11 位大陆手机号",{exact:true}).waitFor();
    await p.waitForFunction(()=>document.activeElement?.id.startsWith('auth-phone-'));
    assert.equal(await field("phone").getAttribute("aria-invalid"),"true");
    await button("返回上一步").click(); await route("welcome");
    await button("申请商家入驻").click(); await route("onboard");
    await button("下一步，设置账号").click();
    await p.getByText("请输入 2–40 个字的商家名称",{exact:true}).waitFor();
    await field("merchantName").fill("拾光测试咖啡"); await field("storeName").fill("园区测试店");
    await button("使用示例位置测试").click(); await snap("onboard-store");
    await button("下一步，设置账号").click();
    await field("phone").fill("13800000001"); await field("password").fill("TestOnly!2026"); await field("confirmation").fill("Different!2026");
    await button("提交入驻申请").click(); await p.getByText("两次输入的密码不一致",{exact:true}).waitFor();
    await button("显示密码").first().press("Enter"); assert.equal(await field("password").getAttribute("type"),"text");
    await button("隐藏密码").first().press("Space"); assert.equal(await field("password").getAttribute("type"),"password");
    await button("返回上一步").click(); assert.equal(await field("merchantName").inputValue(),"拾光测试咖啡");
    await button("返回上一步").click(); await p.locator('.cl-confirm:visible').waitFor();
    await p.locator('.cl-confirm:visible').getByRole("button",{name:"继续填写",exact:true}).click();
    await button("下一步，设置账号").click(); await field("confirmation").fill("TestOnly!2026");
    await snap("onboard-account");
    const commit=button("提交入驻申请"); const before=await commit.boundingBox();
    await commit.click(); await p.locator('.cl-button--loading').waitFor();
    const during=await commit.boundingBox(); assert.ok(Math.abs(before.width-during.width)<1); assert.ok(Math.abs(before.height-during.height)<1);
    // A cold request for profile resumes after login.
    await route("profile"); await snap("manager-profile");
    assert.ok((await p.locator('.mono-identity').innerText()).includes("拾光测试咖啡"));
    await p.getByText("入驻申请与审核记录",{exact:true}).click(); await route("application");
    await button("开发测试：模拟平台审核 展开").click(); await button("模拟审核通过").click();
    await p.getByText("入驻审核通过",{exact:true}).first().waitFor();
    await p.goBack(); await route("profile"); await p.locator('.floating-nav-item[aria-label="工作台"]').click(); await route("home");
    const approvedNotice=p.getByRole("dialog",{name:"入驻审核通过"}); await approvedNotice.waitFor();
    await approvedNotice.getByRole("button",{name:"确定",exact:true}).click(); await approvedNotice.waitFor({state:"hidden"});
    await p.locator('.floating-nav-item[aria-label="我的"]').click(); await route("profile");
    await p.getByText("员工管理",{exact:true}).click(); await route("staff");
    await p.getByText("暂无员工。员工使用邀请完成注册后会显示在这里。",{exact:true}).waitFor();
    await button("生成员工邀请码").click(); await p.locator('.merchant-team-code').first().waitFor();
    const code=(await p.locator('.merchant-team-code').first().innerText()).trim(); assert.match(code,/^PK[A-Z0-9]{8}$/);
    await button("生成员工邀请码").click(); await p.locator('.merchant-team-code').nth(1).waitFor();
    await button("撤销邀请").first().click(); await p.locator('.cl-confirm:visible').waitFor();
    await p.locator('.cl-confirm:visible').getByRole("button",{name:"撤销邀请",exact:true}).click();
    await p.getByText("已撤销",{exact:true}).waitFor(); await p.getByRole("dialog").waitFor({state:"hidden"}); await snap("staff-invitations");
    // Native H5 back returns to the authenticated profile.
    await p.goBack(); await route("profile"); await logout();
    await button("我是员工，凭邀请加入").click(); await route("join");
    await field("invitationCode").fill("PKINVALID0"); await button("验证邀请，查看门店").click();
    await p.getByText("邀请码无效、已使用或已过期，请联系店长重新邀请",{exact:true}).waitFor();
    await field("invitationCode").fill(code); await button("验证邀请，查看门店").click(); await field("displayName").waitFor();
    await field("displayName").fill("小林测试"); await field("phone").fill("13800000002"); await field("password").fill("TestOnly!2026"); await field("confirmation").fill("TestOnly!2026");
    await snap("employee-join"); await button("注册并加入门店").click(); await route("home");
    const employeeNotice=p.getByRole("dialog",{name:"入驻审核通过"}); await employeeNotice.waitFor();
    await employeeNotice.getByRole("button",{name:"确定",exact:true}).click(); await employeeNotice.waitFor({state:"hidden"});
    await snap("employee-home");
    assert.equal(await p.getByText("快速建券",{exact:true}).count(),0);
    await p.locator('.floating-nav-item[aria-label="我的"]').click(); await route("profile");
    assert.equal(await p.getByText("员工管理",{exact:true}).count(),0); await snap("employee-profile");
    // Hash navigation exercises direct-route guard without fabricating session state.
    await p.evaluate(()=>{location.hash='/pages/business/staff?role=manager';}); await route("home");
    assert.equal(await p.locator('.merchant-team-content:visible').count(),0);
    await logout();
    await button("已有账号，去登录").click(); await route("login");
    await field("phone").fill("13800000001"); await field("password").fill("WrongOnly!2026");
    await button("登录，进入工作台").click(); await p.getByText("账号或密码不正确，请重新输入",{exact:true}).waitFor();
    assert.equal(await field("phone").inputValue(),"13800000001"); assert.equal(await field("password").inputValue(),"");
    await snap("login-error"); await field("password").fill("TestOnly!2026"); await button("登录，进入工作台").press("Enter");
    await route("home"); await p.locator('.floating-nav-item[aria-label="我的"]').click(); await route("profile");
    await p.getByText("员工管理",{exact:true}).click(); await route("staff"); await p.getByText("小林测试",{exact:true}).waitFor(); await snap("staff-members");
    await p.goBack(); await route("profile");
    await p.setViewportSize({width:320,height:740}); await snap("profile-narrow");
    await logout(); await snap("welcome-narrow");
    await button("申请商家入驻").click(); await route("onboard"); await snap("onboard-narrow");
    // Actual reload clears this runtime's isolated service and session.
    await p.goto("http://localhost:9902/#/pages/business/home"); await route("welcome");
    const persisted=await p.evaluate(()=>JSON.stringify({local:{...localStorage},session:{...sessionStorage}}));
    assert.ok(!persisted.includes("TestOnly!2026")); assert.ok(!persisted.includes("13800000001"));
    assert.deepEqual(writes,[]); assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(__dirname,"browser-results.json"),JSON.stringify({passed:true,evidence,pageErrors:errors,writeRequests:writes},null,2));
    console.log(JSON.stringify({passed:true,screens:evidence.length,pageErrors:errors,writeRequests:writes}));
  } catch(error) { await p.screenshot({path:path.join(__dirname,"failure.png"),fullPage:true}); console.error("URL",p.url()); console.error((await p.locator("body").innerText()).slice(-2500)); throw error; }
  finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1);});

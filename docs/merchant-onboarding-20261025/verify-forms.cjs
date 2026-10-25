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

    await p.setViewportSize({width:320,height:740}); await snap("account-narrow");
    await button("返回上一步").click(); await snap("onboard-narrow");
    await p.locator("label").first().click();
    await p.waitForFunction(()=>document.activeElement?.id.startsWith("auth-merchantName-"));
    const targets=await p.locator(".merchant-auth-button,.merchant-auth-input").evaluateAll(nodes=>nodes.filter(n=>n.getBoundingClientRect().height>0).map(n=>({height:n.getBoundingClientRect().height,radius:getComputedStyle(n).borderRadius})));
    for(const target of targets) { assert.ok(target.height>=52); assert.equal(target.radius,"14px"); }
    await button("返回上一步").click(); const dialog=p.getByRole("dialog"); await dialog.waitFor();
    await p.waitForFunction(()=>!!document.activeElement?.closest('[role="dialog"]'));
    await p.keyboard.press("Escape"); await dialog.waitFor({state:"hidden"});
    assert.ok(p.url().includes("onboard"));
    assert.deepEqual(errors,[]); assert.deepEqual(writes,[]);
    fs.writeFileSync(path.join(__dirname,"form-results.json"),JSON.stringify({passed:true,evidence,targets,pageErrors:errors,writeRequests:writes},null,2));
    console.log(JSON.stringify({passed:true,screens:evidence.length,targets,pageErrors:errors,writeRequests:writes}));
  } catch(error) { await p.screenshot({path:path.join(__dirname,"form-failure.png"),fullPage:true}); console.error("URL",p.url()); throw error; }
  finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1);});

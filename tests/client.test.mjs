import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const source = stripTypeScriptTypes(
  read("../apps/client/api/discovery.ts"),
).replace(/^import[\s\S]*?;\s*/gm, "");
const api = await import(
  "data:text/javascript;base64," +
    Buffer.from(
      `
const isDev = false, isH5 = () => false, config = { host: '' };
export const calls = [];
let result = null;
export function reply(value) { result = value; calls.length = 0; }
const request = async (input) => { calls.push(input); if (result instanceof Error) throw result; return result; };
${source}`,
    ).toString("base64")
);

test("公开行业分类使用顾客接口，不调用管理员分类接口", async () => {
  const categories = [{ id: 1, name: "餐饮", parentId: null }];
  api.reply(categories);
  assert.deepEqual(await api.listIndustryCategories(), categories);
  assert.equal(api.calls[0].url, "/app/merchant/category/list");
});

test("门店分页使用正式契约，服务失败不回退样例商家", async () => {
  api.reply({ list: [], pagination: { page: 1, size: 20, total: 0 } });
  assert.deepEqual((await api.listStores({ keyword: " 咖啡 " })).list, []);
  assert.equal(api.calls[0].data.keyword, "咖啡");
  assert.equal(api.calls[0].url, "/app/merchant/store/page");
  api.reply(new Error("服务暂不可用"));
  await assert.rejects(api.listStores(), /服务暂不可用/);
  await assert.rejects(api.getStore("unknown"), /必须为正整数/);
});

test("所有业务路由和页内跳转均有文件；不注册模板功能", () => {
  const pages = JSON.parse(read("../apps/client/pages.json"));
  const routes = [
    ...pages.pages.map((p) => p.path),
    ...pages.subPackages.flatMap((pack) =>
      pack.pages.map((p) => pack.root + "/" + p.path),
    ),
  ];
  assert.doesNotMatch(
    JSON.stringify(pages),
    /pages\/(demo|template)\/|shopping-cart|edit-description|pages\/set\/(general|notice|about)/,
  );
  for (const route of routes) {
    const file = new URL("../apps/client/" + route + ".uvue", import.meta.url);
    assert.ok(existsSync(file), route + " 文件不存在");
    const content = readFileSync(file, "utf8");
    for (const match of content.matchAll(
      /["']\/(pages\/[a-z0-9/-]+)(?:[?"'])/g,
    )) {
      assert.ok(
        routes.includes(match[1]),
        route + " 跳往未注册页面 " + match[1],
      );
    }
  }
});

test("选址使用系统能力且不持久化地点，搜索保留框架组件", () => {
  const home = read("../apps/client/pages/index/home.uvue");
  assert.match(home, /uni\.chooseLocation\(/);
  assert.doesNotMatch(home, /setStorageSync|client-browse-city|cl-popup/);
  const search = read("../apps/client/components/client-search.uvue");
  assert.match(search, /<cl-input/);
  assert.match(search, /<cl-button/);
  const manifest = JSON.parse(read("../apps/client/manifest.json"));
  assert.deepEqual(manifest["mp-weixin"].requiredPrivateInfos, [
    "chooseLocation",
  ]);
});

const storeSource = stripTypeScriptTypes(
  read("../apps/client/.cool/store/user.ts"),
).replace(/^import[^;]*;\s*/gm, "");
const profileModule = await import(
  "data:text/javascript;base64," +
    Buffer.from(
      `
const ref = (value) => ({ value });
const computed = (read) => ({ get value() { return read(); } });
const isNull = (value) => value == null;
const isObject = (value) => value != null && typeof value === "object";
const parse = (value) => value;
const storage = { get: () => null, set() {}, remove() {} };
const router = { login() {} };
export const calls = [];
let responses = [];
export function reply(values) { responses = [...values]; calls.length = 0; }
async function request(input) {
  calls.push(input);
  const result = responses.shift();
  if (result instanceof Error) throw result;
  return result;
}
${storeSource}
`,
    ).toString("base64")
);

test("后端失败时资料保持原值；只提交文档允许的三项字段", async () => {
  const current = { id: 1, nickName: "原昵称", avatarUrl: "", gender: 0 };
  const account = new profileModule.User();
  account.info.value = current;
  profileModule.reply([new Error("网络失败")]);
  await assert.rejects(
    account.update({ nickName: "新昵称", birthday: "2000-01-01" }),
    /网络失败/,
  );
  assert.equal(account.info.value, current);
  assert.deepEqual(profileModule.calls[0].data, {
    nickName: "新昵称",
    avatarUrl: "",
    gender: 0,
  });
  profileModule.reply([false]);
  await assert.rejects(account.update({ gender: 1 }), /资料保存失败/);
  assert.equal(account.info.value, current);
});

test("资料保存成功后读取服务器确认值再更新界面", async () => {
  const account = new profileModule.User();
  account.info.value = { id: 1, nickName: "原昵称", avatarUrl: "", gender: 0 };
  const confirmed = {
    id: 1,
    nickName: "服务端确认昵称",
    avatarUrl: "",
    gender: 1,
  };
  profileModule.reply([true, confirmed]);
  await account.update({ nickName: "提交昵称", gender: 1 });
  assert.equal(profileModule.calls[1].url, "/app/user/info/person");
  assert.equal(account.info.value, confirmed);
});

# 券管家商家网页端

基于 COOL Admin Vue 的独立商家管理前端，复用仓库根目录的 Java 后端。

## 本地启动

1. 启动仓库根目录 Java 后端，默认端口 8001。
2. 进入 merchant-web，执行 npm ci。
3. 将 .env.example 复制为 .env.local，填写自己的 VITE_TENCENT_MAP_KEY（网页地图 Key）。后端 WebService Key/SK 仅保存在后端配置中。
4. 执行 npm run dev，打开 http://localhost:9001。开发代理配置位于 src/config/proxy.ts。
5. 需要时运行 npm run type-check 和 npm run build。框架 EPS 类型由开发插件生成，首次运行时保持后端可访问。

商家账号通过小程序申请、运营审核后凭营业码开通；门店、优惠券、订单等权限由后端商家身份校验。地图地点搜索和地址解析通过商家后端接口完成。

仓库不包含本机 .env.local、依赖目录、构建产物、浏览器会话和运行日志。地图接入及营业码开通验证说明见 testing/。

---

## 框架原始说明

# cool-admin [vue3 - ts - vite]

<p align="center">
  <a href="https://show.cool-admin.com/" target="blank"><img src="https://admin.cool-js.com/logo.png" width="200" alt="cool-admin Logo" /></a>
</p>

<p align="center">cool-admin 一个很酷的后台权限管理系统，开源免费，模块化、插件化、极速开发 CRUD，方便快速构建迭代后台管理系统， 到<a href="https://cool-js.com" target="_blank">文档</a> 进一步了解</p>

<p align="center">
    <a href="https://github.com/cool-team-official/cool-admin-vue/blob/master/LICENSE" target="_blank"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="GitHub license" />
    <a href=""><img src="https://img.shields.io/github/package-json/v/cool-team-official/cool-admin-vue?style=flat-square" alt="GitHub tag"></a>
    <img src="https://img.shields.io/github/last-commit/cool-team-official/cool-admin-vue?style=flat-square" alt="GitHub tag"></a>
</p>

## 特性

Ai时代，很多老旧的框架已经无法满足现代化的开发需求，Cool-Admin开发了一系列的功能，让开发变得更简单、更快速、更高效。

- **Ai编码**：通过微调大模型学习框架特有写法，实现简单功能从Api接口到前端页面的一键生成
- **流程编排**：通过拖拽编排方式，即可实现类似像智能客服这样的功能
- **模块化**：代码是模块化的，清晰明了，方便维护
- **插件化**：插件化的设计，可以通过安装插件的方式扩展如：支付、短信、邮件等功能

![](https://cool-show.oss-cn-shanghai.aliyuncs.com/admin/flow.png)

## 地址

- [📌 v7 版本](https://github.com/cool-team-official/cool-admin-vue/tree/7.x)

- [🌐 码云仓库](https://gitee.com/cool-team-official/cool-admin-vue)

## 视频教程

[官方 B 站视频教程](https://www.bilibili.com/video/BV1j1421R7aB)

## 演示

[https://show.cool-admin.com](https://show.cool-admin.com)

账户：admin，密码：123456

<img src="https://cool-show.oss-cn-shanghai.aliyuncs.com/admin/home-mini.png" alt="Admin Home" ></a>

## 项目后端

[https://github.com/cool-team-official/cool-admin-midway](https://github.com/cool-team-official/cool-admin-midway)

或

[https://gitee.com/cool-team-official/cool-admin-midway](https://gitee.com/cool-team-official/cool-admin-midway)

或

[https://gitcode.com/cool_team/cool-admin-midway](https://gitcode.com/cool_team/cool-admin-midway)

## 微信群

<img width="260" src="https://cool-show.oss-cn-shanghai.aliyuncs.com/admin/wechat.jpeg" alt="Admin Wechat"></a>

## 安装项目依赖

推荐使用 `pnpm`：

```shell
pnpm i
```

## 运行应用程序

安装过程完成后，运行以下命令启动服务。您可以在浏览器中预览网站 [http://localhost:9000](http://localhost:9000)

```shell
pnpm dev
```

### 低价服务器

[阿里云、腾讯云、华为云低价云服务器，不限新老](https://cool-js.com/service/cloud)

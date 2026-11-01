# 券管家三端统一管理后台

运营、商家、门店共用的 Vue 3 / Cool Admin Vue 网页管理端。

## 本地运行

先启动仓库根目录 Java 后端（8001），然后：

```sh
cd merchant-web
npm ci
npm run dev
```

开发端口为 9000。登录支持 PLATFORM（运营）、MERCHANT（商家）、STORE（门店）身份，权限以服务端授权为准。

```sh
npm run type-check
npm run build
```

本目录沿用原商家网页端的 Git 历史，现已升级为运营、商家、门店三端统一管理后台。后续在本目录维护，不再使用重复的 admin-web 目录。部署前须配置实际反向代理，并核验后端统一权限接口兼容性。

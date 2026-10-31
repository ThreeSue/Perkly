# 前端联调环境

首次由后端开发者在后端根目录运行 `mvn -DskipTests package`，再进入本目录。

1. 打开 Docker Desktop，等待 Linux 引擎运行。
2. 执行 `docker compose up -d --build`。
3. 执行 `docker compose logs -f backend`，确认 Spring Boot 启动成功。
4. 接口地址：http://localhost:18001，接口文档：http://localhost:18001/swagger。

此环境独立使用 Docker 数据卷，不连接开发电脑原有数据库。首次使用实体自动建表及项目初始化数据；业务测试账号、商家、优惠券和订单需要通过应用流程创建。没有打包真实用户数据、现有上传文件或外部服务密钥。短信和微信登录等外部服务需要单独配置测试凭证。Quartz 调度暂时关闭，以免空数据库缺少调度表导致启动失败；涉及定时任务的流程不属于此环境验收范围。

## 给同学

同学有源码时：发送后端源码（不要包含 config 中的私密凭证）、构建好的 target/cool-admin-8.0.0.jar 和本目录。Docker 构建仅复制 JAR，不复制源码及本地配置文件。

也可在你构建成功后执行：

```
docker save -o backend-image.tar perkly-backend:frontend-test
```

同学只需取得本目录的 compose.yml 和 backend-image.tar，在该目录执行：

```
docker load -i backend-image.tar
docker compose up -d --no-build
```

首次启动仍需联网下载固定版本 MySQL 镜像。不要向同学声称已经包含预置业务账号。

## 常用命令

```
docker compose ps
docker compose logs --tail 100 backend
docker compose stop
docker compose start
```

`docker compose down` 保留数据卷；不要执行带 `-v` 的 down 命令，除非确定要删除全部联调数据。

默认使用主机 18001 端口，避免与你本机 8001 冲突。手机测试时在本目录创建 .env，写入 `PUBLIC_BASE_URL=http://同学电脑的局域网IP:18001`，然后重新执行 up -d，并让前端使用相同地址。需要允许防火墙相应端口。

## 验证状态

Compose 语法检查通过。交付时须确认 Docker 引擎启动后构建、首次空库启动、登录和业务数据创建成功；未完成这些验证前，此配置为待验收版本。

短信 SDK 使用无效占位凭证以满足启动校验，不能发送短信。需要真实短信功能时替换为专用测试凭证。

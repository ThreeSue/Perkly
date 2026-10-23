# 后端工作约定

## 执行日志

- 编译、测试、临时服务启动等命令的输出不得保存到后端根目录。
- 通常直接读取终端输出；确需保存时，先创建 `logs/build/`、`logs/test/` 或 `logs/server/`，再将标准输出和错误输出写入对应子目录。
- `Tee-Object`、`Out-File`、重定向、`Start-Process -RedirectStandardOutput/-RedirectStandardError` 等均须遵循上述目录约定，不能仅依赖 `.gitignore`。
- 历史记录归档在 `logs/archive/日期/`。不删除仍在写入的日志；归档不得覆盖同名文件。
- `target/surefire-reports` 是测试工具管理的报告目录，保留原位置。不要为了整理日志改动业务逻辑或启动真实业务测试。

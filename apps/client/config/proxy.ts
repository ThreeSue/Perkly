export const proxy = {
	// 开发环境配置
	dev: {
		// 前端联调接口文档中的本地后端地址
		target: "http://127.0.0.1:8001",
		changeOrigin: true,
		rewrite: (path: string) => path.replace("/dev", "")
	}
};

export const value = "dev";

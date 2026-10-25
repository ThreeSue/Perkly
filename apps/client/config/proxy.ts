export const proxy = {
	// 开发环境配置
	dev: {
		// 前端联调接口文档中的本地后端地址
		target: "http://127.0.0.1:8001",
		changeOrigin: true,
		rewrite: (path: string) => path.replace("/dev", "")
	},
	// 生产域名由部署环境填写；当前留空，避免把本地地址打进生产包。
	prod: {
		target: "",
		changeOrigin: true,
		rewrite: (path: string) => path.replace("/prod", "")
	}
};

export const value = "dev";

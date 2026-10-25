// 真机调试时将 target 换成设备能访问的电脑IP或服务域名；不复用用户端Token。
export const proxy = {
	dev: {
		target: "http://127.0.0.1:8001",
		changeOrigin: true,
		rewrite: (path: string) => path.replace(/^\/dev/, "")
	},
	prod: { target: "", changeOrigin: true, rewrite: (path: string) => path.replace(/^\/prod/, "") }
};
export const value = "dev";

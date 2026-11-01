// 网页Key仅由Vite注入；不使用后端Key/SK，不记录SDK地址或上游错误。
declare global {
	interface Window {
		TMap?: any;
	}
}
let pending: Promise<any> | undefined;
export function loadTencentMap(): Promise<any> {
	if (window.TMap?.Map && window.TMap?.MarkerCluster && window.TMap?.tools?.GeometryEditor)
		return Promise.resolve(window.TMap);
	if (pending) return pending;
	const key = import.meta.env.VITE_TENCENT_MAP_KEY?.trim();
	if (!key)
		return Promise.reject(new Error('尚未配置网页地图Key，请联系管理员配置后重新启动前端。'));
	pending = new Promise((resolve, reject) => {
		const script = document.createElement('script');
		let settled = false;
		const finish = (success: boolean) => {
			if (settled) return;
			settled = true;
			clearTimeout(timer);
			script.onload = script.onerror = null;
			if (
				success &&
				window.TMap?.Map &&
				window.TMap?.MarkerCluster &&
				window.TMap?.tools?.GeometryEditor
			)
				resolve(window.TMap);
			else {
				script.remove();
				pending = undefined;
				reject(new Error('腾讯地图加载失败，请检查网络、网页Key的域名授权或稍后重试。'));
			}
		};
		const timer = window.setTimeout(() => finish(false), 15000);
		script.async = true;
		script.charset = 'utf-8';
		script.referrerPolicy = 'strict-origin-when-cross-origin';
		script.onload = () => finish(true);
		script.onerror = () => finish(false);
		script.src = `https://map.qq.com/api/gljs?v=1.exp&libraries=cluster,tools&key=${encodeURIComponent(key)}`;
		document.head.appendChild(script);
	});
	return pending;
}

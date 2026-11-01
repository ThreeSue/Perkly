// Tencent's dynamic SDK is kept outside Vue reactivity. Never log script URLs or SDK errors containing a key.
export type TencentSdk = any;
let pending: Promise<TencentSdk> | undefined;
export function loadTencentMap(): Promise<TencentSdk> {
	const existing = (window as any).TMap;
	if (existing?.Map && existing?.tools?.GeometryEditor && existing?.MarkerCluster)
		return Promise.resolve(existing);
	if (pending) return pending;
	const key = String(import.meta.env.VITE_TENCENT_MAP_KEY || '').trim();
	if (!key)
		return Promise.reject(new Error('未配置网页地图 Key，请检查 .env.local 并重新启动前端'));
	pending = new Promise((resolve, reject) => {
		const script = document.createElement('script');
		script.async = true;
		script.referrerPolicy = 'strict-origin-when-cross-origin';
		const timer = window.setTimeout(() => fail(), 20000);
		function fail() {
			window.clearTimeout(timer);
			script.remove();
			pending = undefined;
			reject(
				new Error('腾讯地图加载失败，请检查网络、网页 Key 的域名授权和 WebGL 支持后重试')
			);
		}
		script.onerror = fail;
		script.onload = () => {
			window.clearTimeout(timer);
			const sdk = (window as any).TMap;
			if (!sdk?.Map || !sdk?.MarkerCluster || !sdk?.tools?.GeometryEditor) {
				fail();
				return;
			}
			resolve(sdk);
		};
		script.src =
			'https://map.qq.com/api/gljs?v=1.exp&libraries=tools&key=' + encodeURIComponent(key);
		document.head.appendChild(script);
	});
	return pending;
}
export function markerIcon(color: string, text = '') {
	const svg = text
		? `<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44"><circle cx="22" cy="22" r="20" fill="${color}" stroke="white" stroke-width="3"/><text x="22" y="27" font-size="14" font-family="sans-serif" text-anchor="middle" fill="white">${text}</text></svg>`
		: `<svg xmlns="http://www.w3.org/2000/svg" width="30" height="40"><path fill="${color}" stroke="white" stroke-width="2" d="M15 39C12 32 1 23 1 15a14 14 0 0 1 28 0c0 8-11 17-14 24Z"/><circle cx="15" cy="15" r="5" fill="white"/></svg>`;
	return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

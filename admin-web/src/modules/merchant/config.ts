import type { ModuleConfig } from '/@/cool';
export default (): ModuleConfig => ({
	views: [
		{
			path: '/merchant/storeMap',
			meta: { label: '门店分布地图' },
			component: () => import('./views/storeMap.vue')
		}
	]
});

import type { ModuleConfig } from '/@/cool';
export default (): ModuleConfig => ({
	views: [
		{
			path: '/home/banner',
			meta: { label: '首页广告' },
			component: () => import('./views/banner.vue')
		}
	]
});

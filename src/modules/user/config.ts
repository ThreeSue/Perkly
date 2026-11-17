import { type ModuleConfig } from '/@/cool';
import { riskToolPages } from './risk-navigation';

export default (): ModuleConfig => {
	return {
		views: [
			{ path: '/user/search-risk', meta: { label: '风险记录' }, component: () => import('./views/search-risk.vue') },
			...riskToolPages.map(page => ({
				path: page.path,
				name: `risk-${page.key}`,
				meta: { label: page.label, riskTool: page },
				component: () => import('./views/risk-tools.vue')
			}))
		]
	};
};

import { type ModuleConfig } from '/@/cool';

export default (): ModuleConfig => {
	return { views: [{ path: '/user/search-risk', meta: { label: '内容风险管控' }, component: () => import('./views/search-risk.vue') }] };
};

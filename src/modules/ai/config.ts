import type { ModuleConfig } from '/@/cool';
import { aiUsagePage } from './usage-navigation';

export default (): ModuleConfig => ({
	views: [{
		path: aiUsagePage.path,
		name: 'ai-usage',
		meta: { label: aiUsagePage.label },
		component: () => import('./views/usage.vue')
	}]
});

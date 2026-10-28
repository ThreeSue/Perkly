import { type ModuleConfig } from '/@/cool';
import { isDev } from '/@/config';

export default (): ModuleConfig => {
	return {
		enable: isDev,
		index: {
			component: import('./components/index.vue')
		}
	};
};

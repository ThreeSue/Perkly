import { type ModuleConfig } from '/@/cool';
import { useDict } from './index';
import { isPlatform } from '/@/workspace';

export default (): ModuleConfig => {
	return {
		ignore: {
			NProgress: ['/dict/info/data']
		},
		onLoad({ hasToken }) {
			if (!isPlatform) return;
			const { dict } = useDict();
			hasToken(() => {
				dict.refresh();
			});
		}
	};
};

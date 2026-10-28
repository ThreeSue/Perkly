import { type ModuleConfig } from '/@/cool';
import { useDict } from './index';

export default (): ModuleConfig => {
	return {
		ignore: {
			NProgress: ['/dict/info/data']
		},
		// 商家首版使用业务接口中的可读字段，不请求运营端字典接口。
	};
};

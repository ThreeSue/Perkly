import { proxy } from './proxy';

export default {
	// 根地址
	host: location.origin,

	// 请求地址
	get baseUrl() {
		const mode = import.meta.env.MODE;

		if (mode == 'static') {
			return location.origin;
		} else {
			return '/api';
		}
	}
};

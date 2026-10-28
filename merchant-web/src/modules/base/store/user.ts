import { defineStore } from 'pinia';
import { ref } from 'vue';
import { storage } from '/@/cool/utils';
import { router } from '/@/cool';
import { merchantRequest } from '/@/merchant-api';

// 清除旧平台维护会话，商家中心不再接受运营登录模式。
if (storage.get('authMode') === 'admin') {
	['token', 'refreshToken', 'userInfo', 'base.menuGroup', 'base.menuPerms'].forEach(key => storage.remove(key));
}
storage.remove('authMode');

// 本地缓存
const data = storage.info();

export const useUserStore = defineStore('user', function () {
	// 标识
	const token = ref<string>(data.token);

	// 设置标识
	function setToken(data: {
		token: string;
		expire: number;
		refreshToken: string;
		refreshExpire: number;
	}) {
		// 请求的唯一标识
		token.value = data.token;
		storage.set('token', data.token, data.expire);

		// 刷新 token 的唯一标识
		storage.set('refreshToken', data.refreshToken, data.refreshExpire);
	}

	// 刷新标识
	async function refreshToken(): Promise<string> {
		return new Promise((resolve, reject) => {
			const params = { refreshToken: storage.get('refreshToken') };
			merchantRequest('auth/refreshToken', params)
				.then(res => {
					setToken(res);
					resolve(res.token);
				})
				.catch(err => {
					logout();
					reject(err);
				});
		});
	}

	// 用户信息
	const info = ref<Eps.BaseSysUserEntity | null>(data.userInfo);

	// 设置用户信息
	function set(value: any) {
		info.value = value;
		storage.set('userInfo', value);
	}

	// 清除用户
	function clear() {
		storage.remove('userInfo');
		storage.remove('token');
		storage.remove('refreshToken');
		storage.remove('base.menuGroup');
		storage.remove('base.menuPerms');
		token.value = '';
		info.value = null;
	}

	// 退出
	async function logout() {
		clear();
		router.clear();
		router.push('/login');
	}

	// 获取用户信息
	async function get() {
		return merchantRequest('workspace/me').then(res => {
			set({ ...res, nickName: res.name });
			return res;
		});
	}

	return {
		token,
		info,
		get,
		set,
		logout,
		clear,
		setToken,
		refreshToken
	};
});

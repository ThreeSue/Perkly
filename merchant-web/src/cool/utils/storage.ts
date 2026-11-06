import store from 'store';
import { identity } from '/@/workspace';
// 开发时按标签页隔离账号，支持店长、店员同时联调。
const accountStore = import.meta.env.DEV
	? {
		get(key: string) {
			const value = sessionStorage.getItem(key);
			return value === null ? undefined : JSON.parse(value);
		},
		set(key: string, value: any) {
			if (value === undefined) sessionStorage.removeItem(key);
			else sessionStorage.setItem(key, JSON.stringify(value));
		},
		remove(key: string) {
			sessionStorage.removeItem(key);
		},
		each(callback: (value: any, key: string) => void) {
			Object.keys(sessionStorage).filter(key => key.startsWith('perkly.dev:')).forEach(key => {
				callback(this.get(key), key.slice('perkly.dev:'.length));
			});
		}
	}
	: store;
const prefix = identity === 'PLATFORM' ? '' : `workspace:${identity}:`;
const keyOf = (key: string) => (import.meta.env.DEV ? 'perkly.dev:' : '') + prefix + key;

export default {
	// 后缀标识
	suffix: '_deadtime',

	/**
	 * 获取
	 * @param {string} key 关键字
	 */
	get(key: string) {
		return accountStore.get(keyOf(key));
	},

	/**
	 * 获取全部
	 */
	info() {
		const data: Record<string, any> = {};

		accountStore.each((value: any, key: any) => {
			if (prefix ? key.startsWith(prefix) : !key.startsWith('workspace:'))
				data[prefix ? key.slice(prefix.length) : key] = value;
		});

		return data;
	},

	/**
	 * 设置
	 * @param {string} key 关键字
	 * @param {*} value 值
	 * @param {number} expires 过期时间
	 */
	set(key: string, value: any, expires?: number) {
		accountStore.set(keyOf(key), value);

		if (expires) {
			const expirationTime = Date.now() + expires * 1000;
			accountStore.set(keyOf(`${key}${this.suffix}`), expirationTime);
		}
	},

	/**
	 * 是否过期
	 * @param {string} key 关键字
	 */
	isExpired(key: string) {
		const expiration = this.getExpiration(key) || 0;
		return expiration - Date.now() <= 2000;
	},

	/**
	 * 获取到期时间
	 * @param {string} key 关键字
	 */
	getExpiration(key: string) {
		return this.get(key + this.suffix);
	},

	/**
	 * 移除
	 * @param {string} key 关键字
	 */
	remove(key: string) {
		accountStore.remove(keyOf(key));
		this.removeExpiration(key);
	},

	/**
	 * 移除到期时间
	 * @param {string} key 关键字
	 */
	removeExpiration(key: string) {
		store.remove(keyOf(key + this.suffix));
	},

	/**
	 * 清理
	 */
	clearAll() {
		Object.keys(this.info()).forEach(key => accountStore.remove(keyOf(key)));
	}
};

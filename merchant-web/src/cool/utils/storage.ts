import store from 'store';
import { identity } from '/@/workspace';
const prefix = identity === 'PLATFORM' ? '' : `workspace:${identity}:`;
const keyOf = (key: string) => prefix + key;

export default {
	// 后缀标识
	suffix: '_deadtime',

	/**
	 * 获取
	 * @param {string} key 关键字
	 */
	get(key: string) {
		return store.get(keyOf(key));
	},

	/**
	 * 获取全部
	 */
	info() {
		const data: Record<string, any> = {};

		store.each((value: any, key: any) => {
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
		store.set(keyOf(key), value);

		if (expires) {
			const expirationTime = Date.now() + expires * 1000;
			store.set(keyOf(`${key}${this.suffix}`), expirationTime);
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
		store.remove(keyOf(key));
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
		Object.keys(this.info()).forEach(key => store.remove(keyOf(key)));
	}
};

import { computed, ref } from "vue";
import { isNull, parse, storage } from "../utils";
import { router } from "../router";
import { request } from "../service";
import type { UserInfo } from "../types";

export type Token = {
	token: string; // 访问token
	expire: number; // token过期时间（秒）
	refreshToken: string; // 刷新token
	refreshExpire: number; // 刷新token过期时间（秒）
};

export class User {
	/**
	 * 用户信息，响应式对象
	 */
	info = ref<UserInfo | null>(null);
	profileLoading = ref(false);
	profileLoadFailed = ref(false);
	// 私有业务响应绑定会话版本；刷新Token不等于切换账号。
	sessionVersion = ref(0);

	/**
	 * 当前token，字符串或null
	 */
	token: string | null = null;

	constructor() {
		// 身份资料以服务端为准；清理旧版曾持久化的资料快照。
		storage.remove("userInfo");

		// Token 是请求授权凭据，由客户端保留以恢复会话。
		const token = storage.get("token") as string | null;

		// 如果token为空字符串则置为null
		this.token = token == "" ? null : token;
	}

	/**
	 * 获取用户信息（从服务端拉取并保存在当前运行内存）
	 * @returns Promise<void>
	 */
	async get() {
		if (this.token == null || this.profileLoading.value) return;
		const requestSession = this.sessionVersion.value;
		this.profileLoading.value = true;
		this.profileLoadFailed.value = false;

		try {
			const result = await request({ url: "/app/user/info/person" });
			if (this.sessionVersion.value !== requestSession) return;
			if (result == null) throw new Error("资料暂时无法加载");
			this.set(result);
		} catch {
			if (this.sessionVersion.value === requestSession) this.profileLoadFailed.value = true;
		} finally {
			if (this.sessionVersion.value === requestSession) this.profileLoading.value = false;
		}
	}

	/**
	 * 设置当前运行中的用户信息
	 * @param data 用户信息对象
	 */
	set(data: any) {
		if (isNull(data)) {
			return;
		}

		// 设置
		this.info.value = parse<UserInfo>(data)!;
		this.profileLoadFailed.value = false;
	}

	/**
	 * 更新文档允许的资料字段，服务端确认后重新读取
	 * @param data 新的用户信息
	 */
	async update(data: any) {
		if (isNull(data) || isNull(this.info.value)) {
			throw new Error("请重新登录后修改资料");
		}

		const current = this.info.value!;
		const payload = {
			nickName: data.nickName ?? current.nickName,
			avatarUrl: data.avatarUrl ?? current.avatarUrl ?? "",
			gender: data.gender ?? current.gender
		};
		const result = await request({
			url: "/app/user/info/updatePerson",
			method: "POST",
			data: payload
		});
		if (result !== true) throw new Error("资料保存失败，请重试");
		const latest = await request({ url: "/app/user/info/person" });
		if (latest != null) this.set(latest);
	}

	/**
	 * 清除当前运行中的用户信息
	 */
	remove() {
		this.info.value = null;
		this.profileLoadFailed.value = false;
	}

	/**
	 * 判断用户信息是否为空
	 * @returns boolean
	 */
	isNull() {
		return this.info.value == null;
	}

	/**
	 * 清除本地所有用户信息和token
	 */
	clear() {
		this.sessionVersion.value++;
		storage.remove("userInfo");
		storage.remove("token");
		storage.remove("refreshToken");
		this.token = null;
		this.profileLoading.value = false;
		this.remove();
	}

	/**
	 * 退出登录，清除所有信息并跳转到登录页
	 */
	logout() {
		this.clear();
		router.login();
	}

	/**
	 * 设置token并存储到本地
	 * @param data Token对象
	 */
	setToken(data: Token, keepSession = false) {
		if (!keepSession) this.sessionVersion.value++;
		this.token = data.token;

		// 访问token，提前5秒过期，防止边界问题
		storage.set("token", data.token, data.expire - 5);
		// 刷新token，提前5秒过期
		storage.set("refreshToken", data.refreshToken, data.refreshExpire - 5);
	}

	/**
	 * 刷新token（调用服务端接口，自动更新本地token）
	 * @returns Promise<string> 新的token
	 */
	refreshToken(): Promise<string> {
		const session = this.sessionVersion.value;
		return new Promise((resolve, reject) => {
			request({
				url: "/app/user/login/refreshToken",
				method: "POST",
				data: {
					refreshToken: storage.get("refreshToken")
				}
			})
				.then((res) => {
					if (session !== this.sessionVersion.value) {
						reject(new Error("登录身份已变化"));
						return;
					}
					if (res != null) {
						const token = parse<Token>(res);

						if (token != null) {
							this.setToken(token, true);
							resolve(token.token);
							return;
						}
					}
					reject(new Error("登录状态刷新失败，请重新登录"));
				})
				.catch((err) => {
					reject(err);
				});
		});
	}
}

/**
 * 单例用户对象，项目全局唯一
 */
export const user = new User();

/**
 * 用户信息，响应式对象
 */
export const userInfo = computed(() => user.info.value);

import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useUserStore } from '/$/base/store/user';
import { workspaceApi } from '../api';

export const useNoticeStore = defineStore('workspace-notices', () => {
	const unread = ref(0);
	let revision = 0;

	function reset() {
		revision++;
		unread.value = 0;
	}

	async function refresh() {
		const user = useUserStore();
		const token = user.token;
		const current = ++revision;
		if (!token) {
			unread.value = 0;
			return;
		}
		try {
			const count = await workspaceApi('notices/unread');
			// 阅读后的刷新、退出或换账号后，旧请求不能覆盖新状态。
			if (current === revision && token === user.token) {
				unread.value = Math.max(0, Number(count) || 0);
			}
		} catch {
			// 临时断网时保留已有红点，等待下一次刷新。
		}
	}

	return { unread, refresh, reset };
});

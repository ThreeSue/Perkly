import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useUserStore } from '/$/base/store/user';
import { workspaceApi } from '../api';

export const useNoticeStore = defineStore('workspace-notices', () => {
	const unread = ref(0);
	const pending = ref<any[]>([]);
	let revision = 0;

	function reset() {
		revision++;
		unread.value = 0;
		pending.value = [];
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
			const [count, reminders] = await Promise.all([workspaceApi('notices/unread'), workspaceApi('notices/pending?pageNumber=1&pageSize=20')]);
			// 阅读后的刷新、退出或换账号后，旧请求不能覆盖新状态。
			if (current === revision && token === user.token) {
				unread.value = Math.max(0, Number(count) || 0);
				pending.value = reminders.list;
			}
		} catch {
			// 临时断网时保留已有红点，等待下一次刷新。
		}
	}

	async function confirm(id: number) {
		const user = useUserStore(), token = user.token;
		const result = await workspaceApi('notices/confirm', { id });
		if (token !== user.token) return;
		revision++;
		pending.value = pending.value.filter(item => item.id !== id);
		void refresh();
		return result;
	}
	return { unread, pending, confirm, refresh, reset };
});

import { computed, onUnmounted, ref, watch } from "vue";
import { useStore } from "@/.cool";
import { getApiErrorMessage } from "@/api/discovery";
import type { PageResult } from "@/types/discovery";

export function usePrivatePage<T>(fetchPage: (page: number) => Promise<PageResult<T>>) {
	const { user } = useStore();
	const items = ref<T[]>([]);
	const loading = ref(false);
	const error = ref("");
	const total = ref(0);
	const loggedIn = ref(Boolean(user.token));
	let page = 0;
	let version = 0;
	const hasMore = computed(() => items.value.length < total.value);
	function clear() {
		version++;
		page = 0;
		items.value = [];
		total.value = 0;
		error.value = "";
		loading.value = false;
		loggedIn.value = Boolean(user.token);
	}
	async function load(reset = true) {
		if (reset) clear();
		if (!user.token || loading.value || (!reset && !hasMore.value)) return;
		const id = ++version;
		const session = user.sessionVersion.value;
		loading.value = true;
		error.value = "";
		try {
			const result = await fetchPage(page + 1);
			if (id !== version || session !== user.sessionVersion.value) return;
			items.value = (
				reset ? result.list : [...items.value, ...result.list]
			) as typeof items.value;
			total.value = result.pagination.total;
			page = result.pagination.page;
		} catch (err) {
			if (id === version) error.value = getApiErrorMessage(err, "加载失败，请重试");
		} finally {
			if (id === version) loading.value = false;
		}
	}
	watch(user.sessionVersion, clear);
	onUnmounted(clear);
	return { items, loading, error, total, loggedIn, hasMore, load, clear };
}

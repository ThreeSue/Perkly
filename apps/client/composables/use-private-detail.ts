import { onUnmounted, ref, shallowRef, watch } from "vue";
import { useStore } from "@/.cool";
import { getApiErrorMessage } from "@/api/discovery";
import type { ApiId } from "@/types/discovery";

export function usePrivateDetail<T>(fetchDetail: (id: ApiId) => Promise<T>) {
	const { user } = useStore();
	const value = shallowRef<T | null>(null);
	const loading = ref(false),
		error = ref(""),
		loggedIn = ref(Boolean(user.token));
	let recordId: ApiId = "";
	let version = 0;
	function clear() {
		version++;
		value.value = null;
		loading.value = false;
		error.value = "";
		loggedIn.value = Boolean(user.token);
	}
	async function load() {
		clear();
		if (!user.token) return;
		const current = ++version;
		loading.value = true;
		try {
			const result = await fetchDetail(recordId);
			if (current === version) value.value = result;
		} catch (err) {
			if (current === version) error.value = getApiErrorMessage(err, "详情读取失败，请重试");
		} finally {
			if (current === version) loading.value = false;
		}
	}
	watch(user.sessionVersion, clear);
	onLoad((options) => {
		recordId = options?.id ?? "";
	});
	onShow(load);
	onHide(clear);
	onUnmounted(clear);
	return { value, loading, error, loggedIn, load };
}

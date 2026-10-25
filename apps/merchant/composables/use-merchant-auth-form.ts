import { nextTick, onUnmounted, ref } from "vue";
import { useRefs } from "@/.cool";
import type { MerchantFieldErrors } from "@/types/merchant-auth";
export function useMerchantAuthForm() {
	const busy = ref(false);
	const errors = ref<MerchantFieldErrors>({});
	const message = ref("");
	const refs = useRefs();
	let alive = true;
	onUnmounted(() => {
		alive = false;
	});
	function validate(values: MerchantFieldErrors) {
		errors.value = values;
		message.value = "";
		const key = Object.keys(values)[0];
		if (key)
			nextTick(() => {
				refs.get(key)?.focus?.();
				uni.pageScrollTo({ selector: "#field-" + key, duration: 200 });
			});
		return !key;
	}
	async function run<T>(action: () => Promise<T>, success: (result: T) => void) {
		if (busy.value || !alive) return;
		busy.value = true;
		message.value = "";
		try {
			const result = await action();
			if (alive) success(result);
		} catch (error) {
			if (alive)
				message.value = error instanceof Error ? error.message : "操作未完成，请稍后再试";
		} finally {
			if (alive) busy.value = false;
		}
	}
	return { busy, errors, message, refs, validate, run };
}

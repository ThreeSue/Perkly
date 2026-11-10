import { useMediaQuery } from '@vueuse/core';
import { computed } from 'vue';

export function useRegisterHeight() {
	const bounded = useMediaQuery('(min-width: 851px)');
	return computed(() => (bounded.value ? '100%' : undefined));
}

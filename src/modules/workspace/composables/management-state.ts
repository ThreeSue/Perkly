import { reactive, watch, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { identity } from '/@/workspace';

export function positiveId(value: unknown): number | undefined {
	const n = Number(value);
	return Number.isSafeInteger(n) && n > 0 ? n : undefined;
}

/** Save filters only, never account records, permissions or credentials. */
export function useManagementState<T extends Record<string, any>>(key: string, defaults: T) {
	const route = useRoute(),
		router = useRouter(),
		path = route.path;
	const storageKey = `perkly.management.${identity}.${key}`;
	let saved: Partial<T> = {};
	try {
		saved = JSON.parse(sessionStorage.getItem(storageKey) || '{}');
	} catch {
		/* use defaults */
	}
	const state = reactive({ ...defaults, ...saved }) as T & { merchantId?: number };
	if ('merchantId' in defaults && route.query.merchantId !== undefined)
		state.merchantId = positiveId(route.query.merchantId);
	for (const key of ['storeId', 'roleId']) {
		if (
			key in defaults &&
			(route.query[key] !== undefined || route.query.merchantId !== undefined)
		) {
			(state as Record<string, any>)[key] = positiveId(route.query[key]);
		}
	}
	watch(state, value => sessionStorage.setItem(storageKey, JSON.stringify(value)), {
		deep: true
	});
	watch(
		() => route.query.merchantId,
		value => {
			if (route.path === path && 'merchantId' in defaults)
				state.merchantId = positiveId(value);
		}
	);
	function selectMerchant(id: number | undefined) {
		state.merchantId = id;
		void router.replace({
			query: {
				...route.query,
				merchantId: id == null ? undefined : String(id),
				storeId: undefined,
				roleId: undefined
			}
		});
	}
	return { state, selectMerchant };
}

export function useRequestRevision() {
	let revision = 0,
		alive = true;
	onBeforeUnmount(() => {
		alive = false;
		revision++;
	});
	return { next: () => ++revision, current: (id: number) => alive && id === revision };
}

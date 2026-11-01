/** Keep drawer queries and writes within the selected merchant without mutating the shared service. */
export function createMerchantService<
	T extends {
		page: (data?: any) => Promise<any>;
		list: (data?: any) => Promise<any>;
		info: (data?: any) => Promise<any>;
		add: (data?: any) => Promise<any>;
		update: (data?: any) => Promise<any>;
		delete: (data?: any) => Promise<any>;
	}
>(base: T, merchantId: () => number | undefined, changed: () => void) {
	function scope(data: Record<string, unknown> = {}) {
		const id = merchantId();
		return id == null ? { ...data } : { ...data, merchantId: id };
	}
	function query(data: Record<string, unknown> = {}) {
		// Empty search filters must be omitted, not serialized as JSON null.
		// Retain meaningful values such as status=0; never apply this to writes.
		return Object.fromEntries(
			Object.entries(scope(data)).filter(
				([, value]) => value !== null && value !== undefined && value !== ''
			)
		);
	}
	async function mutate(action: 'add' | 'update' | 'delete', data: any) {
		const result = await base[action](action === 'delete' ? data : scope(data));
		changed();
		return result;
	}
	return {
		...base,
		page: (data?: any) => base.page(query(data)),
		list: (data?: any) => base.list(query(data)),
		info: (data?: any) => base.info(data),
		add: (data?: any) => mutate('add', data),
		update: (data?: any) => mutate('update', data),
		delete: (data?: any) => mutate('delete', data)
	};
}

type Item = Record<string, any>;
type Section = { name: string; icon: string; pages?: string[]; sections?: Section[] };
const section = (name: string, icon: string, pages: string[]): Section => ({ name, icon, pages });

const platform: Section[] = [
	section('商家与门店', 'icon-local', ['/merchant/info', '/merchant/category', '/merchant/store', '/merchant/storeCategory', '/merchant/storeMap']),
	section('优惠与活动', 'icon-tag', ['/coupon/template', '/home/banner', '/workspace-admin/activities', '/workspace-admin/stamps', '/workspace-admin/recharge']),
	section('审核中心', 'icon-approve', ['/approval/merchant', '/approval/coupon']),
	section('交易与账务', 'icon-amount', ['/trade/purchases', '/trade/consumption', '/workspace-admin/wallet']),
	section('用户与服务', 'icon-user', ['/user/list', '/workspace-admin/feedback', '/workspace-admin/notices']),
	section('商家授权', 'icon-auth', ['/workspace-admin/authorization', '/workspace-admin/accounts', '/workspace-admin/roles', '/workspace-admin/menus']),
	section('AI管理', 'icon-ai', ['/workspace-admin/ai-images']),
	{ name: '系统设置', icon: 'icon-set', sections: [
		section('运营账号与权限', 'icon-user', ['/sys/user', '/sys/role']),
		section('菜单与参数', 'icon-menu', ['/sys/menu', '/sys/param', '/dict/list']),
		section('文件与维护', 'icon-data', ['/upload/list', '/space/list', '/sys/log', '/recycle/data', '/helper/plugins'])
	] }
];
const business = (store: boolean): Section[] => [
	section('门店管理', 'icon-local', ['/merchant/stores', '/merchant/store-map']),
	section('优惠与活动', 'icon-tag', ['/merchant/coupons', '/merchant/activities', '/merchant/activity-inbox', '/merchant/activity-notices']),
	section('订单与账单', 'icon-amount', ['/merchant/orders', '/merchant/consumption', '/merchant/wallet']),
	section('员工与权限', 'icon-auth', ['/merchant/accounts', '/merchant/roles', '/merchant/authorization']),
	section('客户服务', 'icon-user', store ? ['/merchant/chats'] : ['/merchant/redemption', '/merchant/chats']),
	section(store ? '门店设置' : '商家设置', 'icon-set', ['/merchant/profile', '/merchant/notices'])
];

// 只整理服务端已授权且可见的菜单，页面路由和权限数据沿用原值。
export function buildNavigation(items: Item[], identity: string): Item[] {
	const visible = items.filter(item => item.isShow !== false && item.isShow !== 0);
	const byId = new Map(items.map(item => [item.id, item]));
	const pages = visible.filter(item => {
		const seen = new Set([item.id]);
		let parent = byId.get(item.parentId);
		while (parent) {
			if (seen.has(parent.id) || parent.isShow === false || parent.isShow === 0) return false;
			seen.add(parent.id);
			parent = byId.get(parent.parentId);
		}
		return item.type === 1;
	});
	const path = (item: Item) => (item.router || item.path || '').replace(/^\/store\//, '/merchant/');
	const used = new Set();
	let id = -910000;
	function group(spec: Section, orderNum: number): Item | undefined {
		const children = spec.sections
			? spec.sections.map((child, index) => group(child, index)).filter(Boolean)
			: (spec.pages || []).flatMap(route => pages.filter(item => path(item) === route).map(item => {
				used.add(item.id);
				return { ...item, children: [] };
			}));
		if (!children.length) return;
		const nodeId = id--;
		return { id: nodeId, name: spec.name, meta: { label: spec.name }, icon: spec.icon,
			type: 0, isShow: true, path: '/navigation/group/' + -nodeId, orderNum, children };
	}
	const sections = (identity === 'PLATFORM' ? platform : business(identity === 'STORE'))
		.map((spec, index) => group(spec, index + 10)).filter(Boolean) as Item[];
	const home = pages.filter(item => ['/', '/merchant/dashboard'].includes(path(item)) ||
		(identity === 'STORE' && path(item) === '/merchant/redemption'));
	home.forEach(item => used.add(item.id));
	// 未纳入规划的扩展页面继续保留原目录关系，空目录不展示。
	function remaining(parentId?: any, seen = new Set()): Item[] {
		return visible.filter(item => (item.parentId ?? null) === (parentId ?? null) && !used.has(item.id) && !seen.has(item.id))
			.flatMap(item => {
				const children = remaining(item.id, new Set([...seen, item.id]));
				if (item.type === 0 && !children.length) return [];
				return [{ ...item, children }];
			});
	}
	return [...home.map(item => ({ ...item, children: [] })), ...sections, ...remaining()];
}

import { riskRecordRoutes, riskToolPages } from '../../user/risk-navigation';
import { aiUsagePage } from '../../ai/usage-navigation';

type Item = Record<string, any>;
type Section = { name: string; icon: string; pages?: string[]; sections?: Section[]; risk?: true };
const section = (name: string, icon: string, pages: string[]): Section => ({ name, icon, pages });

const platform: Section[] = [
	section('商家与门店', 'icon-local', ['/merchant/info', '/merchant/category', '/merchant/store', '/merchant/storeCategory', '/merchant/storeMap']),
	section('优惠与活动', 'icon-tag', ['/coupon/template', '/home/banner', '/workspace-admin/activities', '/workspace-admin/stamps', '/workspace-admin/recharge']),
	section('审核中心', 'icon-approve', ['/approval/merchant', '/approval/coupon']),
	{ name: '内容风险管控', icon: 'icon-auth', risk: true },
	section('交易与账务', 'icon-amount', ['/trade/purchases', '/trade/consumption', '/workspace-admin/wallet']),
	section('用户与服务', 'icon-user', ['/user/list', '/workspace-admin/feedback', '/workspace-admin/notices']),
	section('商家授权', 'icon-auth', ['/workspace-admin/authorization', '/workspace-admin/accounts', '/workspace-admin/roles', '/workspace-admin/menus']),
	section('AI管理', 'icon-ai', ['/workspace-admin/ai-images']),
	{ name: '系统设置', icon: 'icon-set', sections: [
		section('运营账号与权限', 'icon-user', ['/sys/user', '/sys/role']),
		section('菜单与参数', 'icon-menu', ['/sys/menu', '/sys/param', '/dict/list']),
		section('消息与验证', 'icon-menu', ['/workspace-admin/sms-usage']),
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
export function buildNavigation(items: Item[], identity: string, canViewRisk = false, _canManageRisk = false, canManageAi = false): Item[] {
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

	function riskChildren(): Item[] {
		const matches = riskRecordRoutes.flatMap(route => pages.filter(item => path(item) === route));
		// Only expose the tools when the server supplied a visible risk menu and the account can view it.
		matches.forEach(item => used.add(item.id));
		visible.filter(item => riskRecordRoutes.includes(path(item))).forEach(item => used.add(item.id));
		if (!matches.length || !canViewRisk) return [];
		const [records] = matches;
		return [
			{ ...records, meta: { ...records.meta, label: '风险记录' }, orderNum: 0, children: [] },
			...riskToolPages.map((page, index) => ({
					id: -911000 - index,
					name: page.label,
					path: page.path,
					type: 1,
					isShow: true,
					orderNum: index + 1,
					meta: { label: page.label },
					children: []
			}))
		];
	}

	function group(spec: Section, orderNum: number): Item | undefined {
		const children = spec.risk ? riskChildren() : spec.sections
			? spec.sections.map((child, index) => group(child, index)).filter(Boolean)
			: (spec.pages || []).flatMap(route => pages.filter(item => path(item) === route).flatMap(item => {
				used.add(item.id);
				const page = { ...item, children: [] };
				if (route !== '/workspace-admin/ai-images') return [page];
				const settings = { ...page, name: 'AI 功能管理', meta: { ...item.meta, label: 'AI 功能管理' } };
				if (!canManageAi) return [settings];
				return [settings, {
					id: -912000, name: aiUsagePage.label, path: aiUsagePage.path, type: 1, isShow: true,
					orderNum: (item.orderNum ?? 0) + 1, meta: { label: aiUsagePage.label }, children: []
				}];
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

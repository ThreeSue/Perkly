import { storage } from '../cool';
import dev from './dev';
import prod from './prod';

// 是否开发模式
export const isDev = import.meta.env.DEV;

// 配置
export const config = {
	// 项目信息
	app: {
		name: import.meta.env.VITE_NAME,

		// 菜单
		menu: {
			// 是否分组显示
			isGroup: false,
			// 自定义菜单列表
			list: [
				{ id: 1, name: '工作台', type: 1, router: '/merchant/dashboard', viewPath: 'modules/merchant/views/dashboard.vue', icon: 'icon-home', orderNum: 1, keepAlive: 1 },
				{ id: 2, name: '商家资料', type: 1, router: '/merchant/profile', viewPath: 'modules/merchant/views/profile.vue', icon: 'icon-info', orderNum: 2, keepAlive: 1 },
				{ id: 3, name: '门店管理', type: 1, router: '/merchant/stores', viewPath: 'modules/merchant/views/stores.vue', icon: 'icon-menu', orderNum: 3, keepAlive: 1 },
				{ id: 7, name: '我的门店地图', type: 1, router: '/merchant/store-map', viewPath: 'modules/merchant/views/store-map.vue', icon: 'icon-menu', orderNum: 3.5, keepAlive: 1 },
				{ id: 4, name: '优惠券管理', type: 1, router: '/merchant/coupons', viewPath: 'modules/merchant/views/coupons.vue', icon: 'icon-vip', orderNum: 4, keepAlive: 1 },
				{ id: 5, name: '购券订单', type: 1, router: '/merchant/orders', viewPath: 'modules/merchant/views/orders.vue', icon: 'icon-list', orderNum: 5, keepAlive: 1 },
				{ id: 6, name: '账号中心', type: 1, router: '/my/info', viewPath: 'modules/merchant/views/account.vue', icon: 'icon-user', orderNum: 6, keepAlive: 1 }
			]
		},

		// 路由
		router: {
			// 模式
			mode: import.meta.env.MODE == 'static' ? 'hash' : 'history',
			// 转场动画
			transition: 'slide'
		}
	},

	// 国际化配置
	i18n: {
		locale: storage.get('locale') || 'zh-cn',
		languages: [
			{
				label: '中文',
				value: 'zh-cn'
			},
			{
				label: '繁体中文',
				value: 'zh-tw'
			},
			{
				label: 'English',
				value: 'en'
			}
		]
	},

	// 忽略规则
	ignore: {
		// 不显示请求进度条
		NProgress: ['__cool_*'],
		// 页面不需要登录验证
		token: []
	},

	// 当前环境
	...(isDev ? dev : prod)
};

export * from './proxy';

import { defineComponent, h, watch } from 'vue';
import { useBase } from '/$/base';
import { useCool } from '/@/cool';
import { debounce } from 'lodash-es';
import { useNoticeStore } from '/$/workspace/stores/notices';

export default defineComponent({
	name: 'b-menu',

	props: {
		keyWord: String
	},

	setup(props) {
		const { router, route, browser, refs, setRefs } = useCool();
		const { menu, app } = useBase();
		const notices = useNoticeStore();
		const isNotice = (item: Menu.Item) =>
			item.path?.endsWith('/notices') || item.router?.endsWith('/notices');

		// 页面跳转
		function onSelect(url: string) {
			if (url != route.path) {
				router.push(url);
			}

			// 小屏下点击收起左侧菜单
			if (browser.isMini) {
				app.fold(true);
			}
		}

		// 渲染子菜单
		function renderMenu() {
			function deep(list: Menu.Item[], show?: boolean) {
				const keyWord = props.keyWord?.toLowerCase() || '';

				function filterMenu(item: Menu.Item): boolean {
					if (!item.isShow) return false;

					if (show) {
						return true;
					}

					if (item.meta?.label?.toLowerCase().includes(keyWord)) return true;

					if (item.children) {
						return item.children.some(filterMenu);
					}

					return false;
				}

				return list.filter(filterMenu).map(e => {
					if (e.meta?.label?.toLowerCase().includes(keyWord)) {
						show = true;
					}

					const item = (e: Menu.Item) => {
						const arr = [
							<cl-svg name={e.icon} size={18} />,
							<span class="ml-4 tracking-wider text-[14px] mr-auto text-ellipsis overflow-hidden whitespace-nowrap">
								{e.meta?.label}
							</span>
						];
						if (isNotice(e) && notices.unread > 0) {
							arr.push(<i class="b-menu__notice-dot" role="img" aria-label="有未读通知" />);
						}

						if (e.type == 1 && e.badge) {
							arr.push(
								<div class={['b-menu__badge', `is-${e.badgeColor}`]}>
									<span>{e.badge}</span>
								</div>
							);
						}
						return arr;
					};

					if (e.type == 0) {
						return h(
							<el-sub-menu />,
							{
								index: String(e.id),
								key: e.id,
								popperClass: 'app-slider__menu'
							},
							{
								title() {
									return item(e);
								},
								default() {
									return deep(e.children || [], show);
								}
							}
						);
					} else {
						return h(
							<el-menu-item />,
							{
								index: e.meta?.isHome ? '/' : e.path,
								key: e.id
							},
							{
								default() {
									return item(e);
								}
							}
						);
					}
				});
			}

			function hasNotice(list: Menu.Item[]): boolean {
				return list.some(item => item.isShow &&
					(isNotice(item) || hasNotice(item.children || [])));
			}
			const list = [...menu.list];
			if (!hasNotice(list)) {
				list.push({
					id: -101,
					type: 1,
					path: '/my/notices',
					icon: 'icon-notice',
					isShow: true,
					meta: { label: '站内通知' }
				} as Menu.Item);
			}
			return deep(list);
		}

		// 展开所有
		const expand = debounce(() => {
			if (!props.keyWord) {
				return;
			}

			const deep = (list: Menu.Item[]) => {
				list.forEach(e => {
					if (e.type == 0) {
						try {
							refs.menu?.open(String(e.id));
						} catch (err) { }

						if (e.children) {
							deep(e.children);
						}
					}
				});
			};

			deep(menu.list);
		}, 300);

		watch(() => props.keyWord, expand);

		return () => {
			return (
				<div class="app-slider__menu">
					<el-menu
						ref={setRefs('menu')}
						default-active={route.path}
						background-color="transparent"
						collapse-transition={false}
						collapse={browser.isMini ? false : app.isFold}
						onSelect={onSelect}
						popper-offset={10}
					>
						{renderMenu()}
					</el-menu>
				</div>
			);
		};
	}
});

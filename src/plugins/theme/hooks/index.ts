import { defineStore } from 'pinia';
import { reactive } from 'vue';
import { storage } from '/@/cool';
import { useBase } from '/$/base';
import { useDark } from '@vueuse/core';
import { workspacePalette } from '/@/workspace';
import { config } from '/@/config';

export const useTheme = defineStore('theme', () => {
	const { app } = useBase();
	const isDark = useDark();
	const saved = storage.get('theme') as Theme | undefined;
	const theme = reactive({
		isGroup: saved?.isGroup ?? config.app.menu.isGroup,
		transition: saved?.transition ?? config.app.router.transition
	});

	function setTheme({ isGroup, transition, dark }: Theme) {
		if (dark !== undefined) {
			isDark.value = dark;
		}
		// 菜单分组显示
		if (isGroup !== undefined) {
			theme.isGroup = isGroup;
			app.set({
				menu: {
					isGroup
				}
			});
		}

		// 转场动画
		if (transition !== undefined) {
			theme.transition = transition;
			app.set({
				router: {
					transition
				}
			});
		}

		storage.set('theme', theme);
	}

	// 切换暗黑模式
	function changeDark(el: Element, isDark: boolean, cb: () => void) {
		const view = document as Document & {
			startViewTransition?: (update: () => void) => { ready: Promise<void> };
		};
		if (!view.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) {
			cb();
			return;
		}
		const transition = view.startViewTransition(cb);

		transition.ready.then(() => {
			const rect = el.getBoundingClientRect();
			const x = rect.left + rect.width / 2;
			const y = rect.top + rect.height / 2;
			const endRadius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
			const clipPath = [
				`circle(0 at ${x}px ${y}px)`,
				`circle(${endRadius}px at ${x}px ${y}px)`
			];
			document.documentElement.animate(
				{
					clipPath: isDark ? clipPath.reverse() : clipPath
				},
				{
					duration: 400,
					pseudoElement: isDark
						? '::view-transition-old(root)'
						: '::view-transition-new(root)'
				}
			);
		}, () => {});
	}

	// 初始化
	setTheme(theme);

	return {
		isDark,
		color: workspacePalette.accent,
		theme,
		setTheme,
		changeDark
	};
});

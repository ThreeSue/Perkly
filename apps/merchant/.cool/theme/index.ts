import { computed } from "vue";
import uniTheme from "@/theme.json";
import { router } from "../router";
import { ctx } from "../ctx";
import { isNull } from "../utils";

// 保留 Cool 组件的调用签名；本应用只使用浅色主题。
type Theme = "light" | "dark";

// 关闭跟随系统，避免组件与原生标题栏使用不同主题。
export const isAuto = computed(() => false);

/**
 * 获取页面样式
 * @param key 样式 key
 * @returns 样式值
 */
export function getStyle(key: string): string | null {
	// 页面配置
	const style = router.route()?.style;

	// 页面配置 key 映射
	const names = {
		bgColor: "backgroundColor",
		bgContentColor: "backgroundColorContent",
		navBgColor: "navigationBarBackgroundColor",
		navTextStyle: "navigationBarTextStyle"
	};

	// 如果页面配置存在，则使用页面配置
	if (style != null) {
		if (names[key] != null) {
			const val = style[names[key]!] as string | null;

			if (val != null) {
				return val;
			}
		}
	}

	return null;
}

/**
 * 获取颜色
 * @param name 颜色名称
 * @returns 颜色值
 */
export const getColor = (name: string) => {
	if (isNull(ctx.color)) {
		return "";
	}

	return ctx.color[name] as string;
};

/**
 * 获取 uniapp 主题配置
 */
export function getConfig(key: string): string {
	// 主题配置
	const themeVal = (uniTheme.light as UTSJSONObject)[key] as string | null;

	// 页面样式
	const styleVal = getStyle(key);

	return styleVal ?? themeVal ?? "";
}

// 只读状态供现有 Cool 组件消费，不再读取宿主或系统主题。
export const theme = computed<Theme>(() => "light");
export const isDark = computed(() => false);

// 兼容组件旧调用，任何主题请求都固定为浅色。
export const setTheme = (_value: Theme) => {
	// #ifdef APP
	uni.setAppTheme({ theme: "light" });
	// #endif
	// #ifdef H5
	setH5();
	// #endif
};

export const setIsAuto = () => setTheme("light");

// 同步 H5 原生页面外壳，清理开发预览可能遗留的 dark 类。
export const setH5 = () => {
	// #ifdef H5
	const bgContentColor = getConfig("bgContentColor");
	const tabBgColor = getConfig("tabBgColor");
	const navBgColor = getConfig("navBgColor");
	const navTextStyle = getConfig("navTextStyle");

	// 设置主题类
	document.documentElement.classList.remove("dark");
	document.documentElement.style.setProperty("color-scheme", "only light");

	// 设置背景色
	document.body.style.setProperty("--background-color-content", bgContentColor);

	// 设置 tabbar 背景色
	const tabbar = document.querySelector(".uni-tabbar");
	if (tabbar) {
		(tabbar as HTMLElement).style.backgroundColor = tabBgColor;
	}

	// 设置页面头部背景色和文字颜色
	const pageHead = document.querySelector(".uni-page-head");
	if (pageHead) {
		(pageHead as HTMLElement).style.backgroundColor = navBgColor;
		(pageHead as HTMLElement).style.color = navTextStyle;
	}

	// 设置页面头部按钮路径颜色
	const pageHeadBtnPath = document.querySelector(".uni-page-head-btn path");
	if (pageHeadBtnPath) {
		(pageHeadBtnPath as HTMLElement).style.fill = navTextStyle;
	}

	// #endif
};

export const toggleTheme = () => setTheme("light");

// 不注册系统、宿主或父窗口的主题变化监听。
export const initTheme = () => setTheme("light");

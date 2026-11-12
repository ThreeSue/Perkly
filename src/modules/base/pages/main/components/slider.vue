<template>
	<div
		class="app-slider"
		:class="{
			'is-collapse': app.isFold
		}"
	>
		<div class="app-slider__logo">
			<span class="app-slider__brand">券管家</span>
			<div class="app-slider__identity"><i aria-hidden="true"></i>{{ identityName }}工作空间</div>
		</div>

		<div class="app-slider__search">
			<el-input
				v-model="keyWord"
				:placeholder="$t('搜索菜单…')"
				aria-label="搜索菜单"
				clearable
				@focus="app.fold(false)"
			/>
		</div>

		<div class="app-slider__container">
			<el-scrollbar>
				<el-button class="app-slider__assistant" text @click="$router.push('/my/assistant')" aria-label="打开AI助手">
					<span v-if="!app.isFold || browser.isMini">AI 助手</span>
				</el-button>
				<div class="app-slider__caption">工作空间</div>
				<b-menu :keyWord="keyWord" />
			</el-scrollbar>
		</div>
		<button class="app-slider__collapse" @click="app.fold(true)">收起导航 <span aria-hidden="true">←</span></button>
	</div>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'app-slider'
});

import { useBase } from '/$/base';
import { identityName } from '/@/workspace';
import { useBrowser } from '/@/cool';
import BMenu from './bmenu';
import { ref } from 'vue';

const { browser } = useBrowser();
const { app } = useBase();

const keyWord = ref('');
</script>

<style lang="scss">
.app-slider {
  --slider-bg-color: #142139;
  --slider-text-color: #c1ccde;
  display: flex; flex-direction: column; width: 248px; height: 100%;
  box-sizing: border-box; padding: 22px 14px 12px; background: var(--slider-bg-color);
  color: var(--slider-text-color);
  &__logo { display: flex; flex-direction: column; align-items: flex-start; gap: 16px; padding: 8px 12px 28px !important; flex-shrink: 0;
    .app-slider__brand { color: #fff; font-size: 28px !important; line-height: 1.25; font-weight: 750; margin: 0 !important; white-space: nowrap; letter-spacing: -.5px; }
  }
  &__identity { display: flex; align-items: center; gap: 9px; font-size: 13px; line-height: 20px; color: #bacbe8;
    i { width: 6px; height: 6px; flex-shrink: 0; border-radius: 50%; background: color-mix(in srgb, var(--workspace-accent, #254dc8) 45%, white); }
  }
  &__search { margin: 0 2px 18px;
    .el-input__wrapper { min-height: 42px; box-sizing: border-box; padding: 0 14px; background: #1d2b43; box-shadow: inset 0 0 0 1px #ffffff1a; border-radius: 9px; }
    .el-input__inner { color: #f0f4fa; --el-input-placeholder-color: #9cabc0; }
    .el-input__prefix, .el-input__suffix { color: #9cabc0; }
    .el-input__wrapper.is-focus { box-shadow: inset 0 0 0 1px #91a9d6; }
  }
  &__container { flex: 1; min-height: 0;
    // 展开菜单时不让浏览器重新选择滚动锚点。
    .el-scrollbar__wrap { overflow-anchor: none; }
  }
  &__caption { padding: 15px 12px 8px; font-size: 11px; letter-spacing: 2px; color: #8495af; }
  &__assistant.el-button { margin: 0; width: 100%; height: 40px; justify-content: flex-start; padding: 0 12px; color: #e6edf8; font-weight: 600; border-radius: 8px; }
  &__assistant.el-button:hover { background: #ffffff0c; }
  &__collapse { display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; margin-top: 12px; padding: 14px 12px 4px; border: 0; border-top: 1px solid #ffffff12; background: transparent; color: #9cabc0; cursor: pointer; font-size: 12px; }
  &__collapse:hover { color: white; }
  &__menu { user-select: none;
    // 动画临时设置 overflow:hidden；保持独立格式化上下文，避免结束时子项 margin 折叠导致跳位。
    .el-menu { display: flow-root; border: 0; background: transparent; --el-menu-base-level-padding: 12px; --el-menu-level-padding: 12px; }
    .el-menu-item, .el-sub-menu__title { height: 40px; line-height: 40px; margin: 3px 0; border-radius: 8px; color: #c1ccde; font-size: 14px; transition: background-color .18s, color .18s; }
    .el-sub-menu__title { font-weight: 600; padding-right: 30px; }
    .el-sub-menu__icon-arrow { right: 12px; font-size: 11px; color: #8495af; }
    .el-sub-menu > .el-menu { margin-left: 13px; padding-left: 8px; border-left: 1px solid #ffffff16; width: auto; }
    .el-sub-menu .el-menu-item, .el-sub-menu .el-sub-menu__title { padding-left: 12px !important; }
    .el-menu-item:hover, .el-sub-menu__title:hover { background: #ffffff0c; color: #fff; }
    .el-menu-item.is-active { background: var(--workspace-accent, #254dc8); color: #fff; font-weight: 600; }
    .el-sub-menu.is-active > .el-sub-menu__title { color: #f2f6ff; }
    .b-menu__label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .b-menu__notice-dot { width: 6px; height: 6px; border-radius: 50%; background: #ff7979; margin-left: 8px; }
    .b-menu__badge { border-radius: 4px; padding: 0 5px; font-size: 11px; line-height: 18px; background: #ffffff20; color: white; }
  }
  button:focus-visible, .el-menu-item:focus-visible, .el-sub-menu__title:focus-visible { outline: 2px solid #a8c1f6; outline-offset: -2px; }
}
// 浅色导航跟随全站主题；身份主题变量仍由 workspace 统一提供。
html:not(.dark) .app-slider {
  --slider-bg-color: #fafbfc;
  --slider-text-color: #475569;
  border-right: 1px solid #e5e9f0;
  .app-slider__logo .app-slider__brand { color: #17263d; }
  .app-slider__identity { color: #64748b; }
  .app-slider__identity i { background: var(--workspace-accent, #254dc8); }
  .app-slider__search {
    .el-input__wrapper { background: #f0f3f7; box-shadow: inset 0 0 0 1px #e5e9f0; }
    .el-input__inner { color: #243247; --el-input-placeholder-color: #64748b; }
    .el-input__prefix, .el-input__suffix { color: #64748b; }
    .el-input__wrapper.is-focus { box-shadow: inset 0 0 0 1px var(--workspace-accent, #254dc8); }
  }
  .app-slider__caption, .el-sub-menu__icon-arrow { color: #64748b; }
  .app-slider__assistant.el-button { color: #243247; }
  .app-slider__assistant.el-button:hover { background: #edf0f5; }
  .app-slider__collapse { color: #64748b; border-top-color: #e5e9f0; }
  .app-slider__collapse:hover { color: #17263d; }
  .app-slider__menu {
    .el-menu-item, .el-sub-menu__title { color: #475569; }
    .el-sub-menu > .el-menu { border-left-color: #dce2eb; }
    .el-menu-item:hover, .el-sub-menu__title:hover { background: #edf0f5; color: #17263d; }
    .el-menu-item.is-active { background: var(--workspace-selection, #eaf0ff); color: var(--workspace-accent, #254dc8); font-weight: 600; }
    .el-sub-menu.is-active > .el-sub-menu__title { color: var(--workspace-accent, #254dc8); }
    .b-menu__badge { background: #e5e9f0; color: #475569; }
  }
  button:focus-visible, .el-menu-item:focus-visible, .el-sub-menu__title:focus-visible { outline-color: var(--workspace-accent, #254dc8); }
}
@media (prefers-reduced-motion: reduce) { .app-slider * { transition: none !important; } }
</style>

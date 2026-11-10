<template>
	<button type="button" class="cl-comm__icon" :aria-label="$t('设置主题')" @click="open">
		<cl-svg name="theme" />
	</button>

	<button type="button" class="cl-comm__icon ml-[10px]" aria-label="深色模式" :aria-pressed="isDark" @click="setDark">
		<cl-svg :name="isDark ? 'light' : 'dark'" />
	</button>

	<el-drawer
		v-model="visible"
		:title="$t('设置主题')"
		size="350px"
		modal-class="drawer-theme"
		append-to-body
	>
		<div class="cl-theme__drawer">
			<el-form label-position="top">
				<el-form-item label="当前身份主题">
					<span class="cl-theme__identity">{{ identityName }}</span>
				</el-form-item>

				<el-form-item :label="$t('菜单分组显示')">
					<el-switch v-model="theme.isGroup" @change="setGroup" />
				</el-form-item>

				<el-form-item :label="$t('转场动画')">
					<el-switch
						v-model="theme.transition"
						active-value="slide"
						inactive-value="none"
						@change="setTransition"
					/>
				</el-form-item>
			</el-form>
		</div>
	</el-drawer>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'cl-theme'
});

import { ref } from 'vue';
import { useBase } from '/$/base';
import { useTheme } from '../hooks';
import { storeToRefs } from 'pinia';
import { identityName } from '/@/workspace';

const { menu } = useBase();
const themeStore = useTheme();
const { theme, isDark } = storeToRefs(themeStore);
const { setTheme, changeDark } = themeStore;

// 抽屉
const visible = ref(false);

// 打开
function open() {
	visible.value = true;
}

function setDark(event: MouseEvent) {
	const dark = !isDark.value;
	if (event.currentTarget instanceof Element) {
		changeDark(event.currentTarget, dark, () => setTheme({ dark }));
	}
}

// 设置分组
function setGroup() {
	setTheme({ isGroup: theme.value.isGroup });
	menu.setMenu();
}

// 设置转场动画
function setTransition() {
	setTheme({ transition: theme.value.transition });
}
</script>

<style lang="scss">
.cl-theme__identity {
	padding: 3px 8px;
	border-radius: 5px;
	background: var(--workspace-selected);
	color: var(--workspace-accent-text);
	font-size: 12px;
	font-weight: 650;
}

.drawer-theme {
	.el-drawer__header {
		padding: 20px 20px;
		margin-bottom: 0;
	}

	.el-drawer__title {
		font-size: 16px;
	}

}
</style>

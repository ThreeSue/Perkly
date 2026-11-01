<template>
	<div class="app-layout" :class="{ 'is-collapse': app.isFold, 'is-full': app.isFull }">
		<div class="app-layout__mask" @click="app.fold(true)"></div>

		<div class="app-layout__left">
			<slider />
		</div>

		<div class="app-layout__right">
			<topbar />
			<process />
			<views />
		</div>
	</div>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'app-layout'
});

import { onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { isPlatform } from '/@/workspace';
import { workspaceApi } from '/$/workspace/api';
const route = useRoute(), router = useRouter();
import { useBase } from '/$/base';
import Topbar from './components/topbar.vue';
import Slider from './components/slider.vue';
import process from './components/process.vue';
import Views from './components/views.vue';

const { app, menu, user } = useBase();
// 上级授权变化后刷新当前身份的菜单，不清除登录会话。
let permissionTimer: ReturnType<typeof setInterval>;
let syncing = false;
async function syncPermissions() {
 if (isPlatform || syncing || document.hidden || !user.token) return;
 syncing = true;
 try {
  const result = await workspaceApi('permmenu');
  if (JSON.stringify(result.menus) !== JSON.stringify(menu.all)) {
   const previous = menu.routes.some(m => m.path === route.path);
   await menu.get();
   if (previous && !menu.routes.some(m => m.path === route.path)) {
    ElMessage.info('上级已调整权限，当前页面已无权访问');
    await router.replace(menu.routes[0]?.path || '/workspace-empty');
   }
  }
 } catch { /* 请求层处理会话失效；网络异常保留现有页面并等待重试。 */ }
 finally { syncing = false; }
}
onMounted(() => {
 permissionTimer = setInterval(syncPermissions, 15000);
 window.addEventListener('focus', syncPermissions);
});
onBeforeUnmount(() => {
 clearInterval(permissionTimer);
 window.removeEventListener('focus', syncPermissions);
});
</script>

<style lang="scss" scoped>
.app-global {
	position: absolute;
	left: 0;
	top: 0;
}

.app-layout {
	display: flex;
	background-color: var(--bg-color);
	height: 100%;
	width: 100%;
	overflow: hidden;

	&__left {
		overflow: hidden;
		height: 100%;
		width: 255px;
		transition: left 0.2s;
	}

	&__right {
		display: flex;
		flex-direction: column;
		height: 100%;
		width: calc(100% - 255px);
	}

	&__mask {
		position: fixed;
		left: 0;
		top: 0;
		background-color: rgba(0, 0, 0, 0.5);
		height: 100%;
		width: 100%;
		z-index: 999;
	}

	@media only screen and (max-width: 768px) {
		.app-layout__left {
			position: absolute;
			left: 0;
			z-index: 9999;
			transition:
				transform 0.3s cubic-bezier(0.7, 0.3, 0.1, 1),
				box-shadow 0.3s cubic-bezier(0.7, 0.3, 0.1, 1);
		}

		.app-layout__right {
			width: 100%;
		}

		&.is-collapse {
			.app-layout__left {
				transform: translateX(-100%);
			}

			.app-layout__mask {
				display: none;
			}
		}
	}

	@media only screen and (min-width: 768px) {
		.app-layout__left,
		.app-layout__right {
			transition: width 0.2s ease-in-out;
		}

		.app-layout__mask {
			display: none;
		}

		&.is-collapse {
			.app-layout__left {
				width: 67px;
			}

			.app-layout__right {
				width: calc(100% - 67px);
			}
		}
	}

	&.is-full {
		.app-layout__left {
			width: 0;
		}

		.app-layout__right {
			width: 100%;

			:deep(.a-menu),
			:deep(.app-topbar) {
				padding: 0;
				height: 0;
				overflow: hidden;
			}
		}
	}
}
</style>

<template>
	<button type="button" class="pic-captcha" aria-label="刷新图片验证码" :aria-busy="loading" :disabled="loading" @click="refresh">
		<span v-if="svg" class="svg" v-html="svg" />
		<img v-else-if="base64" class="base64" :src="base64" alt="" />
		<el-icon v-else-if="loading" class="is-loading" :size="18"><loading /></el-icon>
		<span v-else class="retry" role="status">加载失败，点击重试</span>
	</button>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'pic-captcha'
});

import { isPlatform } from '/@/workspace';
import { merchantRequest } from '/@/merchant-api';
import { onMounted, ref } from 'vue';
import { Loading } from '@element-plus/icons-vue';
import { useCool } from '/@/cool';

const emit = defineEmits(['update:modelValue', 'change']);

const { service } = useCool();

// base64
const base64 = ref('');

// svg
const svg = ref('');
const loading = ref(false);

// 刷新
async function refresh() {
	if (loading.value) return;
	loading.value = true;
	svg.value = '';
	base64.value = '';
	emit('update:modelValue', '');
	emit('change');

	try {
		const { captchaId, data } = await (isPlatform
			? service.base.open.captcha({ height: 45, width: 150, color: '#2c3142' })
			: merchantRequest('auth/captcha'));
		if (!data || !captchaId) return;
		if (data.includes(';base64,')) base64.value = data;
		else svg.value = data;
		emit('update:modelValue', captchaId);
	} catch {
		// 空图进入可重试状态。
	} finally {
		loading.value = false;
	}
}

onMounted(() => {
	refresh();
});

defineExpose({
	refresh
});
</script>

<style lang="scss" scoped>
.pic-captcha {
	display: flex;
	justify-content: center;
	align-items: center;
	cursor: pointer;
	height: 45px;
	width: 150px;
	position: relative;
	user-select: none;
	border: 0;
	padding: 0;
	border-radius: var(--workspace-control-radius);
	background: var(--el-bg-color);
	color: var(--el-text-color-regular);
	font-size: 12px;
	&:focus-visible { outline: 2px solid var(--workspace-accent-text); outline-offset: -2px; }
	&:disabled { cursor: wait; }

	.svg {
		height: 100%;
		position: relative;
	}

	.base64 {
		height: 100%;
		max-width: 100%;
		object-fit: contain;
	}

	.is-loading {
		position: absolute;
		right: 15px;
	}
}
</style>

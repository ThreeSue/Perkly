<template>
	<div class="pic-captcha" @click="refresh">
		<div v-if="svg" class="svg" v-html="svg" />
		<img v-else-if="base64" class="base64" :src="base64" alt="" />

		<template v-else>
			<el-icon class="is-loading" :size="18">
				<loading />
			</el-icon>
		</template>
	</div>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'pic-captcha'
});

import { onMounted, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { Loading } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { merchantRequest } from '/@/merchant-api';

let generation = 0;

const emit = defineEmits(['update:modelValue', 'change']);

const { t } = useI18n();

// base64
const base64 = ref('');

// svg
const svg = ref('');

// 刷新
async function refresh() {
	const current = ++generation;
	svg.value = '';
	base64.value = '';

	await merchantRequest('auth/captcha')
		.then(({ captchaId, data }) => {
			if (current !== generation) return;
			if (data) {
				if (data.includes(';base64,')) {
					base64.value = data;
				} else {
					svg.value = data;
				}

				emit('update:modelValue', captchaId);
				emit('change', {
					base64,
					svg,
					captchaId
				});
			} else {
				ElMessageBox.alert(t('验证码获取失败'), {
					title: t('提示'),
					type: 'error'
				});
			}
		})
		.catch(err => {
			if (current !== generation) return;
			ElMessageBox.alert(err.message, {
				title: t('提示'),
				type: 'error'
			});
		});
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

	.svg {
		height: 100%;
		position: relative;
	}

	.base64 {
		height: 100%;
	}

	.is-loading {
		position: absolute;
		right: 15px;
	}
}
</style>

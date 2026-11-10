<template>
	<div class="error-page">
		<div class="error-page__wrap">
			<p class="error-page__code">{{ code }}</p>
			<h1 class="error-page__desc">{{ desc }}</h1>

			<template v-if="user.token || isLogout">
				<div class="error-page__btns">
					<el-button @click="home">{{ $t('返回首页') }}</el-button>
					<el-button type="primary" @click="reLogin">{{ $t('重新登录') }}</el-button>
				</div>
			</template>

			<template v-else>
				<div class="error-page__btns">
					<el-button type="primary" @click="toLogin">{{ $t('返回登录页') }}</el-button>
				</div>
			</template>
		</div>
	</div>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'error-page'
});

import { ref } from 'vue';
import { useCool } from '/@/cool';
import { useBase } from '/$/base';

defineProps({
	code: Number,
	desc: String
});

const { router } = useCool();
const { user } = useBase();

const isLogout = ref(false);

function toLogin() {
	router.push('/login');
}

async function reLogin() {
	isLogout.value = true;
	user.logout();
}

function home() {
	router.push('/');
}
</script>

<style lang="scss" scoped>
.error-page {
	background-color: var(--el-bg-color-page);
	height: 100%;
	overflow: auto;
	display: grid;
	place-items: center;

	&__wrap {
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		padding: 24px;
		text-align: center;
	}

	&__code {
		margin: 0;
		color: var(--workspace-accent-text);
		font-size: 64px;
		font-weight: 700;
		font-variant-numeric: tabular-nums;
		line-height: 1.15;
	}

	&__desc {
		margin: 12px 0 0;
		font-size: 20px;
		font-weight: 650;
		color: var(--el-text-color-primary);
	}

	&__btns {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 8px;
		margin-top: 24px;

		.el-button {
			margin: 0;
		}
	}
}
</style>

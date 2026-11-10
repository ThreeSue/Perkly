<template>
	<article class="chat-coupon-card" :aria-label="title || '优惠券资料'">
		<el-image v-if="coverUrl" class="cover" :src="coverUrl" fit="cover" alt="优惠券封面">
			<template #error><span class="cover-fallback">优惠券</span></template>
		</el-image>
		<span v-else class="cover cover-fallback">优惠券</span>
		<div class="details">
			<span class="kind">{{ held ? '顾客持有的券' : '优惠券' }}</span>
			<strong>{{ title || '优惠券资料暂不可用' }}</strong>
			<span v-if="merchantName">{{ merchantName }}</span>
			<span v-if="validityText">{{ validityText }}</span>
			<span v-if="statusText" class="status">{{ statusText }}</span>
		</div>
		<p v-if="unavailable" class="notice" role="status">
			{{ unavailableReason || '该优惠券已失效或暂不可查看，请以最新券状态为准。' }}
		</p>
	</article>
</template>

<script setup lang="ts">
// 展示组件仅接收允许公开的字段；接口适配层不应传入券码或核销凭证。
defineProps<{
	title?: string;
	coverUrl?: string;
	merchantName?: string;
	validityText?: string;
	statusText?: string;
	held?: boolean;
	unavailable?: boolean;
	unavailableReason?: string;
}>();
</script>

<style scoped>
.chat-coupon-card {
	display: grid;
	grid-template-columns: 64px minmax(0, 1fr);
	gap: 12px;
	width: min(320px, 100%);
	box-sizing: border-box;
	padding: 12px;
	border: 1px solid var(--el-border-color-light);
	border-radius: 12px;
	background: var(--el-bg-color);
	color: var(--el-text-color-primary);
}
.cover {
	width: 64px;
	height: 76px;
	border-radius: 6px;
	overflow: hidden;
}
.cover-fallback {
	display: grid;
	place-items: center;
	height: 100%;
	background: var(--workspace-accent-soft, var(--el-fill-color-light));
	color: var(--workspace-accent-text, var(--el-color-primary));
	font-size: 12px;
}
.cover.cover-fallback {
	height: 76px;
}
.details {
	display: grid;
	gap: 4px;
	min-width: 0;
	overflow-wrap: anywhere;
}
.details strong {
	font-size: 14px;
	line-height: 1.5;
}
.details span {
	font-size: 12px;
	line-height: 1.5;
	color: var(--el-text-color-regular);
}
.details .status {
	font-weight: 600;
	color: var(--el-text-color-primary);
}
.notice {
	grid-column: 1 / -1;
	margin: 0;
	padding-top: 8px;
	border-top: 1px solid var(--el-border-color-lighter);
	font-size: 12px;
	line-height: 1.6;
	color: var(--el-text-color-regular);
}
</style>

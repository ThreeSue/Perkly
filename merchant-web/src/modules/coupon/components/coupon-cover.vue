<template>
	<button
		type="button"
		class="coupon-cover"
		:aria-label="'预览' + (name || '优惠券封面')"
		@click="visible = true"
	>
		<el-image class="cover-image" :src="src" :alt="name" fit="cover">
			<template #error><span class="missing">暂无图</span></template>
		</el-image>
		<span class="cover-badge" :class="{ compact: badge.length > 5 }">{{ badge }}</span>
	</button>
	<el-dialog
		v-model="visible"
		:title="name || '优惠券封面'"
		width="390px"
		align-center
		append-to-body
	>
		<div class="cover-preview">
			<el-image class="preview-image" :src="src" :alt="name" fit="cover">
				<template #error><span class="missing">暂无封面</span></template>
			</el-image>
			<div class="preview-caption">
				<div class="preview-headline">
					<strong>{{ badge }}</strong
					><span>{{ typeName }}</span>
				</div>
				<p>{{ subtitle }}</p>
			</div>
		</div>
	</el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
withDefaults(
	defineProps<{
		src?: string;
		name?: string;
		badge: string;
		typeName?: string;
		subtitle?: string;
	}>(),
	{ src: '', name: '', typeName: '', subtitle: '' }
);
const visible = ref(false);
</script>

<style scoped>
.coupon-cover {
	position: relative;
	display: block;
	width: 44px;
	height: 44px;
	padding: 0;
	overflow: hidden;
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 6px;
	background: var(--el-fill-color-light);
	cursor: pointer;
}
.coupon-cover:focus-visible {
	outline: 2px solid var(--el-color-primary);
	outline-offset: 2px;
}
.cover-image {
	width: 100%;
	height: 100%;
}
.cover-badge {
	position: absolute;
	bottom: 0;
	left: 0;
	right: 0;
	height: 17px;
	line-height: 17px;
	background: rgba(255, 255, 255, 0.95);
	color: #174d46;
	font-size: 12px;
	font-weight: 750;
	text-align: center;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}
.cover-badge.compact {
	font-size: 10px;
}
.cover-preview {
	position: relative;
	aspect-ratio: 1;
	border-radius: 12px;
	overflow: hidden;
	background: #f6f4ef;
}
.preview-image {
	width: 100%;
	height: 100%;
}
.preview-caption {
	position: absolute;
	bottom: 0;
	left: 0;
	right: 0;
	padding: 14px 18px;
	background: rgba(255, 255, 255, 0.96);
	color: #243c37;
}
.preview-headline {
	display: flex;
	align-items: baseline;
	gap: 12px;
}
.preview-headline strong {
	font-size: 38px;
	line-height: 1.2;
	font-weight: 750;
	color: #174d46;
}
.preview-headline span {
	font-size: 14px;
	color: #687570;
}
.preview-caption p {
	margin: 5px 0 0;
	font-size: 13px;
	line-height: 1.5;
	overflow-wrap: anywhere;
}
.missing {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 100%;
	height: 100%;
	font-size: 10px;
	color: var(--el-text-color-secondary);
}
</style>

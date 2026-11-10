<template>
	<header class="directory-header">
		<div class="directory-title">
			<h1>{{ title }}</h1>
			<p><span>{{ scopeLabel }}</span><strong>{{ scopeName }}</strong></p>
		</div>
		<slot name="metrics" />
		<div class="directory-create"><slot name="actions" /></div>
	</header>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title?: string; scopeLabel: string; scopeName: string }>(), { title: '门店信息' });
</script>

<style scoped>
.directory-header {
	--directory-hero: var(--workspace-accent);
	display: grid;
	grid-template-columns: minmax(220px, 1fr) auto auto;
	align-items: stretch;
	overflow: hidden;
	border-radius: 8px;
	background: var(--directory-hero);
	color: #fff;
}
html[data-workspace="platform"] .directory-header { --directory-hero: #2146b6; }
.directory-title { min-width: 0; padding: 20px 26px; }
.directory-title h1 { margin: 0; font-size: 32px; line-height: 1.12; font-weight: 750; letter-spacing: -.035em; }
.directory-title p { display: flex; align-items: baseline; flex-wrap: wrap; gap: 3px 8px; margin: 8px 0 0; color: #e3ebff; font-size: 12px; }
.directory-title p strong { color: #fff; font-size: 13px; overflow-wrap: anywhere; }
:deep(.directory-counts) {
	display: grid;
	grid-template-columns: repeat(4, minmax(87px, 1fr));
	margin-block: 10px;
	background: color-mix(in srgb, var(--directory-hero), black 15%);
}
:deep(.directory-counts button) {
	padding: 12px;
	border: 0;
	border-inline-start: 1px solid #ffffff36;
	background: transparent;
	color: #e3ebff;
	font: inherit;
	font-size: 12px;
	text-align: start;
	cursor: pointer;
}
:deep(.directory-counts button:hover) { background: color-mix(in srgb, var(--directory-hero), white 9%); }
:deep(.directory-counts button[aria-pressed="true"]) { background: color-mix(in srgb, var(--directory-hero), white 7%); box-shadow: inset 0 -3px #ffcf55; }
:deep(.directory-counts button:focus-visible) { outline: 2px solid white; outline-offset: -4px; }
:deep(.directory-counts strong) { display: block; margin-top: 2px; color: #fff; font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; }
.directory-create { align-self: center; grid-column: 3; }
.directory-create:has(.store-create) { padding-inline: 18px; }
.directory-create :deep(.store-create) {
	--el-button-bg-color: #ffcf55;
	--el-button-border-color: #ffcf55;
	--el-button-text-color: #19223a;
	--el-button-hover-bg-color: #ffe08f;
	--el-button-hover-border-color: #ffe08f;
	--el-button-hover-text-color: #19223a;
	--el-button-active-bg-color: #f3bb35;
	--el-button-active-border-color: #f3bb35;
	--el-button-active-text-color: #19223a;
	font-weight: 700;
	min-height: 42px;
}
.directory-create :deep(.store-create:focus-visible) { outline: 2px solid white; outline-offset: 3px; }
@media (max-width: 1050px) {
	.directory-header { grid-template-columns: minmax(0, 1fr) auto; }
	.directory-create { grid-column: 2; }
	:deep(.directory-counts) { grid-row: 2; grid-column: 1 / -1; grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 480px) {
	.directory-title { padding: 14px 12px; }
	.directory-title h1 { font-size: 24px; }
	:deep(.directory-counts button) { padding: 8px 6px; }
	.directory-create:has(.store-create) { padding-inline: 10px; }
}
</style>

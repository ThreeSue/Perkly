<template>
	<div class="workbench">
		<header class="workbench-banner">
			<div class="workbench-heading">
				<h1>{{ title }}</h1>
				<p v-if="context">{{ context }}</p>
			</div>
			<div v-if="$slots.actions" class="workbench-banner-actions"><slot name="actions" /></div>
			<div v-if="$slots.metrics" class="workbench-banner-metrics"><slot name="metrics" /></div>
		</header>
		<slot />
	</div>
</template>

<script setup lang="ts">
defineProps<{ title: string; context?: string }>();
</script>

<style>
.workbench {
	box-sizing: border-box;
	height: 100%;
	overflow: auto;
	padding: 0;
	color: var(--workspace-ink);
	font-variant-numeric: tabular-nums;
}
.workbench-banner {
	--workbench-brand: var(--workspace-accent);
	display: grid;
	grid-template-columns: minmax(0, 1fr) auto;
	align-items: stretch;
	overflow: hidden;
	border-radius: 8px;
	background: var(--workbench-brand);
	color: white;
}
html[data-workspace="platform"] .workbench-banner { --workbench-brand: #2146b6; }
.workbench-heading { min-width: 0; padding: 23px 28px; }
.workbench-heading h1 {
	margin: 0;
	font-size: 32px;
	line-height: 1.15;
	font-weight: 750;
	letter-spacing: -.035em;
	overflow-wrap: anywhere;
}
.workbench-heading p {
	margin: 8px 0 0;
	color: color-mix(in srgb, white, var(--workbench-brand) 14%);
	font-size: 13px;
	line-height: 1.55;
	overflow-wrap: anywhere;
}
.workbench-banner-actions {
	display: flex;
	align-items: center;
	justify-content: flex-end;
	gap: 10px;
	padding-inline: 18px;
}
.workbench .workbench-refresh {
	--el-button-bg-color: color-mix(in srgb, var(--workbench-brand), white 10%);
	--el-button-text-color: white;
	--el-button-border-color: color-mix(in srgb, var(--workbench-brand), white 52%);
	--el-button-hover-bg-color: color-mix(in srgb, var(--workbench-brand), white 17%);
	--el-button-hover-text-color: white;
	--el-button-hover-border-color: white;
	--el-button-active-bg-color: color-mix(in srgb, var(--workbench-brand), black 14%);
	--el-button-active-text-color: white;
	--el-button-active-border-color: white;
}
.workbench .workbench-refresh:focus-visible { outline-color: white; }
.workbench-banner-metrics {
	display: grid;
	grid-column: 1 / -1;
	grid-template-columns: repeat(auto-fit, minmax(min(154px, 100%), 1fr));
	background: color-mix(in srgb, var(--workbench-brand), black 16%);
	border-block-start: 1px solid #ffffff29;
}
.workbench-banner-metric {
	padding: 13px 20px;
	color: inherit;
	border-inline-end: 1px solid #ffffff29;
}
.workbench-banner-metric:last-child { border-inline-end: 0; }
.workbench-banner-metric span { font-size: 13px; }
.workbench-banner-metric strong {
	display: block;
	margin-block: 4px;
	font-size: 28px;
	line-height: 1.1;
}
.workbench-banner-metric small {
	display: block;
	color: color-mix(in srgb, white, var(--workbench-brand) 16%);
	font-size: 12px;
}
.workbench-grid {
	display: grid;
	grid-template-columns: minmax(0, 1.65fr) minmax(280px, 1fr);
	align-items: start;
	gap: 16px;
	margin-block-start: 16px;
}
.workbench-stack { display: grid; gap: 16px; min-width: 0; }
.workbench-panel {
	min-width: 0;
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 8px;
	background: var(--el-bg-color);
}
.workbench-section-head {
	display: flex;
	justify-content: space-between;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px 12px;
	padding: 16px 18px 12px;
}
.workbench-section-head h2 { margin: 0; font-size: 17px; letter-spacing: -.015em; }
.workbench-section-head p {
	margin: 4px 0 0;
	color: var(--el-text-color-regular);
	font-size: 12px;
	line-height: 1.55;
}
.workbench-inline-alert { margin-top: 14px; }
.workbench .workbench-link:focus-visible { outline: 2px solid var(--workspace-accent-text); outline-offset: -3px; }
@media (min-width: 1280px) {
	.workbench-banner:has(.workbench-banner-metrics) {
		grid-template-columns: minmax(250px, 1fr) minmax(550px, 1.7fr) auto;
	}
	.workbench-banner-metrics {
		grid-column: 2;
		grid-row: 1;
		align-self: center;
		margin-block: 10px;
		grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
		border-block-start: 0;
	}
	.workbench-banner:has(.workbench-banner-metrics) .workbench-banner-actions { grid-column: 3; grid-row: 1; }
	.workbench-banner-metric { padding: 10px 14px; }
}
@media (max-width: 960px) {
	.workbench-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 590px) {
	.workbench { padding: 0; }
	.workbench-heading { padding: 16px 14px; }
	.workbench-heading h1 { font-size: 24px; }
	.workbench-banner-actions { padding-inline: 10px; }
	.workbench-banner-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
	.workbench-banner-metric { padding: 10px 14px; border-block-end: 1px solid #ffffff29; }
}
</style>

<template>
	<div class="management-shell" :class="{ 'has-sidebar': sidebar }">
		<aside v-if="sidebar" class="management-sidebar" :aria-label="scopeLabel + '筛选'">
			<div class="scope-heading">
				<strong>{{ scopeLabel }}</strong
				><span>{{ items.length }} 项</span>
			</div>
			<el-input
				v-model="scopeKeyword"
				clearable
				:placeholder="'搜索' + scopeLabel"
				:aria-label="'搜索' + scopeLabel"
			/>
			<button
				class="scope-option scope-all"
				:class="{ active: scopeId == null }"
				@click="emit('scope-change', undefined)"
			>
				<span class="scope-avatar">全</span
				><span class="scope-copy"
					><strong>{{ allLabel || '全部' + scopeLabel }}</strong
					><small>{{ allHint }}</small></span
				>
			</button>
			<div class="scope-list">
				<button
					v-for="item in filteredScopes"
					:key="item.id"
					class="scope-option"
					:class="{ active: String(scopeId) === String(item.id) }"
					@click="emit('scope-change', item.id)"
				>
					<span class="scope-avatar">{{ item.name?.slice(0, 1) || '商' }}</span>
					<span class="scope-copy"
						><strong :title="item.name">{{ item.name }}</strong
						><small>{{
							item.contactPhone || (item.status === 0 ? '已停用' : '点击查看')
						}}</small></span
					>
				</button>
				<el-empty
					v-if="!filteredScopes.length"
					:description="scopeKeyword ? '没有匹配项' : '暂无可选' + scopeLabel"
					:image-size="48"
				/>
			</div>
			<div class="scope-footer">
				<slot name="scope-footer">选择左侧{{ scopeLabel }}，右侧筛选与列表同步更新</slot>
			</div>
		</aside>
		<main class="management-main">
			<header class="management-heading">
				<div class="heading-copy">
					<div class="heading-title-line">
						<h2>{{ title }}</h2>
						<span v-if="scopeName" class="heading-scope" :title="scopeName"
							>当前范围：{{ scopeName }}</span
						>
					</div>
					<p>{{ description }}</p>
				</div>
				<div v-if="metrics.length" class="management-metrics">
					<div v-for="metric in metrics" :key="metric.label" class="management-metric">
						<span>{{ metric.label }}</span
						><strong>{{ metric.value ?? '—' }}</strong
						><small v-if="metric.hint">{{ metric.hint }}</small>
					</div>
				</div>
			</header>
			<div class="management-body"><slot /></div>
		</main>
	</div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
const props = withDefaults(
	defineProps<{
		title: string;
		description?: string;
		eyebrow?: string;
		scopeName?: string;
		sidebar?: boolean;
		scopeId?: number;
		scopeLabel?: string;
		allLabel?: string;
		allHint?: string;
		items?: Array<{ id: number; name: string; status?: number; contactPhone?: string }>;
		metrics?: Array<{ label: string; value?: number | string; hint?: string }>;
	}>(),
	{
		description: '',
		eyebrow: '管理中心',
		sidebar: false,
		scopeLabel: '商家',
		allHint: '查看全部范围',
		items: () => [],
		metrics: () => []
	}
);
const emit = defineEmits<{ 'scope-change': [id: number | undefined] }>();
const scopeKeyword = ref('');
const filteredScopes = computed(() => {
	const word = scopeKeyword.value.trim().toLowerCase();
	return props.items.filter(item =>
		`${item.name} ${item.contactPhone || ''}`.toLowerCase().includes(word)
	);
});
</script>
<style>
.management-shell {
	height: 100%;
	min-height: 0;
	display: flex;
	gap: 16px;
	padding: 16px;
	box-sizing: border-box;
	background: var(--el-fill-color-light);
	color: var(--el-text-color-primary);
}
.management-sidebar {
	width: 224px;
	flex: 0 0 224px;
	min-height: 0;
	display: flex;
	flex-direction: column;
	gap: 12px;
	padding: 16px 12px;
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 8px;
	background: var(--el-bg-color);
	box-sizing: border-box;
}
.scope-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 4px;
}
.scope-heading span,
.scope-footer {
	color: var(--el-text-color-secondary);
	font-size: 12px;
}
.scope-list {
	flex: 1;
	min-height: 0;
	overflow-y: auto;
	display: flex;
	flex-direction: column;
	gap: 6px;
}
.scope-option {
	display: flex;
	align-items: center;
	gap: 10px;
	width: 100%;
	border: 1px solid transparent;
	border-radius: 10px;
	background: transparent;
	padding: 11px 9px;
	text-align: left;
	cursor: pointer;
	color: inherit;
	font: inherit;
	box-sizing: border-box;
}
.scope-option:hover {
	background: var(--el-fill-color-light);
}
.scope-option.active {
	background: var(--el-color-primary-light-9);
	border-color: var(--el-color-primary-light-7);
}
.scope-option:focus-visible {
	outline: 2px solid var(--el-color-primary);
	outline-offset: -2px;
}
.scope-avatar {
	display: grid;
	place-items: center;
	width: 34px;
	height: 34px;
	flex: none;
	border-radius: 9px;
	background: var(--el-color-primary-light-8);
	color: var(--el-color-primary);
	font-weight: 700;
}
.scope-all .scope-avatar {
	background: var(--el-text-color-regular);
	color: var(--el-bg-color);
}
.scope-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 5px;
}
.scope-copy strong {
	font-size: 13px;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}
.scope-copy small {
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.scope-footer {
	border-top: 1px solid var(--el-border-color-lighter);
	padding-top: 12px;
	line-height: 1.7;
}
.management-main {
	flex: 1;
	min-width: 0;
	min-height: 0;
	display: flex;
	flex-direction: column;
	gap: 12px;
}
.management-heading {
	flex: none;
	padding: 12px 18px;
	border-radius: 8px;
	background: var(--el-bg-color);
	border: 1px solid var(--el-border-color-lighter);
	color: var(--el-text-color-primary);
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	flex-wrap: wrap;
}
.heading-copy {
	min-width: 0;
}
.heading-title-line {
	display: flex;
	align-items: center;
	gap: 12px;
	min-width: 0;
}
.heading-eyebrow {
	font-size: 11px;
	letter-spacing: 1px;
	color: var(--el-color-primary);
}
.heading-copy h2 {
	margin: 0;
	font-size: 19px;
	line-height: 28px;
	font-weight: 650;
	flex: none;
}
.heading-copy p {
	margin: 4px 0 0;
	color: var(--el-text-color-secondary);
	font-size: 13px;
	line-height: 1.4;
}
.heading-scope {
	display: inline-block;
	margin-top: 0;
	padding: 3px 8px;
	max-width: 38ch;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	border-radius: 5px;
	background: var(--el-color-primary-light-9);
	color: var(--el-color-primary);
	font-size: 12px;
}
.management-metrics {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	flex: none;
}
.management-metric {
	min-width: 64px;
	padding: 6px 12px;
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 10px;
	background: var(--el-fill-color-light);
	display: flex;
	flex-direction: column;
	gap: 2px;
}
.management-metric > span {
	color: var(--el-text-color-secondary);
	font-size: 12px;
}
.management-metric strong {
	color: var(--el-color-primary);
	font-size: 21px;
	line-height: 25px;
	font-variant-numeric: tabular-nums;
}
.management-metric small {
	color: var(--el-color-primary);
	font-size: 11px;
}
.management-body {
	flex: 1;
	min-height: 0;
	min-width: 0;
	background: var(--el-bg-color);
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 8px;
	padding: 18px;
	overflow: auto;
	box-sizing: border-box;
}
.management-toolbar {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10px;
	margin-bottom: 14px;
}
.management-toolbar .toolbar-spacer {
	flex: 1;
}
.management-toolbar .el-input {
	width: 240px;
}
.management-toolbar .el-select {
	width: 160px;
}
.management-toolbar .el-button {
	margin-left: 0;
}
.management-toolbar .filter-label {
	color: var(--el-text-color-secondary);
	font-size: 13px;
}
.management-caption {
	color: var(--el-text-color-secondary);
	font-size: 12px;
	line-height: 1.7;
	margin: 8px 0 14px;
}
.management-filter-note {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 8px;
	margin: 0 0 14px;
}
.management-table {
	width: 100%;
}
.management-table .el-table__cell {
	padding: 13px 0;
}
.cell-main {
	font-weight: 600;
}
.cell-sub {
	color: var(--el-text-color-secondary);
	font-size: 12px;
	margin-top: 5px;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.management-pagination {
	display: flex;
	justify-content: flex-end;
	padding-top: 16px;
}
.management-summary {
	display: flex;
	gap: 10px;
	flex-wrap: wrap;
	padding: 12px 14px;
	margin-bottom: 16px;
	border-radius: 8px;
	background: var(--el-fill-color-light);
	line-height: 1.7;
}
.management-summary strong {
	color: var(--el-color-primary);
}
@media (max-width: 1250px) {
	.management-metric {
		min-width: 58px;
		padding: 6px 10px;
	}
	.management-sidebar {
		width: 200px;
		flex-basis: 200px;
	}
}
@media (max-width: 850px) {
	.management-shell {
		flex-direction: column;
		overflow: auto;
		padding: 10px;
		gap: 10px;
	}
	.management-sidebar {
		width: 100%;
		flex: 0 0 auto;
		max-height: none;
		display: grid;
		grid-template-columns: 150px minmax(0, 1fr);
		gap: 10px;
	}
	.scope-heading,
	.management-sidebar > .el-input {
		grid-column: 1 / -1;
	}
	.scope-all {
		grid-column: 1;
		grid-row: 3;
	}
	.scope-list {
		grid-column: 2;
		grid-row: 3;
		display: flex;
		flex-direction: row;
		overflow-x: auto;
		overflow-y: hidden;
		max-height: none;
	}
	.scope-list .scope-option {
		flex: 0 0 175px;
	}
	.scope-footer {
		display: none;
	}
	.management-main {
		flex: 1 0 auto;
	}
	.management-body {
		min-height: 380px;
		overflow: visible;
	}
	.management-heading {
		padding: 12px;
		align-items: flex-start;
		flex-direction: column;
	}
	.management-toolbar .el-input {
		width: 100%;
	}
	.management-toolbar .el-select {
		flex: 1;
	}
	.management-metrics {
		width: 100%;
	}
	.management-metric {
		flex: 1;
		min-width: 60px;
	}
	.heading-copy h2 {
		font-size: 18px;
	}
}
</style>

<template>
	<WorkbenchLayout title="个人中心" :context="identityName">
		<div class="personal-layout">
			<section class="workbench-panel personal-summary" aria-label="账号概况">
				<div class="personal-avatar" aria-hidden="true">{{ initial }}</div>
				<h2>{{ name || '个人账号' }}</h2>
				<dl v-if="fields.length">
					<div v-for="field in fields" :key="field.label">
						<dt>{{ field.label }}</dt>
						<dd>{{ field.value || '—' }}</dd>
					</div>
				</dl>
			</section>
			<div class="personal-forms"><slot /></div>
		</div>
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import WorkbenchLayout from './workbench-layout.vue';
import { identityName } from '/@/workspace';

const props = defineProps<{
	name?: string;
	fields: { label: string; value?: string }[];
}>();
const initial = computed(() => Array.from(props.name?.trim() || '人')[0]);
</script>

<style>
.personal-layout {
	display: grid;
	grid-template-columns: minmax(190px, 260px) minmax(0, 660px);
	align-items: start;
	gap: 16px;
	margin-block-start: 16px;
}
.personal-summary,
.personal-section {
	padding: 18px;
}
.personal-summary h2,
.personal-section h2 {
	margin: 0 0 12px;
	font-size: 17px;
	overflow-wrap: anywhere;
}
.personal-avatar {
	display: grid;
	place-items: center;
	width: 42px;
	height: 42px;
	margin-bottom: 12px;
	border-radius: 8px;
	background: var(--workspace-accent-soft);
	color: var(--workspace-accent-text);
	font-weight: 700;
}
.personal-summary dl {
	display: grid;
	gap: 8px;
	margin: 0;
}
.personal-summary dl div {
	display: grid;
	gap: 3px;
}
.personal-summary dt,
.personal-section p {
	color: var(--el-text-color-regular);
	font-size: 13px;
}
.personal-summary dd {
	margin: 0;
	overflow-wrap: anywhere;
}
.personal-forms {
	display: grid;
	gap: 16px;
	min-width: 0;
}
.personal-section p {
	margin: 0 0 14px;
	line-height: 1.6;
}
.personal-section .el-form {
	max-width: 500px;
}
@media (max-width: 730px) {
	.personal-layout {
		grid-template-columns: minmax(0, 1fr);
	}
	.personal-summary dl {
		grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
	}
}
</style>

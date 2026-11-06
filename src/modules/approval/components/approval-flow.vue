<template>
	<section class="approval-flow" aria-label="审批流程">
		<header class="flow-heading">
			<div>
				<h3>审批流程</h3>
				<p>提交来源、审核结果与下一步处理</p>
			</div>
			<el-tag :type="flow.tagType">{{ flow.nextStep }}</el-tag>
		</header>
		<el-alert
			v-if="flow.historical"
			title="历史记录缺少部分操作人和时间，缺失信息已明确标注。"
			type="warning"
			:closable="false"
			show-icon
		/>
		<ol class="flow-list">
			<li
				v-for="(node, index) in nodes"
				:key="node.key"
				:class="['flow-node', node.state]"
				:aria-current="node.state === 'current' ? 'step' : undefined"
			>
				<span class="node-marker" aria-hidden="true">{{
					node.state === 'done'
						? '✓'
						: node.state === 'rejected'
							? '×'
							: node.state === 'withdrawn'
								? '↩'
								: index + 1
				}}</span>
				<article class="node-card">
					<div class="node-heading">
						<strong>{{ node.title }}</strong
						><span class="node-status">{{ node.badge }}</span>
					</div>
					<div class="node-actor">
						{{ node.actorLabel }}：<b>{{ node.actor }}</b>
					</div>
					<p class="node-context">{{ node.context }}</p>
					<div v-if="node.time || node.round || node.historical" class="node-meta">
						<span v-if="node.round">第 {{ node.round }} 轮</span>
						<time v-if="node.time">{{ formatTime(node.time) }}</time>
						<span v-else-if="node.historical">处理时间未记录</span>
					</div>
					<p v-if="node.reason" class="node-reason">处理意见：{{ node.reason }}</p>
				</article>
			</li>
		</ol>
	</section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import dayjs from 'dayjs';
import { buildApprovalFlow, type ApprovalDetail, type FlowNode } from '../utils/flow';

type FlowPresentation = {
	nodes: FlowNode[];
	nextStep: string;
	tagType: 'danger' | 'warning' | 'info' | 'success';
	historical?: boolean;
};
const props = defineProps<
	| { detail: ApprovalDetail; type: 'COUPON' | 'MERCHANT'; presentation?: never }
	| { presentation: FlowPresentation; detail?: never; type?: never }
>();
const flow = computed<FlowPresentation>(() => {
	if (props.presentation) return props.presentation;
	const { detail, type } = props;
	return {
		nodes: buildApprovalFlow(detail, type),
		nextStep: detail.nextStep,
		tagType: detail.item.status === 3 ? 'danger' : detail.item.status === 1 ? 'warning' : 'info',
		historical: detail.historical
	};
});
const nodes = computed(() => flow.value.nodes);
const formatTime = (value: string) =>
	dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '时间未记录';
</script>

<style scoped>
.approval-flow {
	margin: 24px 0;
	padding: 20px;
	border: 1px solid var(--el-border-color-light);
	border-radius: 12px;
}
.flow-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	margin-bottom: 20px;
	flex-wrap: wrap;
}
.flow-heading h3 {
	margin: 0 0 6px;
	font-size: 16px;
}
.flow-heading p {
	margin: 0;
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.flow-list {
	padding: 0;
	margin: 20px 0 0;
	list-style: none;
}
.flow-node {
	position: relative;
	padding: 0 0 22px 48px;
	--node-color: var(--el-color-success);
}
.flow-node:not(:last-child)::before {
	content: '';
	position: absolute;
	left: 16px;
	top: 34px;
	bottom: 0;
	width: 2px;
	background: var(--el-border-color);
}
.flow-node:last-child {
	padding-bottom: 0;
}
.node-marker {
	position: absolute;
	left: 0;
	top: 0;
	display: grid;
	place-items: center;
	width: 34px;
	height: 34px;
	border-radius: 10px;
	font-size: 17px;
	font-weight: 700;
	color: white;
	background: var(--node-color);
}
.current {
	--node-color: var(--el-color-primary);
}
.rejected {
	--node-color: var(--el-color-danger);
}
.withdrawn {
	--node-color: var(--el-color-warning);
}
.future {
	--node-color: var(--el-text-color-placeholder);
}
.future .node-marker {
	color: var(--el-text-color-secondary);
	background: var(--el-fill-color);
	border: 1px dashed var(--el-border-color);
	box-sizing: border-box;
}
.future::before {
	border-left: 2px dashed var(--el-border-color);
	background: transparent !important;
	width: 0 !important;
}
.node-card {
	padding: 14px 16px;
	border: 1px solid var(--el-border-color-light);
	border-radius: 8px;
}
.current .node-card {
	border-color: var(--el-color-primary-light-5);
	background: var(--el-color-primary-light-9);
}
.rejected .node-card {
	border-color: var(--el-color-danger-light-5);
}
.future .node-card {
	border-style: dashed;
	background: var(--el-fill-color-lighter);
}
.node-heading {
	display: flex;
	justify-content: space-between;
	gap: 12px;
	align-items: center;
	margin-bottom: 12px;
}
.node-heading strong {
	font-size: 14px;
}
.node-status {
	color: var(--node-color);
	font-size: 12px;
	flex-shrink: 0;
}
.node-actor {
	font-size: 13px;
	line-height: 1.6;
	overflow-wrap: anywhere;
}
.node-context {
	margin: 7px 0 0;
	line-height: 1.7;
	font-size: 12px;
	color: var(--el-text-color-regular);
	overflow-wrap: anywhere;
}
.node-meta {
	display: flex;
	gap: 14px;
	flex-wrap: wrap;
	margin-top: 10px;
	color: var(--el-text-color-secondary);
	font-size: 12px;
}
.node-reason {
	margin: 12px 0 0;
	padding: 10px 12px;
	background: var(--el-fill-color-light);
	border-radius: 5px;
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	font-size: 13px;
	line-height: 1.7;
}
@media (max-width: 600px) {
	.approval-flow {
		padding: 14px;
	}
	.flow-node {
		padding-left: 40px;
	}
	.node-card {
		padding: 12px;
	}
}
</style>

<template>
	<el-drawer
		v-model="open"
		title="本店 AI 客服设置"
		size="min(560px, 100vw)"
		:close-on-click-modal="false"
	>
		<ManagementState
			v-if="error"
			kind="error"
			title="无法读取客服设置"
			:description="error"
			@retry="load"
		/>
		<div v-else v-loading="loading" class="settings">
			<p>AI 仅自动接待尚未转人工的咨询。开启后不会给任何账号增加查看或回复权限。</p>
			<el-switch
				v-model="enabled"
				active-text="开启本店 AI 客服"
				:disabled="loading || saving"
			/>
			<h3>
				常见问题 <small>{{ faqs.length }}/30</small>
			</h3>
			<section v-for="(faq, i) in faqs" :key="i" class="faq">
				<label :for="`faq-q-${i}`">问题 {{ i + 1 }}</label>
				<el-input
					:id="`faq-q-${i}`"
					v-model="faq.question"
					maxlength="200"
					:disabled="saving"
					placeholder="例如：营业时间是什么时候？"
				/>
				<label :for="`faq-a-${i}`">回答</label>
				<el-input
					:id="`faq-a-${i}`"
					v-model="faq.answer"
					type="textarea"
					:rows="3"
					maxlength="1000"
					show-word-limit
					:disabled="saving"
				/>
				<el-button link type="danger" :disabled="saving" @click="faqs.splice(i, 1)"
					>移除这条问题</el-button
				>
			</section>
			<el-button
				:disabled="loading || saving || faqs.length >= 30"
				@click="faqs.push({ question: '', answer: '' })"
				>添加常见问题</el-button
			>
			<p role="status">{{ saveError }}</p>
		</div>
		<template #footer
			><el-button :disabled="saving" @click="open = false">关闭</el-button
			><el-button
				type="primary"
				:loading="saving"
				:disabled="loading || !!error"
				@click="save"
				>保存设置</el-button
			></template
		>
	</el-drawer>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { featureApi } from '../feature-api';
import ManagementState from './management-state.vue';
const open = defineModel<boolean>({ default: false });
const loading = ref(false),
	saving = ref(false),
	error = ref(''),
	saveError = ref('');
const enabled = ref(false),
	version = ref(0),
	faqs = ref<{ question: string; answer: string }[]>([]);
async function load() {
	loading.value = true;
	error.value = '';
	try {
		const r = await featureApi('chats/settings');
		enabled.value = r.aiEnabled;
		version.value = r.version;
		faqs.value = r.faqs || [];
	} catch (e: any) {
		error.value = e.message || '请确认拥有本店客服配置权限后重试';
	} finally {
		loading.value = false;
	}
}
async function save() {
	if (saving.value || loading.value || error.value) return;
	if (faqs.value.some(f => !f.question.trim() || !f.answer.trim())) {
		saveError.value = '请填写每条问题和回答，或移除空白条目。';
		return;
	}
	saving.value = true;
	saveError.value = '';
	try {
		await featureApi('chats/settings', {
			version: version.value,
			aiEnabled: enabled.value,
			faqs: faqs.value
		});
		ElMessage.success('客服设置已保存');
		await load();
	} catch (e: any) {
		saveError.value = e.message || '保存失败，请重试；版本冲突时请重新打开设置。';
	} finally {
		saving.value = false;
	}
}
watch(open, v => {
	if (v) void load();
});
</script>
<style scoped>
.settings {
	display: grid;
	gap: 16px;
	color: var(--el-text-color-primary);
}
.settings p {
	margin: 0;
	font-size: 13px;
	line-height: 1.7;
	color: var(--el-text-color-regular);
}
.settings h3 {
	margin: 8px 0 0;
}
.settings small {
	font-weight: 400;
	font-size: 12px;
}
.faq {
	display: grid;
	gap: 8px;
	padding-block: 12px;
	border-bottom: 1px solid var(--el-border-color-light);
}
.faq label {
	font-size: 13px;
}
.faq .el-button {
	justify-self: start;
}
</style>

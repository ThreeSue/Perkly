<template>
	<ManagementShell title="AI图片管理" description="管理优惠券封面的生成开关、商家额度及调用记录；费用为按配置单价计算的估算，不是云端实际账单。">
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button link type="primary" @click="loadTab">重试</el-button></el-alert>
		<el-tabs v-model="tab" @tab-change="search">
			<el-tab-pane label="功能设置" name="settings">
				<el-form v-if="settings" label-position="top" class="settings" :disabled="saving">
					<el-form-item label="图片生成功能"><el-switch v-model="settings.enabled" active-text="开启" inactive-text="暂停新请求和排队任务" /></el-form-item>
					<el-form-item label="允许的图片模型"><el-checkbox-group v-model="settings.allowedModels"><el-checkbox v-for="model in settings.supportedModels" :key="model" :value="model">{{ model }}</el-checkbox></el-checkbox-group></el-form-item>
					<el-form-item label="默认商家每日额度（0为不允许新请求）"><el-input-number v-model="settings.defaultDailyLimit" :min="0" :max="10000" :precision="0" /></el-form-item>
					<el-form-item label="每张图片估算单价（元）"><el-input-number v-model="settings.estimatedUnitPrice" :min="0" :max="10000" :precision="6" :step="0.01" /><span class="hint">留空表示未配置；新任务记录当时单价，不改写历史费用。</span></el-form-item>
					<el-form-item label="服务状态"><el-tag :type="settings.keyConfigured ? 'success' : 'danger'">{{ settings.keyConfigured ? '密钥已配置' : '密钥未配置' }}</el-tag></el-form-item>
					<el-alert title="每日按北京时间重置。已受理请求预占一次额度，失败、超时或结果未知也计入；提交前校验不通过不计入。已生成但保存失败只补存素材，不再次调用模型。" type="info" :closable="false" />
					<el-button type="primary" :loading="saving" class="save" @click="saveSettings">保存设置</el-button>
				</el-form>
			</el-tab-pane>
			<el-tab-pane label="商家额度" name="policies">
				<div class="toolbar"><el-input v-model="keyword" placeholder="搜索商家名称" clearable style="max-width: 260px" /><el-button @click="loadTab">刷新</el-button></div>
				<el-table v-loading="loading" :data="filteredPolicies">
					<el-table-column prop="merchantName" label="商家" min-width="200" />
					<el-table-column label="允许新请求" width="160"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column>
					<el-table-column label="独立每日额度" min-width="240"><template #default="{ row }"><el-input-number v-model="row.dailyLimit" :min="0" :max="10000" :precision="0" placeholder="继承默认额度" /><el-button link @click="row.dailyLimit = null">恢复继承</el-button></template></el-table-column>
					<el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="primary" link :disabled="saving" @click="savePolicy(row)">保存</el-button></template></el-table-column>
				</el-table>
			</el-tab-pane>
			<el-tab-pane label="生成任务" name="tasks">
				<div class="toolbar">
					<el-select v-model="merchantId" clearable filterable placeholder="全部商家" style="width: 220px"><el-option v-for="m in policies" :key="m.merchantId" :value="m.merchantId" :label="m.merchantName" /></el-select>
					<el-select v-model="status" clearable placeholder="全部状态" style="width: 200px"><el-option v-for="(label, value) in statusNames" :key="value" :value="value" :label="label" /></el-select>
					<el-button :loading="loading" @click="search">查询</el-button>
				</div>
				<el-table v-loading="loading" :data="tasks">
					<el-table-column label="图片" width="100"><template #default="{ row }"><el-image v-if="row.url" :src="row.url" :preview-src-list="[row.url]" preview-teleported fit="cover" style="width: 64px; height: 64px" /><span v-else>—</span></template></el-table-column>
					<el-table-column prop="merchantName" label="商家" min-width="160" /><el-table-column prop="actorName" label="操作人" min-width="120" />
					<el-table-column label="状态" min-width="180"><template #default="{ row }">{{ statusNames[row.status] || row.status }}<small v-if="row.archived">（素材已归档）</small></template></el-table-column>
					<el-table-column label="调用方式" width="110"><template #default="{ row }">{{ row.mode === 'EDIT' ? '原图完善' : '重新生成' }}</template></el-table-column>
					<el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.durationMs == null ? '—' : (row.durationMs / 1000).toFixed(1) + '秒' }}</template></el-table-column>
					<el-table-column label="估算费用" width="120"><template #default="{ row }">{{ row.estimatedCost == null ? '未估算' : '¥' + row.estimatedCost }}</template></el-table-column>
					<el-table-column prop="createTime" label="创建时间" width="175" /><el-table-column label="详情" width="90"><template #default="{ row }"><el-button link @click="detail = row">查看</el-button></template></el-table-column>
				</el-table>
			</el-tab-pane>
			<el-tab-pane label="用量与估算" name="usage">
				<el-alert title="仅统计已知成功生成的图片费用，未配置单价的图片单独列出；未知结果可能已在云端计费，请以云端账单核对。" type="warning" :closable="false" />
				<div class="toolbar"><el-select v-model="merchantId" clearable filterable placeholder="全部商家" style="width: 220px"><el-option v-for="m in policies" :key="m.merchantId" :value="m.merchantId" :label="m.merchantName" /></el-select><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="max-width: 300px" /><el-button :loading="loading" @click="search">查询（最多31天）</el-button></div>
				<el-table v-loading="loading" :data="usage"><el-table-column prop="merchantName" label="商家" min-width="180" /><el-table-column prop="day" label="日期" width="130" /><el-table-column prop="requests" label="受理次数" /><el-table-column prop="success" label="生成成功" /><el-table-column prop="failed" label="失败" /><el-table-column prop="uncertain" label="结果未知" /><el-table-column label="已知估算费用（元）" min-width="160"><template #default="{ row }">{{ row.estimatedCost }}</template></el-table-column><el-table-column prop="unpricedImages" label="未定价图片数" min-width="120" /></el-table>
			</el-tab-pane>
			<el-tab-pane label="操作审计" name="audit"><el-button :loading="loading" @click="search">刷新</el-button><el-table v-loading="loading" :data="audits"><el-table-column prop="createTime" label="时间" width="175" /><el-table-column prop="actorName" label="操作人" min-width="120" /><el-table-column prop="merchantName" label="商家" min-width="160" /><el-table-column label="操作" width="130"><template #default="{ row }">{{ actionNames[row.action] || row.action }}</template></el-table-column><el-table-column prop="detail" label="内容" show-overflow-tooltip min-width="300" /></el-table></el-tab-pane>
		</el-tabs>
		<el-pagination v-if="['tasks', 'usage', 'audit'].includes(tab)" v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="loadTab" />
		<el-drawer :model-value="!!detail" title="生成任务详情" size="min(650px, 100vw)" @update:model-value="detail = undefined">
			<template v-if="detail"><p>{{ detail.merchantName }} · {{ detail.actorName }} · {{ statusNames[detail.status] }}</p><p>模型：{{ detail.model }} · 请求编号：{{ detail.requestId }}</p><p v-if="detail.parentTaskId">原图完善版本，原图与当前图均独立保留。</p><p class="saved-prompt">{{ detail.prompt }}</p><el-alert v-if="detail.errorMessage" :title="detail.errorMessage" type="warning" :closable="false" /><el-image v-if="detail.url" :src="detail.url" fit="contain" style="width: 100%; max-height: 360px" /></template>
		</el-drawer>
	</ManagementShell>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import { featureApi } from '/$/workspace/feature-api';
import { statusNames, type ImageTask } from '../api';
interface Settings { enabled: boolean; allowedModels: string[]; supportedModels: string[]; defaultDailyLimit: number; estimatedUnitPrice: number | null; keyConfigured: boolean }
interface Policy { merchantId: number; merchantName: string; enabled: boolean; dailyLimit: number | null }
const settings = ref<Settings>(), policies = ref<Policy[]>([]), tasks = ref<ImageTask[]>([]), usage = ref<any[]>([]), audits = ref<any[]>([]), detail = ref<ImageTask>();
const tab = ref('settings'), keyword = ref(''), status = ref(''), merchantId = ref<number>(), range = ref<string[]>();
const loading = ref(false), saving = ref(false), error = ref(''), page = ref(1), total = ref(0);
const actionNames: Record<string, string> = { SETTINGS: '修改全局设置', POLICY: '修改商家额度', CREATE: '提交图片任务', SELECT: '选用图片' };
const filteredPolicies = computed(() => policies.value.filter(p => p.merchantName.includes(keyword.value.trim())));
const api = (path: string, data?: unknown) => featureApi('ai/console/' + path, data);
async function loadPolicies() { policies.value = await api('policies'); }
let revision = 0;
async function loadTab() {
	const current = ++revision;
	const view = tab.value;
	loading.value = true;
	error.value = '';
	try {
		if (view === 'settings') {
			const data = await api('settings');
			if (current === revision) settings.value = data;
		} else if (view === 'policies') {
			const data = await api('policies');
			if (current === revision) policies.value = data;
		} else {
			const params = new URLSearchParams({ pageNumber: String(page.value), pageSize: '20' });
			if (merchantId.value && view !== 'audit') params.set('merchantId', String(merchantId.value));
			if (view === 'tasks' && status.value) params.set('status', status.value);
			if (view === 'usage' && range.value?.length === 2) {
				params.set('startDay', range.value[0]);
				params.set('endDay', range.value[1]);
			}
			const result = await api(view + '?' + params);
			if (current !== revision) return;
			total.value = result.total;
			if (view === 'tasks') tasks.value = result.list;
			else if (view === 'usage') usage.value = result.list;
			else audits.value = result.list;
		}
	} catch (cause: any) {
		if (current === revision) error.value = cause?.message || 'AI管理数据加载失败';
	} finally {
		if (current === revision) loading.value = false;
	}
}
async function search() { page.value = 1; await loadTab(); }
async function saveSettings() {
	if (!settings.value || saving.value) return;
	saving.value = true;
	try { const s = settings.value; await api('settings', { enabled: s.enabled, allowedModels: s.allowedModels, defaultDailyLimit: s.defaultDailyLimit, estimatedUnitPrice: s.estimatedUnitPrice ?? null }); ElMessage.success('AI设置已保存'); await loadTab(); }
	catch (cause: any) { error.value = cause?.message || '保存失败'; }
	finally { saving.value = false; }
}
async function savePolicy(row: Policy) {
	if (saving.value) return;
	saving.value = true;
	try { await api('policy', { merchantId: row.merchantId, enabled: row.enabled, dailyLimit: row.dailyLimit ?? null }); ElMessage.success('商家额度已保存'); }
	catch (cause: any) { error.value = cause?.message || '保存失败'; }
	finally { saving.value = false; }
}
onMounted(async () => { await loadTab(); try { await loadPolicies(); } catch (cause: any) { error.value = cause?.message || '商家列表加载失败'; } });
</script>
<style scoped>
.settings { max-width: 680px; padding: 12px 0; }
.hint { margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.save, .el-pagination { margin-top: 20px; }
.saved-prompt { white-space: pre-wrap; overflow-wrap: anywhere; padding: 12px; background: var(--el-fill-color-light); }
</style>

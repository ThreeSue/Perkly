<template>
	<WorkbenchLayout title="商家资料" :context="profile?.name">
		<template #actions>
			<el-button class="workbench-refresh" :icon="Refresh" :loading="loading" :disabled="saving" @click="refresh">刷新</el-button>
		</template>
		<el-skeleton v-if="loading && !profile" class="profile-skeleton" :rows="6" animated aria-label="正在加载商家资料" />
		<ManagementState v-else-if="failure && !profile" kind="error" title="商家资料加载失败" :description="failure" @retry="load" />
		<template v-else-if="profile">
			<el-alert v-if="failure" class="workbench-inline-alert" :title="failure" type="error" :closable="false" show-icon />
			<div class="workbench-grid profile-grid">
				<section class="workbench-panel" aria-labelledby="merchant-settings-title">
					<div class="workbench-section-head">
						<div><h2 id="merchant-settings-title">经营设置</h2><p>设置核销时默认使用的门店，并维护营业执照号。</p></div>
						<el-tag v-if="!editable" type="info">只读</el-tag>
						<el-tag v-else-if="dirty" type="warning">有未保存的修改</el-tag>
					</div>
					<el-form class="profile-settings" label-position="top" :disabled="loading || saving" @submit.prevent="saveSettings">
						<section class="profile-field">
							<el-form-item label="默认门店">
								<el-select v-model="settings.defaultStoreId" clearable filterable :disabled="!editable" placeholder="不设置默认门店">
									<el-option v-for="store in stores" :key="store.id" :value="store.id" :label="store.name" :disabled="store.status !== 1" />
								</el-select>
							</el-form-item>
							<p>留空时，核销前按账号授权选择本次消费门店。</p>
						</section>
						<section class="profile-field">
							<el-form-item label="营业执照号">
								<el-input v-model="settings.licenseNumber" maxlength="18" :disabled="!editable || locked" :placeholder="locked ? undefined : '填写营业执照号'" />
							</el-form-item>
							<p v-if="locked"><el-icon aria-hidden="true"><Lock /></el-icon> 执照号已锁定。需要变更时请联系平台运营。</p>
							<p v-else>首次保存后会锁定，请核对执照号后再保存。</p>
						</section>
						<div v-if="editable" class="profile-actions">
							<el-button :disabled="!dirty || saving || loading" @click="restore">撤销修改</el-button>
							<el-button type="primary" native-type="submit" :disabled="!dirty || loading" :loading="saving">保存设置</el-button>
						</div>
					</el-form>
				</section>
				<section class="workbench-panel" aria-labelledby="merchant-record-title">
					<div class="workbench-section-head"><h2 id="merchant-record-title">主体资料</h2><span class="profile-label">平台维护</span></div>
					<div class="merchant-record">
						<div class="merchant-identity">
							<el-image v-if="profile.logo" class="merchant-mark" :src="profile.logo" fit="contain" :preview-src-list="[profile.logo]" preview-teleported :alt="profile.name + '标志'">
								<template #error><span class="merchant-mark merchant-monogram">{{ monogram }}</span></template>
							</el-image>
							<span v-else class="merchant-mark merchant-monogram" aria-hidden="true">{{ monogram }}</span>
							<div><h3>{{ profile.name || '未填写商家名称' }}</h3><p>{{ profile.introduction || '未填写商家简介' }}</p></div>
							<el-tag v-if="profile.status != null" :type="profile.status === 1 ? 'success' : 'info'">{{ profile.status === 1 ? '启用' : '停用' }}</el-tag>
						</div>
						<dl class="profile-facts">
							<div><dt>联系人</dt><dd>{{ profile.contactName || '未填写' }}</dd></div>
							<div><dt>联系电话</dt><dd>{{ profile.contactPhone || '未填写' }}</dd></div>
							<div class="profile-address"><dt>联系地址</dt><dd>{{ profile.address || '未填写' }}</dd></div>
						</dl>
						<p class="profile-note">主体资料变更请联系平台运营。</p>
					</div>
				</section>
			</div>
		</template>
		<ManagementState v-else title="暂无商家资料" />
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { Lock, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useBase } from '/$/base';
import WorkbenchLayout from '/$/workspace/components/workbench-layout.vue';
import ManagementState from '/$/workspace/components/management-state.vue';
import { workspaceApi } from '/$/workspace/api';
import { merchantRequest } from '/@/merchant-api';

defineOptions({ name: 'merchant-profile' });
interface Settings { defaultStoreId?: number; licenseNumber?: string | null }
interface Profile {
	name?: string;
	logo?: string;
	introduction?: string;
	contactName?: string;
	contactPhone?: string;
	address?: string;
	status?: number;
}
const { menu } = useBase();
const editable = computed(() => menu.perms.includes('profile:edit'));
const profile = ref<Profile>();
const monogram = computed(() => Array.from(profile.value?.name || '商')[0]);
const settings = ref<Settings>({});
const original = ref<Settings>({});
const stores = ref<{ id: number; name: string; status: number }[]>([]);
const loading = ref(false);
const saving = ref(false);
const failure = ref('');
const locked = computed(() => Boolean(original.value.licenseNumber));
const dirty = computed(() =>
	(settings.value.defaultStoreId || null) !== (original.value.defaultStoreId || null) ||
	(settings.value.licenseNumber?.trim() || '') !== (original.value.licenseNumber || '')
);
function restore() {
	settings.value = { ...original.value };
}
async function load() {
	if (loading.value || saving.value) return;
	loading.value = true;
	failure.value = '';
	try {
		const [record, current, list] = await Promise.all([
			merchantRequest('workspace/profile'),
			workspaceApi('settings'),
			workspaceApi('stores')
		]);
		profile.value = record;
		original.value = { ...current, defaultStoreId: current.defaultStoreId ?? undefined };
		stores.value = list;
		restore();
	} catch (error) {
		failure.value = error instanceof Error ? error.message : '加载失败，请重试。';
	} finally {
		loading.value = false;
	}
}
async function discardChanges() {
	if (saving.value) return false;
	if (!dirty.value) return true;
	try {
		await ElMessageBox.confirm('当前设置尚未保存，是否放弃修改？', '未保存的设置', {
			confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning'
		});
		restore();
		return true;
	} catch {
		return false;
	}
}
async function refresh() {
	if (await discardChanges()) await load();
}
async function saveSettings() {
	if (!editable.value || !dirty.value || loading.value || saving.value) return;
	saving.value = true;
	const payload = {
		defaultStoreId: settings.value.defaultStoreId || null,
		licenseNumber: settings.value.licenseNumber?.trim() || null
	};
	try {
		if (!locked.value && payload.licenseNumber) {
			await ElMessageBox.confirm('营业执照号首次保存后不能修改，请核对无误后继续。', '确认执照号', { type: 'warning' });
		}
		await workspaceApi('settings', payload);
		original.value = { ...payload, defaultStoreId: payload.defaultStoreId ?? undefined };
		restore();
		ElMessage.success('设置已保存');
	} catch (error) {
		if (error instanceof Error) ElMessage.error(error.message);
	} finally {
		saving.value = false;
	}
}
onBeforeRouteLeave(discardChanges);
onMounted(load);
</script>

<style scoped>
.profile-grid { grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); }
.profile-skeleton { box-sizing: border-box; padding: 18px; }
.profile-label { color: var(--el-text-color-regular); font-size: 12px; }
.profile-settings { padding: 0 18px 16px; }
.profile-field { padding-block: 12px; border-block-start: 1px solid var(--el-border-color-lighter); }
.profile-field .el-form-item { margin-bottom: 6px; }
.profile-field .el-select { width: 100%; }
.profile-field p, .profile-note { margin: 0; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.5; }
.profile-field p .el-icon { vertical-align: -2px; }
.profile-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; padding-top: 12px; border-block-start: 1px solid var(--el-border-color-lighter); }
.profile-actions .el-button { margin: 0; }
.merchant-record { padding: 0 18px 16px; }
.merchant-identity {
	display: grid;
	grid-template-columns: auto minmax(0, 1fr) auto;
	align-items: center;
	gap: 12px;
	padding: 12px;
	border-radius: 7px;
	background: var(--workspace-selected);
}
.merchant-mark { width: 46px; height: 46px; border-radius: 7px; }
.merchant-monogram { display: grid; place-items: center; color: white; background: var(--workspace-accent); font-size: 20px; font-weight: 700; }
.merchant-identity h3 { margin: 0; font-size: 16px; overflow-wrap: anywhere; }
.merchant-identity p { margin: 4px 0 0; color: var(--el-text-color-regular); font-size: 12px; overflow-wrap: anywhere; }
.profile-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 8px 0 12px; }
.profile-facts > div { padding: 10px 8px 10px 0; border-block-end: 1px solid var(--el-border-color-lighter); }
.profile-facts dt { color: var(--el-text-color-regular); font-size: 12px; }
.profile-facts dd { margin: 4px 0 0; overflow-wrap: anywhere; }
.profile-address { grid-column: 1 / -1; }
@media (max-width: 960px) { .profile-grid { grid-template-columns: minmax(0, 1fr); } }
</style>

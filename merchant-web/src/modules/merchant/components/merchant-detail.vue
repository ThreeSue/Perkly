<template>
	<el-drawer
		v-model="visible"
		title="商家详情与管理"
		:size="activeTab === 'stores' ? 'min(1180px, 100vw)' : 'min(880px, 100vw)'"
		append-to-body
		destroy-on-close
		:before-close="beforeClose"
		@closed="invalidate"
	>
		<div class="merchant-detail">
			<el-skeleton v-if="loading" :rows="6" animated aria-label="正在加载商家资料" />
			<ManagementState v-else-if="error" kind="error" title="商家资料加载失败" @retry="refresh" />
			<template v-else-if="merchant">
				<header class="merchant-heading">
					<el-avatar shape="square" :src="merchant.logo" :size="46">{{ Array.from(merchant.name || '商')[0] }}</el-avatar>
					<div class="merchant-summary">
						<h2>{{ merchant.name }}</h2>
						<p>{{ categoryName(merchant.categoryId!) }}</p>
					</div>
					<el-tag :type="Number(merchant.status) === 1 ? 'success' : 'info'">
						{{ Number(merchant.status) === 1 ? '启用' : '停用' }}
					</el-tag>
					<el-button v-if="!editing && activeTab === 'basic' && service.merchant.info._permission?.update" type="primary" @click="beginEdit">编辑商家</el-button>
				</header>
				<el-tabs v-model="activeTab" :before-leave="beforeTabLeave">
					<el-tab-pane label="基本资料" name="basic">
						<div v-if="!editing" class="merchant-sheet">
							<section class="merchant-facts">
								<h3>主体与联系资料</h3>
								<dl class="facts-grid">
									<div v-for="item in fields" :key="item.label" :class="{ 'field-wide': item.wide }">
										<dt>{{ item.label }}</dt><dd>{{ item.value }}</dd>
									</div>
								</dl>
								<el-image v-if="merchant.logo" class="merchant-logo-preview" :src="merchant.logo" :preview-src-list="[merchant.logo]" preview-teleported fit="contain" alt="商家 Logo" />
							</section>
							<aside class="related-merchant">
								<h3>关联管理</h3>
								<div v-if="user.info?.username === 'admin'" class="related-links">
									<el-button text @click="goManagement('accounts')">管理账号</el-button>
									<el-button text @click="goManagement('roles')">岗位权限</el-button>
									<el-button text @click="goManagement('authorization')">功能授权</el-button>
									<el-button text @click="goStores">在门店列表查看</el-button>
								</div>
								<div class="related-links">
									<el-button text @click="refresh">刷新资料</el-button>
									<el-button v-if="service.merchant.info._permission?.update" text :type="Number(merchant.status) === 1 ? 'danger' : 'primary'" :loading="saving" @click="toggleStatus">
										{{ Number(merchant.status) === 1 ? '停用商家' : '启用商家' }}
									</el-button>
									<el-button v-if="user.info?.username === 'admin'" text type="danger" :loading="resetting" @click="resetPassword">重置密码</el-button>
								</div>
							</aside>
						</div>
						<el-form v-else ref="formRef" :model="draft" label-position="top" class="edit-form">
							<el-row :gutter="20">
								<el-col :xs="24" :sm="12"><el-form-item label="商家名称" prop="name" :rules="required('商家名称')"><el-input v-model="draft.name" maxlength="100" /></el-form-item></el-col>
								<el-col :xs="24" :sm="12"><el-form-item label="商家分类" prop="categoryId" :rules="required('商家分类')"><el-cascader v-model="draft.categoryId" :options="categoryOptions" :props="{ emitPath: false, checkStrictly: true }" filterable style="width:100%" /></el-form-item></el-col>
								<el-col :xs="24" :sm="12"><el-form-item label="联系人" prop="contactName" :rules="required('联系人')"><el-input v-model="draft.contactName" /></el-form-item></el-col>
								<el-col :xs="24" :sm="12"><el-form-item label="联系电话" prop="contactPhone" :rules="[...required('联系电话'), phoneRule]"><el-input v-model="draft.contactPhone" /></el-form-item></el-col>
								<el-col :span="24"><el-form-item label="营业执照号（首次填写后锁定）"><el-input v-model="draft.licenseNumber" :disabled="!!merchant.licenseNumber" maxlength="18" /></el-form-item></el-col>
								<el-col :span="24"><el-form-item label="商家 Logo"><cl-upload v-model="draft.logo" type="image" accept="image/*" :limit="1" :limit-size="5" /></el-form-item></el-col>
								<el-col :span="24"><el-form-item label="省市区"><cl-distpicker v-model="region" /></el-form-item></el-col>
								<el-col :span="24"><el-form-item label="详细地址"><el-input v-model="draft.address" /></el-form-item></el-col>
								<el-col :span="24"><el-form-item label="商家简介"><el-input v-model="draft.introduction" type="textarea" /></el-form-item></el-col>
								<el-col :xs="24" :sm="12"><el-form-item label="状态"><el-select v-model="draft.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item></el-col>
								<el-col :span="24"><el-form-item label="平台内部备注"><el-input v-model="draft.remark" type="textarea" /></el-form-item></el-col>
							</el-row>
						</el-form>
					</el-tab-pane>
					<el-tab-pane
						v-if="service.merchant.store._permission?.page"
						label="门店管理"
						name="stores"
					>
						<StoreManager
							v-if="activeTab === 'stores'"
							:key="merchant.id"
							:merchant-id="merchant.id"
							:merchant-name="merchant.name"
							:default-store-id="merchant.defaultStoreId"
							embedded
							@changed="emit('changed')"
						/>
					</el-tab-pane>
				</el-tabs>
			</template>
		</div>
		<template v-if="editing" #footer>
			<el-button :disabled="saving" @click="cancelEdit">取消编辑</el-button>
			<el-button type="primary" :loading="saving" @click="saveEdit">保存商家</el-button>
		</template>
	</el-drawer>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox, type CascaderOption, type FormInstance } from 'element-plus';
import { phoneRule } from './validation';
import { useCool } from '/@/cool';
import { useBase } from '/$/base';
import StoreManager from './store-manager.vue';
import ManagementState from '/$/workspace/components/management-state.vue';

const props = defineProps<{ categoryName: (id: number) => string; categoryOptions: CascaderOption[] }>();
const emit = defineEmits<{ changed: [] }>();
const { service, router } = useCool();
const { user } = useBase();
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref(false);
const activeTab = ref('basic');
const merchant = ref<(Eps.MerchantInfoEntity & { id: number }) | null>(null);
const editing = ref(false);
const draft = ref<any>({});
const region = ref<string[]>([]);
const formRef = ref<FormInstance>();
let baseline = '';
const required = (name: string) => [{ required: true, message: `请填写${name}`, trigger: 'blur' }];
const snapshot = () => JSON.stringify({ draft: draft.value, region: region.value });
function beginEdit() {
 if (!merchant.value) return;
 draft.value = { ...merchant.value };
 region.value = [merchant.value.province, merchant.value.city, merchant.value.district].filter(Boolean) as string[];
 baseline = snapshot(); editing.value = true;
}
async function canDiscard() {
 if (saving.value) return false;
 if (!editing.value || snapshot() === baseline) return true;
 try { await ElMessageBox.confirm('修改尚未保存，确定放弃吗？', '放弃修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true; } catch { return false; }
}
async function beforeClose(done: () => void) { if (await canDiscard()) { editing.value = false; done(); } }
async function cancelEdit() { if (await canDiscard()) editing.value = false; }
async function beforeTabLeave() { if (!(await canDiscard())) return false; editing.value = false; return true; }
async function saveEdit() {
 if (!merchant.value || saving.value) return;
 try { await formRef.value?.validate(); } catch { return; }
 const license = String(draft.value.licenseNumber || '').trim();
 if (!merchant.value.licenseNumber && license) {
  if (!/^(?:[A-Z0-9]{15}|[A-Z0-9]{18})$/i.test(license)) { ElMessage.warning('营业执照号应为15或18位字母数字'); return; }
  try { await ElMessageBox.confirm('营业执照号首次保存后不能修改，请确认填写正确。', '确认营业执照号', { type: 'warning' }); } catch { return; }
 }
 saving.value = true;
 try {
  const [province = '', city = '', district = ''] = region.value;
  const { id, name, categoryId, contactName, contactPhone, logo, address, introduction, remark, status } = draft.value;
  await service.merchant.info.update({ id, name, categoryId, contactName, contactPhone, logo, address, introduction, remark, status, province, city, district, licenseNumber: merchant.value.licenseNumber || license });
  editing.value = false; ElMessage.success('商家资料已保存'); emit('changed'); await refresh();
 } catch (err: any) { ElMessage.error(err?.message || '保存失败，请重试'); } finally { saving.value = false; }
}
let selectedId: number | undefined;
let revision = 0;
function invalidate() {
	revision++;
	editing.value = false;
	merchant.value = null;
	selectedId = undefined;
}
async function refresh() {
	if (selectedId == null || !visible.value) return;
	const current = ++revision;
	loading.value = true;
	error.value = false;
	try {
		const data = await service.merchant.info.info({ id: selectedId });
		if (current === revision) merchant.value = data as Eps.MerchantInfoEntity & { id: number };
	} catch {
		if (current === revision) error.value = true;
	} finally {
		if (current === revision) loading.value = false;
	}
}
function open(id: number, tab = 'basic') {
	selectedId = id;
	merchant.value = null;
	activeTab.value = tab;
	visible.value = true;
	void refresh();
}
function goManagement(page: string) {
	if (!merchant.value) return;
	const merchantId = merchant.value.id;
	visible.value = false;
	void router.push({ path: '/workspace-admin/' + page, query: { merchantId } });
}
function goStores() {
	if (!merchant.value) return;
	const merchantId = merchant.value.id;
	visible.value = false;
	void router.push({ path: '/merchant/store', query: { merchantId } });
}
const fields = computed(() => {
	const data = merchant.value;
	if (!data) return [];
	return [
		['联系人', data.contactName],
		['联系电话', data.contactPhone],
		['所在地区', [data.province, data.city, data.district].filter(Boolean).join(' / ')],
		['详细地址', data.address, true],
		['营业执照号', data.licenseNumber],
		['商家简介', data.introduction, true],
		['平台内部备注', data.remark, true],
		['创建时间', data.createTime],
		['更新时间', data.updateTime]
	].map(([label, value, wide]) => ({
		wide,
		label: String(label),
		value: value == null || value === '' ? '—' : String(value)
	}));
});
async function toggleStatus() {
	const data = merchant.value;
	if (!data || saving.value) return;
	const status = Number(data.status) === 1 ? 0 : 1;
	try {
		await ElMessageBox.confirm(`确认${status ? '启用' : '停用'}“${data.name}”？`, '商家状态', {
			type: 'warning'
		});
	} catch {
		return;
	}
	saving.value = true;
	try {
		await service.merchant.info.update({ id: data.id, status });
		ElMessage.success('商家状态已更新');
		emit('changed');
		await refresh();
	} catch (err: any) {
		ElMessage.error(err.message || '操作失败，请重试');
	} finally {
		saving.value = false;
	}
}
const resetting = ref(false);
async function resetPassword() {
	const row = merchant.value;
	if (!row || resetting.value) return;
	try {
		await ElMessageBox.confirm(
			`确认将“${row.name}”的商家管理员密码重置为123456？该账号将需要重新登录。`,
			'重置商家密码',
			{ type: 'warning', confirmButtonText: '确认重置' }
		);
		resetting.value = true;
		const count = await service.request({
			url: 'admin/merchant/account/resetPassword',
			method: 'POST',
			data: { merchantId: row.id }
		});
		ElMessage.success(`已重置${count}个商家账号，密码为123456`);
	} catch (error: any) {
		if (error?.message) ElMessage.error(error.message);
	} finally {
		resetting.value = false;
	}
}
defineExpose({ open, refresh });
</script>

<style scoped>
.merchant-detail { min-height: 200px; }
.merchant-heading {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 12px;
	margin-bottom: 12px;
	padding: 14px;
	border-radius: 7px;
	background: var(--workspace-selected);
}
.merchant-heading .el-avatar { flex: none; color: white; background: var(--workspace-accent); font-weight: 700; }
.merchant-summary { flex: 1; min-width: 120px; }
.merchant-summary h2 { margin: 0; font-size: 21px; overflow-wrap: anywhere; color: var(--workspace-ink); }
.merchant-summary p { margin: 3px 0 0; color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.merchant-sheet { display: grid; grid-template-columns: minmax(0, 1fr) 170px; gap: 18px; }
.merchant-sheet h3 { margin: 7px 0 12px; font-size: 15px; }
.facts-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0; }
.facts-grid > div { padding: 9px 10px 9px 0; border-block-start: 1px solid var(--el-border-color-lighter); }
.facts-grid dt { font-size: 12px; color: var(--el-text-color-regular); }
.facts-grid dd { margin: 4px 0 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.field-wide { grid-column: 1 / -1; }
.merchant-logo-preview { width: 100px; height: 76px; margin-top: 10px; border-radius: 6px; }
.related-merchant { padding-inline-start: 14px; border-inline-start: 1px solid var(--el-border-color-lighter); }
.related-links { display: grid; }
.related-links + .related-links { margin-top: 8px; padding-top: 8px; border-block-start: 1px solid var(--el-border-color-lighter); }
.related-links .el-button { justify-content: flex-start; margin: 0; }
.edit-form { padding: 6px 4px 10px; }
@media (max-width: 620px) {
	.merchant-sheet { grid-template-columns: minmax(0, 1fr); }
	.related-merchant { padding: 0; border-inline-start: 0; }
}
</style>

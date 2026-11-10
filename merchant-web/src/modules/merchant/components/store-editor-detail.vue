<template>
 <el-drawer v-model="visible" class="store-record-drawer" :title="editing ? '编辑门店资料' : '门店资料'" size="min(680px, 100vw)" append-to-body destroy-on-close :before-close="beforeClose" @closed="clear">
  <div v-loading="loading">
   <el-result v-if="error" icon="error" title="门店资料加载失败"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
   <template v-else-if="store">
    <div class="store-record-heading">
     <div>
      <h2>{{ store.name }}</h2>
      <p>{{ store.merchantName || merchantName || '商家名称暂不可用' }}</p>
     </div>
     <el-tag effect="light" round :type="Number(store.status) === 1 ? 'success' : Number(store.status) === 2 ? 'warning' : 'info'">{{ statusLabel }}</el-tag>
    </div>
    <div v-if="!editing" class="cover-section">
     <el-image v-if="store.cover" class="store-cover" :src="store.cover" :preview-src-list="[store.cover]" preview-teleported fit="cover" :alt="`${store.name || '门店'}的封面`">
      <template #error><CoverPlaceholder /></template>
     </el-image>
     <div v-else class="store-cover"><CoverPlaceholder /></div>
    </div>
    <el-form v-if="editing" ref="formRef" :model="draft" :disabled="saving" label-position="top" @submit.prevent="save"><el-row :gutter="16">
     <el-col :span="12"><el-form-item label="所属商家" prop="merchantId" :rules="required('所属商家')"><el-input v-if="embedded" :model-value="merchantName" disabled /><MerchantSelect v-else v-model="draft.merchantId" active-only style="width:100%" /></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="门店类型" prop="categoryId" :rules="required('门店类型')"><el-select v-model="draft.categoryId" filterable style="width:100%"><el-option v-for="item in availableCategories" :key="item.id" :value="item.id" :label="item.name" /></el-select></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="门店名称" prop="name" :rules="required('门店名称')"><el-input v-model="draft.name" maxlength="100" /></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="联系电话" prop="phone" :rules="[...required('联系电话'), phoneRule]"><el-input v-model="draft.phone" maxlength="30" /></el-form-item></el-col>
     <el-col :span="24"><el-form-item label="门店封面"><CoverField v-model="draft.cover" /></el-form-item></el-col>
     <el-col :span="24"><el-form-item label="详细地址与位置" prop="address" :rules="required('详细地址')"><div class="location"><el-input v-model="draft.address" maxlength="500" /><el-button @click="picker?.open(draft)">地图选址</el-button><el-text size="small" type="info">{{ hasCoordinates(draft) ? `经度 ${draft.longitude}，纬度 ${draft.latitude}（GCJ-02）` : '尚未定位，可稍后补录。' }}</el-text></div></el-form-item></el-col>
     <el-col :span="24"><el-form-item label="营业时间"><el-input v-model="draft.businessHours" type="textarea" :rows="3" maxlength="4000" /></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="状态"><el-select v-model="draft.status"><el-option label="营业" :value="1"/><el-option label="停业" :value="2"/><el-option label="禁用" :value="0"/><el-option label="关闭" :value="3"/></el-select></el-form-item></el-col>
    </el-row></el-form>
    <template v-else>
     <h3 class="record-section">经营资料</h3>
     <dl class="record-facts">
      <div><dt>门店类型</dt><dd>{{ store.categoryName || categories.find(c => c.id === store.categoryId)?.name || '未获取到门店类型' }}</dd></div>
      <div><dt>联系电话</dt><dd>{{ store.phone || '未填写' }}</dd></div>
      <div><dt>营业时间</dt><dd>{{ store.businessHours || '未填写' }}</dd></div>
      <div><dt>创建时间</dt><dd>{{ store.createTime || '—' }}</dd></div>
     </dl>
     <h3 class="record-section">门店位置</h3>
     <dl class="record-facts">
      <div><dt>详细地址</dt><dd>{{ store.address || '未填写' }}</dd></div>
      <div><dt>地图位置</dt><dd>{{ hasCoordinates(store) ? `经度 ${store.longitude}，纬度 ${store.latitude}（GCJ-02）` : '尚未定位' }}</dd></div>
     </dl>
    </template>
   </template>
  </div><LocationPicker ref="picker" @confirmed="applyLocation" />
  <template #footer>
   <div class="record-footer">
    <span v-if="store && !canEdit" class="read-only-note">当前账号仅可查看</span>
    <div v-if="editing">
     <el-button :disabled="saving" @click="cancel">取消编辑</el-button>
     <el-button type="primary" :loading="saving" :disabled="!canEdit" @click="save">保存资料</el-button>
    </div>
    <div v-else>
     <el-button @click="visible = false">返回列表</el-button>
     <el-button v-if="store && canEdit" type="primary" @click="beginEdit">编辑资料</el-button>
    </div>
   </div>
  </template>
 </el-drawer>
</template>
<script setup lang="ts">
import CoverPlaceholder from '/$/space/components/cover-placeholder.vue';
import { computed, ref } from 'vue';
import CoverField from '/$/space/components/cover-field.vue';
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus';
import { useCool } from '/@/cool';
import MerchantSelect from './merchant-select.vue';
import LocationPicker from './location-picker.vue';
import { hasCoordinates, type LocationValue } from '../map/model';
import { phoneRule } from './validation';
const props = defineProps<{ categories: { id: number; name: string; status: number }[]; merchantName?: string; embedded?: boolean }>();
const emit = defineEmits<{ changed: [] }>();
const { service } = useCool();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref(false), editing = ref(false);
const store = ref<any>(null), draft = ref<any>({});
const canEdit = computed(() => Boolean(service.merchant.store._permission?.update));
const statusLabel = computed(() => ['禁用', '营业', '停业', '关闭'][Number(store.value?.status)] || '未知');
const formRef = ref<FormInstance>(), picker = ref<InstanceType<typeof LocationPicker>>();
const availableCategories = computed(() => props.categories.filter(c => Number(c.status) === 1 || c.id === draft.value.categoryId));
const required = (name: string) => [{ required: true, message: `请填写${name}`, trigger: 'blur' }];
let selectedId: number | undefined, revision = 0, baseline = '', selectedRow: any = null;
async function load() {
 if (selectedId == null || !visible.value) return;
 const current = ++revision; loading.value = true; error.value = false;
 try { const result = await service.merchant.store.info({ id: selectedId }); if (current === revision) store.value = { ...selectedRow, ...result, merchantName: result.merchantName || selectedRow?.merchantName, categoryName: result.categoryName || selectedRow?.categoryName }; }
 catch { if (current === revision) error.value = true; }
 finally { if (current === revision) loading.value = false; }
}
function open(row: any) { selectedRow = row; selectedId = row.id; store.value = null; editing.value = false; visible.value = true; void load(); }
function clear() { revision++; store.value = null; selectedId = undefined; editing.value = false; }
function beginEdit() { if (!canEdit.value || !store.value) return; draft.value = { ...store.value }; baseline = JSON.stringify(draft.value); editing.value = true; }
async function canDiscard() {
 if (saving.value) return false;
 if (!editing.value || JSON.stringify(draft.value) === baseline) return true;
 try { await ElMessageBox.confirm('修改尚未保存，确定放弃吗？', '放弃修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true; } catch { return false; }
}
async function beforeClose(done: () => void) { if (await canDiscard()) { editing.value = false; done(); } }
async function cancel() { if (await canDiscard()) editing.value = false; }
function applyLocation(value: LocationValue) { Object.assign(draft.value, { address: value.address, longitude: value.longitude, latitude: value.latitude }); }
async function save() {
 if (saving.value || !canEdit.value || !formRef.value) return;
 saving.value = true;
 try {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const { id, merchantId, categoryId, name, cover, address, longitude, latitude, phone, businessHours, status } = draft.value;
  await service.merchant.store.update({ id, merchantId, categoryId, name, cover, address, longitude, latitude, phone, businessHours, status });
  editing.value = false;
  ElMessage.success('门店资料已保存');
  emit('changed');
  if (props.embedded) await load();
  else visible.value = false;
 } catch (err: any) { ElMessage.error(err?.message || '保存失败，请重试'); } finally { saving.value = false; }
}
defineExpose({ open });
</script>
<style scoped>
.store-record-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 16px; padding: 14px 16px; background: var(--el-fill-color-light); border-radius: 8px; }
.store-record-heading h2 { margin: 0 0 4px; font-size: 20px; font-weight: 650; overflow-wrap: anywhere; }
.store-record-heading p { margin: 0; color: var(--el-text-color-regular); }
.cover-section { margin-bottom: 16px; }
.record-section { margin: 20px 0 4px; font-size: 14px; font-weight: 650; color: var(--el-text-color-primary); }
.record-facts { margin: 0; }
.record-facts > div { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.record-facts dt { color: var(--el-text-color-regular); }
.record-facts dd { margin: 0; overflow-wrap: anywhere; white-space: pre-line; font-variant-numeric: tabular-nums; }
.store-cover { display: block; width: min(100%, 260px); height: 144px; border-radius: 6px; overflow: hidden; }
.record-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.record-footer > div { margin-inline-start: auto; }
.read-only-note { color: var(--el-text-color-regular); font-size: 13px; }
:global(.store-record-drawer .el-drawer__header) { margin-bottom: 0; padding-bottom: 14px; border-bottom: 1px solid var(--el-border-color-lighter); }
:global(.store-record-drawer .el-drawer__footer) { border-top: 1px solid var(--el-border-color-lighter); }
.location { display:flex; flex-wrap:wrap; gap:10px; width:100%; }.location > .el-input { flex:1; min-width:220px; }.location > .el-text { width:100%; }
</style>

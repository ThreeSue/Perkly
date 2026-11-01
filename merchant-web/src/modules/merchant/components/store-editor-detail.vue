<template>
 <el-drawer v-model="visible" title="门店资料" size="min(800px, 96vw)" destroy-on-close :before-close="beforeClose" @closed="clear">
  <div v-loading="loading">
   <el-result v-if="error" icon="error" title="门店资料加载失败"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
   <template v-else-if="store">
    <div v-if="!editing" class="cover-section">
     <div class="cover-label">门店封面</div>
     <el-image v-if="store.cover" class="store-cover" :src="store.cover" :preview-src-list="[store.cover]" preview-teleported fit="cover" :alt="`${store.name || '门店'}的封面`">
      <template #error><div class="cover-placeholder"><el-icon :size="32"><Picture /></el-icon><span>暂无门店封面</span></div></template>
     </el-image>
     <div v-else class="store-cover cover-placeholder"><el-icon :size="32"><Picture /></el-icon><span>暂无门店封面</span></div>
    </div>
    <div class="actions"><el-button v-if="!editing && service.merchant.store._permission?.update" type="primary" @click="beginEdit">编辑资料</el-button><template v-if="editing"><el-button type="primary" :loading="saving" @click="save">保存</el-button><el-button :disabled="saving" @click="cancel">取消</el-button></template></div>
    <el-form v-if="editing" ref="formRef" :model="draft" label-position="top"><el-row :gutter="20">
     <el-col :span="12"><el-form-item label="所属商家" prop="merchantId" :rules="required('所属商家')"><el-input v-if="embedded" :model-value="merchantName" disabled /><MerchantSelect v-else v-model="draft.merchantId" active-only style="width:100%" /></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="门店类型" prop="categoryId" :rules="required('门店类型')"><el-select v-model="draft.categoryId" filterable style="width:100%"><el-option v-for="item in availableCategories" :key="item.id" :value="item.id" :label="item.name" /></el-select></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="门店名称" prop="name" :rules="required('门店名称')"><el-input v-model="draft.name" maxlength="100" /></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="联系电话" prop="phone" :rules="[...required('联系电话'), phoneRule]"><el-input v-model="draft.phone" maxlength="30" /></el-form-item></el-col>
     <el-col :span="24"><el-form-item label="门店封面"><cl-upload v-model="draft.cover" type="image" accept="image/*" :limit="1" :limit-size="5" /></el-form-item></el-col>
     <el-col :span="24"><el-form-item label="详细地址与位置" prop="address" :rules="required('详细地址')"><div class="location"><el-input v-model="draft.address" maxlength="500" /><el-button @click="picker?.open(draft)">地图选址</el-button><el-text size="small" type="info">{{ hasCoordinates(draft) ? `经度 ${draft.longitude}，纬度 ${draft.latitude}（GCJ-02）` : '尚未定位，可稍后补录。' }}</el-text></div></el-form-item></el-col>
     <el-col :span="24"><el-form-item label="营业时间"><el-input v-model="draft.businessHours" type="textarea" :rows="3" maxlength="4000" /></el-form-item></el-col>
     <el-col :span="12"><el-form-item label="状态"><el-select v-model="draft.status"><el-option label="营业" :value="1"/><el-option label="停业" :value="2"/><el-option label="禁用" :value="0"/><el-option label="关闭" :value="3"/></el-select></el-form-item></el-col>
    </el-row></el-form>
    <el-descriptions v-else :column="1" border>
     <el-descriptions-item label="所属商家">{{ store.merchantName || merchantName || '未获取到商家名称' }}</el-descriptions-item>
     <el-descriptions-item label="门店类型">{{ store.categoryName || categories.find(c => c.id === store.categoryId)?.name || '未获取到门店类型' }}</el-descriptions-item>
     <el-descriptions-item label="门店名称">{{ store.name || '—' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ store.phone || '—' }}</el-descriptions-item>
     <el-descriptions-item label="详细地址">{{ store.address || '—' }}</el-descriptions-item><el-descriptions-item label="地图位置">{{ hasCoordinates(store) ? `经度 ${store.longitude}，纬度 ${store.latitude}（GCJ-02）` : '尚未定位' }}</el-descriptions-item>
     <el-descriptions-item label="营业时间">{{ store.businessHours || '—' }}</el-descriptions-item><el-descriptions-item label="状态">{{ ['禁用', '营业', '停业', '关闭'][Number(store.status)] || '未知' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ store.createTime || '—' }}</el-descriptions-item>
    </el-descriptions>
   </template>
  </div><LocationPicker ref="picker" @confirmed="applyLocation" />
 </el-drawer>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { Picture } from '@element-plus/icons-vue';
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
function beginEdit() { draft.value = { ...store.value }; baseline = JSON.stringify(draft.value); editing.value = true; }
async function canDiscard() {
 if (saving.value) return false;
 if (!editing.value || JSON.stringify(draft.value) === baseline) return true;
 try { await ElMessageBox.confirm('修改尚未保存，确定放弃吗？', '放弃修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true; } catch { return false; }
}
async function beforeClose(done: () => void) { if (await canDiscard()) { editing.value = false; done(); } }
async function cancel() { if (await canDiscard()) editing.value = false; }
function applyLocation(value: LocationValue) { Object.assign(draft.value, { address: value.address, longitude: value.longitude, latitude: value.latitude }); }
async function save() {
 if (saving.value) return;
 try { await formRef.value?.validate(); } catch { return; }
 saving.value = true;
 try {
  const { id, merchantId, categoryId, name, cover, address, longitude, latitude, phone, businessHours, status } = draft.value;
  await service.merchant.store.update({ id, merchantId, categoryId, name, cover, address, longitude, latitude, phone, businessHours, status });
  editing.value = false; ElMessage.success('门店资料已保存'); emit('changed'); await load();
 } catch (err: any) { ElMessage.error(err?.message || '保存失败，请重试'); } finally { saving.value = false; }
}
defineExpose({ open });
</script>
<style scoped>
.cover-section { margin-bottom: 20px; }
.cover-label { margin-bottom: 10px; color: var(--el-text-color-regular); font-weight: 600; }
.store-cover { display: block; width: min(100%, 360px); height: 180px; border-radius: 8px; overflow: hidden; }
.cover-placeholder { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; width: 100%; height: 100%; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); font-size: 13px; }
.store-cover.cover-placeholder { width: min(100%, 360px); height: 180px; }

.actions { display:flex; gap:10px; margin-bottom:20px; }.actions .el-button { margin-left:0; }
.location { display:flex; flex-wrap:wrap; gap:10px; width:100%; }.location > .el-input { flex:1; min-width:220px; }.location > .el-text { width:100%; }
</style>

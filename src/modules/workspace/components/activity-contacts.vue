<template>
	<div v-loading="busy">
		<el-alert v-if="error" :title="error" type="error" :closable="false" />
		<el-alert title="老板及涉及门店的有效店长必收。此处配置额外活动负责人。" type="info" :closable="false" />
		<el-form label-position="top">
			<el-form-item v-if="isPlatform" label="商家">
				<MerchantSelect v-model="merchantId" />
			</el-form-item>
			<el-form-item label="配置范围">
				<el-select v-model="storeId" @change="loadContacts">
					<el-option :value="0" label="商家负责人" />
					<el-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.name" />
				</el-select>
			</el-form-item>
			<el-form-item label="额外接收人">
				<el-select v-model="selected" multiple filterable>
					<el-option v-for="u in options" :key="u.id" :value="u.id" :label="u.name + ' · ' + u.roleName" />
				</el-select>
			</el-form-item>
			<el-button :disabled="!loaded" type="primary" @click="save">保存负责人</el-button>
			<el-button @click="loadStores">刷新</el-button>
		</el-form>
	</div>
</template>
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { isPlatform } from '/@/workspace';
import MerchantSelect from '../../merchant/components/merchant-select.vue';
import { featureApi } from '../feature-api';
const merchantId = ref<number>(), storeId = ref(0), selected = ref<number[]>([]);
const stores = ref<any[]>([]), options = ref<any[]>([]);
const busy = ref(false), loaded = ref(false), error = ref('');
const query = () => '?storeId=' + storeId.value + (merchantId.value ? '&merchantId=' + merchantId.value : '');
async function loadStores() {
	loaded.value = false;
	stores.value = [];
	if (isPlatform && !merchantId.value) return;
	try {
		stores.value = await featureApi('wallet/activities/contacts/stores' + (merchantId.value ? '?merchantId=' + merchantId.value : ''));
		storeId.value = 0;
		await loadContacts();
	} catch (e: any) { error.value = e.message; }
}
async function loadContacts() {
	busy.value = true;
	loaded.value = false;
	error.value = '';
	selected.value = [];
	try {
		options.value = await featureApi('wallet/activities/contacts/options' + query());
		selected.value = await featureApi('wallet/activities/contacts' + query());
		for (const id of selected.value) {
			if (!options.value.some(u => u.id === id)) options.value.push({ id, name: '已失效或已移出范围的负责人', roleName: '请移除后保存' });
		}
		loaded.value = true;
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
async function save() {
	busy.value = true;
	try {
		await featureApi('wallet/activities/contacts', { merchantId: merchantId.value, storeId: storeId.value, recipientIds: selected.value });
		ElMessage.success('已保存负责人');
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
watch(merchantId, loadStores);
onMounted(loadStores);
</script>

<template>
    <div style="height:100%">
	<cl-crud ref="Crud">
		<cl-row v-if="kind !== 'store'"><el-alert :title="kind === 'coupon' ? '启用模板后，还需提交审核、审核通过并上架，用户端才会展示该优惠券。' : '此处为模拟购券订单，仅支持查询，不代表实际收款或结算。'" type="info" :closable="false" show-icon /></cl-row>
		<cl-row><cl-refresh-btn /><cl-add-btn v-if="kind !== 'order'" /><cl-multi-delete-btn v-if="kind !== 'order'" /><cl-flex1 /></cl-row>
		<cl-row v-if="kind !== 'order'"><cl-search ref="Search" reset-btn /></cl-row>
		<cl-row>
			<cl-table ref="Table">
				<template #column-cover="{ scope }">
					<CouponCover v-if="kind === 'coupon'" :src="scope.row.cover" :name="scope.row.name" :badge="coverBadge(scope.row)" :type-name="typeNames[scope.row.type]" :subtitle="ruleText(scope.row)" />
					<el-image v-else-if="scope.row.cover" :src="scope.row.cover" :preview-src-list="[scope.row.cover]" preview-teleported fit="cover" style="width:64px;height:44px;border-radius:6px"><template #error>图片加载失败</template></el-image><span v-else>暂无封面</span>
				</template>
				<template #column-type="{ scope }"><el-tag class="coupon-type-tag" effect="dark" :color="typeColors[scope.row.type] || '#64748b'">{{ typeNames[scope.row.type] || '未知类型' }}</el-tag></template>
				<template #column-publicationActions="{ scope }">
					<el-button v-if="scope.row.publication?.auditStatus !== 1 && scope.row.publication?.publishStatus !== 1" link type="primary" :disabled="publicationBusy" @click="publication(scope.row, 'submitAudit')">提交审核</el-button>
					<el-button v-if="scope.row.publication?.auditStatus === 1" link :disabled="publicationBusy" @click="publication(scope.row, 'withdrawAudit')">撤回</el-button>
					<el-button v-if="scope.row.publication?.auditStatus === 2 && scope.row.publication?.publishStatus !== 1" link type="success" :disabled="publicationBusy" @click="publication(scope.row, 'publish')">上架</el-button>
					<el-button v-if="scope.row.publication?.publishStatus === 1" link type="danger" :disabled="publicationBusy" @click="publication(scope.row, 'unpublish')">下架</el-button>
				</template>
			</cl-table>
		</cl-row>
		<cl-row><cl-flex1 /><cl-pagination /></cl-row>
		<cl-upsert v-if="kind !== 'order'" ref="Upsert">
            <template #slot-cover="{scope}"><MerchantImageUpload :key="uploadEpoch" v-model="scope.cover" @busy-change="uploadBusy=$event" /></template>
            <template #slot-location="{scope}"><div class="location-field"><el-tag :type="validCoordinate(scope.longitude,scope.latitude)?'success':'warning'">{{validCoordinate(scope.longitude,scope.latitude)?'已设置位置（GCJ-02）':'待补充位置'}}</el-tag><span v-if="validCoordinate(scope.longitude,scope.latitude)">经度 {{scope.longitude}}，纬度 {{scope.latitude}}</span><el-button @click="pickFormLocation(scope)">地图选点 / 核对位置</el-button><span>选点后需保存门店资料才会写入</span></div></template>
			<template #slot-category="{ scope }"><el-select v-model="scope.categoryId" filterable placeholder="请选择门店类型" :loading="optionsLoading" style="width:100%"><el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" /></el-select></template>
			<template #slot-stores="{ scope }"><el-select v-model="scope.storeIds" multiple filterable :loading="optionsLoading" placeholder="选择适用门店" style="width:100%"><el-option v-for="store in storeOptions" :key="store.id" :label="store.name + (store.status === 1 ? '' : '（停用）')" :value="store.id" :disabled="store.status !== 1 && !originalStoreIds.includes(store.id)" /></el-select></template>
			<template #slot-issue-limit="{ scope }"><el-switch :model-value="scope.issueLimit == null" active-text="不限量" @update:model-value="value => scope.issueLimit = value ? null : Math.max(scope.issuedCount || 0, 1)" /><el-input-number v-if="scope.issueLimit != null" v-model="scope.issueLimit" :min="Math.max(scope.issuedCount || 0, 1)" :precision="0" controls-position="right" style="margin-left:12px;width:150px" /></template>
			<template #slot-content="{ scope }">
				<div v-for="(item, index) in scope.contentItems || []" :key="index" class="content-row"><el-input v-model="item.name" placeholder="项目名称" :disabled="!!item.productId" /><el-select v-model="item.type" :disabled="!!item.productId"><el-option label="商品" value="GOODS" /><el-option label="服务" value="SERVICE" /><el-option label="体验" value="EXPERIENCE" /></el-select><el-input-number v-model="item.quantity" :min="1" :precision="0" controls-position="right" /><el-button link type="danger" @click="scope.contentItems.splice(index, 1)">移除</el-button></div>
				<el-button @click="(scope.contentItems ||= []).push({name:'',type:'SERVICE',quantity:1})">添加内容项目</el-button>
			</template>
		</cl-upsert>
		<cl-dialog v-model="orderVisible" title="模拟购券订单详情" width="680px">
			<el-descriptions :column="2" border><el-descriptions-item label="订单编号">{{ order.order?.orderNo }}</el-descriptions-item><el-descriptions-item label="金额">{{ money(order.order?.totalAmount) }} 元</el-descriptions-item><el-descriptions-item label="订单状态">{{ orderStates[order.order?.orderStatus] || '—' }}</el-descriptions-item><el-descriptions-item label="模拟支付">{{ paymentStates[order.order?.paymentStatus] || '—' }}</el-descriptions-item></el-descriptions>
			<el-table :data="order.items" border><el-table-column prop="couponName" label="券名称" /><el-table-column prop="quantity" label="数量" /><el-table-column prop="unitPrice" label="单价" /><el-table-column prop="subtotal" label="小计" /></el-table>
		</cl-dialog>
	</cl-crud>
    <cl-dialog v-model="accountVisible" :title="(accountStore?.name || '') + ' · 门店账号管理'" width="1100px" height="70vh"><StoreAccounts v-if="accountVisible && accountStore" :key="accountStore.id" :store-id="accountStore.id" :store-name="accountStore.name" /></cl-dialog>
    <StoreLocationPicker v-if="kind==='store'" ref="LocationPicker" />
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import StoreAccounts from './store-accounts.vue';
import StoreLocationPicker from './store-location-picker.vue';
import { validCoordinate, updateStoreLocation } from '../map/store-map';
import { useCrud, useTable, useSearch, useUpsert } from '@cool-vue/crud';
import { ElMessage, ElMessageBox } from 'element-plus';
import { merchantRequest } from '/@/merchant-api';
import { createMerchantCrudService, type MerchantResource } from '../merchant-service';
import CouponCover from './coupon-cover.vue';
import MerchantImageUpload from './merchant-image-upload.vue';
const uploadBusy = ref(false), uploadEpoch = ref(0);

const props = defineProps<{ kind: MerchantResource }>();
const kind = props.kind;
const categories = ref<any[]>([]), storeOptions = ref<any[]>([]), originalStoreIds = ref<number[]>([]);
const optionsLoading = ref(false), optionsFailed = ref(false), publicationBusy = ref(false);
const orderVisible = ref(false), order = ref<any>({});
const typeNames: Record<string, string> = { FULL_REDUCTION: '满减券', DISCOUNT: '折扣券', CASH: '代金券', PACKAGE: '套餐券', EXPERIENCE: '体验券', EXCHANGE: '兑换券' };
const typeColors: Record<string, string> = { FULL_REDUCTION: '#c94b43', DISCOUNT: '#367bbd', CASH: '#a66a10', PACKAGE: '#7b5db8', EXPERIENCE: '#247f73', EXCHANGE: '#b34b7b' };
const orderStates: Record<string, string> = { PENDING: '待支付', PAID: '已完成', CANCELLED: '已取消', FAILED: '失败' };
const paymentStates: Record<string, string> = { UNPAID: '未支付', SUCCESS: '模拟支付成功', FAILED: '模拟支付失败' };
const contentTypes = ['PACKAGE', 'EXPERIENCE', 'EXCHANGE'];
const money = (value: any) => Number(value || 0).toFixed(2);
const numberText = (value: any) => value == null ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
function coverBadge(row: any) {
	if (row.type === 'CASH') return `${numberText(row.deductAmount)}元`;
	if (row.type === 'DISCOUNT') return `${numberText(row.discountRate)}折`;
	if (row.type === 'FULL_REDUCTION') return `减${numberText(row.discountAmount)}元`;
	return typeNames[row.type] || '优惠券';
}
function ruleText(row: any) {
	if (row.type === 'FULL_REDUCTION') return `满${numberText(row.minSpend)}元减${numberText(row.discountAmount)}元`;
	const threshold = row.minSpend > 0 ? `满${numberText(row.minSpend)}元可用，` : '';
	if (row.type === 'CASH') return `${threshold}抵扣${numberText(row.deductAmount)}元`;
	if (row.type === 'DISCOUNT') return `${threshold}${numberText(row.discountRate)}折${row.maxDiscount == null ? '' : `，最高优惠${numberText(row.maxDiscount)}元`}`;
	return row.description || '内容待配置';
}
const scopeText = (row: any) => row.scopeType === 0 ? '全部营业门店' : row.storeNames?.join('、') || '指定门店';
const LocationPicker=ref<InstanceType<typeof StoreLocationPicker>>();
const locationFingerprint=ref('');
function locationSignature(data:any){return `${(data.address||'').trim()}|${Number(data.longitude)}|${Number(data.latitude)}`;}
function pickFormLocation(data:any){LocationPicker.value?.open({address:data.address,longitude:data.longitude,latitude:data.latitude},location=>{
    Upsert.value?.setForm('address',location.address);Upsert.value?.setForm('longitude',location.longitude);Upsert.value?.setForm('latitude',location.latitude);locationFingerprint.value=locationSignature(location);
});}
async function editStoreLocation(row:any){
    try{const data=await merchantRequest('workspace/store/info?id='+row.id);LocationPicker.value?.open({address:data.address,longitude:data.longitude,latitude:data.latitude},async location=>{await updateStoreLocation(row.id,location);await Crud.value?.refresh();},'确认保存位置');}
    catch(error:any){ElMessage.error(error.message||'门店资料加载失败');}
}
const accountVisible=ref(false);
const accountStore=ref<{id:number;name:string}>();
function manageAccounts(row:any){accountStore.value={id:row.id,name:row.name};accountVisible.value=true;}
const opColumn: any = kind === 'order' ? { type: 'op', width: 130, buttons: [{ label: '查看详情', type: 'primary', onClick: ({ scope }: any) => detail(scope.row) }] } : { type: 'op', width: kind==='store'?350:140, buttons: ['edit', 'delete', ...(kind==='store'?[{label:'门店位置',onClick:({scope}:any)=>editStoreLocation(scope.row)},{label:'账号管理',onClick:({scope}:any)=>manageAccounts(scope.row)}]:[])] };
const columns: any[] = kind === 'coupon' ? [
	{ type: 'selection', fixed: 'left' }, { label: '券封面', prop: 'cover', width: 86, fixed: 'left' }, { label: '券名称', prop: 'name', minWidth: 210 },
	{ label: '平台审核', minWidth: 120, formatter: (row: any) => ['未提交', '待审核', '审核通过', '驳回'][row.publication?.auditStatus ?? 0] },
	{ label: '平台上架', minWidth: 100, formatter: (row: any) => ['未上架', '已上架', '已下架'][row.publication?.publishStatus ?? 0] },
	{ label: '券类型', prop: 'type', minWidth: 100 }, { label: '优惠内容', minWidth: 230, formatter: ruleText },
	{ label: '售价', prop: 'salePrice', minWidth: 110, formatter: (row: any) => Number(row.salePrice) === 0 ? '免费' : `${money(row.salePrice)} 元` },
	{ label: '适用门店', minWidth: 220, formatter: scopeText }, { label: '状态', minWidth: 90, formatter: (row: any) => ['草稿', '启用', '停用'][row.status] },
	{ label: '发布操作', prop: 'publicationActions', width: 180, fixed: 'right' }, opColumn
] : kind === 'store' ? [
	{ type: 'selection', fixed: 'left' }, { label: '门店封面', prop: 'cover', width: 100 }, { label: '门店名称', prop: 'name', minWidth: 200 }, { label: '门店类型', prop: 'categoryName', minWidth: 140 },
	{ label: '详细地址', prop: 'address', minWidth: 260 }, { label: '联系电话', prop: 'phone', minWidth: 150 }, { label: '营业时间', prop: 'businessHours', minWidth: 170 },
	{ label: '状态', minWidth: 90, formatter: (row: any) => row.status === 1 ? '营业' : '停用' }, opColumn
] : [
	{ label: '订单编号', prop: 'orderNo', minWidth: 220 }, { label: '订单状态', minWidth: 110, formatter: (row: any) => orderStates[row.orderStatus] || row.orderStatus },
	{ label: '模拟支付', minWidth: 140, formatter: (row: any) => paymentStates[row.paymentStatus] || row.paymentStatus }, { label: '金额', minWidth: 110, formatter: (row: any) => `${money(row.totalAmount)} 元` },
	{ label: '创建时间', prop: 'createTime', minWidth: 170 }, opColumn
];
const Table = useTable({ props: { showOverflowTooltip: true }, columns });
const Search = useSearch({ items: [{ label: kind === 'coupon' ? '券名称' : '门店名称', prop: 'keyWord', component: { name: 'el-input', props: { clearable: true, maxlength: 100, placeholder: kind === 'coupon' ? '输入券名称搜索' : '输入门店名称搜索' }, style: { width: '190px' } } }] });
const textInput = { name: 'el-input', props: { clearable: true, maxlength: 100 } };
const moneyInput = { name: 'el-input-number', props: { min: 0, max: 99999999.99, precision: 2, controlsPosition: 'right', style: { width: '100%' } } };
const dateInput = { name: 'el-date-picker', props: { type: 'datetime', valueFormat: 'YYYY-MM-DD HH:mm:ss', clearable: true, style: { width: '100%' } } };
const integerInput = { name: 'el-input-number', props: { min: 1, max: 2147483647, precision: 0, controlsPosition: 'right', style: { width: '100%' } } };
const field = (label: string, prop: string, component: any, extra: any = {}) => ({ label, prop, component, span: 12, ...extra });
const select = (options: any[]) => ({ name: 'el-select', options });
const storeItems: any[] = [
	field('门店名称', 'name', textInput, { required: true }), field('门店类型', 'categoryId', { name: 'slot-category' }, { required: true }),
	field('联系电话', 'phone', { name: 'el-input', props: { maxlength: 30 } }, { required: true }), field('营业状态', 'status', select([{ label: '营业', value: 1 }, { label: '停用', value: 0 }]), { required: true }),
	field('详细地址', 'address', { name: 'el-input', props: { maxlength: 500 } }, { required: true, span: 24 }),
    field('门店位置', '_locationPicker', {name:'slot-location'}, {span:24}), field('门店封面', 'cover', { name: 'slot-cover' }, { span: 24 }),
	field('营业时间', 'businessHours', { name: 'el-input', props: { maxlength: 4000, placeholder: '例如周一至周日 09:00–22:00' } }, { span: 24 })
];
const couponItems: any[] = [
	field('券名称', 'name', textInput, { required: true }), field('券类型', 'type', select(Object.entries(typeNames).map(([value, label]) => ({ value, label }))), { required: true }),
	field('模板状态', 'status', select([{ label: '草稿', value: 0 }, { label: '启用', value: 1 }, { label: '停用', value: 2 }]), { required: true }),
	field('优惠券封面', 'cover', { name: 'slot-cover' }, { span: 24 }), field('使用说明', 'description', { name: 'el-input', props: { type: 'textarea', rows: 3, maxlength: 2000, showWordLimit: true } }, { span: 24 }),
	field('套餐／服务内容', 'contentItems', { name: 'slot-content' }, { span: 24, hidden: ({ scope }: any) => !contentTypes.includes(scope.type) }),
	field('售价（元）', 'salePrice', moneyInput, { required: true }), field('最低消费（元）', 'minSpend', moneyInput, { hidden: ({ scope }: any) => contentTypes.includes(scope.type) }),
	field('减免金额（元）', 'discountAmount', moneyInput, { required: true, hidden: ({ scope }: any) => scope.type !== 'FULL_REDUCTION' }), field('抵扣金额（元）', 'deductAmount', moneyInput, { required: true, hidden: ({ scope }: any) => scope.type !== 'CASH' }),
	field('折扣数', 'discountRate', { name: 'el-input-number', props: { min: 0.01, max: 9.99, precision: 2, step: 0.1, controlsPosition: 'right', style: { width: '100%' } } }, { required: true, hidden: ({ scope }: any) => scope.type !== 'DISCOUNT' }),
	field('优惠上限（元）', 'maxDiscount', moneyInput, { hidden: ({ scope }: any) => scope.type !== 'DISCOUNT' }),
	field('适用门店', 'scopeType', select([{ label: '全部营业门店', value: 0 }, { label: '指定门店', value: 1 }]), { required: true }), field('选择门店', 'storeIds', { name: 'slot-stores' }, { required: true, span: 24, hidden: ({ scope }: any) => scope.scopeType !== 1 }),
	field('有效期方式', 'validityType', select([{ label: '发券后按天有效', value: 'AFTER_ISSUE' }, { label: '固定起止日期', value: 'FIXED' }]), { required: true }),
	field('有效天数', 'validDays', integerInput, { required: true, hidden: ({ scope }: any) => scope.validityType !== 'AFTER_ISSUE' }), field('有效期开始', 'validFrom', dateInput, { required: true, hidden: ({ scope }: any) => scope.validityType !== 'FIXED' }), field('有效期结束', 'validTo', dateInput, { required: true, hidden: ({ scope }: any) => scope.validityType !== 'FIXED' }),
	field('发行总量', 'issueLimit', { name: 'slot-issue-limit' }, { span: 24 }), field('每人限领（张）', 'perCustomerLimit', integerInput, { required: true }), field('同享其他优惠', 'allowCombine', select([{ label: '不允许', value: 0 }, { label: '允许', value: 1 }]), { required: true }),
	field('领取开始时间', 'saleStartTime', dateInput), field('领取结束时间', 'saleEndTime', dateInput)
];
const Upsert = useUpsert({
	dialog: { width: '860px', height: '65vh' }, props: { labelPosition: 'top' }, items: kind === 'store' ? storeItems : couponItems,
	onOpened(data) {
		uploadEpoch.value++; uploadBusy.value = false;
		const defaults = kind === 'store' ? { status: 1 } : { type: 'CASH', status: 0, salePrice: 0, minSpend: 0, deductAmount: 10, scopeType: 0, storeIds: [], contentItems: [], validityType: 'AFTER_ISSUE', validDays: 30, perCustomerLimit: 1, allowCombine: 0 };
		if (!data.id) Object.entries(defaults).forEach(([key, value]) => { if (data[key] == null) Upsert.value?.setForm(key, value); });
		if(kind==='store'){locationFingerprint.value=validCoordinate(data.longitude,data.latitude)?locationSignature(data):'';if(!data.id){Upsert.value?.setForm('longitude',null);Upsert.value?.setForm('latitude',null);}}
		originalStoreIds.value = [...(data.storeIds || [])]; void loadOptions();
	},
	onSubmit(data, { next, done }) {
		if (uploadBusy.value) { ElMessage.warning('图片正在上传，请上传完成后再保存'); done(); return; }
		if (optionsLoading.value || optionsFailed.value) { ElMessage.warning('门店或类型选项尚未加载完成，请关闭后重试'); done(); return; }
		const payload = { ...data };
        if(kind==='store'){
            const hasLongitude=data.longitude!==null&&data.longitude!==undefined&&data.longitude!=='';
            const hasLatitude=data.latitude!==null&&data.latitude!==undefined&&data.latitude!=='';
            if(hasLongitude||hasLatitude){
                if(!validCoordinate(data.longitude,data.latitude)){ElMessage.warning('门店坐标不完整或超出范围，请通过地图补录位置');done();return;}
                if(locationSignature(data)!==locationFingerprint.value){ElMessage.warning('地址已变更，请通过地图重新核对位置后保存');done();return;}
                payload.longitude=Number(data.longitude);payload.latitude=Number(data.latitude);
            }
            delete payload._locationPicker;
        }
		if (kind === 'coupon') {
			payload.contentItems = contentTypes.includes(data.type) ? data.contentItems || [] : [];
			if (data.scopeType === 0) payload.storeIds = [];
			for (const key of ['cover', 'description', 'minSpend', 'discountAmount', 'deductAmount', 'discountRate', 'maxDiscount', 'issueLimit', 'saleStartTime', 'saleEndTime', 'validFrom', 'validTo', 'validDays']) payload[key] = data[key] ?? null;
			if (data.type !== 'FULL_REDUCTION') payload.discountAmount = null;
			if (data.type !== 'CASH') payload.deductAmount = null;
			if (data.type !== 'DISCOUNT') { payload.discountRate = null; payload.maxDiscount = null; }
			if (contentTypes.includes(data.type)) payload.minSpend = null;
			if (data.validityType === 'FIXED') payload.validDays = null; else { payload.validFrom = null; payload.validTo = null; }
		}
		for (const key of ['merchantId', 'merchantName', 'categoryName', 'storeNames', 'issuedCount', 'ruleVersion', 'publication', 'createTime', 'updateTime', 'queryWrapper']) delete payload[key];
		next(payload);
	}
});
const Crud = useCrud({ service: createMerchantCrudService(kind) }, app => app.refresh());
async function loadOptions() {
	optionsLoading.value = true; optionsFailed.value = false;
	try { if (kind === 'store') categories.value = await merchantRequest('workspace/category/options'); else storeOptions.value = await merchantRequest('workspace/store/options'); }
	catch (error: any) { optionsFailed.value = true; ElMessage.error(error.message || '选项加载失败'); }
	finally { optionsLoading.value = false; }
}
async function publication(row: any, action: string) {
	try { await ElMessageBox.confirm('确认执行此优惠券状态操作？', '操作确认'); publicationBusy.value = true; await merchantRequest('workspace/coupon/publication', { id: row.id, version: row.ruleVersion, action }); await Crud.value?.refresh(); }
	catch (error: any) { if (error?.message) ElMessage.error(error.message); }
	finally { publicationBusy.value = false; }
}
async function detail(row: any) {
	try { order.value = await merchantRequest(`workspace/order/info?id=${row.id}`); orderVisible.value = true; }
	catch (error: any) { ElMessage.error(error.message); }
}
</script>

<style scoped>
.location-field { display:flex; flex-wrap:wrap; align-items:center; gap:10px; font-size:12px; color:var(--el-text-color-secondary); }
.coupon-type-tag { border:none; color:#fff; white-space:nowrap; }
.content-row { display:flex; gap:8px; margin-bottom:12px; }
.content-row .el-input-number { flex:none; width:120px; }
@media(max-width:760px) { .content-row { flex-wrap:wrap; } }
</style>

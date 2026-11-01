<template>
	<div class="content-editor">
		<p class="hint">
			例如：肥牛一份、虾滑一份，或洗鞋服务一次。可选择已有项目，也可输入名称后回车新增。
		</p>
		<el-alert
			v-if="loadFailed"
			title="已有项目加载失败，可以重试；已填写的内容不会被清空。"
			type="warning"
			:closable="false"
		/>
		<el-button v-if="loadFailed" link type="primary" @click="load">重新加载</el-button>
		<div v-for="(item, index) in modelValue || []" :key="index" class="content-row">
			<el-select
				:model-value="item.productId ?? item.name"
				filterable
				allow-create
				default-first-option
				:loading="loading"
				placeholder="选择项目或输入新名称"
				:disabled="!merchantId"
				@update:model-value="value => select(index, value)"
			>
				<el-option
					v-for="option in available"
					:key="option.id"
					:label="option.name"
					:value="option.id"
				/>
			</el-select>
			<el-select
				:model-value="item.type"
				:disabled="!!item.productId"
				placeholder="项目类型"
				@update:model-value="value => patch(index, { type: value })"
			>
				<el-option label="菜品／商品" value="GOODS" /><el-option
					label="服务"
					value="SERVICE"
				/><el-option label="体验" value="EXPERIENCE" />
			</el-select>
			<el-input-number
				:model-value="item.quantity"
				:min="1"
				:max="2147483647"
				:precision="0"
				controls-position="right"
				aria-label="项目数量"
				@update:model-value="value => patch(index, { quantity: value ?? undefined })"
			/>
			<el-input
				:model-value="item.itemDescription"
				maxlength="500"
				placeholder="规格说明，如150克／份、60分钟／次"
				@update:model-value="value => patch(index, { itemDescription: value })"
			/>
			<el-button type="danger" link @click="remove(index)">移除</el-button>
		</div>
		<el-button :disabled="!merchantId || (modelValue?.length || 0) >= 100" @click="add"
			>添加项目</el-button
		>
		<span v-if="!merchantId" class="hint">请先选择所属商家</span>
		<p class="hint">
			数量表示一次核销包含的项目数量；需分多次使用时应发多张券。新增项目在保存优惠券时才会写入。
		</p>
	</div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useCool } from '/@/cool';
export interface CouponContentItem {
	productId?: number;
	name?: string;
	type?: string;
	quantity?: number;
	itemDescription?: string | null;
}
interface ProductOption {
	id: number;
	name: string;
	type: string;
}
const props = defineProps<{ modelValue?: CouponContentItem[]; merchantId?: number }>();
const emit = defineEmits<{ 'update:modelValue': [items: CouponContentItem[]] }>();
const { service } = useCool();
const options = ref<ProductOption[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
let revision = 0;
const available = computed(() => {
	const rows = [...options.value];
	for (const item of props.modelValue || [])
		if (item.productId && !rows.some(row => row.id === item.productId))
			rows.push({
				id: item.productId,
				name: item.name || '项目已失效，请重新选择',
				type: item.type || 'GOODS'
			});
	return rows;
});
async function load() {
	const request = ++revision;
	options.value = [];
	loadFailed.value = false;
	loading.value = !!props.merchantId;
	if (!props.merchantId) return;
	try {
		const rows = await service.coupon.template.request({
			url: '/productOptions',
			method: 'GET',
			params: { merchantId: props.merchantId }
		});
		if (request === revision) options.value = rows;
	} catch {
		if (request === revision) loadFailed.value = true;
	} finally {
		if (request === revision) loading.value = false;
	}
}
watch(() => props.merchantId, load, { immediate: true });
function patch(index: number, value: Partial<CouponContentItem>) {
	emit(
		'update:modelValue',
		(props.modelValue || []).map((item, i) => (i === index ? { ...item, ...value } : item))
	);
}
function select(index: number, value: number | string) {
	const option = available.value.find(item => item.id === value);
	patch(
		index,
		option
			? { productId: option.id, name: option.name, type: option.type }
			: { productId: undefined, name: String(value).trim() }
	);
}
function add() {
	emit('update:modelValue', [
		...(props.modelValue || []),
		{ name: '', type: 'GOODS', quantity: 1 }
	]);
}
function remove(index: number) {
	emit(
		'update:modelValue',
		(props.modelValue || []).filter((_, i) => i !== index)
	);
}
</script>

<style scoped>
.content-editor {
	width: 100%;
}
.hint {
	color: var(--el-text-color-secondary);
	font-size: 12px;
	line-height: 1.6;
	margin: 6px 0 12px;
}
.content-row {
	display: grid;
	grid-template-columns: minmax(150px, 1.3fr) 110px 100px minmax(140px, 1fr) 40px;
	gap: 8px;
	margin: 12px 0;
	align-items: start;
}
.content-row :deep(.el-input-number) {
	width: 100%;
}
@media (max-width: 700px) {
	.content-row {
		grid-template-columns: 1fr 1fr;
		padding-bottom: 12px;
		border-bottom: 1px solid var(--el-border-color);
	}
}
</style>

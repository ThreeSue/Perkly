<template>
	<el-drawer
		:model-value="modelValue"
		title="制作任务专用奖励券"
		size="min(760px,100vw)"
		append-to-body
		:close-on-click-modal="false"
		:before-close="guard.close"
	>
		<el-alert
			title="沿用现有优惠券规则与审核。保存后提交审核；审核通过上架后才能发布集章活动。任务专用券不开放公开领取。"
			type="info"
			show-icon
			:closable="false"
		/>
		<el-form label-position="top" :disabled="saving">
			<el-form-item label="奖励券所属商家（必选）"
				><el-select
					v-model="form.merchantId"
					filterable
					style="width: 100%"
					@change="
						form.storeIds = [];
						form.contentItems = [];
					"
					><el-option
						v-for="m in merchants"
						:key="m.id"
						:label="m.name"
						:value="m.id" /></el-select
			></el-form-item>
			<el-form-item label="券名称（必填）"
				><el-input v-model="form.name" maxlength="100"
			/></el-form-item>
			<el-form-item label="券类型"
				><el-select v-model="form.type"
					><el-option
						v-for="(label, type) in types"
						:key="type"
						:value="type"
						:label="label" /></el-select
			></el-form-item>
			<el-form-item label="使用说明"
				><el-input v-model="form.description" type="textarea" maxlength="2000"
			/></el-form-item>
			<el-form-item label="封面"
				><CoverField v-model="form.cover" @busy-change="uploading = $event"
			/></el-form-item>
			<el-form-item v-if="monetary" label="最低消费（元，可为0）"
				><el-input-number v-model="form.minSpend" :min="0" :precision="2"
			/></el-form-item>
			<el-form-item v-if="form.type === 'CASH'" label="抵扣金额（元，必填）"
				><el-input-number v-model="form.deductAmount" :min="0.01" :precision="2"
			/></el-form-item>
			<el-form-item v-if="form.type === 'FULL_REDUCTION'" label="减免金额（元，必填）"
				><el-input-number v-model="form.discountAmount" :min="0.01" :precision="2"
			/></el-form-item>
			<template v-if="form.type === 'DISCOUNT'"
				><el-form-item label="折扣（9表示九折）"
					><el-input-number
						v-model="form.discountRate"
						:min="0.01"
						:max="9.99"
						:precision="2" /></el-form-item
				><el-form-item label="最高优惠金额（元，可留空）"
					><el-input-number
						v-model="form.maxDiscount"
						:min="0.01"
						:precision="2" /></el-form-item
			></template>
			<el-form-item v-if="!monetary" label="核销内容（必填）"
				><CouponContentEditor v-model="form.contentItems" :merchant-id="form.merchantId"
			/></el-form-item>
			<el-form-item label="奖励券适用门店"
				><el-radio-group v-model="form.scopeType"
					><el-radio :value="0">所属商家全部正常门店</el-radio
					><el-radio :value="1">指定门店</el-radio></el-radio-group
				></el-form-item
			>
			<el-form-item v-if="form.scopeType === 1" label="指定适用门店"
				><el-select v-model="form.storeIds" multiple style="width: 100%"
					><el-option
						v-for="s in currentStores"
						:key="s.id"
						:label="s.name"
						:value="s.id" /></el-select
			></el-form-item>
			<el-form-item label="发行总量（张，必填）"
				><el-input-number v-model="form.issueLimit" :min="1" :max="1000000" :precision="0"
			/></el-form-item>
			<el-form-item label="每位顾客总发放上限（张）"
				><el-input-number
					v-model="form.perCustomerLimit"
					:min="1"
					:max="1000"
					:precision="0"
			/></el-form-item>
			<el-form-item label="有效期"
				><el-radio-group v-model="form.validityType"
					><el-radio value="AFTER_ISSUE">发券后按天</el-radio
					><el-radio value="FIXED">固定日期</el-radio></el-radio-group
				></el-form-item
			>
			<el-form-item v-if="form.validityType === 'AFTER_ISSUE'" label="发券后有效天数"
				><el-input-number v-model="form.validDays" :min="1" :max="3650" :precision="0"
			/></el-form-item>
			<el-form-item v-else label="固定有效期"
				><el-date-picker
					v-model="form.validRange"
					type="datetimerange"
					value-format="YYYY-MM-DD HH:mm:ss"
					style="width: 100%"
			/></el-form-item>
			<el-form-item label="是否同享其他优惠"
				><el-switch v-model="form.allowCombine" :active-value="1" :inactive-value="0"
			/></el-form-item>
		</el-form>
		<template #footer
			><el-button :disabled="saving || uploading" @click="guard.close()">取消</el-button
			><el-button type="primary" :loading="saving" :disabled="uploading" @click="save"
				>保存并提交审核</el-button
			></template
		>
	</el-drawer>
</template>
<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import CouponContentEditor from '../../coupon/components/coupon-content-editor.vue';
import type { CouponContentItem } from '../../coupon/components/coupon-content-editor.vue';
import CoverField from '../../space/components/cover-field.vue';
import { useUnsavedForm } from '../composables/unsaved-form';
import { featureApi } from '../feature-api';
const props = defineProps<{ modelValue: boolean; merchants: any[] }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; created: [reward: any] }>();
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) });
const saving = ref(false),
	uploading = ref(false);
const blank = () => ({
	merchantId: undefined as number | undefined,
	name: '',
	type: 'CASH',
	description: '',
	cover: null as string | null,
	scopeType: 0,
	storeIds: [] as number[],
	contentItems: [] as CouponContentItem[],
	minSpend: 0,
	deductAmount: 10,
	discountAmount: 10,
	discountRate: 9,
	maxDiscount: undefined as number | undefined,
	issueLimit: 100,
	perCustomerLimit: 1,
	validityType: 'AFTER_ISSUE',
	validDays: 30,
	validRange: [] as string[],
	allowCombine: 1
});
const form = ref(blank()),
	guard = useUnsavedForm(form, visible, saving);
const types: Record<string, string> = {
	CASH: '代金券',
	FULL_REDUCTION: '满减券',
	DISCOUNT: '折扣券',
	PACKAGE: '套餐券',
	EXPERIENCE: '体验券',
	EXCHANGE: '兑换券'
};
const monetary = computed(() => ['CASH', 'FULL_REDUCTION', 'DISCOUNT'].includes(form.value.type));
const currentStores = computed(
	() => props.merchants.find(m => m.id === form.value.merchantId)?.stores || []
);
watch(
	() => props.modelValue,
	v => {
		if (v) {
			form.value = blank();
			guard.markClean();
		}
	}
);
async function save() {
	if (saving.value || uploading.value) return;
	const f = form.value;
	if (!f.merchantId || !f.name.trim() || !f.issueLimit || !f.perCustomerLimit)
		return ElMessage.warning('请填写所属商家、券名称和发放数量');
	if (f.scopeType === 1 && !f.storeIds.length) return ElMessage.warning('请选择奖励券适用门店');
	if (!monetary.value && !f.contentItems.length) return ElMessage.warning('请填写核销内容');
	if (f.validityType === 'FIXED' && f.validRange.length !== 2)
		return ElMessage.warning('请选择完整有效期');
	saving.value = true;
	try {
		const payload = {
			...f,
			scopeType: f.scopeType,
			storeIds: f.scopeType === 0 ? [] : f.storeIds,
			minSpend: monetary.value ? f.minSpend : null,
			deductAmount: f.type === 'CASH' ? f.deductAmount : null,
			discountAmount: f.type === 'FULL_REDUCTION' ? f.discountAmount : null,
			discountRate: f.type === 'DISCOUNT' ? f.discountRate : null,
			maxDiscount: f.type === 'DISCOUNT' ? f.maxDiscount : null,
			contentItems: monetary.value ? [] : f.contentItems,
			validFrom: f.validityType === 'FIXED' ? f.validRange[0] : null,
			validTo: f.validityType === 'FIXED' ? f.validRange[1] : null,
			validDays: f.validityType === 'AFTER_ISSUE' ? f.validDays : null
		};
		const r = await featureApi('promotions/stamps/rewards/create', payload);
		guard.markClean();
		visible.value = false;
		emit('created', r);
		ElMessage.success('任务专用奖励券已提交审核');
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
</script>


<template>
					<div class="coupon-editor-masthead">
						<div class="coupon-editor-ticket" aria-hidden="true">券</div>
						<div>
							<h2>{{ draft.id ? '编辑优惠券' : '设计一张新券' }}</h2>
						</div>
					</div>
					<CouponEditorPreview :draft="draft" :type-name="typeName" />
					<nav class="coupon-steps" :data-step="step" aria-label="新建券步骤">
						<div class="coupon-step-track" aria-hidden="true">
							<div :style="{ transform: 'scaleX(' + step / (couponSteps.length - 1) + ')' }" />
						</div>
						<button v-for="(label, index) in couponSteps" :key="label" type="button"
							:class="{ 'step-complete': step > index }"
							:aria-current="step === index ? 'step' : undefined"
							@click="emit('update:step', index)">
							<span>{{ step > index ? '✓' : index + 1 }}</span>
							<strong>{{ label }}</strong>
						</button>
					</nav>
					<div class="coupon-step-heading">
						<h2>{{ couponSteps[step] }}</h2>
						<p>{{ couponStepHints[step] }}</p>
					</div>
</template>
<script setup lang="ts">
import CouponEditorPreview from "/@/modules/business/components/coupon-editor-preview.vue";
defineProps<{ draft: Record<string, any>; typeName?: string; step: number }>();
const emit = defineEmits<{ "update:step": [value: number] }>();
const couponSteps = ['券类型与内容', '门店与发放', '封面与确认'];
const couponStepHints = [
	'设置券名和优惠规则，左侧会实时显示券面。',
	'设置适用门店、领取方式、有效期和发行数量。',
	'选择封面后检查左侧券面，再保存优惠券。'
];
</script>
<style src="./coupon-editor.scss"></style>

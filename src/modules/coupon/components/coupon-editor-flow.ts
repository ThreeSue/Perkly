import { ref } from 'vue';
const contentFields = ['merchantId', 'name', 'type', 'description', 'contentItems', 'minSpend', 'discountAmount', 'deductAmount', 'discountRate', 'maxDiscount'];
export function couponFieldStep(prop?: string) {
 return contentFields.includes(prop || '') ? 0 : prop === 'cover' ? 2 : 1;
}
export function useCouponEditorFlow() {
 const couponStep = ref(0);
 let initialStep = 0;
 function prepareCouponStep(step: number) { initialStep = step >= 0 && step <= 2 ? step : 0; }
 function resetCouponStep() { couponStep.value = initialStep; initialStep = 0; }
 function goToCouponStep(step: number) {
  if (step < 0 || step > 2 || step === couponStep.value) return;
  couponStep.value = step;
  document.querySelector('.cl-dialog:has(.coupon-steps) .coupon-editor-fields')?.parentElement?.scrollTo({ top: 0 });
 }
 return { couponStep, goToCouponStep, prepareCouponStep, resetCouponStep };
}

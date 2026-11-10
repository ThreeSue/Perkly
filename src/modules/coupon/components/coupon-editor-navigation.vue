<template>
					<div class="coupon-step-navigation">
						<el-button v-if="step > 0" @click="emit('update:step', step - 1)">上一步</el-button>
						<el-button v-if="step < 2" type="primary" @click="emit('update:step', step + 1)">下一步</el-button>
					</div>
</template>
<script setup lang="ts">
defineProps<{ step: number }>();
const emit = defineEmits<{ "update:step": [value: number] }>();
</script>

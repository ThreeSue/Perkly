<template>
	<el-select :model-value="modelValue ?? undefined" filterable remote remote-show-suffix clearable :remote-method="search" :loading="loading" :placeholder="placeholder" @visible-change="open => open && search('')" @update:model-value="change">
		<el-option v-for="item in options" :key="item.id" :value="item.id!" :label="Number(item.status) === 1 ? item.name : `${item.name}（停用）`" />
	</el-select>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useCool } from "/@/cool";
const props = withDefaults(defineProps<{ modelValue?: number | null; activeOnly?: boolean; placeholder?: string }>(), { placeholder: "输入名称或电话搜索商家" });
const emit = defineEmits<{ "update:modelValue": [value: number | undefined]; change: [value: number | undefined]; selected: [value: Eps.MerchantInfoEntity | undefined] }>();
const { service } = useCool();
const options = ref<Eps.MerchantInfoEntity[]>([]);
const selected = ref<Eps.MerchantInfoEntity>();
const loading = ref(false);
let revision = 0;
async function search(keyWord: string) {
	const current = ++revision;
	loading.value = true;
	try {
		const result = await service.merchant.info.page({ page: 1, size: 20, keyWord, ...(props.activeOnly ? { status: 1 } : {}) });
		if (current !== revision) return;
		options.value = result.list;
		if (selected.value && !options.value.some(item => item.id === selected.value!.id)) options.value.unshift(selected.value);
	} catch { if (current === revision) ElMessage.error("商家选项加载失败，请重试"); }
	finally { if (current === revision) loading.value = false; }
}
watch(() => props.modelValue, async id => {
	selected.value = undefined;
	if (id == null) return;
	try {
		const data = await service.merchant.info.info({ id });
		if (id !== props.modelValue) return;
		selected.value = data;
		if (!options.value.some(item => item.id === id)) options.value.unshift(data);
	} catch { ElMessage.error("所属商家加载失败，请重试"); }
}, { immediate: true });
function change(value: number | "") {
	const id = value === "" ? undefined : value;
	emit("update:modelValue", id);
	emit("change", id);
	emit("selected", options.value.find(item => item.id === id));
}
</script>

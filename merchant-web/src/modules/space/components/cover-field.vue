<template>
	<MerchantImageUpload
		v-if="!isPlatform"
		v-model="value"
		v-model:material-id="materialId"
		:can-browse="menu.perms.includes('material:view')"
		:can-manage="menu.perms.includes('material:manage')"
		:ai-draft="aiDraft"
		@busy-change="emit('busy-change', $event)"
	/>
	<div v-else class="cover-field">
		<cl-upload v-model="value" :material-library="false" type="image" accept="image/*" :limit="1" :limit-size="5" text="上传新图" />
		<Space
			v-if="canBrowse"
			:model-value="value || undefined"
			@update:model-value="selectCover"
			:multiple="false"
			:show-list="false"
			selectable
			accept="image/*"
			text="从素材库选择"
			title="选择封面素材"
		/>
		<el-button v-if="!canBrowse" disabled title="请管理员开放文件空间查看权限">从素材库选择</el-button>
		<MerchantMaterialPicker v-if="aiDraft?.merchantId && canAi" :merchant-id="aiDraft.merchantId" :current-url="value" :current-id="materialId" label="本商家素材库" @select="selectMerchantMaterial" />
		<AiCoverPicker v-if="aiDraft" :draft="aiDraft" @select="selectMerchantMaterial" />
	</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { checkPerm, useBase } from '/$/base';
import { isPlatform } from '/@/workspace';
import Space from './space.vue';
import MerchantImageUpload from '/$/business/components/merchant-image-upload.vue';
import MerchantMaterialPicker from '/$/business/components/merchant-material-picker.vue';
import AiCoverPicker from '/$/ai/components/cover-picker.vue';

const props = defineProps<{ aiDraft?: Record<string, any> }>();

const value = defineModel<string | null>();
const materialId = defineModel<number | null>('materialId');
const emit = defineEmits<{ 'busy-change': [value: boolean] }>();
function selectCover(url: unknown) {
	if (typeof url === 'string') { materialId.value = null; value.value = url; }
}
function selectMerchantMaterial(item: { id: number; url: string }) { materialId.value = item.id; value.value = item.url; }
const { user, menu } = useBase();
const canAi = computed(() => user.info?.username === 'admin' || menu.perms.includes('ai:image:generate') && menu.perms.includes(props.aiDraft?.id ? 'coupon:template:update' : 'coupon:template:add'));
const canBrowse = computed(() => isPlatform && (
	user.info?.username === 'admin' ||
	checkPerm({ and: ['space:info:page', 'space:type:list'] })
));
</script>

<style scoped>
.cover-field {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 12px;
}
</style>

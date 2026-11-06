<template>
	<MerchantImageUpload
		v-if="!isPlatform"
		v-model="value"
		v-model:material-id="materialId"
		:can-browse="menu.perms.includes('material:view')"
		:can-manage="menu.perms.includes('material:manage')"
		@busy-change="emit('busy-change', $event)"
	/>
	<div v-else class="cover-field">
		<cl-upload v-model="value" type="image" accept="image/*" :limit="1" :limit-size="5" text="上传新图" />
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
	</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { checkPerm, useBase } from '/$/base';
import { isPlatform } from '/@/workspace';
import Space from './space.vue';
import MerchantImageUpload from '/$/business/components/merchant-image-upload.vue';

const value = defineModel<string | null>();
const materialId = defineModel<number | null>('materialId');
const emit = defineEmits<{ 'busy-change': [value: boolean] }>();
function selectCover(url: unknown) {
	if (typeof url === 'string') value.value = url;
}
const { user, menu } = useBase();
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

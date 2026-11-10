<template>
	<div class="merchant-image-upload">
		<el-image
			v-if="!aiDraft && modelValue"
			:src="modelValue"
			:preview-src-list="[modelValue]"
			preview-teleported
			fit="cover"
			class="preview"
		>
			<template #error><CoverPlaceholder /></template>
		</el-image>
		<div v-else-if="!aiDraft" class="preview"><CoverPlaceholder /></div>
		<component :is="aiDraft ? AiCoverPicker : 'div'"
			v-bind="aiDraft ? { compact: true, draft: aiDraft, disabled: busy, cover: modelValue, onSelect: selectMaterial } : {}">
		<div class="actions">
			<el-upload
				ref="upload"
				accept="image/jpeg,image/png,image/gif"
				:show-file-list="false"
				:disabled="busy || !canBrowse || !canManage"
				:before-upload="validate"
				:http-request="send"
			>
				<el-button :loading="busy" type="primary" plain>{{
					modelValue ? '更换图片' : '上传图片'
				}}</el-button>
			</el-upload>
			<MerchantMaterialPicker
				v-if="canBrowse"
				:key="materialRevision"
				:disabled="busy"
				:current-id="materialId"
				:can-manage="canManage"
				:current-url="modelValue"
				@select="selectMaterial"
			/>
			<el-button v-else disabled title="请上级开放“素材库查看与选用”权限">从素材库选择</el-button>
			<el-button v-if="modelValue" :disabled="busy" @click="remove">移除图片</el-button>
		</div>
		</component>
		<span class="hint">支持 JPG、PNG、GIF，单张不超过5MB；上传成功即进入本商家素材库，保存资料后更新封面。</span>
		<span v-if="!canBrowse || !canManage" class="error" role="alert">请上级开放“素材库查看与选用”和“上传与管理素材”权限后上传；已有图片仍可保留。</span>
		<span v-if="error" class="error" role="alert">{{ error }}</span>
	</div>
</template>
<script setup lang="ts">
import CoverPlaceholder from '/$/space/components/cover-placeholder.vue';
import { onBeforeUnmount, ref } from 'vue';
import { type UploadInstance, type UploadRequestOptions, type UploadRawFile } from 'element-plus';
import MerchantMaterialPicker from './merchant-material-picker.vue';
import AiCoverPicker from '/$/ai/components/cover-picker.vue';
import { uploadMaterial, type MerchantMaterial } from '../merchant-material-api';
const props = defineProps<{ modelValue?: string | null; materialId?: number | null; canBrowse?: boolean; canManage?: boolean; aiDraft?: Record<string, any> }>();
const emit = defineEmits<{
	'update:modelValue': [value: string];
	'update:materialId': [value: number | null];
	'busy-change': [value: boolean];
}>();
const upload = ref<UploadInstance>(),
	busy = ref(false),
	error = ref('');
const materialRevision = ref(0);
let active = true,
	controller: AbortController | undefined;
function validate(file: UploadRawFile) {
	error.value = '';
	if (
		!/\.(jpe?g|png|gif)$/i.test(file.name) ||
		!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)
	) {
		error.value = '请选择JPG、PNG或GIF图片';
		return false;
	}
	if (!file.size || file.size > 5 * 1024 * 1024) {
		error.value = '图片不能为空且不能超过5MB';
		return false;
	}
	return true;
}
async function send(options: UploadRequestOptions) {
	if (busy.value) return;
	if (!props.canBrowse || !props.canManage) {
		error.value = '上传图片需要素材库查看与管理权限，请联系上级授权';
		return;
	}
	busy.value = true;
	emit('busy-change', true);
	error.value = '';
	controller = new AbortController();
	try {
		const material = await uploadMaterial(options.file, controller.signal);
		if (!material || !Number.isSafeInteger(material.id) || material.id <= 0)
			throw new Error('素材登记响应异常，请刷新素材库核对后再选择');
		const url = material.url;
		if (!active) return;
		if (
			typeof url !== 'string' ||
			!url ||
			url.length > 500 ||
			!/^(https?:\/\/|\/(?!\/))/.test(url)
		)
			throw new Error('上传响应异常，请重试');
		updateCover(url, material?.id ?? null);
		materialRevision.value++;
	} catch (cause: any) {
		if (active) error.value = cause?.message || '上传失败，请重新选择图片重试';
	} finally {
		if (active) {
			busy.value = false;
			emit('busy-change', false);
			upload.value?.clearFiles();
		}
	}
}
function updateCover(url: string, id: number | null) {
	emit('update:materialId', id);
	emit('update:modelValue', url);
}
function selectMaterial(material: MerchantMaterial) {
	if (busy.value) return;
	error.value = '';
	updateCover(material.url, material.id);
}
function remove() {
	if (!busy.value) {
		updateCover('', null);
		error.value = '';
	}
}
onBeforeUnmount(() => {
	active = false;
	controller?.abort();
	emit('busy-change', false);
});
</script>
<style scoped>
.merchant-image-upload {
	width: 100%;
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 10px;
}
.preview {
	width: 120px;
	height: 90px;
	border: 1px solid var(--el-border-color);
	border-radius: 6px;
}
.actions {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 8px;
}
.actions :deep(.el-button + .el-button) { margin-left: 0; }
.hint {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	line-height: 1.5;
}
.error {
	font-size: 13px;
	color: var(--el-color-danger);
}
</style>

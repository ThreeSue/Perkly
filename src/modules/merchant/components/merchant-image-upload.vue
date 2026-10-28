<template>
    <div class="merchant-image-upload">
        <el-image v-if="modelValue" :src="modelValue" :preview-src-list="[modelValue]" preview-teleported fit="cover" class="preview">
            <template #error>图片加载失败</template>
        </el-image>
        <div class="actions">
            <el-upload ref="upload" accept="image/jpeg,image/png,image/gif" :show-file-list="false"
                :disabled="busy" :before-upload="validate" :http-request="send">
                <el-button :loading="busy" type="primary" plain>{{ modelValue ? '更换图片' : '上传图片' }}</el-button>
            </el-upload>
            <el-button v-if="modelValue" :disabled="busy" @click="remove">移除图片</el-button>
        </div>
        <span class="hint">支持 JPG、PNG、GIF，单张不超过5MB；上传后保存资料才会更新封面。</span>
        <span v-if="error" class="error" role="alert">{{ error }}</span>
    </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { type UploadInstance, type UploadRequestOptions, type UploadRawFile } from 'element-plus';
import { uploadMerchantImage } from '/@/merchant-api';
defineProps<{ modelValue?: string | null }>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; 'busy-change': [value: boolean] }>();
const upload = ref<UploadInstance>(), busy = ref(false), error = ref('');
let active = true, controller: AbortController | undefined;
function validate(file: UploadRawFile) {
    error.value = '';
    if (!/\.(jpe?g|png|gif)$/i.test(file.name) || !['image/jpeg','image/png','image/gif'].includes(file.type)) {
        error.value = '请选择JPG、PNG或GIF图片'; return false;
    }
    if (!file.size || file.size > 5 * 1024 * 1024) { error.value = '图片不能为空且不能超过5MB'; return false; }
    return true;
}
async function send(options: UploadRequestOptions) {
    if (busy.value) return;
    busy.value = true; emit('busy-change', true); error.value = '';
    controller = new AbortController();
    try {
        const url = await uploadMerchantImage(options.file, controller.signal);
        if (!active) return;
        if (typeof url !== 'string' || !url || url.length > 500 || !/^(https?:\/\/|\/(?!\/))/.test(url)) throw new Error('上传响应异常，请重试');
        emit('update:modelValue', url);
    } catch (cause: any) {
        if (active) error.value = cause?.message || '上传失败，请重新选择图片重试';
    } finally {
        if (active) { busy.value = false; emit('busy-change', false); upload.value?.clearFiles(); }
    }
}
function remove() { if (!busy.value) { emit('update:modelValue', ''); error.value = ''; } }
onBeforeUnmount(() => { active = false; controller?.abort(); emit('busy-change', false); });
</script>
<style scoped>
.merchant-image-upload { display:flex; flex-direction:column; align-items:flex-start; gap:10px; }
.preview { width:120px; height:90px; border:1px solid var(--el-border-color); border-radius:6px; }
.actions { display:flex; align-items:center; gap:12px; }
.hint { font-size:12px; color:var(--el-text-color-secondary); line-height:1.5; }
.error { font-size:13px; color:var(--el-color-danger); }
</style>

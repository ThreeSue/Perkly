<template>
	<el-button :disabled="disabled" @click="open">从素材库选择</el-button>
	<el-dialog v-model="visible" title="选择商家素材" width="min(760px, 94vw)" append-to-body>
		<div class="filters">
			<el-select v-model="category" aria-label="素材分类" @change="search">
				<el-option label="全部分类" value="all" />
				<el-option label="未分类" :value="0" />
				<el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
			</el-select>
			<el-input v-model="keyword" placeholder="搜索素材名称" clearable @keyup.enter="search" @clear="search" />
			<el-button @click="search">搜索</el-button>
			<el-button v-if="canManage" :loading="importing" :disabled="loading || importing" @click="importCovers">导入旧封面</el-button>
		</div>
		<el-alert v-if="categoryError" :title="categoryError" type="warning" :closable="false" />
		<el-alert v-if="error" :title="error" type="error" :closable="false" />
		<div v-loading="loading" class="materials">
			<el-empty v-if="!loading && !error && !items.length" description="暂无匹配图片" />
			<el-radio-group v-else v-model="selectedId" aria-label="素材图片">
				<div v-for="item in items" :key="item.id" class="material">
					<el-image :src="item.url" :alt="item.name" :preview-src-list="[item.url]" preview-teleported fit="cover">
						<template #error><CoverPlaceholder /></template>
					</el-image>
					<el-radio :value="item.id" :title="item.name">{{ item.name }}</el-radio>
				</div>
			</el-radio-group>
		</div>
		<el-pagination
			v-if="total > 20"
			:current-page="page"
			:page-size="20"
			:total="total"
			layout="prev, pager, next"
			@update:current-page="load"
		/>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button type="primary" :disabled="disabled || loading || !selected" @click="confirm">选用图片</el-button>
		</template>
	</el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { importCoverMaterials } from '../merchant-material-api';
import CoverPlaceholder from '/$/space/components/cover-placeholder.vue';
import { listMaterials, listMaterialCategories, type MaterialCategory, type MerchantMaterial } from '../merchant-material-api';

const props = defineProps<{ disabled?: boolean; currentId?: number | null; currentUrl?: string | null; canManage?: boolean }>();
const emit = defineEmits<{ select: [material: MerchantMaterial] }>();
const visible = ref(false);
const categories = ref<MaterialCategory[]>([]);
const category = ref<number | 'all'>('all');
const keyword = ref('');
const appliedKeyword = ref('');
const items = ref<MerchantMaterial[]>([]);
const selectedId = ref<number>();
const selected = computed(() => items.value.find(item => item.id === selectedId.value));
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const importing = ref(false);
const error = ref('');
const categoryError = ref('');
let revision = 0;
let opening = 0;

async function importCovers() {
	if (!props.canManage || props.disabled || importing.value) return;
	importing.value = true;
	try {
		const result = await importCoverMaterials();
		ElMessage.success('已导入 ' + result.imported + ' 张，跳过 ' + result.skipped + ' 张重复或不适用图片');
		category.value = 'all';
		keyword.value = '';
		appliedKeyword.value = '';
		await load(1);
	} catch (cause: any) {
		error.value = cause?.message || '旧封面导入失败';
	} finally {
		importing.value = false;
	}
}

async function open() {
	if (props.disabled) return;
	const version = ++opening;
	visible.value = true;
	category.value = 'all';
	keyword.value = '';
	categories.value = [];
	categoryError.value = '';
	selectedId.value = props.currentId ?? undefined;
	search();
	try {
		const result = await listMaterialCategories();
		if (version === opening) categories.value = result;
	} catch (cause: any) {
		if (version === opening) categoryError.value = cause?.message || '分类加载失败';
	}
}

function search() {
	appliedKeyword.value = keyword.value.trim();
	void load(1);
}

async function load(nextPage: number) {
	const version = ++revision;
	page.value = nextPage;
	loading.value = true;
	items.value = [];
	error.value = '';
	try {
		const result = await listMaterials(nextPage, appliedKeyword.value, category.value === 'all' ? undefined : category.value);
		if (version !== revision) return;
		items.value = result.list;
		if (selectedId.value === undefined && props.currentUrl) {
			selectedId.value = result.list.find(item => item.url === props.currentUrl)?.id;
		}
		total.value = result.total;
	} catch (cause: any) {
		if (version !== revision) return;
		error.value = cause?.message || '素材加载失败';
		total.value = 0;
	} finally {
		if (version === revision) loading.value = false;
	}
}

function confirm() {
	if (!props.disabled && !loading.value && selected.value) {
		emit('select', selected.value);
		visible.value = false;
	}
}
</script>

<style scoped>
.filters { display: flex; gap: 8px; margin-bottom: 12px; }
.filters .el-select { width: 180px; flex-shrink: 0; }
.materials { min-height: 180px; margin: 12px 0; }
.materials .el-radio-group { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 12px; }
.material { min-width: 0; }
.material .el-image { display: block; width: 100%; height: 100px; border-radius: 6px; }
.material .el-radio { max-width: 100%; margin: 0; }
.material :deep(.el-radio__label) { overflow: hidden; text-overflow: ellipsis; }
</style>

<template>
	<ManagementShell
		title="支付优惠活动"
		:description="
			isPlatform
				? '设定储值卡支付折扣，审核商家门店活动；同笔支付取最大单项优惠'
				: '申请本商家授权门店活动，折扣由平台审核设定'
		"
	>
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
		<div class="management-toolbar">
			<el-button type="primary" @click="open">
				{{ isPlatform ? '发布活动' : '申请活动' }}</el-button
			><el-button :loading="busy" @click="load">刷新</el-button>
		</div>
		<el-table v-loading="busy" :data="rows" empty-text="暂无活动"
			><el-table-column prop="title" label="活动名称" min-width="180" /><el-table-column
				label="范围"
				min-width="180"
				><template #default="{ row }">{{
					row.stores.length
						? row.stores.map((s: any) => s.name).join('、')
						: '全部正常门店'
				}}</template></el-table-column
			><el-table-column label="折扣" width="100"
				><template #default="{ row }">{{
					row.discountRate ? row.discountRate + ' 折' : '待运营设定'
				}}</template></el-table-column
			><el-table-column label="状态" width="110"
				><template #default="{ row }">{{
					statusText[row.status] || row.status
				}}</template></el-table-column
			><el-table-column prop="startAt" label="开始" width="175" /><el-table-column
				prop="endAt"
				label="结束（不含）"
				width="175"
			/><el-table-column label="操作" width="170"
				><template #default="{ row }"
					><el-button
						v-if="isPlatform && row.status === 'PENDING'"
						link
						type="primary"
						@click="review(row)"
						>审核</el-button
					><el-button
						v-if="['PENDING', 'PUBLISHED'].includes(row.status)"
						link
						type="danger"
						@click="offline(row)"
						>下架</el-button
					><el-button link @click="show(row)">详情</el-button></template
				></el-table-column
			></el-table
		>
		<el-pagination
			v-model:current-page="page"
			:page-size="20"
			:total="total"
			layout="total, prev, pager, next"
			@current-change="load"
		/>
		<el-dialog
			v-model="visible"
			:title="isPlatform ? '发布储值支付折扣' : '申请门店活动'"
			width="min(660px,94vw)"
			:close-on-click-modal="false"
			><el-form label-position="top" :disabled="saving"
				><el-form-item label="活动名称"
					><el-input v-model="form.title" maxlength="100" /></el-form-item
				><el-form-item label="说明"
					><el-input
						v-model="form.description"
						type="textarea"
						:rows="3"
						maxlength="5000" /></el-form-item
				><el-form-item label="起止时间"
					><el-date-picker
						v-model="range"
						type="datetimerange"
						value-format="YYYY-MM-DD HH:mm:ss"
						start-placeholder="开始时间"
						end-placeholder="结束时间（不含）"
						style="width: 100%" /></el-form-item
				><el-form-item
					:label="
						isPlatform
							? '参与门店（留空表示全部正常门店）'
							: '参与门店（仅本商家授权范围）'
					"
					><el-select
						v-model="form.storeIds"
						multiple
						filterable
						:multiple-limit="100"
						style="width: 100%"
						><el-option
							v-for="s in stores"
							:key="s.id"
							:value="s.id"
							:label="s.merchantName + ' / ' + s.name" /></el-select></el-form-item
				><el-form-item v-if="isPlatform" label="支付折扣（9表示九折）"
					><el-input-number
						v-model="form.discountRate"
						:min="0.01"
						:max="9.99"
						:precision="2" /></el-form-item
				><el-button type="primary" :loading="saving" @click="save"
					>确认{{ isPlatform ? '发布' : '申请' }}</el-button
				></el-form
			></el-dialog
		>
		<el-dialog v-model="reviewVisible" title="审核商家活动" width="min(600px,94vw)"
			><p>{{ reviewing?.title }}</p>
			<p class="activity-description">{{ reviewing?.description }}</p>
			<el-form label-position="top"
				><el-form-item label="审批结果"
					><el-radio-group v-model="approval.approve"
						><el-radio :value="true">通过</el-radio
						><el-radio :value="false">驳回</el-radio></el-radio-group
					></el-form-item
				><el-form-item v-if="approval.approve" label="平台设定折扣"
					><el-input-number
						v-model="approval.discountRate"
						:min="0.01"
						:max="9.99"
						:precision="2" /></el-form-item
				><el-form-item label="审核备注（驳回必填）"
					><el-input
						v-model="approval.note"
						type="textarea"
						maxlength="500" /></el-form-item
				><el-button type="primary" :loading="saving" @click="submitReview"
					>确认审核</el-button
				></el-form
			></el-dialog
		>
		<el-dialog v-model="detailVisible" title="活动详情" width="min(600px,94vw)"
			><h3>{{ detail?.title }}</h3>
			<p class="activity-description">{{ detail?.description || '暂无说明' }}</p>
			<p>审核备注：{{ detail?.reviewNote || '无' }}</p></el-dialog
		>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isPlatform } from '/@/workspace';
import { featureApi } from '../feature-api';
import ManagementShell from '../components/management-shell.vue';
const statusText: Record<string, string> = {
	PENDING: '待审核',
	PUBLISHED: '已发布',
	REJECTED: '已驳回',
	OFFLINE: '已下架'
};
const rows = ref<any[]>([]),
	stores = ref<any[]>([]),
	page = ref(1),
	total = ref(0),
	busy = ref(false),
	saving = ref(false),
	error = ref(''),
	visible = ref(false),
	range = ref<string[]>([]);
const form = ref({ title: '', description: '', storeIds: [] as number[], discountRate: 9 });
const reviewing = ref<any>(),
	reviewVisible = ref(false),
	approval = ref({ approve: true, discountRate: 9, note: '' }),
	detail = ref<any>(),
	detailVisible = ref(false);
async function load() {
	busy.value = true;
	error.value = '';
	try {
		const r = await featureApi(`wallet/activities/page?pageNumber=${page.value}&pageSize=20`);
		rows.value = r.list;
		total.value = r.total;
	} catch (e: any) {
		rows.value = [];
		error.value = e.message;
	} finally {
		busy.value = false;
	}
}
async function open() {
	try {
		stores.value = await featureApi('wallet/activities/stores');
		form.value = { title: '', description: '', storeIds: [], discountRate: 9 };
		range.value = [];
		visible.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
async function save() {
	if (
		!form.value.title.trim() ||
		range.value.length !== 2 ||
		(!isPlatform && !form.value.storeIds.length)
	)
		return ElMessage.warning('请填写活动名称、时间及参与门店');
	saving.value = true;
	try {
		await featureApi('wallet/activities/create', {
			...form.value,
			discountRate: isPlatform ? form.value.discountRate : null,
			startAt: range.value[0],
			endAt: range.value[1]
		});
		visible.value = false;
		ElMessage.success(isPlatform ? '已发布' : '已提交申请');
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
function review(row: any) {
	reviewing.value = row;
	approval.value = { approve: true, discountRate: 9, note: '' };
	reviewVisible.value = true;
}
async function submitReview() {
	if (!approval.value.approve && !approval.value.note.trim())
		return ElMessage.warning('请填写驳回原因');
	saving.value = true;
	try {
		await featureApi('wallet/activities/review', { id: reviewing.value.id, ...approval.value });
		reviewVisible.value = false;
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
async function offline(row: any) {
	try {
		await ElMessageBox.confirm(
			'下架后将停止参与新的储值卡支付优惠，历史交易保持原快照。',
			'下架活动'
		);
	} catch {
		return;
	}
	try {
		await featureApi('wallet/activities/offline', { id: row.id });
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
function show(row: any) {
	detail.value = row;
	detailVisible.value = true;
}
onMounted(load);
</script>
<style scoped>
.el-pagination {
	margin-top: 16px;
}
.activity-description {
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	line-height: 1.8;
}
</style>

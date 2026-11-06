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
			<el-button v-if="canApply" type="primary" @click="open">
				{{ isPlatform ? '发布活动' : '申请活动' }}</el-button
			><el-button :loading="busy" @click="load">刷新</el-button>
			<el-button v-if="canConfigureContacts" @click="contactsVisible = true">通知负责人</el-button>
		</div>
		<el-table v-loading="busy" :data="rows" empty-text="暂无活动"
			><el-table-column prop="title" label="活动名称" min-width="180" /><el-table-column
				label="范围"
				min-width="180"
				><template #default="{ row }">{{
					storeScope(row)
				}}</template></el-table-column
			><el-table-column label="折扣" width="100"
				><template #default="{ row }">{{
                    row.kind === 'STAMP' ? '跨商家集章' : row.discountRate ? row.discountRate + ' 折' : '待运营设定'
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
						v-if="['PENDING', 'PUBLISHED'].includes(row.status) && (isPlatform || !row.platformActivity)"
						link
						type="danger"
						@click="offline(row)"
						>下架</el-button
					><el-button v-if="isPlatform && row.status === 'PUBLISHED'" link @click="configure(row)">启停与时间</el-button>
					<el-button v-if="!isPlatform && canParticipate && row.status === 'PUBLISHED'" link @click="participationId = row.id">参与设置</el-button>
					<el-button v-if="isPlatform" link @click="noticeId = row.id">发送与回执</el-button>
					<el-button link @click="show(row)">详情</el-button></template
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
		<el-drawer
			v-model="visible"
			:title="isPlatform ? '发布储值支付折扣' : '申请门店活动'"
			size="min(660px, 100vw)"
			:close-on-click-modal="false"
			>
			<el-alert
				class="publish-flow"
				:title="isPlatform ? '确认后由平台直接发布' : '提交申请 → 平台审核并设定折扣 → 审核通过后发布'"
				type="info"
				:closable="false"
				show-icon
			/>
			<el-form label-position="top" :disabled="saving"
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
			></el-drawer
		>
		<el-drawer
			v-model="detailVisible"
			title="审批详情与流转记录"
			size="min(860px, 100vw)"
			:close-on-press-escape="!saving"
			:show-close="!saving"
			:close-on-click-modal="!saving"
		>
			<template v-if="detail">
				<header class="activity-heading">
					<div>
                    <span>{{ detail.kind === 'STAMP' ? '跨商家消费集章' : '支付优惠活动' }}</span>
						<h2>{{ detail.title }}</h2>
					</div>
					<el-tag :type="activityFlow.tagType" size="large">{{ statusText[detail.status] }}</el-tag>
				</header>
				<p class="activity-description">{{ detail.description || '暂无说明' }}</p>
				<el-descriptions :column="1" border>
					<el-descriptions-item label="参与门店">{{ storeScope(detail) }}</el-descriptions-item>
					<el-descriptions-item label="活动时间">{{ detail.startAt }} 至 {{ detail.endAt }}（不含）</el-descriptions-item>
                    <el-descriptions-item label="活动规则">{{ detail.kind === 'STAMP' ? '同一商家只计一章，达标发奖励券' : detail.discountRate ? detail.discountRate + ' 折' : '未设定' }}</el-descriptions-item>
				</el-descriptions>
				<ApprovalFlow :presentation="activityFlow" />
				<el-form v-if="reviewMode" label-position="top" :disabled="saving"
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
					></el-form>
			</template>
			<template #footer>
				<el-button :disabled="saving" @click="detailVisible = false">关闭</el-button>
			</template>
		</el-drawer>
		<el-dialog v-model="scheduleVisible" title="储值支付折扣启停" width="min(600px,94vw)">
			<el-form label-position="top">
				<el-form-item label="活动折扣"><el-switch v-model="schedule.enabled" active-text="启用" inactive-text="暂停" /></el-form-item>
				<el-form-item label="生效时间">
					<el-date-picker v-model="schedule.range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" start-placeholder="开始" end-placeholder="结束（不含）" style="width: 100%" />
				</el-form-item>
				<el-button type="primary" :loading="saving" @click="saveSchedule">保存</el-button>
			</el-form>
		</el-dialog>
		<el-dialog v-model="contactsVisible" title="配置额外活动负责人" width="min(660px,94vw)">
			<ActivityContacts v-if="contactsVisible" />
		</el-dialog>
		<el-dialog :model-value="!!participationId" title="活动参与设置" width="min(720px,94vw)" @close="participationId = undefined">
			<ActivityParticipation v-if="participationId" :id="participationId" />
		</el-dialog>
		<el-dialog :model-value="!!noticeId" title="自动通知发送与回执" width="min(1000px,94vw)" @close="noticeId = undefined">
			<ActivityNoticeReceipts v-if="noticeId" :id="noticeId" />
		</el-dialog>
	</ManagementShell>
</template>
<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isPlatform } from '/@/workspace';
import { featureApi } from '../feature-api';
import ApprovalFlow from '../../approval/components/approval-flow.vue';
import type { FlowNode } from '../../approval/utils/flow';
import ManagementShell from '../components/management-shell.vue';
import { useBase } from '/$/base';
import ActivityParticipation from '../components/activity-participation.vue';
import ActivityContacts from '../components/activity-contacts.vue';
import ActivityNoticeReceipts from '../components/activity-notice-receipts.vue';
const { menu } = useBase();
const canApply = computed(() => isPlatform || menu.perms.includes('activity:apply'));
const canParticipate = computed(() => menu.perms.includes('activity:participate'));
const canConfigureContacts = computed(() => isPlatform || menu.perms.includes('activity:recipients:manage'));
const contactsVisible = ref(false);
const participationId = ref<number>(), noticeId = ref<number>();
const scheduleVisible = ref(false);
const schedule = ref({ id: 0, enabled: true, range: [] as string[] });
function configure(row: any) {
	schedule.value = { id: row.id, enabled: row.enabled !== false, range: [row.startAt, row.endAt] };
	scheduleVisible.value = true;
}
async function saveSchedule() {
	if (schedule.value.range.length !== 2) return ElMessage.warning('请选择起止时间');
	saving.value = true;
	try {
		await featureApi('wallet/activities/schedule', {
			id: schedule.value.id, enabled: schedule.value.enabled,
			startAt: schedule.value.range[0], endAt: schedule.value.range[1]
		});
		scheduleVisible.value = false;
		await load();
	} catch (e: any) { ElMessage.error(e.message); }
	finally { saving.value = false; }
}
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
const reviewMode = ref(false),
	approval = ref({ approve: true, discountRate: 9, note: '' }),
	detail = ref<any>(),
	detailVisible = ref(false);
function storeScope(row: any) {
	return row.stores.length ? row.stores.map((store: any) => store.name).join('、') : '发布范围内的正常门店';
}
const activityFlow = computed(() => {
	const row = detail.value;
	const nodes: FlowNode[] = [{
		key: 'created',
		title: row?.status === 'PENDING' || row?.status === 'REJECTED' ? '提交申请' : '创建活动',
		state: 'done',
		badge: '已完成',
		actorLabel: '提交人',
		actor: row?.submittedBy || '姓名不可用',
		context: '参与门店：' + (row ? storeScope(row) : '—')
	}];
	const published = row?.status === 'PUBLISHED' || (row?.status === 'OFFLINE' && row.discountRate);
	const rejected = row?.status === 'REJECTED';
	const pending = row?.status === 'PENDING';
	if (pending || rejected) {
		nodes.push({
			key: 'review',
			title: '平台运营审核',
			state: rejected ? 'rejected' : 'current',
			badge: rejected ? '已驳回' : '待审核',
			actorLabel: rejected ? '审核人' : '负责角色',
			actor: rejected ? '未记录' : '平台运营',
			context: rejected ? '本次申请未通过' : '审核申请并设定支付折扣',
			reason: row.reviewNote
		});
	}
	if (published || pending) {
		nodes.push({
			key: 'publish',
			title: '发布活动',
			state: published ? 'done' : 'future',
			badge: published ? '已发布' : '待发布',
			actorLabel: '处理方式',
			actor: published ? '平台发布' : '审核通过后发布',
            context: published ? (row.kind === 'STAMP' ? '跨商家消费集章，达标发奖励券' : '支付折扣：' + row.discountRate + ' 折') : '活动将按设定的时间生效',
			reason: published ? row.reviewNote : undefined
		});
	}
	if (row?.status === 'OFFLINE') {
		nodes.push({
			key: 'offline',
			title: '下架活动',
			state: 'withdrawn',
			badge: '已下架',
			actorLabel: '操作人',
			actor: '未记录',
			context: '活动已停止参与新的支付优惠',
			reason: published ? undefined : row.reviewNote
		});
	}
	return {
		nodes,
		nextStep: pending ? '等待平台审核' : (statusText[row?.status] || '状态未知'),
		tagType: pending ? 'warning' : rejected ? 'danger' : row?.status === 'PUBLISHED' ? 'success' : 'info'
	} as const;
});
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
	show(row);
	reviewMode.value = true;
	approval.value = { approve: true, discountRate: 9, note: '' };
}
async function submitReview() {
	if (saving.value || !reviewMode.value || detail.value?.status !== 'PENDING') return;
	if (!approval.value.approve && !approval.value.note.trim())
		return ElMessage.warning('请填写驳回原因');
	saving.value = true;
	try {
		await featureApi('wallet/activities/review', { id: detail.value.id, ...approval.value });
		detailVisible.value = false;
		ElMessage.success('审核已保存');
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
	reviewMode.value = false;
	detail.value = row;
	detailVisible.value = true;
}
onMounted(load);
</script>
<style scoped>
.el-pagination {
	margin-top: 16px;
}
.publish-flow {
	margin-bottom: 20px;
}
.activity-heading {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 16px;
}
.activity-heading span {
	color: var(--el-text-color-secondary);
}
.activity-heading h2 {
	margin: 10px 0 12px;
}
.activity-description {
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	line-height: 1.8;
}
</style>

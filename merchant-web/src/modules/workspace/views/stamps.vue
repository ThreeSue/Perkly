<template>
	<ManagementShell
		title="跨商家消费集章"
		description="顾客在指定的不同商家完成到店消费，每商家一章；集齐后自动发放奖励券"
	>
		<el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
		<div class="management-toolbar">
			<el-button type="primary" :disabled="loadingOptions" @click="edit()"
				>新建集章活动</el-button
			>
			<el-button :loading="busy" @click="load">刷新</el-button>
		</div>
		<el-row :gutter="16" class="rule-cards">
			<el-col :sm="8"
				><el-card shadow="never"
					><strong>按不同商家集章</strong>
					<p>同一商家多个门店仅一章</p></el-card
				></el-col
			>
			<el-col :sm="8"
				><el-card shadow="never"
					><strong>只认已完成到店消费</strong>
					<p>内容券零补款核销也计入，购券不计入</p></el-card
				></el-col
			>
			<el-col :sm="8"
				><el-card shadow="never"
					><strong>每人每活动奖励一次</strong>
					<p>库存不足保留进度，补库后可重试</p></el-card
				></el-col
			>
		</el-row>
		<el-table
			v-loading="busy"
			:data="rows"
			empty-text="暂无集章活动，点击新建配置商家目标和奖励"
		>
			<el-table-column prop="title" label="活动名称" min-width="180" />
			<el-table-column label="集章目标" min-width="180"
				><template #default="{ row }">{{
					row.goals.map((g: any) => g.merchantName).join('、')
				}}</template></el-table-column
			>
			<el-table-column label="奖励券" min-width="160"
				><template #default="{ row }">{{ row.reward.name }}</template></el-table-column
			>
			<el-table-column label="状态" width="120"
				><template #default="{ row }"
					><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{
						state(row)
					}}</el-tag></template
				></el-table-column
			>
			<el-table-column label="库存余量" width="100"
				><template #default="{ row }">{{
					row.rewardStock - row.grantedCount
				}}</template></el-table-column
			>
			<el-table-column label="操作" min-width="290"
				><template #default="{ row }">
					<el-button link type="primary" @click="edit(row)">{{
						row.status === 'DRAFT' ? '编辑' : '详情'
					}}</el-button>
					<el-button
						v-if="row.status === 'DRAFT'"
						link
						type="primary"
						:disabled="saving"
						@click="publish(row)"
						>发布</el-button
					>
					<el-button v-if="row.status === 'PUBLISHED'" link @click="openSchedule(row)"
						>启停与时间</el-button
					>
					<el-button link @click="showProgress(row)">进度与奖励</el-button>
					<el-button link @click="stock(row)">补库存</el-button>
					<el-button v-if="row.status !== 'DRAFT'" link @click="noticeId = row.id"
						>通知回执</el-button
					>
					<el-button
						v-if="row.status === 'PUBLISHED'"
						link
						type="danger"
						:disabled="saving"
						@click="closeCampaign(row)"
						>下架</el-button
					>
				</template></el-table-column
			>
		</el-table>
		<el-pagination
			v-model:current-page="page"
			:page-size="20"
			:total="total"
			layout="total, prev, pager, next"
			@current-change="load"
		/>
		<el-drawer
			v-model="visible"
			:title="readonly ? '集章活动详情' : form.id ? '编辑集章草稿' : '新建集章活动'"
			size="min(860px,100vw)"
			:close-on-click-modal="false"
			:before-close="guard.close"
		>
			<el-alert
				title="发布后商家目标、门店范围、奖励券及其规则版本固定。每位顾客只奖励一次，不循环集章。"
				type="info"
				:closable="false"
				show-icon
			/>
			<el-form label-position="top" :disabled="saving || readonly || loadingOptions">
				<el-form-item label="活动名称（必填）"
					><el-input v-model="form.title" maxlength="100" show-word-limit
				/></el-form-item>
				<el-form-item label="活动说明"
					><el-input
						v-model="form.description"
						type="textarea"
						maxlength="5000"
						:rows="3"
				/></el-form-item>
				<el-form-item label="活动时间（北京时间，结束时间不含）"
					><el-date-picker
						v-model="form.range"
						type="datetimerange"
						value-format="YYYY-MM-DD HH:mm:ss"
						start-placeholder="开始时间"
						end-placeholder="结束时间"
						style="width: 100%"
				/></el-form-item>
				<el-divider content-position="left"
					>集章目标 · {{ form.goals.length }} 个不同商家</el-divider
				>
				<el-form-item label="选择2至20个不同商家"
					><el-select
						:model-value="form.goals.map((g: any) => g.merchantId)"
						multiple
						filterable
						:multiple-limit="20"
						style="width: 100%"
						@update:model-value="selectMerchants"
					>
						<el-option
							v-for="m in options"
							:key="m.id"
							:value="m.id"
							:label="m.name"
						/> </el-select
				></el-form-item>
				<el-card
					v-for="goal in form.goals"
					:key="goal.merchantId"
					shadow="never"
					class="goal-card"
				>
					<strong>{{ merchantName(goal.merchantId) }} · 一章</strong>
					<el-form-item label="参与门店（不选表示该商家保存时全部正常门店）">
						<el-select v-model="goal.storeIds" multiple filterable style="width: 100%"
							><el-option
								v-for="s in merchantStores(goal.merchantId)"
								:key="s.id"
								:label="s.name"
								:value="s.id"
						/></el-select>
					</el-form-item>
				</el-card>
				<el-divider content-position="left">达标奖励</el-divider>
				<el-form-item label="任务专用奖励券（必选）">
					<el-select
						v-model="form.rewardTemplateId"
						filterable
						style="width: 100%"
						placeholder="选择已有任务奖励券"
						><el-option
							v-for="r in rewards"
							:key="r.id"
							:value="r.id"
							:label="r.name + ' · ' + rewardState(r)"
					/></el-select>
				</el-form-item>
				<el-button v-if="canMakeReward" @click="rewardEditor = true">制作奖励券</el-button>
				<el-button @click="loadOptions">刷新奖励券</el-button>
				<el-form-item label="活动奖励库存（张，必填）"
					><el-input-number
						v-model="form.rewardStock"
						:min="1"
						:max="1000000"
						:precision="0"
				/></el-form-item>
			</el-form>
			<el-card v-if="selectedReward" shadow="never" class="goal-card">
				<template #header
					><strong>{{ selectedReward.name }}</strong
					><el-tag class="reward-tag">{{ rewardState(selectedReward) }}</el-tag></template
				>
				<p>
					{{ couponTypes[selectedReward.type] }} ·
					{{ selectedReward.merchantName || '所属商家资料待补充' }}
				</p>
				<p>{{ benefit(selectedReward) }}</p>
				<p>
					{{
						selectedReward.validityType === 'AFTER_ISSUE'
							? '发券后' + selectedReward.validDays + '天有效'
							: selectedReward.validFrom + ' 至 ' + selectedReward.validTo
					}}
				</p>
				<p>
					每人上限 {{ selectedReward.perCustomerLimit }} 张；券库存
					{{
						selectedReward.issueLimit == null
							? '不限量'
							: selectedReward.issueLimit - selectedReward.issuedCount
					}}
					张
				</p>
				<p>
					适用门店：{{
						selectedReward.scopeType === 0
							? '所属商家的全部正常门店'
							: (selectedReward.storeNames || []).join('、')
					}}
				</p>
				<p>{{ selectedReward.description }}</p>
				<el-button
					v-if="canMakeReward && selectedReward.publication?.auditStatus === 1"
					:loading="saving"
					type="primary"
					@click="reviewReward(true)"
					>审核通过并上架奖励券</el-button
				>
				<el-button
					v-if="canMakeReward && selectedReward.publication?.auditStatus === 1"
					:disabled="saving"
					@click="reviewReward(false)"
					>驳回奖励券</el-button
				>
			</el-card>
			<el-alert
				v-if="!readonly"
				:title="
					'发布预览：集齐 ' +
					form.goals.length +
					' 个不同商家的章，获得 ' +
					(selectedReward?.name || '待选择奖励券') +
					' 一张。每人本活动限一次。'
				"
				type="success"
				:closable="false"
			/>
			<template #footer
				><el-button :disabled="saving" @click="guard.close()">关闭</el-button
				><el-button v-if="!readonly" type="primary" :loading="saving" @click="save"
					>保存草稿</el-button
				></template
			>
		</el-drawer>
		<StampRewardEditor v-model="rewardEditor" :merchants="options" @created="rewardCreated" />
		<el-dialog
			v-model="scheduleVisible"
			title="集章活动启停与时间"
			width="min(640px,94vw)"
			:close-on-click-modal="false"
		>
			<el-alert
				title="已赚到的章和已达标待发奖励保留。调整只影响后续完成的到店消费。"
				type="info"
				:closable="false"
			/>
			<el-form label-position="top" :disabled="saving"
				><el-form-item label="活动开关"
					><el-switch v-model="schedule.enabled" active-text="启用" inactive-text="暂停"
				/></el-form-item>
				<el-form-item label="生效时间"
					><el-date-picker
						v-model="schedule.range"
						type="datetimerange"
						value-format="YYYY-MM-DD HH:mm:ss"
						style="width: 100%"
				/></el-form-item>
			</el-form>
			<template #footer
				><el-button :disabled="saving" @click="scheduleVisible = false">取消</el-button
				><el-button type="primary" :loading="saving" @click="saveSchedule"
					>保存调整</el-button
				></template
			>
		</el-dialog>
		<el-drawer
			v-model="progressVisible"
			:title="progressTitle + ' · 进度与奖励'"
			size="min(1080px,100vw)"
		>
			<el-alert
				v-if="progressError"
				:title="progressError"
				type="error"
				:closable="false"
			/><el-button :loading="progressBusy" @click="loadProgress">刷新进度</el-button>
			<el-table
				v-loading="progressBusy"
				:data="progressRows"
				empty-text="暂无顾客集章记录，完成有效到店消费后自动计入"
			>
				<el-table-column prop="customerName" label="顾客" min-width="140" />
				<el-table-column label="进度" width="100"
					><template #default="{ row }"
						>{{ row.stampCount }} / {{ row.targetCount }}</template
					></el-table-column
				>
				<el-table-column label="已集商家" min-width="180"
					><template #default="{ row }">{{
						row.stampedMerchantIds.map(merchantName).join('、')
					}}</template></el-table-column
				>
				<el-table-column label="奖励状态" width="110"
					><template #default="{ row }">{{
						rewardStatuses[row.rewardStatus]
					}}</template></el-table-column
				>
				<el-table-column prop="attempts" label="发放次数" width="90" /><el-table-column
					prop="lastError"
					label="失败原因"
					min-width="180"
				/>
				<el-table-column label="操作" width="160"
					><template #default="{ row }"
						><el-button
							v-if="row.rewardStatus === 'PENDING'"
							:disabled="saving"
							link
							type="primary"
							@click="retry(row)"
							>重试发奖</el-button
						><el-button link @click="showAttempts(row)">发放记录</el-button></template
					></el-table-column
				>
			</el-table>
			<el-pagination
				v-model:current-page="progressPage"
				:page-size="20"
				:total="progressTotal"
				layout="total, prev, pager, next"
				@current-change="loadProgress"
			/>
		</el-drawer>
		<el-dialog v-model="attemptVisible" title="奖励发放记录" width="min(700px,94vw)"
			><el-table :data="attemptRows" empty-text="尚未尝试发放"
				><el-table-column label="结果"
					><template #default="{ row }">{{
						row.status === 'SUCCESS' ? '成功' : '失败'
					}}</template></el-table-column
				><el-table-column prop="message" label="记录" min-width="240" /><el-table-column
					prop="createTime"
					label="时间"
					min-width="160" /></el-table
		></el-dialog>
		<el-dialog
			:model-value="!!noticeId"
			title="集章活动通知与回执"
			width="min(1000px,94vw)"
			@close="noticeId = undefined"
			><ActivityNoticeReceipts v-if="noticeId" :id="noticeId"
		/></el-dialog>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useBase } from '/$/base';
import { featureApi } from '../feature-api';
import ManagementShell from '../components/management-shell.vue';
import ActivityNoticeReceipts from '../components/activity-notice-receipts.vue';
import StampRewardEditor from '../components/stamp-reward-editor.vue';
import { useUnsavedForm } from '../composables/unsaved-form';
const { menu, user } = useBase();
const canMakeReward = computed(
	() =>
		user.info?.username === 'admin' ||
		['coupon:template:add', 'coupon:template:update'].every(p => menu.perms.includes(p))
);
const rows = ref<any[]>([]),
	options = ref<any[]>([]),
	rewards = ref<any[]>([]);
const page = ref(1),
	total = ref(0),
	busy = ref(false),
	loadingOptions = ref(false),
	error = ref(''),
	saving = ref(false),
	visible = ref(false),
	readonly = ref(false);
const blank = () => ({
	id: undefined as number | undefined,
	version: undefined as number | undefined,
	title: '',
	description: '',
	range: [] as string[],
	goals: [] as { merchantId: number; storeIds: number[] }[],
	rewardTemplateId: undefined as number | undefined,
	rewardStock: 100
});
const form = ref(blank()),
	guard = useUnsavedForm(form, visible, saving);
const selectedReward = computed(() =>
	rewards.value.find(r => r.id === form.value.rewardTemplateId)
);
const rewardEditor = ref(false),
	noticeId = ref<number>();
const couponTypes: Record<string, string> = {
	CASH: '代金券',
	FULL_REDUCTION: '满减券',
	DISCOUNT: '折扣券',
	PACKAGE: '套餐券',
	EXPERIENCE: '体验券',
	EXCHANGE: '兑换券'
};
const rewardStatuses: Record<string, string> = {
	COLLECTING: '集章中',
	PENDING: '待发放 / 待重试',
	ISSUED: '已发放'
};
const merchantName = (id: number) =>
	options.value.find(m => m.id === id)?.name ||
	rows.value.flatMap(r => r.goals).find(g => g.merchantId === id)?.merchantName ||
	'历史商家';
const merchantStores = (id: number) => options.value.find(m => m.id === id)?.stores || [];
const rewardState = (r: any) =>
	r.publication?.publishStatus === 1
		? '已审核上架'
		: r.publication?.auditStatus === 1
			? '待审核'
			: r.publication?.auditStatus === 3
				? '已驳回'
				: '待提交审核';
const benefit = (r: any) =>
	r.type === 'CASH'
		? '抵扣 ' + r.deductAmount + ' 元'
		: r.type === 'FULL_REDUCTION'
			? '满 ' + r.minSpend + ' 元减 ' + r.discountAmount + ' 元'
			: r.type === 'DISCOUNT'
				? r.discountRate + ' 折'
				: (r.contentItems || []).map((c: any) => c.name + ' × ' + c.quantity).join('、');
const state = (r: any) =>
	r.status === 'DRAFT'
		? '草稿'
		: r.status === 'OFFLINE'
			? '已下架'
			: !r.enabled
				? '已暂停'
				: new Date(r.endAt).getTime() <= Date.now()
					? '已结束'
					: new Date(r.startAt).getTime() > Date.now()
						? '未开始'
						: '进行中';
async function load() {
	busy.value = true;
	error.value = '';
	try {
		const r = await featureApi(
			'promotions/stamps/page?pageNumber=' + page.value + '&pageSize=20'
		);
		rows.value = r.list;
		total.value = r.total;
	} catch (e: any) {
		error.value = e.message;
		rows.value = [];
	} finally {
		busy.value = false;
	}
}
async function loadOptions() {
	loadingOptions.value = true;
	try {
		const [m, r] = await Promise.all([
			featureApi('promotions/stamps/options'),
			featureApi('promotions/stamps/rewards')
		]);
		options.value = m;
		rewards.value = r;
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		loadingOptions.value = false;
	}
}
async function edit(row?: any) {
	if (loadingOptions.value) return;
	try {
		if (row) {
			const r = await featureApi('promotions/stamps/info?id=' + row.id);
            rewards.value = [...rewards.value.filter(c => c.id !== r.reward.id), r.reward];
			form.value = {
				...blank(),
				id: r.id,
				version: r.version,
				title: r.title,
				description: r.description || '',
				range: [r.startAt, r.endAt],
				goals: r.goals.map((g: any) => ({
					merchantId: g.merchantId,
					storeIds: g.stores.map((s: any) => s.id)
				})),
				rewardTemplateId: r.reward.id,
				rewardStock: r.rewardStock
			};
			readonly.value = r.status !== 'DRAFT';
		} else {
			form.value = blank();
			readonly.value = false;
		}
		guard.markClean();
		visible.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
function selectMerchants(ids: number[]) {
	form.value.goals = ids.map(
		id => form.value.goals.find(g => g.merchantId === id) || { merchantId: id, storeIds: [] }
	);
}
async function save() {
	if (saving.value) return;
	if (
		!form.value.title.trim() ||
		form.value.range.length !== 2 ||
		form.value.goals.length < 2 ||
		!form.value.rewardTemplateId ||
		!form.value.rewardStock
	)
		return ElMessage.warning('请填写名称、时间、至少两个商家、奖励券和库存');
	saving.value = true;
	try {
		await featureApi('promotions/stamps/save', {
			...form.value,
			startAt: form.value.range[0],
			endAt: form.value.range[1]
		});
		guard.markClean();
		visible.value = false;
		ElMessage.success('草稿已保存，核对后可在列表发布');
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
async function publish(row: any) {
	if (saving.value) return;
	try {
		await ElMessageBox.confirm(
			'活动：' +
				row.title +
				'；需完成 ' +
				row.goals.map((g: any) => g.merchantName).join('、') +
				' 共 ' +
				row.goals.length +
				' 个商家。奖励：' +
				row.reward.name +
				' 一张；活动库存 ' +
				row.rewardStock +
				' 张。发布后目标和奖励规则不可修改。',
			'核对并发布集章活动',
			{ confirmButtonText: '确认发布', cancelButtonText: '继续核对' }
		);
	} catch {
		return;
	}
	await mutate('publish', { id: row.id, version: row.version });
}
async function mutate(action: string, data: any) {
	saving.value = true;
	try {
		await featureApi('promotions/stamps/' + action, data);
		ElMessage.success('操作已保存');
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
async function closeCampaign(row: any) {
	try {
		await ElMessageBox.confirm(
			'下架后停止新增集章，已获得的进度及待发奖励保留。',
			'下架集章活动'
		);
	} catch {
		return;
	}
	await mutate('close', { id: row.id, version: row.version });
}
async function stock(row: any) {
	if (saving.value) return;
	try {
		const { value } = await ElMessageBox.prompt(
			'输入新增张数，同时补充活动额度和有限量任务券库存；已审核优惠规则保持原版本。',
			'补充奖励库存',
			{ inputPattern: /^[1-9][0-9]{0,6}$/, inputErrorMessage: '请输入1至1000000的整数' }
		);
		await mutate('stock', {
			id: row.id,
			requestId: crypto.randomUUID(),
			amount: Number(value)
		});
	} catch {
		/* 取消输入 */
	}
}
const scheduleVisible = ref(false),
	schedule = ref({ id: 0, version: 0, enabled: true, range: [] as string[] });
function openSchedule(row: any) {
	schedule.value = {
		id: row.id,
		version: row.version,
		enabled: row.enabled,
		range: [row.startAt, row.endAt]
	};
	scheduleVisible.value = true;
}
async function saveSchedule() {
	if (saving.value) return;
	if (schedule.value.range.length !== 2) return ElMessage.warning('请选择时间');
	saving.value = true;
	try {
		await featureApi('promotions/stamps/schedule', {
			...schedule.value,
			startAt: schedule.value.range[0],
			endAt: schedule.value.range[1]
		});
		scheduleVisible.value = false;
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
async function rewardCreated(reward: any) {
	await loadOptions();
	form.value.rewardTemplateId = reward.id;
}
async function reviewReward(approved: boolean) {
	if (saving.value || !selectedReward.value) return;
	let reason = '';
	try {
		if (!approved) {
			const r = await ElMessageBox.prompt('请填写驳回原因', '驳回奖励券', {
				inputValidator: (v: string) => !!v?.trim() || '请输入原因'
			});
			reason = r.value;
		} else
			await ElMessageBox.confirm(
				'确认审核此任务专用奖励券并上架？不会开放公开领取。',
				'审核奖励券'
			);
	} catch {
		return;
	}
	saving.value = true;
	try {
		const r = await featureApi('promotions/stamps/rewards/review', {
			id: selectedReward.value.id,
			version: selectedReward.value.ruleVersion,
			approved,
			reason
		});
		rewards.value = rewards.value.map(c => (c.id === r.id ? r : c));
		ElMessage.success('审核完成');
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
const progressVisible = ref(false),
	progressTitle = ref(''),
	progressId = ref(0),
	progressPage = ref(1),
	progressTotal = ref(0),
	progressBusy = ref(false),
	progressError = ref(''),
	progressRows = ref<any[]>([]);
async function showProgress(row: any) {
	progressId.value = row.id;
	progressTitle.value = row.title;
	progressPage.value = 1;
	progressVisible.value = true;
	await loadProgress();
}
async function loadProgress() {
	progressBusy.value = true;
	progressError.value = '';
	try {
		const r = await featureApi(
			'promotions/stamps/progress?id=' +
				progressId.value +
				'&pageNumber=' +
				progressPage.value +
				'&pageSize=20'
		);
		progressRows.value = r.list;
		progressTotal.value = r.total;
	} catch (e: any) {
		progressError.value = e.message;
		progressRows.value = [];
	} finally {
		progressBusy.value = false;
	}
}
async function retry(row: any) {
	if (saving.value) return;
	saving.value = true;
	try {
		await featureApi('promotions/stamps/retry', { id: row.id });
		await loadProgress();
		ElMessage.success('已处理重试，请查看奖励状态与发放记录');
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
const attemptVisible = ref(false),
	attemptRows = ref<any[]>([]);
async function showAttempts(row: any) {
	try {
		attemptRows.value = await featureApi('promotions/stamps/attempts?id=' + row.id);
		attemptVisible.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
onMounted(() => {
	void load();
	void loadOptions();
});
</script>
<style scoped>
.rule-cards {
	margin: 16px 0;
}
.rule-cards p {
	color: var(--el-text-color-secondary);
	font-size: 13px;
	line-height: 1.6;
}
.goal-card {
	margin: 14px 0;
}
.goal-card .el-form-item {
	margin: 12px 0 0;
}
.el-pagination {
	margin-top: 16px;
}
.reward-tag {
	margin-left: 12px;
}
</style>


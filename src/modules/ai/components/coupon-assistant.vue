<template>
	<section class="coupon-assistant assistant-edge" :class="{ fullscreen, 'is-visible': visible }" aria-label="优惠券 AI 助手">
		<header class="assistant-header">
			<div class="assistant-title">
				<span class="assistant-mark" aria-hidden="true"><cl-svg name="icon-ai" /></span>
				<div><strong>{{ editing ? '核对优惠券草稿' : reviewing ? '核对优惠券操作' : '优惠券 AI 助手' }}</strong><p>{{ editing ? '核对后保存草稿' : '查询、起草与管理优惠券' }}</p></div>
			</div>
			<el-button text size="small" @click="returnOrClose">{{ editing || reviewing ? '返回对话' : '关闭' }}</el-button>
		</header>
		<div v-if="editing && draft" class="assistant-body">
			<el-form label-position="top" :disabled="busy">
				<el-form-item label="所属商家"><el-input :model-value="draft.merchantName" readonly /></el-form-item>
				<el-form-item label="券名称" required><el-input v-model="draft.name" maxlength="100" /></el-form-item>
				<el-form-item label="优惠券类型"><el-select v-model="draft.type"><el-option v-for="(label, key) in types" :key="key" :label="label" :value="key" /></el-select></el-form-item>
				<el-form-item label="使用说明"><el-input v-model="draft.description" type="textarea" maxlength="2000" /></el-form-item>
				<div class="assistant-fields">
					<el-form-item label="售价（元）" required><el-input-number v-model="draft.salePrice" :min="0" :precision="2" /></el-form-item>
					<el-form-item v-if="draft.type === 'CASH'" label="抵扣金额（元）"><el-input-number v-model="draft.deductAmount" :min="0.01" :precision="2" /></el-form-item>
					<el-form-item v-if="draft.type === 'FULL_REDUCTION' || draft.type === 'DISCOUNT'" label="最低消费（元）"><el-input-number v-model="draft.minSpend" :min="0" :precision="2" /></el-form-item>
					<el-form-item v-if="draft.type === 'FULL_REDUCTION'" label="减免金额（元）"><el-input-number v-model="draft.discountAmount" :min="0.01" :precision="2" /></el-form-item>
					<el-form-item v-if="draft.type === 'DISCOUNT'" label="折扣数"><el-input-number v-model="draft.discountRate" :min="0.01" :max="9.99" :precision="2" /></el-form-item>
					<el-form-item label="有效期方式"><el-select v-model="draft.validityType"><el-option label="发券后按天" value="AFTER_ISSUE" /><el-option label="固定日期" value="FIXED" /></el-select></el-form-item>
					<el-form-item v-if="draft.validityType === 'AFTER_ISSUE'" label="发券后有效天数"><el-input-number v-model="draft.validDays" :min="1" :precision="0" /></el-form-item>
					<el-form-item label="每人限领"><el-input-number v-model="draft.perCustomerLimit" :min="1" :precision="0" /></el-form-item>
				</div>
				<template v-if="draft.validityType === 'FIXED'">
					<el-form-item label="固定有效期开始"><el-date-picker v-model="draft.validFrom" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item>
					<el-form-item label="固定有效期结束"><el-date-picker v-model="draft.validTo" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item>
				</template>
				<el-form-item label="领取购买开始"><el-date-picker v-model="draft.saleStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" clearable /></el-form-item>
				<el-form-item label="领取购买结束"><el-date-picker v-model="draft.saleEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" clearable /></el-form-item>
				<el-form-item label="同享其他优惠"><el-switch v-model="draft.allowCombine" :active-value="1" :inactive-value="0" /></el-form-item>
				<el-form-item label="适用门店">
					<el-select v-model="draft.scopeType"><el-option label="全部营业门店" :value="0" /><el-option label="指定门店" :value="1" /></el-select>
					<el-select v-if="draft.scopeType === 1" v-model="draft.storeIds" multiple filterable placeholder="选择门店">
						<el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
					</el-select>
				</el-form-item>
				<el-form-item v-if="['PACKAGE', 'EXPERIENCE', 'EXCHANGE'].includes(draft.type)" label="套餐或服务内容">
					<CouponContentEditor v-model="draft.contentItems" :merchant-id="draft.merchantId" />
				</el-form-item>
				<el-form-item label="优惠券封面">
					<CoverField v-model="draft.cover" v-model:material-id="draft.coverMaterialId" :ai-draft="draft" @busy-change="uploading = $event" />
				</el-form-item>
				<el-form-item label="保存状态"><el-select v-model="draft.status"><el-option label="草稿" :value="0" /><el-option label="启用" :value="1" /></el-select></el-form-item>
				<el-button type="primary" :loading="busy" :disabled="uploading" @click="save">{{ draft.status === 1 ? '保存并启用优惠券' : '保存优惠券草稿' }}</el-button>
			</el-form>
		</div>
		<div v-else-if="reviewing && (reply?.token || reply?.batch?.length)" class="assistant-body">
			<p>{{ reply.text }}</p>
			<el-table v-if="reply.changes.length" :data="reply.changes">
				<el-table-column prop="field" label="字段" />
				<el-table-column prop="before" label="当前值" />
				<el-table-column prop="after" label="修改后" />
			</el-table>
			<div class="assistant-result">
				<ul v-if="reply.batch?.length"><li v-for="item in reply.batch" :key="item.token">{{ item.label }}</li></ul>
				<el-button :type="reply.operation?.includes('删除') ? 'danger' : 'primary'" :loading="busy" @click="confirm">
					确认{{ reply.operation || '执行' }}
				</el-button>
			</div>
			<AssistantComposer v-model="message" :busy="busy" :placeholder="reply?.clarification ? '也可以在这里直接回答' : '输入券名、日期或要做的操作'" @send="send" />
		</div>
		<template v-else>
			<div class="assistant-context">
				<MerchantSelect v-if="isPlatform" v-model="merchant" placeholder="当前授权商家范围" />
				<el-select v-if="merchant || !isPlatform" v-model="store" clearable placeholder="授权门店范围" aria-label="筛选门店">
					<el-option v-for="item in stores" :key="item.id" :value="item.id" :label="item.name" />
				</el-select>
			</div>
			<div ref="transcript" class="assistant-body assistant-transcript" role="log" aria-live="polite">
				<div v-if="!turns.length" class="assistant-starters">
					<p>你想处理哪张优惠券？</p>
					<el-button v-for="sample in samples" :key="sample" :disabled="busy" @click="send(sample)">{{ sample }}<el-icon aria-hidden="true"><ArrowRight /></el-icon></el-button>
				</div>
				<div v-for="(turn, index) in turns" :key="index" class="assistant-turn" :class="turn.role">
					<span>{{ turn.role === 'user' ? '你' : '助手' }}</span><p>{{ typingIndex === index ? visibleText : turn.text }}</p>
				</div>
				<el-button v-if="typingIndex !== null" text size="small" @click="finishTyping(true)">显示完整回复</el-button>
				<div v-else-if="busy" class="assistant-progress" role="status"><el-icon class="is-loading" aria-hidden="true"><Loading /></el-icon>正在处理…</div>
				<div v-if="typingIndex === null && (reply || draft)" class="assistant-result">
					<el-button v-if="reply?.detail && ['编辑封面', '编辑表单'].includes(reply.operation || '')"
						:disabled="busy" type="primary" @click="openEditor(reply.detail, reply.operation === '编辑封面' ? 2 : 0)">{{ reply.operation === '编辑封面' ? '打开封面与素材编辑' : '打开优惠券编辑' }}</el-button>
					<el-button v-for="choice in reply?.choices || []" :key="choice.id" :disabled="busy" @click="choose(choice.id, choice.name)">
						<span>{{ choice.name }} · {{ choice.merchantName }} · {{ choice.type }}{{ choice.status ? ' · ' + choice.status : '' }}<small v-if="choice.createdAt"> · {{ choice.createdAt }}</small></span>
					</el-button>
					<template v-if="reply?.clarification">
						<el-button v-for="option in reply.clarification.options" :key="option.value"
							:disabled="busy" @click="answerChoice(option.value, option.label)">{{ option.label }}</el-button>
						<el-button text size="small" :disabled="busy" @click="send('先别改')">取消这次操作</el-button>
					</template>
					<template v-if="draft">
						<p>{{ draft.name }} · {{ types[draft.type] }} · ¥{{ draft.salePrice }}</p>
						<el-button type="primary" @click="editing = true">核对并回填草稿</el-button>
						<el-button text @click="draft = undefined">放弃草稿</el-button>
					</template>
					<template v-if="reply?.token || reply?.batch?.length">
						<el-button :disabled="busy" @click="send('先别改')">取消操作</el-button>
						<el-button type="primary" @click="reviewing = true">核对{{ reply.operation || '操作' }}</el-button>
					</template>
				</div>
			</div>
			<AssistantComposer v-model="message" :busy="busy" :placeholder="reply?.clarification ? '也可以在这里直接回答' : '输入券名、日期或要做的操作'" @send="send" />
		</template>
		<el-alert v-if="error" class="assistant-error" :title="error" type="error" :closable="false" show-icon />
	</section>
</template>

<script setup lang="ts">
import './assistant-edge.css';
import { nextTick, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import AssistantComposer from './assistant-composer.vue';
import { ArrowRight, Loading } from '@element-plus/icons-vue';
import { isPlatform } from '/@/workspace';
import { merchantRequest } from '/@/merchant-api';
import { featureApi } from '/$/workspace/feature-api';
import MerchantSelect from '/$/merchant/components/merchant-select.vue';
import CouponContentEditor from '/$/coupon/components/coupon-content-editor.vue';
import CoverField from '/$/space/components/cover-field.vue';

type Turn = { role: 'user' | 'assistant'; text: string };
type Clarification = {
	token: string;
	type: 'CANDIDATE' | 'SINGLE' | 'NUMBER' | 'TEXT' | 'REPAIR' | 'CREATE' | 'MERCHANT';
	question?: string;
	options: { value: string; label: string }[];
};
type Reply = {
	text: string;
	choices: { id: number; name: string; merchantName: string; type: string; status?: string; createdAt?: string }[];
	changes: { field: string; before: unknown; after: unknown }[];
	draft?: Record<string, any>;
	token?: string;
	detail?: Record<string, any>;
	searchResult?: boolean;
	batch?: { label: string; token: string }[];
	operation?: string;
	clarification?: Clarification;
	queryToken?: string;
};
const props = defineProps<{ visible?: boolean; fullscreen?: boolean; initialText?: string; bootstrap?: {text:string;reply:Reply}; standaloneEditor?: boolean; merchantId?: number; storeId?: number; couponId?: number; selectedIds?: number[] }>();
const emit = defineEmits<{ close: []; saved: []; edit: [coupon: Record<string, any>, step: number] }>();
const types: Record<string, string> = {
	CASH: '代金券', DISCOUNT: '折扣券', FULL_REDUCTION: '满减券', PACKAGE: '套餐券', EXPERIENCE: '体验券', EXCHANGE: '兑换券'
};
const samples = ['按类型统计优惠券款数', '查询一张券的已支付销量', '新建一张代金券'];
const turns = ref<Turn[]>([]);
const message = ref(props.initialText || '');
watch(() => props.initialText, text => { if (text) message.value = text; });
const merchant = ref(props.merchantId);
const store = ref(props.storeId);
const stores = ref<{ id: number; name: string }[]>([]);
const busy = ref(false);
const uploading = ref(false);
const editing = ref(false);
const reviewing = ref(false);
const error = ref('');
const reply = ref<Reply>();
const queryToken = ref<string>();
const draft = ref<Record<string, any>>();
const activeCoupon = ref<number>();
const transcript = ref<HTMLElement>();
const typingIndex = ref<number | null>(null);
const visibleText = ref('');
let finishTyping: (completed: boolean) => void = () => {};
let disposed = false;
async function showAssistantText(text: string): Promise<boolean> {
	if (disposed) return false;
	finishTyping(false);
	turns.value.push({ role: 'assistant', text });
	const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
	if (preference.matches || !text || props.visible === false) return true;
	const characters = Array.from(text);
	typingIndex.value = turns.value.length - 1;
	visibleText.value = '';
	return new Promise(resolve => {
		let frame = 0;
		let start: number | undefined;
		const duration = Math.min(characters.length * 24, 3500);
		const reduced = () => { if (preference.matches) finishTyping(true); };
		finishTyping = completed => {
			cancelAnimationFrame(frame);
			preference.removeEventListener('change', reduced);
			typingIndex.value = null;
			visibleText.value = '';
			finishTyping = () => {};
			resolve(completed);
		};
		preference.addEventListener('change', reduced);
		const tick = (time: number) => {
			start ??= time;
			const count = Math.min(characters.length, Math.floor((time - start) / duration * characters.length) + 1);
			visibleText.value = characters.slice(0, count).join('');
			void scroll();
			if (count === characters.length) finishTyping(true);
			else frame = requestAnimationFrame(tick);
		};
		frame = requestAnimationFrame(tick);
	});
}
onBeforeUnmount(() => { disposed = true; finishTyping(false); });
onDeactivated(() => finishTyping(false));
watch(() => props.visible, visible => { if (visible === false) finishTyping(true); });
let lastQuestion = '';
let scopeRevision = 0;
watch(() => props.merchantId, value => { merchant.value = value; });
watch(() => props.couponId, () => { activeCoupon.value = undefined; });
watch(() => props.storeId, value => { store.value = value; });
function selectionContext() {
	return JSON.stringify([merchant.value, store.value, props.couponId, [...(props.selectedIds || [])].sort()]);
}
watch(selectionContext, () => {
	finishTyping(false);
	activeCoupon.value = undefined;
	queryToken.value = undefined;
	if (reply.value?.clarification?.type === 'MERCHANT') {
		error.value = '';
		return;
	}
	if (reply.value?.clarification || reply.value?.token || reply.value?.batch?.length || reply.value?.detail) {
		reply.value = undefined;
		reviewing.value = false;
		message.value = '';
		error.value = '券选择或筛选范围已变化，请重新描述操作。';
	}
});
watch(merchant, async (value, previous) => {
	const revision = ++scopeRevision;
	if (previous !== undefined) store.value = undefined;
	stores.value = [];
	try {
		const result = isPlatform
			? value ? await featureApi('merchant/store/list', { merchantId: value }) : []
			: await merchantRequest('workspace/store/options');
		if (revision === scopeRevision) stores.value = result;
	} catch (cause: any) { if (revision === scopeRevision) error.value = cause?.message || '门店加载失败'; }
}, { immediate: true });

async function scroll() {
	await nextTick();
	transcript.value?.scrollTo({ top: transcript.value.scrollHeight });
}
function enter(event: Event) {
	if (event instanceof KeyboardEvent && event.isComposing) return;
	event.preventDefault();
	void send(message.value);
}
function returnOrClose() {
	finishTyping(true);
	if (editing.value || reviewing.value) {
		editing.value = false;
		reviewing.value = false;
		void scroll();
	} else emit('close');
}

async function send(text: string, target = activeCoupon.value ?? props.couponId, fromChoice = false, value?: string) {
	const question = text.trim();
	if (!question || busy.value) return;
	const history = turns.value.slice(-16).map(turn => ({ ...turn, text: turn.text.slice(0, 1500) }));
	const previous = reply.value;
	const clarification = previous?.clarification;
	const context = selectionContext();
	if (!fromChoice) turns.value.push({ role: 'user', text: question });
	lastQuestion = question;
	message.value = '';
	reviewing.value = false;
	reply.value = undefined;
	error.value = '';
	busy.value = true;
	void scroll();
	try {
		const result: Reply = await featureApi('ai/assistant/coupon/chat', {
			text: question, history, merchantId: merchant.value, storeId: store.value, couponId: target,
			selectedIds: props.selectedIds, answer: clarification ? { token: clarification.token, value } : undefined,
			planToken: previous?.token, queryToken: queryToken.value
		});
		if (disposed || context !== selectionContext()) {
			error.value = '券选择或筛选范围已变化，请重新描述操作。';
			return;
		}
		if (!await showAssistantText(result.detail && !['编辑封面', '编辑表单'].includes(result.operation || '') ? detailText(result.detail) : result.text || (result.draft ? '系统提示：草稿已保留，尚未保存。AI 说明暂未生成，可直接核对下方草稿。' : '编辑入口已准备好，AI 说明暂未生成，可点击下方入口继续。')) || disposed || context !== selectionContext()) return;
		reply.value = result;
		if (result.queryToken) queryToken.value = result.queryToken;
		if (result.operation === '修改并提交审核' && result.token) reviewing.value = true;
		if (result.detail) {
			activeCoupon.value = result.detail.id;
		}
		if (result.draft) {
			if (draft.value) turns.value.push({ role: 'assistant', text: '已有未保存的草稿。请先继续核对或放弃草稿。' });
			else {
				draft.value = { validityType: 'AFTER_ISSUE', validDays: 30, perCustomerLimit: 1, allowCombine: 0,
					...Object.fromEntries(Object.entries(result.draft).filter(([, value]) => value != null)) };
			}
		}
	} catch (cause: any) {
		error.value = cause?.message || '对话失败，请重试';
		turns.value.push({ role: 'assistant', text: error.value });
		if (context === selectionContext()) {
			if (clarification && !/失效|重新描述/.test(error.value)) reply.value = previous;
			if (value === undefined) message.value = question;
		}
		error.value = '';
	} finally { busy.value = false; void scroll(); }
}

function answerChoice(value: string, label: string) {
	return send(label, activeCoupon.value ?? props.couponId, false, value);
}

async function choose(id: number, label: string) {
	if (busy.value) return;
	if (reply.value?.clarification) { await answerChoice(String(id), label); return; }
	if (!reply.value?.searchResult) { await send(lastQuestion, id, true); return; }
	busy.value = true;
	error.value = '';
	const context = selectionContext();
	try {
		const result: Reply = await featureApi('ai/assistant/coupon/detail', { merchantId: merchant.value, storeId: store.value, couponId: id });
		if (disposed || context !== selectionContext()) return;
		turns.value.push({ role: 'user', text: `查看「${label}」的详情` });
		if (!await showAssistantText(result.detail ? detailText(result.detail) : result.text)
			|| disposed || context !== selectionContext()) return;
		reply.value = result;
		if (result.detail) activeCoupon.value = result.detail.id;
	} catch (cause: any) { error.value = cause?.message || '详情加载失败'; }
	finally { busy.value = false; void scroll(); }
}

function detailText(coupon: Record<string, any>) {
	const remaining = coupon.issueLimit == null ? '不限量'
		: coupon.issuedCount == null ? '未知' : `${Math.max(0, coupon.issueLimit - coupon.issuedCount)} 张`;
	return [
		`「${coupon.name}」`,
		`商家：${coupon.merchantName || '未提供'}`,
		`类型：${types[coupon.type] || '未知'}`,
		`售价：${coupon.salePrice == null ? '未提供' : Number(coupon.salePrice) === 0 ? '免费领取' : `${coupon.salePrice} 元`}`,
		`优惠规则：${ruleSummary(coupon)}`,
		`剩余库存：${remaining}`,
		`适用门店：${coupon.scopeType === 0 ? '全部营业门店' : coupon.storeNames?.join('、') || '指定门店'}`,
		`模板状态：${['草稿', '启用', '停用'][coupon.status] || '未知'}`,
		`审核状态：${['未提交', '待审核', '审核通过', '已驳回'][coupon.publication?.auditStatus ?? 0] || '未知'}`,
		`上架状态：${['未上架', '已上架', '已下架'][coupon.publication?.publishStatus ?? 0] || '未知'}`,
		`使用说明：${coupon.description || '未填写'}`
	].join('\n');
}

function ruleSummary(coupon: Record<string, any>) {
	if (coupon.type === 'CASH') return `抵扣 ${coupon.deductAmount} 元`;
	if (coupon.type === 'FULL_REDUCTION') return `满 ${coupon.minSpend} 元减 ${coupon.discountAmount} 元`;
	if (coupon.type === 'DISCOUNT') return `${coupon.discountRate} 折`;
	return coupon.contentItems?.map((item: { name: string; quantity: number }) => `${item.name} × ${item.quantity}`).join('、') || '未填写内容';
}

async function confirm() {
	if (!reply.value || busy.value) return;
	const { token, batch } = reply.value;
	const context = selectionContext();
	if (!token && !batch?.length) return;
	busy.value = true;
	error.value = '';
	try {
		if (batch?.length) {
			const results: string[] = [];
			for (const item of batch) {
				try {
					await featureApi('ai/assistant/coupon/confirm', { token: item.token });
					results.push(item.label + '：删除成功');
				} catch (cause: any) { results.push(item.label + '：' + (cause?.message || '执行失败')); }
			}
			reply.value = undefined;
			reviewing.value = false;
			await showAssistantText(results.join('\n'));
		} else {
			const result = await featureApi('ai/assistant/coupon/confirm', { token });
			if (disposed || context !== selectionContext()) return;
			if (result?.recovery) {
				reply.value = result.recovery;
				reviewing.value = false;
				await showAssistantText(result.recovery.text);
				return;
			}
			reply.value = undefined;
			reviewing.value = false;
			await showAssistantText(result?.message || '已执行确认的优惠券操作。');
		}
		reply.value = undefined;
		reviewing.value = false;
		emit('saved');
	} catch (cause: any) { error.value = cause?.message || '操作未完成，请刷新列表核对状态。'; }
	finally { busy.value = false; void scroll(); }
}

async function save() {
	if (!draft.value || busy.value || uploading.value) return;
	const data: Record<string, any> = { ...draft.value, status: draft.value.id ? draft.value.status : draft.value.status === 1 ? 1 : 0 };
	busy.value = true;
	error.value = '';
	try {
		const saved = isPlatform ? await featureApi(data.id ? 'coupon/template/update' : 'coupon/template/add', data) : await merchantRequest('workspace/coupon/save', data);
		const savedId = typeof saved === 'number' ? saved : saved?.id;
		if (Number.isSafeInteger(savedId) && savedId > 0) activeCoupon.value = savedId;
		draft.value = undefined;
		reply.value = undefined;
		editing.value = false;
		await showAssistantText(data.id ? '优惠券修改已保存，发布状态请以最新审核结果为准。' : data.status === 1 ? '优惠券已保存并启用，可以继续提交审核。' : '优惠券草稿已保存，可在启用后提交审核。');
		emit('saved');
	} catch (cause: any) { error.value = cause?.message || '保存失败，草稿已保留'; }
	finally { busy.value = false; void scroll(); }
}
function openEditor(coupon:Record<string,any>, step:number) {
	if(!props.standaloneEditor){emit('edit',coupon,step);return;}
	draft.value=JSON.parse(JSON.stringify(coupon));editing.value=true;reviewing.value=false;
}
watch(()=>props.bootstrap, async value=>{
	if(!value)return;
	lastQuestion=value.text;message.value='';turns.value=[{role:'user',text:value.text}];
	const result=value.reply;reply.value=result;queryToken.value=result.queryToken;
	activeCoupon.value=result.detail?.id;
	if(result.draft)draft.value={validityType:'AFTER_ISSUE',validDays:30,perCustomerLimit:1,allowCombine:0,
		...Object.fromEntries(Object.entries(result.draft).filter(([,v])=>v!=null))};
	await showAssistantText(result.text || '已取得优惠券结果，请核对。');
	void scroll();
},{immediate:true});
</script>

<style scoped>
.coupon-assistant { position: relative; isolation: isolate; box-sizing: border-box; display: flex; flex-direction: column; height: 100%; min-width: 0; min-height: 0; border: 1px solid var(--el-border-color); border-radius: 12px; overflow: hidden; background: var(--el-bg-color); }
.coupon-assistant.fullscreen { position: fixed; inset: 0; z-index: 1999; border-radius: 0; animation: assistant-appear 220ms cubic-bezier(.2, .8, .2, 1); }
.assistant-header { display: flex; justify-content: space-between; align-items: center; gap: 6px; padding: 12px 10px 12px 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.assistant-header .el-button { flex-shrink: 0; padding-inline: 6px; }
.assistant-title { display: flex; align-items: center; gap: 8px; min-width: 0; }
.assistant-title strong { font-size: 14px; color: var(--el-text-color-primary); }
.assistant-mark { display: grid; place-items: center; width: 30px; height: 30px; flex: none; border-radius: 8px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 16px; }
.assistant-header p { margin: 3px 0 0; font-size: 12px; color: var(--el-text-color-regular); }
.assistant-context, .assistant-compose { display: flex; align-items: center; gap: 8px; padding: 9px 12px; }
.assistant-context { flex-wrap: wrap; }
.assistant-context > * { flex: 1 1 102px; min-width: 0; }
.assistant-body { overflow: auto; min-height: 0; flex: 1; padding: 12px; }
.assistant-transcript { background: var(--el-fill-color-extra-light); scrollbar-width: thin; }
.fullscreen .assistant-body, .fullscreen .assistant-compose, .fullscreen .assistant-context { width: min(100%, 840px); margin-inline: auto; }
.assistant-turn { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; margin-bottom: 12px; }
.assistant-turn.user { align-items: flex-end; }
.assistant-turn span { font-size: 12px; color: var(--el-text-color-regular); }
.assistant-turn p { max-width: 100%; margin: 0; padding: 8px 10px; border-radius: 3px 12px 12px; border: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); color: var(--el-text-color-primary); font-size: 13px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.assistant-turn.user p { max-width: 92%; border-color: transparent; border-radius: 12px 3px 12px 12px; background: var(--el-color-primary-light-9); }
.assistant-progress { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--el-text-color-regular); }
.assistant-result { display: grid; gap: 8px; padding-top: 8px; }
.assistant-result .el-button, .assistant-starters .el-button {
	justify-content: flex-start;
	height: auto;
	min-height: 32px;
	margin: 0;
	padding: 6px 9px;
	text-align: left;
}
.assistant-result :deep(.el-button span) { min-width: 0; white-space: normal; overflow-wrap: anywhere; }
.assistant-starters { display: grid; grid-template-columns: minmax(0, 1fr); gap: 6px; }
.assistant-starters p { margin: 5px 0 8px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.assistant-starters :deep(.el-button span) { display: flex; width: 100%; justify-content: space-between; align-items: center; gap: 6px; white-space: normal; line-height: 1.45; }
.assistant-starters .el-icon { flex-shrink: 0; color: var(--el-text-color-regular); }
.assistant-compose { border-top: 1px solid var(--el-border-color-lighter); }
.assistant-input { width: 100%; border: 1px solid var(--el-border-color); border-radius: 10px; transition: border-color 140ms ease; }
.assistant-input:focus-within { border-color: var(--el-color-primary); }
.assistant-input :deep(textarea) { box-shadow: none; background: transparent; font-size: 13px; }
.assistant-input :deep(textarea::placeholder) { color: var(--el-text-color-regular); }
.assistant-send { display: flex; align-items: center; justify-content: space-between; padding: 4px 7px 7px 10px; }
.assistant-send span { font-size: 12px; color: var(--el-text-color-regular); }
.assistant-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 116px), 1fr)); column-gap: 10px; }
.assistant-fields :deep(.el-input-number) { width: 100%; }
.assistant-error { margin: 0; }
@keyframes assistant-appear {
	from { opacity: 0; transform: translateY(6px); }
	to { opacity: 1; transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
	.coupon-assistant.fullscreen { animation: none; }
	.assistant-input { transition: none; }
}
</style>

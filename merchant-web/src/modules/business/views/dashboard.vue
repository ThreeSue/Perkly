<template>
	<WorkbenchLayout :title="heading" :context="accountLine">
		<template #actions>
			<el-button @click="$router.push('/my/assistant')">AI 助手</el-button>
			<el-button class="workbench-refresh" :icon="Refresh" :loading="busy" @click="load">刷新</el-button>
		</template>
		<div v-if="error" class="workbench-inline-alert">
			<el-alert :title="error" type="error" :closable="false" show-icon />
		</div>
		<div class="workbench-grid business-workbench">
			<section class="workbench-panel" aria-labelledby="daily-heading">
				<div class="workbench-section-head">
					<div><h2 id="daily-heading">常用操作</h2><p>{{ isStore ? '先验券，随后可查消费账单或处理顾客会话。' : '管理优惠券与活动，查看顾客购买和门店消费记录。' }}</p></div>
				</div>
				<router-link v-if="primary" :to="primary.path" class="workbench-link business-lead">
					<span class="task-icon"><el-icon :size="22"><component :is="primary.icon" /></el-icon></span>
					<span class="task-copy"><strong>{{ primary.name }}</strong><small>{{ primary.description }}</small></span>
					<span class="lead-action">进入<el-icon aria-hidden="true"><ArrowRight /></el-icon></span>
				</router-link>
				<div v-if="secondary.length" class="business-task-list">
					<router-link v-for="task in secondary" :key="task.path" :to="task.path" class="workbench-link task-row">
						<span class="task-icon"><el-icon :size="18"><component :is="task.icon" /></el-icon></span>
						<span class="task-copy"><strong>{{ task.name }}</strong><small>{{ task.description }}</small></span>
						<el-icon aria-hidden="true"><ArrowRight /></el-icon>
					</router-link>
				</div>
				<ManagementState v-if="!daily.length" title="暂无常用操作" description="可使用的功能会在获得授权后显示。" />
			</section>
			<aside class="workbench-stack">
				<section class="workbench-panel" aria-labelledby="scope-heading">
					<div class="workbench-section-head">
						<div><h2 id="scope-heading">{{ isStore ? '当前门店' : '经营范围' }}</h2><p>按此账号的数据权限统计</p></div>
					</div>
					<div v-if="isStore && me.storeName" class="current-store">
						<el-icon aria-hidden="true"><Location /></el-icon><strong>{{ me.storeName }}</strong>
					</div>
					<el-skeleton v-if="busy && !metrics.length" :rows="2" animated class="scope-loading" aria-label="正在加载经营范围" />
					<dl v-else-if="metrics.length" class="business-metrics">
						<div v-for="metric in metrics" :key="metric.permission" class="business-metric">
							<dt>{{ metric.label }}</dt>
							<dd>
								<router-link v-if="metric.path" :to="metric.path" class="workbench-link metric-link">
									<strong>{{ metric.total ?? '—' }}</strong><el-icon aria-hidden="true"><ArrowRight /></el-icon>
								</router-link>
								<strong v-else>{{ metric.total ?? '—' }}</strong>
							</dd>
						</div>
					</dl>
					<ManagementState v-else-if="!error" title="暂无可展示的统计" />
					<p v-if="metricError" class="metric-error" role="status">部分统计暂不可用。可刷新重试。</p>
					<p v-if="!isStore && loaded && !me.defaultStoreId" class="metric-error">未设置默认门店，核销前请选择本次消费门店。</p>
				</section>
				<section v-if="other.length" class="workbench-panel" aria-labelledby="other-heading">
					<div class="workbench-section-head"><h2 id="other-heading">管理入口</h2></div>
					<div class="business-links">
						<router-link v-for="task in other" :key="task.path" :to="task.path" class="workbench-link">
							<span>{{ task.name }}</span><el-icon aria-hidden="true"><ArrowRight /></el-icon>
						</router-link>
					</div>
				</section>
			</aside>
		</div>
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ArrowRight, ChatDotRound, Collection, Document, Location, Refresh, Tickets, Wallet } from '@element-plus/icons-vue';
import { useBase } from '/$/base';
import WorkbenchLayout from '/$/workspace/components/workbench-layout.vue';
import ManagementState from '/$/workspace/components/management-state.vue';
import { workspaceApi } from '/$/workspace/api';
import { merchantRequest } from '/@/merchant-api';
import { identity } from '/@/workspace';

const { menu } = useBase();
const isStore = identity === 'STORE';
const me = ref<{ storeName?: string; merchantName?: string; name?: string; roleName?: string; defaultStoreId?: number; permissions?: string[] }>({});
const busy = ref(false);
const loaded = ref(false);
const error = ref('');
const metricError = ref(false);
const counts = ref<Record<string, number | undefined>>({});
const heading = computed(() => (isStore ? me.value.storeName : me.value.merchantName) || '工作台');
const accountLine = computed(() => [isStore ? '门店工作台' : '商家工作台', me.value.roleName, me.value.name].filter(Boolean).join(' · '));

const normalize = (path: string) => path.replace(/^\/store\//, '/merchant/');
const links = computed(() => {
	const byRoute = new Map<string, { name: string; path: string }>();
	for (const item of menu.all) {
		const path = item.router || item.path;
		if (item.type !== 1 || !path || item.isShow === false || item.isShow === 0) continue;
		const key = normalize(path);
		if (['/', '/merchant/dashboard'].includes(key)) continue;
		byRoute.set(key, { name: item.name, path });
	}
	return byRoute;
});
const tasks = [
	{ key: 'redemption', name: '验券与核销', icon: Tickets, description: '输入或扫描券码，核对可用状态和消费门店' },
	{ key: 'coupons', name: '优惠券管理', icon: Tickets, description: '编辑券面、提交审核和管理上架' },
	{ key: 'consumption', name: '消费与核销账单', icon: Document, description: '查找门店消费与核销记录' },
	{ key: 'wallet', name: '储值消费账单', icon: Wallet, description: '查看支付账单和储值流水' },
	{ key: 'chats', name: '门店客服', icon: ChatDotRound, description: '打开门店共享的顾客会话' },
	{ key: 'activities', name: '支付优惠活动', icon: Collection, description: '查看活动安排和门店参与范围' }
];
const priority = isStore ? ['redemption', 'consumption', 'wallet', 'chats'] : ['coupons', 'consumption', 'activities', 'redemption'];
const daily = computed(() => priority.flatMap(key => {
	const task = tasks.find(entry => entry.key === key);
	const link = links.value.get('/merchant/' + key);
	return task && link ? [{ ...task, ...link }] : [];
}));
const primary = computed(() => daily.value[0]);
const secondary = computed(() => daily.value.slice(1));
const other = computed(() => [...links.value.values()].filter(link => !daily.value.some(task => task.path === link.path)));
const specs = [
	{ permission: 'store:view', resource: 'store', label: '可查看的门店', route: 'stores' },
	{ permission: 'coupon:view', resource: 'coupon', label: '可查看的优惠券', route: 'coupons' },
	{ permission: 'purchase:view', resource: 'order', label: '可查看的购券订单', route: 'orders' }
];
const allowed = computed(() => specs.filter(spec => me.value.permissions?.includes(spec.permission)));
const metrics = computed(() => allowed.value.map(spec => ({
	...spec,
	path: links.value.get('/merchant/' + spec.route)?.path,
	total: counts.value[spec.resource]
})));
async function load() {
	if (busy.value) return;
	busy.value = true;
	error.value = '';
	metricError.value = false;
	try {
		me.value = await workspaceApi('me');
		loaded.value = true;
		const visible = allowed.value;
		const results = await Promise.allSettled(visible.map(spec =>
			merchantRequest(`workspace/${spec.resource}/page`, { page: 1, size: 1 })
		));
		counts.value = Object.fromEntries(visible.map((spec, index) => {
			const result = results[index];
			return [spec.resource, result.status === 'fulfilled' ? result.value.totalRow : undefined];
		}));
		metricError.value = results.some(result => result.status === 'rejected');
	} catch (e) {
		error.value = e instanceof Error ? e.message : '工作台资料加载失败，请刷新重试。';
	} finally {
		busy.value = false;
	}
}
onMounted(load);
</script>

<style scoped>
.business-lead,
.task-row {
	display: grid;
	grid-template-columns: auto minmax(0, 1fr) auto;
	align-items: center;
	gap: 14px;
	text-decoration: none;
	color: var(--workspace-ink);
}
.business-lead {
	margin: 2px 16px 10px;
	padding: 18px 16px;
	border-radius: 7px;
	background: var(--workspace-selected);
}
.business-lead:hover { background: var(--el-color-primary-light-8); }
.task-icon {
	display: grid;
	place-items: center;
	width: 38px;
	height: 38px;
	border-radius: 7px;
	color: var(--workspace-accent-text);
	background: var(--workspace-neutral);
}
.business-lead .task-icon {
	width: 46px;
	height: 46px;
	background: var(--workspace-accent);
	color: white;
}
.task-copy { display: grid; gap: 5px; min-width: 0; }
.task-copy strong { font-size: 15px; overflow-wrap: anywhere; }
.business-lead .task-copy strong { font-size: 20px; letter-spacing: -.015em; }
.task-copy small { color: var(--el-text-color-regular); font-size: 12px; line-height: 1.45; }
.lead-action {
	display: flex;
	align-items: center;
	gap: 5px;
	font-size: 13px;
	font-weight: 650;
	color: var(--workspace-accent-text);
}
.business-task-list { padding-inline: 16px; }
.task-row { min-height: 67px; padding: 8px 4px; box-sizing: border-box; }
.task-row + .task-row { border-block-start: 1px solid var(--el-border-color-lighter); }
.task-row:hover { background: var(--el-fill-color-light); }
.task-row > .el-icon { color: var(--el-text-color-secondary); }
.business-metrics { margin: 0; padding: 0 16px; }
.business-metric {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	min-height: 55px;
	border-block-start: 1px solid var(--el-border-color-lighter);
}
.business-metric dt { color: var(--el-text-color-regular); font-size: 13px; }
.business-metric dd { margin: 0; }
.business-metric strong { font-size: 24px; }
.metric-link { display: flex; align-items: center; gap: 8px; color: var(--workspace-ink); text-decoration: none; }
.metric-link:hover { color: var(--workspace-accent-text); }
.current-store {
	display: flex;
	align-items: center;
	gap: 8px;
	margin: 0 16px 10px;
	padding: 12px;
	border-radius: 6px;
	background: var(--workspace-selected);
	overflow-wrap: anywhere;
}
.current-store .el-icon { flex: none; color: var(--workspace-accent-text); }
.scope-loading { box-sizing: border-box; padding: 4px 16px 16px; }
.metric-error { margin: 8px 16px 14px; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.5; }
.business-links { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 0 16px 12px; gap: 4px 12px; }
.business-links a {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 6px;
	min-height: 34px;
	padding: 3px 6px;
	border-block-start: 1px solid var(--el-border-color-lighter);
	text-decoration: none;
	overflow-wrap: anywhere;
	color: var(--el-text-color-primary);
}
.business-links a:hover { background: var(--el-fill-color-light); color: var(--workspace-accent-text); }
@media (max-width: 520px) {
	.business-lead { margin-inline: 10px; padding: 12px 10px; gap: 9px; }
	.business-lead .task-copy strong { font-size: 17px; }
	.business-links { grid-template-columns: minmax(0, 1fr); }
}
</style>

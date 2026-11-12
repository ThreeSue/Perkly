<template>
	<div v-loading="busy">
		<el-alert v-if="error" :title="error" type="error" :closable="false" />
		<template v-if="detail">
			<h3>{{ detail.title }}</h3>
			<p>{{ detail.description }}</p>
            <p>{{ detail.kind === 'STAMP' ? '跨商家消费集章 · 同一商家只计一章，达标发奖励券' : detail.discountRate + ' 折' }} · {{ detail.startAt }} 至 {{ detail.endAt }}</p>
            <p>{{ detail.status === 'CONFIRMING' ? '等待全部商家确认，尚未开放集章' : detail.status === 'OFFLINE' ? '已结束' : detail.enabled ? '活动已启用，按时间生效' : '活动已暂停' }}</p>
			<p>适用门店 {{ detail.stores.map((s: any) => s.name).join('、') }}</p>
		</template>
        <template v-if="invitation">
            <p>完成 {{ invitation.targetCount }} 个商家后奖励：{{ invitation.reward?.name }}（每人本活动一次）</p>
            <p>{{ invitation.reward?.description }}</p>
            <el-alert :title="invitation.status === 'CONFIRMING' ? '请核对活动时间与门店；全部商家同意后自动上线。运营撤回调整后需要重新确认。' : '活动参与范围已固定，如无法继续参与，请联系运营暂停活动。'" type="info" :closable="false" />
            <p>本商家确认：{{ ({PENDING:'待确认',ACCEPTED:'已同意',REJECTED:'已拒绝'} as Record<string,string>)[invitation.answer?.status] || '历史活动' }}</p>
            <template v-if="invitation.status === 'CONFIRMING'">
                <p>选择参与门店（至少一家）</p>
                <el-checkbox-group v-model="selectedStores" :disabled="!invitation.canConfirm || busy">
                    <el-checkbox v-for="s in invitation.stores" :key="s.id" :value="s.id">{{ s.name }}</el-checkbox>
                </el-checkbox-group>
                <el-input v-model="answerReason" type="textarea" maxlength="500" placeholder="拒绝时请说明原因，方便运营调整" />
                <p v-if="!invitation.canConfirm">请由具有全部受邀门店权限的商家负责人确认。</p>
                <el-button type="primary" :disabled="!invitation.canConfirm || !selectedStores.length" :loading="busy" @click="answer(true)">同意参与</el-button>
                <el-button :disabled="!invitation.canConfirm || !answerReason.trim()" :loading="busy" @click="answer(false)">拒绝邀请</el-button>
            </template>
        </template>
		<template v-if="participation && detail?.kind !== 'STAMP'">
            <el-alert title="活动范围内默认参加。调整只影响后续消费，已完成订单优惠和已赚取的章保留。" type="info" :closable="false" />
			<el-checkbox v-model="exited" :disabled="!participation.canExit">整商家退出此活动</el-checkbox>
			<p>排除以下门店</p>
			<el-checkbox-group v-model="excluded">
				<el-checkbox v-for="s in participation.stores" :key="s.id" :value="s.id" :disabled="exited">{{ s.name }}</el-checkbox>
			</el-checkbox-group>
			<el-button type="primary" :loading="busy" @click="save">保存参与设置</el-button>
		</template>
		<el-button @click="load">刷新</el-button>
	</div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useBase } from '/$/base';
import { identity } from '/@/workspace';
import { featureApi } from '../feature-api';
const props = defineProps<{ id: number }>();
const { menu } = useBase();
const adjustable = computed(() => identity === 'MERCHANT' && menu.perms.includes('activity:participate'));
const detail = ref<any>(), participation = ref<any>();
const invitation = ref<any>(), selectedStores = ref<number[]>([]), answerReason = ref('');
async function answer(accepted: boolean) {
    busy.value = true; error.value = '';
    try {
        await featureApi('wallet/activities/stamp-confirm', { id: props.id, version: invitation.value.version, accepted, storeIds: selectedStores.value, reason: answerReason.value });
        ElMessage.success(accepted ? '已确认参与，请查看最新活动状态' : '已拒绝邀请');
        await load();
    } catch (e: any) { error.value = e.message; }
    finally { busy.value = false; }
}
const busy = ref(false), error = ref(''), exited = ref(false), excluded = ref<number[]>([]);
async function load() {
	busy.value = true;
	error.value = '';
	try {
		detail.value = await featureApi('wallet/activities/detail?id=' + props.id);
		participation.value = undefined;
        invitation.value = undefined;
        if (adjustable.value && detail.value.kind === 'STAMP') {
            invitation.value = await featureApi('wallet/activities/stamp-invitation?id=' + props.id);
            selectedStores.value = [...(invitation.value.answer?.storeIds || invitation.value.stores.map((s:any)=>s.id))];
            answerReason.value = invitation.value.answer?.reason || '';
        }
		if (adjustable.value && detail.value.kind !== 'STAMP' && detail.value.status === 'PUBLISHED') {
			participation.value = await featureApi('wallet/activities/participation?id=' + props.id);
			exited.value = participation.value.exited;
			excluded.value = [...participation.value.excludedStoreIds];
		}
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
async function save() {
	busy.value = true;
	try {
		await featureApi('wallet/activities/participation', { id: props.id, version: participation.value.version,
			exited: participation.value.canExit ? exited.value : undefined, excludedStoreIds: excluded.value });
		ElMessage.success('已更新参与设置');
		await load();
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
watch(() => props.id, load, { immediate: true });
</script>

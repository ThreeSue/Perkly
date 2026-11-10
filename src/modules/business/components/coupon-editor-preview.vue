<template>
	<aside class="preview">
		<h3>用户端预览</h3>
		<el-radio-group v-model="scene" size="small" aria-label="预览场景">
			<el-radio-button value="home">首页双列</el-radio-button>
			<el-radio-button value="list">优惠券列表</el-radio-button>
			<el-radio-button value="owned">我的券</el-radio-button>
		</el-radio-group>
		<div class="canvas">
			<article :class="scene">
				<div class="cover">
					<img
						v-if="draft.cover && failedCover !== draft.cover"
						:key="draft.cover"
						:src="draft.cover"
						alt=""
						@error="failedCover = draft.cover"
					/>
					<span v-else>{{ typeName || '优惠券' }}</span>
				</div>
				<div class="copy">
					<strong>{{ draft.name || '待填写券名' }}</strong>
					<small>{{ merchant }}<template v-if="scene === 'list' && typeName"> · {{ typeName }}</template></small>
					<b v-if="scene !== 'owned'">{{ price }}</b>
				</div>
				<footer v-if="scene === 'owned'"><span>{{ validity }}</span><b>查看券码 ›</b></footer>
			</article>
		</div>
		<p>示例预览 · 随当前表单更新</p>
	</aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useBase } from '/$/base';

const props = defineProps<{ draft: Record<string, any>; typeName?: string }>();
const { user } = useBase();
const scene = ref('list');
const failedCover = ref('');
watch(() => props.draft.cover, () => { failedCover.value = ''; });
const merchant = computed(() => props.draft.merchantName || (user.info as any)?.merchantName || '当前商家');
const price = computed(() => {
	const value = props.draft.salePrice;
	if (value == null) return '待填写售价';
	return Number(value) === 0 ? '免费领取' : `¥${Number(value).toFixed(2)}`;
});
const validity = computed(() => props.draft.validityType === 'FIXED'
	? props.draft.validTo ? `有效期至 ${String(props.draft.validTo).slice(0, 10)}` : '待设置有效期'
	: props.draft.validDays ? `领券后 ${props.draft.validDays} 天有效` : '待设置有效期');
</script>

<style scoped>
.preview { position: absolute; inset: 0 auto auto 12px; width: 286px; box-sizing: border-box; padding: 16px; border-radius: 14px; background: var(--el-fill-color-light); }
h3 { margin: 0 0 14px; font-size: 17px; }
.canvas { margin-top: 14px; background: #f3f0e6; color: #29493c; border-radius: 13px; padding: 14px; min-height: 210px; display: grid; align-items: center; }
article { overflow: hidden; border-radius: 14px; background: #fff; box-shadow: 0 8px 18px #34483b16; }
.list { display: grid; grid-template-columns: 28% 1fr; }
.cover { background: #dfe7df; display: grid; place-items: center; overflow: hidden; min-width: 0; }
.cover img { width: 100%; height: 100%; object-fit: cover; }
.list .cover { height: 100%; min-height: 92px; }
.home { width: 56%; background: transparent; box-shadow: none; }
.home .cover { aspect-ratio: 1.48; border-radius: 10px; }
.copy { padding: 9px; min-width: 0; line-height: 1.5; }
.copy strong, .copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.copy small { opacity: .8; }
.copy b { display: block; margin-top: 4px; }
.owned { display: grid; grid-template-columns: 1fr 36%; align-items: center; padding: 10px; background: #fffaf0; border: 1px solid #d7cab0; box-shadow: inset 0 0 0 3px #f0e9da; }
.owned .cover { grid-column: 2; grid-row: 1; aspect-ratio: 1; border-radius: 42% 48% 44% 50%; }
.owned .copy { grid-column: 1; grid-row: 1; }
footer { grid-column: 1 / -1; margin-top: 8px; padding-top: 6px; border-top: 1px dashed #baad94; display: flex; justify-content: space-between; font-size: 11px; }
footer { flex-wrap: wrap; gap: 4px 8px; }
p { color: var(--el-text-color-secondary); font-size: 12px; margin: 10px 0 0; }
@media (max-width: 720px) {
	.preview { position: static; width: 100%; margin-bottom: 8px; padding: 8px; }
	h3 { margin-bottom: 5px; font-size: 14px; }
	.canvas { margin-top: 6px; min-height: 0; padding: 6px; }
	.home { width: 110px; }
	p { display: none; }
}
</style>

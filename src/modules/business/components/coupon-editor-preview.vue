<template>
	<aside class="preview">
		<h3>用户端预览</h3>
		<el-radio-group v-model="scene" size="small" aria-label="预览场景">
			<el-radio-button value="home">首页</el-radio-button>
			<el-radio-button value="list">优惠券列表</el-radio-button>
			<el-radio-button value="owned">我的券</el-radio-button>
		</el-radio-group>
		<div class="canvas" ref="canvas">
			<div class="phone" :style="{ zoom: scale }">
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
					<img v-if="scene === 'owned'" class="paper" :src="paper" alt="" />
					<div class="copy">
						<strong>{{ draft.name || '待填写券名' }}</strong>
						<small
							>{{ merchant
							}}<template v-if="scene === 'list' && typeName">
								· {{ typeName }}</template
							></small
						>
						<div v-if="scene === 'home'" class="product-ticket-offer">
							<div v-if="offer.amount" class="product-ticket-offer-mark"></div>
							<span class="product-ticket-offer-label">{{ offer.label }}</span>
							<div v-if="offer.amount" class="product-ticket-value">
								<span class="product-ticket-offer-amount">{{ offer.amount }}</span
								><span class="product-ticket-offer-unit">{{ offer.unit }}</span>
							</div>
						</div>
						<div v-if="scene === 'list'" class="price-row">
							<b>{{ price }}</b
							><span v-if="scene === 'list'">可领购</span>
						</div>
					</div>
					<template v-if="scene === 'home'">
						<div class="product-ticket-stub">
							<div class="product-ticket-rule"></div>
							<span class="product-ticket-action"
								><span class="product-ticket-action-label">{{
									Number(draft.salePrice || 0) === 0 ? '立即领取' : '立即购买'
								}}</span></span
							>
						</div>
						<div class="product-ticket-cut product-ticket-cut--top"></div>
						<div class="product-ticket-cut product-ticket-cut--bottom"></div>
					</template>
					<footer v-if="scene === 'owned'">
						<span>{{ validity }}</span
						><b>查看券码</b>
					</footer>
				</article>
			</div>
		</div>
		<p>示例预览 · 随当前表单更新</p>
	</aside>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue';
import paper from '../assets/client-preview/universal-paper-coupon.webp';
const canvas = ref<HTMLElement>();
const canvasWidth = ref(254);
const scale = computed(() => canvasWidth.value / 366);
let observer: ResizeObserver;
onMounted(() => {
	observer = new ResizeObserver(([entry]) => {
		canvasWidth.value = entry.contentRect.width;
	});
	if (canvas.value) observer.observe(canvas.value);
});
onBeforeUnmount(() => observer?.disconnect());
import { useBase } from '/$/base';

const props = defineProps<{ draft: Record<string, any>; typeName?: string }>();
const { user } = useBase();
const scene = ref('list');
const failedCover = ref('');
watch(
	() => props.draft.cover,
	() => {
		failedCover.value = '';
	}
);
const merchant = computed(
	() => props.draft.merchantName || (user.info as any)?.merchantName || '当前商家'
);
const price = computed(() => {
	const value = props.draft.salePrice;
	if (value == null) return '待填写售价';
	return Number(value) === 0 ? '免费领取' : `¥${Number(value).toFixed(2)}`;
});
// Match frontent-new client-product-card ticket offers; ignore stale fields from other types.
const offer = computed(() => {
	const d = props.draft;
	const amount =
		d.type === 'FULL_REDUCTION' ? d.discountAmount : d.type === 'CASH' ? d.deductAmount : null;
	const money = (v: number) => Number(v).toFixed(2).replace(/\.00$/, '');
	if (amount > 0)
		return {
			label: d.minSpend > 0 ? `满 ${money(d.minSpend)} 减` : '立减',
			amount: money(amount),
			unit: '元'
		};
	if (d.type === 'DISCOUNT' && d.discountRate > 0)
		return { label: '享', amount: String(d.discountRate), unit: '折' };
	if (!Number(d.salePrice)) return { label: '免费领券', amount: '', unit: '' };
	return {
		label: d.type === 'PACKAGE' ? '套餐特惠' : '券价',
		amount: money(d.salePrice),
		unit: '元'
	};
});
const validity = computed(() =>
	props.draft.validityType === 'FIXED'
		? props.draft.validTo
			? `有效期至 ${String(props.draft.validTo).slice(0, 10).replace(/-/g, '.')}`
			: '待设置有效期'
		: props.draft.validDays
			? `领券后 ${props.draft.validDays} 天有效`
			: '待设置有效期'
);
</script>

<style scoped>
.preview {
	position: absolute;
	inset: 0 auto auto 12px;
	width: 286px;
	box-sizing: border-box;
	padding: 16px;
	border-radius: 14px;
	background: var(--el-fill-color-light);
}
h3 {
	margin: 0 0 14px;
	font-size: 17px;
}
/* Sizes and paper artwork mirror client-product-card, client-coupon-card and client-wallet-ticket. */
@font-face {
	font-family: 'Preview Serif';
	src: url('../assets/client-preview/noto-serif-sc-700.woff2');
	font-weight: 700;
}
@font-face {
	font-family: 'Preview Sans';
	src: url('../assets/client-preview/noto-sans-sc-400.woff2');
	font-weight: 400;
}
.canvas {
	margin-top: 14px;
	background: #f4f2e9;
	border-radius: 13px;
	overflow: hidden;
}
.phone {
	width: 366px;
	min-height: 300px;
	padding: 24px 12px;
	box-sizing: border-box;
	display: flex;
	align-items: center;
	color: #272e28;
	font-family: 'Preview Sans', sans-serif;
}
article {
	width: 100%;
	box-sizing: border-box;
}
.cover {
	background: #e9edde;
	display: grid;
	place-items: center;
	overflow: hidden;
}
.cover img {
	width: 100%;
	height: 100%;
	object-fit: cover;
}
.copy strong {
	font-family: 'Preview Serif', serif;
	font-weight: 700;
	overflow-wrap: anywhere;
	display: block;
}
.copy small {
	display: block;
	color: #66715f;
	font-size: 12px;
	line-height: 1.6;
}
.price-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
}
.price-row b {
	color: #304f40;
	font-size: 21px;
	font-weight: 700;
}
.price-row span {
	font-size: 12px;
	color: #66715f;
}
.home {
	display: flex;
	flex-direction: row;
	align-items: center;
	box-sizing: border-box;
	min-height: 99px;
	position: relative;
	overflow: visible;
	border: 1px solid #d8d2c3;
	border-radius: 12px;
	background: #fffdf8;
}
.home .cover {
	width: 24.5%;
	height: 83px;
	margin: 7px 15px 7px 7px;
	flex-shrink: 0;
	border-radius: 9px;
}
.home .cover img {
	height: 100%;
}
.home .copy {
	display: flex;
	flex: 1;
	min-width: 0;
	flex-direction: column;
	justify-content: center;
	margin-right: 17.5px;
}
.home .copy strong {
	margin: 0;
	font-family: 'Preview Sans', sans-serif;
	font-size: 16px;
	line-height: 22px;
	color: #282a25;
}
.home .copy small {
	color: #686761;
	font-size: 13px;
	line-height: 19px;
}
.home .copy strong,
.home .copy small {
	display: block;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}
.product-ticket-offer {
	position: relative;
	display: flex;
	flex-direction: row;
	align-items: baseline;
	align-self: flex-start;
	max-width: 100%;
	margin-top: 0;
}
.product-ticket-value {
	position: relative;
	display: flex;
	flex-direction: row;
	align-items: baseline;
	margin-left: 4px;
}
.product-ticket-offer-mark {
	position: absolute;
	left: 0;
	right: -4px;
	bottom: 0;
	height: 9px;
	border-radius: 6px 5px 5px 7px;
	background: #e0e7c9;
	transform: rotate(-3deg);
}
.product-ticket-offer-label,
.product-ticket-offer-amount {
	position: relative;
	color: #324f3a;
	font-weight: 700;
	white-space: nowrap;
}
.product-ticket-offer-label {
	font-size: 14px;
	line-height: 18px;
}
.product-ticket-offer-amount {
	margin-right: 4px;
	font-size: 24px;
	line-height: 29px;
}
.product-ticket-offer-unit {
	position: relative;
	color: #324f3a;
	font-size: 14px;
	line-height: 18px;
	font-weight: 700;
	white-space: nowrap;
}
.product-ticket-stub {
	position: relative;
	display: flex;
	flex-direction: row;
	flex-shrink: 0;
	align-self: stretch;
	align-items: center;
	justify-content: center;
	width: 26.5%;
	min-height: 97px;
}
.product-ticket-rule {
	position: absolute;
	left: 0;
	top: 10px;
	bottom: 10px;
	width: 1px;
	background-image: repeating-linear-gradient(
		to bottom,
		#e3e0d9 0px,
		#e3e0d9 4px,
		transparent 4px,
		transparent 8px
	);
}
.product-ticket-action {
	display: flex;
	flex-direction: row;
	align-items: center;
	justify-content: center;
	width: 74px;
	max-width: 78%;
	height: 26px;
	margin-left: 4px;
	margin-bottom: 2px;
	border-radius: 13px;
	background: #324f3a;
}
.product-ticket-action-label {
	color: #faf7f0;
	font-size: 12px;
	line-height: 16px;
	white-space: nowrap;
}
.product-ticket-cut {
	position: absolute;
	right: 26.5%;
	transform: translateX(7px);
	width: 14px;
	height: 7px;
	box-sizing: border-box;
	border: 1px solid #d8d2c3;
	background: #f4f2e9;
}
.product-ticket-cut--top {
	top: -1px;
	border-top: 0;
	border-radius: 0 0 7px 7px;
}
.product-ticket-cut--bottom {
	bottom: -1px;
	border-bottom: 0;
	border-radius: 7px 7px 0 0;
}

.list {
	display: flex;
	min-height: 108px;
	border-radius: 20px;
	background: white;
	overflow: hidden;
}
.list .cover {
	width: 88px;
	min-height: 108px;
	flex-shrink: 0;
	border-right: 1px dashed #d9ddce;
	background: #e6ebf1;
	color: #304f40;
	font-size: 13px;
}
.list .cover img {
	height: 108px;
}
.list .copy {
	flex: 1;
	min-width: 0;
	padding: 12px;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
}
.list .copy strong {
	font-size: 16px;
	line-height: 1.5;
	margin-bottom: 6px;
}
.owned {
	position: relative;
	height: 220px;
	color: #2e4b3e;
}
.owned .cover {
	position: absolute;
	left: 51.46%;
	top: 1.82%;
	width: 47.37%;
	height: 75.45%;
	border-radius: 50%;
}
.paper {
	max-width: none;
	object-fit: contain;
	position: absolute;
	z-index: 1;
	left: -3.1%;
	top: -9.45%;
	width: 106.23%;
	height: 115.32%;
	pointer-events: none;
}
.owned .copy {
	position: absolute;
	z-index: 2;
	left: 5.85%;
	top: 45.9%;
	right: 52%;
	display: flex;
	flex-direction: column;
	gap: 6px;
}
.owned .copy strong {
	font-size: 16px;
	line-height: 24px;
	white-space: nowrap;
	text-overflow: ellipsis;
	overflow: hidden;
}
.owned .copy small {
	line-height: 18px;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}
footer {
	position: absolute;
	z-index: 2;
	top: 82%;
	left: 13.7%;
	right: 12.5%;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
	min-height: 28px;
}
footer span {
	font-size: 12px;
	line-height: 20px;
	white-space: nowrap;
}
footer b {
	font-size: 13.5px;
	line-height: 20px;
	white-space: nowrap;
}
p {
	color: var(--el-text-color-secondary);
	font-size: 12px;
	margin: 10px 0 0;
}
@media (max-width: 720px) {
	.preview {
		position: static;
		width: 100%;
		padding: 8px;
	}
	.canvas {
		max-width: 286px;
		margin-inline: auto;
	}
}
</style>

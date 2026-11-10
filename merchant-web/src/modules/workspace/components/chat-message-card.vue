<template>
	<article class="message-card">
		<ChatCouponCard
			:title="card.title"
			:cover-url="card.image"
			:held="card.type === 'CUSTOMER_COUPON'"
			:unavailable="!card.available"
			:unavailable-reason="card.note"
		/>
		<p v-if="!card.applicable">不适用当前门店</p>
		<details>
			<summary>发送时的信息</summary>
			<p>{{ card.snapshot?.status }}</p>
			<dl>
				<template v-for="(fact, i) in card.snapshot?.facts" :key="i"
					><dt>{{ fact.label }}</dt>
					<dd>{{ fact.value }}</dd></template
				>
			</dl>
		</details>
		<section v-if="card.available && card.current">
			<strong>当前状态：{{ card.current.status || '未提供' }}</strong>
			<dl>
				<template v-for="(fact, i) in card.current.facts" :key="i"
					><dt>{{ fact.label }}</dt>
					<dd>{{ fact.value }}</dd></template
				>
			</dl>
		</section>
	</article>
</template>
<script setup lang="ts">
import ChatCouponCard from './chat-coupon-card.vue';
export interface MessageCard {
	type: string;
	id: number;
	title: string;
	image?: string;
	available: boolean;
	applicable: boolean;
	note?: string;
	snapshot?: { status?: string; facts: { label: string; value: string }[] };
	current?: { status?: string; facts: { label: string; value: string }[] };
}
defineProps<{ card: MessageCard }>();
</script>
<style scoped>
.message-card {
	max-width: 320px;
	margin-block: 8px;
	font-size: 12px;
	line-height: 1.6;
}
.message-card p {
	margin: 8px 0;
}
.message-card summary {
	cursor: pointer;
	padding-block: 8px;
}
.message-card dl {
	display: grid;
	grid-template-columns: auto 1fr;
	gap: 4px 12px;
	margin: 8px 0;
}
.message-card dd {
	margin: 0;
	overflow-wrap: anywhere;
}
.message-card dt {
	color: var(--el-text-color-regular);
}
</style>

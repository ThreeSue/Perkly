<template>
	<MerchantAccounts v-if="user.info?.username === 'admin'" />
	<div v-else class="access-notice">
		<el-empty description="商家与门店账号仅向平台超级管理员开放" />
	</div>
</template>
<script setup lang="ts">
import { useBase } from '/$/base';
import MerchantAccounts from '/$/workspace/views/accounts.vue';
defineOptions({ name: 'sys-user' });
const { user } = useBase();
</script>
<style scoped>
.access-notice {
	height: 100%;
	background: var(--el-bg-color);
	display: grid;
	place-items: center;
}
</style>

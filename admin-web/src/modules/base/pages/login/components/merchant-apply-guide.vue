<template>
	<el-dialog v-model="visible" title="申请商家入驻" width="min(520px, 92vw)">
		<el-alert
			title="请在券管家用户端或商家核销端小程序申请"
			type="info"
			:closable="false"
			show-icon
		/>
		<ol class="apply-steps">
			<li>打开任一小程序，进入“申请商家入驻”，验证联系人手机号。</li>
			<li>填写商家名称、分类和联系人等资料，提交平台审核。</li>
			<li>审核通过后，在小程序申请详情中领取营业码。</li>
			<li>回到本页，点击“已有营业码，开通账号”。</li>
		</ol>
		<p>
			申请时不设置账号密码或创建门店。账号开通后，再到商家后台创建门店、填写地址和地图位置。
		</p>
		<p class="muted">小程序名称和正式入口请向平台工作人员确认。</p>
		<template #footer
			><el-button type="primary" @click="visible = false">我知道了</el-button></template
		>
	</el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue';
const visible = ref(false);
defineExpose({
	open: () => {
		visible.value = true;
	}
});
</script>
<style scoped>
.apply-steps {
	padding-left: 24px;
	line-height: 2;
	margin: 20px 0;
}
p {
	line-height: 1.8;
}
.muted {
	color: var(--el-text-color-secondary);
	font-size: 13px;
}
</style>

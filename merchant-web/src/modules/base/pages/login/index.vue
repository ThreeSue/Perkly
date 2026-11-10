<template>
	<div class="page-login">
		<div class="box">
			<div class="logo">
				<div class="icon">
					<img src="/logo.png" alt="Logo" />
				</div>

				<span>券管家管理后台</span>
			</div>

			<p class="desc">券管家管理后台 · 分角色登录</p>
			<el-radio-group
				:model-value="identity"
				@change="value => switchIdentity(value as WorkspaceIdentity)"
				class="identity-options"
				aria-label="登录身份"
			>
				<el-radio-button value="PLATFORM">平台运营</el-radio-button
				><el-radio-button value="MERCHANT">商家管理</el-radio-button
				><el-radio-button value="STORE">门店管理</el-radio-button>
			</el-radio-group>

			<div class="form">
				<el-form label-position="top" :disabled="saving" @submit.prevent="toLogin">
					<el-form-item :label="$t('用户名')">
						<el-input
							v-model="form.username"
							:placeholder="$t('请输入用户名')"
							maxlength="64"
							autocomplete="username"
						/>
					</el-form-item>

					<el-form-item :label="$t('密码')">
						<el-input
							v-model="form.password"
							type="password"
							:placeholder="$t('请输入密码')"
							maxlength="64"
							show-password
							autocomplete="current-password"
						/>
					</el-form-item>

					<el-form-item :label="$t('验证码')">
						<el-input
							v-model="form.verifyCode"
							:placeholder="$t('验证码')"
							maxlength="4"
						>
							<template #suffix>
								<pic-captcha
									:ref="setRefs('picCaptcha')"
									v-model="form.captchaId"
									@change="
										() => {
											form.verifyCode = '';
										}
									"
								/>
							</template>
						</el-input>
					</el-form-item>

					<div class="op">
						<el-button type="primary" native-type="submit" :loading="saving">
							{{ $t('登录') }}
						</el-button>
					</div>
					<div v-if="identity === 'MERCHANT'" class="merchant-entry">
						<el-button link type="primary" @click="activation?.open()"
							>已有营业码，开通账号</el-button
						><el-button link @click="guide?.open()">申请入驻指引</el-button>
					</div>
				</el-form>
			</div>
		</div>

		<MerchantActivation ref="activation" /><MerchantApplyGuide ref="guide" />
		<div class="bg">
			<cl-svg name="bg"></cl-svg>
		</div>

		<a href="https://cool-js.com" class="copyright"> Copyright © COOL </a>
	</div>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'login'
});

import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useCool } from '/@/cool';
import { useBase } from '/$/base';
import { storage } from '/@/cool/utils';
import { useI18n } from 'vue-i18n';
import { identity, switchIdentity, type WorkspaceIdentity, isPlatform } from '/@/workspace';
import { merchantRequest } from '/@/merchant-api';
import MerchantActivation from './components/merchant-activation.vue';
import MerchantApplyGuide from './components/merchant-apply-guide.vue';
const activation = ref(),
	guide = ref();
import PicCaptcha from './components/pic-captcha.vue';

const { refs, setRefs, router, service } = useCool();
const { user, app } = useBase();
const { t } = useI18n();

// 状态
const saving = ref(false);

// 表单数据
const form = reactive({
	username: storage.get('username') || '',
	password: '',
	captchaId: '',
	verifyCode: ''
});

// 演示模式
if (import.meta.env.MODE == 'demo') {
	form.username = 'admin';
	form.password = '123456';
}

// 登录
async function toLogin() {
	if (!form.username) {
		return ElMessage.error(t('用户名不能为空'));
	}

	if (!form.password) {
		return ElMessage.error(t('密码不能为空'));
	}

	if (!form.captchaId || !form.verifyCode) {
		return ElMessage.error(t('图片验证码不能为空'));
	}

	saving.value = true;

	try {
		// 登录
		await (
			isPlatform
				? service.base.open.login(form)
				: merchantRequest('auth/login', { ...form, accountType: identity })
		).then(user.setToken);

		// token 事件
		await Promise.all(app.events.hasToken.map(e => e()));

		// 设置缓存
		storage.set('username', form.username);

		// 跳转首页
		router.push('/');
	} catch (err) {
		// 刷新验证码
		refs.picCaptcha.refresh();

		// 提示错误
		ElMessageBox.alert((err as Error).message, {
			title: t('提示'),
			type: 'error'
		});
	}

	saving.value = false;
}
</script>

<style lang="scss" scoped>
.page-login {
	display: grid;
	grid-template-columns: 1fr 1fr;
	grid-template-rows: 1fr auto;
	height: 100%;
	width: 100%;
	position: relative;
	overflow: auto;
	background-color: var(--el-bg-color);
	color: var(--el-text-color-primary);

	.bg {
		position: absolute;
		left: 0;
		top: 0;
		height: 100%;
		width: 90%;
		pointer-events: none;
		transform: rotate(180deg) scaleY(-1);

		.cl-svg {
			height: 100%;
			width: 100%;
		}
	}

	.copyright {
		grid-column: 1 / -1;
		z-index: 1;
		padding: 12px;
		text-align: center;
		color: var(--el-color-info);
		font-size: 14px;
		user-select: none;
	}

	.box {
		grid-column: 2;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		min-width: 0;
		padding: 24px 16px;
		z-index: 9;

		.logo {
			height: 50px;
			margin-bottom: 20px;
			display: flex;
			align-items: center;
			user-select: none;

			.icon {
				border-radius: 8px;
				padding: 5px;
				margin-right: 10px;
				background-color: var(--workspace-rail);

				img {
					height: 36px;
				}
			}

			span {
				font-size: 28px;
				font-weight: bold;
				line-height: 1;
			}
		}

		.desc {
			font-size: 15px;
			letter-spacing: 1px;
			margin-bottom: 24px;
			user-select: none;
			max-width: 80%;
			text-align: center;
		}

		.form {
			width: min(100%, 328px);

			:deep(.el-form) {
				.el-form-item {
					margin-bottom: 20px;
				}

				.el-form-item__label {
					color: var(--el-color-info);
					padding-left: 5px;
					user-select: none;
				}

				.el-input {
					box-sizing: border-box;
					font-size: 15px;
					border: 0;
					background-color: var(--el-fill-color-light);
					padding: 0 5px;
					border-radius: 8px;
					position: relative;

					&__wrapper {
						box-shadow: 0 0 0 1px var(--el-border-color) inset;
						background-color: transparent;
						&.is-focus { box-shadow: 0 0 0 2px var(--workspace-accent-text) inset; }
					}

					&__inner {
						height: 45px;
						color: var(--el-text-color-primary);
					}

					&:-webkit-autofill {
						box-shadow: 0 0 0 1000px var(--el-fill-color-light) inset;
					}
				}
			}

			:deep(.pic-captcha) {
				margin-inline-end: -10px;
			}
		}

		.op {
			display: flex;
			justify-content: center;
			margin-top: 24px;

			:deep(.el-button) {
				height: 45px;
				width: 100%;
				font-size: 16px;
				border-radius: 8px;
				letter-spacing: 1px;
			}
		}
	}
}

.identity-options { margin-bottom: 24px; }
.merchant-entry {
	display: flex;
	flex-wrap: wrap;
	gap: 8px 16px;
	margin-top: 16px;
	:deep(.el-button + .el-button) { margin-inline-start: 0; }
}

@media screen and (max-width: 1024px) {
	.page-login {
		grid-template-columns: minmax(0, 1fr);
		.box { grid-column: 1; }
		.bg { display: none; }
	}
}
</style>

<template>
	<el-dialog
		v-model="visible"
		title="营业码开通商家账号"
		width="min(560px, 94vw)"
		:close-on-click-modal="false"
		:close-on-press-escape="!busy"
		:show-close="!busy"
		:before-close="close"
	>
		<el-alert
			title="仅首次开通需要营业码。已有商家账号请直接登录。"
			type="info"
			:closable="false"
			show-icon
		/>
		<el-alert
			v-if="error"
			:title="error"
			type="error"
			:closable="false"
			show-icon
			class="notice"
		/>
		<el-form
			label-position="top"
			class="activation-form"
			:disabled="busy || uncertain"
			@submit.prevent
		>
			<template v-if="!verified">
				<el-form-item label="申请时的联系人手机号">
					<el-input
						v-model="phone"
						aria-label="申请手机号"
						maxlength="11"
						inputmode="tel"
						autocomplete="tel"
						@input="phoneChanged"
					/>
				</el-form-item>
				<el-form-item label="图片验证码">
					<div class="captcha-row">
						<el-input
							v-model="verifyCode"
							aria-label="图片验证码"
							maxlength="8"
							autocomplete="off"
						/>
						<el-button
							v-if="captchaImage"
							class="captcha-button"
							aria-label="换一张图片验证码"
							@click="refreshCaptcha"
						>
							<img :src="captchaImage" alt="图片验证码，点击更换" />
						</el-button>
						<el-button v-else @click="refreshCaptcha">加载图片验证码</el-button>
					</div>
				</el-form-item>
				<el-form-item label="短信验证码">
					<div class="captcha-row">
						<el-input
							v-model="smsCode"
							aria-label="短信验证码"
							maxlength="6"
							inputmode="numeric"
							autocomplete="one-time-code"
						/>
						<el-button :disabled="remaining > 0" @click="sendSms">{{
							remaining > 0 ? `${remaining}秒后重发` : '发送验证码'
						}}</el-button>
					</div>
				</el-form-item>
				<el-button type="primary" :loading="busy" @click="verifyPhone"
					>验证手机号，继续开通</el-button
				>
			</template>
			<template v-else>
				<p>已验证手机号：{{ phone.slice(0, 3) }}****{{ phone.slice(-4) }}</p>
				<el-form-item label="选择入驻申请">
					<el-select
						v-model="applicationNo"
						placeholder="选择要开通的商家"
						style="width: 100%"
						@change="selectionChanged"
					>
						<el-option
							v-for="item in applications"
							:key="item.applicationNo"
							:value="item.applicationNo"
							:label="`${item.merchantName}（${item.stateText}）`"
						/>
					</el-select>
				</el-form-item>
				<el-alert
					v-if="selected && selected.state !== 'APPROVED'"
					:title="stateHint"
					type="warning"
					:closable="false"
				/>
				<template v-if="selected?.state === 'APPROVED' && !done">
					<el-form-item label="营业码"
						><el-input
							v-model="code"
							aria-label="营业码"
							maxlength="35"
							placeholder="MK-开头的完整营业码"
							autocomplete="off"
					/></el-form-item>
					<el-form-item label="设置商家账号"
						><el-input
							v-model="username"
							aria-label="设置商家账号"
							maxlength="40"
							placeholder="4至40位字母、数字或下划线"
							autocomplete="off"
					/></el-form-item>
					<el-form-item label="设置密码"
						><el-input
							v-model="password"
							aria-label="设置密码"
							type="password"
							show-password
							maxlength="64"
							placeholder="8至64位，不含空白"
							autocomplete="new-password"
					/></el-form-item>
					<el-form-item label="确认密码"
						><el-input
							v-model="confirmation"
							aria-label="确认密码"
							type="password"
							show-password
							maxlength="64"
							autocomplete="new-password"
					/></el-form-item>
					<el-button type="primary" :loading="busy" @click="activate"
						>确认开通商家账号</el-button
					>
				</template>
				<el-empty
					v-if="!applications.length"
					description="该手机号没有入驻申请，请先到小程序申请"
					:image-size="64"
				/>
			</template>
		</el-form>
		<el-alert
			v-if="uncertain"
			title="开通结果尚未确认，请先查询原申请状态，不要重复开通或更换账号。"
			type="warning"
			:closable="false"
			class="notice"
		/>
		<el-alert
			v-if="done"
			title="账号已开通，请返回登录；开通后再创建门店和员工账号。"
			type="success"
			:closable="false"
			class="notice"
		/>
		<template #footer>
			<el-button v-if="verified && !done && !uncertain" :disabled="busy" @click="restart"
				>重新验证手机号</el-button
			>
			<el-button v-if="verified && !done" :loading="busy" @click="refreshStatus">{{
				uncertain ? '查询开通结果' : '刷新申请状态'
			}}</el-button>
			<el-button :disabled="busy" :type="done ? 'primary' : 'default'" @click="close()">{{
				done ? '返回登录' : '关闭'
			}}</el-button>
		</template>
	</el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import {
	activationApi as api,
	ActivationRequestError,
	type ActivationApplication
} from '/$/merchant/activation-api';
const emit = defineEmits<{ activated: [username: string] }>();
const visible = ref(false),
	busy = ref(false),
	error = ref(''),
	verified = ref(false),
	uncertain = ref(false),
	done = ref(false);
const phone = ref(''),
	verifyCode = ref(''),
	smsCode = ref(''),
	captchaImage = ref('');
const applicationNo = ref(''),
	applications = ref<ActivationApplication[]>([]);
const code = ref(''),
	username = ref(''),
	password = ref(''),
	confirmation = ref('');
let token = '',
	expiresAt = 0,
	captchaId = '',
	generation = 0,
	captchaRound = 0,
	sendAfter = 0;
const now = ref(Date.now());
const timer = setInterval(() => {
	now.value = Date.now();
}, 1000);
const remaining = computed(() => Math.max(0, Math.ceil((sendAfter - now.value) / 1000)));
const selected = computed(() =>
	applications.value.find(item => item.applicationNo === applicationNo.value)
);
const stateHint = computed(
	() =>
		({
			PENDING: '申请尚在审核，请等待平台审核。',
			REJECTED: '申请已驳回，请回小程序查看原因并修改重提。',
			CODE_EXPIRED: '营业码已过期，请回小程序重新领取。',
			ACTIVATED: '该商家已开通，请直接登录已有账号。',
			APPROVED: ''
		})[selected.value?.state || 'PENDING']
);
function clearSecrets() {
	token = '';
	expiresAt = 0;
	code.value = '';
	password.value = '';
	confirmation.value = '';
}
function reset() {
	generation++;
	captchaRound++;
	busy.value = false;
	error.value = '';
	verified.value = false;
	uncertain.value = false;
	done.value = false;
	clearSecrets();
	phone.value = '';
	smsCode.value = '';
	verifyCode.value = '';
	captchaImage.value = '';
	captchaId = '';
	applicationNo.value = '';
	applications.value = [];
	username.value = '';
}
function close(next?: () => void) {
	if (busy.value) return;
	const name = done.value ? username.value : '';
	reset();
	visible.value = false;
	if (name) emit('activated', name);
	next?.();
}
function open() {
	if (visible.value) return;
	reset();
	visible.value = true;
	void refreshCaptcha();
}
function restart() {
	reset();
	void refreshCaptcha();
}
function phoneChanged() {
	smsCode.value = '';
}
function selectionChanged() {
	code.value = '';
	password.value = '';
	confirmation.value = '';
	done.value = false;
	error.value = '';
}
function requireToken() {
	if (!token || Date.now() >= expiresAt) {
		clearSecrets();
		verified.value = false;
		uncertain.value = false;
		throw new Error('手机号验证已过期，请重新验证同一手机号后查询原申请。');
	}
}
async function run(action: (current: () => boolean) => Promise<void>) {
	if (busy.value) return;
	const round = generation;
	busy.value = true;
	error.value = '';
	const current = () => visible.value && generation === round;
	try {
		await action(current);
	} catch (cause) {
		if (current()) error.value = cause instanceof Error ? cause.message : '操作失败，请重试';
	} finally {
		if (current()) busy.value = false;
	}
}
async function refreshCaptcha() {
	const round = ++captchaRound,
		session = generation;
	captchaId = '';
	verifyCode.value = '';
	captchaImage.value = '';
	try {
		const result = await api.captcha();
		if (!visible.value || session !== generation || round !== captchaRound) return;
		if (!result?.captchaId || !result.data?.startsWith('data:image/'))
			throw new Error('图片验证码加载失败，请重试');
		captchaId = result.captchaId;
		captchaImage.value = result.data;
	} catch (cause) {
		if (visible.value && session === generation && round === captchaRound)
			error.value = cause instanceof Error ? cause.message : '图片验证码加载失败';
	}
}
async function sendSms() {
	if (remaining.value > 0) return;
	if (!/^1[3-9]\d{9}$/.test(phone.value) || !captchaId || !verifyCode.value.trim()) {
		error.value = '请填写申请手机号和图片验证码';
		return;
	}
	await run(async current => {
		const number = phone.value;
		await api.sms(number, captchaId, verifyCode.value.trim());
		if (current()) {
			sendAfter = Date.now() + 60000;
			now.value = Date.now();
		}
	});
	if (visible.value) await refreshCaptcha();
}
async function verifyPhone() {
	if (!/^1[3-9]\d{9}$/.test(phone.value) || !/^\d{6}$/.test(smsCode.value)) {
		error.value = '请填写申请手机号和6位短信验证码';
		return;
	}
	await run(async current => {
		const result = await api.verify(phone.value, smsCode.value);
		if (!current()) return;
		if (!/^[a-f0-9]{64}$/.test(result?.onboardingToken) || !(result.expiresIn > 0))
			throw new Error('手机号验证响应异常，请重新验证');
		token = result.onboardingToken;
		expiresAt = Date.now() + result.expiresIn * 1000;
		const rows = await api.list(token);
		if (!current()) return;
		if (!Array.isArray(rows)) throw new Error('申请列表响应异常，请重试');
		applications.value = rows;
		applicationNo.value = rows.length === 1 ? rows[0].applicationNo : '';
		verified.value = true;
		smsCode.value = '';
		verifyCode.value = '';
	});
}
async function refreshStatus() {
	await run(async current => {
		requireToken();
		if (!applicationNo.value) {
			const rows = await api.list(token);
			if (current()) applications.value = rows;
			return;
		}
		const result = await api.info(token, applicationNo.value);
		if (!current()) return;
		if (result.applicationNo !== applicationNo.value)
			throw new Error('申请状态响应不匹配，请重试');
		applications.value = applications.value.map(item =>
			item.applicationNo === result.applicationNo ? result : item
		);
		if (result.state === 'ACTIVATED') {
			done.value = true;
			uncertain.value = false;
			clearSecrets();
		} else {
			uncertain.value = false;
			password.value = '';
			confirmation.value = '';
		}
	});
}
async function activate() {
	if (uncertain.value || done.value || selected.value?.state !== 'APPROVED') return;
	const normalized = code.value.trim().toUpperCase(),
		account = username.value.trim();
	if (!/^MK-[A-F0-9]{32}$/.test(normalized)) {
		error.value = '请输入MK-开头的完整营业码';
		return;
	}
	if (!/^[A-Za-z0-9_]{4,40}$/.test(account) || account.toLowerCase() === 'admin') {
		error.value = '账号需4至40位字母、数字或下划线，不能使用admin';
		return;
	}
	if (
		password.value.length < 8 ||
		password.value.length > 64 ||
		/\s/.test(password.value) ||
		new TextEncoder().encode(password.value).length > 72
	) {
		error.value = '密码需8至64位，不含空白且UTF-8编码不超过72字节';
		return;
	}
	if (confirmation.value !== password.value) {
		error.value = '两次输入的密码不一致';
		return;
	}
	await run(async current => {
		requireToken();
		username.value = account;
		// 先核对所选申请的营业码，保证超时后能查询原申请，而不是误查另一家商家。
		const detail = await api.info(token, applicationNo.value);
		if (!current()) return;
		if (detail.state !== 'APPROVED' || detail.applicationNo !== applicationNo.value)
			throw new Error('申请状态已变化，请刷新申请状态');
		const originalCode = (detail as ActivationApplication & { activationCode?: string })
			.activationCode;
		if (originalCode !== normalized)
			throw new Error('营业码与所选申请不匹配，请核对小程序中的营业码');
		try {
			const result = await api.activate(token, normalized, account, password.value);
			if (!current()) return;
			if (
				result.state !== 'ACTIVATED' ||
				result.applicationNo !== applicationNo.value ||
				!result.accountId
			) {
				uncertain.value = true;
				throw new Error('开通响应异常，请查询原申请状态');
			}
			done.value = true;
			clearSecrets();
		} catch (cause) {
			if (current() && cause instanceof ActivationRequestError && cause.uncertain)
				uncertain.value = true;
			throw cause;
		} finally {
			if (current()) {
				password.value = '';
				confirmation.value = '';
			}
		}
	});
}
onBeforeUnmount(() => {
	reset();
	clearInterval(timer);
});
defineExpose({ open });
</script>
<style scoped>
.activation-form {
	margin-top: 20px;
}
.captcha-row {
	display: flex;
	gap: 12px;
	width: 100%;
	align-items: center;
}
.captcha-row .el-input {
	flex: 1;
	min-width: 0;
}
.captcha-button {
	padding: 0;
	width: 130px;
	height: 42px;
}
.captcha-button img {
	max-width: 128px;
	max-height: 40px;
}
.notice {
	margin-top: 16px;
}
</style>

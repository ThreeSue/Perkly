import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/modules/approval/utils/flow.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
	compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext }
});
const { buildApprovalFlow } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

function event(action, id, round = 1, actorName = '张三', actorType = 'MERCHANT') {
	return { id, action, submissionNo: round, version: round, actorName, actorType,
		reviewedAt: '2026-10-21T09:30:00+08:00' };
}
function detail(status = 1, history = [], publishStatus = 0) {
	return { item: { id: 1, title: '双人餐', merchantName: '青禾咖啡', submittedBy: '张三',
		submittedAt: '2026-10-21T09:00:00+08:00', status, version: 1, publishStatus },
		history, snapshot: {}, historical: history.length === 0, nextStep: '', canReview: true };
}

test('待审核同时展示实际提交人、来源商家、待处理角色和未来上架步骤', () => {
	const nodes = buildApprovalFlow(detail(1, [event('SUBMIT', 1)]), 'COUPON');
	assert.deepEqual(nodes.map(n => n.state), ['done', 'current', 'future']);
	assert.match(nodes[0].actor, /张三/);
	assert.match(nodes[0].context, /青禾咖啡.*提交给：平台运营审核/);
	assert.match(nodes[1].context, /张三/);
	assert.equal(nodes[1].actor, '具备审批权限的运营人员');
	assert.equal(nodes[1].time, undefined);
	assert.equal(nodes[2].time, undefined);
});

test('审核通过记录实际审核人和意见，上架仍为待办', () => {
	const approved = { ...event('APPROVE', 2, 1, '李运营', 'ADMIN'), reason: '资料齐全' };
	const nodes = buildApprovalFlow(detail(2, [event('SUBMIT', 1), approved]), 'COUPON');
	assert.match(nodes[1].actor, /李运营.*平台运营/);
	assert.equal(nodes[1].reason, '资料齐全');
	assert.equal(nodes[1].time, approved.reviewedAt);
	assert.equal(nodes.at(-1).badge, '待商家上架');
	assert.equal(nodes.at(-1).state, 'current');
});

test('驳回保留意见，当前转为修改重提，上架处于未来步骤', () => {
	const nodes = buildApprovalFlow(detail(3, [event('SUBMIT', 1),
		{ ...event('REJECT', 2, 1, '李运营', 'ADMIN'), reason: '请补齐资料' }]), 'COUPON');
	assert.equal(nodes[1].state, 'rejected');
	assert.equal(nodes[1].reason, '请补齐资料');
	assert.equal(nodes[2].title, '修改并重新提交');
	assert.equal(nodes.at(-1).state, 'future');
});

test('重提保留旧轮次并使用最新提交人作为本轮审核来源', () => {
	const nodes = buildApprovalFlow(detail(1, [event('SUBMIT', 1), event('REJECT', 2),
		event('SUBMIT', 3, 2, '王店长')]), 'COUPON');
	assert.equal(nodes.filter(n => n.title === '提交申请').length, 2);
	assert.equal(nodes.find(n => n.key === 'review').round, 2);
	assert.match(nodes.find(n => n.key === 'review').context, /王店长/);
});

test('撤回显示真实操作以及重新提交待办', () => {
	const nodes = buildApprovalFlow(detail(0, [event('SUBMIT', 1), event('WITHDRAW', 2)]), 'COUPON');
	assert.equal(nodes[1].state, 'withdrawn');
	assert.equal(nodes.find(n => n.key === 'resubmit').state, 'current');
});

test('已上架只显示真实上架记录，不重复添加待上架步骤', () => {
	const nodes = buildApprovalFlow(detail(2, [event('SUBMIT', 1), event('APPROVE', 2),
		event('PUBLISH', 3, 1, '王店长')], 1), 'COUPON');
	assert.equal(nodes.length, 3);
	assert.equal(nodes.at(-1).badge, '已上架');
	assert.match(nodes.at(-1).actor, /王店长/);
	assert.equal(nodes.some(n => n.state === 'current'), false);
});

test('下架保留上下架记录，并等待商家重新上架', () => {
	const nodes = buildApprovalFlow(detail(2, [event('SUBMIT', 1), event('APPROVE', 2),
		event('PUBLISH', 3), event('UNPUBLISH', 4)], 2), 'COUPON');
	assert.equal(nodes.at(-2).badge, '已下架');
	assert.equal(nodes.at(-1).title, '商家重新上架');
	assert.equal(nodes.at(-1).state, 'current');
});

test('历史已通过且上架的数据不虚构审核人、上架人和操作时间', () => {
	const nodes = buildApprovalFlow(detail(2, [], 1), 'COUPON');
	assert.equal(nodes.length, 3);
	for (const node of nodes.slice(1)) {
		assert.equal(node.actor, '历史未记录');
		assert.equal(node.time, undefined);
		assert.equal(node.historical, true);
	}
});

test('历史驳回不虚构处理人或驳回意见', () => {
	const nodes = buildApprovalFlow(detail(3), 'COUPON');
	assert.equal(nodes.find(n => n.state === 'rejected').reason, undefined);
	assert.equal(nodes.find(n => n.state === 'rejected').actor, '历史未记录');
});

test('商家入驻待审核后续为系统开通账号，不出现优惠券上架', () => {
	const nodes = buildApprovalFlow(detail(1, [event('SUBMIT', 1, 1, '张三', 'APPLICANT')]), 'MERCHANT');
	assert.equal(nodes.at(-1).title, '开通经营账号');
	assert.equal(nodes.at(-1).state, 'future');
	assert.equal(nodes.at(-1).actor, '系统自动处理');
});

test('商家入驻通过后显示账号已开通', () => {
	const nodes = buildApprovalFlow(detail(2, [event('SUBMIT', 1), event('APPROVE', 2)]), 'MERCHANT');
	assert.equal(nodes.at(-1).badge, '已开通');
	assert.equal(nodes.at(-1).state, 'done');
	assert.equal(nodes.at(-1).time, undefined);
});

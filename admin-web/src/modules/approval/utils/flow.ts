export interface ApprovalItem {
	id: number;
	templateId?: number;
	title: string;
	merchantName: string;
	submittedBy: string;
	submittedAt?: string;
	status: number;
	version: number;
	publishStatus?: number;
}

export interface ApprovalEvent {
	id: number;
	submissionNo: number;
	version: number;
	action: string;
	actorName: string;
	actorType: string;
	reason?: string;
	reviewedAt?: string;
}

export interface ApprovalDetail {
	item: ApprovalItem;
	snapshot: Record<string, any>;
	history: ApprovalEvent[];
	nextStep: string;
	historical: boolean;
	canReview: boolean;
}

export interface FlowNode {
	key: string;
	title: string;
	state: 'done' | 'current' | 'future' | 'rejected' | 'withdrawn';
	badge: string;
	actor: string;
	actorLabel: string;
	context: string;
	round?: number;
	time?: string;
	reason?: string;
	historical?: boolean;
}

const roles: Record<string, string> = {
	ADMIN: '平台运营',
	MERCHANT: '商家',
	APPLICANT: '入驻申请人',
	SYSTEM: '系统操作'
};

// 真实记录按接口给出的顺序展示；待办节点只标负责角色，不推测具体处理人或时间。
export function buildApprovalFlow(detail: ApprovalDetail, type: 'COUPON' | 'MERCHANT'): FlowNode[] {
	const { item, history } = detail;
	const nodes: FlowNode[] = [];
	let source = item.submittedBy || '历史未记录';
	let round: number | undefined;
	let actions = new Set<string>();
	const merchant = item.merchantName || '历史商家未记录';
	const submittedContext = `所属商家：${merchant}；提交给：平台运营审核`;

	for (const event of history) {
		if (round !== event.submissionNo) {
			round = event.submissionNo;
			actions = new Set();
			source = '历史未记录';
		}
		if (event.action === 'SUBMIT') source = event.actorName || '历史未记录';
		actions.add(event.action);
		const titles: Record<string, string> = {
			SUBMIT: '提交申请',
			APPROVE: '平台运营审核',
			REJECT: '平台运营审核',
			WITHDRAW: '撤回申请',
			PUBLISH: '优惠券上架',
			UNPUBLISH: '优惠券下架'
		};
		const badge =
			event.action === 'REJECT'
				? '已驳回'
				: event.action === 'WITHDRAW'
					? '已撤回'
					: event.action === 'APPROVE'
						? '已通过'
						: event.action === 'PUBLISH'
							? '已上架'
							: event.action === 'UNPUBLISH'
								? '已下架'
								: '已完成';
		nodes.push({
			key: `event-${event.id}`,
			title: titles[event.action] || '流程操作',
			state:
				event.action === 'REJECT'
					? 'rejected'
					: event.action === 'WITHDRAW'
						? 'withdrawn'
						: 'done',
			badge,
			actor: `${event.actorName || '历史未记录'} · ${roles[event.actorType] || '历史身份'}`,
			actorLabel: event.action === 'SUBMIT' ? '提交人' : '操作人',
			context:
				event.action === 'SUBMIT'
					? submittedContext
					: ['APPROVE', 'REJECT'].includes(event.action)
						? `由 ${source} 提交；所属商家：${merchant}`
						: `所属商家：${merchant}`,
			round,
			time: event.reviewedAt,
			reason: event.reason
		});
	}
	if (!history.length) {
		nodes.push({
			key: 'historical-submit',
			title: '提交申请',
			state: 'done',
			badge: '已提交',
			actor: source,
			actorLabel: '提交人',
			context: submittedContext,
			time: item.submittedAt,
			historical: true
		});
	}
	if (item.status === 1) {
		nodes.push({
			key: 'review',
			title: '平台运营审核',
			state: 'current',
			badge: '待审核',
			actor: '具备审批权限的运营人员',
			actorLabel: '待处理角色',
			round,
			context: `由 ${source} 提交；所属商家：${merchant}`
		});
	} else if (item.status === 2 && !actions.has('APPROVE')) {
		nodes.push({
			key: 'historical-review',
			title: '平台运营审核',
			state: 'done',
			badge: '已通过',
			actor: '历史未记录',
			actorLabel: '审核人',
			context: `由 ${source} 提交`,
			historical: true
		});
	} else if (item.status === 3 || item.status === 0) {
		const rejected = item.status === 3;
		if (!actions.has(rejected ? 'REJECT' : 'WITHDRAW')) {
			nodes.push({
				key: 'historical-result',
				title: rejected ? '平台运营审核' : '撤回申请',
				state: rejected ? 'rejected' : 'withdrawn',
				badge: rejected ? '已驳回' : '已撤回',
				actor: '历史未记录',
				actorLabel: '操作人',
				context: `所属商家：${merchant}`,
				historical: true
			});
		}
		nodes.push({
			key: 'resubmit',
			title: '修改并重新提交',
			state: 'current',
			badge: '待重新提交',
			actor: type === 'MERCHANT' ? '入驻申请人' : merchant,
			actorLabel: '待处理方',
			context: rejected
				? '根据驳回意见修改资料，重新提交后再次进入运营审核。'
				: '申请已撤回，可修改资料后重新提交。'
		});
	}
	if (type === 'MERCHANT') {
		const approved = item.status === 2;
		nodes.push({
			key: 'account',
			title: '开通经营账号',
			state: approved ? 'done' : 'future',
			badge: approved ? '已开通' : '待进行',
			actor: '系统自动处理',
			actorLabel: '处理方式',
			context: approved
				? `${merchant} 的经营账号已随审核通过开通。`
				: '审核通过后，系统自动开通商家经营账号。'
		});
	} else {
		if (item.publishStatus === 1) {
			if (!actions.has('PUBLISH'))
				nodes.push({
					key: 'historical-publish',
					title: '优惠券上架',
					state: 'done',
					badge: '已上架',
					actor: '历史未记录',
					actorLabel: '操作人',
					context: `所属商家：${merchant}`,
					historical: true
				});
		} else {
			const ready = item.status === 2;
			nodes.push({
				key: 'publish',
				title: item.publishStatus === 2 ? '商家重新上架' : '商家上架',
				state: ready ? 'current' : 'future',
				badge: ready ? '待商家上架' : '待进行',
				actor: merchant,
				actorLabel: '待处理方',
				context: ready
					? '运营审核已通过，等待商家自行操作上架。'
					: '需先通过运营审核，再由商家操作上架。'
			});
		}
	}
	return nodes;
}

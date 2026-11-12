import { request } from '/@/cool/service/request';
import { config } from '/@/config';

export async function riskApi<T = unknown>(path: string, data?: unknown, params?: unknown): Promise<T> {
 return await request({url: `${config.baseUrl}/admin/content-risk/${path}`, method: data === undefined ? 'GET' : 'POST', data, params}) as unknown as T;
}

export const riskScenes: Record<string, string> = {
 SEARCH: '搜索', PROFILE: '公开资料', FEEDBACK: '用户反馈', CHAT: '客服聊天',
 MERCHANT: '商家资料', STORE: '门店资料', COUPON: '优惠券文案', NOTICE: '通知',
 AI_INPUT: 'AI 用户输入', AI_OUTPUT: 'AI 系统输出（不归责用户）'
};

export const riskRecordRoutes = ['/user/search-risk', '/workspace-admin/search-risk'];

export const riskToolPages = [
	{ key: 'words', path: '/user/search-risk/words', label: '词库与规则', description: '维护启用的词条，配置各内容入口的检测方式。' },
	{ key: 'audit', path: '/user/search-risk/audit', label: '操作记录', description: '查询词条变更、人工复核、通知和巡检记录。' }
] as const;

export type RiskToolPage = (typeof riskToolPages)[number];

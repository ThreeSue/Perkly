export type WorkspaceIdentity = 'PLATFORM' | 'MERCHANT' | 'STORE';
const valid = (value: string | null): value is WorkspaceIdentity =>
	['PLATFORM', 'MERCHANT', 'STORE'].includes(value || '');
const url = new URL(window.location.href);
const queryIdentity =
	url.searchParams.get('identity') ||
	new URLSearchParams(url.hash.split('?')[1] || '').get('identity');
const saved = sessionStorage.getItem('perkly.workspace');
export const identity: WorkspaceIdentity = valid(queryIdentity)
	? queryIdentity
	: valid(saved)
		? saved
		: 'PLATFORM';
sessionStorage.setItem('perkly.workspace', identity);
const palettes = {
	PLATFORM: { accent: '#254DC8', selection: '#EAF0FF', canvas: '#F1F4FA' },
	MERCHANT: { accent: '#A84418', selection: '#FFF1E8', canvas: '#F8F5F1' },
	STORE: { accent: '#0F6B68', selection: '#E7F5F3', canvas: '#F0F6F5' }
} satisfies Record<WorkspaceIdentity, { accent: string; selection: string; canvas: string }>;
export const workspacePalette = palettes[identity];
const root = document.documentElement;
root.dataset.workspace = identity.toLowerCase();
for (const [name, value] of Object.entries(workspacePalette)) {
	root.style.setProperty(`--workspace-${name}`, value);
}
export const isPlatform = identity === 'PLATFORM';
export const identityName = { PLATFORM: '平台运营', MERCHANT: '商家管理', STORE: '门店管理' }[
	identity
];
export function switchIdentity(next: WorkspaceIdentity) {
	const target = new URL(import.meta.env.BASE_URL, window.location.origin);
	target.searchParams.set('identity', next);
	if (window.location.hash.startsWith('#/')) target.hash = '/login';
	else target.pathname = target.pathname.replace(/\/$/, '') + '/login';
	window.location.assign(target.href);
}

import type { ModuleConfig } from '/@/cool';
import { isPlatform } from '/@/workspace';
import { useBase } from '/$/base';
export default (): ModuleConfig => ({
	onLoad() {
		if (isPlatform) return;
		let checking = false;
		window.addEventListener('focus', async () => {
			const { user } = useBase();
			if (checking || !user.token) return;
			checking = true;
			const before = JSON.stringify(user.info);
			try {
				await user.get();
				if (JSON.stringify(user.info) !== before) window.location.reload();
			} catch (error: any) {
				if (error.code === 401 || error.code === 403) await user.logout();
			} finally {
				checking = false;
			}
		});
	}
});

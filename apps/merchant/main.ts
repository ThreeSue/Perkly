import { createSSRApp } from "vue";
import App from "./App.uvue";
import { installMerchantAuthGuard } from "./composables/merchant-auth-guard";

export function createApp() {
	const app = createSSRApp(App);
	installMerchantAuthGuard(app);

	return {
		app
	};
}

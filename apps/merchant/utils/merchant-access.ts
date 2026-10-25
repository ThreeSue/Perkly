const publicPaths = ["welcome", "login", "onboard"];
const privatePaths = ["home", "records", "profile", "help", "application"];
export function merchantRouteAccess(
	url: string,
	signedIn: boolean,
	_manager = false,
	_canOperate = false
) {
	const path = url.split("?")[0].replace(/^\//, "");
	if (publicPaths.some((name) => path === "pages/business/" + name)) return "public";
	if (!signedIn) return "login";
	if (path === "pages/business/records" && !_canOperate) return "review";
	return privatePaths.some((name) => path === "pages/business/" + name)
		? "allowed"
		: "unavailable";
}

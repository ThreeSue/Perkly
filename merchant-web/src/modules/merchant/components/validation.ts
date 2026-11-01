export const phoneRule = {
	validator(_rule: unknown, value: unknown, callback: (error?: Error) => void) {
		const text = String(value ?? "").trim();
		const digits = text.replace(/[^0-9]/g, "").length;
		callback(/^[+0-9][0-9 ()-]{5,29}$/.test(text) && digits >= 7 && digits <= 20 ? undefined : new Error("请输入有效的手机号或固定电话"));
	}, trigger: "blur",
};

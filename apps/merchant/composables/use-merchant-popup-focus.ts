import { nextTick, onUnmounted, watch } from "vue";
// 商家表单弹层共用键盘焦点约定；微信端不引入任何DOM API。
export function useMerchantPopupFocus(id: string, visible: () => boolean, close: () => void) {
	// #ifdef H5
	let previous: HTMLElement | null = null;
	let round = 0;
	const available = (node: HTMLElement) =>
		node.tabIndex >= 0 &&
		node.getAttribute("aria-disabled") !== "true" &&
		!node.hasAttribute("disabled") &&
		!node.classList.contains("uni-button-disabled") &&
		node.getBoundingClientRect().height > 0;
	const controls = (panel: HTMLElement) =>
		Array.from(
			panel.querySelectorAll<HTMLElement>(
				"button, input, textarea, select, [tabindex], [role=button]"
			)
		).filter(available);
	function keydown(event: KeyboardEvent) {
		if (!visible()) return;
		// 上层Cool确认框自行处理键盘，不从其后方抢焦点。
		if (
			Array.from(document.querySelectorAll<HTMLElement>(".cl-confirm")).some(
				(node) => node.getBoundingClientRect().height > 0
			)
		)
			return;
		const panel = document.getElementById(id);
		if (!panel) return;
		if (event.key === "Escape") {
			event.preventDefault();
			close();
			return;
		}
		if (event.key !== "Tab") return;
		const items = controls(panel),
			first = items[0],
			last = items[items.length - 1];
		if (!first) {
			event.preventDefault();
			panel.focus();
			return;
		}
		if (
			!panel.contains(document.activeElement) ||
			(!event.shiftKey && document.activeElement === last)
		) {
			event.preventDefault();
			first.focus();
		} else if (event.shiftKey && document.activeElement === first) {
			event.preventDefault();
			last.focus();
		}
	}
	watch(visible, async (open) => {
		const current = ++round;
		if (open) {
			previous = document.activeElement as HTMLElement;
			await nextTick();
			window.requestAnimationFrame(() => {
				if (current !== round || !visible()) return;
				const panel = document.getElementById(id);
				if (panel) (controls(panel)[0] || panel).focus();
			});
			document.addEventListener("keydown", keydown);
		} else {
			document.removeEventListener("keydown", keydown);
			if (previous?.isConnected) previous.focus();
			previous = null;
		}
	});
	onUnmounted(() => {
		round++;
		document.removeEventListener("keydown", keydown);
	});
	// #endif
}

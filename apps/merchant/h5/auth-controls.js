// H5 DOM adapter; excluded from the WeChat build. .uvue uses UniElement proxies,
// so native input attributes must be set from an ordinary JavaScript module.
export function syncAuthInput(id, label, description, invalid, autocomplete) {
	if (!id) return;
	const host = document.getElementById(id + "-host") || document.getElementById(id);
	const input = host?.querySelector("input") || (host instanceof HTMLInputElement ? host : null);
	if (!input) return;
	if (host !== input) host.id = id + "-host";
	input.id = id;
	input.setAttribute("aria-label", label);
	input.setAttribute("aria-describedby", description);
	input.setAttribute("aria-invalid", String(invalid));
	input.setAttribute("autocomplete", autocomplete);
}
export function focusAuthInput(id) {
	document.getElementById(id)?.focus();
}

export function activateAuthDialog(id, escape) {
	const root = document.getElementById(id);
	if (!root) return () => {};
	const previous = document.activeElement;
	const targets = () =>
		[...root.querySelectorAll('[role="button"]')].filter(
			(n) =>
				n.getAttribute("aria-disabled") !== "true" && n.getBoundingClientRect().height > 0
		);
	const focus = targets();
	focus[0]?.focus();
	const onKey = (event) => {
		if (event.key === "Escape") {
			event.preventDefault();
			event.stopPropagation();
			escape();
		}
		if (event.key !== "Tab") return;
		const items = targets(),
			first = items[0],
			last = items.at(-1);
		if (
			event.shiftKey &&
			(document.activeElement === first || !root.contains(document.activeElement))
		) {
			event.preventDefault();
			last?.focus();
		}
		if (
			!event.shiftKey &&
			(document.activeElement === last || !root.contains(document.activeElement))
		) {
			event.preventDefault();
			first?.focus();
		}
	};
	document.addEventListener("keydown", onKey, true);
	return () => {
		document.removeEventListener("keydown", onKey, true);
		if (previous && document.contains(previous)) previous.focus();
	};
}

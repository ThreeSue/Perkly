import type { ClInputType, PassThroughProps } from "../../types";
import type { ClIconProps } from "../cl-icon/props";

export type ClInputPassThrough = {
	className?: string;
	inner?: PassThroughProps;
	prefixIcon?: ClIconProps;
	suffixIcon?: ClIconProps;
};

export type ClInputProps = {
	inputId?: string;
	inputLabel?: string;
	inputDescription?: string;
	inputInvalid?: boolean;
	autocomplete?: string;
	className?: string;
	pt?: ClInputPassThrough;
	modelValue?: string;
	type?: ClInputType;
	prefixIcon?: string;
	suffixIcon?: string;
	password?: boolean;
	autofocus?: boolean;
	disabled?: boolean;
	readonly?: boolean;
	placeholder?: string;
	placeholderClass?: string;
	placeholderStyle?: string;
	border?: boolean;
	clearable?: boolean;
	cursorSpacing?: number;
	confirmHold?: boolean;
	confirmType?: "done" | "go" | "next" | "search" | "send";
	adjustPosition?: boolean;
	maxlength?: number;
	holdKeyboard?: boolean;
	precision?: number;
};

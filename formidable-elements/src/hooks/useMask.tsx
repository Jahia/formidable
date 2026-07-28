import {useRef} from "react";
import {applyMask, extractRawValue, maskedCursorPosition} from "~/utils/mask";

interface UseMaskOptions {
	mask?: string;
}

/**
 * Custom hook for input masking functionality.
 * Formats the value on every input event (typing, paste, drop) and keeps the
 * cursor anchored to the raw character it was after, so editing in the middle
 * of the value does not make the caret jump to the end.
 */
export const useMask = ({mask}: UseMaskOptions) => {
	const inputRef = useRef<HTMLInputElement>(null);

	const handleInput = (e: React.FormEvent<HTMLInputElement>) => {
		if (!mask) return;

		const input = e.currentTarget;
		const cursorPos = input.selectionStart ?? input.value.length;
		// Count raw characters before the caret to restore its logical position after reformatting
		const rawBeforeCursor = extractRawValue(input.value.slice(0, cursorPos)).length;

		const maskedValue = applyMask(input.value, mask);
		input.value = maskedValue;

		const newCursorPos = Math.min(maskedCursorPosition(mask, rawBeforeCursor), maskedValue.length);
		input.setSelectionRange(newCursorPos, newCursorPos);
	};

	return {
		inputRef,
		handleInput,
		// Utility function to format values programmatically
		formatValue: (value: string) => (mask ? applyMask(value || "", mask) : value)
	};
};

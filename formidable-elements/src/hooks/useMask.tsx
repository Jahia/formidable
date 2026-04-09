import {useEffect, useRef} from "react";

interface UseMaskOptions {
	mask?: string;
	defaultValue?: string;
}

interface MaskConfig {
	pattern: RegExp;
	transform?: (char: string) => string;
}

// Define mask pattern configurations
// Each character in the mask corresponds to a specific input validation rule
const MASK_PATTERNS: Record<string, MaskConfig> = {
	'9': {pattern: /\d/}, // Digit only (0-9)
	'A': {pattern: /[a-zA-Z]/, transform: (char) => char.toUpperCase()}, // Letter converted to uppercase
	'a': {pattern: /[a-zA-Z]/, transform: (char) => char.toLowerCase()}, // Letter converted to lowercase
	'X': {pattern: /[a-zA-Z0-9]/, transform: (char) => char.toUpperCase()}, // Alphanumeric uppercase
	'x': {pattern: /[a-zA-Z0-9]/, transform: (char) => char.toLowerCase()} // Alphanumeric lowercase
};

/**
 * Apply mask pattern to input value
 * @param value - Raw input value
 * @param mask - Mask pattern (e.g., "(99) 9999-9999")
 * @returns Formatted value according to mask
 */
const applyMask = (value: string, mask: string): string => {
	if (!mask) return value;

	let maskedValue = '';
	let valueIndex = 0;

	// Iterate through each character in the mask pattern
	for (let i = 0; i < mask.length && valueIndex < value.length; i++) {
		const maskChar = mask[i];
		const inputChar = value[valueIndex];
		const config = MASK_PATTERNS[maskChar];

		if (config) {
			// Check if input character matches the pattern
			if (config.pattern.test(inputChar)) {
				maskedValue += config.transform ? config.transform(inputChar) : inputChar;
				valueIndex++;
			} else {
				break; // Stop if character doesn't match pattern
			}
		} else {
			// Fixed character in mask (e.g., "-", "(", ")", " ")
			maskedValue += maskChar;
			if (inputChar === maskChar) {
				valueIndex++; // Skip if user typed the fixed character
			}
		}
	}

	return maskedValue;
};

/**
 * Custom hook for input masking functionality
 * Provides input ref, event handler, and value formatting utilities
 */
export const useMask = ({mask, defaultValue}: UseMaskOptions) => {
	const inputRef = useRef<HTMLInputElement>(null);

	/**
	 * Handle input events and apply mask formatting in real-time
	 */
	const handleInput = (e: React.FormEvent<HTMLInputElement>) => {
		if (!mask) return;

		const input = e.currentTarget;
		const cursorPos = input.selectionStart || 0;

		// Remove non-alphanumeric characters to get raw value
		const rawValue = input.value.replace(/[^\w]/g, '');
		const maskedValue = applyMask(rawValue, mask);

		// Update input value with formatted result
		input.value = maskedValue;

		// Preserve cursor position after formatting
		requestAnimationFrame(() => {
			const newCursorPos = Math.min(cursorPos, maskedValue.length);
			input.setSelectionRange(newCursorPos, newCursorPos);
		});
	};

	// Apply mask to default value on component mount
	useEffect(() => {
		if (mask && inputRef.current && defaultValue) {
			const rawValue = defaultValue.replace(/[^\w]/g, '');
			inputRef.current.value = applyMask(rawValue, mask);
		}
	}, [mask, defaultValue]);

	return {
		inputRef,
		handleInput,
		// Utility function to format values programmatically
		formatValue: (value: string) => mask ? applyMask(value || '', mask) : value
	};
};

/** What a `formidable:prefill` event carries: the profile's value, and whether it may replace the author's default. */
export interface PrefillDetail {
	value?: unknown;
	overridesDefault?: boolean;
}

/** What the slider knows of itself when the event arrives. */
export interface RangeState {
	/** The author's default, or '' for a slider that starts unanswered. */
	initialValue: string;
	/** Whether the visitor already moved or confirmed the slider. */
	answeredByVisitor: boolean;
	minValue: number;
	maxValue: number;
	step: number;
}

/**
 * The value the slider takes from a prefill, or null to ignore it. The rules are the ones the prefill
 * applies to every other control, spelt here because the slider's named control is a hidden mirror of
 * React state, which a script cannot write into: an answer the visitor gave stays, a default the author
 * gave stays unless the author allowed the override, and only a number within the bounds is an answer.
 * The number is snapped to the step grid, as the displayed position is, so the browser's own sanitizing
 * of the range input cannot desync the controlled value.
 */
export function prefillValue(detail: PrefillDetail, state: RangeState): string | null {
	if (state.answeredByVisitor || (state.initialValue !== '' && !detail.overridesDefault)) {
		return null;
	}
	if (detail.value === undefined || detail.value === null || detail.value === '') {
		return null;
	}
	const number = Number(detail.value);
	if (!Number.isFinite(number) || number < state.minValue || number > state.maxValue) {
		return null;
	}
	const step = state.step > 0 ? state.step : 1;
	const snapped = state.minValue + (Math.round((number - state.minValue) / step) * step);
	return String(Math.min(state.maxValue, Number(snapped.toFixed(6))));
}

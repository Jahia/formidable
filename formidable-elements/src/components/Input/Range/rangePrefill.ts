/** What a `formidable:prefill` event carries: the profile's value, and what the author asked to follow the write. */
export interface PrefillDetail {
	value?: unknown;
	then?: string;
}

/** What the slider knows of itself when the event arrives. */
export interface RangeState {
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
 * gave gives way to the profile's value, and only a number within the bounds is an answer — anything
 * else, a value the profile does not have included, leaves the slider as it is. The number is snapped
 * to the step grid, as the displayed position is, so the browser's own sanitizing of the range input
 * cannot desync the controlled value.
 */
export function prefillValue(detail: PrefillDetail, state: RangeState): string | null {
	if (state.answeredByVisitor) {
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

/**
 * What the author asked once the profile's value is in the slider, applied by the island itself: the
 * mirror is the only named control of the field and it is barred from constraint validation, so the
 * prefill script — which knows what it wrote, not what the island accepted — leaves this shape to the
 * island, which calls this only for a value it accepted (within the bounds, the visitor's own answer
 * kept). `readOnly` marks the field's wrapper and says to disable the visible slider, the mirror keeping
 * the value; `hidden` takes the wrapper out of sight, its value still submitted. Returns whether the
 * slider is to be locked.
 */
export function applyAfterPrefill(then: string | undefined, mirror: HTMLElement | null): boolean {
	const wrapper = mirror?.closest<HTMLElement>('[data-fmdb-node-name]') ?? null;
	if (then === 'readOnly') {
		wrapper?.setAttribute('data-fmdb-prefilled', 'readonly');
		return true;
	}
	if (then === 'hidden' && wrapper) {
		wrapper.setAttribute('data-fmdb-prefilled', 'hidden');
		wrapper.style.display = 'none';
		wrapper.setAttribute('aria-hidden', 'true');
	}
	return false;
}

/** A day-following bound's offset: the current date shifted by a signed amount. */
export interface RelativeOffset {
	amount: number;
	unit: 'days' | 'months' | 'years';
}

/**
 * One resolved bound of a date/datetime input, from its fmdbmix:dateBounds /
 * fmdbmix:datetimeBounds mode: relative to the submission day (as-is, or
 * shifted by an offset), or a fixed value, or nothing. Modes are exclusive by
 * construction, so a bound is never a combination of these.
 */
export interface ResolvedBound {
	today: boolean;
	offset?: RelativeOffset;
	fixed?: string;
}

const OFFSET_UNITS = new Set(['days', 'months', 'years']);

/**
 * Resolves a bound mode against its stored properties. A node stored before the
 * bound modes existed carries no mode but may carry a fixed value: it keeps
 * its historical behavior until the startup migration stamps it (an import of
 * an old export lands in the same state, see the upgrade notes).
 */
export const resolveBound = (
	mode: string | undefined,
	fixed: string | undefined,
	offsetAmount?: number,
	offsetUnit?: string
): ResolvedBound => {
	if (mode === "today") {
		return {today: true};
	}

	if (mode === "relative") {
		return {
			today: false,
			offset: {
				amount: offsetAmount ?? 0,
				unit: OFFSET_UNITS.has(offsetUnit ?? '') ? (offsetUnit as RelativeOffset['unit']) : 'days'
			}
		};
	}

	if (mode === "date" || (!mode && fixed !== undefined)) {
		return {today: false, fixed};
	}

	return {today: false};
};

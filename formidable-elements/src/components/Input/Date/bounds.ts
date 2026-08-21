/**
 * One resolved bound of a date/datetime input, from its fmdbmix:dateBounds /
 * fmdbmix:datetimeBounds mode: either relative to the submission day, or a
 * fixed value, or nothing. Modes are exclusive by construction, so a bound is
 * never a combination of both.
 */
export interface ResolvedBound {
	today: boolean;
	fixed?: string;
}

/**
 * Resolves a bound mode against its fixed value. A node stored before the
 * bound modes existed carries no mode but may carry a fixed value: it keeps
 * its historical behavior until the startup migration stamps it (an import of
 * an old export lands in the same state, see the upgrade notes).
 */
export const resolveBound = (mode: string | undefined, fixed: string | undefined): ResolvedBound => {
	if (mode === "today") {
		return {today: true};
	}

	if (mode === "date" || (!mode && fixed !== undefined)) {
		return {today: false, fixed};
	}

	return {today: false};
};

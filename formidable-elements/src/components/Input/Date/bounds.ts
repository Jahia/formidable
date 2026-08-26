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

/** The stored bound properties of a date/datetime field, as the views read them. */
export interface DateBoundProps {
	minMode?: string;
	maxMode?: string;
	/** Fixed values, already formatted for the input type. */
	min?: string;
	max?: string;
	minRelativeAmount?: number;
	minRelativeUnit?: string;
	maxRelativeAmount?: number;
	maxRelativeUnit?: string;
}

/**
 * Both bounds of a field resolved at once, plus whether any side follows the
 * submission day — the case that cannot be a server-rendered attribute (the
 * fragment cache would freeze it) and turns the input into an island. Shared
 * by the date and datetime-local views so they never drift.
 */
export const resolveDateBounds = (props: DateBoundProps): {
	minBound: ResolvedBound;
	maxBound: ResolvedBound;
	followsDay: boolean;
} => {
	const minBound = resolveBound(props.minMode, props.min, props.minRelativeAmount, props.minRelativeUnit);
	const maxBound = resolveBound(props.maxMode, props.max, props.maxRelativeAmount, props.maxRelativeUnit);
	return {
		minBound,
		maxBound,
		followsDay: minBound.today || maxBound.today || Boolean(minBound.offset) || Boolean(maxBound.offset)
	};
};

/** yyyy-MM-dd with a four-digit year, whatever the year (never Date-string quirks). */
const isoDay = (date: Date): string => {
	const year = String(date.getFullYear()).padStart(4, '0');
	const month = String(date.getMonth() + 1).padStart(2, '0');
	const day = String(date.getDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
};

/**
 * The local calendar day (never through toISOString, which reads the UTC day
 * and shifts around midnight for non-UTC visitors).
 */
export const localToday = (from: Date = new Date()): string => isoDay(from);

/**
 * The local day shifted by a signed offset. Month and year arithmetic mirrors
 * java.time (the server resolves the same bound with it): the day of month
 * clamps to the end of shorter months — January 31 + 1 month is February
 * 28/29, never the March 2/3 a naive setMonth would overflow to. Years go
 * through setFullYear so a computed year 0-99 never maps to the 1900s, and
 * isoDay four-digit-pads them, keeping the browser attribute and the server
 * value identical for any offset landing in years 1-9999.
 */
export const shiftedLocalDay = ({amount, unit}: RelativeOffset, from: Date = new Date()): string => {
	if (unit === 'days') {
		const target = new Date(from);
		target.setDate(target.getDate() + amount);
		return isoDay(target);
	}

	const months = unit === 'years' ? amount * 12 : amount;
	// Anchor on day 1 and let setMonth carry the overflow (negatives included),
	// then clamp the day of month to the target month's length.
	const target = new Date(0);
	target.setFullYear(from.getFullYear(), from.getMonth(), 1);
	target.setMonth(target.getMonth() + months, 1);
	const lastOfMonth = new Date(target);
	lastOfMonth.setMonth(lastOfMonth.getMonth() + 1, 0);
	target.setDate(Math.min(from.getDate(), lastOfMonth.getDate()));
	return isoDay(target);
};

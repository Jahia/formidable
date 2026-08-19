import {useEffect, useRef} from 'react';

interface TodayBoundedInputProps {
	type: 'date' | 'datetime-local';
	inputId: string;
	name: string;
	helpId?: string;
	defaultValue?: string;
	// Fixed bounds, already formatted for the input type (yyyy-MM-dd or
	// yyyy-MM-ddTHH:mm) — one format per input, so plain string comparison
	// orders them chronologically.
	min?: string;
	max?: string;
	minToday?: boolean;
	maxToday?: boolean;
	step?: number;
	required?: boolean;
	validationAttributes: Record<string, string | undefined>;
}

// The visitor's local calendar day (never through toISOString, which reads the
// UTC day and shifts around midnight for non-UTC visitors).
const localToday = (): string => {
	const now = new Date();
	const month = String(now.getMonth() + 1).padStart(2, '0');
	const day = String(now.getDate()).padStart(2, '0');
	return `${now.getFullYear()}-${month}-${day}`;
};

const mostRestrictive = (fixed: string | undefined, relative: string, minSide: boolean): string => {
	if (fixed === undefined) {
		return relative;
	}

	return minSide === (fixed > relative) ? fixed : relative;
};

/**
 * Date or datetime-local input whose bound(s) follow the submission day. The
 * relative bound cannot be server-rendered: the fragment cache would freeze it
 * at first-render time, so it is resolved at hydration, in the visitor's own
 * timezone, and combined with any fixed bound by keeping the most restrictive
 * side. Server-side validation independently enforces the same bounds against
 * the submission date.
 */
export default function TodayBoundedInput({
	type,
	inputId,
	name,
	helpId,
	defaultValue,
	min,
	max,
	minToday,
	maxToday,
	step,
	required,
	validationAttributes
}: TodayBoundedInputProps) {
	const ref = useRef<HTMLInputElement>(null);

	useEffect(() => {
		const input = ref.current;
		if (!input) {
			return;
		}

		const today = localToday();
		if (minToday) {
			input.min = mostRestrictive(min, type === 'date' ? today : `${today}T00:00`, true);
		}

		if (maxToday) {
			input.max = mostRestrictive(max, type === 'date' ? today : `${today}T23:59`, false);
		}
	}, [type, min, max, minToday, maxToday]);

	return (
		<input
			ref={ref}
			type={type}
			id={inputId}
			name={name}
			aria-describedby={helpId}
			className="fmdb-form-control"
			defaultValue={defaultValue}
			min={min}
			max={max}
			step={step}
			required={required}
			{...validationAttributes}
		/>
	);
}

import {useEffect, useRef, type ComponentProps} from 'react';

interface TodayBoundedInputProps {
	type: 'date' | 'datetime-local';
	minToday?: boolean;
	maxToday?: boolean;
	// Everything else the input carries, shared verbatim with the view's static
	// branch so both render identical markup. The fixed bound of a NON-relative
	// side rides in here as a plain min/max attribute — bound modes are
	// exclusive, so a side is never both fixed and relative.
	inputAttributes: ComponentProps<'input'>;
}

// The visitor's local calendar day (never through toISOString, which reads the
// UTC day and shifts around midnight for non-UTC visitors).
const localToday = (): string => {
	const now = new Date();
	const month = String(now.getMonth() + 1).padStart(2, '0');
	const day = String(now.getDate()).padStart(2, '0');
	return `${now.getFullYear()}-${month}-${day}`;
};

/**
 * Date or datetime-local input whose bound(s) follow the submission day. The
 * relative bound cannot be server-rendered: the fragment cache would freeze it
 * at first-render time, so it is resolved at hydration, in the visitor's own
 * timezone. Server-side validation independently enforces the same bounds
 * against the submission date.
 */
export default function TodayBoundedInput({
	type,
	minToday,
	maxToday,
	inputAttributes
}: TodayBoundedInputProps) {
	const ref = useRef<HTMLInputElement>(null);

	useEffect(() => {
		const input = ref.current;
		if (!input) {
			return;
		}

		const applyTodayBounds = () => {
			const today = localToday();
			if (minToday) {
				input.min = type === 'date' ? today : `${today}T00:00`;
			}

			if (maxToday) {
				input.max = type === 'date' ? today : `${today}T23:59`;
			}
		};

		applyTodayBounds();
		// A page can stay open across local midnight: refresh the bound whenever
		// the visitor comes back to the input, so it never enforces yesterday.
		input.addEventListener('focus', applyTodayBounds);
		return () => input.removeEventListener('focus', applyTodayBounds);
	}, [type, minToday, maxToday]);

	return (
		<input
			ref={ref}
			type={type}
			{...inputAttributes}
		/>
	);
}

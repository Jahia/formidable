import {useEffect, useRef, type ComponentProps} from 'react';
import {localToday, shiftedLocalDay, type RelativeOffset} from './bounds';

interface TodayBoundedInputProps {
	type: 'date' | 'datetime-local';
	minToday?: boolean;
	maxToday?: boolean;
	// Signed offset from the current date (relative bound mode); a side carries
	// either the today flag, an offset, or neither — modes are exclusive.
	minOffset?: RelativeOffset;
	maxOffset?: RelativeOffset;
	// Everything else the input carries, shared verbatim with the view's static
	// branch so both render identical markup. The fixed bound of a NON-relative
	// side rides in here as a plain min/max attribute.
	inputAttributes: ComponentProps<'input'>;
}

/**
 * Date or datetime-local input whose bound(s) follow the submission day — the
 * day itself, or the day shifted by a signed offset. Such a bound cannot be
 * server-rendered: the fragment cache would freeze it at first-render time, so
 * it is resolved at hydration, in the visitor's own timezone. Server-side
 * validation independently enforces the same bounds against the submission
 * date.
 */
export default function TodayBoundedInput({
	type,
	minToday,
	maxToday,
	minOffset,
	maxOffset,
	inputAttributes
}: TodayBoundedInputProps) {
	const ref = useRef<HTMLInputElement>(null);

	useEffect(() => {
		const input = ref.current;
		if (!input) {
			return;
		}

		const applyDayBounds = () => {
			const minDay = minToday ? localToday() : (minOffset ? shiftedLocalDay(minOffset) : null);
			const maxDay = maxToday ? localToday() : (maxOffset ? shiftedLocalDay(maxOffset) : null);
			if (minDay) {
				input.min = type === 'date' ? minDay : `${minDay}T00:00`;
			}

			if (maxDay) {
				input.max = type === 'date' ? maxDay : `${maxDay}T23:59`;
			}
		};

		applyDayBounds();
		// A page can stay open across local midnight: refresh the bound whenever
		// the visitor comes back to the input, so it never enforces yesterday.
		input.addEventListener('focus', applyDayBounds);
		return () => input.removeEventListener('focus', applyDayBounds);
	}, [type, minToday, maxToday, minOffset, maxOffset]);

	return (
		<input
			ref={ref}
			type={type}
			{...inputAttributes}
		/>
	);
}

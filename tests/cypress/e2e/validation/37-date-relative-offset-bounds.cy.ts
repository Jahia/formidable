import {
	createPublishedLiveFormPage,
	getInputDateNode,
	getInputDatetimeLocalNode,
	visitLiveForm
} from '../../support/fixtures';
import {useFormidableSite} from './support';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	withSameOriginHeaders
} from '../security/support';

// The browser-local calendar day, the same way the hydrated input resolves it.
const localDay = (offsetDays = 0): string => {
	const day = new Date();
	day.setDate(day.getDate() + offsetDays);
	return isoDay(day);
};

const isoDay = (date: Date): string => {
	const month = String(date.getMonth() + 1).padStart(2, '0');
	const day = String(date.getDate()).padStart(2, '0');
	return `${date.getFullYear()}-${month}-${day}`;
};

// The local day shifted by whole years with java.time's month-end clamping —
// the same arithmetic the island and the server use, so the assertion cannot
// drift from the implementation on a February 29th.
const localDayShiftedByYears = (years: number): string => {
	const now = new Date();
	const year = now.getFullYear() + years;
	const lastDay = new Date(year, now.getMonth() + 1, 0).getDate();
	return isoDay(new Date(year, now.getMonth(), Math.min(now.getDate(), lastDay)));
};

describe('Validation - 37 Date bounds at an offset from the submission day', () => {
	useFormidableSite();

	let formId: string;
	let livePath: string;

	before(() => {
		cy.login();
		createPublishedLiveFormPage(
			'relative-offset-bounds-form',
			'Relative offset bounds form',
			[
				// Age-limit shape: no birth date less than 18 years in the past.
				getInputDateNode({name: 'birthDate', title: 'Birth date', maxRelative: {amount: -18, unit: 'years'}}),
				// Booking-window shape: no appointment beyond 30 days ahead, none in the past.
				getInputDatetimeLocalNode({
					name: 'appointment',
					title: 'Appointment',
					minBoundMode: 'today',
					maxRelative: {amount: 30, unit: 'days'}
				})
			]
		).then(created => {
			formId = created.formId;
			livePath = created.livePath;
		});
		cy.logout();
	});

	it('hydrates the inputs with offsets resolved on the visitor day', () => {
		const form = visitLiveForm(livePath);

		// The bound is set at hydration (an SSR attribute would be frozen by the
		// fragment cache), so the assertions retry until the island has run.
		form.get().find('input[name="birthDate"]').should('have.attr', 'max', localDayShiftedByYears(-18));
		form.get().find('input[name="appointment"]').should('have.attr', 'min', `${localDay()}T00:00`);
		form.get().find('input[name="appointment"]').should('have.attr', 'max', `${localDay(30)}T23:59`);
	});

	it('accepts values inside the offset window', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId,
			fields: {
				birthDate: localDayShiftedByYears(-30),
				appointment: `${localDay(10)}T12:00`
			},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);
	});

	it('rejects values beyond the offsets regardless of the rendered page age', () => {
		cy.logout();

		// A birth date on the submission day is 18 years past the -18y maximum:
		// beyond any timezone widening, whatever clock the runner sits on.
		postDirectMultipartSubmission({
			formId,
			fields: {birthDate: localDay()},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));

		// Three days beyond the +30d window: the widening spans at most two
		// calendar days around any clock, so 33 is provably out.
		postDirectMultipartSubmission({
			formId,
			fields: {appointment: `${localDay(33)}T12:00`},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});
});

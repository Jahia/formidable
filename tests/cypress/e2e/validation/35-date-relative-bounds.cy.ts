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
	const month = String(day.getMonth() + 1).padStart(2, '0');
	const date = String(day.getDate()).padStart(2, '0');
	return `${day.getFullYear()}-${month}-${date}`;
};

describe('Validation - 35 Date bounds relative to the submission day', () => {
	useFormidableSite();

	let formId: string;
	let livePath: string;

	before(() => {
		cy.login();
		createPublishedLiveFormPage(
			'relative-bounds-form',
			'Relative bounds form',
			[
				// Birth-date shape: no date after the submission day.
				getInputDateNode({name: 'birthDate', title: 'Birth date', maxBoundMode: 'today'}),
				// Appointment shape: no datetime before the submission day.
				getInputDatetimeLocalNode({name: 'appointment', title: 'Appointment', minBoundMode: 'today'}),
				// Mixed sides: relative minimum, fixed maximum. Modes are exclusive
				// per side, so the fixed side must stay exactly as configured.
				getInputDateNode({name: 'booking', title: 'Booking', minBoundMode: 'today', max: '2100-06-30T00:00:00.000'})
			]
		).then(created => {
			formId = created.formId;
			livePath = created.livePath;
		});
		cy.logout();
	});

	it('hydrates the inputs with bounds resolved on the visitor day', () => {
		const form = visitLiveForm(livePath);

		// The bound is set at hydration (an SSR attribute would be frozen by the
		// fragment cache), so the assertions retry until the island has run.
		form.get().find('input[name="birthDate"]').should('have.attr', 'max', localDay());
		form.get().find('input[name="appointment"]').should('have.attr', 'min', `${localDay()}T00:00`);
		form.get().find('input[name="booking"]').should('have.attr', 'min', localDay());
		form.get().find('input[name="booking"]').should('have.attr', 'max', '2100-06-30');
	});

	it('accepts values on the submission day', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId,
			fields: {birthDate: localDay(), appointment: `${localDay()}T12:00`},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);
	});

	it('rejects values beyond the relative bounds regardless of the rendered page age', () => {
		cy.logout();

		// Three days out: the server widens a relative bound to the extreme calendar
		// day any timezone can currently be (UTC-12 to UTC+14), which spans at most
		// two calendar days around any clock — so three is provably beyond it,
		// whatever timezone the runner or the server sits in.
		postDirectMultipartSubmission({
			formId,
			fields: {birthDate: localDay(3)},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));

		postDirectMultipartSubmission({
			formId,
			fields: {appointment: `${localDay(-3)}T12:00`},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});
});

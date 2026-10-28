import {getNodeByPath} from '@jahia/cypress';
import {getLatestLiveFormSubmission, getPhoneNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	withSameOriginHeaders
} from '../security/support';
import {useFormidableSite} from './support';

interface StoredValueResponse {
	data?: {jcr?: {nodeByPath?: {properties?: Array<{name: string; value?: string | null}>}}};
}

const SAVE_TO_JCR_ACTION = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: [] as Array<{name: string; value: string}>
};

/** The pattern the CND autocreates, as the browser and the server read it. */
const DEFAULT_PATTERN = String.raw`(?=(?:\D*\d){6,15}\D*$)\+?[\d .\(\)\-]+`;

const PHONE = getPhoneNode({
	name: 'phone',
	title: 'Your phone number',
	required: true,
	placeholder: '+33 6 12 34 56 78'
});

describe('Form fields - 228 Phone (extended-inputs)', () => {
	useFormidableSite();

	let formId: string;
	let livePath: string;

	before(() => {
		cy.login();
		createPublishedLiveFormPage(
			'phone-form',
			'Phone Form',
			[PHONE],
			'phone-form-page',
			'Phone form page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(result => {
			formId = result.formId;
			livePath = result.livePath;
		});
		cy.logout();
	});

	it('renders a tel input with the tel autofill token and the default pattern', () => {
		const form = visitLiveForm(livePath);

		form.get().find('.fmdbext-phone').as('phone').should('be.visible');
		cy.get('@phone').find('label.fmdb-form-label').should('contain.text', 'Your phone number')
			.find('.fmdb-required-indicator').should('exist');
		cy.get('@phone').find('input[name="phone"]')
			.should('have.attr', 'type', 'tel')
			.and('have.class', 'fmdb-form-control')
			.and('have.attr', 'autocomplete', 'tel')
			.and('have.attr', 'pattern', DEFAULT_PATTERN)
			.and('have.attr', 'placeholder', '+33 6 12 34 56 78');
	});

	it('compiles the default pattern in the browser: national and international numbers pass, text does not', () => {
		const form = visitLiveForm(livePath);

		// A pattern the browser cannot compile is ignored, and every value would pass.
		form.get().find('input[name="phone"]').then($input => {
			const input = $input[0] as HTMLInputElement;
			['+33 6 12 34 56 78', '06.12.34.56.78', '(555) 123-4567', '+44 (0)20 7946 0958'].forEach(value => {
				input.value = value;
				expect(input.validity.patternMismatch, value).to.equal(false);
			});
			['call me', '12345', '++33 6 12 34 56 78'].forEach(value => {
				input.value = value;
				expect(input.validity.patternMismatch, value).to.equal(true);
			});
		});
	});

	it('shows the validation message in the field and submits nothing when the value is not a phone number', () => {
		const form = visitLiveForm(livePath);
		form.waitUntilHydrated();

		form.get().find('input[name="phone"]').type('call me');
		form.submit();

		form.get().find('.fmdbext-phone .fmdb-validation-error').should('be.visible');
		form.get().find('input[name="phone"]').should('have.class', 'fmdb-invalid');
		form.getMessage().should('not.exist');
	});

	it('stores the number as the visitor typed it', () => {
		const form = visitLiveForm(livePath);
		form.waitUntilHydrated();

		form.get().find('input[name="phone"]').type('+33 6 12 34 56 78');
		form.submit();
		form.waitForSubmit();

		getLatestLiveFormSubmission('phone-form').then(({path}) => {
			getNodeByPath(`${path}/data`, ['phone'], '', [], 'LIVE')
				.then((response: StoredValueResponse) => {
					const stored = response.data?.jcr?.nodeByPath?.properties?.find(p => p.name === 'phone');
					expect(stored?.value).to.eq('+33 6 12 34 56 78');
				});
		});
	});

	it('rejects a submitted value that does not match the pattern', () => {
		cy.logout();
		postDirectMultipartSubmission({
			formId,
			fields: {phone: 'call me'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});

	it('renders the help text, the length bounds and a custom autofill token, and the server checks the bounds', () => {
		cy.login();
		createPublishedLiveFormPage(
			'phone-bounds-form',
			'Phone Bounds Form',
			[getPhoneNode({
				name: 'workPhone',
				helpText: 'Your office line',
				minLength: 14,
				maxLength: 20,
				autocomplete: 'work tel'
			})],
			'phone-bounds-form-page',
			'Phone bounds form page'
		).then(result => {
			cy.logout();
			const form = visitLiveForm(result.livePath);

			// No title: the input has no visible label, so the field name labels it.
			form.get().find('.fmdbext-phone label').should('not.exist');
			form.get().find('input[name="workPhone"]').as('input')
				.should('have.attr', 'aria-label', 'workPhone')
				.and('have.attr', 'minlength', '14')
				.and('have.attr', 'maxlength', '20')
				.and('have.attr', 'autocomplete', 'work tel');
			form.get().find('.fmdbext-phone .fmdb-form-help').should('have.text', 'Your office line')
				.invoke('attr', 'id').then(helpId => {
					expect(helpId, 'id of the described block').to.match(/^help-/);
					cy.get('@input').should('have.attr', 'aria-describedby', helpId);
				});

			// Each value matches the default pattern, so only the length bound rejects it.
			['06 12 34 56', '+33 6 12 34 56 78 90 1'].forEach(value => {
				postDirectMultipartSubmission({
					formId: result.formId,
					fields: {workPhone: value},
					headers: withSameOriginHeaders()
				}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
			});
			postDirectMultipartSubmission({
				formId: result.formId,
				fields: {workPhone: '+33 6 12 34 56 78'},
				headers: withSameOriginHeaders()
			}).then(expectSuccessResponse);
		});
	});

	it('accepts any value once the author clears the pattern', () => {
		cy.login();
		createPublishedLiveFormPage(
			'phone-free-form',
			'Phone Free Form',
			[getPhoneNode({name: 'phone', title: 'Phone or extension', pattern: ''})],
			'phone-free-form-page',
			'Phone free form page'
		).then(result => {
			cy.logout();
			visitLiveForm(result.livePath).get().find('input[name="phone"]').should('not.have.attr', 'pattern');
			postDirectMultipartSubmission({
				formId: result.formId,
				fields: {phone: 'ext. 42'},
				headers: withSameOriginHeaders()
			}).then(expectSuccessResponse);
		});
	});
});

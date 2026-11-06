import {deleteNodeProperty, getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import {DIRECT_SUBMIT_PATH, FIELD_ACTION_PATH} from '../../support/constants';
import {getInputTextNode, getPasswordNode, getPasswordPolicyFieldActionNode, withFieldActions} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/** The HTML maxlength of a password field: the CND autocreates it, and the view falls back to it. */
const DEFAULT_MAX_LENGTH = '128';

/**
 * The rendering of the Password field (formidable-extended-inputs, fmdbext:password). The view renders an
 * `<input type="password">` that never carries a `value` attribute, so the markup holds no secret, in a wrapper
 * marked `data-fmdb-sensitive="true"`, the marker the page reads to treat the field as a secret. The
 * `autocomplete` token is the author's choice: `current-password` for a sign-in. Without a choice, the view renders
 * `new-password`, and it does the same for a stale value that is not one of the two tokens. The CND constraint
 * refuses such a value, so the spec provisions the unset case only. `maxlength` is 128 unless the author sets another
 * bound, and `minlength` renders only when set. After the server refuses a submission, the page empties the
 * password control and keeps every other answer. What the pipeline does with the value is the subject of the
 * actions specs 85 to 88.
 */
describe('Form fields - 229 Password (extended-inputs)', () => {
	useFormidableSite();

	it('renders a password input with no value, new-password and the default maximum length', () => {
		cy.login();
		createPublishedLiveFormPage(
			'password-default-form',
			'Password Default Form',
			[getPasswordNode({name: 'password', title: 'Choose a password'})],
			'password-default-form-page',
			'Password default form page'
		).then(({livePath}) => {
			cy.logout();
			const form = visitLiveForm(livePath);

			// The server marks the field's wrapper as sensitive: the page empties its controls after a refusal, never
			// prints its value in a message, and never evaluates a rule on it.
			form.get().find('[data-fmdb-node-name="password"]')
				.should('have.attr', 'data-fmdb-sensitive', 'true')
				.and('have.attr', 'data-fmdb-node-type', 'fmdbext:password');

			form.get().find('.fmdbext-password').as('password').should('be.visible');
			cy.get('@password').find('label.fmdb-form-label').should('contain.text', 'Choose a password')
				.find('.fmdb-required-indicator').should('exist');
			cy.get('@password').find('input[name="password"]')
				.should('have.attr', 'type', 'password')
				.and('have.class', 'fmdb-form-control')
				.and('have.attr', 'autocomplete', 'new-password')
				.and('have.attr', 'maxlength', DEFAULT_MAX_LENGTH)
				// one callback: a have.attr with no value yields the attribute's value, so a second could not be chained
				.and($input => {
					expect($input).not.to.have.attr('minlength');
					expect($input).not.to.have.attr('value');
				});
		});
	});

	it('renders current-password when the author chooses it, and the length bounds the author sets', () => {
		cy.login();
		createPublishedLiveFormPage(
			'password-sign-in-form',
			'Password Sign-in Form',
			[getPasswordNode({name: 'password', title: 'Password', autocompleteMode: 'current-password', minLength: 12, maxLength: 64})],
			'password-sign-in-form-page',
			'Password sign-in form page'
		).then(({livePath}) => {
			cy.logout();
			visitLiveForm(livePath).get().find('input[name="password"]')
				.should('have.attr', 'type', 'password')
				.and('have.attr', 'autocomplete', 'current-password')
				.and('have.attr', 'minlength', '12')
				.and('have.attr', 'maxlength', '64')
				.and('not.have.attr', 'value');
		});
	});

	it('falls back to new-password when the autocomplete mode is not set', () => {
		cy.login();
		createPublishedLiveFormPage(
			'password-unset-form',
			'Password Unset Form',
			[getPasswordNode({name: 'password', title: 'Password'})],
			'password-unset-form-page',
			'Password unset form page'
		).then(({formPath, livePath}) => {
			const passwordPath = `${formPath}/fields/password`;
			const propertyNamesIn = (workspace: 'EDIT' | 'LIVE') => getNodeByPath(passwordPath, ['autocompleteMode'], undefined, [], workspace)
				.then(response => {
					const node = response?.data?.jcr?.nodeByPath;
					expect(node, `the password field in ${workspace}`).to.exist;
					return node.properties.map(property => property.name);
				});

			// The CND autocreates the mode; removing it gives the node a field saved without one. The property is not
			// i18n, so the delete names no language: it removes the shared property, not a translation of it.
			propertyNamesIn('EDIT').should('include', 'autocompleteMode');
			deleteNodeProperty(passwordPath, 'autocompleteMode', undefined);
			propertyNamesIn('EDIT').should('not.include', 'autocompleteMode');
			publishAndWaitJobEnding(formPath);
			// Live renders the page: the property must be gone there too, or the CND default renders anyway.
			propertyNamesIn('LIVE').should('not.include', 'autocompleteMode');
			cy.logout();
			visitLiveForm(livePath).get().find('input[name="password"]')
				.should('have.attr', 'autocomplete', 'new-password')
				.and('have.attr', 'maxlength', DEFAULT_MAX_LENGTH);
		});
	});

	it('empties the password after the server refuses the submission, and keeps the other answers', () => {
		const WEAK = 'no-digits-here';
		cy.login();
		createPublishedLiveFormPage(
			'password-refused-form',
			'Password Refused Form',
			[
				getInputTextNode({name: 'username', title: 'Username'}),
				// The samples' password policy opts in to sensitive values: the pipeline refuses a password without a digit.
				withFieldActions(getPasswordNode({name: 'password', title: 'Password'}), [
					getPasswordPolicyFieldActionNode({rejectionMessage: 'Your password needs a digit.'})
				])
			],
			'password-refused-form-page',
			'Password refused form page'
		).then(({livePath}) => {
			// The pre-check cannot answer, so the page blocks nothing and the submission reaches the server, the
			// authority, which refuses it (a page that could ask would block the submission before it is sent).
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`, {statusCode: 503, body: ''}).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');
			cy.logout();
			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();
			form.getTextInput('username').get().type('ada');
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').type(WEAK);
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').should('have.value', WEAK);
			form.submit();
			cy.wait('@submit').then(({response}) => {
				expect(response?.statusCode).to.equal(422);
				expect(response?.body.errorCode).to.equal('FMDB-015');
			});

			// The refusal is shown on the password, the typed secret is gone from the page, the other answer stays.
			cy.get('[data-fmdb-node-name="password"] .fmdb-validation-error').should('be.visible').and('contain.text', 'needs a digit');
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').should('have.value', '');
			form.getTextInput('username').get().should('have.value', 'ada');
		});
	});
});

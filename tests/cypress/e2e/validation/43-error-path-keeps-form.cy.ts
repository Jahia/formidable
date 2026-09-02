import {DIRECT_SUBMIT_PATH} from '../../support/constants';
import {createPublishedLiveFormPage, getInputTextNode} from '../../support/fixtures';
import {visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/**
 * A failed submission must leave the visitor a way out that keeps what they typed.
 * The form used to hide behind the error message, whose Try again button is off by
 * default: the typed data was still in the DOM but unreachable, and the only exit
 * was a reload that lost it. The error now shows ABOVE the form, values intact,
 * and a plain resubmission can succeed.
 */
describe('Validation - 43 A failed submission keeps the form on screen', () => {
	useFormidableSite();

	it('shows the error above the intact form and lets the visitor resubmit', () => {
		createPublishedLiveFormPage(
			'error-path-form',
			'Error Path Form',
			[getInputTextNode({name: 'fullname', title: 'Full name'})]
		).then(({livePath}) => {
			// First submission fails: the server is unreachable for one request.
			cy.intercept(
				{method: 'POST', url: `**${DIRECT_SUBMIT_PATH}**`, times: 1},
				{statusCode: 503, body: {}}
			).as('failedSubmit');

			const form = visitLiveForm(livePath);
			form.getTextInput('fullname').type('Ada Lovelace');
			form.submit();
			cy.wait('@failedSubmit');

			// The error is shown, the form is still there, the typed value survived.
			form.getErrorMessage().should('be.visible');
			form.shouldBeVisible();
			form.getTextInput('fullname').shouldHaveValue('Ada Lovelace');

			// No interception anymore: the same form, resubmitted as-is, succeeds.
			form.submit();
			form.getSuccessMessage().should('be.visible');
		});
	});
});

import {
	createPublishedLiveFormPage,
	getFieldsetNode,
	getInputEmailNode,
	getInputHiddenNode,
	getInputTextNode,
	getRadioNode,
	getRichTextNode,
	getSaveToJcrActionNode,
	getStepNode,
	getTextareaNode,
	visitLiveForm
} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * One-at-a-time (docs/architecture/one-at-a-time.md): one field or fieldset per screen, Next, Enter and a
 * pick in a radio group to move on, the screens the conditional logic hides skipped, the steps walked
 * through screen by screen.
 */
const ONE_AT_A_TIME = [{name: 'formExperience', value: 'oneAtATime'}];

const TOPIC_CHOICES = [
	{value: 'sales', label: 'Sales'},
	{value: 'support', label: 'Support'},
	{value: 'press', label: 'Press'}
];

describe('Validation - 46 One-at-a-time', () => {
	useFormidableSite();

	it('shows one screen at a time, moves on with Next, Enter and a pick, and sends from the last screen', () => {
		createPublishedLiveFormPage(
			'one-at-a-time-form',
			'One At A Time Form',
			[
				getRichTextNode({name: 'welcome', text: '<p>Tell us about you.</p>'}),
				getInputTextNode({name: 'fullName', title: 'Full name', required: true}),
				getInputHiddenNode({name: 'campaign', value: 'autumn'}),
				getRadioNode({name: 'topic', title: 'Topic', required: true, choices: TOPIC_CHOICES}),
				getFieldsetNode({
					name: 'company',
					title: 'Company',
					children: [
						getInputTextNode({name: 'companyName', title: 'Company name'}),
						getInputTextNode({name: 'companySize', title: 'Company size'})
					]
				}),
				getInputEmailNode({name: 'email', title: 'Email', required: true})
			],
			undefined,
			undefined,
			{properties: ONE_AT_A_TIME, actions: [getSaveToJcrActionNode()]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// The first screen: the rich text with the field after it, nothing else
			form.shouldShowScreenOf('fullName');
			form.getRichText().should('be.visible').and('contain', 'Tell us about you.');
			form.getRadioGroup('topic').get().should('not.be.visible');
			form.get().find('.fmdb-prev-btn').should('not.exist');
			form.get().find('.fmdb-form-actions button[type="submit"]').should('not.exist');

			// Next checks the screen: a required field keeps the visitor on it
			form.nextStep();
			form.shouldShowScreenOf('fullName');
			form.getTextInput('fullName').shouldBeFocused();

			// Enter moves on, past the hidden field, to the radio group, which takes the focus
			form.getTextInput('fullName').type('Ada Lovelace{enter}');
			form.shouldShowScreenOf('topic');
			form.get().find('[data-fmdb-screen-active] input[type="radio"]').first().should('have.focus');

			// A pick moves on by itself
			form.get().find('.fmdb-radio-group').should('have.attr', 'aria-describedby').and('contain', 'fmdb-advance-hint-');
			cy.contains('label', 'Support').realClick();
			form.shouldShowScreenOf('company');
			form.getTextInput('companyName').get().should('be.visible');
			form.getTextInput('companySize').get().should('be.visible');

			// Back to the pick: clicking the answer already picked confirms it
			form.previousStep();
			form.shouldShowScreenOf('topic');
			form.getRadioGroup('topic').shouldHaveSelected('Support');
			cy.contains('label', 'Support').realClick();
			form.shouldShowScreenOf('company');

			// The arrow keys move the selection only; Enter moves on
			form.previousStep();
			form.shouldShowScreenOf('topic');
			form.get().find('input[type="radio"][value="support"]').focus();
			cy.realPress('ArrowDown');
			form.getRadioGroup('topic').shouldHaveSelected('Press');
			form.shouldStayOnScreenOf('topic');
			cy.realPress('Enter');
			form.shouldShowScreenOf('company');

			// A fieldset does not move on by itself: Next takes the visitor to the last screen
			form.nextStep();
			form.shouldShowScreenOf('email');
			form.get().find('.fmdb-next-btn').should('not.exist');
			form.getEmailInput('email').get().should('have.attr', 'enterkeyhint', 'send');

			form.getEmailInput('email').type('ada@example.com');
			form.submit();
			form.waitForSubmit();
			form.getSuccessMessage().should('be.visible');
		});
	});

	it('skips the screens the logic hides, and walks the steps screen by screen', () => {
		const companyField = getInputTextNode({name: 'organisation', title: 'Organisation', required: true});
		companyField.properties = [...(companyField.properties ?? []), {
			name: 'logics',
			values: [JSON.stringify({
				logicId: 'oaat-org',
				sourceFieldName: 'reason',
				sourceFieldType: 'fmdb:radio',
				valueKind: 'choice',
				operator: 'in',
				values: ['sales']
			})]
		}];

		createPublishedLiveFormPage(
			'one-at-a-time-steps-form',
			'One At A Time Steps Form',
			[
				getStepNode({
					name: 'aboutStep',
					title: 'About',
					label: 'About',
					children: [
						getRadioNode({name: 'reason', title: 'Reason', required: true, choices: TOPIC_CHOICES}),
						companyField
					]
				}),
				getStepNode({
					name: 'messageStep',
					title: 'Message',
					label: 'Message',
					children: [
						getTextareaNode({name: 'message', title: 'Message'}),
						getInputTextNode({name: 'signature', title: 'Signature'})
					]
				})
			],
			undefined,
			undefined,
			{properties: ONE_AT_A_TIME}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.shouldHaveCurrentStep('About');
			form.shouldShowScreenOf('reason');

			// Support hides the organisation: the pick lands on the first screen of the next step
			cy.contains('label', 'Support').realClick();
			form.shouldHaveCurrentStep('Message');
			form.shouldShowScreenOf('message');

			// Enter writes a new line in a textarea; Ctrl+Enter moves on
			form.getTextarea('message').get().type('Line one{enter}Line two');
			form.shouldShowScreenOf('message');
			form.getTextarea('message').get().should('have.value', 'Line one\nLine two');
			form.getTextarea('message').get().type('{ctrl}{enter}');
			form.shouldShowScreenOf('signature');

			// Previous crosses back into the first step, on its last shown screen
			form.previousStep();
			form.shouldShowScreenOf('message');
			form.previousStep();
			form.shouldHaveCurrentStep('About');
			form.shouldShowScreenOf('reason');

			// Sales opens the organisation: the path goes through it now
			cy.contains('label', 'Sales').realClick();
			form.shouldHaveCurrentStep('About');
			form.shouldShowScreenOf('organisation');
			form.getTextInput('organisation').type('Analytical Engines{enter}');
			form.shouldHaveCurrentStep('Message');
			form.shouldShowScreenOf('message');
		});
	});
});

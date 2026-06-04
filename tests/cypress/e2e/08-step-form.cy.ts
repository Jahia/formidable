import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_IDS} from '../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	getInputTextNode,
	getStepNode,
	INPUT_TEXT_REQUIRED,
	INPUT_TEXT_SECOND_STEP
} from '../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../support/fixtures/forms';

describe('Multi-step Form', () => {
	before(() => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
	});

	beforeEach(() => {
		cy.login();
	});

	afterEach(() => {
		cy.logout();
	});

	it('should navigate across steps and submit from the last one in live mode', () => {
		createPublishedLiveFormPage(
			'step-live-form',
			'Step Live Form',
			[
				getStepNode({
					name: 'identity',
					title: 'Identity',
					label: 'Identity',
					children: [getInputTextNode(INPUT_TEXT_REQUIRED)]
				}),
				getStepNode({
					name: 'details',
					title: 'Details',
					label: 'Details',
					children: [getInputTextNode(INPUT_TEXT_SECOND_STEP)]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form
				.shouldBeVisible()
				.shouldHaveVisibleStepCount(2)
				.shouldHaveCurrentStep('Identity');

			form.getTextInput(INPUT_TEXT_REQUIRED.name!)
				.shouldBeVisible()
				.shouldBeRequired()
				.type('Jane Doe');

			form.nextStep();

			form.shouldHaveCurrentStep('Details');
			form.getTextInput(INPUT_TEXT_SECOND_STEP.name!)
				.shouldBeVisible()
				.type('Second step content');

			form.submit();
			form
				.waitForSubmit()
				.shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});
});

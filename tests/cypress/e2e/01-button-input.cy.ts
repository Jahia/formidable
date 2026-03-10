import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	getInputButtonNode,
	INPUT_BUTTON_COMPLETE,
	INPUT_BUTTON_SIMPLE
} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

describe('Button Input Component', () => {
	before(() => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		enableModule(FORMIDABLE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
	});

	beforeEach(() => {
		cy.login();
	});

	afterEach(() => {
		cy.logout();
	});

	describe('Simple Button - Basic attributes only', () => {
		const FORM_NAME = 'simple-button-form';
		const FORM_TITLE = 'Simple Button Form';

		it('should create and display a simple button', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputButtonNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test button with fixture data
			form.getButton(INPUT_BUTTON_SIMPLE.title)
				.shouldBeVisible()
				.shouldHaveText(INPUT_BUTTON_SIMPLE.title);
		});
	});

	describe('Complete Button - All CND attributes', () => {
		const FORM_NAME = 'complete-button-form';
		const FORM_TITLE = 'Complete Button Form';

		it('should create and display button with all attributes', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputButtonNode(INPUT_BUTTON_COMPLETE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test all button attributes with fixture data
			form.getButton(INPUT_BUTTON_COMPLETE.title)
				.shouldBeVisible()
				.shouldHaveText(INPUT_BUTTON_COMPLETE.title)
				.shouldHaveType(INPUT_BUTTON_COMPLETE.buttonType)
				.shouldHaveVariant(INPUT_BUTTON_COMPLETE.variant);
		});
	});
});


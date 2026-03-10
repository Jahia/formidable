import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	getInputCheckboxNode,
	INPUT_CHECKBOX_COMPLETE,
	INPUT_CHECKBOX_SIMPLE
} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

describe('Checkbox Input Component', () => {
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

	describe('Simple Checkbox - Basic attributes only', () => {
		const FORM_NAME = 'simple-checkbox-form';
		const FORM_TITLE = 'Simple Checkbox Form';
		it('should create and display a simple checkbox', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputCheckboxNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test checkbox with fixture data
			form.getCheckbox(INPUT_CHECKBOX_SIMPLE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_CHECKBOX_SIMPLE.title)
				.shouldHaveValue(INPUT_CHECKBOX_SIMPLE.value)
				.shouldNotBeRequired()
				.shouldBeUnchecked();
		});
	});

	describe('Complete Checkbox - All CND attributes', () => {
		const FORM_NAME = 'complete-checkbox-form';
		const FORM_TITLE = 'Complete Checkbox Form';
		it('should create and display checkbox with all attributes', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputCheckboxNode(INPUT_CHECKBOX_COMPLETE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test all checkbox attributes with fixture data
			form.getCheckbox(INPUT_CHECKBOX_COMPLETE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_CHECKBOX_COMPLETE.title)
				.shouldHaveValue(INPUT_CHECKBOX_COMPLETE.value)
				.shouldBeChecked()
				.shouldBeRequired();
		});
	});
});


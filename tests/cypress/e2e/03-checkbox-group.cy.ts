import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	getInputCheckboxGroupNode,
	INPUT_CHECKBOX_GROUP_COMPLETE,
	INPUT_CHECKBOX_GROUP_SIMPLE
} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

describe('Checkbox Group Component', () => {
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

	describe('Simple Checkbox Group - Basic attributes only', () => {
		const FORM_NAME = 'simple-checkbox-group-form';
		const FORM_TITLE = 'Simple Checkbox Group Form';
		it('should create and display a simple checkbox group', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputCheckboxGroupNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test checkbox group with fixture data
			form.getCheckboxGroup(INPUT_CHECKBOX_GROUP_SIMPLE.name)
				.shouldBeVisible()
				.shouldHaveLegend(INPUT_CHECKBOX_GROUP_SIMPLE.title)
				.shouldNotBeRequired();

			// Test checkboxes within the group - not required and unchecked
			const group = form.getCheckboxGroup(INPUT_CHECKBOX_GROUP_SIMPLE.name);
			INPUT_CHECKBOX_GROUP_SIMPLE.checkboxes.forEach(checkbox => {
				group.getCheckbox(checkbox.title)
					.shouldBeVisible()
					.shouldNotBeRequired() //the group enforces the required state
					.shouldBeUnchecked();
			});
		});
	});

	describe('Complete Checkbox Group - All CND attributes', () => {
		const FORM_NAME = 'complete-checkbox-group-form';
		const FORM_TITLE = 'Complete Checkbox Group Form';

		it('should create and display checkbox group with all attributes', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputCheckboxGroupNode(INPUT_CHECKBOX_GROUP_COMPLETE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test all checkbox group attributes with fixture data
			form.getCheckboxGroup(INPUT_CHECKBOX_GROUP_COMPLETE.name)
				.shouldBeVisible()
				.shouldHaveLegend(INPUT_CHECKBOX_GROUP_COMPLETE.title)
				.shouldBeRequired();

			// Test all checkboxes with their specific attributes
			const group = form.getCheckboxGroup(INPUT_CHECKBOX_GROUP_COMPLETE.name);
			INPUT_CHECKBOX_GROUP_COMPLETE.checkboxes.forEach(checkbox => {
				const checkboxElement = group.getCheckbox(checkbox.title);
				checkboxElement.shouldBeVisible()
					.shouldNotBeRequired(); //the group enforces the required state

				if (checkbox.defaultChecked) {
					checkboxElement.shouldBeChecked();
				} else {
					checkboxElement.shouldBeUnchecked();
				}
			});
		});
	});
});


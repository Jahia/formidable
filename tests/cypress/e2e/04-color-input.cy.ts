import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {FORMIDABLE_TEST_SITE, getInputColorNode, INPUT_COLOR_COMPLETE, INPUT_COLOR_SIMPLE} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

describe('Color Input Component', () => {
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

	describe('Simple Color Input - Basic attributes only', () => {
		const FORM_NAME = 'simple-color-form';
		const FORM_TITLE = 'Simple Color Form';
		it('should create and display a simple color input', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputColorNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test color input with fixture data
			form.getColorInput(INPUT_COLOR_SIMPLE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_COLOR_SIMPLE.title)
				.shouldBeColorInput()
				.shouldNotBeRequired();
		});
	});

	describe('Complete Color Input - All CND attributes', () => {
		const FORM_NAME = 'complete-color-form';
		const FORM_TITLE = 'Complete Color Form';
		it('should create and display color input with all attributes', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputColorNode(INPUT_COLOR_COMPLETE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test all color input attributes with fixture data
			form.getColorInput(INPUT_COLOR_COMPLETE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_COLOR_COMPLETE.title)
				.shouldBeColorInput()
				.shouldHaveValue(INPUT_COLOR_COMPLETE.defaultValue)
				.shouldBeRequired();
		});
	});

	// TODO : fix flaky test ui reload
	// describe('Color Input - User Interaction', () => {
	// 	const FORM_NAME = 'interactive-color-form';
	// 	const FORM_TITLE = 'Interactive Color Form';
	// 	it('should allow user to change color', () => {
	// 		createFormNode(FORM_NAME, FORM_TITLE, [getInputColorNode()]);
	//
	// 		const form = getFormPreview(FORM_TITLE);
	// 		const colorInput = form.getColorInput(INPUT_COLOR_SIMPLE.name);
	//
	// 		// Change color value
	// 		const newColor = '#00ff00';
	// 		colorInput
	// 			.setColor(newColor)
	// 			.shouldHaveValue(newColor);
	// 	});
	// });
});

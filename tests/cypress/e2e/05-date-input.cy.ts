import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {FORMIDABLE_TEST_SITE, getInputDateNode, INPUT_DATE_COMPLETE, INPUT_DATE_SIMPLE} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

describe('Date Input Component', () => {
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

	describe('Simple Date Input - Basic attributes only', () => {
		const FORM_NAME = 'simple-date-form';
		const FORM_TITLE = 'Simple Date Form';
		it('should create and display a simple date input', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputDateNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test date input with fixture data
			form.getDateInput(INPUT_DATE_SIMPLE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_DATE_SIMPLE.title)
				.shouldBeDateInput()
				.shouldNotBeRequired();
		});
	});

	describe('Complete Date Input - All CND attributes', () => {
		const FORM_NAME = 'complete-date-form';
		const FORM_TITLE = 'Complete Date Form';
		it('should create and display date input with all attributes', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputDateNode(INPUT_DATE_COMPLETE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test all date input attributes with fixture data
			form.getDateInput(INPUT_DATE_COMPLETE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_DATE_COMPLETE.title)
				.shouldBeDateInput()
				.shouldHaveValue(INPUT_DATE_COMPLETE.defaultValue)
				.shouldBeRequired()
				.shouldHaveMin(INPUT_DATE_COMPLETE.min)
				.shouldHaveMax(INPUT_DATE_COMPLETE.max)
				.shouldHaveStep(INPUT_DATE_COMPLETE.step);
		});
	});

	// TODO : fix flaky test ui reload
	// describe('Date Input - User Interaction', () => {
	// 	const FORM_NAME = 'interactive-date-form';
	// 	const FORM_TITLE = 'Interactive Date Form';
	// 	it('should allow user to change date', () => {
	// 		createFormNode(FORM_NAME, FORM_TITLE, [getInputDateNode()]);
	//
	// 		const form = getFormPreview(FORM_TITLE);
	// 		const dateInput = form.getDateInput(INPUT_DATE_SIMPLE.name);
	//
	// 		// Change date value
	// 		const newDate = '2019-09-03';
	// 		dateInput
	// 			.setDate(newDate)
	// 			.shouldHaveValue(newDate);
	// 	});
	// });
});

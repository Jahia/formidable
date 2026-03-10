import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {FORMIDABLE_TEST_SITE, getInputDatetimeLocalNode, INPUT_DATETIME_LOCAL_SIMPLE} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

describe('Datetime Local Input Component', () => {
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

	describe('Simple Datetime Local Input - Basic attributes only', () => {
		const FORM_NAME = 'simple-datetime-form';
		const FORM_TITLE = 'Simple Datetime Form';
		it('should create and display a simple datetime input', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputDatetimeLocalNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test datetime input with fixture data
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_SIMPLE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_DATETIME_LOCAL_SIMPLE.title)
				.shouldBeDateTimeLocalInput()
				.shouldNotBeRequired();
		});
	});

	// TODO : fix flaky test -> timezone
	// describe('Complete Datetime Local Input - All CND attributes', () => {
	// 	const FORM_NAME = 'complete-datetime-form';
	// 	const FORM_TITLE = 'Complete Datetime Form';
	// 	it('should create and display datetime input with all attributes', () => {
	// 		createFormNode(FORM_NAME, FORM_TITLE, [getInputDatetimeLocalNode(INPUT_DATETIME_LOCAL_COMPLETE)]);
	//
	// 		const form = getFormPreview(FORM_TITLE);
	//
	// 		// Test all datetime input attributes with fixture data
	// 		form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name)
	// 			.shouldBeVisible()
	// 			.shouldHaveLabel(INPUT_DATETIME_LOCAL_COMPLETE.title)
	// 			.shouldBeDateTimeLocalInput()
	// 			.shouldHaveValue(INPUT_DATETIME_LOCAL_COMPLETE.defaultValue)
	// 			.shouldBeRequired()
	// 			.shouldHaveMin(INPUT_DATETIME_LOCAL_COMPLETE.min)
	// 			.shouldHaveMax(INPUT_DATETIME_LOCAL_COMPLETE.max)
	// 			.shouldHaveStep(INPUT_DATETIME_LOCAL_COMPLETE.step);
	// 	});
	// });
	//
	// TODO : fix flaky test ui reload
	// describe('Datetime Local Input - User Interaction', () => {
	// 	const FORM_NAME = 'interactive-datetime-form';
	// 	const FORM_TITLE = 'Interactive Datetime Form';
	// 	it('should allow user to change datetime', () => {
	// 		createFormNode(FORM_NAME, FORM_TITLE, [getInputDatetimeLocalNode()]);
	//
	// 		const form = getFormPreview(FORM_TITLE);
	// 		const datetimeInput = form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_SIMPLE.name);
	//
	// 		// Change datetime value
	// 		const newDate = '2019-09-03T11:18:00.000';
	// 		datetimeInput
	// 			.setDateTime(newDate)
	// 			.shouldHaveValue(newDate);
	// 	});
	// });
});

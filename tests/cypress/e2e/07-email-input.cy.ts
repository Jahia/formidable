import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_ID} from '../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	getInputEmailNode,
	INPUT_EMAIL_COMPLETE,
	INPUT_EMAIL_MULTIPLE,
	INPUT_EMAIL_SIMPLE,
	INPUT_EMAIL_WITH_LIST
} from '../support/fixtures';
import {createFormNode, getFormPreview} from "../support/fixtures/forms";

// Test suite for Email Input Component
describe('Email Input Component', () => {
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

	describe('Simple Email Input - Basic attributes only', () => {
		const FORM_NAME = 'simple-email-form';
		const FORM_TITLE = 'Simple Email Form';
		it('should create and display a simple email input', () => {
			// Create form
			createFormNode(FORM_NAME, FORM_TITLE, [getInputEmailNode()]);

			const form = getFormPreview(FORM_TITLE);

			// Test email input with fixture data
			form.getEmailInput(INPUT_EMAIL_SIMPLE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_EMAIL_SIMPLE.title)
				.shouldBeValidEmail()
				.shouldHaveValue(INPUT_EMAIL_SIMPLE.defaultValue)
				.shouldNotBeRequired();
		});
	});

	describe('Complete Email Input - All CND attributes', () => {
		const FORM_NAME = 'complete-email-form';
		const FORM_TITLE = 'Complete Email Form';
		it('should create and display email input with all attributes', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputEmailNode(INPUT_EMAIL_COMPLETE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test all email input attributes with fixture data
			form.getEmailInput(INPUT_EMAIL_COMPLETE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_EMAIL_COMPLETE.title)
				.shouldBeValidEmail()
				.shouldHaveValue(INPUT_EMAIL_COMPLETE.defaultValue)
				.shouldBeRequired()
				.shouldHavePlaceholder(INPUT_EMAIL_COMPLETE.placeholder)
				.shouldHavePattern(INPUT_EMAIL_COMPLETE.pattern)
				.shouldHaveAutocomplete(INPUT_EMAIL_COMPLETE.autocomplete)
				.shouldNotBeMultiple()
				.shouldHaveMinLength(INPUT_EMAIL_COMPLETE.minLength)
				.shouldHaveMaxLength(INPUT_EMAIL_COMPLETE.maxLength);
		});
	});

	describe('Email Input - User Interaction', () => {
		const FORM_NAME = 'interactive-email-form';
		const FORM_TITLE = 'Interactive Email Form';
		it('should allow user to change email', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputEmailNode()]);

			const form = getFormPreview(FORM_TITLE);
			const emailInput = form.getEmailInput(INPUT_EMAIL_SIMPLE.name);

			// Change email value
			const newEmail = 'user@domain.com';
			emailInput
				.typeEmail(newEmail)
				.shouldHaveValue(newEmail);
		});
	});

	describe('Email Input - Multiple attribute', () => {
		const FORM_NAME = 'multiple-email-form';
		const FORM_TITLE = 'Multiple Email Form';
		it('should create and display email input with multiple attribute', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputEmailNode(INPUT_EMAIL_MULTIPLE)]);

			const form = getFormPreview(FORM_TITLE);

			// Test multiple attribute
			form.getEmailInput(INPUT_EMAIL_MULTIPLE.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_EMAIL_MULTIPLE.title)
				.shouldBeValidEmail()
				.shouldBeMultiple()
				.shouldHavePlaceholder(INPUT_EMAIL_MULTIPLE.placeholder);
		});
	});

	describe('Email Input - List attribute with datalist', () => {
		const FORM_NAME = 'email-list-form';
		const FORM_TITLE = 'Email List Form';
		it('should create and display email input with datalist suggestions', () => {
			createFormNode(FORM_NAME, FORM_TITLE, [getInputEmailNode(INPUT_EMAIL_WITH_LIST)]);

			const form = getFormPreview(FORM_TITLE);

			// Test list attribute and datalist
			form.getEmailInput(INPUT_EMAIL_WITH_LIST.name)
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_EMAIL_WITH_LIST.title)
				.shouldBeValidEmail()
				.shouldHaveDatalist()
				.shouldHaveDatalistOptions(INPUT_EMAIL_WITH_LIST.list);
		});
	});
});

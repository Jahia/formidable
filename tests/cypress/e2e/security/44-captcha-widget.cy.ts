import {createPublishedLiveFormPage, getInputTextNode, visitLiveForm} from '../../support/fixtures';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	useFormidableSite,
	withSameOriginHeaders
} from './support';

// Google's public reCAPTCHA v2 test key pair, documented for automated tests
// (https://developers.google.com/recaptcha/docs/faq): the checkbox always renders
// (with a "testing purposes only" banner) and siteverify accepts any non-empty token.
const TEST_SITE_KEY = '6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI';
const TEST_SECRET_KEY = '6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe';

const CONFIG_PID = 'org.jahia.modules.formidable';

const CAPTCHA_TEST_CONFIG: Record<string, string> = {
	captchaSiteKey: TEST_SITE_KEY,
	captchaSecretKey: TEST_SECRET_KEY,
	captchaScriptUrl: 'https://www.google.com/recaptcha/api.js',
	captchaWidgetVar: 'grecaptcha',
	captchaTokenField: 'g-recaptcha-response',
	captchaVerifyUrl: 'https://www.google.com/recaptcha/api/siteverify'
};

// Shipped defaults: CAPTCHA not configured. Restored so the other security specs
// keep asserting the unconfigured behavior.
const CAPTCHA_EMPTY_CONFIG: Record<string, string> = Object.fromEntries(
	Object.keys(CAPTCHA_TEST_CONFIG).map(key => [key, ''])
);

const editCaptchaConfig = (properties: Record<string, string>) => {
	cy.runProvisioningScript({
		script: {
			fileContent: JSON.stringify([{editConfiguration: CONFIG_PID, properties}]),
			type: 'application/json'
		}
	});
};

describe('Security - CAPTCHA widget and verification with the Google reCAPTCHA v2 test keys', () => {
	useFormidableSite();

	let captchaFormId: string;
	let captchaLivePath: string;

	before(() => {
		cy.login();

		editCaptchaConfig(CAPTCHA_TEST_CONFIG);

		createPublishedLiveFormPage(
			'captcha-widget-form',
			'Captcha widget form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'captcha-widget-form-page',
			'Captcha widget form page',
			{mixins: ['fmdbmix:captcha']}
		).then(({formId, livePath}) => {
			captchaFormId = formId;
			captchaLivePath = livePath;
		});

		cy.logout();
	});

	after(() => {
		cy.login();
		editCaptchaConfig(CAPTCHA_EMPTY_CONFIG);
		cy.logout();
	});

	it('renders the CAPTCHA checkbox on the live page although the provider API loads asynchronously', () => {
		cy.logout();
		visitLiveForm(captchaLivePath);

		// The provider script must opt out of auto-rendering: the widget is
		// rendered explicitly by the client component.
		cy.get('script[src*="recaptcha/api.js"]')
			.should('have.attr', 'src')
			.and('include', 'render=explicit');

		// Google's api.js only exposes grecaptcha.render once a second script has
		// loaded asynchronously. Regression guard: the widget used to never appear
		// when hydration completed before that second script (cold cache).
		cy.get('.fmdb-captcha iframe[src*="recaptcha"]', {timeout: 10000})
			.should('be.visible');
	});

	it('accepts a direct submission whose token the provider verifies', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: captchaFormId,
			fields: {fullName: 'Alice'},
			headers: withSameOriginHeaders({'X-Formidable-Captcha-Token': 'cypress-test-token'})
		}).then(response => {
			expectSuccessResponse(response);
		});
	});

	it('rejects a direct submission without a token even though CAPTCHA is fully configured', () => {
		cy.logout();

		// Blank tokens are rejected engine-side before any provider call, so this
		// stays deterministic even with the always-accepting test secret.
		postDirectMultipartSubmission({
			formId: captchaFormId,
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 400, 'FMDB-006');
		});
	});
});

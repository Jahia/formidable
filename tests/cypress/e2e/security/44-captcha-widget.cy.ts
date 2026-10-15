import {createPublishedLiveFormPage, FORMIDABLE_TEST_SITE, getInputTextNode, visitLiveForm} from '../../support/fixtures';
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

const CONFIG_PID = 'org.jahia.modules.formidable.captcha';

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

type AuthoringLine = {state: string | null; alert: boolean; text: string};

describe('Security - CAPTCHA widget and verification with the Google reCAPTCHA v2 test keys', () => {
	useFormidableSite();

	let captchaFormId: string;
	let captchaLivePath: string;

	/**
	 * The captcha line of the form as the Page Builder renders it, once it shows the state expected: a
	 * configuration change reaches the engine's service asynchronously, so the page is read until it does.
	 */
	const authoringCaptchaLine = (expectedState: string | null): Cypress.Chainable<AuthoringLine> =>
		cy.waitUntil(() =>
			cy.request(`/cms/editframe/default/en/sites/${FORMIDABLE_TEST_SITE.key}/${captchaLivePath}`).its('body').then((body: string) => {
				const line = new DOMParser().parseFromString(body, 'text/html').querySelector('[data-fmdb-authoring="captcha"]');
				if (!line || line.getAttribute('data-fmdb-authoring-state') !== expectedState) {
					return false;
				}
				return {state: expectedState, alert: line.classList.contains('fmdb-authoring-captcha-alert'), text: line.textContent ?? ''};
			}),
		{timeout: 20000, interval: 1000, errorMsg: `the captcha line never showed the state ${expectedState ?? 'set up'}`}
		) as unknown as Cypress.Chainable<AuthoringLine>;

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

	it('shows a line in place of the widget while authoring, and loads no provider script there', () => {
		cy.login();
		cy.request(`/cms/editframe/default/en/sites/${FORMIDABLE_TEST_SITE.key}/${captchaLivePath}`).its('body').then((body: string) => {
			const page = new DOMParser().parseFromString(body, 'text/html');
			const line = page.querySelector('[data-fmdb-authoring="captcha"]');
			expect(line, 'the captcha line').not.to.equal(null);
			expect(line?.textContent).to.contain('Captcha enabled').and.to.contain('Shown in preview and live');
			expect(line?.getAttribute('data-fmdb-authoring-state'), 'a set-up captcha has no state').to.equal(null);
			expect(page.querySelector('[data-fmdb-authoring="sign-in"]'), 'a form open to anonymous visitors has no sign-in line').to.equal(null);
			expect(page.querySelectorAll('script[src*="recaptcha/api.js"]'), 'the provider script').to.have.length(0);
			// the island receives no captcha either: its props never name the site key
			expect(body).not.to.contain(TEST_SITE_KEY);
		});
		cy.logout();
	});

	it('warns the contributor while authoring when the captcha is not set up on the instance, and sets the line back once it is', () => {
		cy.login();
		// nothing set: the line turns loud, telling the contributor the form cannot be sent and whom to tell
		editCaptchaConfig(CAPTCHA_EMPTY_CONFIG);
		authoringCaptchaLine('unconfigured').then(line => {
			expect(line.alert, 'the alert class').to.equal(true);
			expect(line.text).to.contain('Captcha enabled').and.to.contain('Tell your administrator');
		});
		// the widget set but not its verification: every submission would be refused, the same warning
		editCaptchaConfig({...CAPTCHA_TEST_CONFIG, captchaSecretKey: '', captchaVerifyUrl: ''});
		authoringCaptchaLine('unconfigured').its('alert').should('equal', true);
		// set up end to end: the plain line again
		editCaptchaConfig(CAPTCHA_TEST_CONFIG);
		authoringCaptchaLine(null).then(line => {
			expect(line.alert, 'the alert class').to.equal(false);
			expect(line.text).to.contain('Shown in preview and live');
		});
		cy.logout();
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

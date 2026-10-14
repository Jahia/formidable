import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';

/**
 * What the accounts specs (86 to 88) share: the reCAPTCHA v2 test keys the CAPTCHA specs use, the accounts
 * configuration, the guest's submission, and the two questions asked of the platform afterwards ("who am I?",
 * "does this user exist?"). A spec starts with useFormidableSite() and calls cy.logout() before it posts as a guest.
 */

// Google's public reCAPTCHA v2 test key pair (https://developers.google.com/recaptcha/docs/faq): siteverify accepts any
// non-empty token, so a direct submission passes the CAPTCHA step with this header.
const CAPTCHA_PID = 'org.jahia.modules.formidable.captcha';
export const ACCOUNTS_PID = 'org.jahia.modules.formidable.accounts';
export const CAPTCHA_TEST_CONFIG: Record<string, string> = {
	captchaSiteKey: '6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI',
	captchaSecretKey: '6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe',
	captchaScriptUrl: 'https://www.google.com/recaptcha/api.js',
	captchaWidgetVar: 'grecaptcha',
	captchaTokenField: 'g-recaptcha-response',
	captchaVerifyUrl: 'https://www.google.com/recaptcha/api/siteverify'
};
const CAPTCHA_EMPTY_CONFIG: Record<string, string> = Object.fromEntries(Object.keys(CAPTCHA_TEST_CONFIG).map(key => [key, '']));

/** The shipped defaults of org.jahia.modules.formidable.accounts.cfg, with the address limit raised for the suites. */
export const ACCOUNTS_DEFAULTS: Record<string, string> = {
	requireCaptcha: 'true',
	maxAttemptsPerAddress: '1000',
	addressWindowSeconds: '600',
	maxAttemptsPerAccount: '5',
	accountWindowSeconds: '900',
	deniedGroups: 'administrators;privileged;site-administrators;site-privileged;guest;users',
	publicBaseUrl: '',
	mailFrom: ''
};

export const editConfiguration = (pid: string, properties: Record<string, string>): void => {
	cy.runProvisioningScript({
		script: {
			fileContent: JSON.stringify([{editConfiguration: pid, properties}]),
			type: 'application/json'
		}
	});
};

/** Turns the test CAPTCHA on and the accounts configuration to its defaults (address limit raised). Call as root, in before(). */
export const configureAccounts = (overrides: Record<string, string> = {}): void => {
	editConfiguration(CAPTCHA_PID, CAPTCHA_TEST_CONFIG);
	editConfiguration(ACCOUNTS_PID, {...ACCOUNTS_DEFAULTS, ...overrides});
};

/** Back to what the other specs expect: no CAPTCHA configured, the accounts defaults as shipped. Call as root, in after(). */
export const restoreAccounts = (): void => {
	editConfiguration(CAPTCHA_PID, CAPTCHA_EMPTY_CONFIG);
	editConfiguration(ACCOUNTS_PID, {...ACCOUNTS_DEFAULTS, maxAttemptsPerAddress: '20'});
};

export const CAPTCHA_HEADERS = {'X-Formidable-Captcha-Token': 'cypress-test-token'};

interface Rejection {
	success: boolean;
	errorCode?: string;
	userMessage?: string;
	actionsCompleted?: number;
	actionsTotal?: number;
	redirect?: string;
}

/** Posts a submission as a guest, from the site's own origin, with a CAPTCHA token the test keys accept. */
export const submitAsGuest = (formId: string, fields: Record<string, string>, headers: Record<string, string> = {}): Cypress.Chainable<Cypress.Response<Rejection>> => {
	cy.logout();
	return postDirectMultipartSubmission({
		formId,
		fields,
		headers: withSameOriginHeaders({...CAPTCHA_HEADERS, ...headers})
	}) as Cypress.Chainable<Cypress.Response<Rejection>>;
};

/** The same, from a page whose address is `referer` (a path under the site's origin): the page a sign-in form sits on. */
export const submitFrom = (formId: string, fields: Record<string, string>, refererPath: string): Cypress.Chainable<Cypress.Response<Rejection>> =>
	submitAsGuest(formId, fields, {Referer: `${SAME_ORIGIN}${refererPath}`});

/** The name of the user the current session belongs to ("guest" for a visitor). The cookies of the last request are the session. */
export const currentUserName = (): Cypress.Chainable<string> => cy.request({
	method: 'POST',
	url: '/modules/graphql',
	headers: {Origin: SAME_ORIGIN},
	body: {query: '{ currentUser { name } }'}
}).then(response => response.body.data.currentUser.name as string);

/** Whether a user of that name exists, asked as root (cy.login()). */
export const userExists = (username: string): Cypress.Chainable<boolean> => cy.request({
	method: 'POST',
	url: '/modules/graphql',
	headers: {Origin: SAME_ORIGIN},
	body: {
		query: `query($q: String!) { jcr(workspace: EDIT) { nodesByQuery(query: $q) { nodes { path } } } }`,
		variables: {q: `SELECT * FROM [jnt:user] AS u WHERE LOCALNAME(u) = '${username.replace(/'/g, "''")}'`}
	}
}).then(response => (response.body.data.jcr.nodesByQuery.nodes as unknown[]).length > 0);

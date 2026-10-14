import {deleteUser} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	getCreateAccountActionNode,
	getInputEmailNode,
	getInputTextNode,
	getPasswordNode
} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';
import {configureAccounts, currentUserName, editConfiguration, ACCOUNTS_DEFAULTS, ACCOUNTS_PID, restoreAccounts, submitAsGuest, userExists} from '../support/accounts';

/**
 * The Create account action (formidable-accounts, docs/administration/how-to-accounts-actions.md), through direct
 * submissions with the reCAPTCHA test keys: the visitor is registered and signed in by Jahia's own login, a taken
 * address and every other failure read one sentence, the password has a sentence of its own, a form without
 * CAPTCHA is refused, and an address that keeps trying is stopped.
 */
describe('Actions - 86 Create account', () => {
	useFormidableSite();

	const USERNAME = 'cypress-new-visitor@example.com';
	const PASSWORD = 'Cypress-Pass1!';
	const fields = (email: string = USERNAME, password: string = PASSWORD) => ({email, password, first: 'Ada'});

	let signUpFormId: string;
	let brokenGroupFormId: string;
	let noCaptchaFormId: string;

	const signUpFields = () => [
		getInputEmailNode({name: 'email', title: 'E-mail', required: true}),
		getPasswordNode({name: 'password', title: 'Password'}),
		getInputTextNode({name: 'first', title: 'First name'})
	];

	before(() => {
		cy.login();
		configureAccounts();

		createPublishedLiveFormPage('accounts-signup-form', 'Accounts sign-up form', signUpFields(), undefined, undefined, {
			mixins: ['fmdbmix:captcha'],
			actions: [getCreateAccountActionNode({firstNameField: 'first'})]
		}).then(({formId}) => {
			signUpFormId = formId;
		});
		createPublishedLiveFormPage('accounts-signup-broken-group-form', 'Accounts sign-up, group that does not exist', signUpFields(), undefined, undefined, {
			mixins: ['fmdbmix:captcha'],
			actions: [getCreateAccountActionNode({firstNameField: 'first', group: 'no-such-group'})]
		}).then(({formId}) => {
			brokenGroupFormId = formId;
		});
		createPublishedLiveFormPage('accounts-signup-no-captcha-form', 'Accounts sign-up without CAPTCHA', signUpFields(), undefined, undefined, {
			actions: [getCreateAccountActionNode({firstNameField: 'first'})]
		}).then(({formId}) => {
			noCaptchaFormId = formId;
		});
		cy.logout();
	});

	after(() => {
		cy.login();
		deleteUser(USERNAME);
		restoreAccounts();
		cy.logout();
	});

	it('registers the visitor and signs them in through the platform', () => {
		submitAsGuest(signUpFormId, fields()).then(response => {
			expect(response.status).to.eq(200);
			// the action asked for no redirect: the body is the plain success, nothing else
			expect(response.body).to.deep.equal({success: true});
		});

		// the cookies of that submission are the visitor's session: Jahia says who they are now
		currentUserName().should('equal', USERNAME);
		cy.logout();
		cy.login();
		userExists(USERNAME).should('equal', true);
	});

	it('answers a taken address and a failure of another kind with the same sentence', () => {
		let taken: string | undefined;
		submitAsGuest(signUpFormId, fields()).then(response => {
			expect(response.status).to.eq(422);
			expect(response.body.errorCode).to.eq('FMDB-018');
			taken = response.body.userMessage;
			expect(taken, 'a sentence is shown to the visitor').to.be.a('string').and.not.be.empty;
		});
		// a group that does not exist is another cause of failure, for an address nobody has: the sentence must not tell
		submitAsGuest(brokenGroupFormId, fields('cypress-other-visitor@example.com')).then(response => {
			expect(response.status).to.eq(422);
			expect(response.body.errorCode).to.eq('FMDB-018');
			expect(response.body.userMessage).to.eq(taken);
		});
		cy.login();
		userExists('cypress-other-visitor@example.com').should('equal', false);
	});

	it('gives a password that breaks the rule its own sentence, the same whether the address is taken or not', () => {
		let generic: string | undefined;
		submitAsGuest(signUpFormId, fields()).then(response => {
			generic = response.body.userMessage;
		});
		submitAsGuest(signUpFormId, fields(USERNAME, 'weak')).then(takenResponse => {
			expect(takenResponse.status).to.eq(422);
			expect(takenResponse.body.userMessage).to.not.eq(generic);
			submitAsGuest(signUpFormId, fields('cypress-weak-visitor@example.com', 'weak')).then(freeResponse => {
				expect(freeResponse.body.userMessage).to.eq(takenResponse.body.userMessage);
			});
		});
		cy.login();
		userExists('cypress-weak-visitor@example.com').should('equal', false);
	});

	it('refuses a form without CAPTCHA, answers that it is unavailable and creates nothing', () => {
		submitAsGuest(noCaptchaFormId, fields('cypress-no-captcha@example.com')).then(response => {
			expect(response.status).to.eq(503);
			expect(response.body.errorCode).to.eq('FMDB-018');
			expect(response.body.userMessage).to.be.a('string').and.not.be.empty;
		});
		cy.login();
		userExists('cypress-no-captcha@example.com').should('equal', false);
	});

	it('never carries the password in an answer, accepted or refused', () => {
		submitAsGuest(signUpFormId, fields(USERNAME, PASSWORD)).then(response => {
			expect(JSON.stringify(response.body)).to.not.contain(PASSWORD);
			expect(JSON.stringify(response.headers)).to.not.contain(PASSWORD);
		});
		submitAsGuest(signUpFormId, fields('cypress-echo@example.com', 'weak-Passw0rd')).then(response => {
			expect(JSON.stringify(response.body)).to.not.contain('weak-Passw0rd');
		});
	});

	it('stops an address that keeps trying', () => {
		cy.login();
		editConfiguration(ACCOUNTS_PID, {...ACCOUNTS_DEFAULTS, maxAttemptsPerAddress: '1'});
		cy.logout();
		// the earlier tests of this file already used this address's attempts: the next one is over the limit
		submitAsGuest(signUpFormId, fields('cypress-flood@example.com')).then(response => {
			expect(response.status).to.eq(429);
			expect(response.body.errorCode).to.eq('FMDB-018');
			expect(response.body.userMessage).to.be.a('string').and.not.be.empty;
		});
		cy.login();
		editConfiguration(ACCOUNTS_PID, ACCOUNTS_DEFAULTS);
		userExists('cypress-flood@example.com').should('equal', false);
	});
});

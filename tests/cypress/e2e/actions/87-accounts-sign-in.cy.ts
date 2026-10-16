import {createUser, deleteUser, grantRoles} from '@jahia/cypress';
import {FORMIDABLE_TEST_SITE, createPublishedLiveFormPage, getInputTextNode, getPasswordNode, getSignInActionNode} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';
import {configureAccounts, currentUserName, restoreAccounts, submitAsGuest, submitFrom} from '../support/accounts';

/**
 * The Sign in action (formidable-accounts, docs/administration/how-to-accounts-actions.md): a real Jahia login from a
 * form (the visitor is who Jahia says they are afterwards), a return path the page names is followed only when it is a
 * path of the site, every cause of failure reads exactly the same, an account that keeps failing is stopped, and a
 * back-office account never signs in through a form (jahia-private#5909: that path would skip the platform login's MFA).
 */
describe('Actions - 87 Sign in', () => {
	useFormidableSite();

	const USER = 'cypress-member@example.com';
	const LOCKED_OUT_USER = 'cypress-lockout@example.com';
	const STAFF_USER = 'cypress-staff@example.com';
	const PASSWORD = 'Cypress-Pass1!';
	const SITE_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}`;
	let signInFormId: string;

	before(() => {
		cy.login();
		configureAccounts();
		createUser(USER, PASSWORD);
		createUser(LOCKED_OUT_USER, PASSWORD);
		createUser(STAFF_USER, PASSWORD);
		grantRoles(SITE_PATH, ['editor'], STAFF_USER, 'USER');
		createPublishedLiveFormPage('accounts-sign-in-form', 'Accounts sign-in form', [
			getInputTextNode({name: 'login', title: 'E-mail or user name', required: true}),
			getPasswordNode({name: 'password', title: 'Password', autocompleteMode: 'current-password'})
		], undefined, undefined, {
			mixins: ['fmdbmix:captcha'],
			actions: [getSignInActionNode({redirectParameter: 'redirect'})]
		}).then(({formId}) => {
			signInFormId = formId;
		});
		cy.logout();
	});

	after(() => {
		cy.login();
		deleteUser(USER);
		deleteUser(LOCKED_OUT_USER);
		deleteUser(STAFF_USER);
		restoreAccounts();
		cy.logout();
	});

	it('signs a visitor in through the platform, by e-mail in any case', () => {
		submitAsGuest(signInFormId, {login: 'Cypress-Member@Example.com', password: PASSWORD}).then(response => {
			expect(response.status).to.eq(200);
			expect(response.body).to.deep.equal({success: true});
		});

		currentUserName().should('equal', USER);
	});

	it('sends the visitor back to where they were going when the page names a path of the site', () => {
		const target = `${SITE_PATH}/home/members.html`;
		submitFrom(signInFormId, {login: USER, password: PASSWORD}, `/en${SITE_PATH}/home/login.html?redirect=${encodeURIComponent(target)}`).then(response => {
			expect(response.status).to.eq(200);
			expect(response.body).to.deep.equal({success: true, redirect: target});
		});
	});

	it('never follows a return path that names another site', () => {
		for (const hostile of ['https://evil.example/phish', '//evil.example/phish', '/\\evil.example', 'javascript:alert(1)']) {
			submitFrom(signInFormId, {login: USER, password: PASSWORD}, `/en${SITE_PATH}/home/login.html?redirect=${encodeURIComponent(hostile)}`).then(response => {
				expect(response.status, hostile).to.eq(200);
				expect(response.body, hostile).to.deep.equal({success: true});
			});
		}
	});

	it('reads the same for an unknown account, a wrong password and a name that cannot be one', () => {
		let reference: Cypress.Response<unknown> | undefined;
		submitAsGuest(signInFormId, {login: USER, password: 'Wrong-Password1!'}).then(response => {
			reference = response;
			expect(response.status).to.eq(422);
			expect(response.body.errorCode).to.eq('FMDB-018');
			expect(response.body.userMessage).to.be.a('string').and.not.be.empty;
			expect(response.body.actionsCompleted).to.eq(0);
		});
		for (const attempt of [
			{login: 'cypress-nobody@example.com', password: 'Wrong-Password1!'},
			{login: 'not a valid name!', password: 'Wrong-Password1!'},
			{login: 'root', password: 'root'}
		]) {
			submitAsGuest(signInFormId, attempt).then(response => {
				expect(response.status, attempt.login).to.eq(reference?.status);
				expect(response.body, attempt.login).to.deep.equal(reference?.body);
			});
		}
		// none of them left a session behind
		currentUserName().should('equal', 'guest');
	});

	it('refuses a back-office account like a wrong password and leaves no session', () => {
		submitAsGuest(signInFormId, {login: USER, password: 'Wrong-Password1!'}).then(reference => {
			submitAsGuest(signInFormId, {login: STAFF_USER, password: PASSWORD}).then(response => {
				expect(response.status).to.eq(reference.status);
				expect(response.body).to.deep.equal(reference.body);
			});
		});
		currentUserName().should('equal', 'guest');
	});

	it('stops an account that keeps failing, an unknown one exactly like a real one, even for the right password', () => {
		const wrong = {password: 'Wrong-Password1!'};
		for (let attempt = 0; attempt < 5; attempt++) {
			submitAsGuest(signInFormId, {login: LOCKED_OUT_USER, ...wrong}).its('status').should('eq', 422);
			submitAsGuest(signInFormId, {login: 'cypress-ghost@example.com', ...wrong}).its('status').should('eq', 422);
		}
		submitAsGuest(signInFormId, {login: LOCKED_OUT_USER, password: PASSWORD}).then(real => {
			expect(real.status).to.eq(429);
			expect(real.body.errorCode).to.eq('FMDB-018');
			submitAsGuest(signInFormId, {login: 'cypress-ghost@example.com', password: PASSWORD}).then(ghost => {
				expect(ghost.status).to.eq(429);
				expect(ghost.body).to.deep.equal(real.body);
			});
		});
		currentUserName().should('equal', 'guest');
	});
});

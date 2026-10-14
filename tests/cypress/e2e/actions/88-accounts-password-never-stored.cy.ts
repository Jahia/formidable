import {deleteUser} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	getCreateAccountActionNode,
	getInputEmailNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getPasswordNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {SAME_ORIGIN} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {configureAccounts, restoreAccounts, submitAsGuest} from '../support/accounts';

/**
 * A password field is a sensitive field (fmdbmix:sensitiveField): its value reaches the actions that consume it and
 * nothing else. This spec puts it next to Save to JCR on one form, and alone with Save to JCR on another, and reads
 * what was stored and what came back: the password is nowhere, whatever the actions on the form are. (The e-mail
 * actions, forward and the jExperience block receive the same public values as Save to JCR; their unit tests assert it.)
 */
describe('Actions - 88 A password is never stored nor echoed', () => {
	useFormidableSite();

	const USERNAME = 'cypress-secret-visitor@example.com';
	const PASSWORD = 'Never-Stored-Pass1!';

	before(() => {
		cy.login();
		configureAccounts();
		cy.logout();
	});

	after(() => {
		cy.login();
		deleteUser(USERNAME);
		restoreAccounts();
		cy.logout();
	});

	/** The stored properties of the latest submission of a form, by name: what Save to JCR kept. */
	const storedValues = (formName: string): Cypress.Chainable<Record<string, string>> => getLatestLiveFormSubmission(formName).then(({path}) => cy.request({
		method: 'POST',
		url: '/modules/graphql',
		headers: {Origin: SAME_ORIGIN},
		body: {
			query: `query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { properties { name value } } } }`,
			variables: {path: `${path}/data`}
		}
	})).then(response => {
		const properties = response.body.data.jcr.nodeByPath.properties as Array<{name: string; value: string}>;
		return Object.fromEntries(properties.map(property => [property.name, property.value]));
	});

	it('is stored by nothing when Save to JCR sits next to the account action, and still creates the account', () => {
		cy.login();
		createPublishedLiveFormPage('accounts-password-with-account-form', 'Password next to the account action', [
			getInputEmailNode({name: 'email', title: 'E-mail', required: true}),
			getPasswordNode({name: 'password', title: 'Password'}),
			getInputTextNode({name: 'nickname', title: 'Nickname'})
		], undefined, undefined, {
			mixins: ['fmdbmix:captcha'],
			// Save to JCR first: it runs before the account action and receives every value it is given
			actions: [getSaveToJcrActionNode(), getCreateAccountActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, {email: USERNAME, password: PASSWORD, nickname: 'ada'}).then(response => {
				expect(response.status).to.eq(200);
				expect(JSON.stringify(response.body)).to.not.contain(PASSWORD);
			});
		});

		cy.login();
		storedValues('accounts-password-with-account-form').then(values => {
			// what the form asked to keep is there...
			expect(values.email).to.eq(USERNAME);
			expect(values.nickname).to.eq('ada');
			// ...and the password is neither a property nor a value of one
			expect(Object.keys(values)).to.not.include('password');
			expect(Object.values(values)).to.not.include(PASSWORD);
			expect(JSON.stringify(values)).to.not.contain(PASSWORD);
		});
	});

	it('is stored by nothing when no action consumes it', () => {
		cy.login();
		createPublishedLiveFormPage('accounts-password-alone-form', 'Password with Save to JCR only', [
			getInputTextNode({name: 'nickname', title: 'Nickname'}),
			getPasswordNode({name: 'password', title: 'Password'})
		], undefined, undefined, {
			actions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			cy.logout();
			submitAsGuest(formId, {nickname: 'grace', password: PASSWORD}, {}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.body).to.deep.equal({success: true});
			});
		});

		cy.login();
		storedValues('accounts-password-alone-form').then(values => {
			expect(values.nickname).to.eq('grace');
			expect(JSON.stringify(values)).to.not.contain(PASSWORD);
			expect(Object.keys(values)).to.not.include('password');
		});
	});
});

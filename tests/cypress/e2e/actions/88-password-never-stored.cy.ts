import {
	createPublishedLiveFormPage,
	getInputEmailNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getPasswordNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A password field is a sensitive field (fmdbmix:sensitiveField): its value reaches only an action that declares it
 * consumes sensitive values (a contextual action of another module, a sign-up for instance) and nothing else. This spec
 * puts it next to Save to JCR, which receives every public value, and reads what was stored and what came back: the
 * password is nowhere. (The e-mail actions, forward and the jExperience block receive the same public values as Save
 * to JCR; their unit tests assert it.)
 */
describe('Actions - 88 A password is never stored nor echoed', () => {
	useFormidableSite();

	const PASSWORD = 'Never-Stored-Pass1!';

	/** Posts a submission as a guest, from the site's own origin. */
	const submitAsGuest = (formId: string, fields: Record<string, string>) => {
		cy.logout();
		return postDirectMultipartSubmission({formId, fields, headers: withSameOriginHeaders({})});
	};

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

	for (const [name, title, fields, values] of [
		['password-alone-form', 'Password with Save to JCR only', [
			getInputTextNode({name: 'nickname', title: 'Nickname'}),
			getPasswordNode({name: 'password', title: 'Password'})
		], {nickname: 'grace'}],
		['password-with-email-form', 'Password next to an e-mail and a text', [
			getInputEmailNode({name: 'email', title: 'E-mail', required: true}),
			getPasswordNode({name: 'password', title: 'Password'}),
			getInputTextNode({name: 'nickname', title: 'Nickname'})
		], {email: 'cypress-secret-visitor@example.com', nickname: 'ada'}]
	] as const) {
		it(`is stored by nothing and echoed by nothing (${title})`, () => {
			cy.login();
			createPublishedLiveFormPage(name, title, [...fields], undefined, undefined, {
				actions: [getSaveToJcrActionNode()]
			}).then(({formId}) => {
				submitAsGuest(formId, {...values, password: PASSWORD}).then(response => {
					expect(response.status).to.eq(200);
					expect(response.body).to.deep.equal({success: true});
					expect(JSON.stringify(response.body)).to.not.contain(PASSWORD);
				});
			});

			cy.login();
			storedValues(name).then(stored => {
				// what the form asked to keep is there...
				for (const [field, value] of Object.entries(values)) {
					expect(stored[field], field).to.eq(value);
				}
				// ...and the password is neither a property nor a value of one
				expect(Object.keys(stored)).to.not.include('password');
				expect(JSON.stringify(stored)).to.not.contain(PASSWORD);
			});
		});
	}
});

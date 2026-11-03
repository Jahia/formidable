import {
	createPublishedLiveFormPage,
	getInputEmailNode,
	getInputTextNode,
	getLatestLiveSubmissionValues,
	getPasswordNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A password field is a sensitive field (fmdbmix:sensitiveField): its value reaches only an action that opts in to
 * sensitive values (a contextual action whose sensitiveFieldsConsumed returns SensitiveFields.all(), a sign-up for
 * instance) and nothing else. This spec puts it next to Save to JCR, which receives every public value, and reads what
 * was stored and what came back: the password is nowhere. (The e-mail actions, forward and the jExperience block
 * receive the same public values as Save to JCR; their unit tests assert it. Spec 85 puts a consumer on the form.)
 */
describe('Actions - 88 A password is never stored nor echoed', () => {
	useFormidableSite();

	const PASSWORD = 'Never-Stored-Pass1!';

	/** Posts a submission as a guest, from the site's own origin. */
	const submitAsGuest = (formId: string, fields: Record<string, string>) => {
		cy.logout();
		return postDirectMultipartSubmission({formId, fields, headers: withSameOriginHeaders({})});
	};

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
			createPublishedLiveFormPage(name, title, [...fields], undefined, undefined, {
				actions: [getSaveToJcrActionNode()]
			}).then(({formId}) => {
				submitAsGuest(formId, {...values, password: PASSWORD}).then(response => {
					expect(response.status).to.eq(200);
					expect(response.body).to.deep.equal({success: true});
					expect(JSON.stringify(response.body)).to.not.contain(PASSWORD);
					expect(JSON.stringify(response.headers)).to.not.contain(PASSWORD);
				});
			});

			cy.login();
			getLatestLiveSubmissionValues(name).then(stored => {
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

import {
	SENSITIVE_CONSUMER_HEADERS,
	SENSITIVE_NON_CONSUMER_HEADERS,
	createPublishedLiveFormPage,
	getInputTextNode,
	getLatestLiveSubmissionValues,
	getPasswordNode,
	getSaveToJcrActionNode,
	getSensitiveConsumerActionNode,
	getSensitiveNonConsumerActionNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * The one reader of a sensitive value (docs/architecture/form-submission-flow.md, "Sensitive fields"): a contextual
 * action that opts in from its Java class, sensitiveFieldsConsumed returning SensitiveFields.all(). One form holds a
 * password and three actions: Save to JCR, a contextual action that keeps the default (it consumes nothing), and one
 * that opts in. The two samples of formidable-test-module-samples-java report what they were handed in response
 * headers, never a value: the consumer, the number of sensitive fields and whether the password held a value; the
 * non-consumer, the number of sensitive fields and the names of its public fields. The consumer sees the password,
 * the non-consumer and Save to JCR do not, and nothing that comes back holds it.
 */
describe('Actions - 85 A password reaches the action that consumes it, and nothing else', () => {
	useFormidableSite();

	const PASSWORD = 'Consumed-Pass-42!';

	it('hands the password to the consumer only: the non-consumer and Save to JCR on the same form never see it', () => {
		const formName = 'sensitive-consumer-form';
		createPublishedLiveFormPage(formName, 'Password with a consumer', [
			getInputTextNode({name: 'username', title: 'Username'}),
			getPasswordNode({name: 'password', title: 'Password'})
		], undefined, undefined, {
			actions: [getSaveToJcrActionNode(), getSensitiveNonConsumerActionNode(), getSensitiveConsumerActionNode()]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {username: 'ada', password: PASSWORD},
				headers: withSameOriginHeaders()
			}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.body).to.deep.equal({success: true});

				// The consumer received one sensitive field, the password.
				expect(response.headers[SENSITIVE_CONSUMER_HEADERS.sensitiveFields], 'sensitive fields of the consumer').to.eq('1');
				expect(response.headers[SENSITIVE_CONSUMER_HEADERS.passwordReceived], 'password received by the consumer').to.eq('true');

				// The non-consumer received no sensitive value, and the password is not among its public fields either.
				expect(response.headers[SENSITIVE_NON_CONSUMER_HEADERS.sensitiveFields], 'sensitive fields of the non-consumer').to.eq('0');
				const publicFields = String(response.headers[SENSITIVE_NON_CONSUMER_HEADERS.fields]).split(',');
				expect(publicFields, 'public fields of the non-consumer').to.include('username').and.not.include('password');

				// Nothing that came back holds the password.
				expect(JSON.stringify(response.body)).to.not.contain(PASSWORD);
				expect(JSON.stringify(response.headers)).to.not.contain(PASSWORD);
			});
		});

		// Save to JCR, first in the list, kept the public value and nothing of the password.
		cy.login();
		getLatestLiveSubmissionValues(formName).then(stored => {
			expect(stored.username, 'username').to.eq('ada');
			expect(Object.keys(stored)).to.not.include('password');
			expect(JSON.stringify(stored)).to.not.contain(PASSWORD);
		});
	});

	it('hands the consumer nothing when the password is left empty, and still accepts the submission', () => {
		// The field is optional here. A browser sends an empty password as `password=""`, and the engine hands over only
		// what the visitor typed: the consumer receives no sensitive field, and the submission is accepted.
		createPublishedLiveFormPage('sensitive-consumer-empty-form', 'Optional password with a consumer', [
			getInputTextNode({name: 'username', title: 'Username'}),
			getPasswordNode({name: 'password', title: 'Password', required: false})
		], undefined, undefined, {
			actions: [getSensitiveConsumerActionNode()]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {username: 'ada', password: ''},
				headers: withSameOriginHeaders()
			}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.body).to.deep.equal({success: true});
				expect(response.headers[SENSITIVE_CONSUMER_HEADERS.sensitiveFields], 'sensitive fields of the consumer').to.eq('0');
				expect(response.headers[SENSITIVE_CONSUMER_HEADERS.passwordReceived], 'password received by the consumer').to.eq('false');
			});
		});
	});
});

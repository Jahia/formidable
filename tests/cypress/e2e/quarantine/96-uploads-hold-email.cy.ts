import {
	createPublishedLiveFormPage,
	getEmailContentActionNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {FILE_NAME, fields, setUploadsRequireQuarantine, submissionWithAFile} from './support';

/** How long a mail may take from the action to the mailbox: the mail service queues it. */
const MAIL_DELAY = {timeout: 30000, interval: 500};

/** An address that no earlier attempt wrote to: the mailbox keeps the mails of an earlier run, and of a retry. */
const recipient = (name: string): string => `${name}-${Date.now()}@formidable.test`;

/**
 * The names of the attachments of the one mail sent to the address, and its text. Each attempt of a test sends to an
 * address of its own, so the mail of another test, of another spec or of an earlier run never answers for it.
 */
const mailSentTo = (address: string): Cypress.Chainable<{attachments: string[]; text: string}> => {
	const query = `to:${address}`;
	cy.mailpitHasEmailsBySearch(query, 0, 1, MAIL_DELAY);
	return cy.mailpitSearchEmails(query).then(found => {
		expect(found.messages, `mails sent to ${address}`).to.have.length(1);
		return cy.mailpitGetMail(found.messages[0].ID);
	}).then(mail => ({
		attachments: mail.Attachments.map(attachment => attachment.FileName),
		text: mail.Text
	}));
};

/**
 * The hold of the uploads, as an e-mail action sees it (tests/scenarios/quarantine.md). The e-mail content action is
 * asked to attach the files in every test: what it attaches is what the engine handed it. Mailpit is the mailbox, and
 * the provisioning of @jahia/cypress points the mail service of Jahia at it. The sample hold of the samples module
 * releases a held submission inside the request.
 */
describe('Quarantine - 96 The e-mail of a form carries its uploads only after release', () => {
	useFormidableSite();

	before(() => {
		// Named here, so that a mailbox that does not answer is not read as a mail that was not sent.
		cy.mailpitReady().should('be.true');
		setUploadsRequireQuarantine(false);
	});

	after(() => {
		setUploadsRequireQuarantine(false);
	});

	it('Q06: with the switch on, the e-mail on submission carries no attachment, and the e-mail after release carries it', () => {
		const onSubmission = recipient('q06-on-submission');
		const afterRelease = recipient('q06-after-release');
		createPublishedLiveFormPage('held-mail-form', 'Held mail form', fields(), undefined, undefined, {
			actions: [getEmailContentActionNode({name: 'mailOnSubmission', to: onSubmission, attachFiles: true})],
			deferredActions: [getEmailContentActionNode({name: 'mailAfterRelease', to: afterRelease, attachFiles: true})]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		mailSentTo(onSubmission).then(mail => {
			expect(mail.text, 'values in the mail on submission').to.include('Ada');
			expect(mail.attachments, 'attachments of the mail on submission').to.deep.equal([]);
		});
		mailSentTo(afterRelease).then(mail => {
			expect(mail.attachments, 'attachments of the mail after release').to.deep.equal([FILE_NAME]);
		});
	});

	it('Q14: a form with the switch off still attaches its file to the e-mail', () => {
		const to = recipient('q14-plain');
		createPublishedLiveFormPage('plain-mail-form', 'Plain mail form', fields(), undefined, undefined, {
			actions: [
				getSaveToJcrActionNode(),
				getEmailContentActionNode({to, attachFiles: true})
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Grace'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		mailSentTo(to).then(mail => {
			expect(mail.text, 'values in the mail').to.include('Grace');
			expect(mail.attachments, 'attachments of the mail').to.deep.equal([FILE_NAME]);
		});
	});

	it('Q15: with a mandatory quarantine, the e-mail of a form with the switch off carries no attachment', () => {
		const to = recipient('q15-mandatory');
		setUploadsRequireQuarantine(true);
		createPublishedLiveFormPage('mandatory-mail-form', 'Mandatory mail form', fields(), undefined, undefined, {
			actions: [
				getSaveToJcrActionNode(),
				getEmailContentActionNode({to, attachFiles: true})
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Hedy'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		mailSentTo(to).then(mail => {
			expect(mail.text, 'values in the mail').to.include('Hedy');
			expect(mail.attachments, 'attachments of the mail').to.deep.equal([]);
		});
	});
});

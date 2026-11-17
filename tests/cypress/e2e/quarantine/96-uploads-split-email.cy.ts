import {
	UPLOAD_SPLIT_HEADERS,
	createPublishedLiveFormPage,
	getEmailContentActionNode,
	getSaveToJcrActionNode,
	getUploadQuarantiningActionNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {FILE_NAME, fields, setUploadsRequireQuarantine, submissionWithAFile} from './support';

/** How long a mail may take from the action to the mailbox: the mail service queues it. */
const MAIL_DELAY = {timeout: 30000, interval: 500};

/**
 * The names of the attachments of the one mail sent to the address, and its text. Each test sends to an address of
 * its own, so the mail of another test or of another spec never answers for it.
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
 * The split of the uploads, as an e-mail action sees it (docs/adr/0002, tests/scenarios/quarantine.md). The e-mail
 * content action is asked to attach the files in every test: what it attaches is what the pipeline handed it. Mailpit
 * is the mailbox, and the provisioning of @jahia/cypress points the mail service of Jahia at it.
 */
describe('Quarantine - 96 The e-mail of a form carries its uploads only when no action quarantines them', () => {
	useFormidableSite();

	before(() => {
		// Named here, so that a mailbox that does not answer is not read as a mail that was not sent.
		cy.mailpitReady().should('be.true');
		setUploadsRequireQuarantine(false);
	});

	after(() => {
		setUploadsRequireQuarantine(false);
	});

	it('Q06: on a quarantined form, the e-mail carries the values and no attachment', () => {
		const to = 'q06-quarantined@formidable.test';
		// The e-mail action runs first: the pipeline has not met the quarantining action yet, and still hands it no file.
		createPublishedLiveFormPage('quarantined-mail-form', 'Quarantined mail form', fields(), undefined, undefined, {
			actions: [
				getEmailContentActionNode({to, attachFiles: true}),
				getUploadQuarantiningActionNode()
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.headers[UPLOAD_SPLIT_HEADERS.quarantining], 'files of the quarantining action').to.eq('1');
			});
		});

		mailSentTo(to).then(mail => {
			expect(mail.text, 'values in the mail').to.include('Ada');
			expect(mail.attachments, 'attachments of the mail').to.deep.equal([]);
		});
	});

	it('Q14: a form with no quarantining action still attaches its file to the e-mail', () => {
		const to = 'q14-plain@formidable.test';
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

	it('Q15: with a mandatory quarantine, the e-mail of a form with no quarantining action carries no attachment', () => {
		const to = 'q15-mandatory@formidable.test';
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

import {getInputFileNode, getInputTextNode} from '../../support/fixtures';

const BOUNDARY = '----FormidableCypressBoundary';
export const FILE_FIELD = 'supportingDocument';
export const FILE_NAME = 'claim-note.txt';

/** A submission of the text field and one small text file, as a browser posts it. */
export const submissionWithAFile = (fullName: string): string =>
	`--${BOUNDARY}\r\n`
	+ 'Content-Disposition: form-data; name="fullName"\r\n\r\n'
	+ `${fullName}\r\n`
	+ `--${BOUNDARY}\r\n`
	+ `Content-Disposition: form-data; name="${FILE_FIELD}"; filename="${FILE_NAME}"\r\n`
	+ 'Content-Type: text/plain\r\n\r\n'
	+ 'A note attached to the claim.\r\n'
	+ `--${BOUNDARY}--\r\n`;

/** The fields of every form of the quarantine specs: one text field and one file field. */
export const fields = () => [
	getInputTextNode({name: 'fullName', title: 'Full name'}),
	getInputFileNode({name: FILE_FIELD, title: 'Supporting document'})
];

const editConfiguration = (pid: string, properties: Record<string, string>): Cypress.Chainable => cy.runProvisioningScript({
	script: {
		fileContent: JSON.stringify([{editConfiguration: pid, properties}]),
		type: 'application/json'
	}
});

/** The administrator's gate on development forward targets: the sink is plain HTTP on this instance. */
export const setDevelopmentForwardTargets = (enabled: boolean): Cypress.Chainable =>
	editConfiguration('org.jahia.modules.formidable.formActions', {enableDevForwardTargets: String(enabled)});

/** The operator setting that makes the quarantine mandatory, for every form of the instance. */
export const setUploadsRequireQuarantine = (required: boolean): Cypress.Chainable =>
	editConfiguration('org.jahia.modules.formidable.uploads', {uploadsRequireQuarantine: String(required)});

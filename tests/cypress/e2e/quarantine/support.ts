import {FORWARD_SINK_PATH, QUARANTINE_INSPECTION_PATH, UPLOAD_OBSERVATIONS_PATH} from '../../support/constants';
import {FORMIDABLE_TEST_SITE, FORWARD_SINK_TARGET_ID, getInputFileNode, getInputTextNode} from '../../support/fixtures';
import {SAME_ORIGIN} from '../security/support';

const BOUNDARY = '----FormidableCypressBoundary';
export const FILE_FIELD = 'supportingDocument';
export const FILE_NAME = 'claim-note.txt';
/** The text of the file of submissionWithAFile. */
export const FILE_TEXT = 'A note attached to the claim.';
/** The SHA-256 of FILE_TEXT, computed outside Jahia (`shasum -a 256`). */
export const FILE_SHA256 = 'f7c11b5f5115eb9d0f4f8619af8870daf212b37948739f8534e4088a078e7ffd';

/** A submission of the text field and one small text file, as a browser posts it. */
export const submissionWithAFile = (fullName: string): string =>
	`--${BOUNDARY}\r\n`
	+ 'Content-Disposition: form-data; name="fullName"\r\n\r\n'
	+ `${fullName}\r\n`
	+ `--${BOUNDARY}\r\n`
	+ `Content-Disposition: form-data; name="${FILE_FIELD}"; filename="${FILE_NAME}"\r\n`
	+ 'Content-Type: text/plain\r\n\r\n'
	+ `${FILE_TEXT}\r\n`
	+ `--${BOUNDARY}--\r\n`;

/** A submission of the text field alone: the file field is left empty. */
export const submissionWithNoFile = (fullName: string): string =>
	`--${BOUNDARY}\r\n`
	+ 'Content-Disposition: form-data; name="fullName"\r\n\r\n'
	+ `${fullName}\r\n`
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

/**
 * Declares the forward targets of the instance, the sink alone or none. The targets are instance-global and another
 * spec replaces them all, so a spec declares the one it needs and leaves none behind.
 */
export const setForwardTargets = (targets: Array<Record<string, string>>): Cypress.Chainable =>
	cy.executeGroovy('groovy/replaceFactoryConfigurations.groovy', {
		__FACTORY_PID__: 'org.jahia.modules.formidable.formActions.target',
		__ENTRIES__: JSON.stringify(targets)
	});

/** The sink as a forward target: plain HTTP on the instance itself, hence a development target. */
export const FORWARD_SINK_TARGET = {
	id: FORWARD_SINK_TARGET_ID,
	label: 'Samples forward sink',
	url: `http://localhost:8080${FORWARD_SINK_PATH}`,
	development: 'true'
};

export interface ForwardSinkState {
	count: number;
	fields: string[];
	files: string[];
}

export const resetForwardSink = (): Cypress.Chainable =>
	cy.request({method: 'POST', url: `${FORWARD_SINK_PATH}/reset`, headers: {Origin: SAME_ORIGIN}});

/** What the last forward carried: the number of forwards since the reset, and the names of its value and file parts. */
export const readForwardSink = (): Cypress.Chainable<ForwardSinkState> =>
	cy.request<ForwardSinkState>(FORWARD_SINK_PATH).its('body');

/** One call of the upload observer: the name of its node, the list that holds it, the number of files it received. */
export interface ObserverCall {
	action: string;
	list: 'actions' | 'deferredActions';
	files: number;
}

export interface UploadObservations {
	observer: ObserverCall[];
	/** The number of files of each call of the sample hold. */
	hold: number[];
}

export const resetUploadObservations = (): Cypress.Chainable =>
	cy.request({method: 'POST', url: `${UPLOAD_OBSERVATIONS_PATH}/reset`, headers: {Origin: SAME_ORIGIN}});

/** What the upload observer and the sample hold of the samples module saw since the last reset. */
export const readUploadObservations = (): Cypress.Chainable<UploadObservations> =>
	cy.request<UploadObservations>(UPLOAD_OBSERVATIONS_PATH).its('body');

/** One item of a held submission, as the inspection of the quarantine test module answers it: never its bytes. */
export interface HeldItem {
	name: string;
	fieldName: string;
	fileName: string;
	mimeType: string | null;
	size: number;
	sha256: string;
	binaryPresent: boolean;
}

/** One held submission, as the inspection answers it: the names of its fields, never a value. */
export interface HeldSubmissionRecord {
	path: string;
	state: 'held' | 'checking' | 'releasing' | 'completed' | 'rejected' | 'needsReview';
	createdAt: string;
	attempts: number;
	nextAttempt: string | null;
	completedActions: number;
	fields: string[];
	sensitiveFields: string[];
	valuesPresent: boolean;
	items: HeldItem[];
}

/**
 * The held submissions of a form in the quarantine tree of the test site, oldest first. The inspection answers server
 * administrators alone: the spec reads it logged in as root.
 */
export const readHeldSubmissions = (formId: string): Cypress.Chainable<HeldSubmissionRecord[]> =>
	cy.request<{submissions: HeldSubmissionRecord[]}>({
		url: QUARANTINE_INSPECTION_PATH,
		qs: {site: FORMIDABLE_TEST_SITE.key, form: formId}
	}).its('body.submissions');

import {
	FORWARD_SINK_PATH,
	QUARANTINE_INSPECTION_PATH,
	QUARANTINE_OPERATIONS_PATH,
	QUARANTINE_RETENTION_PATH,
	QUARANTINE_RUN_PATH,
	QUARANTINE_STUB_PATH,
	UPLOAD_OBSERVATIONS_PATH
} from '../../support/constants';
import {FORMIDABLE_TEST_SITE, FORWARD_SINK_TARGET_ID, getInputFileNode, getInputTextNode} from '../../support/fixtures';
import {SAME_ORIGIN} from '../security/support';

const BOUNDARY = '----FormidableCypressBoundary';
/**
 * A POST to an endpoint of the quarantine: each refuses one without X-Requested-With, and the operations and the
 * retention of formidable-quarantine also refuse one whose Content-Type is not application/json (415).
 */
export const QUARANTINE_POST_HEADERS = {Origin: SAME_ORIGIN, 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/json'};
export const FILE_FIELD = 'supportingDocument';
export const FILE_NAME = 'claim-note.txt';
/** The text of the file of submissionWithAFile. */
export const FILE_TEXT = 'A note attached to the claim.';
/** The SHA-256 of FILE_TEXT, computed outside Jahia (`shasum -a 256`). */
export const FILE_SHA256 = 'f7c11b5f5115eb9d0f4f8619af8870daf212b37948739f8534e4088a078e7ffd';

/** A submission of the text field and one small text file of that text, as a browser posts it. */
export const submissionWithText = (fullName: string, text: string): string =>
	`--${BOUNDARY}\r\n`
	+ 'Content-Disposition: form-data; name="fullName"\r\n\r\n'
	+ `${fullName}\r\n`
	+ `--${BOUNDARY}\r\n`
	+ `Content-Disposition: form-data; name="${FILE_FIELD}"; filename="${FILE_NAME}"\r\n`
	+ 'Content-Type: text/plain\r\n\r\n'
	+ `${text}\r\n`
	+ `--${BOUNDARY}--\r\n`;

/** A submission of the text field and one small text file, as a browser posts it. */
export const submissionWithAFile = (fullName: string): string => submissionWithText(fullName, FILE_TEXT);

/**
 * The texts of the files the stub checks read: the first marker line decides what a stub answers. The second line
 * keeps each file apart from the others, so no two of them share a SHA-256.
 */
export const STUB_FILE = {
	pass: 'VERDICT:PASS\nA claim note the checks pass.',
	fail: 'VERDICT:FAIL\nA claim note the checks fail.',
	down: 'DOWN\nA claim note the checks cannot reach while the outage is on.',
	inconclusive: 'INCONCLUSIVE\nA claim note the checks cannot decide.'
};

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
	/** The forwards received since the reset; a refused forward is not received. */
	count: number;
	fields: string[];
	files: string[];
	/** Whether the sink answers each forward with 503. */
	refusing: boolean;
	/** The forwards refused since the reset. */
	refused: number;
}

/** Forgets the forwards received, and lifts a refusal. */
export const resetForwardSink = (): Cypress.Chainable =>
	cy.request({method: 'POST', url: `${FORWARD_SINK_PATH}/reset`, headers: {Origin: SAME_ORIGIN}});

/**
 * Makes the sink answer each forward with 503, so a forward action fails, or lifts it. Server administrators only, with
 * X-Requested-With: the spec sets it logged in as root.
 */
export const setForwardSinkRefusal = (refuse: boolean): Cypress.Chainable =>
	cy.request({method: 'POST', url: `${FORWARD_SINK_PATH}/${refuse ? 'refuse' : 'accept'}`, headers: QUARANTINE_POST_HEADERS});

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

/** One item of a held submission, as the inspection of formidable-quarantine answers it: never its bytes. */
export interface HeldItem {
	name: string;
	fieldName: string;
	fileName: string;
	mimeType: string | null;
	size: number;
	sha256: string;
	binaryPresent: boolean;
}

/** The last outcome of one check for one item, as the inspection answers it: never the ticket, never a byte. */
export interface HeldOutcome {
	item: string;
	check: string;
	outcome: 'PASS' | 'FAIL' | 'INCONCLUSIVE' | 'PENDING' | 'FAILED';
	reason: string | null;
	retryable: boolean;
	ticketPresent: boolean;
	attempts: number;
	sha256: string;
	at: string;
}

/** Why the run stopped a held submission: a reason of the chain, INTEGRITY_FAILED or ACTION_FAILED. */
export interface HeldDecision {
	reason: string;
	item: string | null;
	check: string | null;
	sha256: string | null;
	failedAction: number | null;
	at: string;
}

/** Who changed the state of a held submission, how and when: an operation of a manager, or the run (no user). */
export interface HeldAuditRecord {
	operation: 'retry' | 'release' | 'resume' | 'reject' | 'run';
	user: string | null;
	from: string;
	to: string;
	at: string;
}

/** One held submission, as the inspection answers it: the names of its fields, never a value. */
export interface HeldSubmissionRecord {
	path: string;
	state: 'held' | 'checking' | 'releasing' | 'completed' | 'rejected' | 'needsReview';
	createdAt: string;
	/** The last change of the state: the retention reads it. */
	changedAt: string | null;
	attempts: number;
	nextAttempt: string | null;
	completedActions: number;
	fields: string[];
	sensitiveFields: string[];
	valuesPresent: boolean;
	items: HeldItem[];
	checks: HeldOutcome[];
	decision: HeldDecision | null;
	audit: HeldAuditRecord[];
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

/** The settings of the run of formidable-quarantine, as "run now" of the quarantine test module answers them. */
export interface QuarantineRunSettings {
	allowInProcessFirst: boolean;
	maxAttempts: number;
	backoffInitialSeconds: number;
	backoffCapSeconds: number;
}

/** One held submission a run processed, with its state after the run. */
export interface ProcessedSubmission {
	path: string;
	form: string;
	from: string;
	state: HeldSubmissionRecord['state'] | null;
	error: string | null;
}

export interface QuarantineRunReport {
	site: string;
	form: string | null;
	settings: QuarantineRunSettings;
	processed: ProcessedSubmission[];
}

/**
 * "Run now": one pass of the checks over the due held submissions of the test site, or of one form. A submission that
 * waits for its back-off is not due; ignoreSchedule takes it anyway, so a spec never waits for time. Server
 * administrators only: the spec runs it logged in as root.
 */
export const runQuarantine = (formId?: string, ignoreSchedule = false): Cypress.Chainable<QuarantineRunReport> =>
	cy.request<QuarantineRunReport>({
		method: 'POST',
		url: QUARANTINE_RUN_PATH,
		headers: QUARANTINE_POST_HEADERS,
		qs: {site: FORMIDABLE_TEST_SITE.key, ...(formId ? {form: formId} : {}), ...(ignoreSchedule ? {ignoreSchedule: 'true'} : {})}
	}).its('body');

/** The settings the next run applies. */
export const readQuarantineRunSettings = (): Cypress.Chainable<QuarantineRunSettings> =>
	cy.request<{settings: QuarantineRunSettings}>(QUARANTINE_RUN_PATH).its('body.settings');

/**
 * Edits the configuration of the run, then waits until the run reads it: the configuration admin applies an edit
 * after the provisioning call returns.
 */
export const setQuarantineRunSettings = (settings: Partial<QuarantineRunSettings>): Cypress.Chainable => {
	const properties = Object.fromEntries(Object.entries(settings).map(([key, value]) => [key, String(value)]));
	editConfiguration('org.jahia.modules.formidable.quarantine.run', properties);
	return cy.waitUntil(() => readQuarantineRunSettings().then(current =>
		Object.entries(settings).every(([key, value]) => current[key as keyof QuarantineRunSettings] === value)),
	{timeout: 30000, interval: 500, errorMsg: 'The run of formidable-quarantine did not read its new settings'});
};

/**
 * The period of the scheduled run of formidable-quarantine, in seconds; 0 turns it off. The test lab turns it off
 * (tests/provisioning-manifest-*.yml), so that "run now" alone moves a held submission in a spec. A spec that turns it
 * on turns it off again in its after(). The configuration admin applies the edit after the call returns, and the run
 * answers no period: a spec waits for the effect of the scheduled run, not for the setting.
 */
export const setQuarantineRunPeriod = (periodSeconds: number): Cypress.Chainable =>
	editConfiguration('org.jahia.modules.formidable.quarantine.run', {periodSeconds: String(periodSeconds)});

/** One call a stub check received: the check node, the SHA-256 of the file, and whether it was a submit or a poll. */
export interface StubCall {
	nodeType: string;
	checkName: string | null;
	sha256: string | null;
	kind: 'SUBMIT' | 'POLL';
}

/** The calls the stub checks received, oldest first: all of them, or those of one check node. */
export const readStubCalls = (checkName?: string): Cypress.Chainable<StubCall[]> =>
	cy.request<{calls: StubCall[]}>({
		url: `${QUARANTINE_STUB_PATH}/calls`,
		qs: checkName ? {check: checkName} : {}
	}).its('body.calls');

export const clearStubCalls = (): Cypress.Chainable =>
	cy.request({method: 'POST', url: `${QUARANTINE_STUB_PATH}/calls/clear`, headers: QUARANTINE_POST_HEADERS});

/** Sets the outage of the stub checks (a DOWN file then fails, retryable), or lifts it (a DOWN file then passes). */
export const setStubOutage = (down: boolean): Cypress.Chainable =>
	cy.request({
		method: 'POST',
		url: `${QUARANTINE_STUB_PATH}/outage/${down ? 'set' : 'lift'}`,
		headers: QUARANTINE_POST_HEADERS
	});

export type QuarantineOperation = 'retry' | 'release' | 'resume' | 'reject';

/** The answer of an operation: the state of the held submission before it and after it. */
export interface QuarantineOperationAnswer {
	path: string;
	operation: QuarantineOperation;
	from: string;
	state: HeldSubmissionRecord['state'];
}

/**
 * An operation of a manager on a held submission, as the user of the current session: a manager of the form, or a
 * server administrator. Any other user gets 403; a state that does not allow the operation gets 409.
 */
export const operateHeldSubmission = (path: string, operation: QuarantineOperation, failOnStatusCode = true):
	Cypress.Chainable<Cypress.Response<QuarantineOperationAnswer>> =>
	cy.request<QuarantineOperationAnswer>({
		method: 'POST',
		url: QUARANTINE_OPERATIONS_PATH,
		headers: QUARANTINE_POST_HEADERS,
		qs: {path, operation},
		failOnStatusCode
	});

export interface QuarantineRetentionAnswer {
	site: string;
	retentionDays: number;
	purged: string[];
	failed: string[];
}

/**
 * The retention over the test site: purges each rejected held submission whose last change is older than the limit,
 * retentionDays of the configuration or olderThanDays for this call. Server administrators only.
 */
export const runQuarantineRetention = (olderThanDays?: number, failOnStatusCode = true):
	Cypress.Chainable<Cypress.Response<QuarantineRetentionAnswer>> =>
	cy.request<QuarantineRetentionAnswer>({
		method: 'POST',
		url: QUARANTINE_RETENTION_PATH,
		headers: QUARANTINE_POST_HEADERS,
		qs: {site: FORMIDABLE_TEST_SITE.key, ...(olderThanDays === undefined ? {} : {olderThanDays: String(olderThanDays)})},
		failOnStatusCode
	});

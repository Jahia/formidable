import {
	FORWARD_SINK_PATH,
	GRAPHQL_PATH,
	QUARANTINE_RUN_PATH,
	QUARANTINE_STUB_PATH,
	UPLOAD_OBSERVATIONS_PATH
} from '../../support/constants';
import {FORMIDABLE_TEST_SITE, FORWARD_SINK_TARGET_ID, getInputFileNode, getInputTextNode} from '../../support/fixtures';
import {SAME_ORIGIN} from '../security/support';

const BOUNDARY = '----FormidableCypressBoundary';
/** A POST to an endpoint of the quarantine test module or of the samples: each refuses one without X-Requested-With. */
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

/** The state of a held submission, as the GraphQL API names it (FmdbqState). */
export type GraphqlHeldState = 'HELD' | 'CHECKING' | 'RELEASING' | 'COMPLETED' | 'REJECTED' | 'NEEDS_REVIEW';

/** The stored name of a state, as an audit record and the specs name it: NEEDS_REVIEW is needsReview. */
export const storedState = (state: GraphqlHeldState): HeldSubmissionRecord['state'] =>
	(state === 'NEEDS_REVIEW' ? 'needsReview' : state.toLowerCase()) as HeldSubmissionRecord['state'];

/** The root of the quarantine tree of a site: the identifier of a held submission is its path below it. */
export const quarantineRootOf = (siteKey: string = FORMIDABLE_TEST_SITE.key): string =>
	`/sites/${siteKey}/formidable-quarantine/`;

/** The identifier of the held submission at the path, for the GraphQL API of the site. */
export const heldIdOf = (path: string, siteKey: string = FORMIDABLE_TEST_SITE.key): string => {
	const root = quarantineRootOf(siteKey);
	if (!path.startsWith(root)) {
		throw new Error(`Not a held submission of site ${siteKey}: ${path}`);
	}
	return path.substring(root.length);
};

export interface GraphqlAnswer<T> {
	data?: T;
	errors?: Array<{message?: string}>;
}

/**
 * A GraphQL request as the user of the browser session, as the manager screen sends it. The HTTP status is not
 * checked: a guest may get a refusal of the platform, which the caller reads as "nothing".
 */
export const quarantineGraphql = <T>(query: string, variables: Record<string, unknown>):
	Cypress.Chainable<Cypress.Response<GraphqlAnswer<T>>> =>
	cy.request<GraphqlAnswer<T>>({
		method: 'POST',
		url: GRAPHQL_PATH,
		headers: {Origin: SAME_ORIGIN, 'Content-Type': 'application/json'},
		body: {query, variables},
		failOnStatusCode: false
	});

const errorMessages = (answer: GraphqlAnswer<unknown> | undefined): string =>
	(answer?.errors ?? []).map(error => error.message ?? '').join(' | ');

const HELD_FIELDS = `
	id state createdAt changedAt attempts nextAttempt completedActions fields sensitiveFields valuesPresent
	files {
		item fieldName name mimeType size sha256 bytesPresent
		checks {name type verdict reasonCode retryable ticketPending attempts sha256 at}
	}
	decision {reason item check sha256 failedAction at}
	audit {operation user from to at}
	allowedOperations`;

/** One held submission, as the GraphQL API answers it. */
export interface GraphqlHeldSubmission {
	id: string;
	state: GraphqlHeldState;
	createdAt: string;
	changedAt: string | null;
	attempts: number;
	nextAttempt: string | null;
	completedActions: number;
	fields: string[];
	sensitiveFields: string[];
	valuesPresent: boolean;
	files: Array<{
		item: string;
		fieldName: string;
		name: string;
		mimeType: string | null;
		size: number;
		sha256: string;
		bytesPresent: boolean;
		checks: Array<{
			name: string;
			type: string | null;
			verdict: HeldOutcome['outcome'];
			reasonCode: string | null;
			retryable: boolean;
			ticketPending: boolean;
			attempts: number;
			sha256: string;
			at: string;
		}>;
	}>;
	decision: HeldDecision | null;
	audit: HeldAuditRecord[];
	allowedOperations: Array<'RETRY' | 'RELEASE' | 'RESUME' | 'REJECT'>;
}

/** The record the specs read, from the answer of the GraphQL API: the path, the items and the flat list of outcomes. */
const recordOf = (held: GraphqlHeldSubmission, siteKey: string): HeldSubmissionRecord => ({
	path: `${quarantineRootOf(siteKey)}${held.id}`,
	state: storedState(held.state),
	createdAt: held.createdAt,
	changedAt: held.changedAt,
	attempts: held.attempts,
	nextAttempt: held.nextAttempt,
	completedActions: held.completedActions,
	fields: held.fields,
	sensitiveFields: held.sensitiveFields,
	valuesPresent: held.valuesPresent,
	items: held.files.map(file => ({
		name: file.item,
		fieldName: file.fieldName,
		fileName: file.name,
		mimeType: file.mimeType,
		size: file.size,
		sha256: file.sha256,
		binaryPresent: file.bytesPresent
	})),
	checks: held.files.flatMap(file => file.checks.map(check => ({
		item: file.item,
		check: check.name,
		outcome: check.verdict,
		reason: check.reasonCode,
		retryable: check.retryable,
		ticketPresent: check.ticketPending,
		attempts: check.attempts,
		sha256: check.sha256,
		at: check.at
	}))),
	decision: held.decision,
	audit: held.audit
});

/** One item of a held submission, as the specs read it: never its bytes. */
export interface HeldItem {
	name: string;
	fieldName: string;
	fileName: string;
	mimeType: string | null;
	size: number;
	sha256: string;
	binaryPresent: boolean;
}

/** The last outcome of one check for one item, as the specs read it: never the ticket, never a byte. */
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

/** One held submission, as the specs read it: the names of its fields, never a value. */
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

type SubmissionsAnswer = GraphqlAnswer<{
	formidableQuarantine: {submissions: {totalCount: number; items: GraphqlHeldSubmission[]}} | null;
}>;

/**
 * The held submissions of a form in the quarantine tree of a site, oldest first, through the GraphQL API of
 * formidable-quarantine. The API answers the managers of the site and the server administrators: the specs read it
 * logged in as root. Fails on any GraphQL error, on a user who does not manage the site, and on more than one page.
 */
export const readHeldSubmissions = (formId: string, siteKey: string = FORMIDABLE_TEST_SITE.key):
	Cypress.Chainable<HeldSubmissionRecord[]> =>
	quarantineGraphql<SubmissionsAnswer['data']>(
		`query($siteKey: String!, $formId: String) {
			formidableQuarantine(siteKey: $siteKey) {
				submissions(formId: $formId, limit: 100) {totalCount items {${HELD_FIELDS}}}
			}
		}`,
		{siteKey, formId}
	).then(response => {
		expect(errorMessages(response.body), 'GraphQL errors of the read of the quarantine').to.eq('');
		const quarantine = response.body.data?.formidableQuarantine;
		if (!quarantine) {
			throw new Error(`The user of the session does not see the quarantine of site ${siteKey}`);
		}
		expect(quarantine.submissions.totalCount, 'held submissions on one page').to.be.at.most(100);
		return quarantine.submissions.items.map(held => recordOf(held, siteKey));
	});

/**
 * Whether the user of the session sees the quarantine of a site through the GraphQL API: false for a user who does
 * not manage the site, as for a site that does not exist, and for a guest that the platform refuses.
 */
export const quarantineIsVisible = (siteKey: string = FORMIDABLE_TEST_SITE.key): Cypress.Chainable<boolean> =>
	quarantineGraphql<{formidableQuarantine: {siteKey: string} | null}>(
		'query($siteKey: String!) { formidableQuarantine(siteKey: $siteKey) { siteKey } }',
		{siteKey}
	).then(response => Boolean(response.body?.data?.formidableQuarantine));

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
 * The answer of a mutation of the GraphQL API, read as the status the specs assert: 200 when it ran; 403 when the user
 * does not manage the site (the API answers null), for an identifier that names nothing in the site (the same answer),
 * and for a purge by a user who is not a server administrator; 409 when the state or the decision does not allow the
 * operation; 500 for any other error.
 */
const statusOf = (answer: GraphqlAnswer<unknown> | undefined, ran: boolean): number => {
	const errors = errorMessages(answer);
	if (errors.includes('is not allowed in state')) {
		return 409;
	}
	if (errors.includes('No held submission') || errors.includes('Server administrators only') || (!errors && !ran)) {
		return 403;
	}
	return errors ? 500 : 200;
};

/** The response of a mutation, as the request of an endpoint answered it: the status above and the body. */
const answered = <T>(status: number, body: T, failOnStatusCode: boolean, what: string, errors: string):
	Cypress.Response<T> => {
	if (failOnStatusCode && status !== 200) {
		throw new Error(`${what} answered ${status}: ${errors}`);
	}
	return {status, body} as Cypress.Response<T>;
};

type OperateAnswer = GraphqlAnswer<{
	formidableQuarantine: {operate: {id: string; state: GraphqlHeldState; audit: HeldAuditRecord[]}} | null;
}>;

/**
 * An operation of a manager on a held submission of the test site, as the user of the current session, through the
 * mutation formidableQuarantine(siteKey) { operate } of the GraphQL API: a manager of the site, or a server
 * administrator. The response carries the status of statusOf; its body names the state before the operation, from
 * the audit record the operation wrote, and the state after it.
 */
export const operateHeldSubmission = (path: string, operation: QuarantineOperation, failOnStatusCode = true):
	Cypress.Chainable<Cypress.Response<QuarantineOperationAnswer>> =>
	quarantineGraphql<OperateAnswer['data']>(
		`mutation($siteKey: String!, $id: String!, $operation: FmdbqOperation!) {
			formidableQuarantine(siteKey: $siteKey) {
				operate(id: $id, operation: $operation) {id state audit {operation user from to at}}
			}
		}`,
		{siteKey: FORMIDABLE_TEST_SITE.key, id: heldIdOf(path), operation: operation.toUpperCase()}
	).then(response => {
		const after = response.body.data?.formidableQuarantine?.operate;
		const last = after?.audit[after.audit.length - 1];
		const body = {path, operation, from: last?.from ?? '', state: after ? storedState(after.state) : 'held'};
		return answered(statusOf(response.body, Boolean(after)), body as QuarantineOperationAnswer, failOnStatusCode,
			`The operation ${operation} on ${path}`, errorMessages(response.body));
	});

export interface QuarantineRetentionAnswer {
	site: string;
	retentionDays: number;
	purged: string[];
	failed: string[];
}

type PurgeAnswer = GraphqlAnswer<{
	formidableQuarantine: {purge: {retentionDays: number; purged: string[]; failed: string[]}} | null;
}>;

/**
 * The retention over the test site, through the mutation formidableQuarantine(siteKey) { purge } of the GraphQL API:
 * purges each rejected held submission whose last change is older than the limit, retentionDays of the configuration
 * or olderThanDays for this call. Server administrators only: any other user gets 403. The body names the paths.
 */
export const runQuarantineRetention = (olderThanDays?: number, failOnStatusCode = true):
	Cypress.Chainable<Cypress.Response<QuarantineRetentionAnswer>> =>
	quarantineGraphql<PurgeAnswer['data']>(
		`mutation($siteKey: String!, $olderThanDays: Int) {
			formidableQuarantine(siteKey: $siteKey) {purge(olderThanDays: $olderThanDays) {retentionDays purged failed}}
		}`,
		{siteKey: FORMIDABLE_TEST_SITE.key, olderThanDays: olderThanDays ?? null}
	).then(response => {
		const report = response.body.data?.formidableQuarantine?.purge;
		const toPath = (id: string) => `${quarantineRootOf()}${id}`;
		const body: QuarantineRetentionAnswer = {
			site: FORMIDABLE_TEST_SITE.key,
			retentionDays: report?.retentionDays ?? 0,
			purged: (report?.purged ?? []).map(toPath),
			failed: (report?.failed ?? []).map(toPath)
		};
		return answered(statusOf(response.body, Boolean(report)), body, failOnStatusCode,
			'The retention', errorMessages(response.body));
	});

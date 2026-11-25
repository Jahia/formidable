import {createUser, deleteUser, enableModule, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	FORWARD_SINK_TARGET_ID,
	QUARANTINE_MANAGER_ROLE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getForwardActionNode,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {SAME_ORIGIN, expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	FILE_FIELD,
	FORWARD_SINK_TARGET,
	HeldSubmissionRecord,
	QuarantineOperation,
	STUB_FILE,
	clearStubCalls,
	fields,
	operateHeldSubmission,
	readForwardSink,
	readHeldSubmissions,
	resetForwardSink,
	runQuarantine,
	runQuarantineRetention,
	setDevelopmentForwardTargets,
	setForwardSinkRefusal,
	setForwardTargets,
	setQuarantineRunSettings,
	setStubOutage,
	setUploadsRequireQuarantine,
	submissionWithText
} from './support';

const SITE_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}`;
const OPERATIONS: QuarantineOperation[] = ['retry', 'release', 'resume', 'reject'];

/**
 * The users of Q20. The reader and the editor are editors of the site, the site administrator administers it; the
 * manager holds the role on the form alone.
 */
const PASSWORD = 'quarOps-Password-1';
const READER = 'quarOpsReader';
const EDITOR = 'quarOpsEditor';
const SITE_ADMIN = 'quarOpsSiteAdmin';
const MANAGER = 'quarOpsManager';

interface GraphqlBody {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any -- each query of the spec reads its own shape
	data?: any;
	errors?: Array<{message?: string}>;
}

/** Whether a live node exists, read as root through GraphQL. */
const liveNodeExists = (path: string): Cypress.Chainable<boolean> => cy.request<GraphqlBody>({
	method: 'POST',
	url: '/modules/graphql',
	headers: {Origin: SAME_ORIGIN},
	body: {
		query: 'query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { name } } }',
		variables: {path}
	},
	failOnStatusCode: false
}).then(response => {
	const unexpected = (response.body.errors ?? [])
		.filter(error => !(error.message ?? '').includes(`PathNotFoundException: ${path}`));
	expect(unexpected, `unexpected GraphQL errors of the read of ${path}`).to.deep.eq([]);
	return Boolean(response.body.data?.jcr?.nodeByPath);
});

/** The number of submissions that Save to JCR stored for the form, read as root. */
const storedSubmissionCount = (formName: string): Cypress.Chainable<number> => {
	const path = `${SITE_PATH}/formidable-results/${formName}/submissions`;
	return cy.request<GraphqlBody>({
		method: 'POST',
		url: '/modules/graphql',
		headers: {Origin: SAME_ORIGIN},
		body: {
			query: 'query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { descendants(typesFilter: {types: ["fmdb:formSubmission"]}) { nodes { name } } } } }',
			variables: {path}
		},
		failOnStatusCode: false
	}).then(response => {
		const unexpected = (response.body.errors ?? [])
			.filter(error => !(error.message ?? '').includes(`PathNotFoundException: ${path}`));
		expect(unexpected, `unexpected GraphQL errors of the read of ${path}`).to.deep.eq([]);
		return (response.body.data?.jcr?.nodeByPath?.descendants?.nodes ?? []).length as number;
	});
};

const groovy = (script: string, replacements: Record<string, string>): Cypress.Chainable<string> =>
	cy.executeGroovy(script, replacements).then(result => String(result));

/** Submits one text file to the form as a guest, expects the body of any accepted submission, and logs root back in. */
const submitAsGuest = (formId: string, text: string): void => {
	cy.logout();
	postDirectMultipartSubmission({formId, body: submissionWithText('Ada', text), headers: withSameOriginHeaders()})
		.then(expectSuccessResponse);
	cy.login();
};

/** The one held submission of a form. */
const theHeldSubmission = (formId: string): Cypress.Chainable<HeldSubmissionRecord> =>
	readHeldSubmissions(formId).then(held => {
		expect(held, 'held submissions of the form').to.have.length(1);
		return held[0];
	});

/** Runs the checks of the form once, and expects the run to have processed its one submission into that state. */
const runOnce = (formId: string, state: HeldSubmissionRecord['state'], ignoreSchedule = false): void => {
	runQuarantine(formId, ignoreSchedule).then(report => {
		expect(report.processed, 'submissions processed by the run').to.have.length(1);
		expect(report.processed[0].error, 'error of the run').to.eq(null);
		expect(report.processed[0].state, 'state after the run').to.eq(state);
	});
};

/** Runs an operation as root, and expects it to answer the state after it. */
const operateAsRoot = (path: string, operation: QuarantineOperation, state: HeldSubmissionRecord['state']): void => {
	operateHeldSubmission(path, operation).then(response => {
		expect(response.body.operation, 'operation').to.eq(operation);
		expect(response.body.from, 'state before the operation').to.eq('needsReview');
		expect(response.body.state, 'state after the operation').to.eq(state);
	});
};

/**
 * The operations of a manager and the retention of formidable-quarantine (tests/scenarios/quarantine.md, Q05, Q18,
 * Q20, Q24). A manager of a form holds the role formidable-quarantine-manager on it, published, and retries,
 * releases, resumes or rejects a held submission in state needsReview through POST
 * /modules/formidable-quarantine/operations. Each accepted operation writes an audit record that names the user; the
 * run writes one when it releases, rejects or sends to review. The retention (POST
 * /modules/formidable-quarantine/retention) purges the rejected held submissions older than its limit. The spec starts
 * the run through "run now", ages a record through Groovy, and never waits for time. It reads the held submissions
 * through the inspection, as root.
 *
 * Q20 is a control scenario. Its control build is formidable-quarantine with the check of the permission removed:
 * in QuarantineOperations.applyLocked, replace `!managers.mayManage(record.formId())` with `false`. Build it with `package`,
 * deploy it in place of the module, and run this spec: Q20 must fail by assertion, on the status of an operation of the
 * reader (200 or 409, not 403).
 */
describe('Quarantine - 99 A manager retries, releases, resumes or rejects a held submission, and the retention purges it', () => {
	useFormidableSite();

	before(() => {
		enableModule(QUARANTINE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		setUploadsRequireQuarantine(false);
		setForwardTargets([FORWARD_SINK_TARGET]);
		setDevelopmentForwardTargets(true);
	});

	beforeEach(() => {
		cy.login();
		resetForwardSink();
		clearStubCalls();
		setStubOutage(true);
	});

	after(() => {
		cy.login();
		// never leave a refusing sink, few attempts or a forward target for the rest of the suite
		resetForwardSink();
		setQuarantineRunSettings({maxAttempts: 10});
		setDevelopmentForwardTargets(false);
		setForwardTargets([]);
	});

	it('Q05: the attempts are exhausted, then a manager retries and the submission is released', () => {
		setQuarantineRunSettings({maxAttempts: 2});
		createPublishedLiveFormPage('quarantine-retry-form', 'Quarantine retry form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.down);
			runOnce(formId, 'checking');
			runOnce(formId, 'needsReview', true);
			theHeldSubmission(formId).then(held => {
				expect(held.decision?.reason, 'decision').to.eq('ATTEMPTS_EXHAUSTED');
				expect(held.checks.map(check => [check.outcome, check.attempts]), 'outcome').to.deep.equal([['FAILED', 2]]);
				expect(held.valuesPresent, 'values kept for a retry').to.eq(true);

				setStubOutage(false);
				operateAsRoot(held.path, 'retry', 'checking');
			});
			theHeldSubmission(formId).then(held => {
				expect(held.state, 'state after the retry').to.eq('checking');
				expect(held.attempts, 'attempts after the retry').to.eq(0);
				expect(held.checks, 'failures of the check after the retry').to.deep.equal([]);
			});

			runOnce(formId, 'completed');
			theHeldSubmission(formId).then(held => {
				expect(held.checks.map(check => check.outcome), 'outcomes').to.deep.equal(['PASS']);
				expect(held.completedActions, 'actions after release that ran').to.eq(1);
				expect(held.audit.map(record => [record.operation, record.user, record.from, record.to]), 'audit')
					.to.deep.equal([
						['run', null, 'checking', 'needsReview'],
						['retry', 'root', 'needsReview', 'checking'],
						['run', null, 'checking', 'completed']
					]);
			});
		});
		setQuarantineRunSettings({maxAttempts: 10});
	});

	it('Q18: the retention purges an old rejected record, and keeps a fresh rejected one and a completed one', () => {
		createPublishedLiveFormPage('quarantine-retention-form', 'Quarantine retention form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.fail);
			submitAsGuest(formId, `${STUB_FILE.fail}\nA fresh claim.`);
			submitAsGuest(formId, STUB_FILE.pass);
			runQuarantine(formId).then(report => {
				expect(report.processed.map(one => one.state), 'states after the run')
					.to.deep.equal(['rejected', 'rejected', 'completed']);
			});

			readHeldSubmissions(formId).then(held => {
				const [old, fresh, completed] = held;
				expect(held.map(one => one.state), 'states, oldest first').to.deep.equal(['rejected', 'rejected', 'completed']);
				// the old rejected record and the completed one stayed in their state 40 days; the fresh one did not
				[old, completed].forEach(one => {
					groovy('groovy/ageQuarantineSubmission.groovy', {__PATH__: one.path, __DAYS__: '40'})
						.should('not.contain', '.failed');
				});

				runQuarantineRetention().then(response => {
					expect(response.body.retentionDays, 'limit of the retention').to.eq(30);
					expect(response.body.purged, 'purged records').to.deep.equal([old.path]);
					expect(response.body.failed, 'records that could not be purged').to.deep.equal([]);
				});
				liveNodeExists(old.path).should('eq', false);
				liveNodeExists(`${old.path}/checks`).should('eq', false);
				readHeldSubmissions(formId).then(after => {
					expect(after.map(one => [one.path, one.state]), 'records kept').to.deep.equal([
						[fresh.path, 'rejected'],
						[completed.path, 'completed']
					]);
				});
			});
		});
	});

	describe('Q20: only a manager of the form runs an operation, and each leaves an audit record (control)', () => {
		const formName = 'quarantine-managed-form';
		let formId: string;
		let paths: string[];

		before(() => {
			cy.login();
			[READER, EDITOR, SITE_ADMIN, MANAGER].forEach(name => createUser(name, PASSWORD));
			grantRoles(SITE_PATH, ['editor'], READER, 'USER');
			grantRoles(SITE_PATH, ['editor'], EDITOR, 'USER');
			grantRoles(SITE_PATH, ['site-administrator'], SITE_ADMIN, 'USER');
			createPublishedLiveFormPage(formName, 'Quarantine managed form', fields(), undefined, undefined, {
				qualifiers: [getRemoteStubCheckNode()],
				deferredActions: [getSaveToJcrActionNode()]
			}).then(created => {
				formId = created.formId;
				grantRoles(created.formPath, ['fmdb-results-reader'], READER, 'USER');
				grantRoles(created.formPath, [QUARANTINE_MANAGER_ROLE], MANAGER, 'USER');
				publishAndWaitJobEnding(created.formPath);

				// three inconclusive files: the run sends each submission to review
				[1, 2, 3].forEach(claim => submitAsGuest(formId, `${STUB_FILE.inconclusive}\nClaim ${claim}.`));
				runQuarantine(formId).then(report => {
					expect(report.processed.map(one => one.state), 'states after the run')
						.to.deep.equal(['needsReview', 'needsReview', 'needsReview']);
				});
				readHeldSubmissions(formId).then(held => {
					paths = held.map(one => one.path);
				});
			});
		});

		after(() => {
			cy.login();
			[READER, EDITOR, SITE_ADMIN, MANAGER].forEach(name => deleteUser(name));
		});

		[READER, EDITOR, SITE_ADMIN].forEach(name => {
			it(`${name} is refused each operation, and no state changes`, () => {
				cy.logout();
				cy.login(name, PASSWORD);
				paths.forEach(path => {
					OPERATIONS.forEach(operation => {
						operateHeldSubmission(path, operation, false).its('status').should('eq', 403);
					});
				});
				runQuarantineRetention(0, false).its('status').should('eq', 403);

				cy.logout();
				cy.login();
				readHeldSubmissions(formId).then(held => {
					expect(held.map(one => one.state), 'states').to.deep.equal(['needsReview', 'needsReview', 'needsReview']);
					held.forEach(one => {
						expect(one.audit.map(record => record.operation), `audit of ${one.path}`).to.deep.equal(['run']);
						expect(one.valuesPresent, `values of ${one.path}`).to.eq(true);
					});
				});
			});
		});

		it('the manager releases, rejects and retries, and each operation names the manager in the audit', () => {
			cy.logout();
			cy.login(MANAGER, PASSWORD);
			const [released, rejected, retried] = paths;
			operateHeldSubmission(released, 'release').its('body.state').should('eq', 'completed');
			operateHeldSubmission(rejected, 'reject').its('body.state').should('eq', 'rejected');
			operateHeldSubmission(retried, 'retry').its('body.state').should('eq', 'checking');
			// a completed submission allows no operation
			operateHeldSubmission(released, 'release', false).its('status').should('eq', 409);
			// the retention answers server administrators alone
			runQuarantineRetention(0, false).its('status').should('eq', 403);

			cy.logout();
			cy.login();
			readHeldSubmissions(formId).then(held => {
				expect(held.map(one => one.state), 'states').to.deep.equal(['completed', 'rejected', 'checking']);
				const lastRecord = (one: HeldSubmissionRecord) => one.audit[one.audit.length - 1];
				expect(held.map(one => [lastRecord(one).operation, lastRecord(one).user, lastRecord(one).from, lastRecord(one).to]),
					'last audit record of each submission').to.deep.equal([
					['release', MANAGER, 'needsReview', 'completed'],
					['reject', MANAGER, 'needsReview', 'rejected'],
					['retry', MANAGER, 'needsReview', 'checking']
				]);
				expect(held[1].valuesPresent, 'values of the rejected submission').to.eq(false);
				expect(held[1].items[0].binaryPresent, 'bytes of the rejected submission').to.eq(false);
				expect(JSON.stringify(held.map(one => one.audit)), 'audit').to.not.contain('Ada');
			});
			storedSubmissionCount(formName).should('eq', 1);
		});
	});

	it('Q24: an action after release fails, and a manager resumes it: stored once, forwarded once', () => {
		const formName = 'quarantine-resume-form';
		createPublishedLiveFormPage(formName, 'Quarantine resume form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode(), getForwardActionNode({targetId: FORWARD_SINK_TARGET_ID})]
		}).then(({formId}) => {
			setForwardSinkRefusal(true);
			submitAsGuest(formId, STUB_FILE.pass);
			runOnce(formId, 'needsReview');

			theHeldSubmission(formId).then(held => {
				expect(held.completedActions, 'actions after release that ran').to.eq(1);
				expect(held.decision?.reason, 'decision').to.eq('ACTION_FAILED');
				expect(held.decision?.failedAction, 'action to resume from').to.eq(1);
				expect(held.valuesPresent, 'values kept for the resume').to.eq(true);
			});
			storedSubmissionCount(formName).should('eq', 1);
			readForwardSink().then(sink => {
				expect(sink.count, 'forwards received').to.eq(0);
				expect(sink.refused, 'forwards refused').to.be.greaterThan(0);
			});

			setForwardSinkRefusal(false);
			theHeldSubmission(formId).then(held => {
				// a failed action takes a resume: a release is refused
				operateHeldSubmission(held.path, 'release', false).its('status').should('eq', 409);
				operateAsRoot(held.path, 'resume', 'completed');
			});

			theHeldSubmission(formId).then(held => {
				expect(held.state, 'state').to.eq('completed');
				expect(held.completedActions, 'actions after release that ran').to.eq(2);
				expect(held.valuesPresent, 'values after completion').to.eq(false);
				expect(held.audit.map(record => [record.operation, record.user, record.to]), 'audit').to.deep.equal([
					['run', null, 'needsReview'],
					['resume', 'root', 'completed']
				]);
			});
			storedSubmissionCount(formName).should('eq', 1);
			readForwardSink().then(sink => {
				expect(sink.count, 'forwards received').to.eq(1);
				expect(sink.files, 'file parts forwarded').to.deep.equal([FILE_FIELD]);
			});
		});
	});
});

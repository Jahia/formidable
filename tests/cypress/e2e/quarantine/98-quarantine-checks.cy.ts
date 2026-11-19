import {enableModule} from '@jahia/cypress';
import {QUARANTINE_INSPECTION_PATH, QUARANTINE_RUN_PATH, QUARANTINE_STUB_PATH} from '../../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getInProcessStubCheckNode,
	getInputFileNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode,
	getUploadObserverActionNode,
	visitLiveForm
} from '../../support/fixtures';
import {SAME_ORIGIN, expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	HeldSubmissionRecord,
	QUARANTINE_POST_HEADERS,
	STUB_FILE,
	clearStubCalls,
	fields,
	readHeldSubmissions,
	readQuarantineRunSettings,
	readStubCalls,
	readUploadObservations,
	resetUploadObservations,
	runQuarantine,
	setStubOutage,
	setUploadsRequireQuarantine,
	submissionWithText
} from './support';

const SITE_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}`;
const resultsOf = (formName: string): string => `${SITE_PATH}/formidable-results/${formName}`;

/**
 * The words that would tell a visitor what the checks decided. A path is not among them: an error page may echo the
 * URL it refused.
 */
const OUTCOME_WORDS = ['needsReview', 'rejected', 'releasing', 'stub.marker', 'CHECK_FAILED', 'VERDICT'];

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

const groovy = (script: string, replacements: Record<string, string>): Cypress.Chainable<string> =>
	cy.executeGroovy(script, replacements).then(result => String(result));

/** Submits one text file to the form as a guest, and expects the body of any accepted submission. */
const submitAsGuest = (formId: string, text: string): Cypress.Chainable<Cypress.Response<unknown>> => {
	cy.logout();
	return postDirectMultipartSubmission({formId, body: submissionWithText('Ada', text), headers: withSameOriginHeaders()})
		.then(response => {
			expectSuccessResponse(response);
			cy.login();
			return cy.wrap(response, {log: false});
		});
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

/**
 * The checks and the release of the reference hold of the quarantine test module (tests/scenarios/quarantine.md, Q02,
 * Q03, Q04, Q09, Q12, Q13, Q21, Q22, Q23). A form whose "Checks" list holds a stub check of the module is owned by the
 * reference hold. The spec starts the run of the checks through "run now" (POST /modules/formidable-quarantine/run),
 * restricted to the form of the test, and never waits for time. It reads the held submission, its outcomes and its
 * decision through the inspection, the calls of the stubs through the stub control, and the effects of the actions
 * after release through the samples module and GraphQL, as root.
 *
 * Control scenarios. Each control build is the quarantine test module with one guard removed; build it with
 * `package`, deploy it in place of the module, run this spec, and the scenario must fail by assertion:
 * - Q09: in Release.release, replace `firstChanged(record, submission)` with `null`, so the release never computes the
 *   SHA-256 again. Q09 fails on the state (`completed`, not `needsReview`) or on the stored results.
 * - Q21: in CheckChain.judgeResult, answer `null` for FAIL as for PASS, so every check of the list runs whatever an
 *   earlier one answered. Q21 fails on the calls of the second check.
 * - Q23: in CheckChain.refuseInProcessFirst, replace `if (allowInProcessFirst)` with
 *   `if (allowInProcessFirst || files != null)`, so the method returns `null` at once and a list whose first check is
 *   in-process runs (a bare `return null;` leaves the loop unreachable and does not compile). Q23 fails on the calls
 *   of the stubs. The setting `allowInProcessFirst=true` is not a control: the `beforeEach` asserts it is `false`.
 */
describe('Quarantine - 98 The run of the checks releases, rejects or withholds a held submission', () => {
	useFormidableSite();

	before(() => {
		enableModule(QUARANTINE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		setUploadsRequireQuarantine(false);
	});

	beforeEach(() => {
		resetUploadObservations();
		clearStubCalls();
		setStubOutage(true);
		// a configuration left by another spec would change what the chain decides
		readQuarantineRunSettings().its('allowInProcessFirst').should('eq', false);
	});

	it('Q02: every check passes, and the file is released with the bytes the checks passed', () => {
		const formName = 'quarantine-pass-form';
		createPublishedLiveFormPage(formName, 'Quarantine pass form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.pass);
			theHeldSubmission(formId).then(held => {
				const sha256 = held.items[0].sha256;
				runOnce(formId, 'completed');

				theHeldSubmission(formId).then(after => {
					expect(after.state, 'state').to.eq('completed');
					expect(after.items[0].sha256, 'SHA-256 of the item').to.eq(sha256);
					expect(after.items[0].binaryPresent, 'bytes of the item').to.eq(false);
					expect(after.valuesPresent, 'values of the held submission').to.eq(false);
					expect(after.checks.map(check => [check.item, check.check, check.outcome, check.sha256]), 'outcomes')
						.to.deep.equal([['item0', 'remoteCheck', 'PASS', sha256]]);
					expect(after.completedActions, 'actions after release that ran').to.eq(1);
				});
				getLatestLiveFormSubmission(formName).then(({path}) => {
					groovy('groovy/assertStoredFileSha256.groovy', {__PATH__: path, __SHA256__: sha256})
						.should('not.contain', '.failed');
				});
			});
		});
	});

	it('Q03: a check fails, the submission is rejected, and no action after release runs', () => {
		const formName = 'quarantine-fail-form';
		createPublishedLiveFormPage(formName, 'Quarantine fail form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode(), getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.fail);
			runOnce(formId, 'rejected');

			theHeldSubmission(formId).then(held => {
				expect(held.state, 'state').to.eq('rejected');
				expect(held.checks.map(check => [check.check, check.outcome, check.reason]), 'outcome and reason')
					.to.deep.equal([['remoteCheck', 'FAIL', 'stub.marker']]);
				expect(held.decision?.reason, 'decision').to.eq('CHECK_FAILED');
				expect(held.decision?.check, 'check of the decision').to.eq('remoteCheck');
				expect(held.valuesPresent, 'values of the held submission').to.eq(false);
				expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(false);
			});
		});
		liveNodeExists(resultsOf(formName)).should('eq', false);
		readUploadObservations().its('observer').should('deep.equal', []);
	});

	it('Q04: a check that is down keeps the submission checking, and each run tries again', () => {
		createPublishedLiveFormPage('quarantine-down-form', 'Quarantine down form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.down);
			// Q05 covers the exhaustion of the attempts: the settings must allow more than the runs of this test
			readQuarantineRunSettings().its('maxAttempts').should('be.greaterThan', 2);

			runOnce(formId, 'checking');
			theHeldSubmission(formId).then(held => {
				expect(held.attempts, 'attempts after the first run').to.eq(1);
				expect(held.nextAttempt, 'next attempt').to.not.eq(null);
				expect(held.checks.map(check => [check.outcome, check.reason, check.retryable, check.attempts]), 'outcome')
					.to.deep.equal([['FAILED', 'stub.down', true, 1]]);
			});

			// the next attempt is ahead: a run on schedule leaves the submission alone
			runQuarantine(formId).its('processed').should('deep.equal', []);

			runOnce(formId, 'checking', true);
			theHeldSubmission(formId).then(held => {
				expect(held.attempts, 'attempts after the second run').to.eq(2);
				expect(held.checks[0].attempts, 'failures of the check in a row').to.eq(2);
				expect(held.valuesPresent, 'values of the held submission').to.eq(true);
				expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(true);
			});
			readStubCalls('remoteCheck').its('length').should('eq', 2);
		});
	});

	it('Q09: the bytes changed after intake, and the release is refused (control)', () => {
		const formName = 'quarantine-changed-form';
		createPublishedLiveFormPage(formName, 'Quarantine changed form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode(), getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.pass);
			theHeldSubmission(formId).then(held => {
				// still marked PASS: every check passes the new bytes, so only the release can refuse them
				groovy('groovy/replaceQuarantineItemBinary.groovy', {
					__PATH__: `${held.path}/${held.items[0].name}`,
					__TEXT__: 'VERDICT:PASS\\nThe bytes changed after intake.'
				}).should('not.contain', '.failed');
			});

			runQuarantine(formId).then(report => {
				expect(report.processed.map(one => one.state), 'state after the run').to.deep.equal(['needsReview']);
			});
			theHeldSubmission(formId).then(held => {
				expect(held.state, 'state').to.eq('needsReview');
				expect(held.decision?.reason, 'decision').to.eq('INTEGRITY_FAILED');
				expect(held.decision?.item, 'item of the decision').to.eq('item0');
				expect(held.checks.map(check => check.outcome), 'outcomes').to.deep.equal(['PASS']);
				expect(held.completedActions, 'actions after release that ran').to.eq(0);
			});
		});
		liveNodeExists(resultsOf(formName)).should('eq', false);
		readUploadObservations().its('observer').should('deep.equal', []);
	});

	it('Q12: the visitor learns nothing of the outcome', () => {
		const responses: Array<Cypress.Response<unknown>> = [];
		const keep = (response: Cypress.Response<unknown>) => {
			responses.push(response);
		};
		createPublishedLiveFormPage('quarantine-oracle-form', 'Quarantine oracle form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId, livePath}) => {
			submitAsGuest(formId, STUB_FILE.pass).then(keep);
			submitAsGuest(formId, `${STUB_FILE.pass}\nA second claim.`).then(keep);
			submitAsGuest(formId, STUB_FILE.fail).then(keep);

			runQuarantine(formId).then(report => {
				expect(report.processed.map(one => one.state).sort(), 'states after the run')
					.to.deep.equal(['completed', 'completed', 'rejected']);
			});

			// the file already rejected is accepted again, as the file already released
			submitAsGuest(formId, STUB_FILE.pass).then(keep);
			submitAsGuest(formId, STUB_FILE.fail).then(keep);

			cy.then(() => {
				const bodyOf = (response: Cypress.Response<unknown>) => response.body as Record<string, unknown>;
				const first = bodyOf(responses[0]);
				const second = bodyOf(responses[1]);
				const varying = Object.keys(first).filter(key => JSON.stringify(first[key]) !== JSON.stringify(second[key]));
				expect(responses, 'responses to a submission').to.have.length(5);
				responses.forEach((response, index) => {
					const body = bodyOf(response);
					expect(response.status, `status of response ${index + 1}`).to.eq(responses[0].status);
					expect(Object.keys(body).sort(), `keys of response ${index + 1}`).to.deep.equal(Object.keys(first).sort());
					Object.keys(first).filter(key => !varying.includes(key)).forEach(key => {
						expect(body[key], `${key} of response ${index + 1}`).to.deep.equal(first[key]);
					});
				});
			});

			readHeldSubmissions(formId).then(held => {
				cy.logout();
				// the form page, the quarantine paths and the results path, as a guest: no state, no outcome
				const urls = [
					`/en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`,
					...held.flatMap(one => [`/files/live${one.path}/item0`, `/cms/render/live/en${one.path}.html`]),
					`/cms/render/live/en${resultsOf('quarantine-oracle-form')}.html`
				];
				urls.forEach(url => {
					cy.request({url, failOnStatusCode: false}).then(response => {
						const text = typeof response.body === 'string' ? response.body : JSON.stringify(response.body);
						OUTCOME_WORDS.forEach(word => expect(text, `${word} in the answer to the guest on ${url}`).to.not.contain(word));
					});
				});
				held.forEach(one => {
					cy.request({url: `/files/live${one.path}/item0`, failOnStatusCode: false})
						.its('status').should('be.oneOf', [401, 403, 404]);
				});
				// the endpoints of the module answer server administrators alone; the header of a POST is there, so the
				// 403 is the refusal of the guest
				[
					{method: 'GET', url: QUARANTINE_INSPECTION_PATH, qs: {site: FORMIDABLE_TEST_SITE.key, form: formId}},
					{method: 'POST', url: QUARANTINE_RUN_PATH, qs: {site: FORMIDABLE_TEST_SITE.key, form: formId}},
					{method: 'GET', url: `${QUARANTINE_STUB_PATH}/calls`, qs: {}},
					{method: 'POST', url: `${QUARANTINE_STUB_PATH}/outage/lift`, qs: {}}
				].forEach(({method, url, qs}) => {
					cy.request({method, url, qs, headers: QUARANTINE_POST_HEADERS, failOnStatusCode: false})
						.its('status').should('eq', 403);
				});
				cy.login();
			});
		});
	});

	it('Q13: a file that no check applies to is withheld, and no check is called', () => {
		const formName = 'quarantine-withheld-form';
		const image = {name: 'picture', title: 'Picture', accept: ['image/png']};
		createPublishedLiveFormPage(formName, 'Quarantine withheld form',
			[getInputTextNode({name: 'fullName', title: 'Full name'}), getInputFileNode(image)], undefined, undefined, {
				qualifiers: [getRemoteStubCheckNode()],
				deferredActions: [getSaveToJcrActionNode()]
			}).then(({formId, livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Withheld Image');
			form.getFileInput(image.name).attachFile('cypress/fixtures/files/cats.png').shouldHaveSelectedFile('cats.png');
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			runOnce(formId, 'needsReview');
			theHeldSubmission(formId).then(held => {
				expect(held.items.map(item => item.mimeType), 'items').to.deep.equal(['image/png']);
				expect(held.decision?.reason, 'decision').to.eq('FILE_WITHHELD');
				expect(held.checks, 'outcomes').to.deep.equal([]);
				expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(true);
			});
		});
		readStubCalls().should('deep.equal', []);
		liveNodeExists(resultsOf(formName)).should('eq', false);
	});

	it('Q21: two checks, and the first fails: the second never reads the file (control)', () => {
		createPublishedLiveFormPage('quarantine-first-fails-form', 'Quarantine first fails form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode('firstCheck'), getRemoteStubCheckNode('secondCheck')],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.fail);
			runOnce(formId, 'rejected');
			theHeldSubmission(formId).its('decision.check').should('eq', 'firstCheck');
		});
		readStubCalls('firstCheck').its('length').should('eq', 1);
		readStubCalls('secondCheck').should('deep.equal', []);
	});

	it('Q22: two checks pass in the order of the list, and the actions after release run once, in order', () => {
		createPublishedLiveFormPage('quarantine-both-pass-form', 'Quarantine both pass form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode('firstCheck'), getRemoteStubCheckNode('secondCheck')],
			deferredActions: [getUploadObserverActionNode('firstObserver'), getUploadObserverActionNode('secondObserver')]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.pass);
			runOnce(formId, 'completed');
			// a second run finds nothing due: a completed submission never runs its actions again
			runQuarantine(formId, true).its('processed').should('deep.equal', []);

			theHeldSubmission(formId).then(held => {
				expect(held.state, 'state').to.eq('completed');
				expect(held.completedActions, 'actions after release that ran').to.eq(2);
			});
		});
		readStubCalls().then(calls => {
			expect(calls.map(call => [call.checkName, call.kind]), 'calls of the stubs').to.deep.equal([
				['firstCheck', 'SUBMIT'],
				['secondCheck', 'SUBMIT']
			]);
		});
		readUploadObservations().its('observer').should('deep.equal', [
			{action: 'firstObserver', list: 'deferredActions', files: 1},
			{action: 'secondObserver', list: 'deferredActions', files: 1}
		]);
	});

	it('Q23: an in-process check placed first stops the list before any check reads the file (control)', () => {
		createPublishedLiveFormPage('quarantine-in-process-form', 'Quarantine in-process form', fields(), undefined, undefined, {
			qualifiers: [getInProcessStubCheckNode('inProcessCheck'), getRemoteStubCheckNode('remoteCheck')],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			submitAsGuest(formId, STUB_FILE.pass);
			runOnce(formId, 'needsReview');
			theHeldSubmission(formId).then(held => {
				expect(held.decision?.reason, 'decision').to.eq('IN_PROCESS_FIRST');
				expect(held.decision?.check, 'check of the decision').to.eq('inProcessCheck');
				expect(held.checks, 'outcomes').to.deep.equal([]);
			});
		});
		readStubCalls().should('deep.equal', []);
	});
});

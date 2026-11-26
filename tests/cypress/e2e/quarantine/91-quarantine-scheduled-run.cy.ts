import {enableModule} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	HeldSubmissionRecord,
	STUB_FILE,
	fields,
	readHeldSubmissions,
	readQuarantineRunPeriod,
	setQuarantineRunPeriod,
	setUploadsRequireQuarantine,
	submissionWithText
} from './support';

/** The period of the scheduled run while this spec runs, in seconds. */
const SHORT_PERIOD_SECONDS = 2;
/** How long the spec waits for the scheduled run: the edit of the configuration, then a few periods. */
const SCHEDULED_RUN_TIMEOUT = 60000;

/**
 * The scheduled run of formidable-quarantine (tests/scenarios/quarantine.md, Q29; docs/architecture/quarantine.md,
 * "The scheduled run"). The test lab turns the scheduled run off, so that the other specs move a held submission with
 * "run now" alone. This spec sets a short period through the configuration of the run, holds a submission, and waits,
 * within a bound, for the scheduled run to release it, with no call of "run now". It turns the scheduled run off again
 * at the end.
 *
 * Control scenario: build formidable-quarantine with `RunSchedule.configure` scheduling nothing (replace its body
 * with `job.unschedule();`), deploy it in place of the module, run this spec: Q29 fails on the bound of the wait, the
 * submission still `held`.
 */
describe('Quarantine - 91 The scheduled run moves a held submission with no "run now"', () => {
	useFormidableSite();

	before(() => {
		enableModule(QUARANTINE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		setUploadsRequireQuarantine(false);
		// no wait for the scheduler here: the control build schedules nothing, and Q29 must fail on its assertion
		setQuarantineRunPeriod(SHORT_PERIOD_SECONDS, false);
	});

	after(() => {
		// the afterEach of useFormidableSite logged out, and "run now" answers server administrators alone
		cy.login();
		// waits until the scheduler holds no run, so that no scheduled call touches the submissions of the next spec
		setQuarantineRunPeriod(0);
	});

	it('Q29: a held submission whose checks pass is released by the scheduled run', () => {
		createPublishedLiveFormPage('quarantine-scheduled-form', 'Quarantine scheduled form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithText('Ada', STUB_FILE.pass), headers: withSameOriginHeaders()})
				.then(expectSuccessResponse);
			cy.login();

			cy.waitUntil(() => readHeldSubmissions(formId).then(held => held.length === 1 && held[0].state === 'completed'), {
				timeout: SCHEDULED_RUN_TIMEOUT,
				interval: 1000,
				errorMsg: 'The scheduled run did not release the held submission'
			});

			readHeldSubmissions(formId).then(held => {
				const released: HeldSubmissionRecord = held[0];
				expect(released.checks.map(check => [check.check, check.outcome]), 'outcomes').to.deep.equal([['remoteCheck', 'PASS']]);
				expect(released.completedActions, 'actions after release that ran').to.eq(1);
				// the run moved the state, with no user: no operation of a manager
				expect(released.audit.map(entry => [entry.operation, entry.user, entry.to]), 'audit')
					.to.deep.equal([['run', null, 'completed']]);
			});
			// "run now" reads the job of the module from the scheduler: the period the spec set, not a default
			readQuarantineRunPeriod().should('eq', SHORT_PERIOD_SECONDS);
		});
	});
});

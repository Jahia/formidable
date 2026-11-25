import {enableModule} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getAllowListCheckNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	FILE_TEXT,
	HeldSubmissionRecord,
	fields,
	readHeldSubmissions,
	runQuarantine,
	setUploadsRequireQuarantine,
	submissionWithText
} from './support';

/** What the file of submissionWithText is: claim-note.txt, posted as text/plain. */
const TEXT_FILE = {extensions: ['txt'], mimeTypes: ['text/plain']};

/** Submits the text file to the form as a guest, and expects the submission to be accepted. */
const submitAsGuest = (formId: string): void => {
	cy.logout();
	postDirectMultipartSubmission({formId, body: submissionWithText('Ada', FILE_TEXT), headers: withSameOriginHeaders()})
		.then(response => {
			expectSuccessResponse(response);
			cy.login();
		});
};

/** The one held submission of a form. */
const theHeldSubmission = (formId: string): Cypress.Chainable<HeldSubmissionRecord> =>
	readHeldSubmissions(formId).then(held => {
		expect(held, 'held submissions of the form').to.have.length(1);
		return held[0];
	});

/** Runs the checks of the form once, and expects the run to have processed its one submission into that state. */
const runOnce = (formId: string, state: HeldSubmissionRecord['state']): void => {
	runQuarantine(formId).then(report => {
		expect(report.processed, 'submissions processed by the run').to.have.length(1);
		expect(report.processed[0].error, 'error of the run').to.eq(null);
		expect(report.processed[0].state, 'state after the run').to.eq(state);
	});
};

/** Holds a submission of the text file on a form whose only check is the allow-list, and runs the checks once. */
const holdAndRun = (formName: string, extensions: string[], mimeTypes: string[],
	state: HeldSubmissionRecord['state']): Cypress.Chainable<HeldSubmissionRecord> =>
	createPublishedLiveFormPage(formName, `Allow-list ${formName}`, fields(), undefined, undefined, {
		qualifiers: [getAllowListCheckNode(extensions, mimeTypes)],
		deferredActions: [getSaveToJcrActionNode()]
	}).then(({formId}) => {
		submitAsGuest(formId);
		runOnce(formId, state);
		return theHeldSubmission(formId);
	});

/**
 * The allow-list check of formidable-quarantine (fmdbq:allowListCheck), alone in the "Checks" list of a form, on a
 * site with the module. The check reads the name and the MIME type of the file, never a byte: an allowed type is
 * released at the first run, a refused one is rejected. The spec starts the run through "run now" of the quarantine
 * test module, and reads the held submission through the inspection, as root.
 */
describe('Quarantine - 93 The allow-list check releases an allowed type and rejects any other', () => {
	useFormidableSite();

	before(() => {
		enableModule(QUARANTINE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		setUploadsRequireQuarantine(false);
	});

	it('a file whose extension and MIME type are both listed is released after run now', () => {
		holdAndRun('allow-list-allowed-form', TEXT_FILE.extensions, TEXT_FILE.mimeTypes, 'completed').then(held => {
			expect(held.checks.map(check => [check.check, check.outcome, check.reason]), 'outcomes')
				.to.deep.equal([['allowList', 'PASS', 'allowList.allowed']]);
			expect(held.decision, 'decision').to.eq(null);
			expect(held.completedActions, 'actions after release that ran').to.eq(1);
			expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(false);
		});
	});

	it('a file whose extension is not listed is rejected with CHECK_FAILED', () => {
		holdAndRun('allow-list-extension-form', ['pdf'], TEXT_FILE.mimeTypes, 'rejected').then(held => {
			expect(held.decision?.reason, 'reason of the decision').to.eq('CHECK_FAILED');
			expect(held.decision?.check, 'check of the decision').to.eq('allowList');
			expect(held.checks.map(check => [check.check, check.outcome, check.reason]), 'outcomes')
				.to.deep.equal([['allowList', 'FAIL', 'allowList.extensionRefused']]);
			expect(held.completedActions, 'actions after release that ran').to.eq(0);
			expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(false);
		});
	});

	it('a file whose MIME type is not listed is rejected, even with a listed extension', () => {
		holdAndRun('allow-list-type-form', TEXT_FILE.extensions, ['application/pdf'], 'rejected').then(held => {
			expect(held.decision?.reason, 'reason of the decision').to.eq('CHECK_FAILED');
			expect(held.checks.map(check => [check.check, check.outcome, check.reason]), 'outcomes')
				.to.deep.equal([['allowList', 'FAIL', 'allowList.mimeTypeRefused']]);
		});
	});
});

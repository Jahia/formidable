import {enableModule} from '@jahia/cypress';
import {FORMIDABLE_TEST_SITE, createPublishedLiveFormPage, getUploadObserverActionNode} from '../../support/fixtures';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	withSameOriginHeaders
} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	fields,
	readHeldSubmissions,
	readUploadObservations,
	resetUploadObservations,
	runQuarantine,
	setUploadsRequireQuarantine,
	submissionWithAFile
} from './support';

/** The module of the hold, enabled on the test site by the second scenario only. */
const QUARANTINE_PRODUCT_MODULE_ID = 'formidable-quarantine';

/**
 * Which hold owns a form (tests/scenarios/quarantine.md, Q27, Q28; docs/architecture/quarantine.md, "Ownership of a
 * form"). The hold of formidable-quarantine ranks 100 and owns every held form of a site that has the module, whatever
 * its list "Checks" holds. The sample hold ranks 200 and claims only a form whose list holds fmdbsample:sampleCheck.
 * Each form of this spec passes an empty list on purpose: the fixture would otherwise add a sample check, and the
 * sample hold would claim the form.
 *
 * The test site of useFormidableSite has neither formidable-quarantine nor the quarantine test module, so Q27 runs
 * first, on a site without the module. Q28 then enables the module on the site.
 */
describe('Quarantine - 94 The hold of formidable-quarantine owns the held forms of a site that has the module', () => {
	useFormidableSite();

	before(() => {
		setUploadsRequireQuarantine(false);
	});

	beforeEach(() => {
		resetUploadObservations();
	});

	it('Q27: on a site without the module, a held form that no other hold claims is refused with FMDB-018', () => {
		createPublishedLiveFormPage('ownership-no-module-form', 'Ownership no module form', fields(), undefined, undefined, {
			qualifiers: [],
			deferredActions: [getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()})
				.then(response => expectErrorResponse(response, 503, 'FMDB-018'));

			cy.login();
			readHeldSubmissions(formId).should('deep.equal', []);
		});

		// No hold ran the actions after release: the sample hold did not claim the form, and nothing released it.
		readUploadObservations().then(seen => {
			expect(seen.hold, 'calls of the sample hold').to.deep.equal([]);
			expect(seen.observer, 'calls of the observer action after release').to.deep.equal([]);
		});
	});

	it('Q28: on a site with the module, a held form with an empty list is stored, and the run sends it to review', () => {
		enableModule(QUARANTINE_PRODUCT_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		createPublishedLiveFormPage('ownership-empty-list-form', 'Ownership empty list form', fields(), undefined, undefined, {
			qualifiers: [],
			deferredActions: [getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Grace'), headers: withSameOriginHeaders()})
				.then(expectSuccessResponse);

			cy.login();
			readHeldSubmissions(formId).then(held => {
				expect(held, 'held submissions of the form').to.have.length(1);
				expect(held[0].state, 'state at intake').to.eq('held');
			});

			runQuarantine(formId, true).then(report => {
				expect(report.processed, 'submissions the run processed').to.have.length(1);
				expect(report.processed[0].state, 'state after the run').to.eq('needsReview');
			});
			readHeldSubmissions(formId).then(held => {
				expect(held[0].state, 'state read again').to.eq('needsReview');
				expect(held[0].decision?.reason, 'reason of the review').to.eq('NO_CHECK');
				expect(held[0].valuesPresent, 'values kept for the review').to.eq(true);
			});
		});

		// The hold of the module owned the form: the sample hold was not called, and no action after release ran.
		readUploadObservations().then(seen => {
			expect(seen.hold, 'calls of the sample hold').to.deep.equal([]);
			expect(seen.observer, 'calls of the observer action after release').to.deep.equal([]);
		});
	});
});

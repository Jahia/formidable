import {enableModule} from '@jahia/cypress';
import {CONTENT_PATH} from '../../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	NodeProperty,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getLatestLiveFormSubmission,
	getLatestLiveSubmissionValues,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode,
	getUploadObserverActionNode
} from '../../support/fixtures';
import {SAME_ORIGIN, expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	FILE_FIELD,
	FILE_NAME,
	HeldSubmissionRecord,
	STUB_FILE,
	clearStubCalls,
	fields,
	operateHeldSubmission,
	readHeldSubmissions,
	readUploadObservations,
	resetUploadObservations,
	runQuarantine,
	setStubOutage,
	setUploadsRequireQuarantine
} from './support';

const BOUNDARY = '----FormidableCypressBoundary';
const SCAN_STATUS = 'documentScanStatus';
const CLEAN = 'clean';
const FAILED = 'Uploaded Document failed our security scan';

interface GraphqlBody {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any -- each query of the spec reads its own shape
	data?: any;
	errors?: Array<{message?: string}>;
}

interface EditorFieldSet {
	name: string;
	dynamic: boolean;
	activated: boolean;
	displayName: string;
}

/**
 * The fieldsets of the section of Content Editor that holds the quarantine switch, in their order, for the form at the
 * path, read as root.
 */
const switchSection = (path: string): Cypress.Chainable<EditorFieldSet[]> => cy.request<GraphqlBody>({
	method: 'POST',
	url: '/modules/graphql',
	headers: {Origin: SAME_ORIGIN},
	body: {
		query: 'query($path: String!) { forms { editForm(uuidOrPath: $path, uiLocale: "en", locale: "en") { sections { fieldSets { name dynamic activated displayName } } } } }',
		variables: {path}
	}
}).then(response => {
	expect(response.body.errors, 'errors of the query').to.equal(undefined);
	const sections = response.body.data.forms.editForm.sections as Array<{fieldSets: EditorFieldSet[]}>;
	const section = sections.find(one => one.fieldSets.some(fieldSet => fieldSet.name === 'fmdbmix:quarantine'));
	expect(section, 'the section of the quarantine switch').to.not.equal(undefined);
	return section?.fieldSets ?? [];
});

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

/**
 * A submission of the text field, of a forged value with the name of the scan status, and of one text file. The form
 * has no field of that name, so the engine skips the forged value.
 */
const submission = (fullName: string, forgedStatus: string, text: string): string =>
	`--${BOUNDARY}\r\n`
	+ 'Content-Disposition: form-data; name="fullName"\r\n\r\n'
	+ `${fullName}\r\n`
	+ `--${BOUNDARY}\r\n`
	+ `Content-Disposition: form-data; name="${SCAN_STATUS}"\r\n\r\n`
	+ `${forgedStatus}\r\n`
	+ `--${BOUNDARY}\r\n`
	+ `Content-Disposition: form-data; name="${FILE_FIELD}"; filename="${FILE_NAME}"\r\n`
	+ 'Content-Type: text/plain\r\n\r\n'
	+ `${text}\r\n`
	+ `--${BOUNDARY}--\r\n`;

const submitAsGuest = (formId: string, body: string): void => {
	cy.logout();
	postDirectMultipartSubmission({formId, body, headers: withSameOriginHeaders()}).then(expectSuccessResponse);
	cy.login();
};

/** The properties of the policy of the form (fmdbqmix:quarantinePolicy), with its scan status and two values. */
const policy = (onFail: 'reject' | 'releaseValues'): NodeProperty[] => [
	{name: 'onFail', value: onFail},
	{name: 'scanStatusName', value: SCAN_STATUS},
	{name: 'scanStatusClean', value: CLEAN},
	{name: 'scanStatusFailed', value: FAILED}
];

/** A form with a text field and a file field, one stub check, Save to JCR and the upload observer after release. */
const createForm = (formName: string, onFail: 'reject' | 'releaseValues'): Cypress.Chainable<{formId: string}> =>
	createPublishedLiveFormPage(formName, `Release values ${formName}`, fields(), undefined, undefined, {
		mixins: ['fmdbmix:quarantine', 'fmdbqmix:quarantinePolicy'],
		properties: policy(onFail),
		qualifiers: [getRemoteStubCheckNode()],
		deferredActions: [getSaveToJcrActionNode(), getUploadObserverActionNode('afterRelease')]
	});

/** The one held submission of a form. */
const theHeldSubmission = (formId: string): Cypress.Chainable<HeldSubmissionRecord> =>
	readHeldSubmissions(formId).then(held => {
		expect(held, 'held submissions of the form').to.have.length(1);
		return held[0];
	});

/** Runs the checks of the form once, and expects the run to have processed its one submission into that state. */
const runOnce = (formId: string, state: HeldSubmissionRecord['state']): void => {
	runQuarantine(formId).then(report => {
		expect(report.processed.map(one => [one.state, one.error]), 'run').to.deep.equal([[state, null]]);
	});
};

/**
 * The policy of a form (tests/scenarios/quarantine.md, Q40 to Q44): with onFail=releaseValues, a FAIL verdict, and a
 * reject by a manager, delete the bytes first, then run the actions after release with the values alone and the failed
 * scan status. A release of the files gets the clean scan status. The scan status is a value that the run adds, not a
 * field of the form: a value the visitor sends with its name is skipped by the engine, and written over by the run.
 * With the default policy, a FAIL rejects the submission. The spec starts the run through "run now" and reads the held
 * submissions through the inspection, as root.
 */
describe('Quarantine - 92 The policy of a form releases the values alone on a fail', () => {
	useFormidableSite();

	before(() => {
		enableModule(QUARANTINE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		setUploadsRequireQuarantine(false);
	});

	beforeEach(() => {
		cy.login();
		resetUploadObservations();
		clearStubCalls();
		setStubOutage(true);
	});

	it('Q40: a FAIL under releaseValues deletes the bytes and releases the values alone with the failed status', () => {
		const formName = 'release-values-fail-form';
		createForm(formName, 'releaseValues').then(({formId}) => {
			submitAsGuest(formId, submission('Ada Fail', CLEAN, STUB_FILE.fail));
			theHeldSubmission(formId).then(held => {
				expect(held.fields, 'fields held: the forged status is skipped').to.not.include(SCAN_STATUS);
				expect(held.items[0].binaryPresent, 'bytes at intake').to.eq(true);
			});

			runOnce(formId, 'completed');
			theHeldSubmission(formId).then(held => {
				expect(held.decision?.reason, 'the decision keeps the failed verdict').to.eq('CHECK_FAILED');
				expect(held.checks.map(check => check.outcome), 'outcomes').to.deep.equal(['FAIL']);
				expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(false);
				expect(held.valuesPresent, 'held values').to.eq(false);
				expect(held.completedActions, 'actions after release that ran').to.eq(2);
				expect(held.audit.map(record => [record.operation, record.from, record.to]), 'audit')
					.to.deep.equal([['run', 'held', 'completed']]);
			});

			getLatestLiveSubmissionValues(formName).then(values => {
				expect(values.fullName, 'stored name').to.eq('Ada Fail');
				expect(values[SCAN_STATUS], 'stored scan status').to.eq(FAILED);
			});
			getLatestLiveFormSubmission(formName).then(({path}) => {
				liveNodeExists(`${path}/files`).should('eq', false);
			});
		});
		readUploadObservations().its('observer').should('deep.equal', [
			{action: 'afterRelease', list: 'deferredActions', files: 0}
		]);
	});

	it('Q41: a PASS is released with the file and the clean status, whatever the visitor sent', () => {
		const formName = 'release-values-pass-form';
		createForm(formName, 'releaseValues').then(({formId}) => {
			submitAsGuest(formId, submission('Ada Pass', FAILED, STUB_FILE.pass));
			runOnce(formId, 'completed');
			theHeldSubmission(formId).then(held => {
				expect(held.decision, 'decision').to.eq(null);
			});
			getLatestLiveSubmissionValues(formName).then(values => {
				expect(values.fullName, 'stored name').to.eq('Ada Pass');
				expect(values[SCAN_STATUS], 'stored scan status').to.eq(CLEAN);
			});
			getLatestLiveFormSubmission(formName).then(({path}) => {
				liveNodeExists(`${path}/files/${FILE_FIELD}`).should('eq', true);
			});
		});
		readUploadObservations().its('observer').should('deep.equal', [
			{action: 'afterRelease', list: 'deferredActions', files: 1}
		]);
	});

	it('Q42: a FAIL under the default policy rejects the submission and runs no action', () => {
		const formName = 'release-values-reject-form';
		createForm(formName, 'reject').then(({formId}) => {
			submitAsGuest(formId, submission('Ada Rejected', CLEAN, STUB_FILE.fail));
			runOnce(formId, 'rejected');
			theHeldSubmission(formId).then(held => {
				expect(held.decision?.reason, 'decision').to.eq('CHECK_FAILED');
				expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(false);
				expect(held.valuesPresent, 'held values').to.eq(false);
				expect(held.completedActions, 'actions after release that ran').to.eq(0);
			});
		});
		readUploadObservations().its('observer').should('deep.equal', []);
	});

	it('Q43: a manager reject under releaseValues releases the values alone with the failed status', () => {
		const formName = 'release-values-manager-form';
		createForm(formName, 'releaseValues').then(({formId}) => {
			submitAsGuest(formId, submission('Ada Review', CLEAN, STUB_FILE.inconclusive));
			runOnce(formId, 'needsReview');
			theHeldSubmission(formId).then(held => {
				operateHeldSubmission(held.path, 'reject').then(response => {
					expect(response.body.from, 'state before the reject').to.eq('needsReview');
					expect(response.body.state, 'state after the reject').to.eq('completed');
				});
			});
			theHeldSubmission(formId).then(held => {
				expect(held.decision?.reason, 'the decision keeps the reject').to.eq('MANAGER_REJECTED');
				expect(held.items[0].binaryPresent, 'bytes of the item').to.eq(false);
				expect(held.valuesPresent, 'held values').to.eq(false);
				expect(held.audit.map(record => [record.operation, record.user, record.from, record.to]), 'audit')
					.to.deep.equal([
						['run', null, 'held', 'needsReview'],
						['reject', 'root', 'needsReview', 'completed']
					]);
			});
			getLatestLiveSubmissionValues(formName).then(values => {
				expect(values.fullName, 'stored name').to.eq('Ada Review');
				expect(values[SCAN_STATUS], 'stored scan status').to.eq(FAILED);
			});
			getLatestLiveFormSubmission(formName).then(({path}) => {
				liveNodeExists(`${path}/files`).should('eq', false);
			});
		});
		readUploadObservations().its('observer').should('deep.equal', [
			{action: 'afterRelease', list: 'deferredActions', files: 0}
		]);
	});

	it('Q44: the editor of a form offers the policy as a switch in the section of the quarantine switch', () => {
		const policyOf = (fieldSets: EditorFieldSet[]) => fieldSets.find(fieldSet => fieldSet.name === 'fmdbqmix:quarantinePolicy');
		createPublishedLiveFormPage('policy-editor-off-form', 'Policy editor off form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: []
		});
		createForm('policy-editor-on-form', 'releaseValues');

		switchSection(`${CONTENT_PATH}/policy-editor-off-form`).then(fieldSets => {
			expect(policyOf(fieldSets), 'the policy on a form with the switch on and no policy').to.deep.equal(
				{name: 'fmdbqmix:quarantinePolicy', dynamic: true, activated: false, displayName: 'Quarantine policy'});
		});
		switchSection(`${CONTENT_PATH}/policy-editor-on-form`).then(fieldSets => {
			expect(policyOf(fieldSets), 'the policy on a form that has it').to.deep.equal(
				{name: 'fmdbqmix:quarantinePolicy', dynamic: true, activated: true, displayName: 'Quarantine policy'});
			const names = fieldSets.map(fieldSet => fieldSet.name);
			cy.log(`fieldsets of the section: ${names.join(', ')}`);
			expect(names.indexOf('fmdbqmix:quarantinePolicy'), 'the policy comes after the quarantine switch')
				.to.be.greaterThan(names.indexOf('fmdbmix:quarantine'));
			expect(names.slice(0, 3), 'the form, then the quarantine switch, then the policy')
				.to.deep.equal(['fmdb:form', 'fmdbmix:quarantine', 'fmdbqmix:quarantinePolicy']);
		});
	});
});

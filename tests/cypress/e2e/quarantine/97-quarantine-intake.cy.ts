import {createUser, deleteUser, enableModule, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {QUARANTINE_INSPECTION_PATH} from '../../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getInputFileNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode,
	getUploadObserverActionNode,
	visitLiveForm
} from '../../support/fixtures';
import {
	SAME_ORIGIN,
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	withSameOriginHeaders
} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	FILE_FIELD,
	FILE_NAME,
	FILE_SHA256,
	FILE_TEXT,
	fields,
	readHeldSubmissions,
	readUploadObservations,
	resetUploadObservations,
	setUploadsRequireQuarantine,
	submissionWithAFile,
	submissionWithNoFile
} from './support';

const SITE_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}`;
const quarantineOf = (formId: string): string => `${SITE_PATH}/formidable-quarantine/${formId}`;
const resultsOf = (formName: string): string => `${SITE_PATH}/formidable-results/${formName}`;

/** The three readers of Q10. Each is an editor of the site, which makes the account privileged enough for GraphQL. */
const PASSWORD = 'Quarantine#1234';
const READERS = [
	{name: 'quarResultsReader', siteRoles: ['editor'], formRoles: ['fmdb-results-reader']},
	{name: 'quarEditor', siteRoles: ['editor'], formRoles: []},
	{name: 'quarSiteAdmin', siteRoles: ['site-administrator'], formRoles: []}
];

interface GraphqlBody {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any -- each query of the spec reads its own shape
	data?: any;
	errors?: Array<{message?: string}>;
}

/**
 * A live node as the current session reads it through GraphQL, or null when the read is refused. A refused read is
 * nodeByPath null with a PathNotFoundException for the path; any other error fails, so a null is never a mistake of
 * the query (as in security/43-results-access.cy.ts).
 */
const readNodeAs = (expectedUser: string, path: string): Cypress.Chainable<{name: string} | null> => cy.request<GraphqlBody>({
	method: 'POST',
	url: '/modules/graphql',
	failOnStatusCode: false,
	headers: withSameOriginHeaders(),
	body: {
		query: 'query($path: String!) { currentUser { name } jcr(workspace: LIVE) { nodeByPath(path: $path) { name } } }',
		variables: {path}
	}
}).then(response => {
	expect(response.body.data?.currentUser?.name, 'authenticated user for the read').to.eq(expectedUser);
	const unexpected = (response.body.errors ?? [])
		.filter(error => !(error.message ?? '').includes(`PathNotFoundException: ${path}`));
	expect(unexpected, 'unexpected GraphQL errors').to.deep.eq([]);
	const node: {name: string} | null = response.body.data?.jcr?.nodeByPath ?? null;
	return cy.wrap(node, {log: false});
});

/** The values of one property of a live node, read as root. */
const liveValues = (path: string, property: string): Cypress.Chainable<string[]> => cy.request<GraphqlBody>({
	method: 'POST',
	url: '/modules/graphql',
	headers: {Origin: SAME_ORIGIN},
	body: {
		query: 'query($path: String!, $name: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { property(name: $name) { values } } } }',
		variables: {path, name: property}
	}
}).then(response => {
	expect(response.body.errors, `errors of the read of ${property} on ${path}`).to.equal(undefined);
	return response.body.data.jcr.nodeByPath.property.values as string[];
});

/** Whether a live node exists, read as root. */
const liveNodeExists = (path: string): Cypress.Chainable<boolean> => readNodeAs('root', path).then(node => node !== null);

/** A request that a reader without access must see refused or not found. */
const expectRefused = (url: string): void => {
	cy.request({url, failOnStatusCode: false}).its('status').should('be.oneOf', [401, 403, 404]);
};

const groovy = (script: string, replacements: Record<string, string>): Cypress.Chainable<string> =>
	cy.executeGroovy(script, replacements).then(result => String(result));

const setReadOnlyMode = (enable: boolean): void => {
	groovy('groovy/switchReadOnlyMode.groovy', {__ENABLE__: String(enable)})
		.should('not.contain', '.failed');
};

/**
 * The intake of the reference hold of the quarantine test module (tests/scenarios/quarantine.md, Q01, Q10, Q11, Q16).
 * A form whose "Checks" list holds a check of the module is owned by the reference hold: it stores the submission in
 * the quarantine tree of the site, /sites/<site>/formidable-quarantine/<form>/YYYY/MM/DD/<held>, and returns. Nothing
 * runs after it until a release, which this spec never starts. The spec reads the tree through the inspection of the
 * module, as root: the state, the names of the fields, and of each item its file name, size and SHA-256.
 *
 * Q10 is a control scenario. Its control build is the quarantine test module with the line
 * `root.setAclInheritanceBreak(true);` removed from JcrQuarantineStore.rootOf, so that the quarantine root keeps the
 * ACL inheritance of the site. Build it with `package`, deploy it in place of the module, and run this spec: Q10 must
 * fail by assertion, on the read of the site administrator or of the editor. The root of the tree is created on first
 * use, and the spec creates the site again, so no root of an earlier build survives.
 */
describe('Quarantine - 97 The reference hold stores a submission in the quarantine tree', () => {
	useFormidableSite();

	before(() => {
		enableModule(QUARANTINE_MODULE_ID, FORMIDABLE_TEST_SITE.key);
		setUploadsRequireQuarantine(false);
		setReadOnlyMode(false);
	});

	beforeEach(() => {
		resetUploadObservations();
	});

	after(() => {
		// never leave the platform read-only for the rest of the suite, even when Q16 failed midway
		setReadOnlyMode(false);
	});

	it('Q01: a submission with a file is held, with its values and the SHA-256 of its file, and nothing runs after it', () => {
		const formName = 'quarantine-held-form';
		createPublishedLiveFormPage(formName, 'Quarantine held form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [
				getSaveToJcrActionNode(),
				getUploadObserverActionNode('afterRelease')
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()})
				.then(expectSuccessResponse);

			cy.login();
			readHeldSubmissions(formId).then(held => {
				expect(held, 'held submissions of the form').to.have.length(1);
				expect(held[0].state, 'state').to.eq('held');
				expect(held[0].fields, 'fields held').to.include('fullName');
				expect(held[0].valuesPresent, 'values present').to.eq(true);
				expect(held[0].items, 'items').to.deep.equal([{
					name: 'item0',
					fieldName: FILE_FIELD,
					fileName: FILE_NAME,
					mimeType: 'text/plain',
					size: FILE_TEXT.length,
					sha256: FILE_SHA256,
					binaryPresent: true
				}]);
				liveValues(`${held[0].path}/values`, 'fullName').should('deep.equal', ['Ada']);
			});
		});

		// The reference hold owned the form: the sample hold was not called, and no action after release ran. The
		// observer action reports no call, and Save to JCR stored nothing in the results of the form. The observer is
		// deferrable as an e-mail action is, so it stands for every action after release, with no mailbox.
		readUploadObservations().then(seen => {
			expect(seen.hold, 'calls of the sample hold').to.deep.equal([]);
			expect(seen.observer, 'calls of the observer action after release').to.deep.equal([]);
		});
		liveNodeExists(resultsOf(formName)).should('eq', false);
	});

	it('Q01: a form with no check of the module goes to the sample hold, and nothing is held', () => {
		createPublishedLiveFormPage('quarantine-delegated-form', 'Quarantine delegated form', fields(), undefined, undefined, {
			deferredActions: [getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Grace'), headers: withSameOriginHeaders()})
				.then(expectSuccessResponse);

			cy.login();
			readHeldSubmissions(formId).should('deep.equal', []);
		});

		readUploadObservations().then(seen => {
			expect(seen.hold, 'files of each call of the sample hold').to.deep.equal([1]);
			expect(seen.observer, 'calls of the observer action after release').to.deep.equal([
				{action: 'afterRelease', list: 'deferredActions', files: 1}
			]);
		});
	});

	describe('Q10: no reader role reads the quarantine tree (control)', () => {
		const formName = 'quarantine-acl-form';
		let heldFormId: string;
		let heldPath: string;
		let itemPath: string;
		let rootPath: string;

		before(() => {
			cy.login();
			READERS.forEach(reader => {
				createUser(reader.name, PASSWORD);
				grantRoles(SITE_PATH, reader.siteRoles, reader.name, 'USER');
			});
			createPublishedLiveFormPage(formName, 'Quarantine ACL form', fields(), undefined, undefined, {
				qualifiers: [getRemoteStubCheckNode()],
				deferredActions: [getSaveToJcrActionNode()]
			}).then(({formId, formPath}) => {
				READERS.filter(reader => reader.formRoles.length > 0)
					.forEach(reader => grantRoles(formPath, reader.formRoles, reader.name, 'USER'));
				publishAndWaitJobEnding(formPath);
				rootPath = `${SITE_PATH}/formidable-quarantine`;
				heldFormId = formId;

				cy.logout();
				postDirectMultipartSubmission({formId, body: submissionWithAFile('Hedy'), headers: withSameOriginHeaders()})
					.then(expectSuccessResponse);

				cy.login();
				readHeldSubmissions(formId).then(held => {
					expect(held, 'held submissions of the form').to.have.length(1);
					heldPath = held[0].path;
					itemPath = `${heldPath}/${held[0].items[0].name}`;
				});
			});
			cy.logout();
		});

		after(() => {
			cy.login();
			READERS.forEach(reader => deleteUser(reader.name));
		});

		it('root reads the held submission: the paths below are real', () => {
			readNodeAs('root', heldPath).should('not.be.null');
			readNodeAs('root', itemPath).should('not.be.null');
		});

		READERS.forEach(reader => {
			it(`${reader.name} (${[...reader.siteRoles, ...reader.formRoles].join(', ')}) reads neither the held submission nor its item`, () => {
				cy.logout();
				cy.login(reader.name, PASSWORD);

				readNodeAs(reader.name, rootPath).should('be.null');
				readNodeAs(reader.name, heldPath).should('be.null');
				readNodeAs(reader.name, `${heldPath}/values`).should('be.null');
				readNodeAs(reader.name, itemPath).should('be.null');
				expectRefused(`/files/live${itemPath}`);
				expectRefused(`/modules/api/jcr/v1/live/en/paths${heldPath}`);
				expectRefused(`/modules/api/jcr/v1/live/en/paths${itemPath}`);
				// the inspection of the module answers server administrators alone
				cy.request({
					url: QUARANTINE_INSPECTION_PATH,
					qs: {site: FORMIDABLE_TEST_SITE.key, form: heldFormId},
					failOnStatusCode: false
				}).its('status').should('eq', 403);
			});
		});

		it('the quarantine root cuts its ACL inheritance and holds no access control entry', () => {
			groovy('groovy/assertQuarantineRootAcl.groovy', {__PATH__: rootPath}).should('not.contain', '.failed');
		});
	});

	it('Q11: the quarantine tree is inert: no file node, no extracted text, no thumbnail', () => {
		const attachments = {
			name: 'attachments',
			title: 'Attachments',
			accept: ['application/pdf', 'image/png'],
			multiple: true
		};
		const plainFormName = 'quarantine-plain-pdf-form';
		let heldFormId: string;

		createPublishedLiveFormPage('quarantine-inert-form', 'Quarantine inert form',
			[getInputTextNode({name: 'fullName', title: 'Full name'}), getInputFileNode(attachments)], undefined, undefined, {
				qualifiers: [getRemoteStubCheckNode()],
				deferredActions: [getSaveToJcrActionNode()]
			}).then(({formId, livePath}) => {
			heldFormId = formId;
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Inert Intake');
			form.getFileInput(attachments.name)
				.attachFile(['cypress/fixtures/files/document.pdf', 'cypress/fixtures/files/cats.png'])
				.shouldHaveSelectedFileCount(2);
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});

		// The same PDF on a form with the switch off: once the core extracted its text, it would have done so on the
		// held PDF too, had the tree been a file model.
		createPublishedLiveFormPage(plainFormName, 'Quarantine plain PDF form',
			[getInputTextNode({name: 'fullName', title: 'Full name'}), getInputFileNode({name: 'document', title: 'Document', accept: ['application/pdf']})],
			undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Plain Intake');
			form.getFileInput('document').attachFile('cypress/fixtures/files/document.pdf').shouldHaveSelectedFile('document.pdf');
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
		getLatestLiveFormSubmission(plainFormName).then(({path}) => {
			cy.waitUntil(
				() => groovy('groovy/assertExtractedTextUnder.groovy', {__PATH__: path}).then(result => !result.includes('.failed')),
				{timeout: 60000, interval: 2000, errorMsg: 'The platform extracted no text from the PDF of the form with the switch off: Q11 proves nothing on this platform'}
			);
		});

		cy.then(() => {
			readHeldSubmissions(heldFormId).then(held => {
				expect(held, 'held submissions of the form').to.have.length(1);
				expect(held[0].items.map(item => item.fileName).sort(), 'items').to.deep.equal(['cats.png', 'document.pdf']);
				held[0].items.forEach(item => expect(item.binaryPresent, `binary of ${item.fileName}`).to.eq(true));
			});
			groovy('groovy/assertQuarantineTreeInert.groovy', {__PATH__: quarantineOf(heldFormId), __ITEMS__: '2'})
				.should('not.contain', '.failed');
		});
	});

	// The engine presumes that a form with the switch on writes, whatever its actions: the two forms differ only by
	// the kind of their action after release.
	[
		{formName: 'quarantine-readonly-form', what: 'Save to JCR', deferredActions: () => [getSaveToJcrActionNode()]},
		{
			formName: 'quarantine-readonly-compatible-form',
			what: 'only a read-only compatible action',
			deferredActions: () => [getUploadObserverActionNode('afterRelease')]
		}
	].forEach(({formName, what, deferredActions}) => {
		it(`Q16: in read-only maintenance, a form with a check of the module and ${what} after release answers FMDB-014 and writes nothing`, () => {
			createPublishedLiveFormPage(formName, 'Quarantine read-only form', fields(), undefined, undefined, {
				qualifiers: [getRemoteStubCheckNode()],
				deferredActions: deferredActions()
			}).then(({formId}) => {
				cy.logout();
				setReadOnlyMode(true);

				postDirectMultipartSubmission({formId, body: submissionWithAFile('Katherine'), headers: withSameOriginHeaders()})
					.then(response => expectErrorResponse(response, 503, 'FMDB-014'));
				postDirectMultipartSubmission({formId, body: submissionWithNoFile('Katherine'), headers: withSameOriginHeaders()})
					.then(response => expectErrorResponse(response, 503, 'FMDB-014'));

				setReadOnlyMode(false);
				cy.login();
				readHeldSubmissions(formId).should('deep.equal', []);
				liveNodeExists(quarantineOf(formId)).should('eq', false);
				liveNodeExists(resultsOf(formName)).should('eq', false);
			});
			readUploadObservations().then(seen => {
				expect(seen.hold, 'calls of the sample hold').to.deep.equal([]);
				expect(seen.observer, 'calls of the observer action').to.deep.equal([]);
			});
		});
	});
});

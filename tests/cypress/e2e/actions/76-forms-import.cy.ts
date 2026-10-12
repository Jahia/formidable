import {createUser, deleteNode, deleteUser, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {createFormNode, FORMIDABLE_TEST_SITE, getSelectNode, type JahiaNode} from '../../support/fixtures';
import {withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

const SITE = FORMIDABLE_TEST_SITE.key;
const RESULTS_PAGE = `/jahia/jcontent/${SITE}/en/apps/formidableResults`;
const CONTENTS_PATH = `/sites/${SITE}/contents`;
const IMPORTED_FORMS_PATH = `${CONTENTS_PATH}/imported-forms`;
const RESULTS_ROOT_PATH = `/sites/${SITE}/formidable-results`;
const SAMPLE_EXPORT = 'cypress/fixtures/imports/formFactory-sample.zip';
const SAMPLE_FIXTURE = 'imports/formFactory-sample.zip';
const NO_IDS_EXPORT = 'cypress/fixtures/imports/formFactory-no-ids.zip';
const NOT_A_ZIP = 'cypress/fixtures/imports/conditional-logic-form.xml';
const FORMS_IMPORT_PID = 'org.jahia.modules.formidable.formsImport';
const IMPORT_ENDPOINT = '/modules/formidable-engine/import';

/** The anonymised sample export: 3 forms, 106 submissions. */
const SAMPLE = {
	forms: ['contact-us', 'newsletterregistration', 'newsletter-registration'],
	submissions: {'contact-us': 44, newsletterregistration: 18, 'newsletter-registration': 44},
	contactFields: ['your-first-name', 'your-last-name', 'your-email-address', 'your-telephone-number', 'your-enquiry'],
	contactFieldTypes: ['fmdb:inputText', 'fmdb:inputText', 'fmdb:inputEmail', 'fmdb:inputText', 'fmdb:textarea'],
	contactFormId: '08246a0f-de43-4dbb-b91e-55cdd366614b',
	firstSubmission: {id: '2c591198-6092-485b-b936-2a8cbf0213c8', day: '2023/06/20'}
};

/**
 * The form built by hand the results of newsletterregistration (firstname, lastname, enter-your-email-here)
 * attach to: a field of the same node name, one of the same label, one the administrator picks, and a choice
 * field whose options carry none of the imported values.
 */
const ATTACH = {
	source: 'newsletterregistration',
	form: 'newsletter',
	title: 'Newsletter',
	fields: [
		{name: 'firstname', title: 'First name', type: 'fmdb:inputText'},
		{name: 'surname', title: 'Lastname', type: 'fmdb:inputText'},
		{name: 'email', title: 'Email', type: 'fmdb:inputEmail'}
	],
	choice: {name: 'topic', title: 'Topic', options: [{value: 'sales', label: 'Sales'}, {value: 'support', label: 'Support'}]}
};
const MENU = '.moonstone-menu:not(.moonstone-hidden)';
/** The overlay a Moonstone menu opens over: a click on it closes the menu without picking anything. */
const MENU_OVERLAY = '.moonstone-menu_overlay';

/**
 * The form built by hand the results of contact-us (your-first-name, your-last-name, your-email-address,
 * your-telephone-number, your-enquiry) and of newsletterregistration attach to through the action on the entry.
 */
const CONTACT = {
	form: 'contact',
	title: 'Contact',
	fields: [
		{name: 'your-first-name', title: 'First name', type: 'fmdb:inputText'},
		{name: 'email', title: 'Email', type: 'fmdb:inputEmail'},
		{name: 'message', title: 'Message', type: 'fmdb:textarea'}
	]
};
/** A reader of the results of that form: the attachment hands the entry to them. */
const READER = {name: 'importAttachReader', password: 'ImportAttach#1234'};

type Workspace = 'EDIT' | 'LIVE';
type NamedNode = {name: string; primaryNodeType: {name: string}};
type SubmissionData = {path: string; origin: {value: string}; data: {properties: Array<{name: string; value: string}>}};

const setImportButton = (enabled: boolean) =>
	cy.runProvisioningScript({
		script: {
			fileContent: JSON.stringify([{editConfiguration: FORMS_IMPORT_PID, properties: {importButtonEnabled: String(enabled)}}]),
			type: 'application/json'
		}
	});

const graphql = <T>(query: string, variables: Record<string, unknown>): Cypress.Chainable<T> =>
	cy.request({
		method: 'POST',
		url: '/modules/graphql',
		headers: withSameOriginHeaders(),
		body: {query, variables}
	}).then(response => {
		const body = response.body as {errors?: Array<{message?: string}>; data?: T};
		expect(body.errors, 'GraphQL errors').to.be.undefined;
		return cy.wrap(body.data as T, {log: false});
	});

/** The children of a node, in order, by name and type. */
const children = (path: string, workspace: Workspace): Cypress.Chainable<NamedNode[]> =>
	graphql<{jcr: {nodeByPath: {children: {nodes: NamedNode[]}}}}>(
		`query Children($path: String!, $workspace: Workspace!) { jcr(workspace: $workspace) {
			nodeByPath(path: $path) { children { nodes { name primaryNodeType { name } } } } } }`,
		{path, workspace}
	).then(data => data.jcr.nodeByPath.children.nodes);

const countSubmissions = (entryPath: string): Cypress.Chainable<number> =>
	graphql<{jcr: {nodesByQuery: {pageInfo: {totalCount: number}}}}>(
		`query Count($query: String!) { jcr(workspace: LIVE) {
			nodesByQuery(query: $query, queryLanguage: SQL2, limit: 1) { pageInfo { totalCount } } } }`,
		{query: `SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '${entryPath}')`}
	).then(data => data.jcr.nodesByQuery.pageInfo.totalCount);

/** The imported submission of that Forms identity, with the names its values landed under. */
const importedSubmission = (sourceId: string): Cypress.Chainable<SubmissionData> =>
	graphql<{jcr: {nodesByQuery: {nodes: SubmissionData[]}}}>(
		`query Imported($query: String!) { jcr(workspace: LIVE) { nodesByQuery(query: $query, queryLanguage: SQL2, limit: 1) { nodes {
			path origin: property(name: "origin") { value } data: descendant(relPath: "data") { properties { name value } } } } } }`,
		{query: `SELECT * FROM [fmdbmix:importedSubmission] AS s WHERE ISDESCENDANTNODE(s, '${RESULTS_ROOT_PATH}') AND s.[sourceId] = '${sourceId}'`}
	).then(data => {
		const [submission] = data.jcr.nodesByQuery.nodes;
		expect(submission, `the submission ${sourceId}`).to.exist;
		return cy.wrap(submission, {log: false});
	});

const valueNames = (submission: SubmissionData) => submission.data.properties.filter(p => !p.name.includes(':')).map(p => p.name);

/** The first submissions under an entry, with the names their values landed under. */
const submissionsUnder = (entryPath: string, limit = 5): Cypress.Chainable<SubmissionData[]> =>
	graphql<{jcr: {nodesByQuery: {nodes: SubmissionData[]}}}>(
		`query Under($query: String!, $limit: Int!) { jcr(workspace: LIVE) { nodesByQuery(query: $query, queryLanguage: SQL2, limit: $limit) { nodes {
			path origin: property(name: "origin") { value } data: descendant(relPath: "data") { properties { name value } } } } } }`,
		{query: `SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '${entryPath}')`, limit}
	).then(data => data.jcr.nodesByQuery.nodes);

/** The parentForm of an entry, as stored: absent on an entry imported without a form. */
const parentFormOf = (entryPath: string): Cypress.Chainable<string | null> =>
	graphql<{jcr: {nodeByPath: {parentForm: {value: string} | null; imported: boolean}}}>(
		`query Entry($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) {
			parentForm: property(name: "parentForm") { value } imported: isNodeType(type: {types: ["fmdbmix:importedResults"]}) } } }`,
		{path: entryPath}
	).then(data => {
		expect(data.jcr.nodeByPath.imported, `${entryPath} carries fmdbmix:importedResults`).to.equal(true);
		return cy.wrap(data.jcr.nodeByPath.parentForm?.value ?? null, {log: false});
	});

/** The endpoint of the dialog, called as the dialog calls it. */
const importApi = (method: 'GET' | 'POST' | 'DELETE', path: string) =>
	cy.request({method, url: `${IMPORT_ENDPOINT}${path}?site=${SITE}`, headers: withSameOriginHeaders(), failOnStatusCode: false});

/** Uploads an export through the endpoint with the page's fetch, as the dialog does, and gives the job id. */
const uploadThroughApi = (fixture: string): Cypress.Chainable<string> =>
	cy.fixture(fixture, 'binary').then(binary =>
		cy.window().then({timeout: 60000}, win => {
			const form = new win.FormData();
			form.append('file', Cypress.Blob.binaryStringToBlob(binary, 'application/zip'), 'export.zip');
			return win.fetch(`${IMPORT_ENDPOINT}/jobs?site=${SITE}`, {method: 'POST', body: form, credentials: 'same-origin'})
				.then(response => response.json())
				.then((job: {id: string}) => job.id);
		})
	);

/** Polls a job with the page's fetch until it reaches the state; a failed job fails the test. */
const waitForJobState = (jobId: string, state: string) =>
	cy.window().then({timeout: 120000}, win => new Promise<void>((resolve, reject) => {
		const poll = () => {
			win.fetch(`${IMPORT_ENDPOINT}/jobs/${jobId}?site=${SITE}`, {credentials: 'same-origin'})
				.then(response => response.json())
				.then((job: {state: string; message?: string}) => {
					if (job.state === state) {
						resolve();
					} else if (job.state === 'failed') {
						reject(new Error(`the job ${jobId} failed: ${job.message}`));
					} else {
						win.setTimeout(poll, 500);
					}
				})
				.catch(reject);
		};
		poll();
	}));

const openResultsPage = () => {
	cy.visit(RESULTS_PAGE);
	cy.get('[data-sel-role="import-results"]', {timeout: 60000}).should('be.visible');
};

const openImportDialog = () => {
	cy.get('[data-sel-role="import-results"]').click();
	cy.get('[data-sel-role="import-results-dialog"]').should('have.attr', 'open');
};

const dropFile = (file: string) => cy.get('[data-sel-role="import-file-input"]').selectFile(file, {force: true});

const dialogState = (state: string, timeout = 120000) =>
	cy.get(`[data-sel-role="import-results-dialog"][data-sel-state="${state}"]`, {timeout}).should('exist');

const closeDialog = () => {
	cy.get('[data-sel-role="import-close"]').click();
	cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');
};

const reportForm = (name: string) => cy.get(`[data-sel-role="import-report-form"][data-sel-name="${name}"]`);

/** Picks, for a form of the dry run, what receives its results: the radio items carry an id per form and choice. */
const choose = (name: string, choice: 'resultsOnly' | 'create' | 'attach') =>
	cy.get(`#import-choice-${name}-${choice}`).check({force: true});

/** Opens a Moonstone dropdown of the dialog, once it has loaded its data, and picks the item of that label. */
const pickInDropdown = (dropdown: () => Cypress.Chainable<JQuery>, label: string) => {
	dropdown().find('.moonstone-dropdown_loading', {timeout: 15000}).should('not.exist');
	dropdown().find('.moonstone-dropdown_container').click();
	cy.get(MENU).contains('.moonstone-menuItem', label).click();
	cy.get(MENU).should('not.exist');
};

const mappingRow = (source: string) => cy.get(`[data-sel-role="import-mapping-row"][data-sel-source="${source}"]`);

const formNodes = (fields: Array<{name: string; title: string; type: string}>): JahiaNode[] =>
	fields.map(field => ({name: field.name, primaryNodeType: field.type, properties: [{name: 'jcr:title', value: field.title, language: 'en'}]}));

/** Runs the import of the sample through the endpoint, as the dialog does, with the default choice. */
const importSampleThroughApi = () =>
	uploadThroughApi(SAMPLE_FIXTURE).then(jobId => {
		waitForJobState(jobId, 'review');
		importApi('POST', `/jobs/${jobId}/import`).its('status').should('eq', 200);
		waitForJobState(jobId, 'done');
		importApi('DELETE', `/jobs/${jobId}`).its('status').should('eq', 200);
	});

const openAttachDialog = (entryName: string) => {
	openResultsPage();
	selectEntry(entryName);
	cy.get('[data-sel-role="attach-results"]').click();
	cy.get('[data-sel-role="attach-results-dialog"][data-sel-state="review"]', {timeout: 30000}).should('exist');
};

const attachDialogState = (state: string, timeout = 120000) =>
	cy.get(`[data-sel-role="attach-results-dialog"][data-sel-state="${state}"]`, {timeout}).should('exist');

const closeAttachDialog = () => {
	cy.get('[data-sel-role="attach-close"]').click();
	cy.get('[data-sel-role="attach-results-dialog"]').should('not.exist');
};

/** The properties of an entry the attachment writes: the form, the sources and the mappings it holds, and whether it is still marked imported. */
const entryOf = (entryPath: string) =>
	graphql<{jcr: {nodeByPath: {parentForm: {value: string} | null; sourceFormIds: {values: string[]} | null; mappings: {values: string[]} | null; imported: boolean} | null}}>(
		`query Entry($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) {
			parentForm: property(name: "parentForm") { value } sourceFormIds: property(name: "sourceFormIds") { values }
			mappings: property(name: "mappings") { values } imported: isNodeType(type: {types: ["fmdbmix:importedResults"]}) } } }`,
		{path: entryPath}
	).then(data => data.jcr.nodeByPath);

/** Reads a live node with the current session: null when that user may not read it. */
const readAsCurrentUser = (path: string) =>
	cy.request({
		method: 'POST',
		url: '/modules/graphql',
		failOnStatusCode: false,
		headers: withSameOriginHeaders(),
		body: {query: `query Read($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { uuid } } }`, variables: {path}}
	}).then(response => (response.body as {data?: {jcr?: {nodeByPath?: {uuid: string} | null}}}).data?.jcr?.nodeByPath ?? null);

// The caption inside the entry sits at its centre, which Cypress takes for a cover: the click is forced.
const selectEntry = (name: string) =>
	cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${name}"]`).should('be.visible').click({force: true});

const openFirstSubmission = (name: string) => {
	openResultsPage();
	selectEntry(name);
	cy.get('[data-sel-role="submissions-table"] tbody tr', {timeout: 30000}).first().click();
};

/**
 * The Import button of the Results page, behind the importButtonEnabled setting, takes the zip export of a
 * Jahia Forms formFactory node: a dry run shows what the import will do, and the administrator chooses, per
 * form, what receives its results — the results alone, the default, as an entry without a form, or a form
 * created in the imported-forms folder. One import runs at a time per site, and a later run adds only what
 * is missing, under the names the fields have by then.
 */
describe('Actions - 76 Importing the forms and results of Jahia Forms', () => {
	useFormidableSite();

	before(() => {
		cy.login();
		setImportButton(false);
	});

	after(() => {
		cy.login();
		setImportButton(false);
		deleteUser(READER.name);
	});

	it('hides the Import button while the setting is off', () => {
		cy.visit(RESULTS_PAGE);
		cy.get('[data-sel-role="form-results-empty"], [data-sel-role="form-results-list"]', {timeout: 60000}).should('be.visible');
		cy.get('[data-sel-role="import-results"]').should('not.exist');
		setImportButton(true);
	});

	it('imports the results alone by default, under entries without a form, refusing a second import meanwhile', () => {
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);

		dialogState('review');
		cy.get('[data-sel-role="import-report-form"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		// the default: the results alone, a choice the administrator can still change per form
		cy.get('[data-sel-role="import-report-form"][data-sel-outcome="resultsOnly"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-choice"] input[value="resultsOnly"]:checked').should('have.length', SAMPLE.forms.length);
		reportForm('contact-us').should('contain', 'Contact Us').and('contain', 'Results only');
		// the dry run wrote nothing
		children(CONTENTS_PATH, 'EDIT').then(nodes => expect(nodes.map(n => n.name)).not.to.include('imported-forms'));

		// a second dry run, reviewed and ready, waits for the Import of the first to be clicked
		uploadThroughApi(SAMPLE_FIXTURE).then(otherJob => {
			waitForJobState(otherJob, 'review');
			cy.intercept('POST', `${IMPORT_ENDPOINT}/jobs/*/import*`).as('startImport');
			cy.get('[data-sel-role="import-confirm"]').click();
			cy.wait('@startImport').its('response.statusCode').should('eq', 200);
			// the scheduler holds one import job per site: the second is refused with the reason
			importApi('POST', `/jobs/${otherJob}/import`).then(refused => {
				expect(refused.status).to.equal(409);
				expect(refused.body.error).to.contain('another import is running');
			});
			importApi('DELETE', `/jobs/${otherJob}`).its('status').should('eq', 200);
		});

		// the dialog closes while the import runs, and opens again on the same job
		closeDialog();
		openImportDialog();
		dialogState('done');
		cy.get('[data-sel-role="import-next-step"]').should('be.visible').and('contain', 'without a form');
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');

		// the report survives a reload until it is closed
		cy.reload();
		dialogState('done', 60000);
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		closeDialog();
		cy.get('[data-sel-role="form-results-entry"]', {timeout: 60000}).should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="form-results-entry"] [data-sel-role="form-imported"]').should('have.length', SAMPLE.forms.length);
		cy.reload();
		cy.get('[data-sel-role="form-results-entry"]', {timeout: 60000}).should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');

		// no form was created, the entries carry the marker and no parentForm
		children(CONTENTS_PATH, 'EDIT').then(nodes => expect(nodes.map(n => n.name)).not.to.include('imported-forms'));
		SAMPLE.forms.forEach(form => parentFormOf(`${RESULTS_ROOT_PATH}/${form}`).should('be.null'));
	});

	it('wrote every submission under the system names of its fields, dated as the visitor submitted it', () => {
		Object.entries(SAMPLE.submissions).forEach(([form, count]) => {
			countSubmissions(`${RESULTS_ROOT_PATH}/${form}`).should('eq', count);
		});
		importedSubmission(SAMPLE.firstSubmission.id).then(submission => {
			expect(submission.path).to.contain(`${RESULTS_ROOT_PATH}/contact-us/submissions/${SAMPLE.firstSubmission.day}/`);
			expect(submission.origin.value).to.equal('jahia-forms');
			expect(valueNames(submission)).to.have.members(SAMPLE.contactFields);
			const email = submission.data.properties.find(p => p.name === 'your-email-address');
			expect(email?.value).to.match(/@example\.com$/);
		});
	});

	it('shows an entry without a form as Imported, no form, with the system names as columns', () => {
		openFirstSubmission('contact-us');
		cy.get('[data-sel-role="form-imported"]').should('exist');
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'your-first-name');
	});

	it('imports nothing on a second run: every entry is found, nothing is left to choose', () => {
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		cy.get('[data-sel-role="import-nothing"]').should('be.visible');
		cy.get('[data-sel-role="import-report-form"][data-sel-outcome="found"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-choice"]').should('not.exist');
		cy.get('[data-sel-role="import-confirm"]').should('not.exist');
		closeDialog();
	});

	it('creates the forms when asked, once the entries written alone are gone', () => {
		SAMPLE.forms.forEach(form => deleteNode(`${RESULTS_ROOT_PATH}/${form}`, 'LIVE'));

		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		SAMPLE.forms.forEach(form => choose(form, 'create'));
		cy.get('[data-sel-role="import-report-form"][data-sel-outcome="created"]').should('have.length', SAMPLE.forms.length);
		reportForm('contact-us').should('contain', IMPORTED_FORMS_PATH);
		cy.get('[data-sel-role="import-confirm"]').click();
		dialogState('done');
		cy.get('[data-sel-role="import-next-step"]').should('contain', 'Imported from Jahia Forms');
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		closeDialog();
		cy.get('[data-sel-role="form-results-entry"]', {timeout: 60000}).should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="form-results-entry"] [data-sel-role="form-unpublished"]').should('have.length', SAMPLE.forms.length);
	});

	it('created the forms in the imported-forms folder, with their fields, labels, markers and actions', () => {
		children(IMPORTED_FORMS_PATH, 'EDIT').then(forms => {
			expect(forms.filter(f => f.primaryNodeType.name === 'fmdb:form').map(f => f.name)).to.have.members(SAMPLE.forms);
		});
		children(`${IMPORTED_FORMS_PATH}/contact-us/fields`, 'EDIT').then(fields => {
			expect(fields.map(f => f.name)).to.deep.equal(SAMPLE.contactFields);
			expect(fields.map(f => f.primaryNodeType.name)).to.deep.equal(SAMPLE.contactFieldTypes);
		});
		children(`${IMPORTED_FORMS_PATH}/contact-us/actions`, 'EDIT').then(actions => {
			expect(actions.map(a => a.primaryNodeType.name)).to.deep.equal(['fmdb:save2jcrAction']);
		});
		graphql<{jcr: {nodeByPath: {
			title: {value: string}; titleFr: {value: string}; mixins: Array<{name: string}>; sourceId: {value: string};
			field: {title: {value: string}; placeholder: {value: string}; sourceName: {value: string}};
		}}}>(
			`query ContactUs($path: String!) { jcr(workspace: EDIT) { nodeByPath(path: $path) {
				title: property(name: "jcr:title", language: "en") { value }
				titleFr: property(name: "jcr:title", language: "fr") { value }
				mixins: mixinTypes { name }
				sourceId: property(name: "sourceId") { value }
				field: descendant(relPath: "fields/your-first-name") {
					title: property(name: "jcr:title", language: "fr") { value }
					placeholder: property(name: "placeholder", language: "en") { value }
					sourceName: property(name: "sourceName") { value }
				}
			} } }`,
			{path: `${IMPORTED_FORMS_PATH}/contact-us`}
		).then(data => {
			const form = data.jcr.nodeByPath;
			expect(form.title.value).to.equal('Contact Us');
			expect(form.titleFr.value).to.equal('Contact Us');
			expect(form.mixins.map(m => m.name)).to.include('fmdbmix:importedForm');
			expect(form.sourceId.value).to.equal(SAMPLE.contactFormId);
			expect(form.field.title.value).to.equal('Votre prénom');
			expect(form.field.placeholder.value).to.equal('Your First name*');
			expect(form.field.sourceName.value).to.equal('text-input_0_1');
		});
		Object.entries(SAMPLE.submissions).forEach(([form, count]) => {
			countSubmissions(`${RESULTS_ROOT_PATH}/${form}`).should('eq', count);
		});
		// the prefill of the first name from the visitor profile became its mapping, where the jExperience integration stands;
		// an unknown type is an error for nodeTypeByName, so the probe takes the answer with its errors, as the playground does
		cy.request({
			method: 'POST',
			url: '/modules/graphql',
			failOnStatusCode: false,
			headers: withSameOriginHeaders(),
			body: {query: '{ jcr { nodeTypeByName(name: "fmdbmix:jExperienceProfileMapping") { name } } }'}
		}).then(probe => {
			const integration = !(probe.body as {errors?: unknown[]}).errors;
			graphql<{jcr: {nodeByPath: {property: {value: string} | null; prefill: {value: string} | null; then: {value: string} | null}}}>(
				`query Mapping($path: String!) { jcr(workspace: EDIT) { nodeByPath(path: $path) {
					property: property(name: "jExperienceProfileProperty") { value }
					prefill: property(name: "jExperiencePrefill") { value } then: property(name: "jExperiencePrefillThen") { value } } } }`,
				{path: `${IMPORTED_FORMS_PATH}/contact-us/fields/your-first-name`}
			).then(data => {
				if (integration) {
					expect(data.jcr.nodeByPath.property?.value).to.equal('firstName');
					expect(data.jcr.nodeByPath.prefill?.value).to.equal('true');
					expect(data.jcr.nodeByPath.then?.value).to.equal('editable');
				} else {
					expect(data.jcr.nodeByPath.property, 'no mapping without the integration').to.be.null;
				}
			});
		});
	});

	it('shows the labels of the form while it is unpublished, and still once it is published', () => {
		// the page resolves the labels through the live reference of the form, else through the edit workspace
		const contactIcon = '[data-sel-role="form-results-entry"][data-sel-name="contact-us"] [data-sel-role="form-unpublished"]';
		openFirstSubmission('contact-us');
		cy.get(contactIcon).should('exist');
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'Your First name');

		publishAndWaitJobEnding(`${IMPORTED_FORMS_PATH}/contact-us`, ['en', 'fr']);
		openFirstSubmission('contact-us');
		cy.get(contactIcon).should('not.exist');
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'Your First name');
	});

	it('exports the results of the published form with the labels of its fields as headers', () => {
		// the export reads the fields of the form through the same query as the page: a variable left unset would lose the labels
		openResultsPage();
		selectEntry('contact-us');
		cy.get('[data-sel-role="export-results"]').click();
		cy.get('[data-sel-role="export-all"]').click();
		cy.get('[data-sel-role="export-confirm"]').should('be.enabled').click();
		cy.readFile('cypress/downloads/contact-us-all.csv', {timeout: 30000}).then((content: string) => {
			const [header] = content.split('\n');
			expect(header).to.contain('Your First name (your-first-name)');
			expect(header).to.contain('Your Enquiry (your-enquiry)');
			expect(content.split('\n').filter(line => line.trim() !== '')).to.have.length(SAMPLE.submissions['contact-us'] + 1);
		});
		cy.get('body').then($body => {
			if ($body.find('[data-sel-role="export-cancel"]').length > 0) {
				cy.get('[data-sel-role="export-cancel"]').click();
			}
		});
	});

	it('on a second run, finds the form and names the values after the fields it holds now', () => {
		// the contributor reviewed the form: a field renamed, one deleted, the title changed
		graphql(`mutation Rename($path: String!) { jcr { mutateNode(pathOrId: $path) { rename(name: "firstname") } } }`,
			{path: `${IMPORTED_FORMS_PATH}/contact-us/fields/your-first-name`});
		deleteNode(`${IMPORTED_FORMS_PATH}/contact-us/fields/your-enquiry`);
		graphql(`mutation Retitle($path: String!) { jcr { mutateNode(pathOrId: $path) {
			mutateProperty(name: "jcr:title") { setValue(language: "en", value: "Contact Us (reviewed)") } } } }`,
			{path: `${IMPORTED_FORMS_PATH}/contact-us`});
		// the submissions of one day are gone: the export brings them back
		deleteNode(`${RESULTS_ROOT_PATH}/contact-us/submissions/${SAMPLE.firstSubmission.day}`, 'LIVE');
		countSubmissions(`${RESULTS_ROOT_PATH}/contact-us`).should('be.lessThan', SAMPLE.submissions['contact-us']);

		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		reportForm('contact-us')
			.should('have.attr', 'data-sel-outcome', 'found')
			.and('contain', `${IMPORTED_FORMS_PATH}/contact-us`)
			.and('contain', '4 field(s)');
		cy.get('[data-sel-role="import-confirm"]').click();
		dialogState('done');
		closeDialog();

		countSubmissions(`${RESULTS_ROOT_PATH}/contact-us`).should('eq', SAMPLE.submissions['contact-us']);
		importedSubmission(SAMPLE.firstSubmission.id).then(submission => {
			expect(valueNames(submission)).to.have.members(['firstname', 'your-last-name', 'your-email-address', 'your-telephone-number', 'text-area_0_4']);
		});
		graphql<{jcr: {nodeByPath: {title: {value: string}}}}>(
			`query Title($path: String!) { jcr(workspace: EDIT) { nodeByPath(path: $path) { title: property(name: "jcr:title", language: "en") { value } } } }`,
			{path: `${IMPORTED_FORMS_PATH}/contact-us`}
		).then(data => expect(data.jcr.nodeByPath.title.value).to.equal('Contact Us (reviewed)'));
	});

	it('attaches the results of a form to a form built by hand, the values under the names of its fields', () => {
		// the created form and its entry are gone: the export offers the choice for that form again
		deleteNode(`${IMPORTED_FORMS_PATH}/${ATTACH.source}`);
		deleteNode(`${RESULTS_ROOT_PATH}/${ATTACH.source}`, 'LIVE');
		const fields: JahiaNode[] = [
			...ATTACH.fields.map(field => ({
				name: field.name,
				primaryNodeType: field.type,
				properties: [{name: 'jcr:title', value: field.title, language: 'en'}]
			})),
			getSelectNode(ATTACH.choice)
		];
		let formId = '';
		createFormNode(ATTACH.form, ATTACH.title, fields).then((response: {data?: {jcr?: {addNode?: {uuid?: string}}}}) => {
			formId = response?.data?.jcr?.addNode?.uuid ?? '';
			expect(formId, 'the uuid of the form').to.not.be.empty;
		});

		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		reportForm('contact-us').should('have.attr', 'data-sel-outcome', 'found');
		reportForm(ATTACH.source).should('have.attr', 'data-sel-outcome', 'resultsOnly');
		choose(ATTACH.source, 'attach');
		reportForm(ATTACH.source).should('have.attr', 'data-sel-outcome', 'attached').and('contain', 'Pick the form');
		// nothing starts until the form is picked
		cy.get('[data-sel-role="import-confirm"]').should('be.disabled');
		pickInDropdown(() => reportForm(ATTACH.source).find('[data-sel-role="import-attach-form"]'), ATTACH.title);
		reportForm(ATTACH.source).should('contain', `${CONTENTS_PATH}/${ATTACH.form}`);

		// the same node name is matched, the same label proposed, the third field keeps its name until picked
		mappingRow('firstname').should('have.attr', 'data-sel-target', 'firstname').and('have.attr', 'data-sel-rule', 'sameName');
		mappingRow('lastname').should('have.attr', 'data-sel-target', 'surname').and('have.attr', 'data-sel-rule', 'sameLabel');
		mappingRow('enter-your-email-here').should('have.attr', 'data-sel-target', '').and('have.attr', 'data-sel-rule', '');
		cy.get('[data-sel-role="import-confirm"]').should('be.enabled');
		// a choice field lists the imported values none of its options carries
		pickInDropdown(() => mappingRow('lastname').find('[data-sel-role="import-mapping-target"]'), 'Topic');
		mappingRow('lastname').find('[data-sel-role="import-mapping-unmatched"]').should('contain', 'match no option');
		pickInDropdown(() => mappingRow('lastname').find('[data-sel-role="import-mapping-target"]'), 'Lastname');
		mappingRow('lastname').should('have.attr', 'data-sel-target', 'surname').and('have.attr', 'data-sel-rule', 'sameLabel');
		mappingRow('lastname').find('[data-sel-role="import-mapping-unmatched"]').should('not.exist');
		pickInDropdown(() => mappingRow('enter-your-email-here').find('[data-sel-role="import-mapping-target"]'), 'Email');
		mappingRow('enter-your-email-here').should('have.attr', 'data-sel-target', 'email');
		// a field of the form picked in one row leaves the menus of the others: no two fields can land together
		mappingRow('lastname').find('.moonstone-dropdown_container').click();
		cy.get(MENU).contains('.moonstone-menuItem', 'Lastname').should('exist');
		cy.get(MENU).contains('.moonstone-menuItem', 'Email').should('not.exist');
		cy.get(MENU_OVERLAY).first().click('topLeft', {force: true});
		cy.get(MENU).should('not.exist');
		cy.get('[data-sel-role="import-mapping-conflict"]').should('not.exist');

		cy.get('[data-sel-role="import-confirm"]').click();
		dialogState('done');
		cy.get('[data-sel-role="import-next-step"]').should('contain', 'attached to forms').and('contain', ATTACH.form);
		reportForm(ATTACH.source).should('have.attr', 'data-sel-outcome', 'attached').and('contain', `${CONTENTS_PATH}/${ATTACH.form}`);
		closeDialog();

		// the entry of the form holds the submissions, their values under the names of its fields
		countSubmissions(`${RESULTS_ROOT_PATH}/${ATTACH.form}`).should('eq', SAMPLE.submissions[ATTACH.source]);
		submissionsUnder(`${RESULTS_ROOT_PATH}/${ATTACH.form}`).then(submissions => {
			submissions.forEach(submission => expect(valueNames(submission)).to.have.members(['firstname', 'surname', 'email']));
		});
		// the form is unpublished: its reference does not resolve in live, the stored identifier says which form
		graphql<{jcr: {nodeByPath: {parentForm: {value: string}; mappings: {values: string[]}}}}>(
			`query Entry($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) {
				parentForm: property(name: "parentForm") { value } mappings: property(name: "mappings") { values } } } }`,
			{path: `${RESULTS_ROOT_PATH}/${ATTACH.form}`}
		).then(data => {
			expect(data.jcr.nodeByPath.parentForm.value).to.equal(formId);
			expect(data.jcr.nodeByPath.mappings.values).to.have.length(1);
			expect(JSON.parse(data.jcr.nodeByPath.mappings.values[0]).mapping).to.deep.equal({lastname: 'surname', 'enter-your-email-here': 'email'});
		});
		// the Results page shows them under the form, with its labels, unpublished as the form is
		openFirstSubmission(ATTACH.form);
		cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${ATTACH.form}"] [data-sel-role="form-unpublished"]`).should('exist');
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'First name').and('contain', 'Lastname').and('contain', 'Email');
	});

	it('imports nothing on a third run, and refuses a file that is no zip', () => {
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		cy.get('[data-sel-role="import-nothing"]').should('be.visible');
		cy.get('[data-sel-role="import-confirm"]').should('not.exist');
		closeDialog();

		openImportDialog();
		dropFile(NOT_A_ZIP);
		cy.get('[data-sel-role="import-error"]').should('contain', 'zip');
		cy.get('[data-sel-role="import-cancel"]').click();
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');
	});

	it('refuses an export taken without the live content, and Try again starts over', () => {
		openResultsPage();
		openImportDialog();
		dropFile(NO_IDS_EXPORT);
		dialogState('failed');
		cy.get('[data-sel-role="import-failed"]').should('contain', 'no identifier').and('contain', 'Export Zip with live content');
		cy.get('[data-sel-role="import-try-again"]').click();
		dialogState('waiting');
		// the failed job went with Try again: nothing reopens
		importApi('GET', '/settings').its('body.job').should('be.null');
		cy.get('[data-sel-role="import-cancel"]').click();
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');
	});

	it('attaches an entry without a form to a form built by hand through the action on the entry', () => {
		// everything imported goes, and the sample comes back as the results alone: three entries without a form
		deleteNode(IMPORTED_FORMS_PATH);
		deleteNode(IMPORTED_FORMS_PATH, 'LIVE');
		deleteNode(`${CONTENTS_PATH}/${ATTACH.form}`);
		[...SAMPLE.forms.filter(form => form !== ATTACH.source), ATTACH.form].forEach(entry => deleteNode(`${RESULTS_ROOT_PATH}/${entry}`, 'LIVE'));
		openResultsPage();
		importSampleThroughApi();
		// the form the results go to, with a reader of its results granted before the attachment
		let formId = '';
		createFormNode(CONTACT.form, CONTACT.title, formNodes(CONTACT.fields)).then((response: {data?: {jcr?: {addNode?: {uuid?: string}}}}) => {
			formId = response?.data?.jcr?.addNode?.uuid ?? '';
			expect(formId, 'the uuid of the form').to.not.be.empty;
		});
		createUser(READER.name, READER.password);
		grantRoles(`/sites/${SITE}`, ['editor'], READER.name, 'USER');
		grantRoles(`${CONTENTS_PATH}/${CONTACT.form}`, ['fmdb-results-reader'], READER.name, 'USER');

		openAttachDialog('contact-us');
		cy.get('[data-sel-role="attach-source"]').should('not.exist');
		cy.get('[data-sel-role="attach-confirm"]').should('be.disabled');
		pickInDropdown(() => cy.get('[data-sel-role="import-attach-form"]'), CONTACT.title);
		// no imported form stands for this entry: names alone are matched, the rest is picked by hand
		mappingRow('your-first-name').should('have.attr', 'data-sel-target', 'your-first-name').and('have.attr', 'data-sel-rule', 'sameName');
		mappingRow('your-email-address').should('have.attr', 'data-sel-target', '').and('have.attr', 'data-sel-rule', '');
		pickInDropdown(() => mappingRow('your-email-address').find('[data-sel-role="import-mapping-target"]'), 'Email');
		pickInDropdown(() => mappingRow('your-enquiry').find('[data-sel-role="import-mapping-target"]'), 'Message');
		cy.get('[data-sel-role="attach-confirm"]').should('be.enabled').click();
		attachDialogState('done');
		// the form had no entry and this one held a single source: it became the entry of the form
		cy.get('[data-sel-role="attach-report"]').should('contain', 'now the entry of the form').and('contain', `${CONTENTS_PATH}/${CONTACT.form}`);
		closeAttachDialog();

		cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${CONTACT.form}"] [data-sel-role="form-unpublished"]`, {timeout: 30000}).should('exist');
		cy.get('[data-sel-role="form-results-entry"][data-sel-name="contact-us"]').should('not.exist');
		countSubmissions(`${RESULTS_ROOT_PATH}/${CONTACT.form}`).should('eq', SAMPLE.submissions['contact-us']);
		submissionsUnder(`${RESULTS_ROOT_PATH}/${CONTACT.form}`).then(submissions => {
			submissions.forEach(submission => expect(valueNames(submission)).to.have.members(['your-first-name', 'your-last-name', 'email', 'your-telephone-number', 'message']));
		});
		entryOf(`${RESULTS_ROOT_PATH}/${CONTACT.form}`).then(entry => {
			expect(entry?.parentForm?.value).to.equal(formId);
			expect(entry?.sourceFormIds?.values).to.have.length(1);
			expect(entry?.mappings?.values.map(value => JSON.parse(value).mapping)).to.deep.equal([{'your-email-address': 'email', 'your-enquiry': 'message'}]);
		});
		// the reader of the form reads the entry; the page shows the labels of the form
		cy.logout();
		cy.login(READER.name, READER.password);
		readAsCurrentUser(`${RESULTS_ROOT_PATH}/${CONTACT.form}`).should('not.be.null');
		cy.logout();
		cy.login();
		openFirstSubmission(CONTACT.form);
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'First name').and('contain', 'Email').and('contain', 'Message');
	});

	it('merges a second source into the entry of the form, touching no value already there', () => {
		// a native submission of the form, under a name the second mapping renames
		graphql(`mutation Native($parent: String!) { jcr(workspace: LIVE) { addNode(parentPathOrId: $parent, name: "native-submission",
			primaryNodeType: "fmdb:formSubmission", properties: [{name: "origin", value: "formidable"}],
			children: [{name: "data", primaryNodeType: "fmdb:submissionData", properties: [{name: "firstname", value: "native"}]}]) { uuid } } }`,
			{parent: `${RESULTS_ROOT_PATH}/${CONTACT.form}/submissions`});

		openAttachDialog(ATTACH.source);
		pickInDropdown(() => cy.get('[data-sel-role="import-attach-form"]'), CONTACT.title);
		pickInDropdown(() => mappingRow('firstname').find('[data-sel-role="import-mapping-target"]'), 'First name');
		pickInDropdown(() => mappingRow('enter-your-email-here').find('[data-sel-role="import-mapping-target"]'), 'Email');
		cy.get('[data-sel-role="attach-confirm"]').should('be.enabled').click();
		attachDialogState('done');
		cy.get('[data-sel-role="attach-report"]').should('contain', `${SAMPLE.submissions[ATTACH.source]} submission(s) moved`);
		closeAttachDialog();

		cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${ATTACH.source}"]`).should('not.exist');
		countSubmissions(`${RESULTS_ROOT_PATH}/${CONTACT.form}`).should('eq', SAMPLE.submissions['contact-us'] + SAMPLE.submissions[ATTACH.source] + 1);
		submissionsUnder(`${RESULTS_ROOT_PATH}/${CONTACT.form}`, 200).then(submissions => {
			const native = submissions.filter(submission => submission.origin.value === 'formidable');
			const first = submissions.filter(submission => valueNames(submission).includes('your-last-name'));
			const second = submissions.filter(submission => valueNames(submission).includes('lastname'));
			expect(native, 'the native submission').to.have.length(1);
			expect(valueNames(native[0])).to.deep.equal(['firstname']);
			expect(first, 'the submissions of the first source').to.have.length(SAMPLE.submissions['contact-us']);
			first.forEach(submission => expect(valueNames(submission)).to.have.members(['your-first-name', 'your-last-name', 'email', 'your-telephone-number', 'message']));
			expect(second, 'the submissions of the second source').to.have.length(SAMPLE.submissions[ATTACH.source]);
			second.forEach(submission => expect(valueNames(submission)).to.have.members(['your-first-name', 'lastname', 'email']));
		});
		entryOf(`${RESULTS_ROOT_PATH}/${CONTACT.form}`).then(entry => {
			expect(entry?.sourceFormIds?.values).to.have.length(2);
			expect(entry?.mappings?.values).to.have.length(2);
		});
	});

	it('leaves the native submissions of an entry where they are: an entry holding more than the source merges into a new entry', () => {
		// the entry of newsletter-registration, written alone, takes a native submission before its source goes to a form without an entry
		graphql(`mutation Native($parent: String!) { jcr(workspace: LIVE) { addNode(parentPathOrId: $parent, name: "native-submission",
			primaryNodeType: "fmdb:formSubmission", properties: [{name: "origin", value: "formidable"}],
			children: [{name: "data", primaryNodeType: "fmdb:submissionData", properties: [{name: "email", value: "native@example.com"}]}]) { uuid } } }`,
			{parent: `${RESULTS_ROOT_PATH}/newsletter-registration/submissions`});
		createFormNode('bulletin', 'Bulletin', formNodes([{name: 'bulletin-email', title: 'Email', type: 'fmdb:inputEmail'}]));

		openAttachDialog('newsletter-registration');
		pickInDropdown(() => cy.get('[data-sel-role="import-attach-form"]'), 'Bulletin');
		cy.get('[data-sel-role="attach-confirm"]').should('be.enabled').click();
		attachDialogState('done');
		// not relinked: the form takes an entry of its own with the imported submissions, the native one stays behind
		cy.get('[data-sel-role="attach-report"]').should('contain', `${SAMPLE.submissions['newsletter-registration']} submission(s) moved`);
		closeAttachDialog();
		countSubmissions(`${RESULTS_ROOT_PATH}/bulletin`).should('eq', SAMPLE.submissions['newsletter-registration']);
		countSubmissions(`${RESULTS_ROOT_PATH}/newsletter-registration`).should('eq', 1);
		// the entry left holds native submissions alone: it is no longer marked as imported results
		entryOf(`${RESULTS_ROOT_PATH}/newsletter-registration`).then(entry => {
			expect(entry?.parentForm).to.be.null;
			expect(entry?.imported).to.equal(false);
			expect(entry?.sourceFormIds?.values ?? []).to.have.length(0);
			expect(entry?.mappings?.values ?? []).to.have.length(0);
		});
		entryOf(`${RESULTS_ROOT_PATH}/bulletin`).then(entry => {
			expect(entry?.imported).to.equal(true);
			expect(entry?.sourceFormIds?.values).to.have.length(1);
		});
	});

	it('asks which source when the entry holds several, and shows Form deleted once the form is gone', () => {
		openAttachDialog(CONTACT.form);
		cy.get('[data-sel-role="attach-source"]').should('be.visible');
		cy.get('[data-sel-role="import-attach-form"]').should('not.exist');
		cy.get('[data-sel-role="attach-confirm"]').should('be.disabled');
		cy.get('[data-sel-role="attach-cancel"]').click();
		cy.get('[data-sel-role="attach-results-dialog"]').should('not.exist');

		deleteNode(`${CONTENTS_PATH}/${CONTACT.form}`);
		openResultsPage();
		cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${CONTACT.form}"] [data-sel-role="form-deleted"]`, {timeout: 30000}).should('exist');
	});
});

import {deleteNode, enableModule, publishAndWaitJobEnding} from '@jahia/cypress';
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
/** The same export taken again after a visitor sent a PDF through the file field "Your Id" of contact-us. */
const WITH_FILE_FIXTURE = 'imports/formFactory-with-file.zip';
const WITH_FILE = {submission: '2dd1fe78-e095-4f47-ab08-b5c30df47a4b', fileName: 'document.pdf', mimeType: 'application/pdf', bytes: 578};
/** A hand-written export: one form with a radios matrix (2 rows × 3 columns) and a checkboxes matrix (1 × 2), 2 submissions. */
const MATRIX_FIXTURE = 'imports/formFactory-matrix.zip';
const MATRIX = {
	form: 'satisfaction-survey',
	radios: {name: 'satisfaction', rows: ['the-price', 'the-service'], columns: ['poor', 'fair', 'good']},
	checkboxes: {name: 'extras', rows: ['delivery'], columns: ['fast', 'cheap']},
	firstSubmission: '71d2f6a0-aaaa-4bbb-8ccc-000000000011'
};
const NO_IDS_EXPORT = 'cypress/fixtures/imports/formFactory-no-ids.zip';
const NOT_A_ZIP = 'cypress/fixtures/imports/conditional-logic-form.xml';
/** The add-on: the test site is created without it, and the spec enables it there as an administrator would. */
const IMPORT_MODULE = 'formidable-forms-import';
const IMPORT_ENDPOINT = `/modules/${IMPORT_MODULE}/import`;

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

type Workspace = 'EDIT' | 'LIVE';
type NamedNode = {name: string; primaryNodeType: {name: string}};
type SubmissionFile = {path: string; content: {mimeType: {value: string}; data: {size: number}}};
type SubmissionData = {path: string; origin: {value: string}; data: {properties: Array<{name: string; value: string | null; values: string[] | null}>}};

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
			path origin: property(name: "origin") { value } data: descendant(relPath: "data") { properties { name value values } } } } } }`,
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
			path origin: property(name: "origin") { value } data: descendant(relPath: "data") { properties { name value values } } } } } }`,
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
const importApi = (method: 'GET' | 'POST' | 'DELETE', path: string, body?: Record<string, unknown>) =>
	cy.request({method, url: `${IMPORT_ENDPOINT}${path}?site=${SITE}`, headers: withSameOriginHeaders(), body, failOnStatusCode: false});

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

const CHOICE_LABELS = {resultsOnly: 'Results only', create: 'Create the form', attach: 'Attach to an existing form', skip: 'Don\'t import', add: 'Add the new submissions'};
/** The value a row shows for each choice: a found form that adds its new submissions holds the results-only value. */
const CHOICE_VALUES = {...Object.fromEntries(Object.keys(CHOICE_LABELS).map(choice => [choice, choice])), add: 'resultsOnly'};
const FOR_ALL_LABELS = {import: 'Import the results', create: 'Create the forms', skip: 'Don\'t import'};

/** Opens a Moonstone dropdown of the dialog, once it has loaded its data, and picks the item of that label. */
const pickInDropdown = (dropdown: () => Cypress.Chainable<JQuery>, label: string) => {
	dropdown().find('.moonstone-dropdown_loading', {timeout: 15000}).should('not.exist');
	dropdown().find('.moonstone-dropdown_container').click();
	cy.get(MENU).contains('.moonstone-menuItem', label).click();
	cy.get(MENU).should('not.exist');
};

/** Picks, for a form of the dry run, what receives its results, in the menu of its row. */
const choose = (name: string, choice: keyof typeof CHOICE_LABELS) => {
	pickInDropdown(() => reportForm(name).find('[data-sel-role="import-choice"]'), CHOICE_LABELS[choice]);
	reportForm(name).find('[data-sel-role="import-choice"]').should('have.attr', 'data-sel-value', CHOICE_VALUES[choice]);
};

/** Picks, for every form of the dry run at once, what receives its results, in the menu above the rows. */
const chooseForAll = (choice: keyof typeof FOR_ALL_LABELS) => {
	pickInDropdown(() => cy.get('[data-sel-role="import-choice-all"]'), FOR_ALL_LABELS[choice]);
	cy.get('[data-sel-role="import-choice-all"]').should('have.attr', 'data-sel-value', choice);
};

const mappingRow = (source: string) => cy.get(`[data-sel-role="import-mapping-row"][data-sel-source="${source}"]`);

/** Runs the import of an export through the endpoint, as the dialog does, the results alone for every form named. */
const importSampleThroughApi = (fixture = SAMPLE_FIXTURE, forms = SAMPLE.forms) =>
	uploadThroughApi(fixture).then(jobId => {
		waitForJobState(jobId, 'review');
		importApi('POST', `/jobs/${jobId}/import`, {choices: Object.fromEntries(forms.map(form => [form, 'resultsOnly']))})
			.its('status').should('eq', 200);
		waitForJobState(jobId, 'done');
		importApi('DELETE', `/jobs/${jobId}`).its('status').should('eq', 200);
	});

// The caption inside the entry sits at its centre, which Cypress takes for a cover: the click is forced.
const selectEntry = (name: string) =>
	cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${name}"]`).should('be.visible').click({force: true});

const openFirstSubmission = (name: string) => {
	openResultsPage();
	selectEntry(name);
	cy.get('[data-sel-role="submissions-table"] tbody tr', {timeout: 30000}).first().click();
};

/**
 * The Import button of the Results page, shown once the module is enabled on the site, takes the zip export of a
 * Jahia Forms formFactory node: a dry run shows what the import will do, and the administrator chooses, per
 * form or for all of them at once, what receives its results — the results alone, as an entry without a form,
 * or a form created in the imported-forms folder; nothing is imported until chosen. One import runs at a time
 * per site, and a later run adds only what is missing, under the names the fields have by then.
 */
describe('Actions - 76 Importing the forms and results of Jahia Forms', () => {
	useFormidableSite();

	it('hides the Import button, and refuses the upload, while the module is not enabled on the site', () => {
		cy.visit(RESULTS_PAGE);
		cy.get('[data-sel-role="form-results-empty"], [data-sel-role="form-results-list"]', {timeout: 60000}).should('be.visible');
		cy.get('[data-sel-role="import-results"]').should('not.exist');
		importApi('GET', '/settings').its('body').should('include', {enabled: false, allowed: false});
		importApi('POST', '/jobs').then(refused => {
			expect(refused.status).to.equal(403);
			expect(refused.body.error).to.contain('not enabled on the site');
		});
		// the one switch of the import: the module among the site's modules, as an administrator enables it
		enableModule(IMPORT_MODULE, SITE);
		importApi('GET', '/settings').its('body').should('include', {enabled: true, allowed: true});
	});

	it('imports nothing until told to, the results alone for every form at once, refusing a second import meanwhile', () => {
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);

		dialogState('review');
		cy.get('[data-sel-role="import-report-form"]').should('have.length', SAMPLE.forms.length);
		// nothing is chosen yet: no form is imported, and the import waits
		cy.get('[data-sel-role="import-report-form"][data-sel-outcome="skipped"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-choice"][data-sel-value="skip"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-choice-all"]').should('have.attr', 'data-sel-value', 'skip');
		cy.get('[data-sel-role="import-totals"]').should('contain', '0 submission(s) to import in 0 form(s)');
		cy.get('[data-sel-role="import-confirm"]').should('be.disabled');
		// the menu for all forms chooses for every row; a row changed apart leaves it without a value
		chooseForAll('import');
		cy.get('[data-sel-role="import-report-form"][data-sel-outcome="resultsOnly"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-choice"][data-sel-value="resultsOnly"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		choose('contact-us', 'create');
		cy.get('[data-sel-role="import-choice-all"]').should('have.attr', 'data-sel-value', '').and('contain', 'Per form');
		chooseForAll('import');
		cy.get('[data-sel-role="import-choice"][data-sel-value="resultsOnly"]').should('have.length', SAMPLE.forms.length);
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
		// nothing to do by hand without a created form: no next step, the rows say where the results went
		cy.get('[data-sel-role="import-next-step"]').should('not.exist');
		reportForm('contact-us').find('[data-sel-role="import-report-outcome"]').should('contain', 'without a form');
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');

		// the report survives a reload until it is closed
		cy.reload();
		dialogState('done', 60000);
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		closeDialog();
		cy.get('[data-sel-role="form-results-entry"]', {timeout: 60000}).should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="form-results-entry"] [data-sel-role="form-no-form"]').should('have.length', SAMPLE.forms.length);
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

	it('shows an entry without a form as No form, with the system names as columns', () => {
		openFirstSubmission('contact-us');
		cy.get('[data-sel-role="form-no-form"]').should('exist');
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
		chooseForAll('create');
		cy.get('[data-sel-role="import-report-form"][data-sel-outcome="created"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-choice"][data-sel-value="create"]').should('have.length', SAMPLE.forms.length);
		reportForm('contact-us').find('[data-sel-role="import-report-outcome"]').should('contain', 'Imported from Jahia Forms');
		// what a created form cannot carry over is folded under its count, what to do apart from what was carried over
		reportForm('contact-us').find('[data-sel-role="import-report-notes"]').should('not.exist');
		reportForm('contact-us').find('[data-sel-role="import-report-toggle"]').should('contain', 'to review').click();
		reportForm('contact-us').find('[data-sel-role="import-report-notes-action"] [data-sel-code="form.redirect"]')
			.should('contain', 'page redirect').and('not.contain', 'fcnt:');
		// the review writes nothing yet: there is no report to download before the import
		cy.get('[data-sel-role="import-download-report"]').should('not.exist');
		cy.get('[data-sel-role="import-confirm"]').click();
		dialogState('done');
		cy.get('[data-sel-role="import-next-step"]').should('contain', 'unpublished');
		// the report as a Markdown file: how the import ended, one section per form, what to redo by hand in words
		cy.exec('rm -f cypress/downloads/forms-import-*.md');
		cy.get('[data-sel-role="import-download-report"]').click();
		// cy.exec does not retry: the command waits itself until the browser has renamed its partial download
		cy.exec('for i in $(seq 1 40); do f=$(ls cypress/downloads | grep "^forms-import-report-.*\\.md$") && echo "$f" && exit 0; sleep 0.5; done; exit 1', {timeout: 30000}).its('stdout').then(name => {
			cy.readFile(`cypress/downloads/${name.trim()}`).then((content: string) => {
				expect(content).to.contain('# Jahia Forms import report');
				expect(content).to.contain('- Import done');
				expect(content).to.contain('106 submission(s) imported, 0 not written');
				expect(content).to.contain('## Contact Us');
				expect(content).to.contain('page redirect').and.not.contain('fcnt:');
			});
		});
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
			.should('have.attr', 'data-sel-outcome', 'skipped')
			.and('contain', '4 field(s)');
		// the form found takes its new submissions once told to; the two with nothing new leave nothing to choose
		choose('contact-us', 'add');
		reportForm('contact-us').should('have.attr', 'data-sel-outcome', 'found').and('contain', '"contact-us"');
		cy.get('[data-sel-role="import-choice"]').should('have.length', 1);
		cy.get('[data-sel-role="import-choice-all"]').should('not.exist');
		// a form found with nothing new stays found, in the review and once imported: it is not left out
		reportForm('newsletterregistration').should('have.attr', 'data-sel-outcome', 'found').and('contain', 'nothing new');
		cy.get('[data-sel-role="import-confirm"]').click();
		dialogState('done');
		reportForm('newsletterregistration').should('have.attr', 'data-sel-outcome', 'found').and('contain', 'Joined');
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
		reportForm('contact-us').find('[data-sel-role="import-choice"]').should('not.exist');
		reportForm(ATTACH.source).should('have.attr', 'data-sel-outcome', 'skipped');
		choose(ATTACH.source, 'attach');
		reportForm(ATTACH.source).should('have.attr', 'data-sel-outcome', 'attached').and('contain', 'Pick the form');
		// nothing starts until the form is picked
		cy.get('[data-sel-role="import-confirm"]').should('be.disabled');
		pickInDropdown(() => reportForm(ATTACH.source).find('[data-sel-role="import-attach-form"]'), ATTACH.title);
		reportForm(ATTACH.source).find('[data-sel-role="import-report-outcome"]').should('contain', ATTACH.title);

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
		reportForm(ATTACH.source).should('have.attr', 'data-sel-outcome', 'attached')
			.find('[data-sel-role="import-report-outcome"]').should('contain', ATTACH.form);
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

	it('imports a file a visitor uploaded under its submission, readable by the readers of the results only', () => {
		// a clean slate: the forms and every entry the earlier cases left go, the import jobs stay
		deleteNode(IMPORTED_FORMS_PATH);
		deleteNode(IMPORTED_FORMS_PATH, 'LIVE');
		deleteNode(`${CONTENTS_PATH}/${ATTACH.form}`);
		children(RESULTS_ROOT_PATH, 'LIVE').then(nodes => nodes
			.filter(node => node.primaryNodeType.name !== 'fmdb:importJobs')
			.forEach(node => deleteNode(`${RESULTS_ROOT_PATH}/${node.name}`, 'LIVE')));
		openResultsPage();
		importSampleThroughApi(WITH_FILE_FIXTURE);

		importedSubmission(WITH_FILE.submission).then(submission =>
			graphql<{jcr: {nodesByQuery: {nodes: SubmissionFile[]}}}>(
				`query Files($query: String!) { jcr(workspace: LIVE) { nodesByQuery(query: $query, queryLanguage: SQL2) { nodes {
					path content: descendant(relPath: "jcr:content") { mimeType: property(name: "jcr:mimeType") { value } data: property(name: "jcr:data") { size } } } } } }`,
				{query: `SELECT * FROM [jnt:file] AS f WHERE ISDESCENDANTNODE(f, '${submission.path}')`}
			).then(data => {
				const files = data.jcr.nodesByQuery.nodes;
				expect(files, 'the files of the submission').to.have.length(1);
				const [file] = files;
				expect(file.path.startsWith(`${submission.path}/files/`), file.path).to.equal(true);
				expect(file.path.endsWith(`/${WITH_FILE.fileName}`), file.path).to.equal(true);
				expect(file.content.mimeType.value).to.equal(WITH_FILE.mimeType);
				expect(file.content.data.size).to.equal(WITH_FILE.bytes);

				cy.request({url: `/files/live${file.path}`, encoding: 'binary'}).then(response => {
					expect(response.status).to.equal(200);
					expect(response.body.length).to.equal(WITH_FILE.bytes);
				});
				cy.logout();
				cy.request({url: `/files/live${file.path}`, failOnStatusCode: false}).its('status').should('eq', 404);
				cy.login();
			})
		);
	});

	it('recreates a Forms matrix as a fieldset laid out as a matrix, one field per row, the answers under each row', () => {
		// the endpoint is called with the page's fetch: a page of the site must be open, not the blank one
		openResultsPage();
		uploadThroughApi(MATRIX_FIXTURE).then(jobId => {
			waitForJobState(jobId, 'review');
			importApi('POST', `/jobs/${jobId}/import`, {choices: {[MATRIX.form]: 'create'}}).its('status').should('eq', 200);
			waitForJobState(jobId, 'done');
			importApi('DELETE', `/jobs/${jobId}`).its('status').should('eq', 200);
		});

		// the form: two fieldsets rendered with the Matrix view, one radio or checkbox field per row, the columns as options
		type Element = {name: string; type: {name: string}; view: {value: string} | null; mixins: Array<{name: string}>; options: {values: string[]} | null};
		graphql<{jcr: {nodeByPath: {children: {nodes: Array<Element & {children: {nodes: Element[]}}>}}}}>(
			`fragment Element on JCRNode { name type: primaryNodeType { name } view: property(name: "j:view") { value }
				mixins: mixinTypes { name } options: property(name: "options", language: "en") { values } }
			query Fields($path: String!) { jcr(workspace: EDIT) { nodeByPath(path: $path) { children { nodes {
				...Element children { nodes { ...Element } } } } } } }`,
			{path: `${IMPORTED_FORMS_PATH}/${MATRIX.form}/fields`}
		).then(data => {
			const elements = data.jcr.nodeByPath.children.nodes;
			const fieldset = (name: string) => elements.find(element => element.name === name);
			const radios = fieldset(MATRIX.radios.name);
			expect(radios?.type.name, 'the radios matrix').to.equal('fmdb:fieldset');
			expect(radios?.mixins.map(mixin => mixin.name), 'jmix:renderable on the fieldset').to.include('jmix:renderable');
			expect(radios?.view?.value).to.equal('matrix');
			expect(radios?.children.nodes.map(row => row.name)).to.deep.equal(MATRIX.radios.rows);
			radios?.children.nodes.forEach(row => {
				expect(row.type.name, row.name).to.equal('fmdb:radio');
				expect(row.options?.values.map(option => (JSON.parse(option) as {value: string}).value), row.name).to.deep.equal(MATRIX.radios.columns);
			});
			const checkboxes = fieldset(MATRIX.checkboxes.name);
			expect(checkboxes?.view?.value).to.equal('matrix');
			expect(checkboxes?.children.nodes.map(row => `${row.type.name}:${row.name}`)).to.deep.equal(['fmdb:checkbox:delivery']);
		});

		// the answers: the column keys of each row, under the field of that row, nothing under the matrix
		importedSubmission(MATRIX.firstSubmission).then(submission => {
			// a radio row stores one value, a checkbox row a multi-valued property: the list, whichever way it is stored
			const value = (name: string) => submission.data.properties.filter(p => p.name === name).flatMap(p => p.values ?? [p.value]);
			expect(value('the-price')).to.deep.equal(['good']);
			expect(value('the-service')).to.deep.equal(['poor']);
			expect(value('delivery')).to.deep.equal(['fast', 'cheap']);
			expect(valueNames(submission)).to.not.include(MATRIX.radios.name);
		});

		// rendered, the fieldset is a matrix: the heading row and one grid row per question (the form is unpublished: preview),
		// and the help text of the Forms matrix is the fieldset's, under its legend
		cy.request(`/cms/render/default/en${IMPORTED_FORMS_PATH}/${MATRIX.form}.html.ajax`).then(response => {
			expect(response.status).to.equal(200);
			expect(response.body).to.contain('fmdb-fieldset fmdb-matrix').and.to.contain('--fmdb-matrix-columns:3').and.to.contain('fmdb-matrix-column">Fair<');
			expect(response.body).to.contain('name="the-price"').and.to.contain('name="delivery"');
			expect(response.body).to.match(/<\/legend><div id="help-[^"]*" class="fmdb-form-help">One answer per line<\/div><div class="fmdb-matrix-header"/);
		});
		deleteNode(`${IMPORTED_FORMS_PATH}/${MATRIX.form}`);
		deleteNode(`${RESULTS_ROOT_PATH}/${MATRIX.form}`, 'LIVE');
	});

	it('leaves out a form told not to import, and proposes it again on the next run', () => {
		children(RESULTS_ROOT_PATH, 'LIVE').then(nodes => nodes
			.filter(node => node.primaryNodeType.name !== 'fmdb:importJobs')
			.forEach(node => deleteNode(`${RESULTS_ROOT_PATH}/${node.name}`, 'LIVE')));
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		// the two forms not chosen for stay out
		choose('contact-us', 'resultsOnly');
		reportForm('newsletter-registration').should('have.attr', 'data-sel-outcome', 'skipped');
		cy.get('[data-sel-role="import-totals"]').should('contain', `${SAMPLE.submissions['contact-us']} submission(s) to import in 1 form(s)`);
		// every form left out leaves nothing to import
		chooseForAll('skip');
		cy.get('[data-sel-role="import-confirm"]').should('be.disabled');
		choose('contact-us', 'resultsOnly');
		cy.get('[data-sel-role="import-confirm"]').should('be.enabled').click();
		dialogState('done');
		reportForm('newsletterregistration').should('have.attr', 'data-sel-outcome', 'skipped');
		cy.get('[data-sel-role="import-totals"]').should('contain', `${SAMPLE.submissions['contact-us']} submission(s) imported in 1 form(s)`);
		closeDialog();
		children(RESULTS_ROOT_PATH, 'LIVE').then(nodes => expect(nodes.map(node => node.name)).to.include('contact-us')
			.and.not.include('newsletterregistration').and.not.include('newsletter-registration'));

		// nothing marks a form left out: the next run proposes it as new, with the choices of a new form
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		reportForm('contact-us').find('[data-sel-role="import-choice"]').should('not.exist');
		reportForm('newsletterregistration').should('have.attr', 'data-sel-outcome', 'skipped');
		choose('newsletterregistration', 'resultsOnly');
		cy.get('[data-sel-role="import-cancel"]').click();
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');
	});
});

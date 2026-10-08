import {publishAndWaitJobEnding} from '@jahia/cypress';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures';
import {withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

const SITE = FORMIDABLE_TEST_SITE.key;
const RESULTS_PAGE = `/jahia/jcontent/${SITE}/en/apps/formidableResults`;
const CONTENTS_PATH = `/sites/${SITE}/contents`;
const IMPORTED_FORMS_PATH = `${CONTENTS_PATH}/imported-forms`;
const RESULTS_ROOT_PATH = `/sites/${SITE}/formidable-results`;
const SAMPLE_EXPORT = 'cypress/fixtures/imports/formFactory-sample.zip';
const NOT_A_ZIP = 'cypress/fixtures/imports/conditional-logic-form.xml';
const FORMS_IMPORT_PID = 'org.jahia.modules.formidable.formsImport';

/** The anonymised sample export: 3 forms, 106 submissions. */
const SAMPLE = {
	forms: ['contact-us', 'newsletterregistration', 'newsletter-registration'],
	submissions: {'contact-us': 44, newsletterregistration: 18, 'newsletter-registration': 44},
	contactFields: ['your-first-name', 'your-last-name', 'your-email-address', 'your-telephone-number', 'your-enquiry'],
	contactFieldTypes: ['fmdb:inputText', 'fmdb:inputText', 'fmdb:inputEmail', 'fmdb:inputText', 'fmdb:textarea'],
	contactFormId: '08246a0f-de43-4dbb-b91e-55cdd366614b',
	firstSubmission: {id: '2c591198-6092-485b-b936-2a8cbf0213c8', day: '2023/06/20'}
};

type Workspace = 'EDIT' | 'LIVE';
type NamedNode = {name: string; primaryNodeType: {name: string}};

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

// The caption inside the entry sits at its centre, which Cypress takes for a cover: the click is forced.
const selectEntry = (name: string) =>
	cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${name}"]`).should('be.visible').click({force: true});

/**
 * The Import button of the Results page, behind the importButtonEnabled setting, takes the zip export of a
 * Jahia Forms formFactory node: a dry run shows what the import will do, the import recreates the forms in
 * the imported-forms folder and writes their submissions as results, and a second run adds nothing.
 */
describe('Actions - 76 Importing the forms and results of Jahia Forms', () => {
	useFormidableSite();

	before(() => {
		cy.login();
		setImportButton(true);
	});

	after(() => {
		cy.login();
		setImportButton(false);
	});

	it('offers the import on an empty Results page, shows the dry run, then imports', () => {
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);

		dialogState('review');
		cy.get('[data-sel-role="import-report-form"]').should('have.length', SAMPLE.forms.length);
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		cy.get('[data-sel-role="import-report-form"][data-sel-name="contact-us"]').should('contain', 'Contact Us');
		// the dry run wrote nothing
		children(CONTENTS_PATH, 'EDIT').then(nodes => expect(nodes.map(n => n.name)).not.to.include('imported-forms'));

		cy.get('[data-sel-role="import-confirm"]').click();
		dialogState('done');
		cy.get('[data-sel-role="import-next-step"]').scrollIntoView();
		cy.get('[data-sel-role="import-next-step"]').should('be.visible');
		cy.get('[data-sel-role="import-totals"]').should('contain', '106');
		cy.get('[data-sel-role="import-close"]').click();
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');
		cy.get('[data-sel-role="form-results-entry"]', {timeout: 60000}).should('have.length', SAMPLE.forms.length);
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
	});

	it('wrote every submission under the system names of its fields, dated as the visitor submitted it', () => {
		Object.entries(SAMPLE.submissions).forEach(([form, count]) => {
			countSubmissions(`${RESULTS_ROOT_PATH}/${form}`).should('eq', count);
		});
		graphql<{jcr: {nodesByQuery: {nodes: Array<{
			path: string; origin: {value: string}; data: {properties: Array<{name: string; value: string}>};
		}>}}}>(
			`query First($query: String!) { jcr(workspace: LIVE) { nodesByQuery(query: $query, queryLanguage: SQL2, limit: 1) { nodes {
				path origin: property(name: "origin") { value } data: descendant(relPath: "data") { properties { name value } } } } } }`,
			{query: `SELECT * FROM [fmdbmix:importedSubmission] AS s WHERE ISDESCENDANTNODE(s, '${RESULTS_ROOT_PATH}') AND s.[sourceId] = '${SAMPLE.firstSubmission.id}'`}
		).then(data => {
			const [submission] = data.jcr.nodesByQuery.nodes;
			expect(submission, 'the first submission of the sample').to.exist;
			expect(submission.path).to.contain(`${RESULTS_ROOT_PATH}/contact-us/submissions/${SAMPLE.firstSubmission.day}/`);
			expect(submission.origin.value).to.equal('jahia-forms');
			const values = submission.data.properties.filter(p => !p.name.includes(':')).map(p => p.name);
			expect(values).to.have.members(SAMPLE.contactFields);
			const email = submission.data.properties.find(p => p.name === 'your-email-address');
			expect(email?.value).to.match(/@example\.com$/);
		});
	});

	it('shows the entry as unpublished with the system names, then the labels once the form is published', () => {
		// the page resolves the labels through the live reference of the form: none while it is unpublished
		openResultsPage();
		selectEntry('contact-us');
		cy.get('[data-sel-role="submissions-table"] tbody tr', {timeout: 30000}).first().click();
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'your-first-name').and('not.contain', 'Your First name');

		publishAndWaitJobEnding(`${IMPORTED_FORMS_PATH}/contact-us`, ['en', 'fr']);
		openResultsPage();
		selectEntry('contact-us');
		cy.get('[data-sel-role="submissions-table"] tbody tr', {timeout: 30000}).first().click();
		cy.get('[data-sel-role="submission-detail"]').should('contain', 'Your First name');
	});

	it('imports nothing on a second run, and refuses a file that is no zip', () => {
		openResultsPage();
		openImportDialog();
		dropFile(SAMPLE_EXPORT);
		dialogState('review');
		cy.get('[data-sel-role="import-nothing"]').should('be.visible');
		cy.get('[data-sel-role="import-confirm"]').should('not.exist');
		cy.get('[data-sel-role="import-close"]').click();
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');

		openImportDialog();
		dropFile(NOT_A_ZIP);
		cy.get('[data-sel-role="import-error"]').should('contain', 'zip');
		cy.get('[data-sel-role="import-cancel"]').click();
		cy.get('[data-sel-role="import-results-dialog"]').should('not.exist');
	});
});

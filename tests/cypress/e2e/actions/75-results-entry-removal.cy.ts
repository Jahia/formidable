import {deleteNode, unpublishNode} from '@jahia/cypress';
import {localDay} from '../../support/constants';
import {
	createPublishedLiveFormPage,
	FORMIDABLE_TEST_SITE,
	getInputTextNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

const RESULTS_ROOT_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results`;
const RESULTS_PAGE = `/jahia/jcontent/${FORMIDABLE_TEST_SITE.key}/en/apps/formidableResults`;

const ENTRY_FORM = {name: 'results-entry-form', title: 'Results entry form'};
const EMPTIED_FORM = {name: 'results-emptied-form', title: 'Results emptied form'};
const ORPHAN_FORM = {name: 'results-orphan-form', title: 'Results orphan form'};
const UNPUBLISHED_FORM = {name: 'results-unpublished-form', title: 'Results unpublished form'};

/** The names of the entries the Results page lists: the fmdb:formResults nodes under the site's results root. */
const listResultsEntries = (): Cypress.Chainable<string[]> =>
	cy.request({
		method: 'POST',
		url: '/modules/graphql',
		headers: withSameOriginHeaders(),
		body: {
			query: `query ResultsEntries($path: String!) {
				jcr(workspace: LIVE) {
					nodeByPath(path: $path) {
						children(typesFilter: {types: ["fmdb:formResults"]}) {nodes {name}}
					}
				}
			}`,
			variables: {path: RESULTS_ROOT_PATH}
		}
	}).then(response => {
		const body = response.body as {errors?: Array<{message?: string}>; data?: {jcr?: {nodeByPath?: {children?: {nodes?: Array<{name: string}>}}}}};
		expect(body.errors, 'GraphQL errors listing the results entries').to.be.undefined;
		return cy.wrap((body.data?.jcr?.nodeByPath?.children?.nodes ?? []).map(node => node.name), {log: false});
	});

/** A visitor's submission (anonymous: a logged-in submitter is refused by the CSRF guard); the runner logs back in after. */
const submitAsVisitor = (formId: string, fullName: string) => {
	cy.logout();
	postDirectMultipartSubmission({formId, fields: {fullName}, headers: withSameOriginHeaders()}).its('status').should('eq', 200);
	cy.login();
};

const createStoringForm = (form: {name: string; title: string}) =>
	createPublishedLiveFormPage(
		form.name,
		form.title,
		[getInputTextNode({name: 'fullName', title: 'Full name'})],
		undefined,
		undefined,
		{actions: [getSaveToJcrActionNode()]}
	);

const openResultsPage = () => {
	cy.visit(RESULTS_PAGE);
	cy.get('[data-sel-role="form-results-entry"]', {timeout: 60000}).should('exist');
};

const entry = (name: string) => cy.get(`[data-sel-role="form-results-entry"][data-sel-name="${name}"]`);

/** Selects the entry unless it already is: a second click would deselect it. */
const selectEntry = (name: string) => {
	entry(name).then($entry => {
		if ($entry.attr('aria-pressed') !== 'true') {
			cy.wrap($entry).click();
		}
	});
};

const openDeleteDialog = (name: string) => {
	selectEntry(name);
	cy.get('[data-sel-role="delete-results"]').click();
	cy.get('[data-sel-role="delete-results-dialog"]').should('have.attr', 'open');
};

/**
 * "Delete all results" on the selected entry: the confirmation is whatever name the dialog asks for
 * (the form's title, or the entry's own name once the form is gone), read from the input's placeholder.
 */
const deleteAllResults = (name: string) => {
	openDeleteDialog(name);
	cy.get('[data-sel-role="delete-all-results"]').check({force: true});
	cy.get('[data-sel-role="delete-results-count"]').should('contain', 'will be deleted');
	cy.get('[data-sel-role="delete-results-confirmation"]').invoke('attr', 'placeholder').then(expected => {
		cy.get('[data-sel-role="delete-results-confirmation"]').type(expected as string);
	});
	cy.get('[data-sel-role="delete-results-confirm"]').should('not.be.disabled').click();
	cy.get('[data-sel-role="delete-results-dialog"]').should('not.exist');
};

/**
 * The Results page lists one entry per form that stored submissions. Deleting a date range of
 * submissions keeps the entry; "Delete all results" removes the entry itself, so the form leaves
 * the page until its next submission recreates it. That is also how an entry already emptied by
 * date range, or whose form was deleted (its results are kept on purpose and flagged),
 * is cleared: neither holds a submission a range could match. An unpublished form is told apart
 * from a deleted one by a lookup in EDIT: its entry is flagged "unpublished" and keeps its title.
 */
describe('Actions - 75 Removing a form entry from the Results page', () => {
	useFormidableSite();

	let entryFormId: string;
	let orphanFormPaths: {formPath: string; pagePath: string};
	let unpublishedFormPath: string;

	before(() => {
		cy.login();
		createStoringForm(ENTRY_FORM).then(info => {
			entryFormId = info.formId;
			submitAsVisitor(info.formId, 'First Person');
			submitAsVisitor(info.formId, 'Second Person');
		});
		createStoringForm(EMPTIED_FORM).then(info => {
			submitAsVisitor(info.formId, 'Only Person');
		});
		createStoringForm(ORPHAN_FORM).then(info => {
			orphanFormPaths = {formPath: info.formPath, pagePath: info.pagePath};
			submitAsVisitor(info.formId, 'Orphaned Person');
		});
		createStoringForm(UNPUBLISHED_FORM).then(info => {
			unpublishedFormPath = info.formPath;
			submitAsVisitor(info.formId, 'Patient Person');
		});
	});

	it('removes the form from the page once all its results are deleted, until its next submission', () => {
		openResultsPage();
		entry(ENTRY_FORM.name).should('contain', ENTRY_FORM.title).find('[data-sel-role="form-deleted"]').should('not.exist');
		deleteAllResults(ENTRY_FORM.name);
		entry(ENTRY_FORM.name).should('not.exist');
		listResultsEntries().should('not.include', ENTRY_FORM.name);

		// The next submission recreates the entry through the same path as the first one.
		submitAsVisitor(entryFormId, 'Third Person');
		listResultsEntries().should('include', ENTRY_FORM.name);
		openResultsPage();
		entry(ENTRY_FORM.name).should('contain', ENTRY_FORM.title);
	});

	it('keeps an entry emptied by a date range, and lets it be removed afterwards', () => {
		openResultsPage();
		openDeleteDialog(EMPTIED_FORM.name);
		cy.get('[data-sel-role="delete-results-dialog"] input[type="date"]').first().type(localDay(-1));
		cy.get('[data-sel-role="delete-results-dialog"] input[type="date"]').last().type(localDay(1));
		cy.get('[data-sel-role="delete-results-count"]').should('contain', '1 submissions will be deleted');
		cy.get('[data-sel-role="delete-results-confirm"]').should('not.be.disabled').click();
		cy.get('[data-sel-role="delete-results-dialog"]').should('not.exist');

		// The range took the submission, not the entry.
		entry(EMPTIED_FORM.name).should('exist');
		listResultsEntries().should('include', EMPTIED_FORM.name);
		cy.contains('No submissions').should('be.visible');

		// A click on the selected entry deselects it (the actions go), a second one selects it again.
		entry(EMPTIED_FORM.name).should('have.attr', 'aria-pressed', 'true').click();
		cy.get('[data-sel-role="delete-results"]').should('not.exist');
		entry(EMPTIED_FORM.name).should('have.attr', 'aria-pressed', 'false').click();
		cy.get('[data-sel-role="delete-results"]').should('exist');

		// A range cannot remove what is left (nothing matches); the whole entry can.
		deleteAllResults(EMPTIED_FORM.name);
		entry(EMPTIED_FORM.name).should('not.exist');
		listResultsEntries().should('not.include', EMPTIED_FORM.name);
	});

	it('flags the entry of a deleted form and lets it be removed', () => {
		// The form goes from both workspaces; its results stay, on purpose.
		deleteNode(orphanFormPaths.pagePath, 'LIVE');
		deleteNode(orphanFormPaths.pagePath);
		deleteNode(orphanFormPaths.formPath, 'LIVE');
		deleteNode(orphanFormPaths.formPath);
		listResultsEntries().should('include', ORPHAN_FORM.name);

		openResultsPage();
		entry(ORPHAN_FORM.name).find('[data-sel-role="form-deleted"]').should('exist');
		deleteAllResults(ORPHAN_FORM.name);
		entry(ORPHAN_FORM.name).should('not.exist');
		listResultsEntries().should('not.include', ORPHAN_FORM.name);
	});

	it('flags the entry of an unpublished form apart from a deleted one, keeps its title, and lets it be removed', () => {
		// A real unpublication, not a removal of the live node: the form still stands in EDIT, and on the
		// core's unpublish route the live workspace has been seen throwing ItemNotFoundException on the
		// results entry's parentForm reference instead of answering null — the page must survive both.
		unpublishNode(unpublishedFormPath, 'en');

		openResultsPage();
		// The whole list is still there: one unresolvable reference must not void the page.
		cy.get('[data-sel-role="form-results-entry"]').should('have.length', 2);
		entry(UNPUBLISHED_FORM.name).should('contain', UNPUBLISHED_FORM.title);
		entry(UNPUBLISHED_FORM.name).find('[data-sel-role="form-unpublished"]').should('exist');
		entry(UNPUBLISHED_FORM.name).find('[data-sel-role="form-deleted"]').should('not.exist');

		openDeleteDialog(UNPUBLISHED_FORM.name);
		cy.get('[data-sel-role="delete-all-results"]').check({force: true});
		cy.get('[data-sel-role="delete-results-count"]').should('contain', 'published again');
		// The confirmation is still the form's title, read from EDIT.
		cy.get('[data-sel-role="delete-results-confirmation"]').should('have.attr', 'placeholder', UNPUBLISHED_FORM.title).type(UNPUBLISHED_FORM.title);
		cy.get('[data-sel-role="delete-results-confirm"]').should('not.be.disabled').click();
		cy.get('[data-sel-role="delete-results-dialog"]').should('not.exist');
		entry(UNPUBLISHED_FORM.name).should('not.exist');
		listResultsEntries().should('not.include', UNPUBLISHED_FORM.name);
	});
});

import {JContent} from '@jahia/jcontent-cypress/dist/page-object/jcontent';
import {createFormNode, FORMIDABLE_TEST_SITE, getInputTextNode} from '../../support/fixtures';
import {withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

const FORM_NAME = 'lists-protected-form';
const FORM_TITLE = 'Lists protected form';
/** The structural lists of a form, autocreated with it: a form without them is broken. */
const LIST_TYPES = ['fmdb:fieldList', 'fmdb:actionList', 'fmdb:fieldActionList'];
/** The platform switch jContent reads: Delete and Cut hidden, no drag in the Page Builder. */
const HIDE_DELETE_MIXIN = 'jmix:hideDeleteAction';

const supertypesOf = (type: string): Cypress.Chainable<string[]> =>
	cy.request({
		method: 'POST',
		url: '/modules/graphql',
		headers: withSameOriginHeaders(),
		body: {
			query: 'query Supertypes($type: String!) { jcr { nodeTypeByName(name: $type) { supertypes { name } } } }',
			variables: {type}
		}
	}).then(response => {
		const body = response.body as {errors?: unknown; data?: {jcr: {nodeTypeByName: {supertypes: Array<{name: string}>}}}};
		expect(body.errors, `GraphQL errors for ${type}`).to.be.undefined;
		return cy.wrap(body.data!.jcr.nodeTypeByName.supertypes.map(s => s.name), {log: false});
	});

/**
 * The lists of a form — its fields, its actions, and the actions of a field — are its structure: the
 * repository does not enforce their `mandatory` declaration on a removal, so the types carry the platform's
 * jmix:hideDeleteAction, and jContent offers neither Delete nor Cut on them. The form itself, and the
 * fields inside the list, stay deletable as before.
 */
describe('Validation - 50 The lists of a form cannot be deleted by hand', () => {
	useFormidableSite();

	before(() => {
		cy.login();
		createFormNode(FORM_NAME, FORM_TITLE, [getInputTextNode({name: 'fullName', title: 'Full name'})]);
		cy.logout();
	});

	it('declares the platform switch on the three list types', () => {
		LIST_TYPES.forEach(type => {
			supertypesOf(type).should('include', HIDE_DELETE_MIXIN);
		});
	});

	it('offers neither Delete nor Cut on the Fields and Actions lists, and still Delete on the form and on a field', () => {
		const contents = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', 'content-folders/contents').switchToListMode();
		const formMenu = contents.getTable().getRowByLabel(FORM_TITLE).contextMenu();
		formMenu.shouldHaveItem('Delete');
		formMenu.close();

		const form = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', `content-folders/contents/${FORM_NAME}`).switchToListMode();
		['Form fields', 'Form actions'].forEach(label => {
			const menu = form.getTable().getRowByLabel(label).contextMenu();
			menu.shouldHaveItem('Edit');
			menu.shouldNotHaveItem('Delete');
			menu.shouldNotHaveItem('Cut');
			menu.close();
		});

		const fields = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', `content-folders/contents/${FORM_NAME}/fields`).switchToListMode();
		const fieldMenu = fields.getTable().getRowByLabel('Full name').contextMenu();
		fieldMenu.shouldHaveItem('Delete');
		fieldMenu.close();
	});
});

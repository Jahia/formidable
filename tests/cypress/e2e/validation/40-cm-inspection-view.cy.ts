import gql from 'graphql-tag';
import {
	createConditionalLogicForm,
	createFormNode,
	createPublishedLiveFormPage,
	getInputTextNode,
	getStepNode
} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

const RENDER_VIEW = gql`
	query renderView($path: String!, $view: String!) {
		jcr(workspace: EDIT) {
			nodeByPath(path: $path) {
				renderedContent(templateType: "html", view: $view, contextConfiguration: "module", language: "en") {
					output
				}
			}
		}
	}
`;

type RenderedViewResponse = {
	data?: {jcr?: {nodeByPath?: {renderedContent?: {output?: string}}}};
	errors?: unknown;
};

const renderView = (path: string, view: string): Cypress.Chainable<string> =>
	cy.apollo({query: RENDER_VIEW, variables: {path, view}}).then((response: RenderedViewResponse) => {
		expect(response.errors, `GraphQL errors rendering ${view} of ${path}`).to.equal(undefined);
		const output = response.data?.jcr?.nodeByPath?.renderedContent?.output;
		expect(output, `${view} output of ${path}`).to.be.a('string').and.not.be.empty;
		return cy.wrap(output, {log: false});
	});

/**
 * The cm view is jContent's inspection surface (preview drawer, Content Editor preview):
 * those panels render server markup with no JavaScript, so the live rendering is a dead
 * end there — a multi-step form stays frozen behind inert buttons and logic-hidden fields
 * are unreachable. The cm view shows what the form CONTAINS instead: every step stacked
 * under its title, conditional fields visible, no buttons.
 */
describe('Validation - 40 cm inspection view', () => {
	useFormidableSite();

	it('stacks every step under its title and renders no button', () => {
		const formName = 'cm-steps-form';

		createFormNode(formName, 'CM Steps Form', [
			getStepNode({
				name: 'identityStepCm',
				title: 'Identity',
				label: 'Identity',
				children: [getInputTextNode({name: 'cmEmployeeCode', title: 'Employee code', required: true})]
			}),
			getStepNode({
				name: 'detailsStepCm',
				title: 'Details',
				label: 'Details',
				children: [getInputTextNode({name: 'cmComment', title: 'Comment'})]
			})
		]).then(() => {
			renderView(`${CONTENT_PATH}/${formName}`, 'cm').then(output => {
				expect(output).to.contain('Identity');
				expect(output).to.contain('Details');
				expect(output).to.contain('cmEmployeeCode');
				expect(output).to.contain('cmComment');
				// No step is folded away, and none of the dead-without-a-script buttons render
				expect(output).to.not.match(/display:\s*none/);
				expect(output).to.not.contain('<button');
				expect(output).to.not.contain('fmdb-steps-nav');
			});
		});
	});

	it('wraps a container opened on its own in the form shell', () => {
		const formName = 'cm-container-form';

		createFormNode(formName, 'CM Container Form', [
			getStepNode({
				name: 'shellStepCm',
				title: 'Shell step',
				label: 'Shell step',
				children: [getInputTextNode({name: 'cmShellField', title: 'Shell field'})]
			})
		]).then(() => {
			// The shell (module stylesheet + fmdb-form wrapper) is what the generic
			// fallback rendering was missing; its marker is the unambiguous witness
			// (a bare 'fmdb-form' substring would match the children's fmdb-form-element).
			renderView(`${CONTENT_PATH}/${formName}/fields`, 'cm').then(output => {
				expect(output).to.contain('data-fmdb-cm-view');
				expect(output).to.contain('Shell step');
				expect(output).to.not.contain('<button');
			});
		});
	});

	it('keeps logic-driven fields visible, where the default view hides them', () => {
		createConditionalLogicForm('cm-view').then(({formPath}) => {
			// The default view (what a visitor gets) hides the nickname field until the
			// role select matches: the contrast proves the cm lever, not just an absence.
			renderView(formPath, 'default').then(output => {
				expect(output).to.contain('data-fmdb-logic-hidden="true"');
			});

			renderView(formPath, 'cm').then(output => {
				expect(output).to.contain('nickname');
				expect(output).to.not.contain('data-fmdb-logic-hidden');
			});
		});
	});

	it('inspects the referenced form through a form reference', () => {
		const formName = 'cm-reference-form';

		createPublishedLiveFormPage(formName, 'CM Reference Form', [
			getInputTextNode({name: 'cmReferencedField', title: 'Referenced field'})
		]).then(({pagePath}) => {
			renderView(`${pagePath}/pagecontent/${formName}-reference`, 'cm').then(output => {
				expect(output).to.contain('cmReferencedField');
				expect(output).to.not.contain('<button');
			});
		});
	});
});

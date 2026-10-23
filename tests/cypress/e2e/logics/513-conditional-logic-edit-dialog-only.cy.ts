import gql from 'graphql-tag';
import {CONTENT_PATH} from '../../support/constants';
import {createFormNode, getFieldsetNode, getInputTextNode} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

const FORM_NAME = 'logic-dialogs-form';
const FORM_TITLE = 'Logic dialogs form';
/** The technical name of the Conditional display section (`fmdb.section.logic`). */
const LOGIC_SECTION = 'logic';
/** A section every dialog has: the witness that the query read a Formidable dialog at all. */
const CONTENT_SECTION = 'content';

const CREATE_FORM = gql`
	query createDialogSections($type: String!, $parent: String!) {
		forms {
			createForm(primaryNodeType: $type, uiLocale: "en", locale: "en", uuidOrPath: $parent) {
				sections {
					name
				}
			}
		}
	}
`;

const EDIT_FORM = gql`
	query editDialogSections($path: String!) {
		forms {
			editForm(uiLocale: "en", locale: "en", uuidOrPath: $path) {
				sections {
					name
				}
			}
		}
	}
`;

type Dialog = 'createForm' | 'editForm';

interface DialogResponse {
	errors?: unknown;
	data?: {forms?: Partial<Record<Dialog, {sections: {name: string}[]}>>};
}

const fieldsPath = `${CONTENT_PATH}/${FORM_NAME}/fields`;

/** The section names of a dialog, through the query the Content Editor itself sends. */
const sectionNamesOf = (dialog: Dialog, variables: Record<string, string>, what: string) =>
	cy.apollo({query: dialog === 'createForm' ? CREATE_FORM : EDIT_FORM, variables})
		.then((response: DialogResponse) => {
			expect(response.errors, `GraphQL errors for the ${dialog} of ${what}`).to.be.undefined;
			return cy.wrap((response.data?.forms?.[dialog]?.sections ?? []).map(section => section.name));
		});

/**
 * Conditional display belongs to the edit dialog: at creation the author names and configures the
 * field, the rules come once it exists among its siblings (#448). The engine override declares the
 * section for the edit display mode only, and jContent leaves it out of the create dialog, for a
 * field and for a container alike.
 */
describe('Form logic - 513 Conditional display is an edit-dialog section', () => {
	useFormidableSite();

	before(() => {
		cy.login();
		createFormNode(FORM_NAME, FORM_TITLE, [
			getInputTextNode({name: 'country', title: 'Country'}),
			getFieldsetNode({name: 'address', title: 'Address', children: [getInputTextNode({name: 'street', title: 'Street'})]})
		]);
		cy.logout();
	});

	['fmdb:inputText', 'fmdb:fieldset'].forEach(type => {
		it(`keeps Conditional display out of the create dialog of ${type}`, () => {
			sectionNamesOf('createForm', {type, parent: fieldsPath}, type).then(names => {
				expect(names, `sections of the create dialog of ${type}`).to.include(CONTENT_SECTION);
				expect(names, `no Conditional display while creating ${type}`).not.to.include(LOGIC_SECTION);
			});
		});
	});

	[
		{name: 'country', what: 'a field'},
		{name: 'address', what: 'a container'}
	].forEach(({name, what}) => {
		it(`shows Conditional display in the edit dialog of ${what}`, () => {
			sectionNamesOf('editForm', {path: `${fieldsPath}/${name}`}, what).then(names => {
				expect(names, `sections of the edit dialog of ${what}`).to.include(CONTENT_SECTION);
				expect(names, `Conditional display while editing ${what}`).to.include(LOGIC_SECTION);
			});
		});
	});
});

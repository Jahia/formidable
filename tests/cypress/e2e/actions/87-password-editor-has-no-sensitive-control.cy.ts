import gql from 'graphql-tag';
import {ContentEditor} from '@jahia/jcontent-cypress/dist/page-object';
import {CONTENT_PATH} from '../../support/constants';
import {FORMIDABLE_TEST_SITE, createFormNode, getInputTextNode, getPasswordNode} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

const EDIT_FORM = gql`
	query editFormOfPassword($path: String!) {
		forms {
			editForm(uiLocale: "en", locale: "en", uuidOrPath: $path) {
				sections {
					name
					fieldSets {
						name
						fields {
							name
						}
					}
				}
			}
		}
	}
`;

const MARKERS = gql`
	query markersOfPassword($path: String!) {
		jcr {
			nodeByPath(path: $path) {
				sensitive: isNodeType(type: {types: ["fmdbmix:sensitiveField"]})
				mappable: isNodeType(type: {types: ["fmdbmix:profileMappableField"]})
				logicOperand: isNodeType(type: {types: ["fmdbmix:textField"]})
			}
		}
	}
`;

interface EditFormResponse {
	errors?: unknown;
	data?: {forms?: {editForm?: {sections: {name: string; fieldSets: {name: string; fields: {name: string}[]}[]}[]}}};
}

interface MarkersResponse {
	errors?: unknown;
	data?: {jcr?: {nodeByPath?: {sensitive: boolean; mappable: boolean; logicOperand: boolean}}};
}

/** The fieldset the jExperience module adds to a mappable field's editor (formidable-jexperience-engine). */
const PROFILE_MAPPING_FIELDSET = 'fmdbmix:jExperienceProfileMapping';

/**
 * Sensitivity is a property of the field type (docs/architecture/form-submission-flow.md, decision log D1): the
 * password input declares fmdbmix:sensitiveField in its CND, and there is nothing for an author to tick. Its editor
 * holds no "sensitive" control, no jExperience profile mapping, and the type is neither profile-mappable nor a
 * conditional-logic operand (no fmdbmix:textField). Read through the editor form the Content Editor builds, as spec
 * 225 reads the layouts, then in the Content Editor itself.
 */
describe('Actions - 87 The editor of a password has no sensitive control', () => {
	useFormidableSite();

	const formName = 'password-editor-form';
	const passwordPath = `${CONTENT_PATH}/${formName}/fields/password`;

	before(() => {
		createFormNode(formName, 'Password Editor', [
			getInputTextNode({name: 'username', title: 'Username'}),
			getPasswordNode({name: 'password', title: 'Password'})
		]);
	});

	it('declares the marker on the type and offers the author nothing about it', () => {
		cy.apollo({query: MARKERS, variables: {path: passwordPath}}).then((response: MarkersResponse) => {
			expect(response.errors, 'GraphQL errors for the markers of the password').to.be.undefined;
			const node = response.data?.jcr?.nodeByPath;
			expect(node?.sensitive, 'the password is a sensitive field').to.be.true;
			expect(node?.mappable, 'the password is not profile-mappable').to.be.false;
			expect(node?.logicOperand, 'the password is no conditional-logic operand').to.be.false;
		});

		cy.apollo({query: EDIT_FORM, variables: {path: passwordPath}}).then((response: EditFormResponse) => {
			expect(response.errors, 'GraphQL errors for the edit form of the password').to.be.undefined;
			const sections = response.data?.forms?.editForm?.sections ?? [];
			const fieldSets = sections.flatMap(section => section.fieldSets);
			const fields = fieldSets.flatMap(fieldSet => fieldSet.fields.map(field => field.name));

			// The editor is the password's own: what it is about is there, so the reading below is not of an empty form.
			expect(fields, 'the fields of the password editor').to.include.members(['required', 'autocompleteMode', 'minLength', 'maxLength']);

			// Nothing an author ticks: no field, fieldset nor section speaks of sensitivity.
			[...sections.map(section => section.name), ...fieldSets.map(fieldSet => fieldSet.name), ...fields]
				.forEach(name => expect(name, 'a name of the password editor').not.to.match(/sensitive/i));
			// No jExperience mapping either.
			expect(fieldSets.map(fieldSet => fieldSet.name), 'no profile mapping on the password').not.to.include(PROFILE_MAPPING_FIELDSET);
		});
	});

	it('shows no sensitive control in the Content Editor', () => {
		ContentEditor.visit(passwordPath, FORMIDABLE_TEST_SITE.key, 'en', 'content-folders/contents');
		// The editor is drawn: its own fields are there, Required among them.
		cy.get('[data-sel-content-editor-field]', {timeout: 15000}).should('have.length.greaterThan', 0);
		cy.get('[data-sel-content-editor-field$="_required"]').should('exist');
		// The field settings open too, so that a control hidden in a collapsed section would be in the page.
		ContentEditor.getContentEditor().openSection('fieldSettings');
		cy.get('[data-sel-content-editor-field$="_autocompleteMode"]').should('exist');
		cy.get('[data-sel-content-editor-field]').each(field => {
			expect(field.attr('data-sel-content-editor-field'), 'a field of the password editor').not.to.match(/sensitive/i);
		});
		cy.get('[data-sel-role-dynamic-fieldset]').each(fieldSet => {
			expect(fieldSet.attr('data-sel-role-dynamic-fieldset'), 'a switch of the password editor').not.to.match(/sensitive/i);
		});
		cy.get(ContentEditor.defaultSelector).should('not.contain.text', 'ensitive');
		ContentEditor.getContentEditor().cancel();
	});
});

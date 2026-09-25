import gql from 'graphql-tag';
import {CONTENT_PATH} from '../../support/constants';
import {
	createFormNode,
	getFieldsetNode,
	getInputButtonNode,
	getInputFileNode,
	getInputTextNode,
	getRatingNode,
	getStepNode
} from '../../support/fixtures';
import {useFormidableSite} from './support';

/** The dynamic fieldset of the field's editor form that switches the field actions on. */
const SWITCH = 'fmdbmix:fieldActions';

const EDIT_FORM = gql`
	query editFormOfElement($path: String!) {
		forms {
			editForm(uiLocale: "en", locale: "en", uuidOrPath: $path) {
				sections {
					name
					fieldSets {
						name
						dynamic
						hasEnableSwitch
					}
				}
			}
		}
	}
`;

interface FieldSet {
	name: string;
	dynamic?: boolean;
	hasEnableSwitch?: boolean;
}

interface EditFormResponse {
	errors?: unknown;
	data?: {forms?: {editForm?: {sections: {name: string; fieldSets: FieldSet[]}[]}}};
}

/**
 * The Enable field actions switch is offered by fmdbmix:submittableField — the engine's marker of a
 * field with a value, declared by every built-in and extended field type but the file input — and by
 * nothing else. A fieldset and a button carry fmdbmix:formElement too (title and logic, through
 * fmdbmix:element for the button), and the first cut offered them a switch whose actions could never
 * run: they are non-submittable, and the pipeline judges `formElement && !nonSubmittable`. The switch
 * follows the type, not the place: a text field inside a fieldset or a step gets it, the fieldset and
 * the step do not (docs/architecture/field-actions.md, "Which fields offer it").
 */
describe('Form fields - 223 The field actions switch, per field type', () => {
	useFormidableSite();

	it('is offered on the fields with a value, built-in and extended, wherever they sit — and on nothing else', () => {
		const formPath = `${CONTENT_PATH}/field-actions-switch-form`;
		const stepsPath = `${CONTENT_PATH}/field-actions-switch-steps`;
		createFormNode('field-actions-switch-form', 'Field Actions Switch Form', [
			getInputTextNode({name: 'text', title: 'Text'}),
			getRatingNode({name: 'rating', title: 'Rating'}),
			getInputFileNode({name: 'file', title: 'File'}),
			getInputButtonNode({name: 'button', title: 'Button'}),
			getFieldsetNode({name: 'group', title: 'Group', children: [getInputTextNode({name: 'inGroup', title: 'In group'})]})
		]);
		createFormNode('field-actions-switch-steps', 'Field Actions Switch Steps', [
			getStepNode({name: 'stepOne', title: 'Step one', children: [getInputTextNode({name: 'inStep', title: 'In step'})]})
		]);

		// The switch fieldset of an element's editor form, or null: wrapped, since Cypress reads an
		// undefined return as "keep the previous subject".
		const switchOf = (path: string) => cy.apollo({query: EDIT_FORM, variables: {path}})
			.then((response: EditFormResponse) => {
				expect(response.errors, `GraphQL errors for the edit form of ${path}`).to.be.undefined;
				const fieldSets = (response.data?.forms?.editForm?.sections ?? []).flatMap(section => section.fieldSets);
				return {found: fieldSets.find(fieldSet => fieldSet.name === SWITCH) ?? null};
			});

		// A value — a built-in text, an extended rating, a text inside a fieldset, a text inside a
		// step: the switch, dynamic, with its enable switch.
		[
			`${formPath}/fields/text`,
			`${formPath}/fields/rating`,
			`${formPath}/fields/group/inGroup`,
			`${stepsPath}/fields/stepOne/inStep`
		].forEach(path => {
			switchOf(path).then(({found}) => {
				expect(found, `the switch on ${path}`).not.to.be.null;
				expect(found?.dynamic, `dynamic on ${path}`).to.be.true;
				expect(found?.hasEnableSwitch, `enable switch on ${path}`).to.be.true;
			});
		});

		// A file, a button, a fieldset, a step: no switch — their actions could never run.
		[
			`${formPath}/fields/file`,
			`${formPath}/fields/button`,
			`${formPath}/fields/group`,
			`${stepsPath}/fields/stepOne`
		].forEach(path => {
			switchOf(path).then(({found}) => {
				expect(found, `no switch on ${path}`).to.be.null;
			});
		});
	});
});

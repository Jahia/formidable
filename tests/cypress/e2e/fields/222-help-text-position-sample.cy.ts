import gql from 'graphql-tag';
import {enableModule} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	FORMIDABLE_TEST_SITE,
	getInputTextNode,
	visitLiveForm
} from '../../support/fixtures';
import type {JahiaNode} from '../../support/fixtures';
import {useFormidableSite} from './support';

/** The sample module extending the built-in text input with a help text position. */
const SAMPLE_MODULE = 'formidable-test-module-samples-tsx';
const SAMPLE_VIEW = 'helpTextPosition';
const POSITION_MIXIN = 'fmdbsamplemix:helpTextPosition';

type HelpTextPosition = 'up' | 'down' | 'both';

const HELP = 'Between 3 and 20 characters.';

const FIELDS = {
	up: {name: 'helpUp', title: 'Help above', helpText: HELP},
	down: {name: 'helpDown', title: 'Help below', helpText: HELP},
	both: {name: 'helpBoth', title: 'Help above and below', helpText: HELP},
	// The view without the mixin's setting: the built-in placement.
	unset: {name: 'helpUnset', title: 'Help unset', helpText: HELP},
	// A placement without any help text: nothing to place.
	none: {name: 'helpNone', title: 'No help'}
};

/** A text input rendered by the sample view, the help text where the contributor put it. */
const withHelpTextPosition = (node: JahiaNode, position?: HelpTextPosition): JahiaNode => {
	node.mixins = [...(node.mixins ?? []), 'jmix:renderable', ...(position ? [POSITION_MIXIN] : [])];
	node.properties.push({name: 'j:view', value: SAMPLE_VIEW});
	if (position) {
		node.properties.push({name: 'helpTextPosition', value: position});
	}

	return node;
};

const EDIT_FORM = gql`
	query editFormOfTextInput($path: String!) {
		forms {
			editForm(uiLocale: "en", locale: "en", uuidOrPath: $path) {
				sections {
					name
					fieldSets {
						name
						fields {
							name
							valueConstraints {
								displayValue
								value {
									string
								}
							}
						}
					}
				}
			}
		}
	}
`;

interface EditFormResponse {
	errors?: unknown;
	data?: {
		forms?: {
			editForm?: {
				sections: {
					name: string;
					fieldSets: {
						name: string;
						fields: {
							name: string;
							valueConstraints?: {displayValue: string; value?: {string?: string}}[];
						}[];
					}[];
				}[];
			};
		};
	};
}

/**
 * A third-party module adds a setting to the built-in text input — where its help text goes —
 * and a view honouring it: above the field (the built-in rendering), below it, or in both
 * places. The setting is a mixin the module's own form override surfaces inside the text
 * input's own editor fieldset, right under Help text, and keeps in force without a switch; the
 * view is picked per field through the View chooser. Whatever the placement, the control keeps
 * describing one help block for assistive technology; the repeat of "both" is decorative.
 */
describe('Form fields - 222 Help text position (third-party sample)', () => {
	useFormidableSite();

	before(() => {
		// The site created by useFormidableSite knows the core modules only: the sample module's
		// views, mixin and form override are offered on a site once the module is enabled there.
		enableModule(SAMPLE_MODULE, FORMIDABLE_TEST_SITE.key);
	});

	it('places the help text above, below or on both sides of the field, described once', () => {
		createPublishedLiveFormPage('help-text-position-form', 'Help Text Position Form', [
			withHelpTextPosition(getInputTextNode(FIELDS.up), 'up'),
			withHelpTextPosition(getInputTextNode(FIELDS.down), 'down'),
			withHelpTextPosition(getInputTextNode(FIELDS.both), 'both'),
			withHelpTextPosition(getInputTextNode(FIELDS.unset)),
			withHelpTextPosition(getInputTextNode(FIELDS.none), 'down')
		]).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// Above: the single help block precedes the control, as the built-in view renders it.
			[FIELDS.up, FIELDS.unset].forEach(field => {
				const input = form.getTextInput(field.name);
				input.shouldHaveHelpText(HELP);
				input.getContainer().find('.fmdb-form-help').should('have.length', 1);
				input.getInput().prev().should('have.class', 'fmdb-form-help');
				// The built-in contract of a text input is kept by the third-party view.
				input.getInput()
					.should('have.class', 'fmdb-form-control')
					.and('have.attr', 'name', field.name)
					.invoke('attr', 'id').should('match', /^input-/);
			});

			// Below: the single help block follows the control, still the one it is described by.
			const below = form.getTextInput(FIELDS.down.name);
			below.shouldHaveHelpText(HELP);
			below.getContainer().find('.fmdb-form-help').should('have.length', 1);
			below.getInput().next().should('have.class', 'fmdb-form-help');
			below.getInput().prev().should('have.class', 'fmdb-form-label');

			// Both: two blocks. The first carries the id the control references; the second is a
			// visual repeat, without id and hidden from assistive technology.
			const both = form.getTextInput(FIELDS.both.name);
			both.getContainer().find('.fmdb-form-help').should('have.length', 2).each($help => {
				expect($help.text(), 'help text of each block').to.equal(HELP);
			});
			both.getInput().prev().should('have.class', 'fmdb-form-help')
				.and('not.have.attr', 'aria-hidden')
				.invoke('attr', 'id').then(helpId => {
					expect(helpId, 'id of the described block').to.match(/^help-/);
					both.getInput().should('have.attr', 'aria-describedby', helpId);
				});
			both.getInput().next().should('have.class', 'fmdb-form-help')
				.and('have.attr', 'aria-hidden', 'true')
				.and('not.have.attr', 'id');

			// No help text: nothing to place, and nothing referenced.
			const none = form.getTextInput(FIELDS.none.name);
			none.shouldNotHaveHelpText();
			none.getInput().should('not.have.attr', 'aria-describedby');
		});
	});

	it('offers the position right under the help text in the editor, without a switch', () => {
		createPublishedLiveFormPage('help-text-position-editor-form', 'Help Text Position Editor Form', [
			withHelpTextPosition(getInputTextNode(FIELDS.up), 'up')
		]).then(({formPath}) => {
			cy.apollo({
				query: EDIT_FORM,
				variables: {path: `${formPath}/fields/${FIELDS.up.name}`}
			}).then((response: EditFormResponse) => {
				expect(response.errors, 'GraphQL errors for the edit form').to.be.undefined;

				const sections = response.data?.forms?.editForm?.sections ?? [];
				const content = sections.find(section => section.name === 'content');
				const main = content?.fieldSets.find(fieldSet => fieldSet.name === 'fmdb:inputText');
				const names = main?.fields.map(field => field.name) ?? [];

				// Surfaced in the text input's own fieldset, right after Help text.
				expect(names, 'fields of the text input fieldset').to.include('helpText');
				expect(names[names.indexOf('helpText') + 1], 'field under Help text').to.equal('helpTextPosition');

				// Its own fieldset holds nothing any more: no switch to flip before the setting shows.
				const own = content?.fieldSets.find(fieldSet => fieldSet.name === POSITION_MIXIN);
				expect(own?.fields ?? [], 'fields left in the mixin fieldset').to.be.empty;

				// The choices, labelled from the sample module's bundle.
				const position = main?.fields.find(field => field.name === 'helpTextPosition');
				expect(position?.valueConstraints?.map(constraint => constraint.value?.string), 'values')
					.to.deep.equal(['up', 'down', 'both']);
				expect(position?.valueConstraints?.map(constraint => constraint.displayValue), 'labels')
					.to.deep.equal(['Above the field', 'Below the field', 'Above and below the field']);
			});
		});
	});
});

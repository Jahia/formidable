import gql from 'graphql-tag';
import {CONTENT_PATH} from '../../support/constants';
import {createFormNode, getInputEmailNode, getInputTextNode, getTextareaNode} from '../../support/fixtures';
import {useFormidableSite} from './support';

const EDIT_FORM = gql`
	query editFormOfField($path: String!) {
		forms {
			editForm(uiLocale: "en", locale: "en", uuidOrPath: $path) {
				sections {
					name
					expanded
					fieldSets {
						name
						visible
						dynamic
						hasEnableSwitch
						fields {
							name
						}
					}
				}
			}
		}
	}
`;

interface FieldSet {
	name: string;
	visible?: boolean;
	dynamic?: boolean;
	hasEnableSwitch?: boolean;
	fields: {name: string}[];
}

interface Section {
	name: string;
	expanded?: boolean;
	fieldSets: FieldSet[];
}

interface EditFormResponse {
	errors?: unknown;
	data?: {forms?: {editForm?: {sections: Section[]}}};
}

/** What Content keeps on every reorganised field type: the title, the system name, the required switch. */
const CONTENT_FIELDS = ['jcr:title', 'ce:systemName', 'required'];
/** The editor's own block listing a node's children, which the field actions list makes appear. */
const LIST_ORDERING_SECTION = 'listOrdering';
const SETTINGS_SECTION = 'fieldSettings';
const FIELD_ACTIONS_SWITCH = 'fmdbmix:fieldActions';
const VALIDATION_MESSAGES = ['msgValueMissing', 'msgTypeMismatch', 'msgPatternMismatch', 'msgTooShort', 'msgTooLong'];

/**
 * The Field settings section of each type, fieldset by fieldset in rank order, and what the type's
 * storage mixin keeps hidden (docs/architecture/content-editor-layout.md).
 */
const LAYOUTS: Record<string, {settings: [string, string[]][]; hidden?: {fieldSet: string; fields: string[]}}> = {
	'fmdb:inputText': {
		hidden: {fieldSet: 'fmdbmix:advancedInputTextSettings', fields: ['form', 'dirname', 'size']},
		settings: [
			['helpAndPresentation', ['helpText', 'title']],
			['valueAndInput', ['placeholder', 'defaultValue', 'mask', 'pattern', 'autocomplete', 'spellcheck', 'list']],
			['constraints', ['minLength', 'maxLength']],
			['behaviour', ['readonly', 'disabled', 'autofocus']],
			['validationMessages', VALIDATION_MESSAGES]
		]
	},
	'fmdb:inputEmail': {
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['placeholder', 'defaultValue', 'multiple', 'pattern', 'autocomplete', 'list']],
			['constraints', ['minLength', 'maxLength']],
			['validationMessages', VALIDATION_MESSAGES]
		]
	},
	'fmdb:textarea': {
		hidden: {fieldSet: 'fmdbmix:advancedTextareaSettings', fields: ['cols', 'form', 'dirname']},
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['placeholder', 'defaultValue', 'autocomplete', 'spellcheck', 'wrap', 'rows', 'resize']],
			['constraints', ['minLength', 'maxLength']],
			['behaviour', ['readonly', 'disabled', 'autofocus']],
			['validationMessages', VALIDATION_MESSAGES]
		]
	}
};

/**
 * The editor of a text, an email and a textarea field is laid out by Content Editor form overrides, not
 * by the CND: Content keeps what a contributor almost always fills, every other setting sits in the Field
 * settings section, in fieldsets shared by the three types. The properties the type declares behind its
 * "advanced settings" mixin are spread over those fieldsets and the mixin is kept as hidden storage, always
 * activated; the attributes a form never needs (form, dirname, size, cols) stay in that hidden fieldset.
 * The validation messages join the section; the field actions switch stays at the end of Content, a
 * capability of the field rather than a setting, next to the children block its list makes the editor
 * show — a block the engine folds, one node being nothing to order. Read through the editor form
 * the Content Editor builds, on a field carrying the storage mixin and on one without it: the layout is
 * the same, which is the point of the hidden always-activated fieldset.
 */
describe('Form fields - 225 The field editor layout of the text family', () => {
	useFormidableSite();

	const formName = 'editor-layout-text-family';
	const fieldPath = (name: string) => `${CONTENT_PATH}/${formName}/fields/${name}`;

	before(() => {
		createFormNode(formName, 'Editor Layout Text Family', [
			// A mask puts the storage mixin on the node; the plain one has no mixin at all.
			getInputTextNode({name: 'maskedText', title: 'Masked text', mask: 'AA-9999'}),
			getInputTextNode({name: 'plainText', title: 'Plain text'}),
			getInputEmailNode({name: 'email', title: 'Email'}),
			// wrap puts the storage mixin on the node.
			getTextareaNode({name: 'wrappedTextarea', title: 'Wrapped textarea', wrap: 'hard'}),
			getTextareaNode({name: 'plainTextarea', title: 'Plain textarea'})
		]);
	});

	const editFormOf = (name: string) => cy.apollo({query: EDIT_FORM, variables: {path: fieldPath(name)}})
		.then((response: EditFormResponse) => {
			expect(response.errors, `GraphQL errors for the edit form of ${name}`).to.be.undefined;
			return cy.wrap(response.data?.forms?.editForm?.sections ?? []);
		});

	const assertLayout = (name: string, type: string) => editFormOf(name).then(sections => {
		const layout = LAYOUTS[type];
		const content = sections.find(section => section.name === 'content');
		const main = content?.fieldSets.find(fieldSet => fieldSet.name === type);
		expect(main?.fields.map(field => field.name), `Content fields of ${name}`).to.deep.equal(CONTENT_FIELDS);

		if (layout.hidden) {
			// The storage mixin: hidden, always activated, keeping only what no form needs.
			const storage = content?.fieldSets.find(fieldSet => fieldSet.name === layout.hidden?.fieldSet);
			expect(storage, `storage fieldset of ${name}`).not.to.be.undefined;
			expect(storage?.visible, `storage fieldset hidden on ${name}`).to.be.false;
			expect(storage?.fields.map(field => field.name), `hidden fields of ${name}`).to.deep.equal(layout.hidden.fields);
		}

		const settings = sections.find(section => section.name === SETTINGS_SECTION);
		expect(settings, `Field settings section of ${name}`).not.to.be.undefined;
		expect(settings?.fieldSets.map(fieldSet => fieldSet.name), `fieldsets of ${name}`)
			.to.deep.equal(layout.settings.map(([fieldSet]) => fieldSet));
		layout.settings.forEach(([fieldSetName, fields]) => {
			const fieldSet = settings?.fieldSets.find(candidate => candidate.name === fieldSetName);
			expect(fieldSet?.fields.map(field => field.name), `fields of ${fieldSetName} on ${name}`).to.deep.equal(fields);
			expect(fieldSet?.visible, `${fieldSetName} visible on ${name}`).to.be.true;
		});

		// The field actions switch stays in Content, after the type's own fieldset, with its enable switch.
		const contentNames = content?.fieldSets.filter(fieldSet => fieldSet.visible).map(fieldSet => fieldSet.name) ?? [];
		expect(contentNames.indexOf(FIELD_ACTIONS_SWITCH), `field actions switch after the ${type} fieldset on ${name}`)
			.to.be.greaterThan(contentNames.indexOf(type));
		const actions = content?.fieldSets.find(fieldSet => fieldSet.name === FIELD_ACTIONS_SWITCH);
		expect(actions?.dynamic, `field actions switch dynamic on ${name}`).to.be.true;
		expect(actions?.hasEnableSwitch, `field actions enable switch on ${name}`).to.be.true;
		expect(settings?.fieldSets.map(fieldSet => fieldSet.name), `no switch in Field settings on ${name}`).not.to.include(FIELD_ACTIONS_SWITCH);

		// The Validation messages section is gone; the editor's children block starts folded.
		expect(sections.map(section => section.name), `sections of ${name}`).not.to.include('validationMessages');
		expect(sections.find(section => section.name === LIST_ORDERING_SECTION)?.expanded, `children block folded on ${name}`).to.be.false;
	});

	it('lays out a text input the same way with and without its storage mixin', () => {
		assertLayout('maskedText', 'fmdb:inputText');
		assertLayout('plainText', 'fmdb:inputText');
	});

	it('lays out an email input', () => {
		assertLayout('email', 'fmdb:inputEmail');
	});

	it('lays out a textarea the same way with and without its storage mixin', () => {
		assertLayout('wrappedTextarea', 'fmdb:textarea');
		assertLayout('plainTextarea', 'fmdb:textarea');
	});
});

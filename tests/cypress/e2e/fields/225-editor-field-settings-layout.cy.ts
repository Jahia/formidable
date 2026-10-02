import gql from 'graphql-tag';
import {CONTENT_PATH} from '../../support/constants';
import {
	createFormNode,
	getFieldsetNode,
	getCheckboxNode,
	getConsentNode,
	getInputColorNode,
	getInputDateNode,
	getInputFileNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputNumberNode,
	getInputRangeNode,
	getInputTextNode,
	getRadioNode,
	getRatingNode,
	getScaleNode,
	getSelectNode,
	getSwitchNode,
	getTextareaNode
} from '../../support/fixtures';
import type {JahiaNode} from '../../support/fixtures';
import {useFormidableSite} from './support';

const EDIT_FORM = gql`
	query editFormOfField($path: String!) {
		forms {
			editForm(uiLocale: "en", locale: "en", uuidOrPath: $path) {
				sections {
					name
					fieldSets {
						name
						description
						visible
						dynamic
						activated
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
	description?: string;
	visible?: boolean;
	dynamic?: boolean;
	activated?: boolean;
	hasEnableSwitch?: boolean;
	fields: {name: string}[];
}

interface Section {
	name: string;
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
/** The sample module's setting (spec 222 enables the module on this site): not part of the layout under test. */
const SAMPLE_FIELD = 'helpTextPosition';
const SETTINGS_SECTION = 'fieldSettings';
const FIELD_ACTIONS_SWITCH = 'fmdbmix:fieldActions';
const TEXT_MESSAGES = ['msgValueMissing', 'msgTypeMismatch', 'msgPatternMismatch', 'msgTooShort', 'msgTooLong'];
const RANGE_MESSAGES = ['msgValueMissing', 'msgRangeUnderflow', 'msgRangeOverflow', 'msgStepMismatch', 'msgBadInput'];
/** The one note under the Validation messages title, said once instead of under each message. */
const VALIDATION_MESSAGES_NOTE = /browser/;
/** The form's Step navigation fieldset in Buttons: the switch of the steps bar, then the two labels it governs. */
const STEP_NAVIGATION = ['showStepsNav', 'previousBtnLabel', 'nextBtnLabel'];
/** The four dynamic bound fieldsets of a date contract, at ranks 3.1 to 3.4, each carrying one mode's value. */
const boundFieldSets = (kind: 'Date' | 'Datetime'): [string, string[]][] => [
	[`fmdbmix:fixedMin${kind}`, ['min']],
	[`fmdbmix:relativeMin${kind}`, ['minRelativeAmount', 'minRelativeUnit']],
	[`fmdbmix:fixedMax${kind}`, ['max']],
	[`fmdbmix:relativeMax${kind}`, ['maxRelativeAmount', 'maxRelativeUnit']]
];

interface Layout {
	/** What Content keeps besides the title and the system name: Required, and what defines the field. */
	content?: string[];
	/** A file field has no field actions switch: what it submits is a file, not a value a check judges. */
	noFieldActions?: boolean;
	/** The Field settings section, fieldset by fieldset in rank order. */
	settings: [string, string[]][];
	/** The HTML attributes the CND hides on this type: no field of the editor at all ("Hidden for good"). */
	hidden?: string[];
}

/** The layout of each type (docs/architecture/content-editor-layout.md). */
const LAYOUTS: Record<string, Layout> = {
	'fmdb:inputText': {
		hidden: ['form', 'dirname', 'size'],
		settings: [
			['helpAndPresentation', ['helpText', 'title']],
			['valueAndInput', ['placeholder', 'defaultValue', 'mask', 'pattern', 'autocomplete', 'spellcheck', 'list']],
			['constraints', ['minLength', 'maxLength']],
			['behaviour', ['readonly', 'disabled', 'autofocus']],
			['validationMessages', TEXT_MESSAGES]
		]
	},
	'fmdb:inputEmail': {
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['placeholder', 'defaultValue', 'multiple', 'pattern', 'autocomplete', 'list']],
			['constraints', ['minLength', 'maxLength']],
			['validationMessages', TEXT_MESSAGES]
		]
	},
	'fmdb:textarea': {
		hidden: ['form', 'dirname', 'cols'],
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['placeholder', 'defaultValue', 'autocomplete', 'spellcheck', 'wrap', 'rows', 'resize']],
			['constraints', ['minLength', 'maxLength']],
			['behaviour', ['readonly', 'disabled', 'autofocus']],
			['validationMessages', TEXT_MESSAGES]
		]
	},
	'fmdb:inputNumber': {
		hidden: ['form'],
		settings: [
			['helpAndPresentation', ['helpText', 'title']],
			['valueAndInput', ['placeholder', 'defaultValue', 'step', 'list']],
			['constraints', ['minValue', 'maxValue']],
			['behaviour', ['readonly', 'disabled', 'autofocus']],
			['validationMessages', RANGE_MESSAGES]
		]
	},
	// A slider is drawn by its bounds: they stay in Content, beside Required.
	'fmdb:inputRange': {
		hidden: ['form'],
		content: ['required', 'minValue', 'maxValue'],
		settings: [
			['helpAndPresentation', ['helpText', 'minLabel', 'maxLabel', 'title']],
			['valueAndInput', ['defaultValue', 'step', 'list']],
			['behaviour', ['disabled', 'autofocus']],
			['validationMessages', ['msgValueMissing']]
		]
	},
	'fmdb:inputDate': {
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['defaultValue', 'step']],
			['constraints', ['minBoundMode', 'maxBoundMode']],
			...boundFieldSets('Date'),
			['validationMessages', RANGE_MESSAGES]
		]
	},
	'fmdb:inputDatetimeLocal': {
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['defaultValue', 'step']],
			['constraints', ['minBoundMode', 'maxBoundMode']],
			...boundFieldSets('Datetime'),
			['validationMessages', RANGE_MESSAGES]
		]
	},
	// A choice field is its options: the options origin stays in Content (its dynamic fieldsets follow it there).
	'fmdb:select': {
		content: ['required', 'optionsMode', 'multiple'],
		settings: [
			['helpAndPresentation', ['helpText', 'optionsEmptyLabel']],
			['valueAndInput', ['size']],
			['behaviour', ['disabled', 'autofocus']],
			['validationMessages', ['msgValueMissing']]
		]
	},
	'fmdb:radio': {
		content: ['required', 'optionsMode'],
		settings: [['helpAndPresentation', ['helpText']], ['validationMessages', ['msgValueMissing']]]
	},
	'fmdb:checkbox': {
		content: ['required', 'optionsMode'],
		settings: [['helpAndPresentation', ['helpText']], ['validationMessages', ['msgValueMissing']]]
	},
	// A file field is what it accepts and how many: both stay in Content.
	'fmdb:inputFile': {
		content: ['required', 'accept', 'multiple'],
		noFieldActions: true,
		settings: [['helpAndPresentation', ['helpText']], ['validationMessages', ['msgValueMissing']]]
	},
	'fmdb:inputColor': {
		settings: [
			['helpAndPresentation', ['helpText']],
			['valueAndInput', ['defaultValue']],
			['validationMessages', ['msgValueMissing']]
		]
	},
	// One property, no help text, no messages: nothing to move, no section.
	'fmdb:inputHidden': {content: ['value'], settings: []},
	// The optional types, laid out from their own module: what defines the control stays, what dresses it moves.
	'fmdbext:consent': {
		content: ['required', 'statement', 'termsTarget'],
		settings: [['helpAndPresentation', ['helpText', 'termsLinkLabel']], ['validationMessages', ['msgValueMissing']]]
	},
	'fmdbext:rating': {
		content: ['required', 'maxValue'],
		settings: [['helpAndPresentation', ['helpText', 'icon', 'minLabel', 'maxLabel']], ['validationMessages', ['msgValueMissing']]]
	},
	'fmdbext:scale': {
		content: ['required', 'minValue', 'maxValue'],
		settings: [
			['helpAndPresentation', ['helpText', 'minLabel', 'maxLabel']],
			['valueAndInput', ['step']],
			['validationMessages', ['msgValueMissing']]
		]
	},
	'fmdbext:switch': {
		settings: [
			['helpAndPresentation', ['helpText', 'displayMode', 'onLabel', 'offLabel']],
			['valueAndInput', ['defaultState']],
			['validationMessages', ['msgValueMissing']]
		]
	}
};

const hiddenInput: JahiaNode = {
	name: 'tracking',
	primaryNodeType: 'fmdb:inputHidden',
	properties: [{name: 'jcr:title', value: 'Tracking', language: 'en'}, {name: 'value', value: 'campaign-42'}]
};

/**
 * The editor of a field is laid out by Content Editor form overrides, not by the CND: Content keeps what a
 * contributor almost always fills, every other setting sits in the Field settings section, in fieldsets
 * shared by every type. The properties once behind a type's "advanced settings" switch belong to a supertype
 * of the type now and are spread over those fieldsets; the attributes a form never needs (form, dirname, size,
 * cols) are hidden in the CND and appear nowhere. A slider keeps the bounds that draw
 * it in Content; the date bound modes sit in Constraints with their dynamic fieldsets right after, shown
 * once their mode is chosen. A choice field keeps its options origin and the fieldsets of its origins in
 * Content, a file field what it accepts and how many; the hidden input has nothing to move. The optional
 * types of formidable-extended-inputs follow from their own module, the shared labels resolved through its
 * dependency on formidable-elements.
 * The validation messages join the section; the field actions switch stays at the end of Content, a
 * capability of the field rather than a setting, next to the children block its list makes the editor
 * show — a block the engine hides, one node being nothing to order. Read through the editor form
 * the Content Editor builds, on a field with its settings set and on a plain one: the layout owes nothing
 * to the values.
 */
describe('Form fields - 225 The field editor layout', () => {
	useFormidableSite();

	const formName = 'editor-layout';
	const fieldPath = (name: string) => `${CONTENT_PATH}/${formName}/fields/${name}`;

	before(() => {
		createFormNode(formName, 'Editor Layout Text Family', [
			// One with a setting set, one plain: the layout must be the same.
			getInputTextNode({name: 'maskedText', title: 'Masked text', mask: 'AA-9999'}),
			getInputTextNode({name: 'plainText', title: 'Plain text'}),
			getInputEmailNode({name: 'email', title: 'Email'}),
			getTextareaNode({name: 'wrappedTextarea', title: 'Wrapped textarea', wrap: 'hard'}),
			getTextareaNode({name: 'plainTextarea', title: 'Plain textarea'}),
			getInputNumberNode({name: 'quantity', title: 'Quantity'}),
			getInputRangeNode({name: 'satisfaction', title: 'Satisfaction'}),
			// A fixed maximum puts the fixedMaxDate mixin on the node: its bound fieldset is the activated one.
			getInputDateNode({name: 'booking', title: 'Booking', max: '2100-06-30T00:00:00.000'}),
			getInputDateNode({name: 'plainDate', title: 'Plain date'}),
			getInputDatetimeLocalNode({name: 'appointment', title: 'Appointment', minBoundMode: 'today'}),
			// A container: its children are worth ordering, the editor's block must stay on it.
			getFieldsetNode({name: 'group', title: 'Group', children: [getInputTextNode({name: 'inGroup', title: 'In group'})]}),
			getSelectNode({name: 'country', title: 'Country', options: [{value: 'fr', label: 'France', selected: false}]}),
			getRadioNode({name: 'size', title: 'Size', choices: [{value: 's', label: 'Small', selected: false}]}),
			getCheckboxNode({name: 'topics', title: 'Topics', choices: [{value: 'news', label: 'News', selected: false}]}),
			getInputFileNode({name: 'attachment', title: 'Attachment'}),
			getInputColorNode({name: 'favourite', title: 'Favourite colour'}),
			hiddenInput,
			getConsentNode({name: 'terms', title: 'Terms', statement: 'I agree to the terms.'}),
			getRatingNode({name: 'stars', title: 'Stars'}),
			getScaleNode({name: 'nps', title: 'Recommendation'}),
			getSwitchNode({name: 'newsletter', title: 'Newsletter'})
		]);
	});

	const editFormAt = (path: string, what: string) => cy.apollo({query: EDIT_FORM, variables: {path}})
		.then((response: EditFormResponse) => {
			expect(response.errors, `GraphQL errors for the edit form of ${what}`).to.be.undefined;
			return cy.wrap(response.data?.forms?.editForm?.sections ?? []);
		});
	const editFormOf = (name: string) => editFormAt(fieldPath(name), name);

	const assertLayout = (name: string, type: string) => editFormOf(name).then(sections => {
		const layout = LAYOUTS[type];
		const content = sections.find(section => section.name === 'content');
		const main = content?.fieldSets.find(fieldSet => fieldSet.name === type);
		expect(main?.fields.map(field => field.name), `Content fields of ${name}`)
			.to.deep.equal(layout.content ? ['jcr:title', 'ce:systemName', ...layout.content] : CONTENT_FIELDS);

		// The attributes the CND hides on the type are no field of the editor, in no section: nothing to tuck away.
		// Per type: the select's `size` is another property, the number of visible rows, and is a field.
		const everyField = sections.flatMap(section => section.fieldSets.flatMap(fieldSet => fieldSet.fields.map(field => field.name)));
		(layout.hidden ?? []).forEach(attribute => expect(everyField, `${attribute} absent from the editor of ${name}`).not.to.include(attribute));

		const settings = sections.find(section => section.name === SETTINGS_SECTION);
		if (layout.settings.length === 0) {
			expect(settings, `no Field settings section on ${name}`).to.be.undefined;
		} else {
			expect(settings, `Field settings section of ${name}`).not.to.be.undefined;
			expect(settings?.fieldSets.map(fieldSet => fieldSet.name), `fieldsets of ${name}`)
				.to.deep.equal(layout.settings.map(([fieldSet]) => fieldSet));
			layout.settings.forEach(([fieldSetName, fields]) => {
				const fieldSet = settings?.fieldSets.find(candidate => candidate.name === fieldSetName);
				const names = fieldSet?.fields.map(field => field.name).filter(field => field !== SAMPLE_FIELD);
				expect(names, `fields of ${fieldSetName} on ${name}`).to.deep.equal(fields);
				expect(fieldSet?.visible, `${fieldSetName} visible on ${name}`).to.be.true;
				if (fieldSetName === 'validationMessages') {
					// Said once under the title ("a message left empty keeps the browser's"), not under each message.
					expect(fieldSet?.description, `the Validation messages note on ${name}`).to.match(VALIDATION_MESSAGES_NOTE);
				}
			});
		}

		// The field actions switch stays in Content, after the type's own fieldset, with its enable switch —
		// on the fields with a value; a file field is offered none.
		const contentNames = content?.fieldSets.filter(fieldSet => fieldSet.visible).map(fieldSet => fieldSet.name) ?? [];
		const actions = content?.fieldSets.find(fieldSet => fieldSet.name === FIELD_ACTIONS_SWITCH);
		if (layout.noFieldActions) {
			expect(actions, `no field actions switch on ${name}`).to.be.undefined;
		} else {
			expect(contentNames.indexOf(FIELD_ACTIONS_SWITCH), `field actions switch after the ${type} fieldset on ${name}`)
				.to.be.greaterThan(contentNames.indexOf(type));
			expect(actions?.dynamic, `field actions switch dynamic on ${name}`).to.be.true;
			expect(actions?.hasEnableSwitch, `field actions enable switch on ${name}`).to.be.true;
			expect(settings?.fieldSets.map(fieldSet => fieldSet.name) ?? [], `no switch in Field settings on ${name}`).not.to.include(FIELD_ACTIONS_SWITCH);
		}

		// The Validation messages section is gone, and so is the editor's children block.
		expect(sections.map(section => section.name), `sections of ${name}`).not.to.include('validationMessages');
		// The children block is hidden on every field: by the engine override keyed on the field actions mixin on the
		// fields with a value, by its own override on the file field, which carries no field actions and has no children.
		expect(sections.map(section => section.name), `children block hidden on ${name}`).not.to.include(LIST_ORDERING_SECTION);
	});

	it('lays out a text input the same way, masked or plain', () => {
		assertLayout('maskedText', 'fmdb:inputText');
		assertLayout('plainText', 'fmdb:inputText');
	});

	it('lays out an email input', () => {
		assertLayout('email', 'fmdb:inputEmail');
	});

	it('lays out a textarea the same way, wrapped or plain', () => {
		assertLayout('wrappedTextarea', 'fmdb:textarea');
		assertLayout('plainTextarea', 'fmdb:textarea');
	});

	it('lays out a number input, and a slider with its bounds in Content', () => {
		assertLayout('quantity', 'fmdb:inputNumber');
		assertLayout('satisfaction', 'fmdb:inputRange');
	});

	it('lays out the date inputs, the bound fieldset of a chosen mode activated in Constraints', () => {
		assertLayout('booking', 'fmdb:inputDate');
		assertLayout('plainDate', 'fmdb:inputDate');
		assertLayout('appointment', 'fmdb:inputDatetimeLocal');

		editFormOf('booking').then(sections => {
			const settings = sections.find(section => section.name === SETTINGS_SECTION);
			const activated = settings?.fieldSets.filter(fieldSet => fieldSet.dynamic && fieldSet.activated).map(fieldSet => fieldSet.name);
			expect(activated, 'the one bound fieldset activated on booking').to.deep.equal(['fmdbmix:fixedMaxDate']);
		});
	});

	it('keeps the editor\'s children block on a container: its children are the fields it orders', () => {
		editFormOf('group').then(sections => {
			expect(sections.map(section => section.name), 'sections of the fieldset container').to.include(LIST_ORDERING_SECTION);
		});
	});

	it('lays out the form: the steps bar switch with the Previous/Next labels in Buttons, no Multi-step section, no children block', () => {
		editFormAt(`${CONTENT_PATH}/${formName}`, 'the form').then(sections => {
			const names = sections.map(section => section.name);
			// One checkbox had a section to itself; it governs the step labels, so it sits with them (#150).
			expect(names, 'no Multi-step section on the form').not.to.include('multistep');
			// The form's children are its field and action lists: nothing a contributor orders.
			expect(names, 'children block hidden on the form').not.to.include(LIST_ORDERING_SECTION);
			const navigation = sections.find(section => section.name === 'buttons')?.fieldSets.find(fieldSet => fieldSet.name === 'stepNavigation');
			expect(navigation?.fields.map(field => field.name), 'Step navigation fieldset of the form').to.deep.equal(STEP_NAVIGATION);
			expect(navigation?.visible, 'Step navigation visible on the form').to.be.true;
		});
	});
	it('lays out the choice fields, their options origin kept in Content with its fieldsets', () => {
		assertLayout('country', 'fmdb:select');
		assertLayout('size', 'fmdb:radio');
		assertLayout('topics', 'fmdb:checkbox');

		editFormOf('country').then(sections => {
			const content = sections.find(section => section.name === 'content');
			const options = content?.fieldSets.filter(fieldSet => fieldSet.name.startsWith('fmdbmix:') && fieldSet.name.endsWith('Options'));
			expect(options?.map(fieldSet => fieldSet.name), 'the options fieldsets stay in Content on country')
				.to.have.members(['fmdbmix:manualOptions', 'fmdbmix:sourcedOptions', 'fmdbmix:categoryOptions', 'fmdbmix:contentOptions']);
		});
	});

	it('lays out a file, a colour and a hidden input', () => {
		assertLayout('attachment', 'fmdb:inputFile');
		assertLayout('favourite', 'fmdb:inputColor');
		assertLayout('tracking', 'fmdb:inputHidden');
	});

	it('lays out the extended inputs from their own module', () => {
		assertLayout('terms', 'fmdbext:consent');
		assertLayout('stars', 'fmdbext:rating');
		assertLayout('nps', 'fmdbext:scale');
		assertLayout('newsletter', 'fmdbext:switch');
	});
});

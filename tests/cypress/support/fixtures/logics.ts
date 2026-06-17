import gql from 'graphql-tag';
import {createFormNode} from './forms';
import {getFieldsetNode} from './fieldset';
import {getCheckboxNode} from './inputCheckbox';
import {getInputDateNode} from './inputDate';
import {getInputTextNode} from './inputText';
import {getSelectNode} from './select';
import {getTextareaNode} from './textarea';
import type {JahiaNode} from './types';
import {CONTENT_PATH} from '../constants';

const GET_LOGIC_NODE = gql`
	query getConditionalLogicNode($path: String!, $language: String!) {
		jcr(workspace: EDIT) {
			nodeByPath(path: $path) {
				uuid
				name
				properties(names: ["logics"], language: $language) {
					name
					values
				}
				descendant(relPath: "logicsSrc") {
					name
					primaryNodeType {
						name
					}
					children {
						nodes {
							name
							primaryNodeType {
								name
							}
							property(name: "logicNodeSource") {
								refNode {
									uuid
									name
									path
								}
							}
						}
					}
				}
			}
		}
	}
`;

const IMPORT_CONTENT = gql`
	mutation importConditionalLogicFixture($file: String!, $parentPathOrId: String!, $rootBehaviour: Int!) {
		jcr {
			importContent(file: $file, parentPathOrId: $parentPathOrId, rootBehaviour: $rootBehaviour)
		}
	}
`;

const IMPORT_ROOT_BEHAVIOUR_CREATE_NEW_CHILDREN = 2;
const LEGACY_IMPORT_SOURCE_NODE_ID = '11111111-1111-1111-1111-111111111111';
const LEGACY_DUPLICATE_IMPORT_SOURCE_NODE_ID = '22222222-2222-2222-2222-222222222222';

export interface ConditionalLogicRule {
	logicId: string;
	sourceNodeId: string;
	sourceFieldName: string;
	sourceFieldType: string;
	operator: string;
	value?: string;
	values?: string[];
}

export interface ConditionalLogicNode {
	uuid: string;
	name: string;
	properties?: Array<{
		name: string;
		values?: string[] | null;
	}> | null;
	descendant?: {
		name: string;
		primaryNodeType?: {name?: string | null} | null;
		children?: {
			nodes?: Array<{
				name: string;
				primaryNodeType?: {name?: string | null} | null;
				property?: {
					refNode?: {
						uuid: string;
						name: string;
						path: string;
					} | null;
				} | null;
			}> | null;
		} | null;
	} | null;
}

export interface ConditionalLogicFormInfo {
	formName: string;
	formPath: string;
	rolePath: string;
	targetPath: string;
}

export interface DuplicateSourceNameConditionalLogicFormInfo {
	formName: string;
	formPath: string;
	firstSourcePath: string;
	secondSourcePath: string;
	targetPath: string;
}

export interface ConditionalLogicNoMatchingSourceFormInfo {
	formName: string;
	formPath: string;
	targetPath: string;
}

export interface ImportedConditionalLogicFormInfo {
	formName: string;
	formPath: string;
	rolePath: string;
	targetPath: string;
	legacySourceNodeId: string;
}

export interface ImportedDuplicateSourceNameConditionalLogicFormInfo {
	formName: string;
	formPath: string;
	firstSourcePath: string;
	secondSourcePath: string;
	targetPath: string;
	legacySourceNodeId: string;
}

export const CONDITIONAL_LOGIC_FORM_ELEMENTS: JahiaNode[] = [
	getSelectNode({
		name: 'role',
		title: 'role',
		options: [
			{value: 'admin', label: 'admin', selected: false},
			{value: 'editor', label: 'editor', selected: false},
			{value: 'viewer', label: 'viewer', selected: false}
		]
	}),
	getCheckboxNode({
		name: 'accept-terms',
		title: 'accept-terms',
		choices: [{value: 'accepted', label: 'accepted', selected: false}]
	}),
	getInputDateNode({
		name: 'start-date',
		title: 'start-date'
	}),
	getTextareaNode({
		name: 'notes',
		title: 'notes',
		defaultValue: 'notes'
	}),
	getInputTextNode({
		name: 'nickname',
		title: 'nickname',
		placeholder: 'nickname'
	})
];

export const DUPLICATE_SOURCE_NAME_CONDITIONAL_LOGIC_FORM_ELEMENTS: JahiaNode[] = [
	getFieldsetNode({
		name: 'termination',
		title: 'termination',
		children: [
			getSelectNode({
				name: 'select-an-option',
				title: 'select-an-option',
				options: [
					{value: 'alpha', label: 'alpha', selected: false},
					{value: 'beta', label: 'beta', selected: false}
				]
			})
		]
	}),
	getFieldsetNode({
		name: 'reduction',
		title: 'reduction',
		children: [
			getSelectNode({
				name: 'select-an-option',
				title: 'select-an-option',
				options: [
					{value: 'manager', label: 'manager', selected: false},
					{value: 'staff', label: 'staff', selected: false}
				]
			})
		]
	}),
	getInputTextNode({
		name: 'nickname',
		title: 'nickname',
		placeholder: 'nickname'
	})
];

export const CONDITIONAL_LOGIC_NO_MATCHING_SOURCE_FORM_ELEMENTS: JahiaNode[] = [
	getCheckboxNode({
		name: 'accept-terms',
		title: 'accept-terms',
		choices: [{value: 'accepted', label: 'accepted', selected: false}]
	}),
	getInputDateNode({
		name: 'start-date',
		title: 'start-date'
	}),
	getInputTextNode({
		name: 'nickname',
		title: 'nickname',
		placeholder: 'nickname'
	})
];

export const createConditionalLogicForm = (suffix: string): Cypress.Chainable<ConditionalLogicFormInfo> => {
	const formName = `conditional-logic-${suffix}`;

	return createFormNode(formName, formName, CONDITIONAL_LOGIC_FORM_ELEMENTS).then(() => ({
		formName,
		formPath: `${CONTENT_PATH}/${formName}`,
		rolePath: `${CONTENT_PATH}/${formName}/fields/role`,
		targetPath: `${CONTENT_PATH}/${formName}/fields/nickname`
	}));
};

export const createDuplicateSourceNameConditionalLogicForm = (
	suffix: string
): Cypress.Chainable<DuplicateSourceNameConditionalLogicFormInfo> => {
	const formName = `conditional-logic-duplicates-${suffix}`;

	return createFormNode(formName, formName, DUPLICATE_SOURCE_NAME_CONDITIONAL_LOGIC_FORM_ELEMENTS).then(() => ({
		formName,
		formPath: `${CONTENT_PATH}/${formName}`,
		firstSourcePath: `${CONTENT_PATH}/${formName}/fields/termination/select-an-option`,
		secondSourcePath: `${CONTENT_PATH}/${formName}/fields/reduction/select-an-option`,
		targetPath: `${CONTENT_PATH}/${formName}/fields/nickname`
	}));
};

export const createConditionalLogicNoMatchingSourceForm = (
	suffix: string
): Cypress.Chainable<ConditionalLogicNoMatchingSourceFormInfo> => {
	const formName = `conditional-logic-no-match-${suffix}`;

	return createFormNode(formName, formName, CONDITIONAL_LOGIC_NO_MATCHING_SOURCE_FORM_ELEMENTS).then(() => ({
		formName,
		formPath: `${CONTENT_PATH}/${formName}`,
		targetPath: `${CONTENT_PATH}/${formName}/fields/nickname`
	}));
};

export const buildSelectOptionsPropertyValues = (
	options: Array<{value: string; label: string; selected?: boolean}>
): string[] => options.map(option => JSON.stringify({
	value: option.value,
	label: option.label,
	selected: option.selected ?? false
}));

export const findConditionalLogicNode = (targetPath: string): Cypress.Chainable<ConditionalLogicNode | null> => cy.apollo({
	query: GET_LOGIC_NODE,
	variables: {
		path: targetPath,
		language: 'en'
	}
}).then((response: {data?: {jcr?: {nodeByPath?: ConditionalLogicNode | null} | null}}) => response.data?.jcr?.nodeByPath ?? null);

export const getConditionalLogicNode = (targetPath: string): Cypress.Chainable<ConditionalLogicNode> => findConditionalLogicNode(targetPath).then(node => {
	if (!node) {
		throw new Error(`Unable to load node at '${targetPath}'`);
	}

	return node;
});

export const parseStoredLogicRule = (rawValue: string): ConditionalLogicRule => JSON.parse(rawValue) as ConditionalLogicRule;

const importXmlContentFixture = (
	fixturePath: string,
	importedFileName: string,
	replacements: Record<string, string>
): Cypress.Chainable<void> => cy.fixture(fixturePath, 'utf8').then(rawContent => {
	let xmlContent = rawContent;
	Object.entries(replacements).forEach(([placeholder, value]) => {
		xmlContent = xmlContent.replaceAll(placeholder, value);
	});

	const file = new File([xmlContent], importedFileName, {type: 'application/xml'});

	return cy.apollo({
		mutation: IMPORT_CONTENT,
		variables: {
			file,
			parentPathOrId: CONTENT_PATH,
			rootBehaviour: IMPORT_ROOT_BEHAVIOUR_CREATE_NEW_CHILDREN
		}
	}).then((response: {data?: {jcr?: {importContent?: boolean | null} | null}}) => {
		expect(response.data?.jcr?.importContent).to.equal(true);
		return undefined;
	});
});

export const importConditionalLogicForm = (suffix: string): Cypress.Chainable<ImportedConditionalLogicFormInfo> => {
	const formName = `conditional-logic-import-${suffix}`;

	return importXmlContentFixture(
		'imports/conditional-logic-form.xml',
		`${formName}.xml`,
		{
			'FORM_NAME_PLACEHOLDER': formName,
			'FORM_TITLE_PLACEHOLDER': formName
		}
	).then(() => ({
		formName,
		formPath: `${CONTENT_PATH}/${formName}`,
		rolePath: `${CONTENT_PATH}/${formName}/fields/role`,
		targetPath: `${CONTENT_PATH}/${formName}/fields/nickname`,
		legacySourceNodeId: LEGACY_IMPORT_SOURCE_NODE_ID
	}));
};

export const importDuplicateSourceNameConditionalLogicForm = (
	suffix: string
): Cypress.Chainable<ImportedDuplicateSourceNameConditionalLogicFormInfo> => {
	const formName = `conditional-logic-import-duplicates-${suffix}`;

	return importXmlContentFixture(
		'imports/conditional-logic-duplicates.xml',
		`${formName}.xml`,
		{
			'DUPLICATE_FORM_NAME_PLACEHOLDER': formName,
			'DUPLICATE_FORM_TITLE_PLACEHOLDER': formName
		}
	).then(() => ({
		formName,
		formPath: `${CONTENT_PATH}/${formName}`,
		firstSourcePath: `${CONTENT_PATH}/${formName}/fields/termination/select-an-option`,
		secondSourcePath: `${CONTENT_PATH}/${formName}/fields/reduction/select-an-option`,
		targetPath: `${CONTENT_PATH}/${formName}/fields/nickname`,
		legacySourceNodeId: LEGACY_DUPLICATE_IMPORT_SOURCE_NODE_ID
	}));
};

import {JahiaNode} from './types';

export type ChoiceFieldType = 'fmdb:select' | 'fmdb:radio' | 'fmdb:checkbox';

export interface SourcedChoiceFieldData {
	primaryNodeType: ChoiceFieldType;
	name: string;
	title: string;
	sourceKey: string;
	required?: boolean;
	multiple?: boolean;
}

export interface CategoryChoiceFieldData {
	primaryNodeType: ChoiceFieldType;
	name: string;
	title: string;
	rootCategoryUuid: string;
	required?: boolean;
	multiple?: boolean;
}

/**
 * A choice field whose options come from an admin-declared options source
 * (fmdbmix:sourcedOptions), resolved at render and submit time.
 */
export function getSourcedChoiceFieldNode(data: SourcedChoiceFieldData): JahiaNode {
	const properties: JahiaNode['properties'] = [
		{name: 'jcr:title', value: data.title, language: 'en'},
		{name: 'fmdb:optionsMode', value: 'sourced'},
		{name: 'fmdb:optionsSourceKey', value: data.sourceKey}
	];

	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.multiple !== undefined) properties.push({name: 'multiple', value: String(data.multiple), type: 'BOOLEAN'});

	return {
		name: data.name,
		primaryNodeType: data.primaryNodeType,
		mixins: ['fmdbmix:sourcedOptions'],
		properties
	};
}

/**
 * A choice field whose options are the direct children of a picked category
 * (fmdbmix:categoryOptions): value = category name, label = localized title.
 */
export function getCategoryChoiceFieldNode(data: CategoryChoiceFieldData): JahiaNode {
	const properties: JahiaNode['properties'] = [
		{name: 'jcr:title', value: data.title, language: 'en'},
		{name: 'fmdb:optionsMode', value: 'category'},
		{name: 'fmdb:optionsRootCategory', value: data.rootCategoryUuid, type: 'WEAKREFERENCE'}
	];

	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.multiple !== undefined) properties.push({name: 'multiple', value: String(data.multiple), type: 'BOOLEAN'});

	return {
		name: data.name,
		primaryNodeType: data.primaryNodeType,
		mixins: ['fmdbmix:categoryOptions'],
		properties
	};
}

export function getCategoryNode(name: string, titleEn: string, titleFr: string): JahiaNode {
	return {
		name,
		primaryNodeType: 'jnt:category',
		properties: [
			{name: 'jcr:title', value: titleEn, language: 'en'},
			{name: 'jcr:title', value: titleFr, language: 'fr'}
		]
	};
}

/**
 * Declares the options sources in the module OSGi configuration. The
 * configuration is instance-global: specs that change it mid-test must
 * restore their own declaration afterwards.
 */
export function setOptionsSourcesConfig(lines: string[]): Cypress.Chainable {
	return cy.runProvisioningScript({
		script: {
			fileContent: JSON.stringify([{
				editConfiguration: 'org.jahia.modules.formidable',
				properties: {optionsSources: lines.join('\n')}
			}]),
			type: 'application/json'
		}
	});
}

import {JahiaNode, NodeProperty, TextareaData} from './types';

export const TEXTAREA_COMPLETE: TextareaData = {
	name: 'projectSummary',
	title: 'Project summary',
	placeholder: 'Describe the project',
	defaultValue: 'Initial summary',
	required: true,
	minLength: 10,
	maxLength: 200,
	rows: 5,
	cols: 60,
	autocomplete: 'off',
	spellcheck: true,
	wrap: 'soft',
	resize: 'vertical'
};

export function getTextareaNode(data: TextareaData = TEXTAREA_COMPLETE): JahiaNode {
	const properties: NodeProperty[] = [];
	const mixins: string[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.placeholder) properties.push({name: 'placeholder', value: data.placeholder, language: 'en'});
	if (data.defaultValue) properties.push({name: 'defaultValue', value: data.defaultValue, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.minLength !== undefined) properties.push({name: 'minLength', value: String(data.minLength), type: 'LONG'});
	if (data.maxLength !== undefined) properties.push({name: 'maxLength', value: String(data.maxLength), type: 'LONG'});
	if (data.rows !== undefined) properties.push({name: 'rows', value: String(data.rows), type: 'LONG'});
	if (data.cols !== undefined) properties.push({name: 'cols', value: String(data.cols), type: 'LONG'});
	if (data.autocomplete) properties.push({name: 'autocomplete', value: data.autocomplete});
	if (data.spellcheck !== undefined) properties.push({name: 'spellcheck', value: String(data.spellcheck), type: 'BOOLEAN'});
	if (data.readonly !== undefined) properties.push({name: 'readonly', value: String(data.readonly), type: 'BOOLEAN'});
	if (data.autofocus !== undefined) properties.push({name: 'autofocus', value: String(data.autofocus), type: 'BOOLEAN'});
	if (data.disabled !== undefined) properties.push({name: 'disabled', value: String(data.disabled), type: 'BOOLEAN'});
	if (data.wrap) properties.push({name: 'wrap', value: data.wrap});
	if (data.resize) properties.push({name: 'resize', value: data.resize});

	if (
		data.cols !== undefined ||
		data.spellcheck !== undefined ||
		data.readonly !== undefined ||
		data.autofocus !== undefined ||
		data.disabled !== undefined ||
		data.wrap !== undefined
	) {
		mixins.push('fmdbmix:advancedTextareaSettings');
	}

	return {
		name: data.name || 'textarea',
		primaryNodeType: 'fmdb:textarea',
		properties,
		mixins
	};
}

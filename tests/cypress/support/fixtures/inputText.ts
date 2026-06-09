import {InputTextData, JahiaNode, NodeProperty} from './types';

export const INPUT_TEXT_SIMPLE: InputTextData = {
	name: 'fullName',
	title: 'Full name',
	placeholder: 'Enter your full name'
};

export const INPUT_TEXT_COMPLETE: InputTextData = {
	name: 'employeeCode',
	title: 'Employee code',
	placeholder: 'AB-1234',
	required: true,
	defaultValue: 'AB-1234',
	minLength: 7,
	maxLength: 7,
	pattern: '[A-Z]{2}-[0-9]{4}',
	autocomplete: 'off',
	list: ['AB-1234', 'CD-5678']
};

export const INPUT_TEXT_REQUIRED: InputTextData = {
	name: 'firstStepName',
	title: 'Step one name',
	placeholder: 'Required on step one',
	required: true
};

export const INPUT_TEXT_SECOND_STEP: InputTextData = {
	name: 'secondStepComment',
	title: 'Step two comment',
	placeholder: 'Optional comment'
};

export function getInputTextNode(data: InputTextData = INPUT_TEXT_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];
	const mixins: string[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.placeholder) properties.push({name: 'placeholder', value: data.placeholder, language: 'en'});
	if (data.defaultValue) properties.push({name: 'defaultValue', value: data.defaultValue, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.minLength !== undefined) properties.push({name: 'minLength', value: String(data.minLength), type: 'LONG'});
	if (data.maxLength !== undefined) properties.push({name: 'maxLength', value: String(data.maxLength), type: 'LONG'});
	if (data.pattern) properties.push({name: 'pattern', value: data.pattern});
	if (data.autocomplete) properties.push({name: 'autocomplete', value: data.autocomplete});
	if (data.list && data.list.length > 0) properties.push({name: 'list', values: data.list, language: 'en'});

	if (data.pattern) {
		mixins.push('fmdbmix:advancedInputTextSettings');
	}

	return {
		name: data.name || 'textInput',
		primaryNodeType: 'fmdb:inputText',
		properties,
		mixins
	};
}

import {InputTextData, JahiaNode, NodeProperty} from './types';

export const INPUT_TEXT_SIMPLE: InputTextData = {
	name: 'fullName',
	title: 'Full name',
	placeholder: 'Enter your full name'
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

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.placeholder) properties.push({name: 'placeholder', value: data.placeholder, language: 'en'});
	if (data.defaultValue) properties.push({name: 'defaultValue', value: data.defaultValue, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.minLength !== undefined) properties.push({name: 'minLength', value: String(data.minLength), type: 'LONG'});
	if (data.maxLength !== undefined) properties.push({name: 'maxLength', value: String(data.maxLength), type: 'LONG'});
	if (data.pattern) properties.push({name: 'pattern', value: data.pattern});
	if (data.autocomplete) properties.push({name: 'autocomplete', value: data.autocomplete});

	return {
		name: data.name || 'textInput',
		primaryNodeType: 'fmdb:inputText',
		properties
	};
}

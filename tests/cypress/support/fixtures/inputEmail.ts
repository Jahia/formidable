import {InputEmailData, JahiaNode, NodeProperty} from './types';

export const INPUT_EMAIL_SIMPLE: InputEmailData = {
	name: 'simpleEmail',
	title: 'Email Address',
	defaultValue: 'default@example.com'
};

export const INPUT_EMAIL_COMPLETE: InputEmailData = {
	name: 'completeEmail',
	title: 'Contact Email',
	defaultValue: 'test@example.com',
	required: true,
	placeholder: 'Enter your email address',
	pattern: '[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$',
	autocomplete: 'email',
	multiple: false,
	minLength: 5,
	maxLength: 100
};

export const INPUT_EMAIL_MULTIPLE: InputEmailData = {
	name: 'multipleEmail',
	title: 'Multiple Email Addresses',
	multiple: true,
	placeholder: 'Enter multiple emails separated by commas'
};

export const INPUT_EMAIL_WITH_LIST: InputEmailData = {
	name: 'emailWithList',
	title: 'Email with Suggestions',
	list: ['admin@example.com', 'user@example.com', 'contact@example.com']
};

export function getInputEmailNode(data: InputEmailData = INPUT_EMAIL_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.defaultValue) properties.push({name: 'defaultValue', value: data.defaultValue, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.placeholder) properties.push({name: 'placeholder', value: data.placeholder, language: 'en'});
	if (data.pattern) properties.push({name: 'pattern', value: data.pattern, type: 'STRING'});
	if (data.autocomplete) properties.push({name: 'autocomplete', value: data.autocomplete, type: 'STRING'});
	if (data.multiple !== undefined) properties.push({name: 'multiple', value: String(data.multiple), type: 'BOOLEAN'});
	if (data.minLength !== undefined) properties.push({name: 'minLength', value: String(data.minLength), type: 'LONG'});
	if (data.maxLength !== undefined) properties.push({name: 'maxLength', value: String(data.maxLength), type: 'LONG'});
	if (data.list && Array.isArray(data.list)) properties.push({name: 'list', values: data.list, language: 'en'});
	return {name: data.name || 'emailInput', primaryNodeType: 'fmdb:inputEmail', properties};
}

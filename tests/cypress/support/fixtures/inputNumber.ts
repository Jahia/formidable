import {InputNumberData, JahiaNode, NodeProperty} from './types';

export const INPUT_NUMBER_SIMPLE: InputNumberData = {
	name: 'simpleNumber',
	title: 'Quantity'
};

export const INPUT_NUMBER_COMPLETE: InputNumberData = {
	name: 'completeNumber',
	title: 'Satisfaction score',
	helpText: 'Pick a value between 1 and 10',
	placeholder: 'e.g. 7.5',
	defaultValue: 5,
	minValue: 1,
	maxValue: 10,
	step: 0.5,
	required: true
};

export function getInputNumberNode(data: InputNumberData = INPUT_NUMBER_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.placeholder) properties.push({name: 'placeholder', value: data.placeholder, language: 'en'});
	if (data.defaultValue !== undefined) properties.push({name: 'defaultValue', value: String(data.defaultValue), type: 'DOUBLE'});
	if (data.minValue !== undefined) properties.push({name: 'minValue', value: String(data.minValue), type: 'DOUBLE'});
	if (data.maxValue !== undefined) properties.push({name: 'maxValue', value: String(data.maxValue), type: 'DOUBLE'});
	if (data.step !== undefined) properties.push({name: 'step', value: String(data.step), type: 'DOUBLE'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});

	return {
		name: data.name || 'numberInput',
		primaryNodeType: 'fmdb:inputNumber',
		properties
	};
}

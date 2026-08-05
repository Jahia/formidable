import {InputRangeData, JahiaNode, NodeProperty} from './types';

export const INPUT_RANGE_SIMPLE: InputRangeData = {
	name: 'simpleRange',
	title: 'Volume'
};

export const INPUT_RANGE_COMPLETE: InputRangeData = {
	name: 'completeRange',
	title: 'Likelihood to recommend',
	helpText: 'Slide to the value that matches your feeling',
	minValue: 0,
	maxValue: 10,
	step: 1,
	minLabel: 'Not at all likely',
	maxLabel: 'Extremely likely',
	list: ['0', '5', '10'],
	required: true
};

export function getInputRangeNode(data: InputRangeData = INPUT_RANGE_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.defaultValue !== undefined) properties.push({name: 'defaultValue', value: String(data.defaultValue), type: 'DOUBLE'});
	if (data.minValue !== undefined) properties.push({name: 'minValue', value: String(data.minValue), type: 'DOUBLE'});
	if (data.maxValue !== undefined) properties.push({name: 'maxValue', value: String(data.maxValue), type: 'DOUBLE'});
	if (data.step !== undefined) properties.push({name: 'step', value: String(data.step), type: 'DOUBLE'});
	if (data.minLabel) properties.push({name: 'minLabel', value: data.minLabel, language: 'en'});
	if (data.maxLabel) properties.push({name: 'maxLabel', value: data.maxLabel, language: 'en'});
	// The list property is not i18n (tick-mark values are language-independent)
	if (data.list && data.list.length > 0) properties.push({name: 'list', values: data.list});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});

	return {
		name: data.name || 'rangeInput',
		primaryNodeType: 'fmdb:inputRange',
		properties
	};
}

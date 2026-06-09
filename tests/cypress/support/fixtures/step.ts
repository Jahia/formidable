import {JahiaNode, NodeProperty, StepData} from './types';

export function getStepNode(data: StepData): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.label) properties.push({name: 'label', value: data.label, language: 'en'});
	if (data.intro) properties.push({name: 'intro', value: data.intro, language: 'en'});

	return {
		name: data.name || 'step',
		primaryNodeType: 'fmdb:step',
		properties,
		children: data.children || []
	};
}

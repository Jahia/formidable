import {InputButtonData, JahiaNode, NodeProperty} from './types';

export const INPUT_BUTTON_SIMPLE: InputButtonData = {
	name: 'simpleButton',
	title: 'Click Me'
};

export const INPUT_BUTTON_COMPLETE: InputButtonData = {
	name: 'completeButton',
	title: 'Submit Form',
	buttonType: 'submit',
	variant: 'primary'
};

export function getInputButtonNode(data: InputButtonData = INPUT_BUTTON_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) {
		properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	}

	if (data.buttonType) {
		properties.push({name: 'buttonType', value: data.buttonType});
	}

	if (data.variant) {
		properties.push({name: 'variant', value: data.variant});
	}

	return {
		name: data.name || 'buttonInput',
		primaryNodeType: 'fmdb:inputButton',
		properties
	};
}


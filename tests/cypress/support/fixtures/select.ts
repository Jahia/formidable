import {JahiaNode, NodeProperty, SelectData} from './types';

export const SELECT_SINGLE: SelectData = {
	name: 'department',
	title: 'Department',
	required: true,
	options: [
		// Empty first option (no value) so the field starts empty instead of
		// defaulting to the first valid value. Treated as empty by required validation.
		{value: '', label: 'Please select', selected: false},
		{value: 'engineering', label: 'Engineering', selected: false},
		{value: 'sales', label: 'Sales', selected: false},
		{value: 'support', label: 'Support', selected: false}
	]
};

export const SELECT_MULTIPLE: SelectData = {
	name: 'regions',
	title: 'Regions',
	multiple: true,
	size: 4,
	options: [
		{value: 'emea', label: 'EMEA', selected: true},
		{value: 'na', label: 'North America', selected: false},
		{value: 'latam', label: 'LATAM', selected: true},
		{value: 'apac', label: 'APAC', selected: false}
	]
};

export const SELECT_DISABLED: SelectData = {
	name: 'archivedStatus',
	title: 'Archived status',
	disabled: true,
	options: [
		{value: 'closed', label: 'Closed', selected: true},
		{value: 'open', label: 'Open', selected: false}
	]
};

export function getSelectNode(data: SelectData = SELECT_SINGLE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.multiple !== undefined) properties.push({name: 'multiple', value: String(data.multiple), type: 'BOOLEAN'});
	if (data.size !== undefined) properties.push({name: 'size', value: String(data.size), type: 'LONG'});
	if (data.disabled !== undefined) properties.push({name: 'disabled', value: String(data.disabled), type: 'BOOLEAN'});
	if (data.autofocus !== undefined) properties.push({name: 'autofocus', value: String(data.autofocus), type: 'BOOLEAN'});
	properties.push({
		name: 'options',
		values: data.options.map(option => JSON.stringify({
			value: option.value,
			label: option.label,
			selected: option.selected ?? false
		})),
		language: 'en'
	});

	return {
		name: data.name || 'select',
		primaryNodeType: 'fmdb:select',
		properties
	};
}

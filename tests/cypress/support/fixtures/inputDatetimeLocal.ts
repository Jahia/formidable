import {InputDatetimeLocalData, JahiaNode, NodeProperty} from './types';
import {pushBoundModeProperties} from './dateBounds';

export const INPUT_DATETIME_LOCAL_SIMPLE: InputDatetimeLocalData = {
	name: 'simpleDatetime',
	title: 'Select Date and Time'
};
export const INPUT_DATETIME_LOCAL_COMPLETE: InputDatetimeLocalData = {
	name: 'completeDatetime',
	title: 'Appointment',
	defaultValue: '1990-01-02T10:58:00.000',
	required: true,
	min: '1900-01-01T09:38:00.000',
	max: '2026-12-31T11:18:00.000',
	step: 1
};

export function getInputDatetimeLocalNode(data: InputDatetimeLocalData = INPUT_DATETIME_LOCAL_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];
	const mixins: string[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.defaultValue) properties.push({name: 'defaultValue', value: data.defaultValue, type: 'DATE'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	pushBoundModeProperties(properties, mixins, data, 'Datetime');
	if (data.step !== undefined) properties.push({name: 'step', value: String(data.step), type: 'LONG'});
	return {name: data.name || 'datetimeLocalInput', primaryNodeType: 'fmdb:inputDatetimeLocal', properties, mixins};
}

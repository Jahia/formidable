import {InputDatetimeLocalData, JahiaNode, NodeProperty} from './types';

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
	// A fixed value implies the 'date' mode, whose mixin carries the property;
	// a fixed value is only written when that mode ends up selected.
	const minMode = data.minBoundMode ?? (data.min ? 'date' : undefined);
	const maxMode = data.maxBoundMode ?? (data.max ? 'date' : undefined);
	if (minMode) properties.push({name: 'fmdb:minBoundMode', value: minMode});
	if (maxMode) properties.push({name: 'fmdb:maxBoundMode', value: maxMode});
	if (minMode === 'date') mixins.push('fmdbmix:fixedMinDatetime');
	if (maxMode === 'date') mixins.push('fmdbmix:fixedMaxDatetime');
	if (minMode === 'date' && data.min) properties.push({name: 'min', value: data.min, type: 'DATE'});
	if (maxMode === 'date' && data.max) properties.push({name: 'max', value: data.max, type: 'DATE'});
	if (data.step !== undefined) properties.push({name: 'step', value: String(data.step), type: 'LONG'});
	return {name: data.name || 'datetimeLocalInput', primaryNodeType: 'fmdb:inputDatetimeLocal', properties, mixins};
}

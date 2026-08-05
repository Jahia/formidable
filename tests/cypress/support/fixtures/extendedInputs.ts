import {BaseFormElementData, JahiaNode, NodeProperty} from './types';

/**
 * Node factories for the formidable-extended-inputs add-on fields
 * (fmdbext:rating, fmdbext:scale, fmdbext:switch, fmdbext:consent).
 */

export interface RatingData extends BaseFormElementData {
	icon?: 'star' | 'heart' | 'thumb' | 'number';
	maxValue?: number;
	minLabel?: string;
	maxLabel?: string;
	required?: boolean;
}

export function getRatingNode(data: RatingData): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.icon) properties.push({name: 'icon', value: data.icon});
	if (data.maxValue !== undefined) properties.push({name: 'maxValue', value: String(data.maxValue), type: 'LONG'});
	if (data.minLabel) properties.push({name: 'minLabel', value: data.minLabel, language: 'en'});
	if (data.maxLabel) properties.push({name: 'maxLabel', value: data.maxLabel, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	return {name: data.name || 'rating', primaryNodeType: 'fmdbext:rating', properties};
}

export interface ScaleData extends BaseFormElementData {
	minValue?: number;
	maxValue?: number;
	step?: number;
	minLabel?: string;
	maxLabel?: string;
	required?: boolean;
	npsView?: boolean;
}

export function getScaleNode(data: ScaleData): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.minValue !== undefined) properties.push({name: 'minValue', value: String(data.minValue), type: 'LONG'});
	if (data.maxValue !== undefined) properties.push({name: 'maxValue', value: String(data.maxValue), type: 'LONG'});
	if (data.step !== undefined) properties.push({name: 'step', value: String(data.step), type: 'LONG'});
	if (data.minLabel) properties.push({name: 'minLabel', value: data.minLabel, language: 'en'});
	if (data.maxLabel) properties.push({name: 'maxLabel', value: data.maxLabel, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});

	const node: JahiaNode = {name: data.name || 'scale', primaryNodeType: 'fmdbext:scale', properties};
	if (data.npsView) {
		node.mixins = ['jmix:renderable'];
		properties.push({name: 'j:view', value: 'nps'});
	}

	return node;
}

export interface SwitchData extends BaseFormElementData {
	displayMode?: 'toggle' | 'buttons';
	onLabel?: string;
	offLabel?: string;
	defaultState?: boolean;
	required?: boolean;
}

export function getSwitchNode(data: SwitchData): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.displayMode) properties.push({name: 'displayMode', value: data.displayMode});
	if (data.onLabel) properties.push({name: 'onLabel', value: data.onLabel, language: 'en'});
	if (data.offLabel) properties.push({name: 'offLabel', value: data.offLabel, language: 'en'});
	if (data.defaultState !== undefined) properties.push({name: 'defaultState', value: String(data.defaultState), type: 'BOOLEAN'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	return {name: data.name || 'switch', primaryNodeType: 'fmdbext:switch', properties};
}

export interface ConsentData extends BaseFormElementData {
	statement: string;
	termsTargetUuid?: string;
	termsLinkLabel?: string;
	required?: boolean;
}

export function getConsentNode(data: ConsentData): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'statement', value: data.statement, language: 'en'}
	];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.termsTargetUuid) properties.push({name: 'termsTarget', value: data.termsTargetUuid, type: 'WEAKREFERENCE'});
	if (data.termsLinkLabel) properties.push({name: 'termsLinkLabel', value: data.termsLinkLabel, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	return {name: data.name || 'consent', primaryNodeType: 'fmdbext:consent', properties};
}

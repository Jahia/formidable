import {JahiaNode, NodeProperty} from './types';
/**
 * Builders for the action nodes of a form (children of its 'actions' list).
 * The titles are i18n (mix:title), the recipients are not.
 */

export interface EmailNotificationActionData {
	name?: string;
	title?: string;
	to: string;
	from?: string;
	subject?: string;
	templateMessage?: string;
}

export function getEmailNotificationActionNode(data: EmailNotificationActionData): JahiaNode {
	const properties: NodeProperty[] = [{name: 'to', value: data.to}];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.from) properties.push({name: 'from', value: data.from});
	if (data.subject) properties.push({name: 'subject', value: data.subject, language: 'en'});
	if (data.templateMessage) properties.push({name: 'templateMessage', value: data.templateMessage, language: 'en'});

	return {
		name: data.name || 'emailNotification',
		primaryNodeType: 'fmdb:emailNotificationAction',
		properties
	};
}

export function getSaveToJcrActionNode(name: string = 'storeSubmission'): JahiaNode {
	return {
		name,
		primaryNodeType: 'fmdb:save2jcrAction',
		properties: []
	};
}

/**
 * The test module's own action (fmdbsample:logSubmissionAction, formidable-test-module-samples-java):
 * a third-party type, declared with its label, tooltip and icon outside formidable-engine.
 */
export function getLogSubmissionActionNode(name: string = 'logSubmission'): JahiaNode {
	return {
		name,
		primaryNodeType: 'fmdbsample:logSubmissionAction',
		properties: []
	};
}

/**
 * A forward action pointing at a configured forward target (fmdb:forwardAction, targetId is a
 * choicelist fed by the forward target files).
 */
export function getForwardActionNode(data: {name?: string; title?: string; targetId: string}): JahiaNode {
	const properties: NodeProperty[] = [{name: 'targetId', value: data.targetId}];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});

	return {
		name: data.name || 'forwardData',
		primaryNodeType: 'fmdb:forwardAction',
		properties
	};
}

/**
 * The redirect action (fmdb:redirectAction): a page of the site (weak reference to a page UUID) or a same-origin
 * relative path. The path is stored as the contributor typed it, a hostile one included: the server decides.
 */
export function getRedirectActionNode(data: {name?: string; title?: string; path?: string; pageUuid?: string}): JahiaNode {
	const properties: NodeProperty[] = [{name: 'targetType', value: data.pageUuid ? 'page' : 'path'}];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.path) properties.push({name: 'path', value: data.path, language: 'en'});
	if (data.pageUuid) properties.push({name: 'page', value: data.pageUuid, type: 'WEAKREFERENCE'});

	return {
		name: data.name || 'redirect',
		primaryNodeType: 'fmdb:redirectAction',
		properties
	};
}

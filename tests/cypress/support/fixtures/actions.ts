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

export interface CreateAccountActionData {
	name?: string;
	emailField?: string;
	passwordField?: string;
	firstNameField?: string;
	lastNameField?: string;
	group?: string;
	signIn?: boolean;
	redirectPageUuid?: string;
	redirectParameter?: string;
	passwordPattern?: string;
	activationRequired?: boolean;
	activationHours?: number;
}

/** The Create account action of formidable-accounts (fmdbacc:createAccountAction); fields are named by their node name. */
export function getCreateAccountActionNode(data: CreateAccountActionData = {}): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'emailField', value: data.emailField ?? 'email'},
		{name: 'passwordField', value: data.passwordField ?? 'password'}
	];
	if (data.firstNameField) properties.push({name: 'firstNameField', value: data.firstNameField});
	if (data.lastNameField) properties.push({name: 'lastNameField', value: data.lastNameField});
	if (data.group) properties.push({name: 'group', value: data.group});
	if (data.signIn !== undefined) properties.push({name: 'signIn', value: String(data.signIn), type: 'BOOLEAN'});
	if (data.redirectPageUuid) properties.push({name: 'redirectPage', value: data.redirectPageUuid, type: 'WEAKREFERENCE'});
	if (data.redirectParameter) properties.push({name: 'redirectParameter', value: data.redirectParameter});
	if (data.passwordPattern) properties.push({name: 'passwordPattern', value: data.passwordPattern});
	if (data.activationRequired !== undefined) properties.push({name: 'activationRequired', value: String(data.activationRequired), type: 'BOOLEAN'});
	if (data.activationHours !== undefined) properties.push({name: 'activationHours', value: String(data.activationHours), type: 'LONG'});

	return {
		name: data.name || 'createAccount',
		primaryNodeType: 'fmdbacc:createAccountAction',
		properties
	};
}

/** The Sign in action of formidable-accounts (fmdbacc:signInAction). */
export function getSignInActionNode(data: {name?: string; loginField?: string; passwordField?: string; redirectParameter?: string} = {}): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'loginField', value: data.loginField ?? 'login'},
		{name: 'passwordField', value: data.passwordField ?? 'password'}
	];
	if (data.redirectParameter) properties.push({name: 'redirectParameter', value: data.redirectParameter});

	return {
		name: data.name || 'signIn',
		primaryNodeType: 'fmdbacc:signInAction',
		properties
	};
}

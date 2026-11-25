import {JahiaNode} from './types';

/**
 * The module of the stub checks: a spec of the quarantine enables it on the test site itself. It depends on
 * formidable-quarantine, so the site then has the hold of formidable-quarantine too, which owns every held form of the
 * site that the sample hold does not claim.
 */
export const QUARANTINE_MODULE_ID = 'formidable-test-module-quarantine';

/**
 * The site role of formidable-quarantine that grants its permission formidableQuarantineManage: granted on a site, it
 * lets a user read the quarantine of the site through the GraphQL API, and retry, release, resume or reject its held
 * submissions.
 */
export const QUARANTINE_MANAGER_ROLE = 'formidable-quarantine-manager';

/**
 * The samples' check (fmdbsample:sampleCheck, formidable-test-module-samples-java), for the "Checks" list of a form:
 * the sample hold ranks above the hold of formidable-quarantine and claims only a form whose list holds it, then
 * releases the submission at once. createFormNode puts one in the list of a held form that names no list.
 */
export function getSampleCheckNode(name: string = 'sampleCheck'): JahiaNode {
	return {name, primaryNodeType: 'fmdbsample:sampleCheck', properties: []};
}

/**
 * The stub of a remote check of the quarantine test module, for the "Checks" list of a form (the `qualifiers` option
 * of createFormNode), with the CND defaults of its settings (review, retry). On a site with the module, the hold of
 * formidable-quarantine owns the form and stores its submissions in the quarantine tree.
 */
export function getRemoteStubCheckNode(name: string = 'remoteCheck'): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:remoteStubCheck', properties: []};
}

/**
 * The stub of an in-process check of the quarantine test module: it declares that it reads the bytes inside Jahia, so
 * the run refuses a list where it would read a file before a remote check passed it.
 */
export function getInProcessStubCheckNode(name: string = 'inProcessCheck'): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:inProcessStubCheck', properties: []};
}

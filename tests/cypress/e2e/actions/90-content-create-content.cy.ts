import {createUser, deleteUser} from '@jahia/cypress';
import gql from 'graphql-tag';
import {
	FORMIDABLE_TEST_SITE,
	createPublishedLiveFormPage,
	getCreateContentActionNode,
	getInputTextNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * The Create content action (formidable-content, docs/administration/how-to-content-action.md): a signed-in user's
 * submission becomes a content node in the folder their group names, and only there.
 *
 * Fixture (groovy/createContentSuppliers.groovy): one content folder and one files folder per supplier, closed to
 * everyone but the supplier's group, published with their ACL; the "ghost" supplier has a group and folders but its
 * folders grant the role to another group, the misconfiguration the action must survive.
 *
 * Not run by the author of the spec: it assumes jnt:text can be created in a jnt:contentFolder, and a groovy fixture
 * written without an instance at hand. Fix the fixture before the assertions if the first run fails there.
 */
interface SupplierTexts {
	data: {jcr: {nodeByPath: {children: {nodes: Array<{path: string; en?: {value: string}; fr?: {value: string}}>}} | null}};
}

describe('Actions - 90 Create content', () => {
	useFormidableSite();

	const SITE = FORMIDABLE_TEST_SITE.key;
	const PASSWORD = 'Cypress-Pass1!';
	const ROOT = `/sites/${SITE}/contents/orvane-briefs`;
	const NORTHLIGHT = {name: 'cypress-supplier-northlight', groups: 'cyp-supplier-northlight'};
	const BOTH = {name: 'cypress-supplier-both', groups: 'cyp-supplier-northlight,cyp-supplier-harbor'};
	const GHOST = {name: 'cypress-supplier-ghost', groups: 'cyp-supplier-ghost'};
	const OUTSIDER = {name: 'cypress-outsider', groups: ''};
	const USERS = [NORTHLIGHT, BOTH, GHOST, OUTSIDER];
	const FORM = 'create-content-form';

	let formId: string;

	/** Puts the users in their groups (the fixture is idempotent: it also removes them from the others). */
	const arrange = (user: {name: string; groups: string}) => {
		cy.executeGroovy('groovy/createContentSuppliers.groovy', {
			__SITE__: SITE,
			__USER__: user.name,
			__GROUPS__: user.groups
		}).then(result => cy.log(String(result)));
	};

	const submitAs = (user: {name: string} | undefined, fields: Record<string, string>) => {
		cy.logout();
		if (user) {
			cy.login(user.name, PASSWORD);
		}
		return postDirectMultipartSubmission({formId, fields, headers: withSameOriginHeaders()}).then(response => {
			cy.logout();
			return cy.wrap(response, {log: false});
		});
	};

	/** The jnt:text nodes of a supplier's folder, as root sees them in a workspace. */
	const texts = (supplier: string, workspace: 'EDIT' | 'LIVE') => {
		cy.login();
		return cy.apollo({
			query: gql`
				query supplierTexts($path: String!) {
					jcr(workspace: ${workspace}) {
						nodeByPath(path: $path) {
							children(typesFilter: {types: ["jnt:text"]}) {
								nodes {
									path
									en: property(name: "text", language: "en") {value}
									fr: property(name: "text", language: "fr") {value}
								}
							}
						}
					}
				}
			`,
			variables: {path: `${ROOT}/${supplier}`}
		}).then((result: SupplierTexts) => result.data.jcr.nodeByPath?.children.nodes ?? []);
	};

	before(() => {
		cy.login();
		USERS.forEach(user => createUser(user.name, PASSWORD));
		cy.executeGroovy('groovy/createContentSuppliers.groovy', {__SITE__: SITE, __USER__: NORTHLIGHT.name, __GROUPS__: NORTHLIGHT.groups}).then(result => {
			const [parentUuid, filesUuid] = String(result).split('|');
			createPublishedLiveFormPage(FORM, 'Create content form', [
				getInputTextNode({name: 'body', title: 'Body', required: true})
			], undefined, undefined, {
				actions: [getCreateContentActionNode({
					contentType: 'jnt:text',
					parentFolderUuid: parentUuid,
					filesFolderUuid: filesUuid,
					groupPrefix: 'cyp-supplier-',
					fieldMappings: ['body=text']
				})]
			}).then(info => {
				formId = info.formId;
			});
		});
		cy.logout();
	});

	after(() => {
		cy.login();
		USERS.forEach(user => deleteUser(user.name));
	});

	it('refuses a guest and writes nothing', () => {
		submitAs(undefined, {body: 'from a guest'}).then(response => {
			expect(response.status).to.eq(401);
			expect((response.body as {errorCode: string}).errorCode).to.eq('FMDB-018');
		});
		texts('northlight', 'EDIT').should('have.length', 0);
	});

	it('refuses a signed-in user who belongs to no supplier group', () => {
		cy.login();
		arrange(OUTSIDER);
		submitAs(OUTSIDER, {body: 'from an outsider'}).then(response => {
			expect(response.status).to.eq(403);
			expect((response.body as {errorCode: string}).errorCode).to.eq('FMDB-018');
		});
		texts('northlight', 'EDIT').should('have.length', 0);
	});

	it('refuses a user who belongs to two supplier groups, whichever folder they might have meant', () => {
		cy.login();
		arrange(BOTH);
		submitAs(BOTH, {body: 'from both'}).its('status').should('eq', 403);
		texts('northlight', 'EDIT').should('have.length', 0);
		texts('harbor', 'EDIT').should('have.length', 0);
	});

	it('refuses a user whose group names a folder they cannot read, though the folder exists', () => {
		cy.login();
		arrange(GHOST);
		submitAs(GHOST, {body: 'from the ghost'}).its('status').should('eq', 403);
		texts('ghost', 'EDIT').should('have.length', 0);
	});

	it('creates the content in the folder of the submitter\'s group, publishes it, and touches no other folder', () => {
		cy.login();
		arrange(NORTHLIGHT);
		submitAs(NORTHLIGHT, {body: 'Northlight proposal'}).then(response => {
			expect(response.status).to.eq(200);
			expect(response.body).to.deep.equal({success: true});
		});

		texts('northlight', 'EDIT').then(nodes => {
			expect(nodes).to.have.length(1);
			expect(nodes[0].en?.value).to.eq('Northlight proposal');
			// the supplier's own text is written in every language of the site
			expect(nodes[0].fr?.value).to.eq('Northlight proposal');
		});
		texts('northlight', 'LIVE').should('have.length', 1);
		texts('harbor', 'EDIT').should('have.length', 0);
		texts('ghost', 'EDIT').should('have.length', 0);
	});
});

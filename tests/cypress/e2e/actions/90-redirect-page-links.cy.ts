import {addNode, createSite, deleteSite, enableModule, getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import gql from 'graphql-tag';
import {FORMIDABLE_TEST_SITE, createPublishedLiveFormPage, getInputTextNode, getRedirectActionNode} from '../../support/fixtures';
import {FORMIDABLE_MODULE_IDS, SITE_HOME_PATH} from '../../support/constants';
import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';

/**
 * The URL of a page picked in the redirect action is the one the platform renders for that page
 * (docs/architecture/redirect-action.md, § Server): the absolute URL of a page of a site that answers on another server
 * name, and the vanity URL of a page that has one. The platform builds the first, and looks the second up, only for a
 * request that reached a server name other than localhost, so the submissions name the server of the test site in
 * their Host header.
 */
describe('Actions - 90 Redirect to a page, as the platform links it', () => {
	const SERVER_A = 'servera';
	const SERVER_B = 'serverb';
	const SITE_B = 'FormidableRedirectSiteB';
	const LANGUAGES = ['en', 'fr'];
	const {port, protocol} = new URL(SAME_ORIGIN);
	const hostA = port ? `${SERVER_A}:${port}` : SERVER_A;
	const originB = `${protocol}//${port ? `${SERVER_B}:${port}` : SERVER_B}`;

	const nameField = () => [getInputTextNode({name: 'name', title: 'Name'})];
	const submit = (formId: string, lang: string) => postDirectMultipartSubmission({
		formId,
		lang,
		fields: {name: 'Ada'},
		headers: withSameOriginHeaders({Host: hostA, Origin: `${protocol}//${hostA}`, Referer: `${protocol}//${hostA}/`})
	});
	const redirectOf = (response: Cypress.Response<unknown>) => {
		expect(response.status).to.eq(200);
		return (response.body as {redirect?: string}).redirect;
	};
	const uuidOf = (path: string) => getNodeByPath(path).then(node => node.data.jcr.nodeByPath.uuid as string);
	const recreateTestSite = (serverName: string) => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, {...FORMIDABLE_TEST_SITE.config, serverName});
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
	};

	before(() => {
		recreateTestSite(SERVER_A);
		deleteSite(SITE_B);
		createSite(SITE_B, {...FORMIDABLE_TEST_SITE.config, serverName: SERVER_B});
	});

	beforeEach(() => {
		cy.login();
	});

	afterEach(() => {
		cy.logout();
	});

	after(() => {
		deleteSite(SITE_B);
		// the other specs submit on localhost
		recreateTestSite(FORMIDABLE_TEST_SITE.config.serverName);
	});

	it('sends the visitor to a page of a site with another server name at that server\'s address', () => {
		const pagePath = `/sites/${SITE_B}/home/thank-you`;
		addNode({
			parentPathOrId: `/sites/${SITE_B}/home`,
			name: 'thank-you',
			primaryNodeType: 'jnt:page',
			properties: [
				{name: 'jcr:title', value: 'Thank you', language: 'en'},
				{name: 'jcr:title', value: 'Merci', language: 'fr'},
				{name: 'j:templateName', value: 'simple'}
			]
		});
		publishAndWaitJobEnding(pagePath, LANGUAGES);

		uuidOf(pagePath).then(pageUuid => {
			createPublishedLiveFormPage('redirect-other-site-form', 'Redirect to a page of another site', nameField(), undefined, undefined, {
				actions: [getRedirectActionNode({pageUuid})],
				publishLanguages: LANGUAGES
			}).then(({formId}) => {
				// the default language of the site has no language in its URL, and no /modules, the servlet's context path
				submit(formId, 'en').then(response => expect(redirectOf(response)).to.eq(`${originB}/home/thank-you.html`));
				submit(formId, 'fr').then(response => expect(redirectOf(response)).to.eq(`${originB}/fr/home/thank-you.html`));
			});
		});
	});

	it('sends the visitor to the vanity URL of a page that has one', () => {
		const pagePath = `${SITE_HOME_PATH}/vanity-target`;
		createPublishedLiveFormPage('redirect-vanity-holder', 'The vanity target holder', nameField(), 'vanity-target', 'Thanks', {
			pageProperties: [{name: 'jcr:title', value: 'Merci', language: 'fr'}],
			publishLanguages: LANGUAGES
		});
		cy.apollo({
			mutation: gql`
				mutation addVanityUrls($path: String!) {
					jcr {
						mutateNode(pathOrId: $path) {
							addVanityUrl(vanityUrlInputList: [
								{language: "en", url: "/thanks", active: true, defaultMapping: true},
								{language: "fr", url: "/merci", active: true, defaultMapping: true}
							]) {
								uuid
							}
						}
					}
				}
			`,
			variables: {path: pagePath}
		});
		publishAndWaitJobEnding(pagePath, LANGUAGES);

		uuidOf(pagePath).then(pageUuid => {
			createPublishedLiveFormPage('redirect-vanity-form', 'Redirect to a page with a vanity URL', nameField(), undefined, undefined, {
				actions: [getRedirectActionNode({pageUuid})],
				publishLanguages: LANGUAGES
			}).then(({formId}) => {
				submit(formId, 'en').then(response => expect(redirectOf(response)).to.eq('/thanks'));
				submit(formId, 'fr').then(response => expect(redirectOf(response)).to.eq('/merci'));
			});
		});
	});
});

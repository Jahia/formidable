import {addNode, addVanityUrl, createSite, deleteSite, publishAndWaitJobEnding} from '@jahia/cypress';
import {FORMIDABLE_TEST_SITE, createPublishedLiveFormPage, getInputTextNode, getRedirectActionNode} from '../../support/fixtures';
import {SITE_HOME_PATH} from '../../support/constants';
import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

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
	const expectRedirect = (formId: string, lang: string, redirect: string) => submit(formId, lang).then(response => {
		expect(response.status, lang).to.eq(200);
		expect(response.body, lang).to.deep.equal({success: true, redirect});
	});
	const addPage = (parentPath: string, name: string, titles: Record<string, string>) => addNode({
		parentPathOrId: parentPath,
		name,
		primaryNodeType: 'jnt:page',
		properties: [
			...Object.entries(titles).map(([language, value]) => ({name: 'jcr:title', value, language})),
			{name: 'j:templateName', value: 'simple'}
		]
	}).then(response => response.data.jcr.addNode.uuid as string);
	const expectRedirectsTo = (formName: string, pageUuid: string, targets: Record<string, string>) => {
		createPublishedLiveFormPage(formName, formName, nameField(), undefined, undefined, {
			actions: [getRedirectActionNode({pageUuid})],
			publishLanguages: LANGUAGES
		}).then(({formId}) => {
			Object.entries(targets).forEach(([lang, target]) => expectRedirect(formId, lang, target));
		});
	};

	useFormidableSite({serverName: SERVER_A});

	before(() => {
		deleteSite(SITE_B);
		createSite(SITE_B, {...FORMIDABLE_TEST_SITE.config, serverName: SERVER_B});
	});

	after(() => {
		deleteSite(SITE_B);
	});

	it('sends the visitor to a page of a site with another server name at that server\'s address', () => {
		addPage(`/sites/${SITE_B}/home`, 'thank-you', {en: 'Thank you', fr: 'Merci'}).then(pageUuid => {
			publishAndWaitJobEnding(`/sites/${SITE_B}/home/thank-you`, LANGUAGES);
			// the default language of the site has no language in its URL, and no /modules, the servlet's context path
			expectRedirectsTo('redirect-other-site-form', pageUuid, {
				en: `${originB}/home/thank-you.html`,
				fr: `${originB}/fr/home/thank-you.html`
			});
		});
	});

	it('sends the visitor to the vanity URL of a page that has one', () => {
		const pagePath = `${SITE_HOME_PATH}/vanity-target`;
		addPage(SITE_HOME_PATH, 'vanity-target', {en: 'Thanks', fr: 'Merci'}).then(pageUuid => {
			addVanityUrl(pagePath, 'en', '/thanks');
			addVanityUrl(pagePath, 'fr', '/merci');
			publishAndWaitJobEnding(pagePath, LANGUAGES);
			expectRedirectsTo('redirect-vanity-form', pageUuid, {en: '/thanks', fr: '/merci'});
		});
	});
});

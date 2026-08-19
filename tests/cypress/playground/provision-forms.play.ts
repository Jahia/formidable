/**
 * Manual-testing playground provisioning — NOT a test.
 *
 * Rebuilds the Formidable test site with a ready-to-use set of published live
 * forms, so the whole UI and submission process can be exercised by hand on a
 * stable model. Run it with: yarn playground (from the tests/ folder).
 *
 * Provisioned forms (pages under /sites/<site>/home), all with a save-to-JCR
 * action so the results screens can be exercised:
 *   - playground-simple    minimal contact form
 *   - playground-steps     three-step form with navigation
 *   - playground-complete  every built-in field type (same set as spec 20)
 *                          plus sourced choice fields (countries + categories)
 *
 * It also declares the options sources in the OSGi config, creates the sample
 * category tree product/tv (plasma, oled, led) used by the
 * fmdbSampleCategoryTree initializer of formidable-test-module-samples-java,
 * and provisions the results reader user john-doe (password John#1234, kept on
 * the server across runs, site member as editor) with fmdb-results-reader
 * granted on the simple form only — to test the results access rights.
 */
import {addNode, createSite, createUser, deleteSite, enableModule, getNodeByPath, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	CHECKBOX_GROUP_COMPLETE,
	CHECKBOX_SINGLE_COMPLETE,
	FORMIDABLE_TEST_SITE,
	getCategoryChoiceFieldNode,
	getCategoryNode,
	getCheckboxNode,
	getInputColorNode,
	getInputDateNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputFileNode,
	getInputTextNode,
	getRadioNode,
	getSelectNode,
	getSourcedChoiceFieldNode,
	getStepNode,
	getTextareaNode,
	INPUT_COLOR_COMPLETE,
	INPUT_DATE_COMPLETE,
	INPUT_DATETIME_LOCAL_COMPLETE,
	INPUT_EMAIL_COMPLETE,
	INPUT_FILE_MULTIPLE,
	INPUT_TEXT_COMPLETE,
	RADIO_GROUP,
	SELECT_SINGLE,
	setOptionsSourcesConfig,
	TEXTAREA_COMPLETE
} from '../support/fixtures';
import {createPublishedLiveFormPage} from '../support/fixtures/forms';
import {FORMIDABLE_MODULE_IDS, SITE_HOME_PATH} from '../support/constants';
import type {JahiaNode} from '../support/fixtures/types';

const CATEGORY_ROOT = '/sites/systemsite/categories';

const RESULTS_READER = {name: 'john-doe', password: 'John#1234'};

const saveToJcrAction = (): JahiaNode => ({
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
});

// Adds French values on top of the fixture's English ones.
const withFrench = (node: JahiaNode, frProperties: Array<{name: string; value: string}>): JahiaNode => {
	node.properties.push(...frProperties.map(property => ({...property, language: 'fr'})));
	return node;
};

// Full name field with a custom required message in both site languages.
const fullNameField = (): JahiaNode => {
	const node = getInputTextNode({name: 'fullName', title: 'Full name', required: true});
	node.properties.push(
		{name: 'msgValueMissing', value: 'Please fill in your full name', language: 'en'},
		{name: 'msgValueMissing', value: 'Merci de renseigner votre nom complet', language: 'fr'}
	);
	return node;
};

const OPTIONS_SOURCES_CONFIG = [
	// Literal label
	'countries|Countries|country',
	// Localized label: resolved against the module's resource bundle in the editor UI language
	'tv|formidable-test-module-samples-java:sample.optionsSource.tv|fmdbSampleCategoryTree|product/tv'
];

describe('Playground - provision manual-testing forms', () => {
	before(() => {
		cy.login();
	});

	it('resets the test site', () => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
		// A live page renders only if the site home is published in the page
		// language (getSite().getHome() resolves to null otherwise -> 500),
		// and publishing a page does not cascade up to its home: publish the
		// home in every site language now, while it is still empty.
		publishAndWaitJobEnding(SITE_HOME_PATH, ['en', 'fr']);
	});

	it('declares the options sources in the module configuration', () => {
		setOptionsSourcesConfig(OPTIONS_SOURCES_CONFIG);
	});

	it('creates and publishes the sample category tree product/tv', () => {
		// Categories are global; creations are idempotent (existing nodes are kept).
		addNode({parentPathOrId: CATEGORY_ROOT, ...getCategoryNode('product', 'Product', 'Produit')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product`, ...getCategoryNode('tv', 'TV', 'Téléviseur')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product/tv`, ...getCategoryNode('plasma', 'Plasma', 'Plasma')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product/tv`, ...getCategoryNode('oled', 'OLED', 'OLED')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product/tv`, ...getCategoryNode('led', 'LED', 'LED')});
		publishAndWaitJobEnding(`${CATEGORY_ROOT}/product`, ['en', 'fr']);
	});

	it('provisions the simple form and grants its results to the reader user', () => {
		createPublishedLiveFormPage(
			'playground-simple',
			'Playground - Simple contact form',
			[
				withFrench(fullNameField(), [{name: 'jcr:title', value: 'Nom complet'}]),
				withFrench(getInputEmailNode({name: 'email', title: 'Email', required: true}), [{name: 'jcr:title', value: 'Email'}]),
				withFrench(getTextareaNode({name: 'message', title: 'Message'}), [{name: 'jcr:title', value: 'Message'}])
			],
			undefined,
			undefined,
			{
				actions: [saveToJcrAction()],
				properties: [{name: 'jcr:title', value: 'Playground - Formulaire de contact simple', language: 'fr'}],
				pageProperties: [{name: 'jcr:title', value: 'Playground - Formulaire de contact simple', language: 'fr'}],
				publishLanguages: ['en', 'fr']
			}
		).then(({formPath, livePath}) => {
			// Server-level user, kept across runs; site member so jContent is reachable.
			createUser(RESULTS_READER.name, RESULTS_READER.password);
			grantRoles(`/sites/${FORMIDABLE_TEST_SITE.key}`, ['editor'], RESULTS_READER.name, 'USER');

			// Results access: fmdb-results-reader on the form node, propagated to the
			// results by the ACL sync once the form is (re)published.
			grantRoles(formPath, ['fmdb-results-reader'], RESULTS_READER.name, 'USER');
			publishAndWaitJobEnding(formPath, ['en', 'fr']);

			cy.log(`Simple form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`);
			cy.log(`Results reader: ${RESULTS_READER.name} / ${RESULTS_READER.password} (access to playground-simple results only)`);
		});
	});

	it('provisions the multi-step form', () => {
		createPublishedLiveFormPage(
			'playground-steps',
			'Playground - Multi-step form',
			[
				getStepNode({
					name: 'identity',
					title: 'Identity',
					label: 'Identity',
					children: [
						fullNameField(),
						getInputEmailNode({name: 'email', title: 'Email', required: true})
					]
				}),
				getStepNode({
					name: 'preferences',
					title: 'Preferences',
					label: 'Preferences',
					children: [
						getSelectNode(SELECT_SINGLE),
						getRadioNode(RADIO_GROUP)
					]
				}),
				getStepNode({
					name: 'confirmation',
					title: 'Confirmation',
					label: 'Confirmation',
					children: [
						getCheckboxNode(CHECKBOX_SINGLE_COMPLETE),
						getTextareaNode({name: 'comment', title: 'Comment'})
					]
				})
			],
			undefined,
			undefined,
			// Both site languages: the actions get localized default titles at creation,
			// so an en-only publication would leave their fr translation unpublished.
			{actions: [saveToJcrAction()], publishLanguages: ['en', 'fr']}
		).then(({livePath}) => cy.log(`Multi-step form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
	});

	it('provisions the complete form (all field types + sourced options)', () => {
		getNodeByPath(`${CATEGORY_ROOT}/product/tv`).then(response => {
			const tvCategoryUuid: string = response.data.jcr.nodeByPath.uuid;

			createPublishedLiveFormPage(
				'playground-complete',
				'Playground - Complete form',
				[
					getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}),
					getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}),
					getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined}),
					getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined}),
					getInputColorNode(INPUT_COLOR_COMPLETE),
					getCheckboxNode(CHECKBOX_GROUP_COMPLETE),
					getRadioNode(RADIO_GROUP),
					getSelectNode(SELECT_SINGLE),
					getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}),
					getInputFileNode(INPUT_FILE_MULTIPLE),
					getSourcedChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'country', title: 'Country (sourced: countries)', sourceKey: 'countries'}),
					getSourcedChoiceFieldNode({primaryNodeType: 'fmdb:radio', name: 'tvType', title: 'TV type (sourced: categories product/tv)', sourceKey: 'tv'}),
					getCategoryChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'tvCategory', title: 'TV category (category mode, multiple select)', rootCategoryUuid: tvCategoryUuid, multiple: true})
				],
				undefined,
				undefined,
				{actions: [saveToJcrAction()], publishLanguages: ['en', 'fr']}
			).then(({livePath}) => cy.log(`Complete form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
		});
	});
});

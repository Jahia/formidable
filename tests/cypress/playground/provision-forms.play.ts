/**
 * Manual-testing playground provisioning — NOT a test.
 *
 * Rebuilds the Formidable test site with a ready-to-use set of published live
 * forms, so the whole UI and submission process can be exercised by hand on a
 * stable model. Run it with: yarn playground (from the tests/ folder).
 *
 * Provisioned forms (pages under /sites/<site>/home):
 *   - playground-simple    minimal contact form
 *   - playground-steps     three-step form with navigation
 *   - playground-complete  every built-in field type (same set as spec 20)
 *                          plus sourced choice fields (countries + categories)
 *
 * It also declares the options sources in the OSGi config and creates the
 * sample category tree product/tv (plasma, oled, led) used by the
 * fmdbSampleCategoryTree initializer of formidable-test-module-samples-java.
 */
import {addNode, createSite, deleteSite, enableModule, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	CHECKBOX_GROUP_COMPLETE,
	CHECKBOX_SINGLE_COMPLETE,
	FORMIDABLE_TEST_SITE,
	getCheckboxNode,
	getInputColorNode,
	getInputDateNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputFileNode,
	getInputTextNode,
	getRadioNode,
	getSelectNode,
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
	TEXTAREA_COMPLETE
} from '../support/fixtures';
import {createPublishedLiveFormPage} from '../support/fixtures/forms';
import {FORMIDABLE_MODULE_IDS} from '../support/constants';
import type {JahiaNode} from '../support/fixtures/types';

const CATEGORY_ROOT = '/sites/systemsite/categories';

const OPTIONS_SOURCES_CONFIG = [
	'countries|Countries|country',
	'tv|TV screens|fmdbSampleCategoryTree|product/tv'
].join('\n');

const sourcedChoiceField = (
	primaryNodeType: 'fmdb:select' | 'fmdb:radio' | 'fmdb:checkbox',
	name: string,
	title: string,
	sourceKey: string
): JahiaNode => ({
	name,
	primaryNodeType,
	mixins: ['fmdbmix:sourcedOptions'],
	properties: [
		{name: 'jcr:title', value: title, language: 'en'},
		{name: 'fmdb:optionsMode', value: 'sourced'},
		{name: 'fmdb:optionsSourceKey', value: sourceKey}
	]
});

const category = (name: string, titleEn: string, titleFr: string): JahiaNode => ({
	name,
	primaryNodeType: 'jnt:category',
	properties: [
		{name: 'jcr:title', value: titleEn, language: 'en'},
		{name: 'jcr:title', value: titleFr, language: 'fr'}
	]
});

describe('Playground - provision manual-testing forms', () => {
	before(() => {
		cy.login();
	});

	it('resets the test site', () => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
	});

	it('declares the options sources in the module configuration', () => {
		cy.runProvisioningScript({
			script: {
				fileContent: JSON.stringify([{
					editConfiguration: 'org.jahia.modules.formidable',
					properties: {optionsSources: OPTIONS_SOURCES_CONFIG}
				}]),
				type: 'application/json'
			}
		});
	});

	it('creates and publishes the sample category tree product/tv', () => {
		// Categories are global; creations are idempotent (existing nodes are kept).
		addNode({parentPathOrId: CATEGORY_ROOT, ...category('product', 'Product', 'Produit')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product`, ...category('tv', 'TV', 'Téléviseur')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product/tv`, ...category('plasma', 'Plasma', 'Plasma')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product/tv`, ...category('oled', 'OLED', 'OLED')});
		addNode({parentPathOrId: `${CATEGORY_ROOT}/product/tv`, ...category('led', 'LED', 'LED')});
		publishAndWaitJobEnding(`${CATEGORY_ROOT}/product`, ['en', 'fr']);
	});

	it('provisions the simple form', () => {
		createPublishedLiveFormPage(
			'playground-simple',
			'Playground - Simple contact form',
			[
				getInputTextNode({name: 'fullName', title: 'Full name', required: true}),
				getInputEmailNode({name: 'email', title: 'Email', required: true}),
				getTextareaNode({name: 'message', title: 'Message'})
			]
		).then(({livePath}) => cy.log(`Simple form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
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
						getInputTextNode({name: 'fullName', title: 'Full name', required: true}),
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
			]
		).then(({livePath}) => cy.log(`Multi-step form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
	});

	it('provisions the complete form (all field types + sourced options)', () => {
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
				sourcedChoiceField('fmdb:select', 'country', 'Country (sourced: countries)', 'countries'),
				sourcedChoiceField('fmdb:radio', 'tvType', 'TV type (sourced: categories product/tv)', 'tv')
			]
		).then(({livePath}) => cy.log(`Complete form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
	});
});

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
 *                          and a content-mode select (texts under
 *                          contents/agencies, incl. an unpublished draft to
 *                          showcase that only published contents reach live)
 *
 * All editorial values are provided in both site languages (en and fr), so the
 * localized rendering and editing can be exercised too. The sourced country
 * select carries an empty-option label in both languages to showcase the
 * native required validation on the site.
 *
 * It also declares the options sources in the OSGi config (countries + the
 * static screen-type list of the fmdbSampleStaticList initializer of
 * formidable-test-module-samples-java), creates the sample category tree
 * product/tv (plasma, oled, led) used by the category-mode field, and
 * provisions the results reader user john-doe (password John#1234, kept on
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
	getContentChoiceFieldNode,
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
	getTitledTextNode,
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
import {CONTENT_PATH, FORMIDABLE_MODULE_IDS, SITE_HOME_PATH} from '../support/constants';
import type {JahiaNode} from '../support/fixtures/types';

// Sample theme written into the complete form's css property; lives with the
// sample code so module developers can pick it up as a starting point.
const COMPLETE_FORM_THEME_PATH = '../jahia-test-module/sample-form-css/registration-yellow-theme.css';

const CATEGORY_ROOT = '/sites/systemsite/categories';
const AGENCIES_ROOT_PATH = `${CONTENT_PATH}/agencies`;

const RESULTS_READER = {name: 'john-doe', password: 'John#1234'};

const saveToJcrAction = (): JahiaNode => ({
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
});

// Adds French values on top of the fixture's English ones. Touching the French
// locale of a choice field makes its French options mandatory (i18n property):
// choice fields must always pair a French title with frOptions.
const withFrench = (node: JahiaNode, frProperties: Array<{name: string; value?: string; values?: string[]}>): JahiaNode => {
	node.properties.push(...frProperties.map(property => ({...property, language: 'fr'})));
	return node;
};

// Adds English values the fixture does not set (same shape as withFrench).
const withEnglish = (node: JahiaNode, enProperties: Array<{name: string; value?: string; values?: string[]}>): JahiaNode => {
	node.properties.push(...enProperties.map(property => ({...property, language: 'en'})));
	return node;
};

// French option list in the manual-options storage format.
const frOptions = (options: Array<{value: string; label: string; selected?: boolean}>): {name: string; values: string[]} => ({
	name: 'fmdb:options',
	values: options.map(option => JSON.stringify({
		value: option.value,
		label: option.label,
		selected: option.selected ?? false
	}))
});

const FR_DEPARTMENT_OPTIONS = frOptions([
	{value: 'engineering', label: 'Ingénierie'},
	{value: 'sales', label: 'Ventes'},
	{value: 'support', label: 'Support'}
]);

// Department select in both languages. The empty first entry of the shared
// fixture is replaced by the empty-option label property: the field still
// starts empty, through the supported configuration.
const departmentSelect = (): JahiaNode => withFrench(
	withEnglish(
		getSelectNode({...SELECT_SINGLE, options: SELECT_SINGLE.options.filter(option => option.value !== '')}),
		[{name: 'fmdb:optionsEmptyLabel', value: 'Please select'}]
	),
	[
		{name: 'jcr:title', value: 'Service'},
		{name: 'fmdb:optionsEmptyLabel', value: 'Veuillez sélectionner'},
		FR_DEPARTMENT_OPTIONS
	]
);

// A HALF-translated option list: French carries a label for the first entry
// only, which is what the save-time feeding leaves behind for the others (an
// entry nobody translated is stored with an empty label, never with the
// default language's words). Whether those two render as "Chocolate" and
// "Pistachio" or vanish from the French form is the site's call — see the log
// line at the end of this run.
const FR_FLAVOR_OPTIONS = frOptions([
	{value: 'vanilla', label: 'Vanille'},
	{value: 'chocolate', label: ''},
	{value: 'pistachio', label: ''}
]);

const FR_DELIVERY_OPTIONS = frOptions([
	{value: 'standard', label: 'Standard'},
	{value: 'express', label: 'Express', selected: true},
	{value: 'pickup', label: 'Retrait sur place'}
]);

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
	'tv|formidable-test-module-samples-java:sample.optionsSource.tv|fmdbSampleStaticList|plasma,oled,led'
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

	it('creates and publishes the agency contents (content-mode targets)', () => {
		addNode({parentPathOrId: CONTENT_PATH, name: 'agencies', primaryNodeType: 'jnt:contentFolder', properties: []});
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('paris', 'Paris agency', 'Agence de Paris')});
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('lyon', 'Lyon agency', 'Agence de Lyon')});
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, name: 'europe', primaryNodeType: 'jnt:contentFolder', properties: []});
		addNode({parentPathOrId: `${AGENCIES_ROOT_PATH}/europe`, ...getTitledTextNode('berlin', 'Berlin agency', 'Agence de Berlin')});
		publishAndWaitJobEnding(AGENCIES_ROOT_PATH, ['en', 'fr']);
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
				withFrench(getStepNode({
					name: 'identity',
					title: 'Identity',
					label: 'Identity',
					children: [
						withFrench(fullNameField(), [{name: 'jcr:title', value: 'Nom complet'}]),
						withFrench(getInputEmailNode({name: 'email', title: 'Email', required: true}), [{name: 'jcr:title', value: 'Email'}])
					]
				}), [{name: 'jcr:title', value: 'Identité'}, {name: 'label', value: 'Identité'}]),
				withFrench(getStepNode({
					name: 'preferences',
					title: 'Preferences',
					label: 'Preferences',
					children: [
						departmentSelect(),
						withFrench(getRadioNode(RADIO_GROUP), [{name: 'jcr:title', value: 'Mode de livraison'}, FR_DELIVERY_OPTIONS])
					]
				}), [{name: 'jcr:title', value: 'Préférences'}, {name: 'label', value: 'Préférences'}]),
				withFrench(getStepNode({
					name: 'confirmation',
					title: 'Confirmation',
					label: 'Confirmation',
					children: [
						withFrench(getCheckboxNode(CHECKBOX_SINGLE_COMPLETE), [
							{name: 'jcr:title', value: 'J\'accepte les conditions'},
							frOptions([{value: 'agreed', label: 'J\'accepte les conditions', selected: true}])
						]),
						withFrench(getTextareaNode({name: 'comment', title: 'Comment'}), [{name: 'jcr:title', value: 'Commentaire'}])
					]
				}), [{name: 'jcr:title', value: 'Confirmation'}, {name: 'label', value: 'Confirmation'}])
			],
			undefined,
			undefined,
			// Both site languages: the actions get localized default titles at creation,
			// so an en-only publication would leave their fr translation unpublished.
			{
				actions: [saveToJcrAction()],
				properties: [{name: 'jcr:title', value: 'Playground - Formulaire multi-étapes', language: 'fr'}],
				pageProperties: [{name: 'jcr:title', value: 'Playground - Formulaire multi-étapes', language: 'fr'}],
				publishLanguages: ['en', 'fr']
			}
		).then(({livePath}) => cy.log(`Multi-step form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
	});

	it('provisions the complete form (all field types + sourced options)', () => {
		// Enqueued before the creation chain, so the variable is set by the time
		// the nested callbacks below build the form properties.
		let themeCss = '';
		cy.readFile(COMPLETE_FORM_THEME_PATH).then((content: string) => {
			themeCss = content;
		});

		getNodeByPath(`${CATEGORY_ROOT}/product/tv`).then(response => {
			const tvCategoryUuid: string = response.data.jcr.nodeByPath.uuid;

			getNodeByPath(AGENCIES_ROOT_PATH).then(agenciesResponse => {
				const agenciesRootUuid: string = agenciesResponse.data.jcr.nodeByPath.uuid;

					createPublishedLiveFormPage(
					'playground-complete',
					'Playground - Complete form',
					[
						withFrench(getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}), [{name: 'jcr:title', value: 'Code employé'}]),
						withFrench(getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}), [{name: 'jcr:title', value: 'Email de contact'}]),
						// A birth date cannot be after the submission day; the appointment
						// cannot be before it — the relative bound modes showcased live.
						withFrench(getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined, max: undefined, maxBoundMode: 'today'}), [{name: 'jcr:title', value: 'Date de naissance'}]),
						withFrench(getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined, min: undefined, minBoundMode: 'today'}), [{name: 'jcr:title', value: 'Rendez-vous'}]),
						withFrench(getInputColorNode(INPUT_COLOR_COMPLETE), [{name: 'jcr:title', value: 'Choisissez votre couleur préférée'}]),
						withFrench(getCheckboxNode(CHECKBOX_GROUP_COMPLETE), [
							{name: 'jcr:title', value: 'Centres d\'intérêt requis'},
							frOptions([
								{value: 'reading', label: 'Lecture'},
								{value: 'sports', label: 'Sport', selected: true},
								{value: 'music', label: 'Musique'}
							])
						]),
						withFrench(getRadioNode(RADIO_GROUP), [{name: 'jcr:title', value: 'Mode de livraison'}, FR_DELIVERY_OPTIONS]),
						departmentSelect(),
						withFrench(getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}), [{name: 'jcr:title', value: 'Résumé du projet'}]),
						withFrench(getInputFileNode(INPUT_FILE_MULTIPLE), [{name: 'jcr:title', value: 'Pièces jointes'}]),
						// The sourced select showcases the empty-option label: the field starts
						// empty and its native required validation is exercisable on the site.
						withFrench(
							withEnglish(
								getSourcedChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'country', title: 'Country (sourced: countries)', sourceKey: 'countries'}),
								[{name: 'fmdb:optionsEmptyLabel', value: 'Select a country…'}]
							),
							[
								{name: 'jcr:title', value: 'Pays (source : countries)'},
								{name: 'fmdb:optionsEmptyLabel', value: 'Sélectionnez un pays…'}
							]
						),
						withFrench(getSourcedChoiceFieldNode({primaryNodeType: 'fmdb:radio', name: 'tvType', title: 'TV type (sourced: static screen-type list)', sourceKey: 'tv'}), [{name: 'jcr:title', value: 'Type de TV (source : liste statique de types d\'écrans)'}]),
						withFrench(getCategoryChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'tvCategory', title: 'TV category (category mode, multiple select)', rootCategoryUuid: tvCategoryUuid, multiple: true}), [{name: 'jcr:title', value: 'Catégorie TV (mode catégorie, sélection multiple)'}]),
						withFrench(getContentChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'agency', title: 'Agency (content mode: texts under contents/agencies)', rootNodeUuid: agenciesRootUuid, nodeType: 'jnt:text'}), [{name: 'jcr:title', value: 'Agence (mode contenu : textes sous contents/agencies)'}])
					],
					undefined,
					undefined,
					{
						actions: [saveToJcrAction()],
						properties: [
							{name: 'jcr:title', value: 'Playground - Formulaire complet', language: 'fr'},
							// The showcase theme lives in the form's own css property, so it
							// survives every re-provisioning without a manual step.
							{name: 'css', value: themeCss}
						],
						pageProperties: [{name: 'jcr:title', value: 'Playground - Formulaire complet', language: 'fr'}],
						publishLanguages: ['en', 'fr']
					}
				).then(({livePath}) => cy.log(`Complete form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
			});
		});
	});

	it('provisions the half-translated options form (untranslated-content playground)', () => {
		createPublishedLiveFormPage(
			'playground-languages',
			'Playground - Half-translated options',
			[
				withFrench(
					getSelectNode({
						name: 'flavor',
						title: 'Flavor',
						options: [
							{value: 'vanilla', label: 'Vanilla'},
							{value: 'chocolate', label: 'Chocolate'},
							{value: 'pistachio', label: 'Pistachio'}
						]
					}),
					[{name: 'jcr:title', value: 'Parfum'}, FR_FLAVOR_OPTIONS]
				)
			],
			undefined,
			undefined,
			{
				actions: [saveToJcrAction()],
				properties: [{name: 'jcr:title', value: 'Playground - Options traduites à moitié', language: 'fr'}],
				pageProperties: [{name: 'jcr:title', value: 'Playground - Options traduites à moitié', language: 'fr'}],
				publishLanguages: ['en', 'fr']
			}
		).then(({livePath}) => {
			cy.log(`Half-translated options, English: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`);
			cy.log(`Half-translated options, French: /fr/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`);
			cy.log('Toggle "Replace untranslated content with the default language content" in the site settings: '
				+ 'ON renders Chocolate/Pistachio with their English labels, OFF drops them from the French form.');
		});
	});

	it('adds an unpublished draft agency, absent from the live options', () => {
		// Created after the complete form is published: publishing a form
		// publishes its referenced options root with its subtree, so an earlier
		// draft would have been published along.
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('draft', 'Draft agency', 'Agence brouillon')});
	});
});

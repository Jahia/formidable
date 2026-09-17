/**
 * Manual-testing playground provisioning — NOT a test.
 *
 * Rebuilds the Formidable test site with a ready-to-use set of published live
 * forms, so the whole UI and submission process can be exercised by hand on a
 * stable model. Run it with: yarn playground (from the tests/ folder).
 *
 * Provisioned forms (pages under /sites/<site>/home), all with a save-to-JCR
 * action so the results screens can be exercised:
 *   - playground-simple    minimal contact form, the visitor named by a first and a last name so
 *                          that each maps to its own visitor-profile property
 *   - playground-newsletter a second small form, shown only on the two-forms page below
 *   - playground-steps     three-step form with navigation (step 2 holds a
 *                          fieldset, the deepest authoring level, and the
 *                          delivery method drives a field and that fieldset)
 *   - playground-steps-styled  the same three-step form carrying the sample
 *                          theme in its css property, to exercise the
 *                          authoring UI (Page Builder zones, boxes) both
 *                          with and without a business stylesheet
 *   - playground-two-forms-page  the simple form and the newsletter one on a single page, the case a
 *                          page with one form never shows (two results sets, two mappings, one script)
 *   - playground-complete  every built-in field type (same set as spec 20), plus a gender radio
 *                           and a number of children — the two shapes the visitor profile mapping needs
 *
 * When formidable-jexperience-engine is on the instance, the simple and the complete forms map their
 * fields to jCustomer's default visitor profile properties (firstName, lastName, email, phoneNumber,
 * birthDate, gender, kids, countryName), one field per form is marked sensitive, and jExperience is
 * enabled on the site — so a publication writes the mapping rules and a live submission feeds the
 * profile. The two shapes jCustomer's default schema has no property for — a multi-valued string for the
 * checkbox group, a boolean for the newsletter switch — get one in a "Formidable playground" card, created
 * once through jExperience's admin proxy. Without the module the same forms are provisioned, mappings left
 * out: nothing here depends on it. The yarn script runs the browser under a plain Chrome user agent: the tracker
 * carries the crawler-user-agents list, which names HeadlessChrome, and would otherwise start in its fallback mode
 * and send nothing for the sample submissions.
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
import gql from 'graphql-tag';
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
	getFieldsetNode,
	getInputDateNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputFileNode,
	getInputNumberNode,
	getInputTextNode,
	getRadioNode,
	getSelectNode,
	getSourcedChoiceFieldNode,
	getStepNode,
	getSwitchNode,
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
import {createFormNode, createPublishedLiveFormPage, visitLiveForm} from '../support/fixtures/forms';
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

// Titles of the two lists of every form, in both site languages: the words of the
// module's default titles, set explicitly. The Page Builder shows a list's title on
// its box, and a default left unresolved at provisioning time (a bundle not registered
// yet) would leave the bare resource key there for the whole life of the playground.
const LIST_TITLES = {
	fieldListProperties: [
		{name: 'jcr:title', value: 'Form fields', language: 'en'},
		{name: 'jcr:title', value: 'Champs du formulaire', language: 'fr'}
	],
	actionListProperties: [
		{name: 'jcr:title', value: 'Form actions', language: 'en'},
		{name: 'jcr:title', value: 'Actions du formulaire', language: 'fr'}
	]
};

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

// --- jExperience: the visitor profile mapping of a field, applied only when the module is on the instance.
// The mixins below are declared by formidable-jexperience-engine; the flag is read in the first test, before
// any form is built, and the two helpers hand the node back untouched when the module is absent.
const JXP_MAPPING_MIXIN = 'fmdbmix:jExperienceProfileMapping';
const JXP_SENSITIVE_MIXIN = 'fmdbmix:jExperienceSensitiveField';
let jExperienceAvailable = false;

/** Maps the field to a visitor profile property: the mapping mixin, the property, the write strategy and the prefill switch. */
const mappedTo = (node: JahiaNode, profileProperty: string, options: {strategy?: 'alwaysSet' | 'setIfMissing'; prefill?: boolean} = {}): JahiaNode => {
	if (!jExperienceAvailable) return node;
	node.mixins = [...(node.mixins ?? []), JXP_MAPPING_MIXIN];
	node.properties.push(
		{name: 'jExperienceProfileProperty', value: profileProperty},
		{name: 'jExperienceSetStrategy', value: options.strategy ?? 'alwaysSet'},
		{name: 'jExperiencePrefillFromProfile', value: String(options.prefill ?? false), type: 'BOOLEAN'}
	);
	return node;
};

/** Marks the field sensitive: its value never reaches the visitor profile, and the dropdown offers it no mapping. */
const sensitive = (node: JahiaNode): JahiaNode => {
	if (!jExperienceAvailable) return node;
	node.mixins = [...(node.mixins ?? []), JXP_SENSITIVE_MIXIN];
	node.properties.push({name: 'jExperienceSensitive', value: 'true', type: 'BOOLEAN'});
	return node;
};

const GENDER_RADIO = {
	name: 'gender',
	title: 'Gender',
	required: false,
	choices: [
		{value: 'female', label: 'Female', selected: false},
		{value: 'male', label: 'Male', selected: false},
		{value: 'other', label: 'Other', selected: false}
	]
};

// jCustomer's default schema offers no multi-valued and no boolean property a form could feed, so the
// playground adds the two it needs, in a card of their own — through jExperience's admin proxy, which
// carries the logged-in session; created once, found again on the next run. A proxy that does not
// answer (no jCustomer connected) is logged, not fatal: the mappings are then skipped at publication.
const PLAYGROUND_CARD_TAG = 'cardDataTag/_fmdbplaygd/6/Formidable playground';
const CUSTOM_PROFILE_PROPERTIES = [
	{id: 'formidableInterests', name: 'Interests (Formidable playground)', type: 'string', multivalued: true},
	{id: 'formidableOptIn', name: 'Newsletter opt-in (Formidable playground)', type: 'boolean', multivalued: false}
];
const ensureCustomProfileProperties = (): void => {
	const endpoint = `/modules/jexperience/proxy/${FORMIDABLE_TEST_SITE.key}/cxs/profiles/properties`;
	cy.request({url: `${endpoint}/targets/profiles`, failOnStatusCode: false}).then(response => {
		if (response.status !== 200 || !Array.isArray(response.body)) {
			cy.log(`jCustomer not reachable through jExperience (HTTP ${response.status}): the playground's profile properties are not created`);
			return;
		}
		const existing = new Set((response.body as Array<{itemId: string}>).map(property => property.itemId));
		CUSTOM_PROFILE_PROPERTIES.forEach((property, position) => {
			if (existing.has(property.id)) return;
			cy.request({
				method: 'POST',
				url: endpoint,
				failOnStatusCode: false,
				body: {
					itemId: property.id,
					itemType: 'propertyType',
					target: 'profiles',
					type: property.type,
					multivalued: property.multivalued,
					metadata: {id: property.id, name: property.name, tags: [], systemTags: [PLAYGROUND_CARD_TAG, `positionInCard.${position}`, 'hasCardDataTag'], enabled: true, hidden: false, readOnly: false}
				}
			}).then(created => cy.log(`visitor profile property ${property.id}: HTTP ${created.status}`));
		});
	});
};

// Deleting the site removes its forms without telling their mapping rules apart (a removed node's types cannot
// be resolved), so each run would leave the previous run's rules behind in jCustomer — and every one of them
// would still put a stale form id in the tracker's watch list. The rules of the site are deleted before the
// site is, by the id prefix the synchroniser writes.
const MAPPING_RULE_PREFIX = `formidable-form-mapping_${FORMIDABLE_TEST_SITE.key}_`;
const deleteMappingRulesOfTheSite = (): void => {
	const endpoint = `/modules/jexperience/proxy/${FORMIDABLE_TEST_SITE.key}/cxs/rules`;
	cy.request({url: endpoint, failOnStatusCode: false}).then(response => {
		if (response.status !== 200 || !Array.isArray(response.body)) {
			cy.log(`jCustomer not reachable through jExperience (HTTP ${response.status}): the previous run's mapping rules are left as they are`);
			return;
		}
		const stale = (response.body as Array<{id: string}>).map(rule => rule.id).filter(id => id.startsWith(MAPPING_RULE_PREFIX));
		cy.log(`${stale.length} mapping rule(s) of a previous run to delete`);
		stale.forEach(id => cy.request({method: 'DELETE', url: `${endpoint}/${id}`, failOnStatusCode: false}));
	});
};

// French option list in the manual-options storage format.
const frOptions = (options: Array<{value: string; label: string; selected?: boolean}>): {name: string; values: string[]} => ({
	name: 'options',
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
// Conditional logic driven by the delivery method, so rules can be tried in live,
// preview and edit mode: the pickup location shows only for "pickup", and (on the
// multi-step forms) the delivery address fieldset shows for every other method.
const PICKUP_LOCATION_RULE = JSON.stringify({
	logicId: 'pg-pickup-location',
	sourceFieldName: 'deliveryMethod',
	sourceFieldType: 'fmdb:radio',
	valueKind: 'choice',
	operator: 'in',
	values: ['pickup']
});

const DELIVERY_ADDRESS_RULE = JSON.stringify({
	logicId: 'pg-delivery-address',
	sourceFieldName: 'deliveryMethod',
	sourceFieldType: 'fmdb:radio',
	valueKind: 'choice',
	operator: 'notIn',
	values: ['pickup']
});

// Attaches conditional-logic rules (authoring format) to a field, fieldset or step.
const withLogics = (node: JahiaNode, ...rules: string[]): JahiaNode => ({
	...node,
	properties: [...node.properties, {name: 'logics', values: rules}]
});

// Second conditional case, on the simple form this time: that form has no custom
// CSS, so it shows how the core renders a conditional field by default.
const PHONE_NUMBER_RULE = JSON.stringify({
	logicId: 'pg-phone-number',
	sourceFieldName: 'contactChannel',
	sourceFieldType: 'fmdb:select',
	valueKind: 'choice',
	operator: 'in',
	values: ['phone']
});

const contactChannelSelect = (): JahiaNode => withFrench(
	getSelectNode({
		name: 'contactChannel',
		title: 'How should we get back to you?',
		options: [
			{value: 'email', label: 'By email', selected: true},
			{value: 'phone', label: 'By phone', selected: false}
		]
	}),
	[
		{name: 'jcr:title', value: 'Comment vous recontacter ?'},
		frOptions([{value: 'email', label: 'Par e-mail', selected: true}, {value: 'phone', label: 'Par téléphone'}])
	]
);

const phoneNumberField = (): JahiaNode => {
	const field = getInputTextNode({
		name: 'phoneNumber',
		title: 'Phone number (shown when you ask for a call)',
		placeholder: '+33 6 12 34 56 78',
		// Digits only are typed, the literals come by themselves: what reaches the profile is the formatted number.
		mask: '+99 9 99 99 99 99'
	});
	return withFrench(
		withLogics(field, PHONE_NUMBER_RULE),
		[
			{name: 'jcr:title', value: 'Numéro de téléphone (affiché si vous demandez un appel)'},
			{name: 'placeholder', value: '+33 6 12 34 56 78'}
		]
	);
};

const pickupLocationField = (): JahiaNode => {
	const field = getInputTextNode({
		name: 'pickupLocation',
		title: 'Pickup location (shown when delivery method is Pickup)',
		placeholder: 'Store name or city'
	});
	return withFrench(
		withLogics(field, PICKUP_LOCATION_RULE),
		[
			{name: 'jcr:title', value: 'Point de retrait (affiché si le mode de livraison est Retrait)'},
			{name: 'placeholder', value: 'Nom du magasin ou ville'}
		]
	);
};

const departmentSelect = (): JahiaNode => withFrench(
	withEnglish(
		getSelectNode({...SELECT_SINGLE, options: SELECT_SINGLE.options.filter(option => option.value !== '')}),
		[{name: 'optionsEmptyLabel', value: 'Please select'}]
	),
	[
		{name: 'jcr:title', value: 'Service'},
		{name: 'optionsEmptyLabel', value: 'Veuillez sélectionner'},
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
const FR_GENDER_OPTIONS = frOptions([
	{value: 'female', label: 'Femme'},
	{value: 'male', label: 'Homme'},
	{value: 'other', label: 'Autre'}
]);

// Full name field with a custom required message in both site languages.
// datetime-local value one week ahead, so it always satisfies the "today" lower bound.
const nextWeekAtTen = (): string => {
	const date = new Date();
	date.setDate(date.getDate() + 7);
	return `${date.toISOString().slice(0, 10)}T10:00`;
};

// The first field of each form carries a help text in both languages, in the shape
// the editor stores (a paragraph), so the help-text look can be judged on every form.
const fullNameField = (): JahiaNode => {
	const node = getInputTextNode({name: 'fullName', title: 'Full name', required: true});
	node.properties.push(
		{name: 'helpText', value: '<p>As written on your identity document.</p>', language: 'en'},
		{name: 'helpText', value: '<p>Tel qu\'il figure sur votre pièce d\'identité.</p>', language: 'fr'},
		{name: 'msgValueMissing', value: 'Please fill in your full name', language: 'en'},
		{name: 'msgValueMissing', value: 'Merci de renseigner votre nom complet', language: 'fr'}
	);
	return node;
};

const firstNameField = (): JahiaNode => {
	const node = getInputTextNode({name: 'firstName', title: 'First name', required: true});
	node.properties.push(
		{name: 'helpText', value: '<p>As written on your identity document.</p>', language: 'en'},
		{name: 'helpText', value: '<p>Tel qu\'il figure sur votre pièce d\'identité.</p>', language: 'fr'},
		{name: 'msgValueMissing', value: 'Please fill in your first name', language: 'en'},
		{name: 'msgValueMissing', value: 'Merci de renseigner votre prénom', language: 'fr'}
	);
	return node;
};

// Given and family name rather than one "full name" field: a visitor profile holds them apart, so
// this is the form that exercises a mapping field by field.
const lastNameField = (): JahiaNode => {
	const node = getInputTextNode({name: 'lastName', title: 'Last name', required: true});
	node.properties.push(
		{name: 'msgValueMissing', value: 'Please fill in your last name', language: 'en'},
		{name: 'msgValueMissing', value: 'Merci de renseigner votre nom', language: 'fr'}
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
		// The mapping mixin is registered only when formidable-jexperience-engine is deployed (and it resolves
		// only with jExperience present); a missing type answers a GraphQL error, which cy.apollo reports
		// rather than throws, so the flag is false and the playground goes on without mappings.
		cy.apollo({query: gql`query jExperienceMappingMixin { jcr { nodeTypeByName(name: "${JXP_MAPPING_MIXIN}") { name } } }`})
			.then((response: {errors?: unknown; data?: {jcr?: {nodeTypeByName?: {name?: string} | null}}}) => {
				jExperienceAvailable = !response.errors && Boolean(response.data?.jcr?.nodeTypeByName?.name);
				cy.log(jExperienceAvailable
					? 'formidable-jexperience-engine present: the simple and complete forms map their fields to visitor profile properties'
					: 'formidable-jexperience-engine absent: forms provisioned without visitor profile mappings');
			});
		cy.then(() => {
			if (jExperienceAvailable) deleteMappingRulesOfTheSite();
		});
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
		cy.then(() => {
			if (jExperienceAvailable) {
				// Both halves of the render filter's site check: jExperience among the site's modules, and ours.
				enableModule('jexperience', FORMIDABLE_TEST_SITE.key);
				enableModule('formidable-jexperience-engine', FORMIDABLE_TEST_SITE.key);
				ensureCustomProfileProperties();
			}
		});
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
				// The visitor profile mapping, on the fields jCustomer knows by default; the two strategies and the
				// prefill switch are both represented, and the free-text message stays out of the profile.
				mappedTo(withFrench(firstNameField(), [{name: 'jcr:title', value: 'Prénom'}]), 'firstName', {prefill: true}),
				mappedTo(withFrench(lastNameField(), [{name: 'jcr:title', value: 'Nom'}]), 'lastName', {prefill: true}),
				mappedTo(withFrench(getInputEmailNode({name: 'email', title: 'Email', required: true}), [{name: 'jcr:title', value: 'Email'}]), 'email', {strategy: 'setIfMissing', prefill: true}),
				sensitive(withFrench(getTextareaNode({name: 'message', title: 'Message'}), [{name: 'jcr:title', value: 'Message'}])),
				contactChannelSelect(),
				mappedTo(phoneNumberField(), 'phoneNumber')
			],
			undefined,
			undefined,
			{
				actions: [saveToJcrAction()],
				...LIST_TITLES,
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

	// The three steps of the multi-step playground, shared by its plain and styled variants.
	const multiStepNodes = (): JahiaNode[] => [
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
						withFrench(getRadioNode(RADIO_GROUP), [{name: 'jcr:title', value: 'Mode de livraison'}, FR_DELIVERY_OPTIONS]),
						// The delivery method drives what follows: the pickup location for "pickup",
						// the address fieldset for the other methods — a rule on a field and a rule
						// on a container, in the same step as their source.
						pickupLocationField(),
						// A fieldset inside a step: the deepest authoring level (list > step > fieldset > field).
						withLogics(withFrench(getFieldsetNode({
							name: 'deliveryAddress',
							title: 'Delivery address',
							children: [
								withFrench(getInputTextNode({name: 'street', title: 'Street'}), [{name: 'jcr:title', value: 'Rue'}]),
								withFrench(getInputTextNode({name: 'postalCode', title: 'Postal code'}), [{name: 'jcr:title', value: 'Code postal'}]),
								withFrench(getInputTextNode({name: 'city', title: 'City'}), [{name: 'jcr:title', value: 'Ville'}])
							]
						}), [{name: 'jcr:title', value: 'Adresse de livraison'}]), DELIVERY_ADDRESS_RULE)
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
	];

	const provisionMultiStepForm = (name: string, title: string, frTitle: string, extraProperties: Array<{name: string; value: string; language?: string}> = []) =>
		createPublishedLiveFormPage(
			name,
			title,
			multiStepNodes(),
			undefined,
			undefined,
			// Both site languages: the actions get localized default titles at creation,
			// so an en-only publication would leave their fr translation unpublished.
			{
				actions: [saveToJcrAction()],
				...LIST_TITLES,
				properties: [{name: 'jcr:title', value: frTitle, language: 'fr'}, ...extraProperties],
				pageProperties: [{name: 'jcr:title', value: frTitle, language: 'fr'}],
				publishLanguages: ['en', 'fr']
			}
		);

	it('provisions a page carrying two forms', () => {
		// The case a page with one form never shows: two forms side by side, each with its own results and
		// its own mapping. It is also what the jExperience integration has to get right — one configuration
		// block per form, one tracking script for the page. The simple form is referenced rather than copied,
		// so the page also shows one form living in two places, which is how an author uses a reference.
		createFormNode(
			'playground-newsletter',
			'Playground - Newsletter',
			[
				withFrench(getInputEmailNode({name: 'email', title: 'Email', required: true}), [{name: 'jcr:title', value: 'Email'}]),
				withFrench(getInputTextNode({name: 'firstName', title: 'First name'}), [{name: 'jcr:title', value: 'Prénom'}])
			],
			{
				actions: [saveToJcrAction()],
				...LIST_TITLES,
				properties: [{name: 'jcr:title', value: 'Playground - Lettre d\'information', language: 'fr'}]
			}
		).then(response => {
			const newsletterId: string = response.data.jcr.addNode.uuid;

			getNodeByPath(`${CONTENT_PATH}/playground-simple`).then(simpleResponse => {
				const simpleId: string = simpleResponse.data.jcr.nodeByPath.uuid;

				addNode({
					parentPathOrId: SITE_HOME_PATH,
					name: 'playground-two-forms-page',
					primaryNodeType: 'jnt:page',
					properties: [
						{name: 'jcr:title', value: 'Playground - Two forms on one page', language: 'en'},
						{name: 'jcr:title', value: 'Playground - Deux formulaires sur une page', language: 'fr'},
						{name: 'j:templateName', value: 'simple'}
					],
					children: [
						{
							name: 'pagecontent',
							primaryNodeType: 'jnt:contentList',
							properties: [],
							children: [
								{name: 'main-resource-display', primaryNodeType: 'jnt:mainResourceDisplay', properties: []},
								{
									name: 'playground-simple-reference',
									primaryNodeType: 'fmdb:formReference',
									properties: [{name: 'j:node', value: simpleId, type: 'WEAKREFERENCE'}]
								},
								{
									name: 'playground-newsletter-reference',
									primaryNodeType: 'fmdb:formReference',
									properties: [{name: 'j:node', value: newsletterId, type: 'WEAKREFERENCE'}]
								}
							]
						}
					]
				});

				publishAndWaitJobEnding(`${CONTENT_PATH}/playground-newsletter`, ['en', 'fr']);
				publishAndWaitJobEnding(`${SITE_HOME_PATH}/playground-two-forms-page`, ['en', 'fr']);
				cy.log(`Two forms on one page: /en/sites/${FORMIDABLE_TEST_SITE.key}/home/playground-two-forms-page.html`);
			});
		});
	});

	it('provisions the multi-step form', () => {
		provisionMultiStepForm('playground-steps', 'Playground - Multi-step form', 'Playground - Formulaire multi-étapes')
			.then(({livePath}) => cy.log(`Multi-step form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
	});

	it('provisions the styled multi-step form (same steps, with the sample theme)', () => {
		// A business stylesheet on a multi-step form: the authoring UI must stay readable
		// on top of it. The theme lives in the form's own css property, like the complete form's.
		cy.readFile(COMPLETE_FORM_THEME_PATH).then((themeCss: string) => {
			provisionMultiStepForm('playground-steps-styled', 'Playground - Multi-step form (styled)', 'Playground - Formulaire multi-étapes (stylé)', [{name: 'css', value: themeCss}])
				.then(({livePath}) => cy.log(`Styled multi-step form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
		});
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
						// placeholder and list are i18n as well: without a French value the
						// field loses its example and its suggestion list in that language.
						// An employee code is the kind of value that must never reach a visitor profile: the sensitive flag.
						sensitive(withFrench(getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined, helpText: '<p>Two capital letters, a dash, four digits: <strong>AB-1234</strong>.</p>'}), [
							{name: 'jcr:title', value: 'Code employé'},
							{name: 'helpText', value: '<p>Deux lettres majuscules, un tiret, quatre chiffres : <strong>AB-1234</strong>.</p>'},
							{name: 'placeholder', value: 'AB-1234'},
							{name: 'list', values: ['AB-1234', 'CD-5678']}
						])),
						mappedTo(withFrench(getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}), [
							{name: 'jcr:title', value: 'Email de contact'},
							{name: 'placeholder', value: 'Saisissez votre adresse e-mail'}
						]), 'email', {strategy: 'setIfMissing'}),
						// A birth date cannot be after the submission day; the appointment
						// cannot be before it — the relative bound modes showcased live.
						mappedTo(withFrench(getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined, max: undefined, maxBoundMode: 'today'}), [{name: 'jcr:title', value: 'Date de naissance'}]), 'birthDate', {prefill: true}),
						withFrench(getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined, min: undefined, minBoundMode: 'today'}), [{name: 'jcr:title', value: 'Rendez-vous'}]),
						withFrench(getInputColorNode(INPUT_COLOR_COMPLETE), [{name: 'jcr:title', value: 'Choisissez votre couleur préférée'}]),
						// The group and the switch feed the two properties the playground adds to jCustomer (see above).
						mappedTo(withFrench(getCheckboxNode(CHECKBOX_GROUP_COMPLETE), [
							{name: 'jcr:title', value: 'Centres d\'intérêt requis'},
							frOptions([
								{value: 'reading', label: 'Lecture'},
								{value: 'sports', label: 'Sport', selected: true},
								{value: 'music', label: 'Musique'}
							])
						]), 'formidableInterests'),
						mappedTo(withFrench(getSwitchNode({name: 'newsletter', title: 'Newsletter opt-in', onLabel: 'Yes', offLabel: 'No'}), [
							{name: 'jcr:title', value: 'Lettre d\'information'},
							{name: 'onLabel', value: 'Oui'},
							{name: 'offLabel', value: 'Non'}
						]), 'formidableOptIn'),
						withFrench(getRadioNode(RADIO_GROUP), [{name: 'jcr:title', value: 'Mode de livraison'}, FR_DELIVERY_OPTIONS]),
						pickupLocationField(),
						// The two shapes the profile mapping had no field for: a single choice to a string property,
						// a number to an integer one.
						mappedTo(withFrench(getRadioNode(GENDER_RADIO), [{name: 'jcr:title', value: 'Genre'}, FR_GENDER_OPTIONS]), 'gender', {prefill: true}),
						mappedTo(withFrench(getInputNumberNode({name: 'kids', title: 'Number of children', minValue: 0, maxValue: 20, step: 1}), [{name: 'jcr:title', value: 'Nombre d\'enfants'}]), 'kids'),
						departmentSelect(),
						withFrench(getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}), [
							{name: 'jcr:title', value: 'Résumé du projet'},
							{name: 'placeholder', value: 'Décrivez le projet'}
						]),
						withFrench(getInputFileNode(INPUT_FILE_MULTIPLE), [{name: 'jcr:title', value: 'Pièces jointes'}]),
						// The sourced select showcases the empty-option label: the field starts
						// empty and its native required validation is exercisable on the site.
						// The countries source holds ISO codes, which is what jCustomer's countryName expects.
						mappedTo(withFrench(
							withEnglish(
								getSourcedChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'country', title: 'Country (sourced: countries)', sourceKey: 'countries'}),
								[{name: 'optionsEmptyLabel', value: 'Select a country…'}]
							),
							[
								{name: 'jcr:title', value: 'Pays (source : countries)'},
								{name: 'optionsEmptyLabel', value: 'Sélectionnez un pays…'}
							]
						), 'countryName'),
						withFrench(getSourcedChoiceFieldNode({primaryNodeType: 'fmdb:radio', name: 'tvType', title: 'TV type (sourced: static screen-type list)', sourceKey: 'tv'}), [{name: 'jcr:title', value: 'Type de TV (source : liste statique de types d\'écrans)'}]),
						withFrench(getCategoryChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'tvCategory', title: 'TV category (category mode, multiple select)', rootCategoryUuid: tvCategoryUuid, multiple: true}), [{name: 'jcr:title', value: 'Catégorie TV (mode catégorie, sélection multiple)'}]),
						withFrench(getContentChoiceFieldNode({primaryNodeType: 'fmdb:select', name: 'agency', title: 'Agency (content mode: texts under contents/agencies)', rootNodeUuid: agenciesRootUuid, nodeType: 'jnt:text'}), [{name: 'jcr:title', value: 'Agence (mode contenu : textes sous contents/agencies)'}])
					],
					undefined,
					undefined,
					{
						actions: [saveToJcrAction()],
						...LIST_TITLES,
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
						helpText: '<p>Two of the three flavours are left untranslated on purpose.</p>',
						options: [
							{value: 'vanilla', label: 'Vanilla'},
							{value: 'chocolate', label: 'Chocolate'},
							{value: 'pistachio', label: 'Pistachio'}
						]
					}),
					[
						{name: 'jcr:title', value: 'Parfum'},
						{name: 'helpText', value: '<p>Deux des trois parfums sont volontairement laissés sans traduction.</p>'},
						FR_FLAVOR_OPTIONS
					]
				)
			],
			undefined,
			undefined,
			{
				actions: [saveToJcrAction()],
				...LIST_TITLES,
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

	it('submits sample entries so the results screens have something to show', () => {
		const liveFormPath = (formName: string) => `home/${formName}-page.html`;

		// Simple contact form: three visitors, one of them in French. Typed values stay
		// ASCII: realType (cypress-real-events) rejects accented characters.
		[
			{lang: 'en', firstName: 'Alice', lastName: 'Martin', email: 'alice.martin@example.com', message: 'Could you send me the brochure of your spring collection?'},
			{lang: 'en', firstName: 'Bob', lastName: 'Dupont', email: 'bob.dupont@example.com', message: 'The store in Lyon was closed on Monday, is that expected?', phone: '33612345678'},
			{lang: 'fr', firstName: 'Chloe', lastName: 'Bernard', email: 'chloe.bernard@example.com', message: 'Bonjour, je souhaite recevoir le catalogue par courrier.'}
		].forEach(({lang, firstName, lastName, email, message, phone}) => {
			const form = visitLiveForm(liveFormPath('playground-simple'), lang);
			form.getTextInput('firstName').type(firstName);
			form.getTextInput('lastName').type(lastName);
			form.getEmailInput('email').type(email);
			form.getTextarea('message').type(message);
			if (phone) {
				// Asking for a call reveals the conditional phone number field.
				form.getSelectInput('contactChannel').selectByValue('phone');
				form.getTextInput('phoneNumber').type(phone);
			}

			form.submit();
			form.waitForSubmit();
		});

		// Multi-step form: two visitors going through the three steps.
		[
			{fullName: 'Diane Roux', email: 'diane.roux@example.com', department: 'Engineering', delivery: 'Standard', comment: 'Looking forward to the next release.'},
			{fullName: 'Ethan Moreau', email: 'ethan.moreau@example.com', department: 'Sales', delivery: 'Express', comment: 'Please call me back in the afternoon.'}
		].forEach(({fullName, email, department, delivery, comment}) => {
			const form = visitLiveForm(liveFormPath('playground-steps'));
			form.getTextInput('fullName').type(fullName);
			form.getEmailInput('email').type(email);
			form.nextStep();
			form.getSelectInput('department').select(department);
			form.getRadioGroup('deliveryMethod').select(delivery);
			form.nextStep();
			form.getTextarea('comment').type(comment);
			form.submit();
			form.waitForSubmit();
		});

		// Complete form: every field type, with the PDF and CSV fixtures as attachments.
		// The third entry picks "Pickup", which reveals the conditional pickup location.
		[
			{code: 'AB-1234', email: 'fanny.girard@example.com', birth: '1988-04-12', color: '#ff5733', interests: ['Sports', 'Music'], delivery: 'Express', pickup: null, gender: 'Female', kids: '2', country: 'FR', newsletter: true, department: 'Engineering', summary: 'A new intranet for the engineering team, with a form for incident reports.', files: ['cypress/fixtures/files/document.pdf']},
			{code: 'CD-5678', email: 'gabriel.lefevre@example.com', birth: '1975-11-30', color: '#3366cc', interests: ['Reading'], delivery: 'Standard', pickup: null, gender: 'Male', kids: '0', country: 'DE', newsletter: false, department: 'Sales', summary: 'Quarterly sales dashboard with an export of the leads collected on the site.', files: ['cypress/fixtures/files/sample.csv']},
			{code: 'EF-9012', email: 'helene.petit@example.com', birth: '1992-07-08', color: '#2e8b57', interests: ['Sports'], delivery: 'Pickup', pickup: 'Paris - Rue de Rivoli', gender: 'Other', kids: '3', country: 'CH', newsletter: true, department: 'Support', summary: 'Support knowledge base migration, including the attached inventory and specification.', files: ['cypress/fixtures/files/document.pdf', 'cypress/fixtures/files/sample.csv']}
		].forEach(({code, email, birth, color, interests, delivery, pickup, gender, kids, country, newsletter, department, summary, files}) => {
			const form = visitLiveForm(liveFormPath('playground-complete'));
			form.getTextInput(INPUT_TEXT_COMPLETE.name!).type(code);
			form.getEmailInput(INPUT_EMAIL_COMPLETE.name!).type(email);
			form.getDateInput(INPUT_DATE_COMPLETE.name!).setDate(birth);
			// The appointment cannot be before the submission day (relative "today" bound).
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name!).setDateTime(nextWeekAtTen());
			form.getColorInput(INPUT_COLOR_COMPLETE.name!).setColor(color);
			form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!).uncheckAll().checkByLabels(interests);
			form.getRadioGroup(RADIO_GROUP.name!).select(delivery);
			if (pickup) {
				form.getTextInput('pickupLocation').type(pickup);
			}

			form.getRadioGroup('gender').select(gender);
			form.getNumberInput('kids').type(kids);
			if (newsletter) {
				// the switch's track covers its input, as spec 214 knows: force the check
				form.getCheckbox('newsletter').getInput().check({force: true});
			}

			form.getSelectInput('country').selectByValue(country);
			form.getSelectInput('department').select(department);
			form.getTextarea(TEXTAREA_COMPLETE.name!).type(summary);
			form.getFileInput(INPUT_FILE_MULTIPLE.name!).attachFile(files).shouldHaveSelectedFileCount(files.length);
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('adds an unpublished draft agency, absent from the live options', () => {
		// Created after the complete form is published: publishing a form
		// publishes its referenced options root with its subtree, so an earlier
		// draft would have been published along.
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('draft', 'Draft agency', 'Agence brouillon')});
	});
});

import {enableModule} from '@jahia/cypress';
import gql from 'graphql-tag';
import {ConditionalLogicEditor} from '../../page-object';
import {
	createConditionalLogicForm,
	FORMIDABLE_TEST_SITE,
	getConditionalLogicNode,
	getInputTextNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {DIRECT_SUBMIT_PATH} from '../../support/constants';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A property of the visitor's jExperience profile as a conditional-logic source (issue #422). The
 * value is read in the browser from the context jExperience's tracker loads, so these specs need a
 * Jahia with formidable-jexperience-engine deployed and a jCustomer it reaches; without one they are
 * skipped, as the CI stack has none.
 */
describe('Form logic - 514 jExperience profile property source', () => {
	useFormidableSite();

	const PROXY = `/modules/jexperience/proxy/${FORMIDABLE_TEST_SITE.key}/cxs`;
	const PROFILE_SOURCE = 'jExperience profile property';

	before(function () {
		// before the suite's own beforeEach logs in: the checks below need root
		cy.login();
		// The mapping mixin is declared by formidable-jexperience-engine, which resolves only with jExperience present
		cy.apollo({query: gql`query jExperienceEngine { jcr { nodeTypeByName(name: "fmdbmix:jExperienceProfileMapping") { name } } }`})
			.then((response: {errors?: unknown; data?: {jcr?: {nodeTypeByName?: {name?: string} | null}}}) => {
				if (response.errors || !response.data?.jcr?.nodeTypeByName?.name) {
					cy.log('formidable-jexperience-engine absent: skipped');
					this.skip();
				}
			});
		enableModule('jexperience', FORMIDABLE_TEST_SITE.key);
		enableModule('formidable-jexperience-engine', FORMIDABLE_TEST_SITE.key);
		cy.request({url: `${PROXY}/profiles/properties/targets/profiles`, failOnStatusCode: false}).then(response => {
			if (response.status !== 200) {
				cy.log(`jCustomer not reachable through jExperience (HTTP ${response.status}): skipped`);
				this.skip();
			}
		});
	});

	const wrapperShouldBeHidden = (fieldName: string, hidden: boolean) =>
		cy.get(`[data-fmdb-node-name="${fieldName}"]`).should('have.attr', 'data-fmdb-logic-hidden', hidden ? 'true' : 'false');

	/**
	 * Waits for the context the tracker loaded, read through the integration's own gate. jExperience's
	 * tracker sends no context request for a headless user agent — Cypress's default, `HeadlessChrome` —
	 * and then starts with no profile: said first, or the wait fails with nothing to go on.
	 */
	const trackerReady = () => {
		cy.window().its('wemLoaded').should('equal', true);
		cy.window().then(win => {
			const context = (win as unknown as {wem?: {getLoadedContext?: () => {profileId?: string} | undefined}}).wem?.getLoadedContext?.();
			expect(context?.profileId, 'the visitor\'s jExperience context — the tracker asks none for a headless user agent ('
				+ `${win.navigator.userAgent}): run with CYPRESS_userAgent set to a browser one, as the playground does`).to.be.a('string');
		});
		cy.window().its('formidableJxp').invoke('trackerReady').should('equal', true);
	};

	// The visitor is a guest, its cookies cleared to be a new one: jExperience's proxy is reached as root
	const asRoot = () => ({user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD') ?? 'root1234'});

	/** Writes properties into the profile of the visitor this browser is, in jCustomer. */
	const setProfileProperties = (properties: Record<string, string>) =>
		cy.window().then(win => {
			const profileId = (win as unknown as {wem: {getLoadedContext: () => {profileId: string}}}).wem.getLoadedContext().profileId;
			cy.request({url: `${PROXY}/profiles/${profileId}`, auth: asRoot()}).then(({body}) => {
				cy.request({method: 'POST', url: `${PROXY}/profiles`, auth: asRoot(), body: {...body, properties: {...body.properties, ...properties}}});
			});
		});

	/**
	 * Visits the page until the field shows the expected state: jCustomer indexes a saved profile
	 * asynchronously, so the context of the very next request may still carry the previous value.
	 */
	const visitUntil = (livePath: string, fieldName: string, hidden: boolean, attempt = 1): void => {
		visitLiveForm(livePath);
		trackerReady();
		cy.get(`[data-fmdb-node-name="${fieldName}"]`).invoke('attr', 'data-fmdb-logic-hidden').then(state => {
			if (state !== (hidden ? 'true' : 'false') && attempt < 5) {
				// eslint-disable-next-line cypress/no-unnecessary-waiting -- jCustomer's refresh, between two visits
				cy.wait(1000);
				visitUntil(livePath, fieldName, hidden, attempt + 1);
			}
		});
		wrapperShouldBeHidden(fieldName, hidden);
	};

	const profileRule = (logicId: string, operator: string, value?: string) =>
		JSON.stringify({logicId, sourceType: 'jexperience:profile', property: 'country', operator, value});

	const liveForm = (name: string, rules: Record<string, string>) =>
		createPublishedLiveFormPage(`${name}-${Date.now()}`, name, [
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			...Object.entries(rules).map(([field, rule]) => ({
				...getInputTextNode({name: field, title: field}),
				properties: [
					{name: 'jcr:title', value: field, language: 'en'},
					{name: 'logics', values: [rule]}
				]
			}))
		]);

	it('stores a profile property rule with no source-field binding', () => {
		createConditionalLogicForm(`${Date.now()}-jxp`).then(({targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSourceType(0, PROFILE_SOURCE);
			// offered with the operators of every source outside the form
			logicField.openProviderOperatorDropdown(0);
			logicField.menuShouldHaveItems(['equals', 'does not equal', 'contains', 'is defined', 'is not defined']);
			logicField.closeMenu();
			// 'is defined' needs no value, so the only text input is the property name
			logicField.selectProviderOperator(0, 'is defined');
			logicField.ruleShouldHaveTextInputCount(0, 1);
			logicField.typeTextValue(0, 'country');
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node =>
				(node.properties?.find(property => property.name === 'logics')?.values ?? []).length === 1));

			getConditionalLogicNode(targetPath).then(node => {
				const storedRule = parseStoredLogicRule(node.properties!.find(property => property.name === 'logics')!.values![0]);

				expect(storedRule.sourceType).to.equal('jexperience:profile');
				expect(storedRule.property).to.equal('country');
				expect(storedRule.operator).to.equal('exists');
				expect(storedRule.value).to.equal(undefined);
				expect(storedRule.sourceFieldName).to.equal(undefined);
				expect(node.descendant?.children?.nodes ?? []).to.have.length(0);
			});
		});
	});

	it('shows the field only while the profile property has the expected value', () => {
		liveForm('jxp-country', {companySize: profileRule('rt-jxp-fr', 'equals', 'FR')}).then(({livePath}) => {
			cy.clearCookies();
			visitLiveForm(livePath);
			trackerReady();
			// a new visitor: the profile holds no country yet
			wrapperShouldBeHidden('companySize', true);

			setProfileProperties({country: 'FR'});
			visitUntil(livePath, 'companySize', false);

			setProfileProperties({country: 'DE'});
			visitUntil(livePath, 'companySize', true);
		});
	});

	it('keeps the field hidden while the tracker has no context, even on "is not defined"', () => {
		liveForm('jxp-blocked', {noCountry: profileRule('rt-jxp-none', 'notExists')}).then(({livePath}) => {
			cy.clearCookies();
			// the tracker loaded: this new visitor's profile holds no country
			visitLiveForm(livePath);
			trackerReady();
			wrapperShouldBeHidden('noCountry', false);

			// the context request cut off, as a consent tool or an ad blocker does: the tracker starts in its
			// fallback mode with no profile, and the source cannot be read. The tracker itself is inline in
			// the page, so its context request — the proxy's context.json — is the one to cut.
			cy.intercept('**/context.json*', {forceNetworkError: true});
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submission');
			cy.clearCookies();
			const form = visitLiveForm(livePath);
			cy.window().its('wemLoaded').should('equal', true);
			cy.window().its('formidableJxp').invoke('trackerReady').should('equal', false);
			wrapperShouldBeHidden('noCountry', true);

			// nor does the browser declare anything for it: the server fails the rule closed too
			form.getTextInput('fullname').type('Blocked Visitor');
			form.submit();
			cy.wait('@submission').then(({request}) => {
				const header = request.headers['x-formidable-logic-state'];
				const declared = typeof header === 'string' ? JSON.parse(atob(header)) : {providers: {}};
				expect(declared.providers).not.to.have.property('jexperience:profile');
			});
		});
	});
});

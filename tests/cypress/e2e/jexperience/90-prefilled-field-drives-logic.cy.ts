import {enableModule} from '@jahia/cypress';
import {FORMIDABLE_TEST_SITE, getInputTextNode, getSelectNode, JahiaNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A field that the jExperience profile prefills is the source of a field rule (#422), on a real jExperience and
 * jCustomer: the jExperience runner of the CI (.github/workflows/jexperience-tests.yml), never the shards of a PR.
 * A visitor's submission is what writes the profile, through the mapping rule of the form; the next visit reads it
 * back. jCustomer processes the event asynchronously, so a visit after a submission is retried until the profile
 * answers.
 */
describe('jExperience - 90 a prefilled field drives the conditional logic of another field', () => {
	useFormidableSite();

	const JXP_MAPPING_MIXIN = 'fmdbmix:jExperienceProfileMapping';
	// A cold jCustomer takes longer than the default command timeout to give the tracker its context.
	const CONTEXT_TIMEOUT_MS = 30000;
	const PROFILE_ATTEMPTS = 15;
	const PROFILE_ATTEMPT_PAUSE_MS = 2000;

	const mappedTo = (node: JahiaNode, profileProperty: string, strategy: 'alwaysSet' | 'setIfMissing', prefillThen?: 'hidden'): JahiaNode => ({
		...node,
		mixins: [...(node.mixins ?? []), JXP_MAPPING_MIXIN],
		properties: [
			...node.properties,
			{name: 'jExperienceProfileProperty', value: profileProperty},
			{name: 'jExperienceSetStrategy', value: strategy},
			{name: 'jExperiencePrefill', value: 'true', type: 'BOOLEAN'},
			...(prefillThen ? [{name: 'jExperiencePrefillThen', value: prefillThen}] : [])
		]
	});

	// The country holds ISO codes, which is what jCustomer's countryName expects.
	const countrySelect = () => getSelectNode({
		name: 'country',
		title: 'Country',
		options: [
			{value: '', label: 'Select a country', selected: false},
			{value: 'FR', label: 'France', selected: false},
			{value: 'DE', label: 'Germany', selected: false}
		]
	});

	const companySizeRule = JSON.stringify({
		logicId: 'jxp-company-size',
		sourceFieldName: 'country',
		sourceFieldType: 'fmdb:select',
		valueKind: 'choice',
		operator: 'in',
		values: ['FR']
	});

	let hiddenCountryPath: string;
	let editableCountryPath: string;

	before(() => {
		// Both halves of the render filter's site check: jExperience among the site's modules, and ours.
		enableModule('jexperience', FORMIDABLE_TEST_SITE.key);
		enableModule('formidable-jexperience-engine', FORMIDABLE_TEST_SITE.key);

		const stamp = Date.now();
		// The country is hidden once the profile knows it, and the company size is asked for France only.
		createPublishedLiveFormPage(`jxp-logic-hidden-${stamp}`, 'Prefilled country, hidden', [
			mappedTo(countrySelect(), 'countryName', 'setIfMissing', 'hidden'),
			{
				...getInputTextNode({name: 'companySize', title: 'Company size'}),
				properties: [
					{name: 'jcr:title', value: 'Company size', language: 'en'},
					{name: 'logics', values: [companySizeRule]}
				]
			}
		]).then(({livePath}) => {
			hiddenCountryPath = livePath;
		});
		// The country stays editable, and the first name is written on every submission: once the profile has the
		// new first name, it has processed the event that carried the country too.
		createPublishedLiveFormPage(`jxp-logic-editable-${stamp}`, 'Prefilled country, editable', [
			mappedTo(getInputTextNode({name: 'firstName', title: 'First name'}), 'firstName', 'alwaysSet'),
			mappedTo(countrySelect(), 'countryName', 'setIfMissing')
		]).then(({livePath}) => {
			editableCountryPath = livePath;
		});
	});

	const countryWrapper = () => cy.get('[data-fmdb-node-name="country"]');
	const countryControl = () => cy.get('select[name="country"]');

	// Hydration has run once the attribute is present: before that, every field carrying a rule is hidden.
	const companySizeShouldBeHidden = (hidden: boolean) =>
		cy.get('[data-fmdb-node-name="companySize"]').should('have.attr', 'data-fmdb-logic-hidden', hidden ? 'true' : 'false');

	/** A visitor jCustomer has never seen: no Jahia session, no profile cookie. */
	const newVisitor = () => {
		cy.logout();
		cy.clearAllCookies();
	};

	/** The page of the form, once the tracker has its context: the prefill runs then, or never. */
	const visitWithContext = (livePath: string) => {
		const form = visitLiveForm(livePath);
		cy.window()
			.its('formidableJxp', {timeout: CONTEXT_TIMEOUT_MS})
			.invoke('trackerReady')
			.should('eq', true);
		return form;
	};

	/** Visits the page until the profile answers what `ready` reads on it, the submission's event being processed. */
	const visitUntil = (livePath: string, ready: ($body: JQuery<HTMLElement>) => boolean, what: string, attempt = 1): void => {
		visitWithContext(livePath);
		cy.get('body').then($body => {
			if (ready($body)) {
				return;
			}

			if (attempt >= PROFILE_ATTEMPTS) {
				throw new Error(`the profile still does not answer ${what} after ${attempt} visits`);
			}

			// eslint-disable-next-line cypress/no-unnecessary-waiting -- a pause between two visits, not a wait for an element
			cy.wait(PROFILE_ATTEMPT_PAUSE_MS);
			visitUntil(livePath, ready, what, attempt + 1);
		});
	};

	const countryPrefilledHidden = ($body: JQuery<HTMLElement>) =>
		$body.find('[data-fmdb-node-name="country"]').attr('data-fmdb-prefilled') === 'hidden';

	/** A new visitor whose profile ends up with this country, through a submission of the form that hides it. */
	const visitorFrom = (country: 'FR' | 'DE') => {
		newVisitor();
		const form = visitWithContext(hiddenCountryPath);
		form.getSelectInput('country').selectByValue(country);
		form.submit();
		form.waitForSubmit();
		visitUntil(hiddenCountryPath, countryPrefilledHidden, `countryName ${country}`);
	};

	it('leaves the field to a visitor the profile knows nothing of, and the target follows the answer', () => {
		newVisitor();
		visitWithContext(hiddenCountryPath);

		countryControl().should('have.value', '').and('be.visible');
		countryWrapper().should('not.have.attr', 'data-fmdb-prefilled');
		companySizeShouldBeHidden(true);

		countryControl().select('FR');
		companySizeShouldBeHidden(false);
		countryControl().select('DE');
		companySizeShouldBeHidden(true);
	});

	it('prefills and hides the field when the profile says France, shows the target, and still submits the value', () => {
		visitorFrom('FR');

		countryControl().should('have.value', 'FR').and('not.be.visible').and('not.be.disabled');
		countryWrapper().should('have.attr', 'data-fmdb-prefilled', 'hidden');
		companySizeShouldBeHidden(false);
		// hidden by the prefill, not by the logic: the value is posted, and the server evaluates the rule with it
		cy.get('form.fmdb-form').then($form => {
			expect(new FormData($form[0] as HTMLFormElement).get('country')).to.equal('FR');
		});
	});

	it('keeps the target hidden when the profile names another country', () => {
		visitorFrom('DE');

		countryControl().should('have.value', 'DE');
		companySizeShouldBeHidden(true);
	});

	it('keeps the country the profile knows when a setIfMissing field submits another one', () => {
		visitorFrom('FR');

		const firstName = `Ada-${Date.now()}`;
		const form = visitWithContext(editableCountryPath);
		countryControl().should('have.value', 'FR');
		form.getTextInput('firstName').clear().type(firstName);
		countryControl().select('DE');
		form.submit();
		form.waitForSubmit();

		visitUntil(
			editableCountryPath,
			$body => $body.find('input[name="firstName"]').val() === firstName,
			`firstName ${firstName}`
		);
		countryControl().should('have.value', 'FR');
	});
});

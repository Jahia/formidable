import {createPublishedLiveFormPage, getInputTextNode} from '../../support/fixtures';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	useFormidableSite,
	withSameOriginHeaders
} from './support';

/**
 * Submission coherence for conditional logic. The server rejects a value submitted for a
 * field it can PROVE was hidden — from submitted values for field-sourced rules, or from
 * the provider state the browser itself declared in the X-Formidable-Logic-State header.
 * An unprovable verdict (provider rule without a declaration) keeps the historical
 * fail-safe: value kept, required skipped. This is tamper evidence, not enforcement: the
 * declaration is forgeable, but one single declared state backs every rule reading it.
 */
describe('Security - 42 Conditional logic coherence', () => {
	useFormidableSite();

	const LOGIC_STATE_HEADER = 'X-Formidable-Logic-State';

	const declarationHeader = (providers: Record<string, Record<string, string | null>>) =>
		btoa(JSON.stringify({v: 1, providers}));

	const fieldGateRule = JSON.stringify({
		logicId: 'coh-field',
		sourceFieldName: 'gate',
		sourceFieldType: 'fmdb:inputText',
		valueKind: 'text',
		operator: 'equals',
		value: 'open'
	});

	const cookieGateRule = JSON.stringify({
		logicId: 'coh-cookie',
		sourceType: 'cookie',
		cookie: 'consent',
		operator: 'exists'
	});

	const gatedForm = (suffix: string, rule: string, gatedFieldExtras: object = {}) => {
		// Append the rule to the fixture-built properties: replacing them would silently
		// drop what the extras produced (required, constraints…).
		const gated = getInputTextNode({name: 'details', title: 'details', ...gatedFieldExtras});
		gated.properties = [...(gated.properties ?? []), {name: 'logics', values: [rule]}];

		return createPublishedLiveFormPage(
			`coherence-${suffix}-${Date.now()}`,
			`Coherence form ${suffix}`,
			[getInputTextNode({name: 'gate', title: 'gate'}), gated]
		);
	};

	it('rejects a value for a field provably hidden by a field-sourced rule', () => {
		gatedForm('field-tamper', fieldGateRule).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {gate: 'closed', details: 'smuggled'},
				headers: withSameOriginHeaders()
			}).then(response => expectErrorResponse(response, 400, 'FMDB-013'));
		});
	});

	it('accepts the same value when the gate makes the field visible', () => {
		gatedForm('field-honest', fieldGateRule).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {gate: 'open', details: 'legitimate'},
				headers: withSameOriginHeaders()
			}).then(expectSuccessResponse);
		});
	});

	it('keeps the fail-safe for provider-gated values submitted without a declaration', () => {
		// No declaration → the hidden verdict is not a measurement, so the value is kept:
		// exactly the pre-existing behaviour, no regression for non-browser clients.
		gatedForm('provider-failsafe', cookieGateRule).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {details: 'datalayer gated value'},
				headers: withSameOriginHeaders()
			}).then(expectSuccessResponse);
		});
	});

	it('rejects a value contradicting the declared provider state', () => {
		// The submission itself declares the cookie absent, which hides the field: a
		// value for it is incoherent with the submission's own declaration.
		gatedForm('provider-tamper', cookieGateRule).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {details: 'smuggled'},
				headers: withSameOriginHeaders({
					[LOGIC_STATE_HEADER]: declarationHeader({cookie: {consent: null}})
				})
			}).then(response => expectErrorResponse(response, 400, 'FMDB-013'));
		});
	});

	it('re-arms required validation when the declaration shows the field', () => {
		// Historically a required provider-gated field was never enforced. A declaration
		// satisfying the rule makes it visible again, so the missing value is FMDB-010.
		gatedForm('provider-required', cookieGateRule, {required: true}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {gate: 'anything'},
				headers: withSameOriginHeaders({
					[LOGIC_STATE_HEADER]: declarationHeader({cookie: {consent: 'yes'}})
				})
			}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
		});
	});

	it('ignores an unreadable declaration and falls back to the historical behaviour', () => {
		gatedForm('provider-garbage', cookieGateRule).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId,
				fields: {details: 'kept'},
				headers: withSameOriginHeaders({[LOGIC_STATE_HEADER]: '%%% not base64 %%%'})
			}).then(expectSuccessResponse);
		});
	});
});

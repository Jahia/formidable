import {ConditionalLogicEditor} from '../../page-object';
import {createConditionalLogicForm, getConditionalLogicNode, getInputTextNode, parseStoredLogicRule} from '../../support/fixtures';
import {DIRECT_SUBMIT_PATH} from '../../support/constants';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A conditional-logic source that another module registers (docs/adr/0001-extension-modules-register-logic-sources.md):
 * its editor half in jContent's `@jahia/ui-extender` registry, its page half pushed onto
 * `window.formidableLogicSources`. The spec plays that module itself, so it needs no stack of its own:
 * `acme:segment` reads the visitor's segment from a value the test sets on the page.
 */
describe('Form logic - 515 A source another module registers', () => {
	useFormidableSite();

	const SOURCE_ID = 'acme:segment';
	const SOURCE_LABEL = 'Acme segment';

	/** The editor half, as the module's jContent bundle registers it. Labels are plain text: no namespace ships them. */
	const registerEditorHalf = () =>
		cy.window().then(win => {
			const {registry} = (win as unknown as {jahia: {uiExtender: {registry: {add: (type: string, key: string, entry: object) => void}}}}).jahia.uiExtender;
			registry.add('formidableLogicSource', SOURCE_ID, {
				configKey: 'segment',
				labelKey: SOURCE_LABEL,
				configLabelKey: 'Segment',
				configPlaceholderKey: 'tier',
				operators: ['equals', 'notEquals', 'exists', 'notExists'],
				isValidRef: (ref: string) => /^\w+$/.test(ref.trim())
			});
		});

	type AcmePage = Window & {acmeSegments?: Record<string, string>; formidableLogicSources?: {push: (source: object) => unknown}};

	/** The page half, as the module's page script pushes it. */
	const pushPageHalf = (win: AcmePage) => {
		win.formidableLogicSources ??= [] as unknown as {push: (source: object) => unknown};
		win.formidableLogicSources.push({
			id: SOURCE_ID,
			configKey: 'segment',
			read: (ref: string) => win.acmeSegments?.[ref]
		});
	};

	/** Before Formidable's script: the module's script runs first, and the visitor is in the `gold` tier. */
	const pushBeforeFormidable = () =>
		cy.on('window:before:load', win => {
			(win as AcmePage).acmeSegments = {tier: 'gold'};
			pushPageHalf(win as AcmePage);
		});

	/** A form whose `offer` field is required and shown by a rule on the source. */
	const liveForm = (name: string, operator: string, value?: string) =>
		createPublishedLiveFormPage(`${name}-${Date.now()}`, name, [
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			{
				...getInputTextNode({name: 'offer', title: 'offer'}),
				properties: [
					{name: 'jcr:title', value: 'offer', language: 'en'},
					// required, so that a server that judged it visible refuses it empty
					{name: 'required', value: 'true', type: 'BOOLEAN'},
					{name: 'logics', values: [JSON.stringify({logicId: `rt-${name}`, sourceType: SOURCE_ID, segment: 'tier', operator, value})]}
				]
			}
		]);

	const offerWrapper = () => cy.get('[data-fmdb-node-name="offer"]');

	/** The provider state the browser declared at submit time, read from the request. */
	const declaredState = () =>
		cy.wait('@submission').then(({request}) => {
			const header = request.headers['x-formidable-logic-state'];
			return typeof header === 'string' ? JSON.parse(atob(header)) as {providers: Record<string, Record<string, string | null>>} : null;
		});

	it('offers the registered source in the rule editor and stores its rule', () => {
		createConditionalLogicForm(`${Date.now()}-acme`).then(({targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			registerEditorHalf();
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSourceType(0, SOURCE_LABEL);
			// the operators the entry declares, not the five of the built-ins
			logicField.openProviderOperatorDropdown(0);
			logicField.menuShouldHaveItems(['equals', 'does not equal', 'is defined', 'is not defined']);
			logicField.menuShouldNotHaveItems(['contains']);
			logicField.closeMenu();
			logicField.selectProviderOperator(0, 'is defined');
			logicField.ruleShouldHaveTextInputCount(0, 1);
			logicField.typeTextValue(0, 'tier');
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node =>
				(node.properties?.find(property => property.name === 'logics')?.values ?? []).length === 1));

			getConditionalLogicNode(targetPath).then(node => {
				const storedRule = parseStoredLogicRule(node.properties!.find(property => property.name === 'logics')!.values![0]);

				expect(storedRule.sourceType).to.equal(SOURCE_ID);
				expect((storedRule as unknown as Record<string, unknown>).segment).to.equal('tier');
				expect(storedRule.operator).to.equal('exists');
				expect(storedRule.sourceFieldName).to.equal(undefined);
			});
		});
	});

	it('evaluates a source pushed before Formidable\'s script, and the server accepts what the browser declared', () => {
		liveForm('acme-before', 'equals', 'gold').then(({livePath}) => {
			pushBeforeFormidable();
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submission');
			const form = visitLiveForm(livePath);

			offerWrapper().should('have.attr', 'data-fmdb-logic-hidden', 'false');
			form.getTextInput('fullname').type('Ada');
			form.getTextInput('offer').type('premium');
			form.submit();

			declaredState().then(state => {
				expect(state?.providers[SOURCE_ID]).to.deep.equal({tier: 'gold'});
			});
			// the server agrees the field was visible: its value is accepted
			form.getSuccessMessage().should('be.visible');
		});
	});

	it('evaluates the rules again when the source registers after the form came up', () => {
		liveForm('acme-after', 'equals', 'gold').then(({livePath}) => {
			visitLiveForm(livePath);
			offerWrapper().should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('have.attr', 'data-fmdb-logic-unresolved', `source:${SOURCE_ID}`);

			cy.window().then(win => {
				(win as AcmePage).acmeSegments = {tier: 'gold'};
				pushPageHalf(win as AcmePage);
			});

			offerWrapper().should('have.attr', 'data-fmdb-logic-hidden', 'false')
				.and('not.have.attr', 'data-fmdb-logic-unresolved');
		});
	});

	it('keeps the field hidden on the page and on the server when no module registers the source', () => {
		liveForm('acme-none', 'notExists').then(({livePath}) => {
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submission');
			const form = visitLiveForm(livePath);

			// even "is not defined" stays false: an unregistered source cannot be read
			offerWrapper().should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('have.attr', 'data-fmdb-logic-unresolved', `source:${SOURCE_ID}`);
			form.getTextInput('fullname').type('Grace');
			form.submit();

			// nothing is declared for it, so the server fails the rule closed too: the required field, left
			// empty, is accepted because the server holds it hidden
			declaredState().then(state => {
				expect(state?.providers ?? {}).not.to.have.property(SOURCE_ID);
			});
			form.getSuccessMessage().should('be.visible');
		});
	});
});

import {ConditionalLogicEditor} from '../../page-object';
import {
	createConditionalLogicForm,
	getConditionalLogicNode,
	getInputDateNode,
	getInputTextNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: [] as Array<{name: string; value: string}>
};

const LOGIC_STATE_HEADER = 'X-Formidable-Logic-State';
const DIRECT_SUBMIT_PATH = '/modules/formidable-engine/form-submit';

/** The browser's local calendar day shifted by offsetDays, as yyyy-MM-dd. */
const localDay = (offsetDays: number): string => {
	const date = new Date();
	date.setDate(date.getDate() + offsetDays);
	const month = String(date.getMonth() + 1).padStart(2, '0');
	const day = String(date.getDate()).padStart(2, '0');
	return `${date.getFullYear()}-${month}-${day}`;
};

/**
 * Date rules comparing against the submission day: the value stores the "today" sentinel
 * instead of a fixed date, the browser resolves it to the visitor's local day on every
 * re-evaluation, and the submission declares that day in the logic state header so the
 * server resolves the same rules to the same calendar day. The server-side coherence
 * consequences (FMDB-013, the one-day plausibility clamp) are covered by security/42.
 */
describe('Form logic - 510 Rules relative to the submission day', () => {
	useFormidableSite();

	const onTodayRule = JSON.stringify({
		logicId: 'rt-today',
		sourceFieldName: 'start-date',
		sourceFieldType: 'fmdb:inputDate',
		valueKind: 'date',
		operator: 'on',
		value: 'today'
	});

	const createTodayGatedFormPage = (suffix: string) => {
		const gated = getInputTextNode({name: 'details', title: 'details'});
		gated.properties = [...(gated.properties ?? []), {name: 'logics', values: [onTodayRule]}];

		return createPublishedLiveFormPage(
			`today-live-${suffix}-${Date.now()}`,
			'Submission-day live form',
			[getInputDateNode({name: 'start-date', title: 'start-date'}), gated],
			undefined,
			undefined,
			{actions: [SAVE_TO_JCR_ACTION]}
		);
	};

	it('stores the sentinel through the editor and reopens it as the checked toggle', () => {
		createConditionalLogicForm(`${Date.now()}-today`).then(({targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSource(0, 'start-date');
			logicField.selectOperator(0, 'is on');
			logicField.toggleTodayValue(0);
			logicField.dateInputShouldBeDisabled(0, 0, true);
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				return rawLogics.length === 1;
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				const storedRule = parseStoredLogicRule(rawLogics[0]);

				expect(storedRule.operator).to.equal('on');
				expect(storedRule.valueKind).to.equal('date');
				// The stored value is the sentinel itself, never a resolved date: the
				// rule must keep following the submission day, whenever that is.
				expect(storedRule.value).to.equal('today');
			});

			const reopenedEditor = ConditionalLogicEditor.visit(targetPath);
			reopenedEditor.logicField.waitUntilReady();
			reopenedEditor.logicField.todayValueShouldBeChecked(0, 0, true);
			reopenedEditor.logicField.dateInputShouldBeDisabled(0, 0, true);

			// Unchecking restores an editable fixed-date input, so the sentinel is
			// a reversible choice rather than a one-way rewrite of the rule.
			reopenedEditor.logicField.toggleTodayValue(0);
			reopenedEditor.logicField.dateInputShouldBeDisabled(0, 0, false);
			reopenedEditor.cancelAndDiscard();
		});
	});

	it('shows the gated field only while the source date is the visitor\'s day', () => {
		createTodayGatedFormPage('visibility').then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// Empty source date: the rule fails, the gated field starts hidden.
			cy.get('[data-fmdb-node-name="details"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true');

			form.getDateInput('start-date').setDate(localDay(0));
			cy.get('[data-fmdb-node-name="details"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'false');

			form.getDateInput('start-date').setDate(localDay(1));
			cy.get('[data-fmdb-node-name="details"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true');
		});
	});

	it('declares the visitor\'s day at submit and the submission is accepted', () => {
		createTodayGatedFormPage('submit').then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getDateInput('start-date').setDate(localDay(0));
			cy.get('[data-fmdb-node-name="details"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'false');
			form.getTextInput('details').type('same-day answer');

			cy.intercept('POST', `${DIRECT_SUBMIT_PATH}*`).as('submission');
			form.submit();
			form.getSuccessMessage().should('be.visible');

			// The declaration rides along and names the visitor's own calendar day:
			// that is what lets the server resolve "today" to the same day it did.
			cy.get('@submission').its('request.headers').then(headers => {
				const raw = headers[LOGIC_STATE_HEADER.toLowerCase()] as string;
				expect(raw, 'logic state header').to.be.a('string').and.not.be.empty;
				const declaration = JSON.parse(atob(raw)) as {v: number; today?: string};
				expect(declaration.today).to.equal(localDay(0));
			});
		});
	});
});

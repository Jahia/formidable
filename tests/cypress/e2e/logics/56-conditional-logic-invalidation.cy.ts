import {getInputTextNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A source outside the form can change without any form event, so the runtime offers two
 * ways to notice: the exact one, a `fmdb:logic-invalidate` event any integrator can
 * dispatch, and a fallback that samples the referenced JS variables.
 */
describe('Form logic - 56 Conditional logic invalidation', () => {
	useFormidableSite();

	const INVALIDATE_EVENT = 'fmdb:logic-invalidate';

	const rule = JSON.stringify({
		logicId: 'rt-invalidate',
		sourceType: 'jsVariable',
		variable: 'window.fmdbTestContext.userType',
		operator: 'equals',
		value: 'member'
	});

	const wrapperShouldBeHidden = (fieldName: string, hidden: boolean) =>
		cy.get(`[data-fmdb-node-name="${fieldName}"]`)
			.should('have.attr', 'data-fmdb-logic-hidden', hidden ? 'true' : 'false');

	const createFormPage = (suffix: string) => createPublishedLiveFormPage(
		`invalidation-live-${suffix}-${Date.now()}`,
		'Invalidation live form',
		[
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			{
				...getInputTextNode({name: 'memberid', title: 'memberid'}),
				properties: [
					{name: 'jcr:title', value: 'memberid', language: 'en'},
					{name: 'logics', values: [rule]}
				]
			}
		]
	);

	it('re-evaluates when the invalidation event is dispatched on the document', () => {
		createFormPage('event').then(({livePath}) => {
			visitLiveForm(livePath);
			// Waiting on the attribute proves hydration ran: before it, the field is hidden
			// because the server rendered it so, not because the rule was evaluated.
			wrapperShouldBeHidden('memberid', true);

			cy.window().then(win => {
				(win as unknown as {fmdbTestContext?: {userType: string}}).fmdbTestContext = {userType: 'member'};
				win.document.dispatchEvent(new win.Event(INVALIDATE_EVENT, {bubbles: true}));
			});

			wrapperShouldBeHidden('memberid', false);
		});
	});

	it('hides the field again when the variable stops matching', () => {
		createFormPage('back').then(({livePath}) => {
			visitLiveForm(livePath);

			cy.window().then(win => {
				(win as unknown as {fmdbTestContext?: {userType: string}}).fmdbTestContext = {userType: 'member'};
				win.document.dispatchEvent(new win.Event(INVALIDATE_EVENT, {bubbles: true}));
			});
			wrapperShouldBeHidden('memberid', false);

			cy.window().then(win => {
				(win as unknown as {fmdbTestContext?: {userType: string}}).fmdbTestContext = {userType: 'guest'};
				win.document.dispatchEvent(new win.Event(INVALIDATE_EVENT, {bubbles: true}));
			});
			wrapperShouldBeHidden('memberid', true);
		});
	});

	it('notices a late variable without any event, through sampling', () => {
		createFormPage('sampling').then(({livePath}) => {
			visitLiveForm(livePath);
			wrapperShouldBeHidden('memberid', true);

			// No event dispatched: the JS-variable watcher must pick the change up on its own,
			// which is what keeps datalayers that populate late working out of the box.
			cy.window().then(win => {
				(win as unknown as {fmdbTestContext?: {userType: string}}).fmdbTestContext = {userType: 'member'};
			});

			wrapperShouldBeHidden('memberid', false);
		});
	});
});

import {enableModule} from '@jahia/cypress';
import {createPublishedLiveFormPage, FORMIDABLE_TEST_SITE, getInputTextNode} from '../../support/fixtures';
import {
	expectSuccessResponse,
	postDirectMultipartSubmission,
	useFormidableSite,
	withSameOriginHeaders
} from '../security/support';

/**
 * End-to-end coverage of TypeScript form actions: actions registered from a JavaScript
 * module through the "formidable-form-action" registry type and dispatched by
 * formidable-engine's submission pipeline (JsFormActionDispatcher).
 *
 * The sample actions live in formidable-test-module-samples-tsx
 * (src/server/formActions.server.tsx) and use the raw registry contract.
 */
describe('Actions - TypeScript form actions', () => {
	useFormidableSite();

	let logActionFormId: string;
	let failingActionFormId: string;
	let noHandlerFormId: string;

	before(() => {
		cy.login();
		enableModule('formidable-test-module-samples-tsx', FORMIDABLE_TEST_SITE.key);

		createPublishedLiveFormPage(
			'ts-log-action-form',
			'TS log action form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'ts-log-action-form-page',
			'TS log action form page',
			{actions: [{
				name: 'logSubmissionTs',
				primaryNodeType: 'fmdbsampletsx:logSubmissionTsAction',
				properties: []
			}]}
		).then(({formId}) => {
			logActionFormId = formId;
		});

		createPublishedLiveFormPage(
			'ts-failing-action-form',
			'TS failing action form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'ts-failing-action-form-page',
			'TS failing action form page',
			{actions: [{
				name: 'alwaysFailTs',
				primaryNodeType: 'fmdbsampletsx:alwaysFailTsAction',
				properties: []
			}]}
		).then(({formId}) => {
			failingActionFormId = formId;
		});

		createPublishedLiveFormPage(
			'ts-no-handler-form',
			'TS no handler form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'ts-no-handler-form-page',
			'TS no handler form page',
			{actions: [{
				name: 'noHandler',
				primaryNodeType: 'fmdbsampletsx:noHandlerAction',
				properties: []
			}]}
		).then(({formId}) => {
			noHandlerFormId = formId;
		});

		cy.logout();
	});

	it('executes a TypeScript action registered through the formidable-form-action registry', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: logActionFormId,
			fields: {fullName: 'Alice'},
			headers: withSameOriginHeaders()
		}).then(response => {
			// Expected outcome: the JS handler ran and the submission succeeds end-to-end.
			expectSuccessResponse(response);
		});
	});

	it('reports a failing TypeScript action as FMDB-008 with action progress counters', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: failingActionFormId,
			fields: {fullName: 'Alice'},
			headers: withSameOriginHeaders()
		}).then(response => {
			// Expected outcome: the {ok:false, status:422} result surfaces as the standard
			// FMDB-008 action failure, with progress counters in the response body.
			expect(response.status).to.eq(422);
			expect(response.body).to.deep.equal({
				success: false,
				errorCode: 'FMDB-008',
				actionsCompleted: 0,
				actionsTotal: 1
			});
		});
	});

	it('rejects an action node type with no registered handler (Java or JS) as FMDB-008', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: noHandlerFormId,
			fields: {fullName: 'Alice'},
			headers: withSameOriginHeaders()
		}).then(response => {
			// Expected outcome: unchanged historical behavior — no handler means FMDB-008.
			expect(response.status).to.eq(422);
			expect(response.body).to.deep.equal({
				success: false,
				errorCode: 'FMDB-008',
				actionsCompleted: 0,
				actionsTotal: 1
			});
		});
	});
});

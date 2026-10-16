import {getNodeByPath} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	createPublishedLiveFormPage,
	getInputTextNode,
	getRedirectActionNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * The redirect action (docs/architecture/redirect-action.md): an accepted submission carries the target in the reserved
 * key `redirect`, and the island navigates there. The target is always a path of the site: a page picked in the editor,
 * or a path typed in; whatever else a contributor stored (another site) is dropped by the server, and the submission is
 * accepted without a redirect. A failing action never redirects.
 */
describe('Actions - 89 Redirect', () => {
	useFormidableSite();

	const submit = (formId: string) => postDirectMultipartSubmission({formId, fields: {name: 'Ada'}, headers: withSameOriginHeaders()});

	it('sends the visitor to a path of the site', () => {
		const target = `/sites/${FORMIDABLE_TEST_SITE.key}/home/thank-you.html`;
		createPublishedLiveFormPage('redirect-path-form', 'Redirect to a path', [getInputTextNode({name: 'name', title: 'Name'})], undefined, undefined, {
			actions: [getSaveToJcrActionNode(), getRedirectActionNode({path: target})]
		}).then(({formId}) => {
			submit(formId).then(response => {
				expect(response.status).to.eq(200);
				expect(response.body).to.deep.equal({success: true, redirect: target});
			});
		});
	});

	it('sends the visitor to a page picked in the editor, at the address the platform gives it', () => {
		createPublishedLiveFormPage('redirect-thanks-form', 'The thank-you page holder', [getInputTextNode({name: 'name', title: 'Name'})], 'redirect-thanks', 'Thank you').then(({pagePath}) => {
			getNodeByPath(pagePath).then(page => {
				const pageUuid = page.data.jcr.nodeByPath.uuid as string;
				createPublishedLiveFormPage('redirect-page-form', 'Redirect to a page', [getInputTextNode({name: 'name', title: 'Name'})], undefined, undefined, {
					actions: [getRedirectActionNode({pageUuid})]
				}).then(({formId}) => {
					submit(formId).then(response => {
						expect(response.status).to.eq(200);
						const redirect = (response.body as {redirect?: string}).redirect;
						expect(redirect, 'a path of this site, not a URL').to.match(/^\/[^/\\]/);
						expect(redirect).to.contain('redirect-thanks');
						expect(redirect).to.match(/\.html$/);
					});
				});
			});
		});
	});

	it('drops a target that names another site and still accepts the submission', () => {
		for (const [index, hostile] of ['https://evil.example/phish', '//evil.example', 'javascript:alert(1)', '/\\evil.example'].entries()) {
			createPublishedLiveFormPage(`redirect-hostile-form-${index}`, `Redirect, hostile target ${index}`, [getInputTextNode({name: 'name', title: 'Name'})], undefined, undefined, {
				actions: [getRedirectActionNode({path: hostile})]
			}).then(({formId}) => {
				submit(formId).then(response => {
					expect(response.status, hostile).to.eq(200);
					expect(response.body, hostile).to.deep.equal({success: true});
				});
			});
		}
	});

	it('does not redirect a submission that was refused', () => {
		createPublishedLiveFormPage('redirect-refused-form', 'Redirect, required field', [getInputTextNode({name: 'name', title: 'Name', required: true})], undefined, undefined, {
			actions: [getRedirectActionNode({path: '/sites/x/home.html'})]
		}).then(({formId}) => {
			postDirectMultipartSubmission({formId, fields: {}, headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(400);
				expect(response.body).to.deep.equal({success: false, errorCode: 'FMDB-010'});
			});
		});
	});
});

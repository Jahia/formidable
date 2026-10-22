import {getNodeByPath} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	createPublishedLiveFormPage,
	getInputTextNode,
	getRedirectActionNode,
	getSaveToJcrActionNode,
	visitEditForm,
	visitLiveForm
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * The redirect action (docs/architecture/redirect-action.md): an accepted submission carries the target in the reserved
 * key `redirect`, and the island navigates there instead of showing the submission message. The target is a page picked
 * in the editor, or a URL typed in: an http(s) URL or a path of the site. Whatever else a contributor stored (a
 * javascript: URL, a protocol-relative one) is dropped by the server, and the submission is accepted without a
 * redirect. The first usable redirect of the list wins, and the editor warns about the others. A refused submission
 * never redirects.
 */
describe('Actions - 89 Redirect', () => {
	useFormidableSite();

	const nameField = () => [getInputTextNode({name: 'name', title: 'Name'})];
	const submit = (formId: string) => postDirectMultipartSubmission({formId, fields: {name: 'Ada'}, headers: withSameOriginHeaders()});

	it('sends the visitor to a path of the site or to another website', () => {
		const path = `/sites/${FORMIDABLE_TEST_SITE.key}/home/thank-you.html`;
		for (const [index, target] of [path, 'https://www.example.com/thank-you?from=form'].entries()) {
			createPublishedLiveFormPage(`redirect-url-form-${index}`, `Redirect to a URL ${index}`, nameField(), undefined, undefined, {
				actions: [getSaveToJcrActionNode(), getRedirectActionNode({url: target})]
			}).then(({formId}) => {
				submit(formId).then(response => {
					expect(response.status, target).to.eq(200);
					expect(response.body, target).to.deep.equal({success: true, redirect: target});
				});
			});
		}
	});

	it('drops a target the browser must not follow and still accepts the submission', () => {
		for (const [index, unusable] of ['javascript:alert(1)', '//evil.example', '/\\evil.example', 'www.example.com', 'ftp://example.com/'].entries()) {
			createPublishedLiveFormPage(`redirect-unusable-form-${index}`, `Redirect, unusable target ${index}`, nameField(), undefined, undefined, {
				actions: [getRedirectActionNode({url: unusable})]
			}).then(({formId}) => {
				submit(formId).then(response => {
					expect(response.status, unusable).to.eq(200);
					expect(response.body, unusable).to.deep.equal({success: true});
				});
			});
		}
	});

	it('follows the first usable redirect of the list', () => {
		createPublishedLiveFormPage('redirect-first-form', 'Redirect, several', nameField(), undefined, undefined, {
			actions: [
				getRedirectActionNode({name: 'unusable', url: 'javascript:alert(1)'}),
				getRedirectActionNode({name: 'first-usable', url: 'https://www.example.com/first'}),
				getRedirectActionNode({name: 'later', url: '/sites/x/later.html'})
			]
		}).then(({formId}) => {
			submit(formId).then(response => {
				expect(response.status).to.eq(200);
				expect(response.body).to.deep.equal({success: true, redirect: 'https://www.example.com/first'});
			});
		});
	});

	it('does not redirect a submission that was refused', () => {
		createPublishedLiveFormPage('redirect-refused-form', 'Redirect, required field', [getInputTextNode({name: 'name', title: 'Name', required: true})], undefined, undefined, {
			actions: [getRedirectActionNode({url: '/sites/x/home.html'})]
		}).then(({formId}) => {
			postDirectMultipartSubmission({formId, fields: {}, headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(400);
				expect(response.body).to.deep.equal({success: false, errorCode: 'FMDB-010'});
			});
		});
	});

	it('takes the visitor to the page picked in the editor, without the submission message', () => {
		createPublishedLiveFormPage('redirect-thanks-form', 'The thank-you page holder', nameField(), 'redirect-thanks', 'Thank you').then(({pagePath}) => {
			getNodeByPath(pagePath).then(page => {
				const pageUuid = page.data.jcr.nodeByPath.uuid as string;
				createPublishedLiveFormPage('redirect-page-form', 'Redirect to a page', nameField(), undefined, undefined, {
					actions: [getRedirectActionNode({pageUuid})]
				}).then(({formId, livePath}) => {
					submit(formId).then(response => {
						expect(response.status).to.eq(200);
						const redirect = (response.body as {redirect?: string}).redirect;
						expect(redirect, 'a path of this site, not a URL').to.match(/^\/[^/\\]/);
						expect(redirect).to.contain('redirect-thanks');
					});

					cy.logout();
					const form = visitLiveForm(livePath);
					form.getTextInput('name').get().type('Ada');
					form.submit();
					cy.location('pathname').should('contain', '/redirect-thanks');
					cy.get('.fmdb-message-success').should('not.exist');
				});
			});
		});
	});

	it('warns in the actions zone about a redirect that an earlier one comes before', () => {
		createPublishedLiveFormPage('redirect-zone-form', 'Redirect, actions zone', nameField(), undefined, undefined, {
			actions: [
				getRedirectActionNode({name: 'first', title: 'To the partner', url: 'https://www.example.com/partner'}),
				getRedirectActionNode({name: 'second', title: 'To the home page', url: '/sites/x/home.html'})
			]
		}).then(({pagePath}) => {
			visitEditForm(pagePath);

			cy.get('.fmdb-authoring-actions .fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').should('have.length', 2);
			cy.get('.fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').eq(0).within(() => {
				cy.root().should('not.have.attr', 'data-fmdb-authoring-state');
				cy.get('.fmdb-authoring-action-detail').should('have.text', 'https://www.example.com/partner');
				cy.get('.fmdb-authoring-action-warning').should('not.exist');
			});
			cy.get('.fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').eq(1)
				.should('have.attr', 'data-fmdb-authoring-state', 'overridden-redirect')
				.find('.fmdb-authoring-action-warning').should('be.visible');
		});
	});
});

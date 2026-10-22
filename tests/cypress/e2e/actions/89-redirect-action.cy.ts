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
 * in the editor, or a URL typed in: an http(s) URL or a path of the site. The repository refuses a URL with neither a
 * scheme nor a leading slash; whatever else the server cannot use (a protocol-relative URL, user info) is dropped, and
 * the submission is accepted without a redirect. The first usable redirect of the list wins, and the editor warns about
 * the others and about an unusable address. A refused submission never redirects, and the URL never carries a
 * submitted value.
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

	it('refuses, as it is saved, a URL with neither a scheme nor a leading slash', () => {
		createPublishedLiveFormPage('redirect-constraint-form', 'Redirect, constraint', nameField(), undefined, undefined, {
			actions: [getRedirectActionNode({url: '/sites/x/home.html'})]
		}).then(({formPath}) => {
			for (const refused of ['www.example.com', 'javascript:alert(1)', 'ftp://example.com/']) {
				cy.request({
					method: 'POST',
					url: '/modules/graphql',
					headers: withSameOriginHeaders(),
					body: {
						query: 'mutation Set($path: String!, $value: String!) { jcr { mutateNode(pathOrId: $path) { mutateProperty(name: "url") { setValue(value: $value, language: "en") } } } }',
						variables: {path: `${formPath}/actions/redirect`, value: refused}
					}
				}).its('body.errors').should('have.length.greaterThan', 0);
			}
		});
	});

	it('drops a target the browser must not follow and still accepts the submission', () => {
		for (const [index, unusable] of ['//evil.example', '/\\evil.example', 'https://user@evil.example/', 'https://', 'http:///path'].entries()) {
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
				getRedirectActionNode({name: 'unusable', url: '//evil.example'}),
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
					// what the form page shows as the browser leaves it: Cypress hands its commands over to the next page,
					// so the page is read in its own beforeunload
					const leaving = {spinner: false, message: true};
					cy.window().then(win => win.addEventListener('beforeunload', () => {
						leaving.spinner = win.document.querySelector('.fmdb-spinner') !== null;
						leaving.message = win.document.querySelector('.fmdb-message-success') !== null;
					}));
					form.getTextInput('name').get().type('Ada');
					form.submit();
					cy.location('pathname').should('contain', '/redirect-thanks');
					cy.wrap(leaving).should('deep.equal', {spinner: true, message: false});
					// the URL carries no submitted value
					cy.location('search').should('eq', '');
					cy.location('href').should('not.contain', 'Ada');
				});
			});
		});
	});

	it('warns in the actions zone about a redirect that an earlier one comes before, and about an unusable address', () => {
		createPublishedLiveFormPage('redirect-zone-form', 'Redirect, actions zone', nameField(), undefined, undefined, {
			actions: [
				getRedirectActionNode({name: 'first', title: 'To the partner', url: 'https://www.example.com/partner'}),
				getRedirectActionNode({name: 'second', title: 'To the home page', url: '/sites/x/home.html'}),
				getRedirectActionNode({name: 'third', title: 'Protocol-relative', url: '//evil.example'})
			]
		}).then(({pagePath}) => {
			visitEditForm(pagePath);

			cy.get('.fmdb-authoring-actions .fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').should('have.length', 3);
			cy.get('.fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').eq(0).within(() => {
				cy.root().should('not.have.attr', 'data-fmdb-authoring-state');
				cy.get('.fmdb-authoring-action-detail').should('have.text', 'https://www.example.com/partner');
				cy.get('.fmdb-authoring-action-warning').should('not.exist');
			});
			cy.get('.fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').eq(1)
				.should('have.attr', 'data-fmdb-authoring-state', 'overridden-redirect')
				.find('.fmdb-authoring-action-warning').should('be.visible');
			// an address the redirect drops is the warning the card shows, before the earlier redirect's
			cy.get('.fmdb-authoring-action[data-fmdb-action-type="fmdb:redirectAction"]').eq(2)
				.should('have.attr', 'data-fmdb-authoring-state', 'unusable-redirect-url')
				.find('.fmdb-authoring-action-warning').should('be.visible');
		});
	});
});

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
 * in the editor, or a web address typed in, an http(s) URL: a page of the site goes through the picker. The repository
 * refuses an address that does not start with http:// or https://; whatever else the server cannot use (no host, user
 * info) is dropped, and
 * the submission is accepted without a redirect. The first usable redirect of the list wins, and the editor warns about
 * the others and about an unusable address. A refused submission never redirects, and the URL never carries a
 * submitted value.
 */
describe('Actions - 89 Redirect', () => {
	useFormidableSite();

	const nameField = () => [getInputTextNode({name: 'name', title: 'Name'})];
	const submit = (formId: string) => postDirectMultipartSubmission({formId, fields: {name: 'Ada'}, headers: withSameOriginHeaders()});

	it('sends the visitor to another website', () => {
		for (const [index, target] of ['https://www.example.com/thank-you?from=form', 'http://example.com'].entries()) {
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

	it('sends each language to its own address', () => {
		const targets = {en: 'https://www.example.com/en/thank-you', fr: 'https://www.example.com/fr/merci'};
		createPublishedLiveFormPage('redirect-languages-form', 'Redirect per language', nameField(), undefined, undefined, {
			actions: [getRedirectActionNode({urls: targets})],
			publishLanguages: ['en', 'fr']
		}).then(({formId}) => {
			for (const [lang, target] of Object.entries(targets)) {
				postDirectMultipartSubmission({formId, fields: {name: 'Ada'}, headers: withSameOriginHeaders(), lang}).then(response => {
					expect(response.status, lang).to.eq(200);
					expect(response.body, lang).to.deep.equal({success: true, redirect: target});
				});
			}
		});
	});

	it('sends a language without an address to the address of the site\'s default language', () => {
		// the site's default language is English: the French submission takes the English address
		const target = 'https://www.example.com/en/thank-you';
		createPublishedLiveFormPage('redirect-fallback-form', 'Redirect, fallback', nameField(), undefined, undefined, {
			actions: [getRedirectActionNode({urls: {en: target}})],
			publishLanguages: ['en', 'fr']
		}).then(({formId}) => {
			postDirectMultipartSubmission({formId, fields: {name: 'Ada'}, headers: withSameOriginHeaders(), lang: 'fr'}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.body).to.deep.equal({success: true, redirect: target});
			});
		});
	});

	it('takes the visitor to another website, without the submission message', () => {
		const target = 'https://www.example.com/thank-you';
		createPublishedLiveFormPage('redirect-live-url-form', 'Redirect to a URL, live', nameField(), undefined, undefined, {
			actions: [getRedirectActionNode({url: target})]
		}).then(({livePath}) => {
			cy.logout();
			const form = visitLiveForm(livePath);
			// the other website is stubbed: the test proves the navigation, and depends on no outside host
			cy.intercept({method: 'GET', url: `${target}*`}, {statusCode: 200, headers: {'content-type': 'text/html'}, body: '<html><body>Thank you</body></html>'}).as('external');
			const leaving = {spinner: false, message: true};
			cy.window().then(win => win.addEventListener('beforeunload', () => {
				leaving.spinner = win.document.querySelector('.fmdb-spinner') !== null;
				leaving.message = win.document.querySelector('.fmdb-message-success') !== null;
			}));
			form.getTextInput('name').get().type('Ada');
			form.submit();
			// the URL carries no submitted value
			cy.wait('@external', {timeout: 15000}).its('request.url').should('eq', target);
			cy.wrap(leaving).should('deep.equal', {spinner: true, message: false});
		});
	});

	it('refuses, as it is saved, an address that does not start with http:// or https://', () => {
		createPublishedLiveFormPage('redirect-constraint-form', 'Redirect, constraint', nameField(), undefined, undefined, {
			actions: [getRedirectActionNode({url: 'https://www.example.com/thanks'})]
		}).then(({formPath}) => {
			const actionPath = `${formPath}/actions/redirect`;
			const setUrl = (value: string) => cy.request({
				method: 'POST',
				url: '/modules/graphql',
				headers: withSameOriginHeaders(),
				body: {
					query: 'mutation Set($path: String!, $value: String!) { jcr { mutateNode(pathOrId: $path) { mutateProperty(name: "url") { setValue(value: $value, language: "en") } } } }',
					variables: {path: actionPath, value}
				}
			}).its('body');
			const storedUrl = () => getNodeByPath(actionPath, ['url'], 'en')
				.then(response => response.data.jcr.nodeByPath.properties.find(property => property.name === 'url')?.value);

			// the same mutation saves an address that starts with https://, so an error below is the constraint's
			const accepted = 'https://www.example.com/accepted';
			setUrl(accepted).its('errors').should('be.undefined');
			storedUrl().should('eq', accepted);
			// a path of the site included: a page goes through the page target
			for (const refused of ['www.example.com', `/sites/${FORMIDABLE_TEST_SITE.key}/home.html`, '//evil.example', 'javascript:alert(1)', 'ftp://example.com/']) {
				setUrl(refused).its('errors').should('have.length.greaterThan', 0);
				storedUrl().should('eq', accepted);
			}
		});
	});

	it('drops a target the browser must not follow and still accepts the submission', () => {
		for (const [index, unusable] of ['https://user@evil.example/', 'https://', 'http:///path', 'https://www.example.com/a b'].entries()) {
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
				getRedirectActionNode({name: 'unusable', url: 'https://user@evil.example/'}),
				getRedirectActionNode({name: 'first-usable', url: 'https://www.example.com/first'}),
				getRedirectActionNode({name: 'later', url: 'https://www.example.com/later'})
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
			actions: [getRedirectActionNode({url: 'https://www.example.com/thanks'})]
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
					cy.location('pathname', {timeout: 15000}).should('contain', '/redirect-thanks');
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
				getRedirectActionNode({name: 'second', title: 'To the shop', url: 'https://shop.example.com/'}),
				getRedirectActionNode({name: 'third', title: 'With a user name', url: 'https://user@evil.example/'})
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

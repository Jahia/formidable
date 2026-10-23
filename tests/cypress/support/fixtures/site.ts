import gql from 'graphql-tag';

/**
 * A site forms are provisioned on: how it is created, and what its template set names the page template and
 * the area a form page holds its form in (the JSP test template set: `simple` and `pagecontent`; the TSX one:
 * `basic` and `main`).
 */
export interface FormSite {
	key: string
	config: {
		templateSet: string
		serverName: string
		locale: string
		languages: string
	}
	/** The page template of a form page, as the template set names it. */
	templateName: string
	/** The area of a form page the form reference lands in, as the template set names it. */
	areaName: string
}

/**
 * Formidable site configuration for Formidable tests
 */
export const FORMIDABLE_TEST_SITE: FormSite = {
	key: 'FormidableSite4Tests',
	config: {
		templateSet: 'formidable-test-module-templateset-jsp',
		serverName: 'localhost',
		locale: 'en',
		languages: 'en,fr',
	},
	templateName: 'simple',
	areaName: 'pagecontent',
}

/**
 * Flushes the rendered-fragment cache of the test site. Needed when a module
 * configuration change must show on an already-rendered live page: the config
 * reaches the services immediately, but live pages keep serving their cached
 * fragments until a content change flushes them.
 */
export function flushSiteCache(): Cypress.Chainable {
	return cy.apollo({
		mutation: gql`
			mutation flushTestSiteCache($sitePath: String!) {
				jcontent {
					flushSiteCache(sitePath: $sitePath)
				}
			}
		`,
		variables: {sitePath: `/sites/${FORMIDABLE_TEST_SITE.key}`}
	});
}

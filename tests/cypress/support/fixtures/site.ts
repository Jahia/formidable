import gql from 'graphql-tag';

/**
 * Formidable site configuration for Formidable tests
 */
export const FORMIDABLE_TEST_SITE: {
	key: string
	config: {
		templateSet: string
		serverName: string
		locale: string
		languages: string
	}
} = {
	key: 'FormidableSite4Tests',
	config: {
		templateSet: 'formidable-test-module-templateset-jsp',
		serverName: 'localhost',
		locale: 'en',
		languages: 'en,fr',
	},
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

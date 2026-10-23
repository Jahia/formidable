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

/** The two test template sets, and what each names its page template and its area. */
export const TEMPLATE_SETS = {
	/** The JSP one: no stylesheet, the modules' own look. */
	jsp: {templateSet: 'formidable-test-module-templateset-jsp', templateName: 'simple', areaName: 'pagecontent'},
	/** The TSX one, a JavaScript module whose Layout ships the complete stylesheet (dist/assets/style.css). */
	tsx: {templateSet: 'formidable-test-module-templateset-tsx', templateName: 'basic', areaName: 'main'},
} as const

/**
 * Formidable site configuration for Formidable tests
 */
export const FORMIDABLE_TEST_SITE: FormSite = {
	key: 'FormidableSite4Tests',
	config: {
		templateSet: TEMPLATE_SETS.jsp.templateSet,
		serverName: 'localhost',
		locale: 'en',
		languages: 'en,fr',
	},
	templateName: TEMPLATE_SETS.jsp.templateName,
	areaName: TEMPLATE_SETS.jsp.areaName,
}

/**
 * A site on the TSX test template set, for what only a template set shipping a stylesheet shows: created
 * and deleted by the specs that need it (validation/40), next to the test site, never in its place.
 */
export const FORMIDABLE_TSX_TEST_SITE: FormSite = {
	key: 'FormidableTsxSite4Tests',
	config: {
		templateSet: TEMPLATE_SETS.tsx.templateSet,
		serverName: 'localhost',
		locale: 'en',
		languages: 'en',
	},
	templateName: TEMPLATE_SETS.tsx.templateName,
	areaName: TEMPLATE_SETS.tsx.areaName,
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

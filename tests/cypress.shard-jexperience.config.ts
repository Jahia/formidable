import {defineConfig} from 'cypress'
import base from './cypress.config'

// The jExperience runner (.github/workflows/jexperience-tests.yml): a Jahia beside a jCustomer, and only the specs of
// cypress/e2e/jexperience/, which every other config excludes. The browser runs under a plain Chrome user agent: the
// tracker carries the crawler-user-agents list, which names HeadlessChrome, and would otherwise start in its fallback
// mode, where nothing is sent and nothing is prefilled.
export default defineConfig({
	...base,
	userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
	e2e: {
		...base.e2e,
		specPattern: ['cypress/e2e/jexperience/**/*.cy.ts'],
		excludeSpecPattern: ['**/*.ignore.ts'],
	},
})

import {defineConfig} from 'cypress'
import fs from "fs";

export default defineConfig({
	chromeWebSecurity: false,
	defaultCommandTimeout: 5000,
	requestTimeout: 5000,
	responseTimeout: 7000,
	reporter: 'cypress-multi-reporters',
	reporterOptions: {
		configFile: 'reporter-config.json',
	},
	screenshotsFolder: './results/screenshots',
	videosFolder: './results/videos',
	viewportWidth: 1366,
	viewportHeight: 768,
	watchForFileChanges: false,
	experimentalModifyObstructiveThirdPartyCode: true, // Required for SSO/social authentication
	includeShadowDom: true, // Enable automatic shadow DOM traversal (including closed shadow roots)
	// CI resilience (#237) is opted into PER SPEC, not here: a retry re-enters the it body
	// (before does not re-run), so it only helps a spec whose fixtures are retry-safe —
	// unique node names per attempt (Date.now() evaluated inside the test). On the many
	// specs with static fixture names, a global retry would replace the real failure with
	// a name collision in the report. A spec that is retry-safe declares
	// {retries: {runMode: 2, openMode: 0}} on its describe (see e2e/integrity/).
	e2e: {
		specPattern: ['**/**.cy.ts'],
		// We've imported your old cypress plugins here.
		// You may want to clean this up later by importing these.
		setupNodeEvents(on, config) {
			on('task', {
				readFileMaybe(filename) {
					if (fs.existsSync(filename)) {
						return fs.readFileSync(filename, 'utf8')
					}

					return null
				},
			})

			// eslint-disable-next-line @typescript-eslint/no-require-imports
			return require('./cypress/plugins/index.js')(on, config)
		},
		excludeSpecPattern: ['**/*.ignore.ts'],
		baseUrl: 'http://localhost:8080',
	}
})

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
	// CI resilience (#237): a test gets two more attempts before failing the run — the
	// suite's heavy cases (full submissions, integrity scans) intermittently outlast a
	// timeout when the instance is under load, and one slow moment must not turn a PR red.
	// Interactive runs keep zero retries so a genuine failure stays loud while developing.
	// A retried test only benefits if its fixtures are retry-safe (unique node names per
	// attempt, e.g. Date.now() suffixes evaluated inside the test body).
	retries: {runMode: 2, openMode: 0},
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

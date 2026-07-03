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

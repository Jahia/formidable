import {FORMIDABLE_TEST_SITE} from './fixtures'

/**
 * Formidable modules required by the runtime and editor features under test.
 */
export const FORMIDABLE_MODULE_IDS = ['formidable-elements', 'formidable-engine'] as const

/**
 * Base content path for the test site
 * Used as the parent path for creating form content nodes
 */
export const CONTENT_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}/contents`
export const SITE_HOME_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}/home`

/**
 * JContent selectors for test automation
 */
export const JCONTENT_SELECTORS = {
	previewIframe: 'iframe[data-sel-role="edit-preview-frame"]'
}

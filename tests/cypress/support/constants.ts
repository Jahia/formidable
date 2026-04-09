import {FORMIDABLE_TEST_SITE} from './fixtures'

/**
 * Formidable module identifier
 */
export const FORMIDABLE_MODULE_ID = 'formidable-elements'

/**
 * Base content path for the test site
 * Used as the parent path for creating form content nodes
 */
export const CONTENT_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}/contents`

/**
 * JContent selectors for test automation
 */
export const JCONTENT_SELECTORS = {
	previewIframe: 'iframe[data-sel-role="edit-preview-frame"]'
}

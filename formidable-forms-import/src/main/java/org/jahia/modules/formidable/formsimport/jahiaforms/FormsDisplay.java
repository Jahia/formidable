package org.jahia.modules.formidable.formsimport.jahiaforms;

/**
 * One {@code fcnt:formDisplay} of the repository, the content that shows a Forms form on a page.
 *
 * @param page        the path of the page that holds the display, the display's own path when it has no page
 * @param redirectUrl the {@code redirectUrl} of {@code fcmix:formRedirectUrl}, which Forms follows after a submission
 *                    from this display; null when the display carries none
 */
public record FormsDisplay(String page, String redirectUrl) {
}

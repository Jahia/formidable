import {AddResources, buildModuleFileUrl} from "@jahia/javascript-modules-library";
import {styleTagCss} from "~/utils/richTextUtils";
import {type SiteLike, templateSetStylesheetUrl} from "~/utils/templateSetStylesheet.server";
import type {RenderContext} from "org.jahia.services.render";
import type {JSX, ReactNode} from "react";

/**
 * The shell shared by the cm inspection views (the form's and the containers'): the form's
 * own CSS, the site's template set stylesheet when it ships one, the module stylesheet, and
 * the fmdb-form wrapper a business stylesheet keys its look on. The stylesheets come in the
 * order a live page serves them, the template set's before the module's. Not a <form>:
 * nothing in an inspection preview submits. data-fmdb-cm-view is the hook this surface
 * offers to CSS, next to edit mode's data-fmdb-edit-mode (docs/styling/class-hooks.md).
 */
export const CmShell = ({css, renderContext, children}: {css?: string; renderContext: RenderContext; children: ReactNode}): JSX.Element => {
	// The library types JCRSiteNode up to getTemplatePackageName(); the host hands the package itself.
	const site = renderContext.getSite() as unknown as SiteLike | null;
	const templateSetCss = templateSetStylesheetUrl(site, renderContext.getRequest().getContextPath());

	return (
		<>
			{css && <style dangerouslySetInnerHTML={{__html: styleTagCss(css)}}/>}
			{templateSetCss && <AddResources type="css" resources={templateSetCss}/>}
			<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")}/>
			<div className="fmdb-form" data-fmdb-cm-view="true">{children}</div>
		</>
	);
};

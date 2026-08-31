import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import {Layout} from "~/templates/Layout";

/**
 * Template for fmdb:form: a form is displayable as a page of its own, which is what lets
 * jContent open it in the Page Builder from the Content Folders (the view mode is offered
 * to jnt:page and jmix:mainResource nodes only). Priority 1 wins over a template set's
 * generic jmix:mainResource template (the sample one ships with priority -1). Renders the
 * hidden.pageBuilder view, dedicated to this page.
 *
 * The template is technical: it only answers the Page Builder. Everywhere else — live,
 * preview — the standalone URL responds 404, so giving every form a page does not quietly
 * publish it outside the pages (and their ACLs) that embed it. The 404 carries a short
 * explanation for the contributor landing here from jContent's Preview/Live buttons —
 * generic on purpose: revealing nothing of the form (not even its title) is the point.
 * A template set that wants a public standalone form page declares its own fmdb:form
 * template with a priority above 1 and renders the fullPage view.
 */
jahiaComponent(
	{
		nodeType: "fmdb:form",
		name: "default",
		componentType: "template",
		priority: 1,
		// Never cached: outside edit mode the render is a 404, and the page cache would
		// otherwise store the empty errored fragment and serve it back as a 200
		properties: {"cache.expiration": "0"}
	},
	({"jcr:title": title}: {"jcr:title"?: string}, {currentNode, renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_formPage"});

		if (!renderContext.isEditMode()) {
			// The library typing only exposes the response getters, the runtime proxy has it all
			(renderContext.getResponse() as unknown as {setStatus: (code: number) => void}).setStatus(404);
			return (
				<Layout className="fmdb-form-page">
					<p>{t("unreachable")}</p>
				</Layout>
			);
		}

		return (
			<Layout title={title} className="fmdb-form-page">
				<Render node={currentNode} view="hidden.pageBuilder"/>
			</Layout>
		);
	}
);

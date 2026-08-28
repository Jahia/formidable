import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {Layout} from "~/templates/Layout";

/**
 * Template for fmdb:form: a form is displayable as a page of its own, which is what lets
 * jContent open it in the Page Builder from the Content Folders (the view mode is offered
 * to jnt:page and jmix:mainResource nodes only). Priority 1 wins over a template set's
 * generic jmix:mainResource template (the sample one ships with priority -1).
 */
jahiaComponent(
	{
		nodeType: "fmdb:form",
		name: "default",
		componentType: "template",
		priority: 1
	},
	({"jcr:title": title}: {"jcr:title"?: string}, {currentNode}) => (
		<Layout title={title} className="fmdb-form-page">
			<Render node={currentNode} view="fullPage"/>
		</Layout>
	)
);

import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import {useTranslation} from "react-i18next";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:formReference",
		name: "default",
	},
	(
		{'j:node': node, 'j:referenceView': referenceView}: {'j:node'?: JCRNodeWrapper; 'j:referenceView'?: string},
		{currentNode, currentResource, renderContext}
	) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_formReference"});

		if (!node) {
			if (renderContext.isEditMode()) {
				return <div className="fmdb-form-reference-empty">{t("noFormSelected")}</div>;
			}

			return null;
		}

		// The cached fragment must follow the form, whose real path hides behind the
		// contextualized one below (core's nodeReference view declares the same dependency).
		currentResource.getDependencies().add(node.getPath());

		// The form renders THROUGH the reference — contextualized node, read-only at its
		// root — the way the core's jmix:nodeReference view does (editable="false" on the
		// dereferenced target). The nested module paths stay scoped under the reference
		// (page/ref@/ref/fields/...), so the Page Builder box a contributor reaches is the
		// REFERENCE's: deleting removes the reference and not the form, and Edit source is
		// offered (jmix:nodeReference). The form's children stay editable inside.
		return <Render node={currentNode.getProperty("j:node").getContextualizedNode()} view={referenceView ?? "default"} readOnly/>;
	},
);

import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:formReference",
		name: "default",
	},
	({'j:node': node, 'j:referenceView': referenceView}, {renderContext}) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_formReference"});

		if (!node) {
			if (renderContext.isEditMode()) {
				return <div className="fmdb-form-reference-empty">{t("noFormSelected")}</div>;
			}
			return null;
		}

		return <Render node={node} view={referenceView ?? "default"} />;
	},
);


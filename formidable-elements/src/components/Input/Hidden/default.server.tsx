import {jahiaComponent} from "@jahia/javascript-modules-library";
import {useTranslation} from "react-i18next";
import EyeOffIcon from "~/design/EyeOffIcon";

interface InputHiddenProps {
	"jcr:title"?: string;
	value?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputHidden",
		name: "default"
	},
	(
		{"jcr:title": title, value}: InputHiddenProps,
		{currentNode, renderContext}
	) => {
		const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_inputHidden"});

		// Generate unique name for the input
		const inputName = currentNode.getName();
		const input = <input type="hidden" name={inputName} value={value || ""}/>;

		if (!renderContext.isEditMode()) {
			return input;
		}

		// While authoring, a line the contributor can see and click: without it the field's Page
		// Builder box has no height and the field can only be reached from the content tree. It says
		// what the field sends; live and preview keep the bare input.
		return (
			<div className="fmdb-authoring-hidden-field">
				<EyeOffIcon/>
				<span className="fmdb-authoring-hidden-field-label">{t("label")}</span>
				<span className="fmdb-authoring-hidden-field-title">{title || inputName}</span>
				{value
					? <span className="fmdb-authoring-hidden-field-value">{value}</span>
					: <span className="fmdb-authoring-hidden-field-value fmdb-authoring-hidden-field-empty">{t("empty")}</span>}
				{input}
			</div>
		);
	}
);

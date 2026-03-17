import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputButtonProps {
	"jcr:title"?: string;
	buttonType?: "button" | "submit" | "reset";
	variant?: "primary" | "secondary" | "danger";
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputButton",
		name: "default"
	},
	(
		{"jcr:title": title, buttonType = "button", variant = "primary"}: InputButtonProps
	) => {
		return (
			<button
				type={buttonType}
				className={`fmdb-btn fmdb-btn-${variant}`}
			>
				{title || "Bouton"}
			</button>
		);
	}
);


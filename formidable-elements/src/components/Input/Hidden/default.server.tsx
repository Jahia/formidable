import {jahiaComponent} from "@jahia/javascript-modules-library";

interface InputHiddenProps {
	value?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:inputHidden",
		name: "default"
	},
	(
		{value}: InputHiddenProps,
		{currentNode}
	) => {

		// Generate unique name for the input
		const inputName = currentNode.getName();

		return (
			<input
				type="hidden"
				name={inputName}
				value={value || ""}
			/>
		);
	}
);


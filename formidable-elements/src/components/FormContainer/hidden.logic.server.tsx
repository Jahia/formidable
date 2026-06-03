import {getNodeProps, jahiaComponent} from "@jahia/javascript-modules-library";
import LogicAwareRender from "./LogicAwareRender";

type FormContainerNode = Parameters<typeof getNodeProps>[0];

/**
 * Container-level view that renders child nodes through {@link LogicAwareRender},
 * providing shared conditional-logic hiding for fieldsets, steps, and field lists.
 *
 * Accepted moduleParams:
 * @param className       - CSS class wrapping the entire children list
 * @param childClassName  - CSS class applied to each child wrapper
 * @param childView       - fallback view name when the child has no `j:view`
 * @param preferCompactStepView - if `"true"`, uses the `compact` view for steps without `j:view`
 * @param hideStepsAfterFirst   - if `"true"`, hides all steps after the first on initial render
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:formContainer",
		name: "hidden.logic"
	},
	(_props, {currentNode, currentResource}) => {
		const elementNodes = Array.from(currentNode.getNodes()) as FormContainerNode[];
		const className = currentResource.getModuleParams().get("className")?.toString();
		const childClassName = currentResource.getModuleParams().get("childClassName")?.toString();
		const childView = currentResource.getModuleParams().get("childView")?.toString();
		const preferCompactStepView = currentResource.getModuleParams().get("preferCompactStepView")?.toString() === "true";
		const hideStepsAfterFirst = currentResource.getModuleParams().get("hideStepsAfterFirst")?.toString() === "true";

		let stepIndex = 0;
		const children = elementNodes.map((elementNode) => {
			const isStep = elementNode.isNodeType("fmdb:step");
			const currentStepIndex = isStep ? stepIndex++ : -1;
			const nodeView = getNodeProps<{ "j:view"?: string }>(elementNode, ["j:view"])["j:view"];
			const fallbackView = isStep && preferCompactStepView ? "compact" : childView;
			const resolvedView = nodeView ?? fallbackView;
			const childParameters = isStep && hideStepsAfterFirst && currentStepIndex > 0
				? {initiallyHidden: "true"}
				: undefined;

			return (
				<LogicAwareRender
					key={elementNode.getIdentifier()}
					node={elementNode}
					view={resolvedView}
					parameters={childParameters}
					className={childClassName}
				/>
			);
		});

		if (!className) {
			return children;
		}

		return <div className={className}>{children}</div>;
	}
);

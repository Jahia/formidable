import {AddContentButtons, getNodeProps, jahiaComponent} from "@jahia/javascript-modules-library";
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
 * @param showLogicHidden       - if `"true"`, logic-driven elements stay visible outside edit
 *                                mode too (the cm inspection view: no script ever reveals them)
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:formContainer",
		name: "hidden.logic"
	},
	(_props, {currentNode, currentResource, renderContext}) => {
		const elementNodes = (Array.from(currentNode.getNodes()) as FormContainerNode[])
			.filter(node => !node.isNodeType("fmdb:logicList"));
		const className = currentResource.getModuleParams().get("className")?.toString();
		const childClassName = currentResource.getModuleParams().get("childClassName")?.toString();
		const childView = currentResource.getModuleParams().get("childView")?.toString();
		const preferCompactStepView = currentResource.getModuleParams().get("preferCompactStepView")?.toString() === "true";
		const hideStepsAfterFirst = currentResource.getModuleParams().get("hideStepsAfterFirst")?.toString() === "true";
		const showLogicHidden = currentResource.getModuleParams().get("showLogicHidden")?.toString() === "true";

		let stepIndex = 0;
		const children = elementNodes.map((elementNode) => {
			const isStep = elementNode.isNodeType("fmdb:step");
			const currentStepIndex = isStep ? stepIndex++ : -1;
			const nodeView = getNodeProps<{ "j:view"?: string }>(elementNode, ["j:view"])["j:view"];
			const fallbackView = isStep && preferCompactStepView ? "compact" : childView;
			const resolvedView = nodeView ?? fallbackView;
			// showLogicHidden travels down: a step or fieldset renders its own children
			// through another hidden.logic pass, which must keep the same visibility rule.
			const childParameters = {
				...(isStep && hideStepsAfterFirst && currentStepIndex > 0 ? {initiallyHidden: "true"} : {}),
				...(showLogicHidden ? {showLogicHidden: "true"} : {})
			};

			return (
				<LogicAwareRender
					key={elementNode.getIdentifier()}
					node={elementNode}
					view={resolvedView}
					parameters={childParameters}
					className={childClassName}
					showLogicHidden={showLogicHidden}
				/>
			);
		});

		// Page Builder: the container's own "New content" buttons — always, not only while empty.
		// Beyond letting an empty step, fieldset or field list receive its first element, this
		// wildcard placeholder is what jContent reads the container's accepted types from
		// (JahiaRenderedModulesUtil.resolveNodeTypes): without it, no insertion point appears
		// between the children (e.g. no "New Form step" between two steps).
		const addButtons = renderContext.isEditMode() ? <AddContentButtons/> : null;

		if (!className) {
			return <>{children}{addButtons}</>;
		}

		return <div className={className}>{children}{addButtons}</div>;
	}
);

import { jahiaComponent } from "@jahia/javascript-modules-library";
import ScaleField, { type ScaleProps } from "./ScaleField";

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:scale",
    name: "default",
  },
  (props: ScaleProps, { currentNode, renderContext }) => (
    <ScaleField
      props={props}
      inputName={currentNode.getName()}
      nodeId={currentNode.getIdentifier()}
      editMode={renderContext.isEditMode()}
    />
  ),
);

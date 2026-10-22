import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
  Render,
} from "@jahia/javascript-modules-library";
import { HelpText, helpTextId } from "@jahia/formidable-library";
import classes from "./twoColumns.module.css";

interface FieldsetProps {
  "jcr:title"?: string;
  helpText?: string;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdb:fieldset",
    name: "twoColumns",
    displayName: "Fieldset - Two columns",
  },
  ({ "jcr:title": title, helpText }: FieldsetProps, { currentNode }) => {
    // A view of its own still renders the help of the fieldset, under the legend, and points at it
    const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;
    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <fieldset aria-describedby={helpId}>
          {title && <legend>{title}</legend>}
          <HelpText id={helpId} text={helpText} />

          <Render
            node={currentNode}
            view="hidden.logic"
            parameters={{
              className: classes.grid,
              childClassName: classes.item,
            }}
          />
        </fieldset>
      </>
    );
  },
);

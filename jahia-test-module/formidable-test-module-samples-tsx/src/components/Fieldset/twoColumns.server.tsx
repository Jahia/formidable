import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
  Render,
} from "@jahia/javascript-modules-library";
import classes from "./twoColumns.module.css";

interface FieldsetProps {
  "jcr:title"?: string;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdb:fieldset",
    name: "twoColumns",
    displayName: "Fieldset - Two columns",
  },
  ({ "jcr:title": title }: FieldsetProps, { currentNode }) => {
    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <fieldset>
          {title && <legend>{title}</legend>}

          <Render
            node={currentNode}
            view="logic.hidden"
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

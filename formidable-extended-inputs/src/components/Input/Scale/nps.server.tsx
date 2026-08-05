import { jahiaComponent } from "@jahia/javascript-modules-library";
import { useTranslation } from "react-i18next";
import ScaleField, { type ScaleProps } from "./ScaleField";

// Dedicated NPS view: forces the standard 0-10 Net Promoter Score presentation.
// Authored min/max labels win over the translated defaults; min/max/step are always 0/10/1.
jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:scale",
    name: "nps",
    displayName: "Scale - Net Promoter Score (0-10)",
  },
  (props: ScaleProps, { currentNode }) => {
    const { t } = useTranslation("formidable-extended-inputs", { keyPrefix: "fmdbext_scale" });
    return (
      <ScaleField
        props={props}
        inputName={currentNode.getName()}
        nodeId={currentNode.getIdentifier()}
        forced={{
          min: 0,
          max: 10,
          step: 1,
          defaultMinLabel: t("npsMinLabel"),
          defaultMaxLabel: t("npsMaxLabel"),
        }}
      />
    );
  },
);

import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
} from "@jahia/javascript-modules-library";
import { type BaseValidationMessageProps, validationDataAttributes } from "@jahia/formidable-library";
import { HelpText, helpTextId } from "@jahia/formidable-library";
import { resolveFieldOptions } from "~/utils/optionsSource.server";
import OptionsSourceError from "~/design/OptionsSourceError";
import "./card.css";

interface CardProps extends BaseValidationMessageProps {
  "jcr:title"?: string;
  "helpText"?: string;
  "options"?: string[];
  "required"?: boolean;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:card",
    name: "default",
  },
  (
    {
      "jcr:title": label,
      helpText,
      options: rawChoices = [],
      required,
      ...validationMsgs
    }: CardProps,
    { currentNode, renderContext },
  ) => {
    const inputName = currentNode.getName();
    const nodeId = currentNode.getIdentifier();
    const { choices, sourceError } = resolveFieldOptions(currentNode, rawChoices, renderContext);
    if (sourceError) {
      return <OptionsSourceError label={label} required={required} />;
    }

    const vAttrs = validationDataAttributes(validationMsgs);
    const helpId = helpText ? helpTextId(nodeId) : undefined;

    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        {/* radiogroup: the explicit role a fieldset of radios needs to carry aria-readonly, which the
            jExperience prefill sets when the author locks the field. data-fmdb-advance-on-change: one
            answer is enough, so a form showing one field at a time may move on once a card is picked */}
        <fieldset
          className="fmdb-form-group fmdbext-card"
          role="radiogroup"
          aria-describedby={helpId}
          aria-label={label ? undefined : inputName}
          data-fmdb-advance-on-change="true"
        >
          {label && (
            <legend className="fmdb-group-legend">
              {label}
              {required && (
                <span className="fmdb-required-indicator" aria-hidden="true">
                  *
                </span>
              )}
            </legend>
          )}
          <HelpText id={helpId} text={helpText} />
          <div className="fmdbext-card-items">
            {choices.map((choice, idx) => {
              const inputId = `card-${nodeId}-${idx}`;
              return (
                <span key={choice.value || String(idx)} className="fmdbext-card-item">
                  <input
                    type="radio"
                    id={inputId}
                    name={inputName}
                    className="fmdb-form-control fmdbext-card-input"
                    value={choice.value}
                    defaultChecked={choice.selected}
                    required={required}
                    {...vAttrs}
                  />
                  {/* The label is the card: the jExperience lock takes the pointer off labels and radios */}
                  <label htmlFor={inputId} className="fmdbext-card-label">
                    {choice.label}
                  </label>
                </span>
              );
            })}
          </div>
        </fieldset>
      </>
    );
  },
);

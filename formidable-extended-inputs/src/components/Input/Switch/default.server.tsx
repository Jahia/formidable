import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
} from "@jahia/javascript-modules-library";
import { type BaseValidationMessageProps, validationDataAttributes } from "~/utils/validationProps";
import HelpText, { helpTextId } from "~/design/HelpText";
import "./switch.css";

interface SwitchProps extends BaseValidationMessageProps {
  "jcr:title"?: string;
  "helpText"?: string;
  "displayMode"?: string;
  "onLabel"?: string;
  "offLabel"?: string;
  "defaultState"?: boolean;
  "required"?: boolean;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:switch",
    name: "default",
  },
  (
    {
      "jcr:title": label,
      helpText,
      displayMode = "toggle",
      onLabel = "Yes",
      offLabel = "No",
      defaultState,
      required,
      ...validationMsgs
    }: SwitchProps,
    { currentNode },
  ) => {
    const inputName = currentNode.getName();
    const nodeId = currentNode.getIdentifier();
    const vAttrs = validationDataAttributes(validationMsgs);
    const helpId = helpText ? helpTextId(nodeId) : undefined;

    if (displayMode === "buttons") {
      const onId = `switch-${nodeId}-on`;
      const offId = `switch-${nodeId}-off`;
      return (
        <>
          <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
          <fieldset className="fmdb-form-group fmdbext-switch" aria-describedby={helpId}>
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
            <div className="fmdbext-switch-buttons">
              <span className="fmdbext-switch-button">
                <input
                  type="radio"
                  id={onId}
                  name={inputName}
                  className="fmdb-form-control fmdbext-switch-input"
                  value="true"
                  defaultChecked={defaultState === true}
                  required={required}
                  {...vAttrs}
                />
                <label htmlFor={onId} className="fmdbext-switch-button-label">
                  {onLabel}
                </label>
              </span>
              <span className="fmdbext-switch-button">
                <input
                  type="radio"
                  id={offId}
                  name={inputName}
                  className="fmdb-form-control fmdbext-switch-input"
                  value="false"
                  defaultChecked={defaultState === false}
                  required={required}
                  {...vAttrs}
                />
                <label htmlFor={offId} className="fmdbext-switch-button-label">
                  {offLabel}
                </label>
              </span>
            </div>
          </fieldset>
        </>
      );
    }

    const inputId = `switch-${nodeId}`;
    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <div className="fmdb-form-group fmdbext-switch">
          <span className="fmdbext-switch-toggle-wrapper">
            <input
              type="checkbox"
              id={inputId}
              name={inputName}
              className="fmdb-form-control fmdbext-switch-input"
              value="true"
              defaultChecked={defaultState}
              required={required}
              aria-describedby={helpId}
              {...vAttrs}
            />
            <label htmlFor={inputId} className="fmdbext-switch-toggle-label">
              <span className="fmdbext-switch-track" aria-hidden="true" />
              <span className="fmdbext-switch-text">
                {label}
                {required && (
                  <span className="fmdb-required-indicator" aria-hidden="true">
                    *
                  </span>
                )}
              </span>
              <span className="fmdbext-switch-state" aria-hidden="true">
                <span className="fmdbext-switch-state-on">{onLabel}</span>
                <span className="fmdbext-switch-state-off">{offLabel}</span>
              </span>
            </label>
          </span>
          <HelpText id={helpId} text={helpText} />
        </div>
      </>
    );
  },
);

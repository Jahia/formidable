import { jahiaComponent } from "@jahia/javascript-modules-library";

interface QuickConfirmProps {
  "jcr:title"?: string;
  "required"?: boolean;
}

/**
 * A field type of a third-party module (fmdbsample:quickConfirm): one checkbox confirming its title.
 * The markup follows the built-in conventions (docs/extension/how-to-extend-views-and-elements-from-third-party-module.md):
 * the node name is the input's name, `input-<nodeId>` its id, the Formidable class hooks. It submits
 * "true" when checked, nothing otherwise, as a single fmdb:checkbox does.
 *
 * data-fmdb-advance-on-change on the element holding the control: checked, the box is the whole answer,
 * so a One-at-a-time form moves to the next screen with the visitor's click — the contract a field type
 * of any module opts into, shown here on a control that is not a radio button.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbsample:quickConfirm",
    name: "default",
  },
  ({ "jcr:title": label, required }: QuickConfirmProps, { currentNode }) => {
    const inputId = `input-${currentNode.getIdentifier()}`;
    return (
      <div className="fmdb-form-group fmdbsample-quick-confirm" data-fmdb-advance-on-change="true">
        <input
          type="checkbox"
          id={inputId}
          name={currentNode.getName()}
          className="fmdb-form-control"
          value="true"
          required={required}
        />
        <label htmlFor={inputId} className="fmdb-checkbox-label">
          {label}
          {required && (
            <span className="fmdb-required-indicator" aria-hidden="true">
              *
            </span>
          )}
        </label>
      </div>
    );
  },
);

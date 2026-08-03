import {
  AddResources,
  buildModuleFileUrl,
  buildNodeUrl,
  jahiaComponent,
} from "@jahia/javascript-modules-library";
import { useTranslation } from "react-i18next";
import { type BaseValidationMessageProps, validationDataAttributes } from "~/utils/validationProps";
import "./consent.css";

interface ConsentProps extends BaseValidationMessageProps {
  "jcr:title"?: string;
  "statement"?: string;
  "termsTarget"?: Parameters<typeof buildNodeUrl>[0];
  "termsLinkLabel"?: string;
  "required"?: boolean;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:consent",
    name: "default",
  },
  (
    { statement, termsTarget, termsLinkLabel, required, ...validationMsgs }: ConsentProps,
    { currentNode },
  ) => {
    const { t } = useTranslation("formidable-extended-inputs", { keyPrefix: "fmdbext_consent" });
    const inputName = currentNode.getName();
    const nodeId = currentNode.getIdentifier();
    const inputId = `consent-${nodeId}`;
    const vAttrs = validationDataAttributes(validationMsgs);

    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <div className="fmdb-form-group fmdbext-consent">
          <input
            type="checkbox"
            id={inputId}
            name={inputName}
            className="fmdb-form-control fmdbext-consent-input"
            value="true"
            required={required}
            {...vAttrs}
          />
          <label htmlFor={inputId} className="fmdbext-consent-label">
            {/* statement is contributor-authored rich text — same trust model as fmdb:richText */}
            {statement && (
              <span
                className="fmdbext-consent-statement"
                dangerouslySetInnerHTML={{ __html: statement }}
              />
            )}
            {required && (
              <span className="fmdb-required-indicator" aria-hidden="true">
                *
              </span>
            )}
          </label>
          {termsTarget && (
            <a
              className="fmdbext-consent-terms-link"
              href={buildNodeUrl(termsTarget)}
              target="_blank"
              rel="noopener noreferrer"
            >
              {termsLinkLabel || t("defaultTermsLinkLabel")}
            </a>
          )}
        </div>
      </>
    );
  },
);

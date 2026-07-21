import { enableModule } from "@jahia/cypress";
import {
  createPublishedLiveFormPage,
  FORMIDABLE_TEST_SITE,
  getConsentNode,
  getRatingNode,
  getScaleNode,
  getSwitchNode,
} from "../../support/fixtures";
import {
  expectErrorResponse,
  expectSuccessResponse,
  postDirectMultipartSubmission,
  useFormidableSite,
  withSameOriginHeaders,
} from "./support";

/**
 * Server-side enforcement of the formidable-extended-inputs field constraints.
 *
 * These specs bypass the browser entirely (direct multipart POST to the submit servlet) to prove
 * that the JavaScript field validators — registered via registerFormFieldValidator and executed by
 * the submission pipeline through the js-modules SDK — reject forged values with FMDB-010.
 *
 * Requires the formidable-extended-inputs module (and a javascript-modules engine exposing
 * JSServerExtensionInvoker); without them, forged values would be accepted as free text.
 */

const EXTENDED_INPUTS_MODULE_ID = "formidable-extended-inputs";
const FIELD_VALIDATION_ERROR = "FMDB-010";

describe("Security - extended inputs server-side validation", () => {
  useFormidableSite();

  let formId: string;

  before(() => {
    cy.login();

    enableModule(EXTENDED_INPUTS_MODULE_ID, FORMIDABLE_TEST_SITE.key);

    createPublishedLiveFormPage(
      "extended-validation-form",
      "Extended validation form",
      [
        getRatingNode({ name: "satisfaction", title: "Satisfaction", maxValue: 5 }),
        getScaleNode({ name: "effort", title: "Effort", minValue: 1, maxValue: 7, step: 1 }),
        getScaleNode({
          name: "evenScale",
          title: "Even scale",
          minValue: 0,
          maxValue: 10,
          step: 2,
        }),
        getSwitchNode({ name: "newsletter", title: "Newsletter" }),
        getConsentNode({ name: "consent", title: "Consent", required: true }),
      ],
      "extended-validation-page",
      "Extended validation page",
    ).then((info) => {
      formId = info.formId;
    });

    cy.logout();
  });

  const submit = (fields: Record<string, string>) => {
    cy.logout();
    return postDirectMultipartSubmission({
      formId,
      fields,
      headers: withSameOriginHeaders(),
    });
  };

  it("accepts a submission with valid values", () => {
    submit({
      satisfaction: "4",
      effort: "6",
      evenScale: "8",
      newsletter: "true",
      consent: "true",
    }).then(expectSuccessResponse);
  });

  it("accepts boundary values (rating max, scale min, switch false)", () => {
    submit({
      satisfaction: "5",
      effort: "1",
      evenScale: "0",
      newsletter: "false",
      consent: "true",
    }).then(expectSuccessResponse);
  });

  ["999", "0", "abc", "3.5", "-1"].forEach((forged) => {
    it(`rejects a forged rating value '${forged}'`, () => {
      submit({ satisfaction: forged, consent: "true" }).then((response) =>
        expectErrorResponse(response, 400, FIELD_VALIDATION_ERROR),
      );
    });
  });

  ["0", "8", "99", "abc"].forEach((forged) => {
    it(`rejects a forged scale value '${forged}' (bounds 1..7)`, () => {
      submit({ effort: forged, consent: "true" }).then((response) =>
        expectErrorResponse(response, 400, FIELD_VALIDATION_ERROR),
      );
    });
  });

  it("rejects a scale value off the configured step grid", () => {
    // evenScale accepts 0,2,4,6,8,10 — 7 is inside the bounds but off-step.
    submit({ evenScale: "7", consent: "true" }).then((response) =>
      expectErrorResponse(response, 400, FIELD_VALIDATION_ERROR),
    );
  });

  ["maybe", "1", "TRUE"].forEach((forged) => {
    it(`rejects a forged switch value '${forged}'`, () => {
      submit({ newsletter: forged, consent: "true" }).then((response) =>
        expectErrorResponse(response, 400, FIELD_VALIDATION_ERROR),
      );
    });
  });

  ["yes", "false", "1"].forEach((forged) => {
    it(`rejects a forged consent value '${forged}'`, () => {
      submit({ consent: forged }).then((response) =>
        expectErrorResponse(response, 400, FIELD_VALIDATION_ERROR),
      );
    });
  });

  it("rejects a submission missing the required consent", () => {
    submit({ satisfaction: "3" }).then((response) =>
      expectErrorResponse(response, 400, FIELD_VALIDATION_ERROR),
    );
  });
});

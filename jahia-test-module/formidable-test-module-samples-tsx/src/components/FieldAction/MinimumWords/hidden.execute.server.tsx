import { jahiaComponent } from "@jahia/javascript-modules-library";
import { fieldActionResult, readFieldActionRequest } from "@jahia/formidable-library";

interface MinimumWordsActionProps {
  /** Absent on a node saved without it: the CND default stands in. */
  minimumWords?: number;
}

/**
 * A field action written in JavaScript — the sample to copy (docs/architecture/field-actions.md,
 * "A field action in JavaScript"). The engine renders this view with the candidate value in a request
 * attribute, as the visitor leaves the field and again at submission, and reads its whole output as
 * one verdict: `readFieldActionRequest` hands the request over, `fieldActionResult` writes the
 * verdict in the form the engine reads — the view returns one of the three and nothing else, never
 * the value. The visitor's message is not this view's business: the contributor writes it on the
 * node, the engine renders it. Never cached (`cache.expiration` 0): a verdict is about one value.
 * A direct hit of the view through the render servlet finds no request and answers nothing.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbsample:minimumWordsAction",
    name: "hidden.execute",
    properties: { "cache.expiration": "0" },
  },
  ({ minimumWords = 3 }: MinimumWordsActionProps, { renderContext }) => {
    const request = readFieldActionRequest(renderContext);
    if (!request) return null;
    const words = request.value.trim().split(/\s+/).filter(Boolean).length;
    return words >= minimumWords
      ? fieldActionResult.accept()
      : fieldActionResult.reject(`${words} word(s), ${minimumWords} needed`);
  },
);

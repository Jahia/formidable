import { AddResources, jahiaComponent } from "@jahia/javascript-modules-library";

/**
 * The site's resources for a content rendered without a page of its own (preview, Page Builder of a
 * form from a content folder): the platform's content template renders the site node with this view
 * and puts what it declares in the document head. This test template set has no stylesheet, so it
 * declares an inert marker a test can look for in the frame head, and nothing visible.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "jnt:virtualsite",
    name: "hidden.resources",
  },
  () => (
    <AddResources
      type="inline"
      key="formidable-test-site-resources"
      inlineResource='<style data-site-resources="formidable-test-module-templateset-tsx">:root{--fmdb-test-site-resources:1}</style>'
    />
  ),
);

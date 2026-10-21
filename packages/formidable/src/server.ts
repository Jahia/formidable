// The server-side part of the package, under its own entry point
// (`@jahia/formidable-library/server`): it calls the engine through the GraalVM bridge of
// `@jahia/javascript-modules-library`, which a client island must never pull into its bundle.
// The admission criterion of index.ts holds here too.
//
// - `resolveFieldOptions`: the options a choice field renders, in every options mode — the
//   manual options aligned on the site's default language (what the submission validation
//   accepts), the sourced, category and content options resolved by the engine, and the cache
//   dependencies that keep a rendered field current.
export {resolveFieldOptions, type FieldOptions, type ChoiceFieldNode} from "./optionsSource.js";

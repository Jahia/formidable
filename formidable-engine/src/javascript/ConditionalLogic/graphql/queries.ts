import {JCR_NODE_IDENTITY_SOURCE} from '../../graphql';

/**
 * Everything the rules editor needs, in ONE operation: the edited field, the logic source references
 * its `logicsSrc` subnode holds, and — through the field's `fmdb:form` ancestor — the whole form tree
 * the source dropdown offers. Two queries in sequence before (the field, then the form at the path the
 * first answered), each paying the editor client's batching window; the site default language, which
 * holds the option identity of choice fields, now comes from the editor context. The flags on each
 * descendant come from the engine's value-kind markers, so a third-party field opts in from its own
 * CND (docs/extension/how-to-extend-views-and-elements-from-third-party-module.md). A plain document
 * source: ConditionalLogic/sources.ts posts it outside the editor's Apollo client.
 */
export const CONDITIONAL_LOGIC_SOURCES = `
    query ConditionalLogicSources($path: String!, $workspace: Workspace!, $language: String!, $defaultLanguage: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                ...JcrNodeIdentity
                descendant(relPath: "logicsSrc") {
                    children {
                        nodes {
                            ...JcrNodeIdentity
                            property(name: "logicNodeSource") {
                                refNode {
                                    ...JcrNodeIdentity
                                }
                            }
                        }
                    }
                }
                ancestors(fieldFilter: {filters: [{fieldName: "primaryNodeType.name", value: "fmdb:form"}]}) {
                    ...JcrNodeIdentity
                    primaryNodeType { name }
                    descendants(
                        typesFilter: {types: ["fmdbmix:formElement", "fmdbmix:formStep"], multi: ANY}
                    ) {
                        nodes {
                            ...JcrNodeIdentity
                            displayName(language: $language)
                            primaryNodeType { name }
                            isChoiceField: isNodeType(type: {types: ["fmdbmix:choiceField"]})
                            isDateField: isNodeType(type: {types: ["fmdbmix:dateField"]})
                            isNumberField: isNodeType(type: {types: ["fmdbmix:numberField"]})
                            isBooleanField: isNodeType(type: {types: ["fmdbmix:booleanField"]})
                            isTextField: isNodeType(type: {types: ["fmdbmix:textField"]})
                            properties(names: ["options", "fieldKey"], language: $language) {
                                name
                                value
                                values
                            }
                            defaultProperties: properties(names: ["options"], language: $defaultLanguage) {
                                name
                                values
                            }
                        }
                    }
                }
            }
        }
    }
    ${JCR_NODE_IDENTITY_SOURCE}
`;

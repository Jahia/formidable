import {gql} from '@apollo/client';

/**
 * The identity every JCR node selection carries, as the Results page's own app declares it (formidable-engine,
 * graphql/fragments.ts): uuid and workspace key the editor's Apollo cache, name and path are what the code
 * reads. Copied, not shared: the two apps are federated bundles of their own.
 */
const JCR_NODE_IDENTITY = gql`
    fragment JcrNodeIdentity on GenericJCRNode {
        uuid
        workspace
        name
        path
    }
`;

/** The fields of a form, in tree order, with what the mapping of an attachment reads of each: as the Results page reads them. */
export const GET_FORM_FIELD_LABELS = gql`
    ${JCR_NODE_IDENTITY}
    query GetFormFieldLabels($formUuid: String!, $language: String!, $optionsLanguage: String, $workspace: Workspace = LIVE) {
        jcr(workspace: $workspace) {
            nodeById(uuid: $formUuid) {
                ...JcrNodeIdentity
                fields: children(names: ["fields"]) {
                    nodes {
                        ...JcrNodeIdentity
                        descendants(
                            typesFilter: {types: ["fmdbmix:formElement"], multi: ANY}
                        ) {
                            nodes {
                                ...JcrNodeIdentity
                                name
                                displayName(language: $language)
                                isDate: isNodeType(type: {types: ["fmdbmix:dateField"]})
                                isDatetime: isNodeType(type: {types: ["fmdbmix:datetimeLocalField"]})
                                nonSubmittable: isNodeType(type: {types: ["fmdbmix:nonSubmittable"]})
                                options: property(name: "options", language: $optionsLanguage) {
                                    values
                                }
                            }
                        }
                    }
                }
            }
        }
    }
`;

/**
 * The forms of the site the imported results can attach to, unpublished ones included, and the default
 * language of the site, where the options of a choice field are always held: the statement comes from
 * buildSiteFormsQuery (import.utils.ts), the variables from siteFormsVariables.
 */
export const GET_SITE_FORMS = gql`
    ${JCR_NODE_IDENTITY}
    query GetSiteForms($formsQuery: String!, $language: String!, $sitePath: String!) {
        jcr(workspace: EDIT) {
            nodesByQuery(query: $formsQuery, queryLanguage: SQL2) {
                nodes {
                    ...JcrNodeIdentity
                    displayName(language: $language)
                }
            }
            site: nodeByPath(path: $sitePath) {
                ...JcrNodeIdentity
                defaultLanguage: property(name: "j:defaultLanguage") {
                    value
                }
            }
        }
    }
`;

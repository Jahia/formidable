import {gql} from '@apollo/client';
import {JCR_NODE_IDENTITY} from '../../graphql';

export const CURRENT_NODE_BY_PATH = gql`
    ${JCR_NODE_IDENTITY}
    query ConditionalLogicCurrentNodeByPath($path: String!, $workspace: Workspace!, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                ...JcrNodeIdentity
                displayName(language: $language)
                primaryNodeType { name }
                ancestors(fieldFilter: {filters: [{fieldName: "primaryNodeType.name", value: "fmdb:form"}]}) {
                    ...JcrNodeIdentity
                    primaryNodeType { name }
                }
            }
        }
    }
`;

export const FORM_TREE_BY_PATH = gql`
    ${JCR_NODE_IDENTITY}
    query ConditionalLogicFormTreeByPath($path: String!, $workspace: Workspace!, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                ...JcrNodeIdentity
                descendants(
                    typesFilter: {types: ["fmdbmix:formElement", "fmdbmix:formStep"], multi: ANY}
                ) {
                    nodes {
                        ...JcrNodeIdentity
                        displayName(language: $language)
                        primaryNodeType { name }
                        properties(names: ["choices", "options"], language: $language) {
                            name
                            value
                            values
                        }
                    }
                }
            }
        }
    }
`;


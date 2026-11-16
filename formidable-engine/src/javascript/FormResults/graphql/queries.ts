import {gql} from '@apollo/client';
import {JCR_NODE_IDENTITY} from '../../graphql';

export const GET_FORM_RESULTS_LIST = gql`
    ${JCR_NODE_IDENTITY}
    query GetFormResultsList($resultsPath: String!, $workspace: Workspace = LIVE, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $resultsPath) {
                ...JcrNodeIdentity
                children(typesFilter: {types: ["fmdb:formResults"]}) {
                    nodes {
                        ...JcrNodeIdentity
                        displayName(language: $language)
                        canRemoveNode: hasPermission(permissionName: "jcr:removeNode")
                        submissionsContainer: children(names: ["submissions"]) {
                            nodes {
                                ...JcrNodeIdentity
                                canRemoveNode: hasPermission(permissionName: "jcr:removeNode")
                                canRemoveChildNodes: hasPermission(permissionName: "jcr:removeChildNodes")
                            }
                        }
                        parentForm: property(name: "parentForm") {
                            value
                            refNode {
                                ...JcrNodeIdentity
                                displayName(language: $language)
                            }
                        }
                    }
                }
            }
        }
    }
`;

export const GET_SUBMISSIONS = gql`
    ${JCR_NODE_IDENTITY}
    query GetSubmissions(
        $submissionsQuery: String!
        $limit: Int!
        $offset: Int!
        $workspace: Workspace = LIVE
    ) {
        jcr(workspace: $workspace) {
            nodesByQuery(
                query: $submissionsQuery
                queryLanguage: SQL2
                limit: $limit
                offset: $offset
            ) {
                pageInfo {
                    totalCount
                    hasNextPage
                }
                nodes {
                    ...JcrNodeIdentity
                    created: property(name: "jcr:created") {
                        value
                    }
                    origin: property(name: "origin") {
                        value
                    }
                    locale: property(name: "locale") {
                        value
                    }
                    referer: property(name: "referer") {
                        value
                    }
                    timeZone: property(name: "timeZone") {
                        value
                    }
                    data: children(names: ["data"]) {
                        nodes {
                            ...JcrNodeIdentity
                            properties {
                                name
                                value
                                values
                            }
                        }
                    }
                    files: children(names: ["files"]) {
                        nodes {
                            ...JcrNodeIdentity
                            children {
                                nodes {
                                    ...JcrNodeIdentity
                                    children {
                                        nodes {
                                            ...JcrNodeIdentity
                                            url
                                            thumbnailUrl(name: "thumbnail", checkIfExists: true)
                                            content: children(names: ["jcr:content"]) {
                                                nodes {
                                                    ...JcrNodeIdentity
                                                    mimeType: property(name: "jcr:mimeType") {
                                                        value
                                                    }
                                                }
                                            }
                                        }
                                    }
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
 * The submissions as the analytics read them: the date, the language, the page and the values, and for the
 * files only the fields that hold one. Lighter than GET_SUBMISSIONS, since every submission of a period is read.
 */
export const GET_SUBMISSIONS_FOR_ANALYTICS = gql`
    ${JCR_NODE_IDENTITY}
    query GetSubmissionsForAnalytics(
        $submissionsQuery: String!
        $limit: Int!
        $offset: Int!
        $workspace: Workspace = LIVE
    ) {
        jcr(workspace: $workspace) {
            nodesByQuery(
                query: $submissionsQuery
                queryLanguage: SQL2
                limit: $limit
                offset: $offset
            ) {
                pageInfo {
                    totalCount
                }
                nodes {
                    ...JcrNodeIdentity
                    created: property(name: "jcr:created") {
                        value
                    }
                    locale: property(name: "locale") {
                        value
                    }
                    referer: property(name: "referer") {
                        value
                    }
                    data: children(names: ["data"]) {
                        nodes {
                            ...JcrNodeIdentity
                            properties {
                                name
                                value
                                values
                            }
                        }
                    }
                    files: children(names: ["files"]) {
                        nodes {
                            ...JcrNodeIdentity
                            children {
                                nodes {
                                    ...JcrNodeIdentity
                                }
                            }
                        }
                    }
                }
            }
        }
    }
`;

export const GET_SUBMISSION_COUNT = gql`
    query GetSubmissionCount($countQuery: String!, $workspace: Workspace = LIVE) {
        jcr(workspace: $workspace) {
            nodesByQuery(
                query: $countQuery
                queryLanguage: SQL2
                limit: 1
                offset: 0
            ) {
                pageInfo {
                    totalCount
                }
            }
        }
    }
`;

export const DELETE_SUBMISSIONS = gql`
    mutation DeleteSubmissions($submissionsQuery: String!, $workspace: Workspace = LIVE) {
        jcr(workspace: $workspace) {
            mutateNodesByQuery(
                query: $submissionsQuery
                queryLanguage: SQL2
            ) {
                delete
            }
        }
    }
`;

/**
 * Removes a form's whole entry from the Results page: the fmdb:formResults node with its ACL
 * and whatever submissions it still holds. The form's next submission recreates the node.
 */
export const DELETE_FORM_RESULTS = gql`
    mutation DeleteFormResults($pathOrId: String!, $workspace: Workspace = LIVE) {
        jcr(workspace: $workspace) {
            deleteNode(pathOrId: $pathOrId)
        }
    }
`;

/**
 * The forms, among those whose live reference does not resolve, that still stand in EDIT: their
 * entries are "not published" rather than "deleted". The statement comes from buildFormsInEditQuery.
 */
export const GET_FORMS_IN_EDIT = gql`
    ${JCR_NODE_IDENTITY}
    query GetFormsInEdit($formsQuery: String!, $language: String!) {
        jcr(workspace: EDIT) {
            nodesByQuery(query: $formsQuery, queryLanguage: SQL2) {
                nodes {
                    ...JcrNodeIdentity
                    displayName(language: $language)
                }
            }
        }
    }
`;

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
                                isSubmittable: isNodeType(type: {types: ["fmdbmix:submittableField"]})
                                isSensitive: isNodeType(type: {types: ["fmdbmix:sensitiveField"]})
                                isChoice: isNodeType(type: {types: ["fmdbmix:choiceField"]})
                                isBoolean: isNodeType(type: {types: ["fmdbmix:booleanField"]})
                                isNumber: isNodeType(type: {types: ["fmdbmix:numberField"]})
                                isFile: isNodeType(type: {types: ["fmdbmix:fileField"]})
                                options: property(name: "options", language: $optionsLanguage) {
                                    values
                                }
                            }
                        }
                        logicElements: descendants(typesFilter: {types: ["fmdbmix:formLogicElement"]}) {
                            nodes {
                                ...JcrNodeIdentity
                                fieldKey: property(name: "fieldKey") {
                                    value
                                }
                                logics: property(name: "logics") {
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


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
                        parentForm: property(name: "parentForm") {
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
                    ipAddress: property(name: "ipAddress") {
                        value
                    }
                    locale: property(name: "locale") {
                        value
                    }
                    submitterUsername: property(name: "submitterUsername") {
                        value
                    }
                    userAgent: property(name: "userAgent") {
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


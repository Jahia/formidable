import {gql} from '@apollo/client';

export const GET_FORM_RESULTS_LIST = gql`
    query GetFormResultsList($resultsPath: String!, $workspace: Workspace = LIVE, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $resultsPath) {
                children(typesFilter: {types: ["fmdb:formResults"]}) {
                    nodes {
                        uuid
                        path
                        name
                        displayName(language: $language)
                        parentForm: property(name: "parentForm") {
                            refNode {
                                uuid
                                path
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
                    uuid
                    path
                    name
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
                            properties {
                                name
                                value
                                values
                            }
                        }
                    }
                    files: children(names: ["files"]) {
                        nodes {
                            children {
                                nodes {
                                    name
                                    children {
                                        nodes {
                                            uuid
                                            name
                                            path
                                            thumbnailUrl(name: "thumbnail", checkIfExists: true)
                                            content: children(names: ["jcr:content"]) {
                                                nodes {
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

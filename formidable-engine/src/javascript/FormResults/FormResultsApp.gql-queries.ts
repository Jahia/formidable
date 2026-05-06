import {gql} from '@apollo/client';

export const GET_FORM_RESULTS_LIST = gql`
    query GetFormResultsList($resultsPath: String!) {
        jcr {
            nodeByPath(path: $resultsPath) {
                children(typesFilter: {types: ["fmdb:formResults"]}) {
                    nodes {
                        uuid
                        path
                        name
                        displayName
                        parentForm: property(name: "parentForm") {
                            refNode {
                                uuid
                                path
                                displayName
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
    ) {
        jcr {
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
                    status: property(name: "status") {
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
    query GetSubmissionCount($countQuery: String!) {
        jcr {
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


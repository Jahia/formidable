import {gql} from '@apollo/client';
import type {QuarantineOperation, QuarantineState} from './quarantine.utils';

/** The counts per state and the forms of the filter, never read again on a change of page, filter or order. */
export const GET_QUARANTINE_SUMMARY = gql`
    query GetFormidableQuarantineSummary($siteKey: String!) {
        formidableQuarantine(siteKey: $siteKey) {
            siteKey
            counts {
                state
                count
            }
            forms {
                formId
                formTitle
                count
            }
        }
    }
`;

/** One page of the list. */
export const GET_QUARANTINE_LIST = gql`
    query GetFormidableQuarantineList(
        $siteKey: String!, $formId: String, $states: [FmdbqState!], $offset: Int!, $newestFirst: Boolean
    ) {
        formidableQuarantine(siteKey: $siteKey) {
            siteKey
            submissions(formId: $formId, states: $states, offset: $offset, limit: 25, newestFirst: $newestFirst) {
                totalCount
                offset
                items {
                    id
                    formId
                    formTitle
                    createdAt
                    state
                    attempts
                    reason
                    files {
                        item
                        name
                    }
                }
            }
        }
    }
`;

const HELD_DETAIL = gql`
    fragment FmdbqHeldDetail on FmdbqHeldSubmission {
        id
        formId
        formTitle
        createdAt
        state
        attempts
        reason
        files {
            item
            name
            mimeType
            size
            bytesPresent
            checks {
                name
                verdict
                reasonCode
            }
        }
        decision {
            reason
            item
            check
            at
        }
        audit {
            operation
            user
            from
            to
            at
        }
        publicValues {
            name
            values
        }
        sensitiveFields
        allowedOperations
    }
`;

/** The detail of one held submission. The sensitive values are not a field of the API: only their names. */
export const GET_QUARANTINE_SUBMISSION = gql`
    ${HELD_DETAIL}
    query GetFormidableQuarantineSubmission($siteKey: String!, $id: String!) {
        formidableQuarantine(siteKey: $siteKey) {
            siteKey
            submission(id: $id) {
                ...FmdbqHeldDetail
            }
        }
    }
`;

/** An operation of a manager: the answer is the submission as the operation left it. */
export const OPERATE_QUARANTINE_SUBMISSION = gql`
    ${HELD_DETAIL}
    mutation OperateFormidableQuarantineSubmission($siteKey: String!, $id: String!, $operation: FmdbqOperation!) {
        formidableQuarantine(siteKey: $siteKey) {
            operate(id: $id, operation: $operation) {
                ...FmdbqHeldDetail
            }
        }
    }
`;

export interface StateCount {
    state: QuarantineState;
    count: number;
}

export interface FormEntry {
    formId: string;
    formTitle: string | null;
    count: number;
}

export interface SubmissionRow {
    id: string;
    formId: string;
    formTitle: string | null;
    createdAt: string;
    state: QuarantineState;
    attempts: number;
    reason: string | null;
    files: Array<{item: string; name: string | null}>;
}

export interface QuarantineSummary {
    siteKey: string;
    counts: StateCount[];
    forms: FormEntry[];
}

export interface SummaryData {
    formidableQuarantine: QuarantineSummary | null;
}

export interface SubmissionPage {
    totalCount: number;
    offset: number;
    items: SubmissionRow[];
}

export interface ListData {
    formidableQuarantine: {siteKey: string; submissions: SubmissionPage} | null;
}

export interface CheckOutcome {
    name: string;
    verdict: string;
    reasonCode: string | null;
}

export interface HeldFile {
    item: string;
    name: string | null;
    mimeType: string | null;
    size: number | null;
    bytesPresent: boolean;
    checks: CheckOutcome[];
}

export interface HeldDetail {
    id: string;
    formId: string;
    formTitle: string | null;
    createdAt: string;
    state: QuarantineState;
    attempts: number;
    reason: string | null;
    files: HeldFile[];
    decision: {reason: string; item: string | null; check: string | null; at: string | null} | null;
    audit: Array<{operation: string; user: string | null; from: string; to: string; at: string}>;
    publicValues: Array<{name: string; values: string[]}> | null;
    sensitiveFields: string[];
    allowedOperations: QuarantineOperation[];
}

export interface DetailData {
    formidableQuarantine: {siteKey: string; submission: HeldDetail | null} | null;
}

export interface OperateData {
    formidableQuarantine: {operate: HeldDetail} | null;
}

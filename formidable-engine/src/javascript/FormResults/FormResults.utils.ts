export interface FormResultsNode {
    uuid: string;
    path: string;
    name: string;
    displayName: string;
    parentForm: {
        refNode: {
            uuid: string;
            path: string;
            displayName: string;
        } | null;
    } | null;
    submissionCount?: number;
}

export interface SubmissionFieldValue {
    name: string;
    values: string[];
}

interface SubmissionProperty {
    name?: string;
    value?: unknown;
    values?: unknown;
}

export interface SubmissionFile {
    fieldName: string;
    fileName: string;
    fileUuid: string;
    filePath: string;
    fileUrl: string;
    mimeType: string | null;
    thumbnailUrl: string | null;
}

export interface SubmissionRow {
    uuid: string;
    path: string;
    name: string;
    created: string;
    origin: string | null;
    ipAddress: string | null;
    locale: string | null;
    submitterUsername: string | null;
    userAgent: string | null;
    referer: string | null;
    fieldValues: SubmissionFieldValue[];
    files: SubmissionFile[];
}

export interface SubmissionQueryFilters {
    startDate?: string;
    endDate?: string;
}

const JCR_PROPERTY_PREFIXES = ['jcr:', 'j:', 'mix:'];

export function isUserProperty(name: string): boolean {
    return !JCR_PROPERTY_PREFIXES.some(prefix => name.startsWith(prefix));
}

function normalizePropertyValues(property: SubmissionProperty): string[] {
    if (Array.isArray(property.values) && property.values.length > 0) {
        return property.values
            .filter(value => value != null && value !== '')
            .map(value => String(value));
    }

    if (property.value == null || property.value === '') {
        return [];
    }

    return [String(property.value)];
}

export function parseSubmissionNode(node: any): SubmissionRow {
    const fieldValues: SubmissionFieldValue[] = [];
    const dataNode = node.data?.nodes?.[0];
    const properties = dataNode?.properties as SubmissionProperty[] | undefined;
    if (Array.isArray(properties)) {
        for (const prop of properties) {
            const name = typeof prop.name === 'string' ? prop.name : '';
            if (name && isUserProperty(name)) {
                fieldValues.push({name, values: normalizePropertyValues(prop)});
            }
        }
    }

    const files: SubmissionFile[] = [];
    const filesNode = node.files?.nodes?.[0];
    if (filesNode?.children?.nodes) {
        for (const fieldFolder of filesNode.children.nodes) {
            for (const fileNode of fieldFolder.children?.nodes ?? []) {
                files.push({
                    fieldName: fieldFolder.name,
                    fileName: fileNode.name,
                    fileUuid: fileNode.uuid,
                    filePath: fileNode.path,
                    fileUrl: fileNode.url ?? '',
                    mimeType: fileNode.content?.nodes?.[0]?.mimeType?.value ?? null,
                    thumbnailUrl: fileNode.thumbnailUrl ?? null
                });
            }
        }
    }

    return {
        uuid: node.uuid,
        path: node.path,
        name: node.name,
        created: node.created?.value ?? '',
        origin: node.origin?.value ?? null,
        ipAddress: node.ipAddress?.value ?? null,
        locale: node.locale?.value ?? null,
        submitterUsername: node.submitterUsername?.value ?? null,
        userAgent: node.userAgent?.value ?? null,
        referer: node.referer?.value ?? null,
        fieldValues,
        files
    };
}

const toJcrDateStart = (dateValue: string): string => {
    const [year, month, day] = dateValue.split('-').map(Number);
    return new Date(year, month - 1, day, 0, 0, 0, 0).toISOString();
};

const toJcrDateEndExclusive = (dateValue: string): string => {
    const [year, month, day] = dateValue.split('-').map(Number);
    return new Date(year, month - 1, day + 1, 0, 0, 0, 0).toISOString();
};

export function buildSubmissionsQuery(
    formResultsPath: string,
    sortBy: string,
    sortDirection: string,
    filters: SubmissionQueryFilters = {}
): string {
    const orderDirection = sortDirection === 'ascending' ? 'ASC' : 'DESC';
    const orderColumn = sortBy === 'created' ? 's.[jcr:created]' : `s.[${sortBy}]`;
    const whereClauses = [`ISDESCENDANTNODE(s, '${formResultsPath}/submissions')`];

    if (filters.startDate) {
        whereClauses.push(`s.[jcr:created] >= CAST('${toJcrDateStart(filters.startDate)}' AS DATE)`);
    }

    if (filters.endDate) {
        whereClauses.push(`s.[jcr:created] < CAST('${toJcrDateEndExclusive(filters.endDate)}' AS DATE)`);
    }

    return `SELECT * FROM [fmdb:formSubmission] AS s WHERE ${whereClauses.join(' AND ')} ORDER BY ${orderColumn} ${orderDirection}`;
}

export function buildCountQuery(formResultsPath: string): string {
    return `SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '${formResultsPath}/submissions')`;
}

export function formatDate(isoDate: string): string {
    if (!isoDate) {
        return '';
    }

    try {
        const date = new Date(isoDate);
        return date.toLocaleString();
    } catch {
        return isoDate;
    }
}

export function formatFileSize(bytes: number | null): string {
    if (bytes == null) {
        return '';
    }

    if (bytes < 1024) {
        return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}




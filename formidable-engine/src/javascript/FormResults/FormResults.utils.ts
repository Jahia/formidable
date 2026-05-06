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

export interface SubmissionFile {
    fieldName: string;
    fileName: string;
    fileUuid: string;
    filePath: string;
    mimeType: string | null;
}

export interface SubmissionRow {
    uuid: string;
    path: string;
    name: string;
    created: string;
    origin: string | null;
    status: string | null;
    ipAddress: string | null;
    locale: string | null;
    submitterUsername: string | null;
    userAgent: string | null;
    referer: string | null;
    fieldValues: SubmissionFieldValue[];
    files: SubmissionFile[];
}

const JCR_PROPERTY_PREFIXES = ['jcr:', 'j:', 'mix:'];

export function isUserProperty(name: string): boolean {
    return !JCR_PROPERTY_PREFIXES.some(prefix => name.startsWith(prefix));
}

export function parseSubmissionNode(node: any): SubmissionRow {
    const fieldValues: SubmissionFieldValue[] = [];
    const dataNode = node.data?.nodes?.[0];
    const properties = dataNode?.properties;
    if (Array.isArray(properties)) {
        for (const prop of properties) {
            if (isUserProperty(prop.name)) {
                fieldValues.push({name: prop.name, values: prop.values ?? []});
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
                    mimeType: fileNode.content?.nodes?.[0]?.mimeType?.value ?? null
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
        status: node.status?.value ?? null,
        ipAddress: node.ipAddress?.value ?? null,
        locale: node.locale?.value ?? null,
        submitterUsername: node.submitterUsername?.value ?? null,
        userAgent: node.userAgent?.value ?? null,
        referer: node.referer?.value ?? null,
        fieldValues,
        files
    };
}

export function buildSubmissionsQuery(formResultsPath: string, sortBy: string, sortDirection: string): string {
    const orderDirection = sortDirection === 'ascending' ? 'ASC' : 'DESC';
    const orderColumn = sortBy === 'created' ? 's.[jcr:created]' : `s.[${sortBy}]`;

    return `SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '${formResultsPath}/submissions') ORDER BY ${orderColumn} ${orderDirection}`;
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

export function buildDownloadUrl(filePath: string): string {
    const ctx = (window as any).contextJsParameters?.contextPath ?? '';
    return `${ctx}/files/default${filePath}`;
}




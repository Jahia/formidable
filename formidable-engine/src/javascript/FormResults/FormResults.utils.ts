export interface FormResultsNode {
    uuid: string;
    path: string;
    name: string;
    displayName: string;
    submissionsContainer?: {
        nodes?: Array<{
            canRemoveNode?: boolean;
            canRemoveChildNodes?: boolean;
        }>;
    };
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
    locale: string | null;
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

/**
 * What the results screen knows about the source form: the label of each field in
 * the UI language, and the order in which the fields are displayed in the form.
 */
export interface FormFields {
    labels: Map<string, string>;
    order: string[];
}

export const EMPTY_FORM_FIELDS: FormFields = {labels: new Map(), order: []};

/**
 * Sorts items by the position of their field in the form. Fields the form no longer
 * has (deleted or renamed since the submission) keep their stored order, after the
 * known ones, so nothing disappears from the results.
 */
export function sortByFormOrder<T>(items: T[], getFieldName: (item: T) => string, order: string[]): T[] {
    if (order.length === 0) {
        return items;
    }

    const position = new Map(order.map((name, index) => [name, index]));
    return items
        .map((item, index) => ({item, index, rank: position.get(getFieldName(item)) ?? order.length + index}))
        .sort((a, b) => a.rank - b.rank)
        .map(({item}) => item);
}

export function parseSubmissionNode(node: any, fieldOrder: string[] = []): SubmissionRow {
    const fieldValues: SubmissionFieldValue[] = [];
    const dataNode = node.data?.nodes?.[0];
    const properties = dataNode?.properties as SubmissionProperty[] | undefined;

    if (Array.isArray(properties)) {
        for (const prop of properties) {
            const name = typeof prop.name === 'string' ? prop.name : '';
            if (name && isUserProperty(name)) {
                fieldValues.push({
                    name,
                    values: normalizePropertyValues(prop)
                });
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
        locale: node.locale?.value ?? null,
        referer: node.referer?.value ?? null,
        fieldValues: sortByFormOrder(fieldValues, field => field.name, fieldOrder),
        files: sortByFormOrder(files, file => file.fieldName, fieldOrder)
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

function buildSubmissionWhereClauses(formResultsPath: string, filters: SubmissionQueryFilters = {}): string[] {
    const whereClauses = [`ISDESCENDANTNODE(s, '${formResultsPath}/submissions')`];

    if (filters.startDate) {
        whereClauses.push(`s.[jcr:created] >= CAST('${toJcrDateStart(filters.startDate)}' AS DATE)`);
    }

    if (filters.endDate) {
        whereClauses.push(`s.[jcr:created] < CAST('${toJcrDateEndExclusive(filters.endDate)}' AS DATE)`);
    }

    return whereClauses;
}

export function buildSubmissionsQuery(
    formResultsPath: string,
    sortBy: string,
    sortDirection: string,
    filters: SubmissionQueryFilters = {}
): string {
    const orderDirection = sortDirection === 'ascending' ? 'ASC' : 'DESC';
    const orderColumn = sortBy === 'created' ? 's.[jcr:created]' : `s.[${sortBy}]`;
    const whereClauses = buildSubmissionWhereClauses(formResultsPath, filters);

    return `SELECT * FROM [fmdb:formSubmission] AS s WHERE ${whereClauses.join(' AND ')} ORDER BY ${orderColumn} ${orderDirection}`;
}

export function buildCountQuery(formResultsPath: string, filters: SubmissionQueryFilters = {}): string {
    const whereClauses = buildSubmissionWhereClauses(formResultsPath, filters);
    return `SELECT * FROM [fmdb:formSubmission] AS s WHERE ${whereClauses.join(' AND ')}`;
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

/**
 * Reads GET_FORM_FIELD_LABELS. The descendants come back in tree order, which is
 * the order the form displays its fields in (steps, then blocks, then fields).
 */
export function parseFormFields(data: any): FormFields {
    const labels = new Map<string, string>();
    const order: string[] = [];
    const fieldListNodes = data?.jcr?.nodeById?.fields?.nodes;
    if (!Array.isArray(fieldListNodes) || fieldListNodes.length === 0) {
        return EMPTY_FORM_FIELDS;
    }

    const nodes = fieldListNodes[0]?.descendants?.nodes;
    if (!Array.isArray(nodes)) {
        return EMPTY_FORM_FIELDS;
    }

    for (const node of nodes) {
        if (typeof node.name !== 'string' || !node.name) {
            continue;
        }

        order.push(node.name);
        if (node.displayName && node.displayName !== node.name) {
            labels.set(node.name, node.displayName);
        }
    }

    return {labels, order};
}

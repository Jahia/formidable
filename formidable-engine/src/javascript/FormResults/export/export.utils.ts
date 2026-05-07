import type {FormResultsNode} from '../FormResults.utils';

export const sanitizeFilenamePart = (value: string): string => value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'form-results';

export const buildFilename = (
    formResults: FormResultsNode,
    filters: {startDate: string; endDate: string; allResults: boolean},
    extension: string
): string => {
    const baseLabel = formResults.parentForm?.refNode?.displayName ?? formResults.displayName ?? formResults.name;
    const suffix = filters.allResults
        ? 'all'
        : `${filters.startDate.replaceAll('-', '')}-${filters.endDate.replaceAll('-', '')}`;

    return `${sanitizeFilenamePart(baseLabel)}-${suffix}.${extension}`;
};

export const downloadFile = (filename: string, content: string, mimeType: string) => {
    const blob = new Blob([content], {type: mimeType});
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
};


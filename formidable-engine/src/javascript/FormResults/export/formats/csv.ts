import type {SubmissionRow} from '../../FormResults.utils';
import type {ExportFormat} from './ExportFormat';

const escapeCsvValue = (value: string): string => {
    if (value.includes('"') || value.includes(',') || value.includes('\n')) {
        return `"${value.replace(/"/g, '""')}"`;
    }

    return value;
};

const toAbsoluteUrl = (url: string): string => {
    if (url.startsWith('http://') || url.startsWith('https://')) {
        return url;
    }

    return new URL(url, window.location.origin).toString();
};

const formatFilesValue = (submission: SubmissionRow): string => {
    const grouped = new Map<string, string[]>();
    for (const file of submission.files) {
        const urls = grouped.get(file.fieldName) ?? [];
        urls.push(`  ${file.fileName} : ${toAbsoluteUrl(file.fileUrl)}`);
        grouped.set(file.fieldName, urls);
    }

    return Array.from(grouped.entries())
        .map(([fieldName, urls]) => `${fieldName}:\n${urls.join('\n')}`)
        .join('\n');
};

const buildCsvContent = (
    submissions: SubmissionRow[],
    t: (key: string) => string
): string => {
    const fieldNames = Array.from(new Set(submissions.flatMap(s => s.fieldValues.map(f => f.name))));

    const headerRow = [
        t('formResults.export.columns.id'),
        t('formResults.export.columns.name'),
        t('formResults.table.date'),
        t('formResults.table.user'),
        t('formResults.table.locale'),
        t('formResults.table.ipAddress'),
        t('formResults.detail.origin'),
        t('formResults.detail.referer'),
        t('formResults.detail.userAgent'),
        t('formResults.detail.files'),
        ...fieldNames
    ];

    const rows = submissions.map(submission => {
        const fieldValues = new Map(submission.fieldValues.map(f => [f.name, f.values.join(' | ')]));

        return [
            submission.uuid,
            submission.name,
            submission.created,
            submission.submitterUsername ?? '',
            submission.locale ?? '',
            submission.ipAddress ?? '',
            submission.origin ?? '',
            submission.referer ?? '',
            submission.userAgent ?? '',
            formatFilesValue(submission),
            ...fieldNames.map(name => fieldValues.get(name) ?? '')
        ];
    });

    return [
        headerRow.map(escapeCsvValue).join(','),
        ...rows.map(row => row.map(v => escapeCsvValue(String(v))).join(','))
    ].join('\n');
};

export const csvFormat: ExportFormat = {
    id: 'csv',
    label: 'CSV',
    extension: 'csv',
    mimeType: 'text/csv;charset=utf-8;',
    buildContent: buildCsvContent
};


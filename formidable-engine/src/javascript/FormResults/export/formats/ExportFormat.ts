import type {FormFields, SubmissionRow} from '../../FormResults.utils';

export interface ExportFormat {
    id: string;
    label: string;
    extension: string;
    mimeType: string;
    buildContent: (submissions: SubmissionRow[], t: (key: string) => string, formFields: FormFields) => string;
}


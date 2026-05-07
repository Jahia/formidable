# Export

The export feature allows users to download form submissions from the Form Results screen. It is designed to support multiple output formats through a simple plugin architecture.

## Architecture

```
FormResults/export/
├── index.ts                    Barrel re-export
├── ExportResultsDialog.tsx     UI dialog component
├── export.utils.ts             Shared utilities (filename, download)
└── formats/
    ├── index.ts                Barrel, exports available formats
    ├── ExportFormat.ts         Format interface
    └── csv.ts                  CSV format implementation
```

### ExportFormat interface

Each format implements the `ExportFormat` interface defined in `formats/ExportFormat.ts`:

```ts
interface ExportFormat {
    id: string;          // Unique identifier (e.g. "csv", "json")
    label: string;       // Display label in the UI (e.g. "CSV")
    extension: string;   // File extension without dot (e.g. "csv")
    mimeType: string;    // MIME type for the Blob (e.g. "text/csv;charset=utf-8;")
    buildContent: (
        submissions: SubmissionRow[],
        t: (key: string) => string
    ) => string;         // Builds the file content from submissions
}
```

The `buildContent` function receives:
- `submissions` — an array of parsed submission rows with metadata, field values, and file references
- `t` — the i18n translation function, scoped to the `formidable-engine` namespace

### ExportResultsDialog

The dialog component handles:
- Date range filtering (start date, end date, or all results)
- Batched GraphQL fetching of all matching submissions
- Delegating content generation to the active format
- Triggering the browser download

It does not contain any formatting logic.

### Shared utilities (`export.utils.ts`)

- `buildFilename(formResults, filters, extension)` — generates a sanitized download filename like `contact-form-20260501-20260507.csv`
- `downloadFile(filename, content, mimeType)` — creates a Blob and triggers a browser download
- `sanitizeFilenamePart(value)` — normalizes a string for safe use in filenames

## Adding a new format

1. Create a new file in `formats/`, e.g. `formats/json.ts`:

```ts
import type {SubmissionRow} from '../../FormResults.utils';
import type {ExportFormat} from './ExportFormat';

const buildJsonContent = (
    submissions: SubmissionRow[],
    _t: (key: string) => string
): string => {
    return JSON.stringify(submissions, null, 2);
};

export const jsonFormat: ExportFormat = {
    id: 'json',
    label: 'JSON',
    extension: 'json',
    mimeType: 'application/json;charset=utf-8;',
    buildContent: buildJsonContent
};
```

2. Register it in `formats/index.ts`:

```ts
export type {ExportFormat} from './ExportFormat';
export {csvFormat} from './csv';
export {jsonFormat} from './json';

import {csvFormat} from './csv';
import {jsonFormat} from './json';
import type {ExportFormat} from './ExportFormat';

export const exportFormats: ExportFormat[] = [csvFormat, jsonFormat];
```

3. That's it. When a format selector is added to the dialog, it can import `exportFormats` and let the user choose.

## Data model

Each `SubmissionRow` contains:

| Field | Type | Description |
|---|---|---|
| `uuid` | `string` | JCR node identifier |
| `name` | `string` | JCR node name |
| `created` | `string` | ISO date of submission |
| `origin` | `string \| null` | Submission origin |
| `ipAddress` | `string \| null` | Client IP address |
| `locale` | `string \| null` | Submission locale |
| `submitterUsername` | `string \| null` | Jahia username |
| `userAgent` | `string \| null` | Browser user agent |
| `referer` | `string \| null` | HTTP referer |
| `fieldValues` | `SubmissionFieldValue[]` | Submitted form field values |
| `files` | `SubmissionFile[]` | Uploaded file references with URLs |

Each `SubmissionFile` contains `fieldName`, `fileName`, `fileUrl`, `mimeType`, and `thumbnailUrl`.

## CSV specifics

The CSV format (`formats/csv.ts`) produces:
- A header row with fixed metadata columns followed by dynamic field name columns
- One data row per submission
- Multi-value fields joined with ` | `
- Files grouped by field name with absolute download URLs
- Proper CSV escaping (quotes, commas, newlines)


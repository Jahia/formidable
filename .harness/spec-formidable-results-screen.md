# Technical Specification - Formidable Results Screen Backed by JCR

This document describes the current Formidable results screen shipped by `formidable-engine`.
It is intentionally aligned with the existing implementation rather than an earlier target architecture.

---

## 1. Goal

Provide a Jahia admin screen that allows authorized users to inspect Formidable submissions persisted in JCR by the `fmdb:save2jcrAction` action.

The screen is exposed from jContent in the **Additional apps** area through a UI Extender `adminRoute`.

Main capabilities:

- list forms that have saved results;
- browse submissions for one form;
- inspect submission metadata;
- inspect submitted field values;
- inspect uploaded files;
- sort, paginate, and select submissions;
- open a submission detail panel;
- support export and deletion operations.

---

## 2. jContent Integration Point

### 2.1 UI Extender registration

File:

```text
src/javascript/init.tsx
```

Current implementation:

```tsx
import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {Form} from '@jahia/moonstone';
import {FormResultsApp} from './FormResults';

export default function () {
    registry.add('callback', 'FormidableEngineEditor', {
        targets: ['jahiaApp-init:20'],
        callback: () => {
            i18next.loadNamespaces('formidable-engine');

            registry.add('adminRoute', 'formidableResults', {
                targets: ['jcontent:50'],
                icon: <Form/>,
                label: 'formidable-engine:formResults.nav.title',
                isSelectable: true,
                requireModuleInstalledOnSite: 'formidable-engine',
                render: () => <FormResultsApp/>
            });
        }
    });
}
```

The results screen is therefore shipped by `formidable-engine`, not by a separate `formidable-results` module.

### 2.2 Permissions

There is no dedicated Jahia permission such as `formidableResultsAccess` in the current implementation.

Read access relies on standard `LIVE`-workspace JCR ACLs, typically through the `fmdb-results-reader` role propagated to `fmdb:formResults` nodes.

Deletion relies on standard JCR permissions:

- `jcr:removeNode`
- `jcr:removeChildNodes`

The frontend detects deletion capability by querying `hasPermission(...)` on the `submissions` container.

---

## 3. Expected JCR Model

Submissions are stored under:

```text
/sites/<site>/formidable-results/<form-results>/submissions/YYYY/MM/DD/<submission>
```

Example:

```text
/sites/industrial/formidable-results/form-actions/submissions/2026/05/05/submission-20260505-150707-a9e
```

Each submission contains:

```text
<submission>
  data/
    fieldA = ...
    fieldB = ...
  files/
    <fieldName>/
      <fileName>/
        jcr:content
```

`fmdb:formResults` is functionally identified by its `parentForm` weakreference, not by its JCR node name. This keeps the logical identity stable even if the form is renamed.

---

## 4. Architecture

```text
React app in jContent
  |
  | built-in JCR GraphQL API
  v
LIVE JCR session
  |
  v
/sites/<site>/formidable-results
  |
  +-- <form-results>
        |
        +-- submissions/YYYY/MM/DD/<submission>
```

Important decision:

The frontend does not walk the auto-split folder tree manually. Instead, it queries JCR using GraphQL and SQL2 so the split structure remains an implementation detail.

Logical model exposed to the UI:

```text
FormResults -> Submission rows -> Submission detail
```

Hidden storage detail:

```text
submissions/YYYY/MM/DD
```

---

## 5. GraphQL API Used by the Screen

The current screen uses Jahia's built-in JCR GraphQL API directly. It does not rely on a custom GraphQL extension with dedicated DTO types.

### 5.1 Main frontend types

```ts
interface FormResultsNode {
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
  submissionsContainer?: {
    nodes?: Array<{
      canRemoveNode?: boolean;
      canRemoveChildNodes?: boolean;
    }>;
  };
}

interface SubmissionRow {
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
```

### 5.2 GraphQL operations

Current queries and mutation:

- `GET_FORM_RESULTS_LIST`
- `GET_SUBMISSIONS`
- `GET_SUBMISSION_COUNT`
- `GET_FORM_FIELD_LABELS`
- `DELETE_SUBMISSIONS`

Representative examples:

```graphql
query GetFormResultsList($resultsPath: String!, $workspace: Workspace = LIVE, $language: String!) {
  jcr(workspace: $workspace) {
    nodeByPath(path: $resultsPath) {
      children(typesFilter: {types: ["fmdb:formResults"]}) {
        nodes {
          uuid
          path
          name
          displayName(language: $language)
          submissionsContainer: children(names: ["submissions"]) {
            nodes {
              canRemoveNode: hasPermission(permissionName: "jcr:removeNode")
              canRemoveChildNodes: hasPermission(permissionName: "jcr:removeChildNodes")
            }
          }
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
```

```graphql
query GetSubmissions(
  $submissionsQuery: String!
  $limit: Int!
  $offset: Int!
  $workspace: Workspace = LIVE
) {
  jcr(workspace: $workspace) {
    nodesByQuery(query: $submissionsQuery, queryLanguage: SQL2, limit: $limit, offset: $offset) {
      pageInfo {
        totalCount
        hasNextPage
      }
      nodes {
        uuid
        path
        name
        created: property(name: "jcr:created") { value }
        origin: property(name: "origin") { value }
        ipAddress: property(name: "ipAddress") { value }
        locale: property(name: "locale") { value }
        submitterUsername: property(name: "submitterUsername") { value }
        userAgent: property(name: "userAgent") { value }
        referer: property(name: "referer") { value }
      }
    }
  }
}
```

```graphql
mutation DeleteSubmissions($submissionsQuery: String!, $workspace: Workspace = LIVE) {
  jcr(workspace: $workspace) {
    mutateNodesByQuery(query: $submissionsQuery, queryLanguage: SQL2) {
      delete
    }
  }
}
```

### 5.3 Future GraphQL evolution

A dedicated GraphQL schema could still be introduced later if stronger backend abstraction becomes necessary.

Possible reasons:

- stricter DTO contracts;
- server-side field-label resolution;
- backend-level filtering rules;
- backend-managed file-download endpoints.

That is not how the current screen works.

---

## 6. Backend Responsibilities

### 6.1 List form-results nodes

Current approach:

- open `/sites/<siteKey>/formidable-results` in `LIVE`;
- read child nodes of type `fmdb:formResults`;
- rely on GraphQL and JCR ACLs for visibility filtering;
- resolve the parent form through the `parentForm` weakreference.

Pseudo-code:

```java
List<FormResultsDTO> getFormidableResults(String siteKey) {
    JCRSessionWrapper session = getCurrentUserSession("LIVE");
    String rootPath = "/sites/" + siteKey + "/formidable-results";

    if (!session.nodeExists(rootPath)) {
        return emptyList();
    }

    JCRNodeWrapper root = session.getNode(rootPath);
    List<FormResultsDTO> results = new ArrayList<>();

    for (JCRNodeWrapper child : getChildren(root)) {
        if (!child.isNodeType("fmdb:formResults")) {
            continue;
        }

        FormResultsDTO dto = new FormResultsDTO();
        dto.id = child.getIdentifier();
        dto.path = child.getPath();
        dto.name = child.getName();
        dto.displayName = resolveDisplayName(child);
        dto.parentFormId = readWeakReferenceIdentifier(child, "parentForm");
        dto.parentFormPath = resolveWeakReferencePath(child, "parentForm");
        results.add(dto);
    }

    return results;
}
```

### 6.2 List one form's submissions

Current approach:

- resolve one `fmdb:formResults` node in `LIVE`;
- query descendant `fmdb:formSubmission` nodes through SQL2;
- sort by `jcr:created`;
- apply limit and offset at the query level.

Pseudo-code:

```java
SubmissionPageDTO getSubmissions(
    String formResultsId,
    int page,
    int pageSize,
    String sortBy,
    SortDirection direction,
    Filters filters
) {
    JCRSessionWrapper session = getCurrentUserSession("LIVE");
    JCRNodeWrapper formResults = session.getNodeByIdentifier(formResultsId);

    assertNodeType(formResults, "fmdb:formResults");

    if (!formResults.hasNode("submissions")) {
        return emptyPage(page, pageSize);
    }

    String sql2 = buildSql2(formResults.getPath(), page, pageSize, sortBy, direction, filters);
    return execute(sql2);
}
```

### 6.3 Map one submission to a table row

The UI needs a flattened row object built from:

- metadata on the `fmdb:formSubmission` node itself;
- residual properties under the `data` child node;
- files under `files/<fieldName>/<fileName>`.

The current frontend performs this transformation client-side after GraphQL retrieval.

Pseudo-code:

```ts
function parseSubmissionNode(node: any): SubmissionRow {
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
    fieldValues: extractUserProperties(node.data),
    files: extractFiles(node.files)
  };
}
```

---

## 7. Sorting and Pagination

### 7.1 Current simple version

The current screen paginates with:

- SQL2 query + `limit` + `offset`;
- sort column fixed to `jcr:created`;
- sort direction toggled between ascending and descending.

### 7.2 Current scalable characteristics

The implementation is already scalable enough for a first production version because:

- filtering is delegated to SQL2;
- pagination is delegated to GraphQL/JCR;
- the UI does not load the full submissions tree at once.

Representative SQL2 shape:

```sql
SELECT *
FROM [fmdb:formSubmission] AS s
WHERE ISDESCENDANTNODE(s, '/sites/industrial/formidable-results/contact/submissions')
ORDER BY s.[jcr:created] DESC
```

---

## 8. React Frontend

### 8.1 File structure

```text
src/javascript/
  init.tsx
  FormResults/
    FormResultsApp.tsx
    FormResults.utils.ts
    graphql/queries.ts
    components/
      FormResultsList.tsx
      SubmissionsTable.tsx
      SubmissionDetailPanel.tsx
      FilePreviewDialog.tsx
    export/
      ExportResultsDialog.tsx
    delete/
      DeleteResultsDialog.tsx
```

### 8.2 Root component

`FormResultsApp` is responsible for:

- reading `siteKey` and `uilang` from `window.contextJsParameters`;
- loading the list of `fmdb:formResults` nodes;
- tracking selected form, selected submission, refresh state, export dialog state, and delete dialog state;
- loading form field labels from the referenced parent form;
- rendering the main two-panel layout.

Pseudo-code:

```tsx
const FormResultsApp = () => {
  const siteKey = window.contextJsParameters?.siteKey;
  const language = window.contextJsParameters?.uilang || 'en';
  const resultsPath = `/sites/${siteKey}/formidable-results`;

  const {data, loading, error, refetch} = useQuery(GET_FORM_RESULTS_LIST, {
    variables: {resultsPath, workspace: 'LIVE', language}
  });

  const forms = data?.jcr?.nodeByPath?.children?.nodes ?? [];
  const selectedForm = ...;

  return (
    <Layout>
      <FormResultsList/>
      <SubmissionsTable/>
      <SubmissionDetailPanel/>
    </Layout>
  );
};
```

---

## 9. Form List

The left panel lists available `fmdb:formResults` nodes.

Displayed label priority:

1. `parentForm.refNode.displayName`
2. `formResults.displayName`
3. `formResults.name`

This keeps the screen aligned with the current form title whenever the parent form still exists.

---

## 10. Submissions Table

### 10.1 Recommended frontend DTO

```ts
interface SubmissionRow {
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
  fieldValues: Array<{name: string; values: string[]}>;
  files: SubmissionFile[];
}
```

### 10.2 GraphQL query behavior

The table queries JCR directly with:

- `workspace: 'LIVE'`
- `queryLanguage: SQL2`
- `limit`
- `offset`

### 10.3 UI behavior

The current table supports:

- pagination (`10`, `25`, `50` rows per page);
- sort direction toggle on date;
- row selection;
- keyboard navigation with arrow up/down;
- opening the side detail panel.

Current visible columns:

- Date
- User
- Locale
- IP address
- Files count
- Filled fields count
- Action button / row selection affordance

---

## 11. Flattened Frontend Model

The current implementation deliberately flattens nested GraphQL data into a compact UI model.

Benefits:

- components render simpler objects;
- table rendering is decoupled from raw GraphQL node shape;
- detail panel can reuse the same row object.

Residual JCR properties are filtered so only user data remains. Properties beginning with:

- `jcr:`
- `j:`
- `mix:`

are excluded from submitted field values.

---

## 12. Submission Detail

### 12.1 Data source

The detail panel currently reuses the selected `SubmissionRow` already loaded by the submissions query.

No separate detail query is required in the current implementation.

### 12.2 Drawer / side panel

`SubmissionDetailPanel` shows:

- metadata (`created`, `origin`, `ipAddress`, `locale`, `submitterUsername`, `userAgent`, `referer`);
- submitted values, with labels resolved from the parent form when available;
- uploaded files, with preview and download actions.

Field-label resolution comes from `GET_FORM_FIELD_LABELS` against the parent form in `LIVE`.

If the parent form no longer exists, the panel falls back to raw field names.

---

## 13. File Handling

Files are stored under:

```text
<submission>/files/<fieldName>/<fileName>/jcr:content
```

Current implementation:

- GraphQL returns `file.url`, `thumbnailUrl`, and MIME type;
- the frontend reuses `file.url` for both preview and download;
- image files open in an `<img>` preview;
- video files open in a `<video>` preview;
- other previewable formats fall back to an `<iframe>`.

Download pattern:

```tsx
<a href={file.fileUrl} download={file.fileName}>
  <Button label="Download"/>
</a>
```

### Option V2: dedicated endpoint

No `/modules/formidable-results/download` endpoint exists today.

If a harder security boundary becomes necessary later, a dedicated endpoint could be added to centralize:

- access checks;
- `Content-Disposition` normalization;
- abstraction over internal file URLs.

Target pseudo-code:

```java
void downloadFile(String fileNodeIdentifier, HttpServletResponse response) {
    JCRNodeWrapper fileNode = session.getNodeByIdentifier(fileNodeIdentifier);
    assertFileBelongsToAccessibleSubmission(fileNode);
    assertPermission(fileNode, "jcr:read");
    // stream jcr:content/jcr:data
}
```

---

## 14. i18n

Current namespace:

```text
formidable-engine
```

Results-screen keys live under `formResults.*`.

Example:

```json
{
  "formResults": {
    "nav": {
      "title": "Form Results"
    },
    "table": {
      "date": "Date",
      "user": "User",
      "locale": "Language",
      "files": "Files"
    },
    "empty": {
      "noForms": "No results available",
      "noSubmissions": "No submissions"
    }
  }
}
```

---

## 15. UI States to Handle

### Loading

- loading the form-results list;
- loading submissions;
- loading export and delete counts.

### Empty states

- no `/formidable-results` folder yet;
- no visible `fmdb:formResults` nodes;
- no submissions for the selected form;
- submission without `data`;
- submission without `files`.

### Errors

- insufficient permissions;
- parent form deleted while results still exist;
- submission removed between list and detail view;
- missing file node;
- malformed or unexpected JCR property values.

---

## 16. Security

Current read model:

```text
standard LIVE JCR ACLs
on `fmdb:formResults` and descendants
```

Current delete model:

```text
jcr:removeNode
jcr:removeChildNodes
```

Current file-download model:

```text
no dedicated permission;
frontend reuses GraphQL file URL
```

The application must not rely only on route visibility in the UI. Backend enforcement still comes from GraphQL + JCR ACLs.

---

## 17. Performance

### V1 acceptable

The current design is acceptable for V1 because:

- results are queried page by page;
- submissions are sorted and paginated server-side;
- field labels are loaded once per selected form;
- detail view reuses already loaded row data.

### V2 possible improvements

Potential future optimizations:

- dedicated backend DTOs instead of raw JCR GraphQL traversal;
- server-side aggregation for counts and summaries;
- explicit backend API for file access;
- precomputed form labels or cached label lookups.

---

## 18. Future Export

The current module already ships an export dialog.

Current scope:

- date-range filtering;
- export-all mode;
- multiple formats (`csv`, `json`);
- filename generation client-side;
- GraphQL-based data retrieval in `LIVE`.

The exported payload is built from the same submission query model used by the screen.

---

## 19. Deletion

Current implementation:

```graphql
mutation DeleteSubmissions($submissionsQuery: String!, $workspace: Workspace = LIVE) {
  jcr(workspace: $workspace) {
    mutateNodesByQuery(query: $submissionsQuery, queryLanguage: SQL2) {
      delete
    }
  }
}
```

Pseudo-code:

```java
boolean deleteSubmissions(String sql2Query) {
    // GraphQL runs in LIVE and remains subject to standard JCR delete permissions.
    mutateNodesByQuery(sql2Query);
    return true;
}
```

The delete dialog additionally requires a confirmation string when deleting all results for a form.

---

## 20. V1 Delivery Scope

### Backend V1

- rely on built-in JCR GraphQL;
- rely on SQL2 queries for submission listing;
- rely on standard JCR ACLs;
- no dedicated results-download servlet.

### Frontend V1

- forms list;
- submissions table;
- detail panel;
- file preview;
- file download;
- export dialog;
- delete dialog;
- FR/EN i18n;
- loading, empty, and error states.

---

## 21. Global Pseudo-code

```text
on module initialization:
  load i18n namespace "formidable-engine"
  register adminRoute "formidableResults" into jcontent additional apps
  require module installed on site: "formidable-engine"
  render FormResultsApp

FormResultsApp:
  read siteKey from contextJsParameters
  query /sites/<site>/formidable-results in LIVE
  if loading -> loader
  if error -> error state
  if no forms -> empty state
  render FormResultsList
  render SubmissionsTable(selectedForm)
  render SubmissionDetailPanel(selectedSubmission)
  optionally render ExportResultsDialog
  optionally render DeleteResultsDialog

SubmissionsTable:
  build SQL2 query on selected formResults path
  query fmdb:formSubmission descendants in LIVE
  map raw GraphQL nodes to SubmissionRow
  support sort direction toggle
  support pagination
  support keyboard navigation

SubmissionDetailPanel:
  render submission metadata
  render submitted values
  render files
  open preview dialog when possible
```

---

## 22. Save-to-JCR Specific Attention Points

The screen is tightly coupled to the storage contract of `fmdb:save2jcrAction`.

Key assumptions:

- results live under `/sites/<site>/formidable-results`;
- each form has one logical `fmdb:formResults` identified by `parentForm`;
- submissions are descendants of `<form-results>/submissions`;
- user field values are stored as properties under `data`;
- uploaded files are stored under `files/<fieldName>/<fileName>`.

If the storage contract changes, the screen queries and mapping utilities must be updated accordingly.

---

## 23. Implementation Notes

### Workspace

Current implementation uses `LIVE` consistently.

That is coherent because `SaveToJcrFormAction` persists results directly in `LIVE`, and the admin screen explicitly queries `LIVE`.

`EDIT` is not used by the shipped results screen.

### Dynamic columns

The current table focuses on technical metadata and summary counts rather than rendering one column per submitted field.

If dynamic business columns are needed later, the preferred source of truth is the parent form definition rather than only the fields present on the current page of submissions.

### Renamed forms

The `fmdb:formResults` node name is not contractual.

The screen should always treat `parentForm` as the functional identity and use JCR identifiers internally where possible.

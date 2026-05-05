# Spécification technique — Écran de consultation des résultats Formidable sauvegardés en JCR

## 1. Objectif

Créer un écran Jahia permettant aux contributeurs autorisés de consulter les soumissions d’un formulaire Formidable sauvegardées en JCR via l’action `fmdb:save2jcrAction`.

L’écran doit être accessible depuis jContent, dans la zone **Additional apps**, sur le même principe que le module `content-releases`, qui enregistre une route d’administration via `registry.add('adminRoute', ...)`.

Fonctionnalités principales :

- lister les formulaires ayant des résultats sauvegardés ;
- afficher les soumissions d’un formulaire ;
- afficher les métadonnées de soumission ;
- afficher les valeurs saisies dans le formulaire ;
- afficher les fichiers uploadés ;
- permettre tri, pagination et sélection ;
- ouvrir le détail d’une soumission ;
- préparer des actions futures : export CSV, suppression, téléchargement des fichiers.

---

## 2. Point d’intégration jContent

### 2.1 Enregistrement UI Extender

Créer un fichier :

```text
src/javascript/init.js
```

Pseudo-code :

```js
import React from 'react';
import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {TableChart} from '@jahia/moonstone';
import FormResultsApp from './FormResultsApp';

export default function () {
    i18next.loadNamespaces('formidable-results');

    registry.add('adminRoute', 'formidableResults', {
        targets: ['jcontent:50'],
        icon: <TableChart/>,
        label: 'formidable-results:label.appsAccordion.title',
        isSelectable: true,
        requireModuleInstalledOnSite: 'formidable',
        requiredPermission: 'formidableResultsAccess',
        render: () => <FormResultsApp/>
    });

    console.debug('%c Formidable Results extension is activated', 'color: #3c8cba');
}
```

À adapter selon le nom exact du module :

```js
requireModuleInstalledOnSite: 'formidable'
```

ou :

```js
requireModuleInstalledOnSite: 'formidable-results'
```

si l’écran est livré dans un module séparé.

### 2.2 Permission

Créer une permission Jahia dédiée :

```text
formidableResultsAccess
```

Elle sert à contrôler l’accès à l’application.

Permissions optionnelles pour les évolutions :

```text
formidableResultsAccess
formidableResultsExport
formidableResultsDelete
formidableResultsDownloadFiles
```

---

## 3. Modèle JCR attendu

Les soumissions sont stockées sous :

```text
/sites/<site>/formidable-results/<form-results>/submissions/YYYY/MM/DD/<submission>
```

Exemple :

```text
/sites/industrial/formidable-results/form-actions/submissions/2026/05/05/submission-20260505-150707-a9e
```

Chaque soumission contient :

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

Le noeud `fmdb:formResults` est identifié fonctionnellement par sa propriété `parentForm`, et non uniquement par son nom JCR. Cela permet de conserver une identité stable si le formulaire est renommé.

---

## 4. Architecture cible

```text
React app jContent
  |
  | GraphQL
  v
Jahia GraphQL extension / DataFetcher
  |
  | JCR session EDIT ou LIVE selon besoin
  v
/sites/<site>/formidable-results
  |
  +-- <form-results>
        |
        +-- submissions/YYYY/MM/DD/<submission>
```

### Décision importante

Le front ne doit pas parcourir directement l’arborescence JCR auto-splittée.

Le backend doit exposer une liste logique :

```text
FormResults -> Submissions[]
```

et masquer le détail :

```text
submissions/YYYY/MM/DD
```

---

## 5. API GraphQL proposée

### 5.1 Types GraphQL

```graphql
type FormidableFormResults {
  id: String!
  path: String!
  name: String!
  displayName: String
  parentFormId: String
  parentFormPath: String
  submissionCount: Int!
  lastSubmissionDate: Date
}

type FormidableSubmissionPage {
  nodes: [FormidableSubmissionRow!]!
  pageInfo: FormidablePageInfo!
}

type FormidablePageInfo {
  currentPage: Int!
  pageSize: Int!
  totalCount: Int!
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
}

type FormidableSubmissionRow {
  id: String!
  path: String!
  name: String!
  created: Date!

  origin: String
  status: String
  ipAddress: String
  locale: String
  submitterUsername: String
  userAgent: String
  referer: String

  values: [FormidableFieldValue!]!
  files: [FormidableFileSummary!]!
}

type FormidableFieldValue {
  name: String!
  value: [String!]!
}

type FormidableFileSummary {
  fieldName: String!
  fileName: String!
  path: String!
  mimeType: String
  size: Long
}

type FormidableSubmissionDetail {
  id: String!
  path: String!
  name: String!
  created: Date!
  metadata: [FormidableMetadata!]!
  values: [FormidableFieldValue!]!
  files: [FormidableFileSummary!]!
}

type FormidableMetadata {
  name: String!
  value: String
}
```

### 5.2 Queries

```graphql
extend type Query {
  formidableResults(siteKey: String!): [FormidableFormResults!]!

  formidableSubmissions(
    siteKey: String!
    formResultsId: String!
    page: Int = 1
    pageSize: Int = 25
    sortBy: String = "created"
    sortDirection: String = "descending"
    filters: FormidableSubmissionFilters
  ): FormidableSubmissionPage!

  formidableSubmission(
    id: String!
  ): FormidableSubmissionDetail
}

input FormidableSubmissionFilters {
  fromDate: Date
  toDate: Date
  status: String
  submitterUsername: String
  text: String
}
```

### 5.3 Mutations futures

```graphql
extend type Mutation {
  deleteFormidableSubmission(id: String!): Boolean!
  exportFormidableSubmissionsCsv(formResultsId: String!): String!
}
```

Pour une première version, limiter le périmètre à la lecture.

---

## 6. Backend : responsabilités

### 6.1 Lister les formulaires ayant des résultats

Entrée :

```text
siteKey
```

Chemin à lire :

```text
/sites/<siteKey>/formidable-results
```

Pseudo-code Java :

```java
List<FormResultsDTO> getFormidableResults(String siteKey) {
    JCRSessionWrapper session = getCurrentUserSession("EDIT");

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

        if (!hasPermission(child, "formidableResultsAccess")) {
            continue;
        }

        FormResultsDTO dto = new FormResultsDTO();
        dto.id = child.getIdentifier();
        dto.path = child.getPath();
        dto.name = child.getName();
        dto.displayName = resolveDisplayName(child);
        dto.parentFormId = readWeakReferenceIdentifier(child, "parentForm");
        dto.parentFormPath = resolveWeakReferencePath(child, "parentForm");
        dto.submissionCount = countSubmissions(child);
        dto.lastSubmissionDate = findLastSubmissionDate(child);

        results.add(dto);
    }

    return results;
}
```

### 6.2 Lister les soumissions d’un formulaire

Entrée :

```text
formResultsId
page
pageSize
sortBy
sortDirection
filters
```

Étapes :

1. charger le noeud `fmdb:formResults` par identifier ;
2. vérifier la permission ;
3. récupérer son enfant `submissions` ;
4. parcourir récursivement les enfants jusqu’aux noeuds `fmdb:formSubmission` ;
5. appliquer les filtres ;
6. trier ;
7. paginer ;
8. transformer en DTO table.

Pseudo-code :

```java
SubmissionPageDTO getSubmissions(
    String formResultsId,
    int page,
    int pageSize,
    String sortBy,
    SortDirection direction,
    Filters filters
) {
    JCRSessionWrapper session = getCurrentUserSession("EDIT");

    JCRNodeWrapper formResults = session.getNodeByIdentifier(formResultsId);

    assertNodeType(formResults, "fmdb:formResults");
    assertPermission(formResults, "formidableResultsAccess");

    if (!formResults.hasNode("submissions")) {
        return emptyPage(page, pageSize);
    }

    JCRNodeWrapper submissionsRoot = formResults.getNode("submissions");

    List<JCRNodeWrapper> submissions = new ArrayList<>();

    collectSubmissionNodes(submissionsRoot, submissions);

    submissions = submissions.stream()
        .filter(node -> matchesFilters(node, filters))
        .sorted(comparator(sortBy, direction))
        .collect(toList());

    int total = submissions.size();

    List<JCRNodeWrapper> pageNodes = paginate(submissions, page, pageSize);

    List<SubmissionRowDTO> rows = pageNodes.stream()
        .map(this::toSubmissionRowDTO)
        .collect(toList());

    return new SubmissionPageDTO(rows, page, pageSize, total);
}
```

Parcours récursif :

```java
void collectSubmissionNodes(JCRNodeWrapper node, List<JCRNodeWrapper> result) {
    for (JCRNodeWrapper child : getChildren(node)) {
        if (child.isNodeType("fmdb:formSubmission")) {
            result.add(child);
            continue;
        }

        if (child.isNodeType("jnt:contentFolder")
            || child.isNodeType("fmdb:splittedSubmission")
            || child.isNodeType("fmdb:submissions")) {
            collectSubmissionNodes(child, result);
        }
    }
}
```

Selon les types réellement déclarés pour les dossiers `YYYY/MM/DD`, le test peut être simplifié en parcourant tous les enfants tant que ce ne sont pas des fichiers.

### 6.3 Transformer une soumission en ligne de table

```java
SubmissionRowDTO toSubmissionRowDTO(JCRNodeWrapper submission) {
    SubmissionRowDTO dto = new SubmissionRowDTO();

    dto.id = submission.getIdentifier();
    dto.path = submission.getPath();
    dto.name = submission.getName();
    dto.created = getDateProperty(submission, "jcr:created");

    dto.origin = getStringProperty(submission, "origin");
    dto.status = getStringProperty(submission, "status");
    dto.ipAddress = getStringProperty(submission, "ipAddress");
    dto.locale = getStringProperty(submission, "locale");
    dto.submitterUsername = getStringProperty(submission, "submitterUsername");
    dto.userAgent = getStringProperty(submission, "userAgent");
    dto.referer = getStringProperty(submission, "referer");

    dto.values = readDataValues(submission);
    dto.files = readFileSummaries(submission);

    return dto;
}
```

Lecture de `data` :

```java
List<FieldValueDTO> readDataValues(JCRNodeWrapper submission) {
    if (!submission.hasNode("data")) {
        return emptyList();
    }

    JCRNodeWrapper dataNode = submission.getNode("data");

    List<FieldValueDTO> values = new ArrayList<>();

    PropertyIterator properties = dataNode.getProperties();

    while (properties.hasNext()) {
        Property property = properties.nextProperty();

        if (property.getName().startsWith("jcr:")) {
            continue;
        }

        FieldValueDTO dto = new FieldValueDTO();
        dto.name = property.getName();

        if (property.isMultiple()) {
            dto.value = Arrays.stream(property.getValues())
                .map(this::valueToString)
                .collect(toList());
        } else {
            dto.value = List.of(valueToString(property.getValue()));
        }

        values.add(dto);
    }

    return values;
}
```

Lecture de `files` :

```java
List<FileSummaryDTO> readFileSummaries(JCRNodeWrapper submission) {
    if (!submission.hasNode("files")) {
        return emptyList();
    }

    JCRNodeWrapper filesRoot = submission.getNode("files");

    List<FileSummaryDTO> files = new ArrayList<>();

    for (JCRNodeWrapper fieldFolder : getChildren(filesRoot)) {
        String fieldName = fieldFolder.getName();

        for (JCRNodeWrapper fileNode : getChildren(fieldFolder)) {
            FileSummaryDTO dto = new FileSummaryDTO();

            dto.fieldName = fieldName;
            dto.fileName = fileNode.getName();
            dto.path = fileNode.getPath();

            if (fileNode.hasNode("jcr:content")) {
                JCRNodeWrapper content = fileNode.getNode("jcr:content");
                dto.mimeType = getStringProperty(content, "jcr:mimeType");
                dto.size = getBinarySize(content, "jcr:data");
            }

            files.add(dto);
        }
    }

    return files;
}
```

---

## 7. Tri et pagination

### 7.1 Première version simple

Pour une V1, tri et pagination peuvent être faits en mémoire côté backend après collecte récursive.

Avantages :

- simple ;
- robuste avec l’arborescence auto-splittée ;
- facile à implémenter.

Limite :

- peut devenir coûteux si un formulaire a beaucoup de soumissions.

### 7.2 Version scalable

Pour une V2, utiliser une requête JCR-SQL2 ou QueryObjectModel sur les descendants de :

```text
/sites/<site>/formidable-results/<form-results>/submissions
```

Exemple conceptuel :

```sql
SELECT * FROM [fmdb:formSubmission] AS s
WHERE ISDESCENDANTNODE(s, '/sites/industrial/formidable-results/contact/submissions')
ORDER BY s.[jcr:created] DESC
```

Le backend garderait :

```text
limit
offset
sort
filters
```

mais il faut vérifier la faisabilité des filtres dynamiques sur les propriétés de `<submission>/data`, car elles sont stockées sur un noeud enfant, pas directement sur la soumission.

---

## 8. Frontend React

### 8.1 Structure des fichiers

```text
src/javascript/
  init.js
  FormResultsApp/
    FormResultsApp.jsx
    FormResultsApp.gql-queries.js
    FormResultsList.jsx
    SubmissionsTable.jsx
    SubmissionDetailDrawer.jsx
    submissionTable.utils.js
    index.js
  i18n/
    en.json
    fr.json
```

### 8.2 Composant racine

Responsabilités :

- récupérer `siteKey` / `siteUuid` depuis `window.contextJsParameters` ;
- charger la liste des formulaires avec résultats ;
- gérer le formulaire sélectionné ;
- afficher soit un empty state, soit la table.

Pseudo-code :

```jsx
import React, {useState} from 'react';
import {useQuery} from '@apollo/client';
import {Loader, Typography} from '@jahia/moonstone';
import {GET_FORM_RESULTS} from './FormResultsApp.gql-queries';
import FormResultsList from './FormResultsList';
import SubmissionsTable from './SubmissionsTable';

const FormResultsApp = () => {
    const {siteKey} = window.contextJsParameters;
    const [selectedFormResultsId, setSelectedFormResultsId] = useState(null);

    const {loading, error, data} = useQuery(GET_FORM_RESULTS, {
        variables: {siteKey},
        fetchPolicy: 'network-only'
    });

    if (loading) {
        return <Loader/>;
    }

    if (error) {
        return <Typography color="danger">{error.message}</Typography>;
    }

    const forms = data?.formidableResults || [];

    if (forms.length === 0) {
        return (
            <EmptyState
                title="No form results"
                description="No saved Formidable submissions were found for this site."
            />
        );
    }

    const selectedForm = forms.find(form => form.id === selectedFormResultsId) || forms[0];

    return (
        <div className="formidableResultsApp">
            <FormResultsList
                forms={forms}
                selectedId={selectedForm.id}
                onSelect={setSelectedFormResultsId}
            />

            <SubmissionsTable formResults={selectedForm}/>
        </div>
    );
};

export default FormResultsApp;
```

---

## 9. Liste des formulaires

```jsx
const FormResultsList = ({forms, selectedId, onSelect}) => {
    return (
        <aside className="formidableResultsApp_sidebar">
            {forms.map(form => (
                <button
                    key={form.id}
                    type="button"
                    className={form.id === selectedId ? 'selected' : ''}
                    onClick={() => onSelect(form.id)}
                >
                    <span>{form.displayName || form.name}</span>
                    <span>{form.submissionCount}</span>
                </button>
            ))}
        </aside>
    );
};
```

Variante Moonstone :

- `Menu` ;
- `MenuItem` ;
- `Typography` ;
- `Chip` ;
- ou une `List` si disponible dans la version Moonstone utilisée.

---

## 10. Table des soumissions avec Moonstone DataTable

Le composant Moonstone `DataTable` supporte :

- `data` ;
- `columns` ;
- `primaryKey` ;
- sorting ;
- sélection ;
- pagination ;
- custom rows via `renderRow`.

Il est basé sur TanStack Table côté interne.

### 10.1 DTO front recommandé

Aplatir les valeurs dynamiques pour simplifier la table :

```ts
type SubmissionTableRow = {
    id: string;
    path: string;
    name: string;
    created: string;
    status?: string;
    locale?: string;
    submitterUsername?: string;
    ipAddress?: string;
    fileCount: number;

    values: Record<string, string | string[]>;
};
```

Puis créer des colonnes fixes et des colonnes dynamiques.

### 10.2 Query GraphQL

```js
import {gql} from '@apollo/client';

export const GET_SUBMISSIONS = gql`
    query GetFormidableSubmissions(
        $siteKey: String!
        $formResultsId: String!
        $page: Int!
        $pageSize: Int!
        $sortBy: String!
        $sortDirection: String!
    ) {
        formidableSubmissions(
            siteKey: $siteKey
            formResultsId: $formResultsId
            page: $page
            pageSize: $pageSize
            sortBy: $sortBy
            sortDirection: $sortDirection
        ) {
            nodes {
                id
                path
                name
                created
                status
                locale
                submitterUsername
                ipAddress
                values {
                    name
                    value
                }
                files {
                    fieldName
                    fileName
                    path
                    mimeType
                    size
                }
            }
            pageInfo {
                currentPage
                pageSize
                totalCount
                hasNextPage
                hasPreviousPage
            }
        }
    }
`;
```

### 10.3 Table

```jsx
import React, {useMemo, useState} from 'react';
import {useQuery} from '@apollo/client';
import {
    DataTable,
    TableRow,
    TableCellActions
} from '@jahia/moonstone';
import {Button} from '@jahia/moonstone';
import {Visibility} from '@jahia/moonstone';
import {GET_SUBMISSIONS} from './FormResultsApp.gql-queries';

const toRow = submission => {
    const values = {};

    submission.values.forEach(field => {
        values[field.name] = field.value.length > 1 ? field.value : field.value[0];
    });

    return {
        ...submission,
        values,
        fileCount: submission.files.length
    };
};

const valueToString = value => {
    if (Array.isArray(value)) {
        return value.join(', ');
    }

    return value || '';
};

const SubmissionsTable = ({formResults}) => {
    const {siteKey} = window.contextJsParameters;

    const [currentPage, setCurrentPage] = useState(1);
    const [itemsPerPage, setItemsPerPage] = useState(25);
    const [sortBy, setSortBy] = useState('created');
    const [sortDirection, setSortDirection] = useState('descending');
    const [selectedSubmissionId, setSelectedSubmissionId] = useState(null);

    const {loading, error, data} = useQuery(GET_SUBMISSIONS, {
        variables: {
            siteKey,
            formResultsId: formResults.id,
            page: currentPage,
            pageSize: itemsPerPage,
            sortBy,
            sortDirection
        },
        fetchPolicy: 'network-only'
    });

    const submissions = data?.formidableSubmissions?.nodes || [];
    const pageInfo = data?.formidableSubmissions?.pageInfo;

    const rows = useMemo(() => submissions.map(toRow), [submissions]);

    const dynamicFieldNames = useMemo(() => {
        const names = new Set();

        submissions.forEach(submission => {
            submission.values.forEach(field => names.add(field.name));
        });

        return Array.from(names);
    }, [submissions]);

    const columns = useMemo(() => {
        const fixedColumns = [
            {
                key: 'created',
                label: 'Date',
                isSortable: true,
                width: '180px'
            },
            {
                key: 'status',
                label: 'Status',
                isSortable: true,
                width: '120px'
            },
            {
                key: 'submitterUsername',
                label: 'User',
                isSortable: true,
                width: '160px'
            },
            {
                key: 'locale',
                label: 'Locale',
                width: '80px'
            },
            {
                key: 'fileCount',
                label: 'Files',
                width: '80px',
                align: 'right'
            }
        ];

        const fieldColumns = dynamicFieldNames.map(fieldName => ({
            key: fieldName,
            label: fieldName,
            isScrollable: true,
            render: (_value, row) => valueToString(row.values[fieldName])
        }));

        return [...fixedColumns, ...fieldColumns];
    }, [dynamicFieldNames]);

    if (loading) {
        return <Loader/>;
    }

    if (error) {
        return <ErrorState message={error.message}/>;
    }

    if (rows.length === 0) {
        return <EmptyState title="No submissions"/>;
    }

    return (
        <>
            <DataTable
                data={rows}
                columns={columns}
                primaryKey="id"
                enableSelection
                enableSorting
                enablePagination
                sortBy={sortBy}
                sortDirection={sortDirection}
                currentPage={currentPage}
                itemsPerPage={itemsPerPage}
                totalItems={pageInfo.totalCount}
                onSortChange={(newSortBy, newSortDirection) => {
                    setSortBy(newSortBy);
                    setSortDirection(newSortDirection);
                }}
                onPageChange={setCurrentPage}
                onItemsPerPageChange={setItemsPerPage}
                renderRow={(row, renderCells) => (
                    <TableRow
                        key={row.id}
                        onDoubleClick={() => setSelectedSubmissionId(row.original.id)}
                    >
                        {renderCells({
                            after: (
                                <TableCellActions
                                    actions={
                                        <Button
                                            icon={<Visibility/>}
                                            variant="ghost"
                                            aria-label="Open submission"
                                            onClick={() => setSelectedSubmissionId(row.original.id)}
                                        />
                                    }
                                />
                            )
                        })}
                    </TableRow>
                )}
            />

            {selectedSubmissionId && (
                <SubmissionDetailDrawer
                    submissionId={selectedSubmissionId}
                    onClose={() => setSelectedSubmissionId(null)}
                />
            )}
        </>
    );
};
```

Note importante : les `key` dynamiques comme `fieldName` ne sont pas des clés directes du type TypeScript si les valeurs sont sous `row.values`. En JS simple cela fonctionne, mais en TypeScript strict il faudra soit :

- aplatir les champs dans la row ;
- typer plus large ;
- créer des colonnes custom avec une convention contrôlée.

Recommandation : aplatir les valeurs côté `toRow`.

---

## 11. Version aplatie côté front

```js
const toRow = submission => {
    const row = {
        id: submission.id,
        path: submission.path,
        name: submission.name,
        created: submission.created,
        status: submission.status,
        locale: submission.locale,
        submitterUsername: submission.submitterUsername,
        ipAddress: submission.ipAddress,
        fileCount: submission.files.length,
        files: submission.files
    };

    submission.values.forEach(field => {
        row[field.name] = field.value.length > 1 ? field.value.join(', ') : field.value[0];
    });

    return row;
};
```

Dans ce cas, les colonnes dynamiques deviennent simples :

```js
const fieldColumns = dynamicFieldNames.map(fieldName => ({
    key: fieldName,
    label: fieldName,
    isScrollable: true
}));
```

C’est l’option la plus simple avec `DataTable`.

---

## 12. Détail d’une soumission

### 12.1 Query

```js
export const GET_SUBMISSION_DETAIL = gql`
    query GetFormidableSubmission($id: String!) {
        formidableSubmission(id: $id) {
            id
            path
            name
            created
            metadata {
                name
                value
            }
            values {
                name
                value
            }
            files {
                fieldName
                fileName
                path
                mimeType
                size
            }
        }
    }
`;
```

### 12.2 Drawer / panneau latéral

Pseudo-code :

```jsx
const SubmissionDetailDrawer = ({submissionId, onClose}) => {
    const {loading, error, data} = useQuery(GET_SUBMISSION_DETAIL, {
        variables: {id: submissionId},
        fetchPolicy: 'network-only'
    });

    if (loading) {
        return <Drawer onClose={onClose}><Loader/></Drawer>;
    }

    if (error) {
        return <Drawer onClose={onClose}><ErrorState message={error.message}/></Drawer>;
    }

    const submission = data.formidableSubmission;

    return (
        <Drawer onClose={onClose} title="Submission detail">
            <Section title="Metadata">
                {submission.metadata.map(item => (
                    <KeyValue key={item.name} label={item.name} value={item.value}/>
                ))}
            </Section>

            <Section title="Submitted values">
                {submission.values.map(field => (
                    <KeyValue
                        key={field.name}
                        label={field.name}
                        value={field.value.join(', ')}
                    />
                ))}
            </Section>

            <Section title="Files">
                {submission.files.map(file => (
                    <FileRow
                        key={`${file.fieldName}-${file.fileName}`}
                        fieldName={file.fieldName}
                        fileName={file.fileName}
                        mimeType={file.mimeType}
                        size={file.size}
                        downloadUrl={buildDownloadUrl(file.path)}
                    />
                ))}
            </Section>
        </Drawer>
    );
};
```

---

## 13. Gestion des fichiers

Les fichiers sont stockés sous :

```text
<submission>/files/<fieldName>/<fileName>/jcr:content
```

Pour télécharger un fichier, deux options sont possibles.

### Option A : URL JCR directe

Construire une URL vers le noeud fichier si Jahia permet de le servir :

```js
const buildDownloadUrl = filePath => {
    return `${window.contextJsParameters.contextPath}/files/${filePath}`;
};
```

À valider selon le routing Jahia exact.

### Option B : endpoint dédié

Créer un endpoint backend :

```text
GET /modules/formidable-results/download?path=<encodedPath>
```

ou un endpoint basé sur l’identifiant du fichier :

```text
GET /modules/formidable-results/download?id=<fileNodeIdentifier>
```

Recommandation : endpoint dédié, car il permet :

- de vérifier la permission ;
- d’éviter d’exposer directement des chemins JCR sensibles ;
- de forcer `Content-Disposition: attachment`.

Pseudo-code Java :

```java
void downloadFile(String fileNodeIdentifier, HttpServletResponse response) {
    JCRNodeWrapper fileNode = session.getNodeByIdentifier(fileNodeIdentifier);

    assertFileBelongsToAccessibleSubmission(fileNode);
    assertPermission(fileNode, "formidableResultsDownloadFiles");

    JCRNodeWrapper content = fileNode.getNode("jcr:content");

    Binary binary = content.getProperty("jcr:data").getBinary();
    String mimeType = getStringProperty(content, "jcr:mimeType");

    response.setContentType(mimeType);
    response.setHeader(
        "Content-Disposition",
        "attachment; filename=\"" + sanitize(fileNode.getName()) + "\""
    );

    copy(binary.getStream(), response.getOutputStream());
}
```

---

## 14. I18n

Créer un namespace :

```text
formidable-results
```

Exemple `fr.json` :

```json
{
  "label": {
    "appsAccordion": {
      "title": "Résultats de formulaires"
    }
  },
  "table": {
    "date": "Date",
    "status": "Statut",
    "user": "Utilisateur",
    "locale": "Langue",
    "files": "Fichiers",
    "actions": "Actions"
  },
  "empty": {
    "noForms": "Aucun formulaire avec résultats",
    "noSubmissions": "Aucune soumission"
  }
}
```

---

## 15. États UI à gérer

### Loading

- chargement de la liste des formulaires ;
- chargement des soumissions ;
- chargement du détail.

### Empty states

- aucun dossier `/formidable-results` ;
- aucun `fmdb:formResults` ;
- aucune soumission pour un formulaire ;
- soumission sans `data` ;
- soumission sans `files`.

### Erreurs

- permissions insuffisantes ;
- formulaire supprimé mais résultats encore présents ;
- soumission déplacée ou supprimée entre la liste et le détail ;
- fichier manquant ;
- propriété JCR multi-valuée invalide ou type non textuel.

---

## 16. Sécurité

À vérifier côté backend pour chaque query :

```text
formidableResultsAccess
```

À vérifier côté download :

```text
formidableResultsDownloadFiles
```

À vérifier côté suppression future :

```text
formidableResultsDelete
```

Ne pas s’appuyer uniquement sur le fait que l’onglet est masqué côté front. L’accès doit être contrôlé côté GraphQL / backend.

---

## 17. Performance

### V1 acceptable

- lecture récursive ;
- tri en mémoire ;
- pagination en mémoire.

Suffisant pour des volumes modestes.

### V2 recommandée

- requête JCR-SQL2 sur `[fmdb:formSubmission]` ;
- `ISDESCENDANTNODE` ;
- tri par `jcr:created` ;
- `limit` / `offset` ;
- index JCR si nécessaire.

Pseudo-code SQL2 :

```sql
SELECT * FROM [fmdb:formSubmission] AS s
WHERE ISDESCENDANTNODE(s, '/sites/industrial/formidable-results/contact/submissions')
ORDER BY s.[jcr:created] DESC
```

---

## 18. Export CSV futur

Endpoint ou mutation :

```graphql
formidableSubmissionsCsv(formResultsId: String!, filters: FormidableSubmissionFilters): String!
```

Approche :

1. récupérer toutes les soumissions filtrées ;
2. collecter l’union des noms de champs ;
3. créer un header :

```text
created,status,submitterUsername,locale,ipAddress,<field1>,<field2>,...
```

4. écrire les valeurs ;
5. retourner une URL de téléchargement ou un contenu encodé.

---

## 19. Suppression future

Mutation :

```graphql
deleteFormidableSubmission(id: String!): Boolean!
```

Pseudo-code :

```java
boolean deleteSubmission(String id) {
    JCRNodeWrapper submission = session.getNodeByIdentifier(id);

    assertNodeType(submission, "fmdb:formSubmission");
    assertPermission(submission, "formidableResultsDelete");

    submission.remove();
    session.save();

    return true;
}
```

Côté front :

- sélection multiple via `enableSelection` ;
- bouton “Delete selected” ;
- confirmation obligatoire.

---

## 20. Spec de livraison V1

### Backend V1

- GraphQL `formidableResults(siteKey)` ;
- GraphQL `formidableSubmissions(...)` ;
- GraphQL `formidableSubmission(id)` ;
- permissions ;
- lecture des fichiers en résumé ;
- pas de suppression ;
- pas d’export ;
- pagination backend en mémoire.

### Frontend V1

- `init.js` avec `registry.add('adminRoute', 'formidableResults', ...)` ;
- écran React dans jContent ;
- sidebar des formulaires ;
- `DataTable` des soumissions ;
- colonnes metadata fixes ;
- colonnes dynamiques pour champs soumis ;
- drawer de détail ;
- i18n FR/EN ;
- loading / empty / error states.

---

## 21. Pseudo-code global

```text
on module initialization:
  load i18n namespace
  register adminRoute "formidableResults" into jcontent additional apps
  require module installed on site
  require permission formidableResultsAccess
  render FormResultsApp

FormResultsApp:
  read siteKey from contextJsParameters
  query formidableResults(siteKey)
  if loading -> loader
  if error -> error state
  if no forms -> empty state
  select first form by default
  render FormResultsList
  render SubmissionsTable(selectedForm)

SubmissionsTable:
  keep state:
    currentPage
    itemsPerPage
    sortBy
    sortDirection
    selectedSubmissionId

  query formidableSubmissions(
    siteKey,
    formResultsId,
    currentPage,
    itemsPerPage,
    sortBy,
    sortDirection
  )

  convert submissions to table rows
  collect dynamic field columns
  render Moonstone DataTable:
    data = rows
    columns = fixed columns + field columns
    primaryKey = id
    enableSelection = true
    enableSorting = true
    enablePagination = true
    controlled pagination
    controlled sorting
    renderRow adds action cell

  on row action:
    open SubmissionDetailDrawer

SubmissionDetailDrawer:
  query formidableSubmission(id)
  render metadata
  render submitted values
  render uploaded files
```

---

## 22. Points d’attention spécifiques au stockage save2jcr

Le backend doit traiter ces cas :

```text
submissions/<submission>
submissions/YYYY/<submission>
submissions/YYYY/MM/<submission>
submissions/YYYY/MM/DD/<submission>
```

même si le cas attendu est `YYYY/MM/DD`.

Il ne faut pas supposer que tous les noeuds enfants de `submissions` sont des soumissions directes.

Il faut éviter d’identifier un formulaire par le nom du dossier `fmdb:formResults`. La vraie identité est `parentForm`.

Pour le détail ou les actions, il faut préférer l’`identifier` JCR à `path`, car les chemins peuvent changer si un formulaire est renommé ou si une soumission est déplacée par auto-splitting.

---

## 23. Remarques d’implémentation

### Workspace

Le choix du workspace dépend de l’usage attendu :

- `EDIT` : cohérent avec jContent et les données de contribution ;
- `LIVE` : seulement si les résultats doivent être consultés comme données publiées.

Pour un écran d’administration dans jContent, `EDIT` est le choix par défaut.

### Colonnes dynamiques

Idéalement, les colonnes dynamiques doivent être issues de la définition du formulaire plutôt que uniquement des soumissions retournées sur la page courante. Cela permet d’éviter que les colonnes changent d’une page à l’autre.

Approche V1 : union des champs présents dans les soumissions de la page.

Approche V2 : résolution des champs depuis le formulaire parent référencé par `parentForm`.

### Formulaire renommé

Comme le dossier `fmdb:formResults` peut être renommé pour rester aligné avec le nom courant du formulaire, l’écran doit toujours utiliser l’identifiant JCR du `formResults` côté API.


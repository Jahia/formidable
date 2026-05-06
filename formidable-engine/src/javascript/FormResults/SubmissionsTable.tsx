import React, {useMemo, useState} from 'react';
import {useQuery} from '@apollo/client';
import {Loader, Typography, Button} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_SUBMISSIONS} from './FormResultsApp.gql-queries';
import {
    buildSubmissionsQuery,
    formatDate,
    parseSubmissionNode,
    type FormResultsNode,
    type SubmissionRow
} from './FormResults.utils';
import {SubmissionDetailPanel} from './SubmissionDetailPanel';

interface SubmissionsTableProps {
    formResults: FormResultsNode;
}

const PAGE_SIZE_OPTIONS = [10, 25, 50];

export const SubmissionsTable = ({formResults}: SubmissionsTableProps) => {
    const {t} = useTranslation('formidable-engine');

    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [sortBy] = useState('created');
    const [sortDirection] = useState('descending');
    const [selectedSubmission, setSelectedSubmission] = useState<SubmissionRow | null>(null);

    const submissionsQuery = useMemo(
        () => buildSubmissionsQuery(formResults.path, sortBy, sortDirection),
        [formResults.path, sortBy, sortDirection]
    );

    const offset = (currentPage - 1) * pageSize;

    const {loading, error, data} = useQuery(GET_SUBMISSIONS, {
        variables: {
            submissionsQuery,
            limit: pageSize,
            offset
        },
        fetchPolicy: 'network-only'
    });

    const queryResult = data?.jcr?.nodesByQuery;
    const totalCount = queryResult?.pageInfo?.totalCount ?? 0;
    const hasNextPage = queryResult?.pageInfo?.hasNextPage ?? false;
    const totalPages = Math.ceil(totalCount / pageSize);

    const submissions: SubmissionRow[] = useMemo(() => {
        const nodes = queryResult?.nodes ?? [];
        return nodes.map(parseSubmissionNode);
    }, [queryResult]);

    const dynamicFieldNames = useMemo(() => {
        const names = new Set<string>();
        for (const sub of submissions) {
            for (const fv of sub.fieldValues) {
                names.add(fv.name);
            }
        }

        return Array.from(names);
    }, [submissions]);

    const formLabel = formResults.parentForm?.refNode?.displayName ?? formResults.displayName ?? formResults.name;

    if (loading) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '200px'}}>
                <Loader size="big"/>
            </div>
        );
    }

    if (error) {
        return <Typography color="danger">{error.message}</Typography>;
    }

    if (submissions.length === 0 && currentPage === 1) {
        return (
            <div style={{textAlign: 'center', padding: '48px'}}>
                <Typography variant="heading" weight="bold">{t('formResults.empty.noSubmissions')}</Typography>
            </div>
        );
    }

    return (
        <div style={{display: 'flex', gap: '16px', height: '100%'}}>
            <div style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden'}}>
                <div style={{marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                    <Typography variant="heading" weight="bold">
                        {formLabel} ({totalCount})
                    </Typography>
                </div>

                <div style={{flex: 1, overflow: 'auto'}}>
                    <table style={{width: '100%', borderCollapse: 'collapse', fontSize: '14px'}}>
                        <thead>
                            <tr style={{borderBottom: '2px solid var(--color-gray_light40)', textAlign: 'left'}}>
                                <th style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{t('formResults.table.date')}</th>
                                <th style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{t('formResults.table.status')}</th>
                                <th style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{t('formResults.table.user')}</th>
                                <th style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{t('formResults.table.locale')}</th>
                                <th style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{t('formResults.table.files')}</th>
                                {dynamicFieldNames.map(fieldName => (
                                    <th key={fieldName} style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{fieldName}</th>
                                ))}
                                <th style={{padding: '8px 12px', width: '60px'}}/>
                            </tr>
                        </thead>
                        <tbody>
                            {submissions.map(submission => (
                                <tr
                                    key={submission.uuid}
                                    style={{
                                        borderBottom: '1px solid var(--color-gray_light40)',
                                        cursor: 'pointer',
                                        backgroundColor: selectedSubmission?.uuid === submission.uuid ? 'var(--color-blue10)' : 'transparent'
                                    }}
                                    onClick={() => setSelectedSubmission(submission)}
                                >
                                    <td style={{padding: '8px 12px', whiteSpace: 'nowrap'}}>{formatDate(submission.created)}</td>
                                    <td style={{padding: '8px 12px'}}>{submission.status ?? ''}</td>
                                    <td style={{padding: '8px 12px'}}>{submission.submitterUsername ?? ''}</td>
                                    <td style={{padding: '8px 12px'}}>{submission.locale ?? ''}</td>
                                    <td style={{padding: '8px 12px', textAlign: 'right'}}>{submission.files.length}</td>
                                    {dynamicFieldNames.map(fieldName => {
                                        const field = submission.fieldValues.find(fv => fv.name === fieldName);
                                        const displayValue = field ? field.values.join(', ') : '';
                                        return (
                                            <td
                                                key={fieldName}
                                                style={{
                                                    padding: '8px 12px',
                                                    maxWidth: '200px',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap'
                                                }}
                                                title={displayValue}
                                            >
                                                {displayValue}
                                            </td>
                                        );
                                    })}
                                    <td style={{padding: '8px 12px'}}>
                                        <Button
                                            variant="ghost"
                                            size="small"
                                            label={t('formResults.table.view')}
                                            onClick={e => {
                                                e.stopPropagation();
                                                setSelectedSubmission(submission);
                                            }}
                                        />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderTop: '1px solid var(--color-gray_light40)'}}>
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <Typography variant="caption">
                            {t('formResults.table.rowsPerPage')}
                        </Typography>
                        <select
                            value={pageSize}
                            onChange={e => {
                                setPageSize(Number(e.target.value));
                                setCurrentPage(1);
                            }}
                            style={{padding: '4px 8px'}}
                        >
                            {PAGE_SIZE_OPTIONS.map(size => (
                                <option key={size} value={size}>{size}</option>
                            ))}
                        </select>
                    </div>
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <Typography variant="caption">
                            {t('formResults.table.pageInfo', {
                                current: String(currentPage),
                                total: String(totalPages || 1),
                                count: String(totalCount)
                            } as any)}
                        </Typography>
                        <Button
                            variant="ghost"
                            size="small"
                            label="←"
                            isDisabled={currentPage <= 1}
                            onClick={() => setCurrentPage(p => p - 1)}
                        />
                        <Button
                            variant="ghost"
                            size="small"
                            label="→"
                            isDisabled={!hasNextPage}
                            onClick={() => setCurrentPage(p => p + 1)}
                        />
                    </div>
                </div>
            </div>

            {selectedSubmission && (
                <SubmissionDetailPanel
                    submission={selectedSubmission}
                    onClose={() => setSelectedSubmission(null)}
                />
            )}
        </div>
    );
};



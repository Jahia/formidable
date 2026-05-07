import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@apollo/client';
import {
    Button,
    Loader,
    Paper,
    SortIndicator,
    Table,
    TableBody,
    TableBodyCell,
    TableHead,
    TableHeadCell,
    TablePagination,
    TableRow,
    Typography,
    Visibility
} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_SUBMISSIONS} from '../graphql';
import {
    buildSubmissionsQuery,
    formatDate,
    parseSubmissionNode,
    type FormResultsNode,
    type SubmissionRow
} from '../FormResults.utils';

interface SubmissionsTableProps {
    formResults: FormResultsNode;
    selectedSubmission: SubmissionRow | null;
    onSelectSubmission: (submission: SubmissionRow | null) => void;
    onRegisterRefresh: (refresh: (() => Promise<unknown>) | null) => void;
}

const PAGE_SIZE_OPTIONS = [10, 25, 50];

export const SubmissionsTable = ({
    formResults,
    selectedSubmission,
    onSelectSubmission,
    onRegisterRefresh
}: SubmissionsTableProps) => {
    const {t} = useTranslation('formidable-engine');

    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [sortBy] = useState('created');
    const [sortDirection, setSortDirection] = useState<'ascending' | 'descending'>('descending');

    const submissionsQuery = useMemo(
        () => buildSubmissionsQuery(formResults.path, sortBy, sortDirection),
        [formResults.path, sortBy, sortDirection]
    );

    const offset = (currentPage - 1) * pageSize;

    const {loading, error, data, refetch} = useQuery(GET_SUBMISSIONS, {
        variables: {
            submissionsQuery,
            limit: pageSize,
            offset,
            workspace: 'LIVE'
        },
        fetchPolicy: 'network-only'
    });

    const queryResult = data?.jcr?.nodesByQuery;
    const totalCount = queryResult?.pageInfo?.totalCount ?? 0;
    const totalPages = Math.ceil(totalCount / pageSize);

    const submissions: SubmissionRow[] = useMemo(() => {
        const nodes = queryResult?.nodes ?? [];
        return nodes.map(parseSubmissionNode);
    }, [queryResult]);

    useEffect(() => {
        onRegisterRefresh(() => refetch());

        return () => {
            onRegisterRefresh(null);
        };
    }, [onRegisterRefresh, refetch]);

    useEffect(() => {
        if (!selectedSubmission) {
            return;
        }

        const updatedSelection = submissions.find(submission => submission.uuid === selectedSubmission.uuid);
        if (updatedSelection && updatedSelection !== selectedSubmission) {
            onSelectSubmission(updatedSelection);
        }
    }, [onSelectSubmission, selectedSubmission, submissions]);

    const tableRef = useRef<HTMLDivElement | null>(null);

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') {
            return;
        }

        e.preventDefault();

        if (submissions.length === 0) {
            return;
        }

        const currentIndex = selectedSubmission
            ? submissions.findIndex(s => s.uuid === selectedSubmission.uuid)
            : -1;

        let nextIndex: number;
        if (e.key === 'ArrowDown') {
            nextIndex = currentIndex < submissions.length - 1 ? currentIndex + 1 : 0;
        } else {
            nextIndex = currentIndex > 0 ? currentIndex - 1 : submissions.length - 1;
        }

        onSelectSubmission(submissions[nextIndex]);
    }, [submissions, selectedSubmission, onSelectSubmission]);

    useEffect(() => {
        if (!selectedSubmission || !tableRef.current) {
            return;
        }

        const row = tableRef.current.querySelector<HTMLElement>(`[data-submission-uuid="${selectedSubmission.uuid}"]`);
        if (row) {
            row.focus({preventScroll: true});
            row.scrollIntoView({block: 'nearest'});
        }
    }, [selectedSubmission]);


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
        <Paper hasPadding={false} style={{display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0}}>
            <div
                ref={tableRef}
                tabIndex={0}
                onKeyDown={handleKeyDown}
                style={{
                    flex: 1,
                    overflow: 'auto',
                    outline: 'none'
                }}
            >
                <Table
                    style={{
                        minWidth: '870px'
                    }}
                >
                    <TableHead>
                        <TableRow>
                            <TableHeadCell
                                width="180px"
                                style={{cursor: 'pointer'}}
                                onClick={() => setSortDirection(current => current === 'descending' ? 'ascending' : 'descending')}
                                iconEnd={<SortIndicator direction={sortDirection} isSorted/>}
                            >
                                {t('formResults.table.date')}
                            </TableHeadCell>
                            <TableHeadCell width="110px">{t('formResults.table.user')}</TableHeadCell>
                            <TableHeadCell width="90px">{t('formResults.table.locale')}</TableHeadCell>
                            <TableHeadCell width="140px">{t('formResults.table.ipAddress')}</TableHeadCell>
                            <TableHeadCell width="80px" textAlign="center">{t('formResults.table.files')}</TableHeadCell>
                            <TableHeadCell width="90px" textAlign="center">{t('formResults.table.filledFields')}</TableHeadCell>
                            <TableHeadCell width="110px" textAlign="right"/>
                        </TableRow>
                    </TableHead>

                    <TableBody>
                        {submissions.map(submission => (
                            <TableRow
                                key={submission.uuid}
                                isHighlighted={selectedSubmission?.uuid === submission.uuid}
                                tabIndex={-1}
                                data-submission-uuid={submission.uuid}
                                onClick={() => onSelectSubmission(submission)}
                                style={{cursor: 'pointer', outline: 'none'}}
                            >
                                <TableBodyCell width="180px" isScrollable>
                                    {formatDate(submission.created)}
                                </TableBodyCell>
                                <TableBodyCell width="110px" isScrollable>
                                    {submission.submitterUsername ?? ''}
                                </TableBodyCell>
                                <TableBodyCell width="90px">
                                    {submission.locale ?? ''}
                                </TableBodyCell>
                                <TableBodyCell width="140px" isScrollable>
                                    {submission.ipAddress ?? ''}
                                </TableBodyCell>
                                <TableBodyCell width="80px" textAlign="center">
                                    {submission.files.length}
                                </TableBodyCell>
                                <TableBodyCell width="90px" textAlign="center">
                                    {submission.fieldValues.length}
                                </TableBodyCell>
                                <TableBodyCell width="110px" textAlign="right">
                                    <Button
                                        variant="ghost"
                                        size="small"
                                        label={t('formResults.table.view')}
                                        icon={<Visibility/>}
                                        onClick={e => {
                                            e.stopPropagation();
                                            onSelectSubmission(submission);
                                        }}
                                    />
                                </TableBodyCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </div>

            <div style={{padding: '0 16px 16px'}}>
                <TablePagination
                    rowsPerPage={pageSize}
                    rowsPerPageOptions={PAGE_SIZE_OPTIONS}
                    onRowsPerPageChange={rowsPerPage => {
                        setPageSize(rowsPerPage);
                        setCurrentPage(1);
                    }}
                    totalNumberOfRows={totalCount}
                    currentPage={currentPage}
                    onPageChange={setCurrentPage}
                    label={{
                        rowsPerPage: t('formResults.table.rowsPerPage'),
                        of: t('formResults.table.of')
                    }}
                />
                <div style={{paddingTop: '8px'}}>
                    <Typography variant="caption">
                        {t('formResults.table.pageInfo', {
                            current: String(currentPage),
                            total: String(totalPages || 1),
                            count: String(totalCount)
                        } as any)}
                    </Typography>
                </div>
            </div>
        </Paper>
    );
};


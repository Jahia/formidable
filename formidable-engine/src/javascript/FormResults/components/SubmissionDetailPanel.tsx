import React, {useState} from 'react';
import {Button, Close, Download, File, FilePdf, FileVideo, Paper, Separator, Typography, Visibility} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {formatDate, type SubmissionRow, type SubmissionFile} from '../FormResults.utils';
import {FilePreviewDialog} from './FilePreviewDialog';

interface SubmissionDetailPanelProps {
    submission: SubmissionRow;
    onClose: () => void;
}

const isPreviewable = (mimeType: string | null): boolean => {
    if (!mimeType) {
        return false;
    }

    return mimeType.startsWith('image/') || mimeType.startsWith('video/') || mimeType === 'application/pdf';
};

const fallbackIconStyle = {
    width: '3.5rem',
    height: '3.5rem'
};

const getFileFallbackIcon = (mimeType: string | null) => {
    if (mimeType === 'application/pdf') {
        return <FilePdf color="red" style={fallbackIconStyle}/>;
    }

    if (mimeType?.startsWith('video/')) {
        return <FileVideo color="blue" style={fallbackIconStyle}/>;
    }

    return <File color="gray" style={fallbackIconStyle}/>;
};

export const SubmissionDetailPanel = ({submission, onClose}: SubmissionDetailPanelProps) => {
    const {t} = useTranslation('formidable-engine');
    const [previewFile, setPreviewFile] = useState<SubmissionFile | null>(null);

    const metadata = [
        {label: t('formResults.detail.created'), value: formatDate(submission.created)},
        {label: t('formResults.detail.origin'), value: submission.origin},
        {label: t('formResults.detail.ipAddress'), value: submission.ipAddress},
        {label: t('formResults.detail.locale'), value: submission.locale},
        {label: t('formResults.detail.user'), value: submission.submitterUsername},
        {label: t('formResults.detail.userAgent'), value: submission.userAgent},
        {label: t('formResults.detail.referer'), value: submission.referer}
    ].filter(item => item.value);

    return (
        <aside style={{
            width: '500px',
            minWidth: '500px',
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
            backgroundColor: 'var(--color-light)',
            borderRadius: '4px',
            overflow: 'hidden'
        }}>
            <div style={{
                overflowY: 'auto',
                flex: 1,
                minHeight: 0
            }}>
            <Paper hasPadding style={{display: 'flex', flexDirection: 'column', gap: '16px', boxShadow: 'none'}}>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px'}}>
                    <Typography variant="heading" weight="bold">
                            {t('formResults.detail.title')}
                    </Typography>
                    <Button
                        variant="ghost"
                        size="small"
                        icon={<Close/>}
                        aria-label={t('formResults.detail.close')}
                        onClick={onClose}
                    />
                </div>

                <Separator/>

                <section>
                    <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                        {t('formResults.detail.metadata')}
                    </Typography>
                    <div style={{display: 'grid', gridTemplateColumns: '120px minmax(0, 1fr)', gap: '8px 12px'}}>
                        {metadata.map(item => (
                            <React.Fragment key={item.label}>
                                <Typography variant="body" weight="bold">
                                    {item.label}
                                </Typography>
                                <Typography variant="body" style={{wordBreak: 'break-all'}}>
                                    {item.value}
                                </Typography>
                            </React.Fragment>
                        ))}
                    </div>
                </section>

                <Separator/>

                <section>
                    <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                        {t('formResults.detail.submittedValues')}
                    </Typography>
                    {submission.fieldValues.length === 0 ? (
                        <Typography variant="body" isItalic>{t('formResults.detail.noValues')}</Typography>
                    ) : (
                        <div style={{display: 'grid', gridTemplateColumns: '120px minmax(0, 1fr)', gap: '8px 12px'}}>
                            {submission.fieldValues.map(field => (
                                <React.Fragment key={field.name}>
                                    <Typography variant="body" weight="bold">
                                        {field.name}
                                    </Typography>
                                    <Typography variant="body" style={{wordBreak: 'break-all'}}>
                                        {field.values.join(', ')}
                                    </Typography>
                                </React.Fragment>
                            ))}
                        </div>
                    )}
                </section>

                {submission.files.length > 0 && (
                    <section>
                        <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                            {t('formResults.detail.files')}
                        </Typography>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                            {submission.files.map(file => (
                                <Paper
                                    key={`${file.fieldName}-${file.fileName}`}
                                    hasPadding
                                    style={{display: 'flex', flexDirection: 'column', gap: '8px'}}
                                >
                                    <div style={{display: 'flex', gap: '12px', alignItems: 'flex-start'}}>
                                        {file.thumbnailUrl ? (
                                            <img
                                                src={file.thumbnailUrl}
                                                alt={file.fileName}
                                                onClick={() => isPreviewable(file.mimeType) && setPreviewFile(file)}
                                                style={{
                                                    width: '150px',
                                                    maxHeight: '150px',
                                                    objectFit: 'contain',
                                                    padding: '16px',
                                                    borderRadius: '4px',
                                                    backgroundColor: 'var(--color-gray_light40)',
                                                    flexShrink: 0,
                                                    cursor: isPreviewable(file.mimeType) ? 'pointer' : 'default'
                                                }}
                                            />
                                        ) : (
                                            <button
                                                type="button"
                                                onClick={() => isPreviewable(file.mimeType) && setPreviewFile(file)}
                                                aria-label={isPreviewable(file.mimeType) ? t('formResults.detail.preview') : file.fileName}
                                                style={{
                                                    width: '150px',
                                                    maxHeight: '150px',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    border: 'none',
                                                    padding: '16px 0',
                                                    borderRadius: '4px',
                                                    backgroundColor: 'var(--color-gray_light40)',
                                                    color: 'var(--color-gray)',
                                                    flexShrink: 0,
                                                    cursor: isPreviewable(file.mimeType) ? 'pointer' : 'default'
                                                }}
                                            >
                                                {getFileFallbackIcon(file.mimeType)}
                                            </button>
                                        )}
                                        <div style={{minWidth: 0, flex: 1}}>
                                            <Typography variant="body" weight="bold" style={{wordBreak: 'break-all'}}>
                                                {file.fileName}
                                            </Typography>
                                            <Typography variant="body" style={{display: 'block'}}>
                                                {file.fieldName}
                                            </Typography>
                                            {file.mimeType && (
                                                <Typography variant="body" style={{display: 'block', color: 'var(--color-gray)'}}>
                                                    {file.mimeType}
                                                </Typography>
                                            )}
                                        </div>
                                    </div>
                                    <div style={{display: 'flex', justifyContent: 'flex-end', gap: '4px'}}>
                                        {isPreviewable(file.mimeType) && (
                                            <Button
                                                variant="ghost"
                                                size="small"
                                                label={t('formResults.detail.preview')}
                                                icon={<Visibility/>}
                                                onClick={() => setPreviewFile(file)}
                                            />
                                        )}
                                        <a
                                            href={file.fileUrl}
                                            download={file.fileName}
                                            style={{textDecoration: 'none'}}
                                        >
                                            <Button
                                                variant="ghost"
                                                size="small"
                                                label={t('formResults.detail.download')}
                                                icon={<Download/>}
                                            />
                                        </a>
                                    </div>
                                </Paper>
                            ))}
                        </div>
                    </section>
                )}
            </Paper>
            </div>
            {previewFile && (
                <FilePreviewDialog
                    file={previewFile}
                    onClose={() => setPreviewFile(null)}
                />
            )}
        </aside>
    );
};


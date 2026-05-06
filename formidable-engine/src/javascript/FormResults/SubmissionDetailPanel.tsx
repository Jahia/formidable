import React, {useCallback, useRef, useState} from 'react';
import {Button, Close, Download, Paper, Separator, Typography, Visibility} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {formatDate, buildDownloadUrl, type SubmissionRow, type SubmissionFile} from './FormResults.utils';

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

const FilePreviewDialog = ({file, onClose}: {file: SubmissionFile; onClose: () => void}) => {
    const {t} = useTranslation('formidable-engine');
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const fileUrl = buildDownloadUrl(file.filePath);
    const isImage = file.mimeType?.startsWith('image/');
    const isVideo = file.mimeType?.startsWith('video/');

    const handleBackdropClick = useCallback((e: React.MouseEvent) => {
        if (e.target === dialogRef.current) {
            onClose();
        }
    }, [onClose]);

    return (
        <dialog
            ref={el => {
                dialogRef.current = el;
                if (el && !el.open) {
                    el.showModal();
                }
            }}
            onClick={handleBackdropClick}
            style={{
                border: 'none',
                borderRadius: '8px',
                padding: 0,
                maxWidth: '90vw',
                maxHeight: '90vh',
                backgroundColor: 'var(--color-light)',
                boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)'
            }}
        >
            <div style={{display: 'flex', flexDirection: 'column', maxHeight: '90vh'}}>
                <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '12px 16px',
                    borderBottom: '1px solid var(--color-gray_light40)'
                }}>
                    <Typography variant="subheading" weight="bold" style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        marginRight: '16px'
                    }}>
                        {file.fileName}
                    </Typography>
                    <div style={{display: 'flex', gap: '8px', flexShrink: 0}}>
                        <a
                            href={fileUrl}
                            download={file.fileName}
                            style={{textDecoration: 'none'}}
                        >
                            <Button
                                variant="ghost"
                                size="small"
                                icon={<Download/>}
                                label={t('formResults.detail.download')}
                            />
                        </a>
                        <Button
                            variant="ghost"
                            size="small"
                            icon={<Close/>}
                            onClick={onClose}
                        />
                    </div>
                </div>
                <div style={{
                    flex: 1,
                    overflow: 'auto',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    padding: '16px',
                    backgroundColor: 'var(--color-gray_light40)'
                }}>
                    {isImage ? (
                        <img
                            src={fileUrl}
                            alt={file.fileName}
                            style={{maxWidth: '100%', maxHeight: '75vh', objectFit: 'contain'}}
                        />
                    ) : isVideo ? (
                        <video
                            src={fileUrl}
                            controls
                            style={{maxWidth: '100%', maxHeight: '75vh'}}
                        />
                    ) : (
                        <iframe
                            src={fileUrl}
                            title={file.fileName}
                            style={{width: '80vw', height: '75vh', border: 'none', borderRadius: '4px'}}
                        />
                    )}
                </div>
            </div>
        </dialog>
    );
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
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
        }}>
            <Paper hasPadding style={{display: 'flex', flexDirection: 'column', gap: '16px'}}>
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
                                <Typography variant="caption" weight="bold">
                                    {item.label}
                                </Typography>
                                <Typography variant="caption" style={{wordBreak: 'break-all'}}>
                                    {item.value}
                                </Typography>
                            </React.Fragment>
                        ))}
                    </div>
                </section>

                <section>
                    <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                        {t('formResults.detail.submittedValues')}
                    </Typography>
                    {submission.fieldValues.length === 0 ? (
                        <Typography variant="caption" isItalic>{t('formResults.detail.noValues')}</Typography>
                    ) : (
                        <div style={{display: 'grid', gridTemplateColumns: '120px minmax(0, 1fr)', gap: '8px 12px'}}>
                            {submission.fieldValues.map(field => (
                                <React.Fragment key={field.name}>
                                    <Typography variant="caption" weight="bold">
                                        {field.name}
                                    </Typography>
                                    <Typography variant="caption" style={{wordBreak: 'break-all'}}>
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
                                        {file.thumbnailUrl && (
                                            <img
                                                src={file.thumbnailUrl}
                                                alt={file.fileName}
                                                onClick={() => isPreviewable(file.mimeType) && setPreviewFile(file)}
                                                style={{
                                                    width: '150px',
                                                    maxHeight: '150px',
                                                    objectFit: 'contain',
                                                    borderRadius: '4px',
                                                    backgroundColor: 'var(--color-gray_light40)',
                                                    flexShrink: 0,
                                                    cursor: isPreviewable(file.mimeType) ? 'pointer' : 'default'
                                                }}
                                            />
                                        )}
                                        <div style={{minWidth: 0, flex: 1}}>
                                            <Typography variant="caption" weight="bold" style={{wordBreak: 'break-all'}}>
                                                {file.fileName}
                                            </Typography>
                                            <Typography variant="caption" style={{display: 'block'}}>
                                                {file.fieldName}
                                            </Typography>
                                            {file.mimeType && (
                                                <Typography variant="caption" style={{display: 'block', color: 'var(--color-gray)'}}>
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
                                            href={buildDownloadUrl(file.filePath)}
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
            {previewFile && (
                <FilePreviewDialog
                    file={previewFile}
                    onClose={() => setPreviewFile(null)}
                />
            )}
        </aside>
    );
};

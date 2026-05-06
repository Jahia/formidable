import React from 'react';
import {Typography, Button} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {formatDate, buildDownloadUrl, type SubmissionRow} from './FormResults.utils';

interface SubmissionDetailPanelProps {
    submission: SubmissionRow;
    onClose: () => void;
}

export const SubmissionDetailPanel = ({submission, onClose}: SubmissionDetailPanelProps) => {
    const {t} = useTranslation('formidable-engine');

    const metadata = [
        {label: t('formResults.detail.created'), value: formatDate(submission.created)},
        {label: t('formResults.detail.origin'), value: submission.origin},
        {label: t('formResults.detail.status'), value: submission.status},
        {label: t('formResults.detail.ipAddress'), value: submission.ipAddress},
        {label: t('formResults.detail.locale'), value: submission.locale},
        {label: t('formResults.detail.user'), value: submission.submitterUsername},
        {label: t('formResults.detail.userAgent'), value: submission.userAgent},
        {label: t('formResults.detail.referer'), value: submission.referer}
    ].filter(item => item.value);

    return (
        <aside style={{
            width: '400px',
            minWidth: '400px',
            borderLeft: '1px solid var(--color-gray_light40)',
            overflowY: 'auto',
            backgroundColor: 'var(--color-light)',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '24px'
        }}>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                <Typography variant="heading" weight="bold">
                    {t('formResults.detail.title')}
                </Typography>
                <Button variant="ghost" size="small" label="✕" onClick={onClose}/>
            </div>

            {/* Metadata */}
            <section>
                <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                    {t('formResults.detail.metadata')}
                </Typography>
                <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                    {metadata.map(item => (
                        <div key={item.label} style={{display: 'flex', gap: '8px'}}>
                            <Typography variant="caption" weight="bold" style={{minWidth: '120px'}}>
                                {item.label}
                            </Typography>
                            <Typography variant="caption" style={{wordBreak: 'break-all'}}>
                                {item.value}
                            </Typography>
                        </div>
                    ))}
                </div>
            </section>

            {/* Submitted values */}
            <section>
                <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                    {t('formResults.detail.submittedValues')}
                </Typography>
                {submission.fieldValues.length === 0 ? (
                    <Typography variant="caption" isItalic>{t('formResults.detail.noValues')}</Typography>
                ) : (
                    <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        {submission.fieldValues.map(field => (
                            <div key={field.name} style={{display: 'flex', gap: '8px'}}>
                                <Typography variant="caption" weight="bold" style={{minWidth: '120px'}}>
                                    {field.name}
                                </Typography>
                                <Typography variant="caption" style={{wordBreak: 'break-all'}}>
                                    {field.values.join(', ')}
                                </Typography>
                            </div>
                        ))}
                    </div>
                )}
            </section>

            {/* Files */}
            {submission.files.length > 0 && (
                <section>
                    <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                        {t('formResults.detail.files')}
                    </Typography>
                    <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        {submission.files.map(file => (
                            <div
                                key={`${file.fieldName}-${file.fileName}`}
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    padding: '8px',
                                    border: '1px solid var(--color-gray_light40)',
                                    borderRadius: '4px'
                                }}
                            >
                                <div>
                                    <Typography variant="caption" weight="bold">{file.fileName}</Typography>
                                    <Typography variant="caption"> ({file.fieldName})</Typography>
                                    {file.mimeType && (
                                        <Typography variant="caption" style={{display: 'block', color: 'var(--color-gray)'}}>
                                            {file.mimeType}
                                        </Typography>
                                    )}
                                </div>
                                <a
                                    href={buildDownloadUrl(file.filePath)}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    style={{textDecoration: 'none'}}
                                >
                                    <Button variant="ghost" size="small" label={t('formResults.detail.download')}/>
                                </a>
                            </div>
                        ))}
                    </div>
                </section>
            )}
        </aside>
    );
};


import React, {useCallback, useRef} from 'react';
import {Button, Close, Download, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {type SubmissionFile} from '../FormResults.utils';

interface FilePreviewDialogProps {
    file: SubmissionFile;
    onClose: () => void;
}

export const FilePreviewDialog = ({file, onClose}: FilePreviewDialogProps) => {
    const {t} = useTranslation('formidable-engine');
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const fileUrl = file.fileUrl;
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
                            aria-label={t('formResults.detail.close')}
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


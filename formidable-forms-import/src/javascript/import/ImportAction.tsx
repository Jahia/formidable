import React, {useEffect, useState} from 'react';
import {Button, Upload} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {ImportResultsDialog} from './ImportResultsDialog';
import {fetchSettings, type ImportJob, type ImportSettings} from './import.utils';

export interface ImportActionProps {
    /** The site of the Results page. */
    siteKey: string;
    /** Re-reads the list of the page: called once an import wrote into the results. */
    refetchForms: () => Promise<unknown>;
}

/**
 * The Import button of the Results page toolbar and its dialog (docs/architecture/forms-import.md), an action
 * registered on the toolbar's target: offered when the setting is on and the user administers the site. A
 * job left running or unread reopens when the page loads, and the button opens on the job the site has,
 * which may have started since.
 */
export const ImportAction = ({siteKey, refetchForms}: ImportActionProps) => {
    const {t} = useTranslation('formidable-forms-import');
    const [settings, setSettings] = useState<ImportSettings | null>(null);
    const [dialog, setDialog] = useState<{open: boolean; job: ImportJob | null}>({open: false, job: null});

    useEffect(() => {
        if (!siteKey) {
            return;
        }

        fetchSettings(siteKey)
            .then(fetched => {
                setSettings(fetched);
                if (fetched.job) {
                    setDialog({open: true, job: fetched.job});
                }
            })
            .catch(() => {
                setSettings(null);
            });
    }, [siteKey]);

    if (!settings?.allowed) {
        return null;
    }

    const open = async () => {
        try {
            const fetched = await fetchSettings(siteKey);
            setSettings(fetched);
            setDialog({open: true, job: fetched.job ?? null});
        } catch {
            setDialog({open: true, job: null});
        }
    };

    return (
        <>
            <Button
                variant="ghost"
                icon={<Upload/>}
                label={t('formResults.actions.import')}
                title={t('formResults.actions.importTitle')}
                data-sel-role="import-results"
                onClick={open}
            />
            {dialog.open && (
                <ImportResultsDialog
                    siteKey={siteKey}
                    settings={settings}
                    initialJob={dialog.job}
                    onClose={async imported => {
                        setDialog({open: false, job: null});
                        if (imported) {
                            await refetchForms();
                        }
                    }}
                />
            )}
        </>
    );
};

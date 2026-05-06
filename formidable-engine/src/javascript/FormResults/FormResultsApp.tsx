import React, {useState} from 'react';
import {useQuery} from '@apollo/client';
import {Loader, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_FORM_RESULTS_LIST} from './FormResultsApp.gql-queries';
import {FormResultsList} from './FormResultsList';
import {SubmissionsTable} from './SubmissionsTable';
import type {FormResultsNode} from './FormResults.utils';

export const FormResultsApp = () => {
    const {t} = useTranslation('formidable-engine');
    const siteKey = (window as any).contextJsParameters?.siteKey;
    const resultsPath = `/sites/${siteKey}/formidable-results`;

    const [selectedFormResultsId, setSelectedFormResultsId] = useState<string | null>(null);

    const {loading, error, data} = useQuery(GET_FORM_RESULTS_LIST, {
        variables: {resultsPath},
        fetchPolicy: 'network-only',
        skip: !siteKey
    });

    if (!siteKey) {
        return <Typography>{t('formResults.error.noSite')}</Typography>;
    }

    if (loading) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%'}}>
                <Loader size="big"/>
            </div>
        );
    }

    if (error) {
        if (error.graphQLErrors?.some(e => e.message?.includes('javax.jcr.PathNotFoundException'))) {
            return (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', flexDirection: 'column', gap: '1rem'}}>
                    <Typography variant="heading" weight="bold">{t('formResults.empty.noForms')}</Typography>
                    <Typography>{t('formResults.empty.noFormsDescription')}</Typography>
                </div>
            );
        }

        return <Typography color="danger">{error.message}</Typography>;
    }

    const forms: FormResultsNode[] = data?.jcr?.nodeByPath?.children?.nodes ?? [];

    if (forms.length === 0) {
        return (
            <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', flexDirection: 'column', gap: '1rem'}}>
                <Typography variant="heading" weight="bold">{t('formResults.empty.noForms')}</Typography>
                <Typography>{t('formResults.empty.noFormsDescription')}</Typography>
            </div>
        );
    }

    const selectedForm = forms.find(f => f.uuid === selectedFormResultsId) ?? forms[0];

    return (
        <div style={{display: 'flex', height: '100%', overflow: 'hidden'}}>
            <FormResultsList
                forms={forms}
                selectedId={selectedForm.uuid}
                onSelect={setSelectedFormResultsId}
            />
            <div style={{flex: 1, overflow: 'auto', padding: '16px'}}>
                <SubmissionsTable formResults={selectedForm}/>
            </div>
        </div>
    );
};


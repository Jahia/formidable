import React, {useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {Dropdown, Typography} from '@jahia/moonstone';
import {GET_FORM_FIELD_LABELS} from '../graphql';
import {
    type AttachChoice,
    fieldTitle,
    type ImportReportForm,
    mappingConflicts,
    mappingOf,
    type MappingProposal,
    parseTargetFields,
    proposeMapping,
    type SiteForm,
    type TargetField
} from './import.utils';

/** The option of a mapping row that keeps the name of the source field. */
const KEEP_NAME = ':keep';

interface AttachToFormProps {
    form: ImportReportForm;
    choice: AttachChoice;
    /** The forms of the site, null while they load. */
    siteForms: SiteForm[] | null;
    language: string;
    t: (key: string, options?: Record<string, unknown>) => string;
    onChange: (choice: AttachChoice) => void;
}

/**
 * The attachment of the results of a source form to a form of the site (docs/architecture/forms-import.md,
 * "The mapping"): the form picked among those of the site, then one row per field of the source with the
 * field of that form its values land under, proposed by name or by label, or kept as it is.
 */
export const AttachToForm = ({form, choice, siteForms, language, t, onChange}: AttachToFormProps) => {
    const client = useApolloClient();
    // the fields of each form read so far, by identifier: picked again, a form needs no second read
    const [targetFields, setTargetFields] = useState<Record<string, TargetField[]>>({});
    const [proposals, setProposals] = useState<Record<string, Record<string, MappingProposal>>>({});
    const [loadingForm, setLoadingForm] = useState<string | null>(null);
    const [readError, setReadError] = useState('');

    const pickForm = async (uuid: string) => {
        setReadError('');
        let fields = targetFields[uuid];
        if (!fields) {
            setLoadingForm(uuid);
            try {
                const {data} = await client.query({
                    query: GET_FORM_FIELD_LABELS,
                    variables: {formUuid: uuid, language, workspace: 'EDIT'},
                    fetchPolicy: 'network-only'
                });
                fields = parseTargetFields(data);
                setTargetFields(previous => ({...previous, [uuid]: fields}));
            } catch (error) {
                setReadError(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
                setLoadingForm(null);
                return;
            }
            setLoadingForm(null);
        }
        const proposed = proposeMapping(form.fields, fields, language);
        setProposals(previous => ({...previous, [uuid]: proposed}));
        onChange({choice: 'attach', form: uuid, mapping: mappingOf(proposed)});
    };

    const setTarget = (source: string, target: string) => {
        const mapping = {...choice.mapping};
        if (target === KEEP_NAME) {
            delete mapping[source];
        } else {
            mapping[source] = target;
        }
        onChange({...choice, mapping});
    };

    const fields = choice.form ? targetFields[choice.form] : undefined;
    const proposed = choice.form ? proposals[choice.form] ?? {} : {};
    const conflicts = fields ? mappingConflicts(form.fields, choice.mapping) : [];
    const targetOptions = [
        {label: t('formResults.import.attach.keep'), value: KEEP_NAME},
        ...(fields ?? []).map(field => ({label: field.label, description: field.name === field.label ? undefined : field.name, value: field.name}))
    ];
    return (
        <div data-sel-role="import-attach" style={{display: 'flex', flexDirection: 'column', gap: '12px', paddingLeft: '28px'}}>
            <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                <Typography variant="body">{t('formResults.import.attach.form')}</Typography>
                <div data-sel-role="import-attach-form" style={{minWidth: '320px'}}>
                    <Dropdown
                        variant="outlined"
                        placeholder={siteForms?.length === 0 ? t('formResults.import.attach.noForms') : t('formResults.import.attach.pickForm')}
                        data={(siteForms ?? []).map(siteForm => ({label: siteForm.title, description: siteForm.path, value: siteForm.uuid}))}
                        value={choice.form || undefined}
                        isLoading={siteForms === null || loadingForm !== null}
                        isDisabled={siteForms !== null && siteForms.length === 0}
                        onChange={(_event, item) => {
                            if (item.value && item.value !== choice.form) {
                                pickForm(item.value);
                            }
                        }}
                    />
                </div>
            </div>
            {readError && <Typography color="danger">{readError}</Typography>}
            {fields && (
                <div data-sel-role="import-mapping" style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                    <Typography variant="body" weight="bold">{t('formResults.import.attach.mapping')}</Typography>
                    {fields.length === 0 && <Typography variant="body">{t('formResults.import.attach.noFields')}</Typography>}
                    {form.fields.map(field => {
                        const target = choice.mapping[field.name];
                        const proposal = proposed[field.name];
                        const rule = proposal?.target === target ? proposal.rule : null;
                        const title = fieldTitle(field, language);
                        return (
                            <div
                                key={field.name}
                                data-sel-role="import-mapping-row"
                                data-sel-source={field.name}
                                data-sel-target={target ?? ''}
                                data-sel-rule={rule ?? ''}
                                style={{display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto minmax(0, 1fr) auto', alignItems: 'center', gap: '12px'}}
                            >
                                <div style={{minWidth: 0}}>
                                    <Typography variant="body" weight="bold">{field.name}</Typography>
                                    {title && title !== field.name && (
                                        <Typography variant="caption" style={{color: 'var(--color-gray)'}}>{title}</Typography>
                                    )}
                                </div>
                                <Typography variant="body" aria-hidden="true">→</Typography>
                                <div data-sel-role="import-mapping-target">
                                    <Dropdown
                                        variant="outlined"
                                        data={targetOptions}
                                        value={target ?? KEEP_NAME}
                                        isDisabled={fields.length === 0}
                                        onChange={(_event, item) => {
                                            if (item.value) {
                                                setTarget(field.name, item.value);
                                            }
                                        }}
                                    />
                                </div>
                                <Typography variant="caption" style={{color: 'var(--color-gray)', minWidth: '80px'}}>
                                    {rule ? t(`formResults.import.attach.${rule}`) : ''}
                                </Typography>
                            </div>
                        );
                    })}
                    {conflicts.length > 0 && (
                        <Typography color="danger" data-sel-role="import-mapping-conflict">
                            {t('formResults.import.attach.conflict', {names: conflicts.join(', ')})}
                        </Typography>
                    )}
                </div>
            )}
        </div>
    );
};

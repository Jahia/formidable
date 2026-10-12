import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {Dropdown, Typography} from '@jahia/moonstone';
import {GET_FORM_FIELD_LABELS} from '../graphql';
import {
    type AttachChoice,
    availableTargets,
    fieldTitle,
    type ImportReportField,
    mappingConflicts,
    mappingOf,
    parseTargetFields,
    proposeMapping,
    type SiteForm,
    type TargetField,
    unmatchedValues
} from './import.utils';

/** The option of a mapping row that keeps the name of the source field. */
const KEEP_NAME = ':keep';

interface AttachToFormProps {
    /** The fields of the source whose values are mapped: those of the report, or those an entry holds. */
    sourceFields: ImportReportField[];
    choice: AttachChoice;
    /** The forms of the site, null while they load. */
    siteForms: SiteForm[] | null;
    /** The fields of the forms read so far, by identifier: the dialog holds them, so that a remount keeps the rows. */
    targetFields: Record<string, TargetField[]>;
    language: string;
    /** The language the options of a choice field are read in: the default language of the site, which always holds them. */
    optionsLanguage: string;
    t: (key: string, options?: Record<string, unknown>) => string;
    onTargetFields: (formId: string, fields: TargetField[]) => void;
    onChange: (choice: AttachChoice) => void;
}

/**
 * The attachment of the results of a source form to a form of the site (docs/architecture/forms-import.md,
 * "The mapping"): the form picked among those of the site, then one row per field of the source with the
 * field of that form its values land under, proposed by name or by label, or kept as it is. A field of the
 * form picked in one row leaves the menus of the others, as does the name of another field of the source,
 * so that no two fields land together; a choice field lists the values none of its options carries.
 */
export const AttachToForm = ({sourceFields, choice, siteForms, targetFields, language, optionsLanguage, t, onTargetFields, onChange}: AttachToFormProps) => {
    const client = useApolloClient();
    const [loadingForm, setLoadingForm] = useState<string | null>(null);
    const [readError, setReadError] = useState('');
    // a read that ends after another one started, or after the component went, is dropped
    const requestRef = useRef(0);
    const mountedRef = useRef(true);
    useEffect(() => () => {
        mountedRef.current = false;
    }, []);

    /** Reads the fields of a form and hands them to the dialog; null when the read is stale or failed. */
    const readFields = useCallback(async (uuid: string): Promise<TargetField[] | null> => {
        const request = ++requestRef.current;
        setReadError('');
        setLoadingForm(uuid);
        try {
            const {data} = await client.query({
                query: GET_FORM_FIELD_LABELS,
                variables: {formUuid: uuid, language, optionsLanguage, workspace: 'EDIT'},
                fetchPolicy: 'network-only'
            });
            if (!mountedRef.current || request !== requestRef.current) {
                return null;
            }
            const fields = parseTargetFields(data);
            onTargetFields(uuid, fields);
            return fields;
        } catch (error) {
            if (mountedRef.current && request === requestRef.current) {
                setReadError(error instanceof Error ? error.message : t('formResults.import.error.unexpected'));
            }
            return null;
        } finally {
            if (mountedRef.current && request === requestRef.current) {
                setLoadingForm(null);
            }
        }
    }, [client, language, optionsLanguage, onTargetFields, t]);

    // A choice remounted with its form but without its fields reads them again: the rows need them.
    useEffect(() => {
        if (choice.form && !targetFields[choice.form] && loadingForm !== choice.form) {
            readFields(choice.form);
        }
    }, [choice.form, targetFields, loadingForm, readFields]);

    const pickForm = async (uuid: string) => {
        const fields = targetFields[uuid] ?? await readFields(uuid);
        if (fields) {
            onChange({choice: 'attach', form: uuid, mapping: mappingOf(proposeMapping(sourceFields, fields, language))});
        }
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
    const proposed = useMemo(() => (fields ? proposeMapping(sourceFields, fields, language) : {}), [sourceFields, fields, language]);
    const conflicts = fields ? mappingConflicts(sourceFields, choice.mapping) : [];
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
                    {sourceFields.map(field => {
                        const target = choice.mapping[field.name];
                        const proposal = proposed[field.name];
                        // a field without a proposal, kept under its name, has no rule to show: both are undefined then
                        const rule = proposal?.rule && proposal.target === target ? proposal.rule : null;
                        const title = fieldTitle(field, language);
                        const unmatched = unmatchedValues(field, fields.find(candidate => candidate.name === target));
                        const options = [
                            {label: t('formResults.import.attach.keep'), value: KEEP_NAME},
                            ...availableTargets(field, sourceFields, choice.mapping, fields)
                                .map(candidate => ({label: candidate.label, description: candidate.name === candidate.label ? undefined : candidate.name, value: candidate.name}))
                        ];
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
                                        data={options}
                                        value={target ?? KEEP_NAME}
                                        isDisabled={fields.length === 0}
                                        onChange={(_event, item) => {
                                            if (item.value) {
                                                setTarget(field.name, item.value);
                                            }
                                        }}
                                    />
                                    {unmatched.length > 0 && (
                                        <Typography variant="caption" color="warning" data-sel-role="import-mapping-unmatched">
                                            {t('formResults.import.attach.unmatched', {
                                                count: unmatched.length + (field.moreValues ? 1 : 0),
                                                values: unmatched.join(', ') + (field.moreValues ? '…' : '')
                                            })}
                                        </Typography>
                                    )}
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

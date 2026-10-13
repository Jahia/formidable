import React, {useState} from 'react';
import {Button, Check, Dropdown, Typography, Warning} from '@jahia/moonstone';
import {formatFileSize} from '../FormResults.utils';
import {AttachToForm} from './AttachToForm';
import {
    type AttachChoice,
    formTitle,
    groupedNotes,
    type ImportChoice,
    type ImportChoiceKind,
    type ImportNote,
    type ImportReport,
    type ImportReportForm,
    kindOf,
    plannedImport,
    type ReportLine,
    type SiteForm,
    type TargetField
} from './import.utils';
import {noteText, type ProfileMapping, profileMappings} from './notes';

type Translate = (key: string, options?: Record<string, unknown>) => string;

interface ReportTableProps {
    report: ImportReport;
    language: string;
    t: Translate;
    /** The choices of the review, when the report is a dry run the administrator can still steer. */
    choices?: Record<string, ImportChoice>;
    /** The forms of the site an attachment can pick, null while they load. */
    siteForms?: SiteForm[] | null;
    /** The language the options of a choice field are read in: the default language of the site. */
    optionsLanguage?: string;
    /** The fields of the forms read so far, by identifier, and how to keep the ones read next. */
    targetFields?: Record<string, TargetField[]>;
    onTargetFields?: (formId: string, fields: TargetField[]) => void;
    onChoice?: (sourceName: string, choice: ImportChoice) => void;
}

const GRID = {display: 'grid', gridTemplateColumns: 'minmax(0, 5fr) minmax(0, 4fr) minmax(120px, auto)', gap: '16px', alignItems: 'start'};

/**
 * The report of a dry run or of an import (docs/architecture/forms-import.md, "The dialog"): one row per form of
 * the export, with its figures, what receives its results — a menu while the review can still change it — and
 * what is left to do by hand once its form is created, folded until asked for.
 */
export const ReportTable = ({report, language, t, choices = {}, onChoice, ...rest}: ReportTableProps) => {
    const steerable = Boolean(onChoice);
    const planned = plannedImport(report, choices);
    const created = report.forms.some(form => form.outcome === 'created');
    return (
        <div data-sel-role="import-report" style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
            <Typography data-sel-role="import-totals">
                {steerable ?
                    t('formResults.import.report.totals', {forms: planned.forms, submissions: planned.submissions}) :
                    t('formResults.import.done.totals', {
                        // as the review counted them: a form found from an earlier run counts when it took new submissions
                        forms: report.forms.filter(form => form.outcome !== 'skipped' && (form.outcome !== 'found' || form.submissions.imported > 0)).length,
                        submissions: report.totals.submissionsImported
                    })}
            </Typography>
            {report.nothingToImport && (
                <Typography data-sel-role="import-nothing">{t('formResults.import.report.nothing')}</Typography>
            )}
            {!steerable && created && (
                <Typography data-sel-role="import-next-step">{t('formResults.import.done.created')}</Typography>
            )}
            <div>
                <div aria-hidden="true" style={{...GRID, padding: '8px 12px', borderBottom: '1px solid var(--color-gray_light40)'}}>
                    <Typography variant="caption" weight="bold">{t('formResults.import.columns.form')}</Typography>
                    <Typography variant="caption" weight="bold">
                        {t(steerable ? 'formResults.import.columns.choice' : 'formResults.import.columns.result')}
                    </Typography>
                    <Typography variant="caption" weight="bold">{t('formResults.import.columns.todo')}</Typography>
                </div>
                {report.forms.map(form => (
                    <FormRow
                        key={form.sourceName}
                        form={form}
                        report={report}
                        language={language}
                        t={t}
                        choices={choices}
                        onChoice={onChoice}
                        {...rest}
                    />
                ))}
            </div>
        </div>
    );
};

interface FormRowProps extends ReportTableProps {
    form: ImportReportForm;
}

/** The choice of a form the administrator left as it is. */
const DEFAULT_CHOICE: ImportChoiceKind = 'resultsOnly';

const OUTCOME_OF_CHOICE = {resultsOnly: 'resultsOnly', create: 'created', attach: 'attached', skip: 'skipped'} as const;

/** What the import will do for a form: the choice of the review while it can still change, else the outcome the server reports. */
function outcomeOf(form: ImportReportForm, kind: ImportChoiceKind, steerable: boolean): ImportReportForm['outcome'] {
    if (!steerable) {
        return form.outcome;
    }
    if (form.outcome === 'found') {
        return kind === 'skip' ? 'skipped' : 'found';
    }
    return OUTCOME_OF_CHOICE[kind];
}

const FormRow = ({form, report, language, t, choices = {}, siteForms, optionsLanguage, targetFields, onTargetFields, onChoice}: FormRowProps) => {
    const [open, setOpen] = useState(false);
    const steerable = Boolean(onChoice);
    // a form imported before with nothing new has nothing to choose
    const choosable = steerable && !(form.outcome === 'found' && form.submissions.toImport === 0);
    const choice = choices[form.sourceName];
    const kind = kindOf(choice);
    const outcome = outcomeOf(form, kind, steerable);
    const attachment = typeof choice === 'object' ? choice : null;
    // what the form itself cannot carry over only matters when a form is created
    const notes = outcome === 'created' ? groupedNotes(form, language) : {action: [], info: []};
    const hasNotes = notes.action.length + notes.info.length > 0;
    const profile = profileMappings(notes.info);
    const pickedForm = attachment && siteForms?.find(siteForm => siteForm.uuid === attachment.form);
    const choose = (value: string) => {
        onChoice?.(form.sourceName, value === 'attach' ? {choice: 'attach', form: '', mapping: {}} : value as ImportChoice);
    };
    const options = form.outcome === 'found' ?
        [{label: t('formResults.import.choice.add'), value: DEFAULT_CHOICE}, {label: t('formResults.import.choice.skip'), value: 'skip'}] :
        ([DEFAULT_CHOICE, 'create', 'attach', 'skip'] as const).map(value => ({label: t(`formResults.import.choice.${value}`), value}));
    return (
        <div
            data-sel-role="import-report-form"
            data-sel-name={form.sourceName}
            data-sel-outcome={outcome ?? ''}
            style={{padding: '12px', borderBottom: '1px solid var(--color-gray_light40)', display: 'flex', flexDirection: 'column', gap: '12px'}}
        >
            <div style={GRID}>
                <div style={{minWidth: 0}}>
                    <Typography variant="body" weight="bold">{formTitle(form, language)}</Typography>
                    <Typography variant="caption" style={{color: 'var(--color-gray)'}} data-sel-role="import-report-figures">
                        {figuresOf(form, report.dryRun, t)}
                    </Typography>
                </div>
                <div style={{minWidth: 0, display: 'flex', flexDirection: 'column', gap: '4px'}}>
                    {choosable && (
                        <div data-sel-role="import-choice" data-sel-value={kind}>
                            <Dropdown
                                variant="outlined"
                                aria-label={t('formResults.import.columns.choice')}
                                data={options}
                                value={kind}
                                onChange={(_event, item) => {
                                    if (item.value && item.value !== kind) {
                                        choose(item.value);
                                    }
                                }}
                            />
                        </div>
                    )}
                    <Typography variant={steerable ? 'caption' : 'body'} data-sel-role="import-report-outcome" style={steerable ? {color: 'var(--color-gray)'} : undefined}>
                        {describe(form, outcome, steerable, pickedForm ? pickedForm.title : form.targetName, Boolean(attachment && !pickedForm), t)}
                    </Typography>
                </div>
                <div>
                    {hasNotes ? (
                        <Button
                            variant="ghost"
                            icon={notes.action.length > 0 ? <Warning color="yellow"/> : undefined}
                            label={notes.action.length > 0 ?
                                t('formResults.import.todo.count', {count: notes.action.length}) :
                                t('formResults.import.todo.details')}
                            aria-expanded={open}
                            data-sel-role="import-report-toggle"
                            onClick={() => setOpen(!open)}
                        />
                    ) : (
                        <Typography variant="body" style={{color: 'var(--color-gray)'}}>—</Typography>
                    )}
                </div>
            </div>
            {steerable && attachment && (
                <AttachToForm
                    sourceFields={form.fields}
                    choice={attachment}
                    siteForms={siteForms ?? null}
                    targetFields={targetFields ?? {}}
                    language={language}
                    optionsLanguage={optionsLanguage ?? language}
                    t={t}
                    onTargetFields={onTargetFields ?? (() => undefined)}
                    onChange={(next: AttachChoice) => onChoice?.(form.sourceName, next)}
                />
            )}
            {open && hasNotes && (
                <div data-sel-role="import-report-notes" style={{display: 'flex', flexDirection: 'column', gap: '8px', paddingLeft: '12px'}}>
                    {notes.action.length > 0 && <NotesList title={t('formResults.import.notes.action')} lines={notes.action} level="action" t={t}/>}
                    <ProfileMappings profile={profile} t={t}/>
                    {profile.others.length > 0 && <NotesList title={t('formResults.import.notes.info')} lines={profile.others} level="info" t={t}/>}
                </div>
            )}
        </div>
    );
};

/** The figures of a form: its submissions, its fields, its files, and what the values lost. */
function figuresOf(form: ImportReportForm, dryRun: boolean, t: Translate): string {
    return [
        dryRun ?
            t('formResults.import.report.submissions', {count: form.submissions.found}) :
            t('formResults.import.report.imported', {count: form.submissions.imported}),
        form.submissions.alreadyImported > 0 && t('formResults.import.report.already', {count: form.submissions.alreadyImported}),
        t('formResults.import.report.fields', {count: form.fields.length}),
        form.files.count > 0 && t('formResults.import.report.files', {count: form.files.count, size: formatFileSize(form.files.bytes)}),
        form.files.missing > 0 && t('formResults.import.report.filesMissing', {count: form.files.missing}),
        (form.values.dropped > 0 || form.values.notConverted > 0) &&
            t('formResults.import.report.values', {dropped: form.values.dropped, notConverted: form.values.notConverted})
    ].filter(Boolean).join(' · ');
}

/** What receives the results, in words: a plan while the review can change it, else what the import did. */
function describe(form: ImportReportForm, outcome: ImportReportForm['outcome'], steerable: boolean, name: string | null,
                  attachPending: boolean, t: Translate): string {
    const tense = steerable ? 'plan' : 'outcome';
    if (outcome === 'attached' && attachPending) {
        return t('formResults.import.plan.attachPending');
    }
    if (outcome === 'found' && steerable && form.submissions.toImport === 0) {
        return t('formResults.import.plan.foundNothing', {name});
    }
    return t(`formResults.import.${tense}.${outcome ?? DEFAULT_CHOICE}`, {name});
}

/**
 * The fields mapped to the visitor profile, one line each — label, property, a check when prefilled — and what follows
 * the prefill said once when every prefilled field shares it.
 */
const ProfileMappings = ({profile, t}: {profile: ReturnType<typeof profileMappings>; t: Translate}) => {
    const {mappings, commonAfter} = profile;
    if (mappings.length === 0) {
        return null;
    }
    // what follows the prefill, on the line of a field only when the fields do not all share it
    const after = (mapping: ProfileMapping): string => {
        if (!mapping.prefilled || !mapping.after || commonAfter !== null) {
            return '';
        }
        const label = t(`formResults.import.after.${mapping.after}`);
        return `· ${label}`;
    };
    return (
        <div data-sel-role="import-report-profile">
            <Typography variant="caption" weight="bold">{t('formResults.import.profile.title')}</Typography>
            {commonAfter && (
                <Typography variant="caption" style={{color: 'var(--color-gray)'}}>
                    {t('formResults.import.profile.after', {after: t(`formResults.import.after.${commonAfter}`)})}
                </Typography>
            )}
            <ul style={{margin: '4px 0 0', paddingLeft: '20px'}}>
                {mappings.map(mapping => (
                    <li key={mapping.field} data-sel-field={mapping.field}>
                        <Typography variant="body" style={{display: 'inline-flex', alignItems: 'center', gap: '6px'}}>
                            <strong>{mapping.field}</strong>
                            <span aria-hidden="true">→</span>
                            <em>{mapping.property}</em>
                            {mapping.prefilled && <Check size="small" color="green" aria-label={t('formResults.import.profile.prefilled')}/>}
                            {after(mapping)}
                        </Typography>
                    </li>
                ))}
            </ul>
        </div>
    );
};

const NotesList = ({title, lines, level, t}: {title: string; lines: ReportLine[]; level: ImportNote['level']; t: Translate}) => (
    <div data-sel-role={`import-report-notes-${level}`}>
        <Typography variant="caption" weight="bold">{title}</Typography>
        <ul style={{margin: '4px 0 0', paddingLeft: '20px'}}>
            {lines.map(line => (
                <li key={JSON.stringify([line.note.code, line.note.params, line.fields])} data-sel-code={line.note.code}>
                    <Typography variant="body">
                        {line.fields.length > 0 && <strong>{line.fields.join(', ')}: </strong>}
                        {noteText(line.note, t)}
                    </Typography>
                </li>
            ))}
        </ul>
    </div>
);

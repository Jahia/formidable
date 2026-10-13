import {describe, expect, it} from 'vitest';
import {
    attachProblem,
    availableTargets,
    choicesReady,
    fieldTitle,
    formTitle,
    groupedNotes,
    type ImportNote,
    type ImportReport,
    type ImportReportField,
    type ImportReportForm,
    kindOf,
    mappingConflicts,
    mappingOf,
    parseSiteDefaultLanguage,
    parseSiteForms,
    parseTargetFields,
    plannedImport,
    proposeMapping,
    refuseFile,
    requestChoices,
    type TargetField,
    typeKey,
    unmatchedValues
} from './import.utils';
import {noteText, profileMappings, wordsOf} from './notes';

const fileOf = (name: string, size: number, type = ''): File => {
    const file = new File([new Uint8Array(1)], name, {type});
    Object.defineProperty(file, 'size', {value: size});
    return file;
};

describe('refuseFile', () => {
    it('takes a zip under the bound', () => {
        expect(refuseFile(fileOf('formFactory.zip', 1024), 200)).toBeNull();
        expect(refuseFile(fileOf('EXPORT.ZIP', 1024), 200)).toBeNull();
        expect(refuseFile(fileOf('export', 1024, 'application/zip'), 200)).toBeNull();
    });

    it('refuses anything but a zip, and a zip past the bound', () => {
        expect(refuseFile(fileOf('repository.xml', 1024), 200)).toEqual('notZip');
        expect(refuseFile(fileOf('export.zip', 201 * 1024 * 1024), 200)).toEqual('tooLarge');
    });
});

describe('formTitle', () => {
    const form = {sourceName: 'contact-us', titles: {en: 'Contact Us', fr: 'Contact'}} as unknown as ImportReportForm;

    it('prefers the language of the user, then any language, then the source name', () => {
        expect(formTitle(form, 'fr')).toEqual('Contact');
        expect(formTitle(form, 'de')).toEqual('Contact Us');
        expect(formTitle({sourceName: 'newsletter', titles: {}} as ImportReportForm, 'en')).toEqual('newsletter');
    });
});

describe('groupedNotes', () => {
    const logic: ImportNote = {code: 'field.logic', level: 'action', params: {}};
    const rule: ImportNote = {code: 'field.rule', level: 'action', params: {rule: 'fcnt:regexValidation', type: 'fmdb:textarea'}};
    const mapped = (property: string): ImportNote => ({code: 'field.profileMapped', level: 'info', params: {property}});
    const redirect: ImportNote = {code: 'form.redirect', level: 'action', params: {target: '/home'}};

    it('gives one line per distinct note with the labels of the fields it concerns, what to do apart from what was carried over', () => {
        const form = {
            notes: [redirect],
            fields: [
                {name: 'first-name', type: 'fmdb:inputText', titles: {en: 'First name'}, notes: [logic, mapped('firstName')]},
                {name: 'last-name', type: 'fmdb:inputText', titles: {}, notes: [logic, mapped('lastName')]},
                {name: 'enquiry', type: 'fmdb:textarea', titles: {en: 'Enquiry'}, notes: [rule]}
            ]
        } as unknown as ImportReportForm;

        const lines = groupedNotes(form, 'en');

        expect(lines.action).toEqual([
            {note: redirect, fields: []},
            {note: logic, fields: ['First name', 'last-name']},
            {note: rule, fields: ['Enquiry']}
        ]);
        expect(lines.info.map(line => line.fields)).toEqual([['First name'], ['last-name']]);
        expect(groupedNotes({notes: [], fields: []} as unknown as ImportReportForm, 'en')).toEqual({action: [], info: []});
    });

    it('keys the label of a field type by its local name', () => {
        expect(typeKey('fmdb:inputText')).toEqual('inputText');
        expect(typeKey('fmdbext:consent')).toEqual('consent');
        expect(typeKey(undefined)).toEqual('');
    });
});

describe('plannedImport', () => {
    const form = (sourceName: string, outcome: ImportReportForm['outcome'], toImport: number) =>
        ({sourceName, outcome, submissions: {toImport}}) as unknown as ImportReportForm;
    const report = {forms: [form('contact', 'resultsOnly', 45), form('news', 'found', 3), form('old', 'found', 0)]} as unknown as ImportReport;

    it('counts what the choices import: a form left out counts for nothing, a found form only with new submissions', () => {
        expect(plannedImport(report, {})).toEqual({forms: 2, submissions: 48});
        expect(plannedImport(report, {contact: 'skip'})).toEqual({forms: 1, submissions: 3});
        expect(plannedImport(report, {contact: 'skip', news: 'skip'})).toEqual({forms: 0, submissions: 0});
        expect(choicesReady({contact: 'skip', news: 'skip'}, report)).toBe(false);
        expect(choicesReady({contact: 'skip'}, report)).toBe(true);
        expect(kindOf('skip')).toEqual('skip');
    });
});

describe('attaching to an existing form', () => {
    const field = (name: string, title?: string): ImportReportField => ({name, type: 'fmdb:inputText', titles: title ? {en: title} : {}, notes: []});
    const source = [field('firstname', 'Firstname'), field('lastname', 'Lastname'), field('enter-your-email-here', 'enter your email here')];
    const target = [{name: 'firstname', label: 'First name', options: null}, {name: 'surname', label: 'Lastname', options: null}, {name: 'email', label: 'Email', options: null}];

    it('proposes the field of the same name, else the field of the same label, else nothing', () => {
        expect(proposeMapping(source, target, 'en')).toEqual({
            firstname: {target: 'firstname', rule: 'sameName'},
            lastname: {target: 'surname', rule: 'sameLabel'}
        });
        expect(mappingOf(proposeMapping(source, target, 'en'))).toEqual({firstname: 'firstname', lastname: 'surname'});
    });

    it('proposes a field of the form once, and matches the label in the language of the user alone', () => {
        const twice = [field('name', 'Lastname'), field('lastname', 'Lastname')];
        expect(proposeMapping(twice, target, 'en')).toEqual({name: {target: 'surname', rule: 'sameLabel'}});
        expect(proposeMapping(source, target, 'fr')).toEqual({firstname: {target: 'firstname', rule: 'sameName'}});
        // a field named after another source field is taken by that one, whatever its label says
        expect(proposeMapping([field('surname', 'Email'), field('x', 'Lastname')], target, 'en'))
            .toEqual({surname: {target: 'surname', rule: 'sameName'}});
    });

    it('finds the names two fields would land under, a kept name included, and the name of another field of the source', () => {
        expect(mappingConflicts(source, {lastname: 'surname'})).toEqual([]);
        expect(mappingConflicts(source, {lastname: 'email', 'enter-your-email-here': 'email'})).toEqual(['email']);
        // a field sent to the name another field keeps collides with it
        expect(mappingConflicts(source, {lastname: 'firstname'})).toEqual(['firstname']);
        // a chain or a swap sends a field under a name that is sent elsewhere itself: refused too
        expect(mappingConflicts(source, {lastname: 'firstname', firstname: 'name'})).toEqual(['firstname']);
        expect(mappingConflicts(source, {lastname: 'firstname', firstname: 'lastname'}).sort((a, b) => a.localeCompare(b))).toEqual(['firstname', 'lastname']);
        // two rows sent to one field of the form: refused on the mapping alone
        expect(mappingConflicts(source, {lastname: 'surname', 'enter-your-email-here': 'surname'})).toEqual(['surname']);
        // a stopped run left the earlier submissions under name and the later ones under firstname: never held together, the rename goes on,
        // and the name row proposed under its own name is no rename
        const split = [{...field('firstname', 'Firstname'), alongside: ['lastname']}, {...field('name'), alongside: ['lastname']}, {...field('lastname', 'Lastname'), alongside: ['firstname', 'name']}];
        expect(mappingConflicts(split, {name: 'name', lastname: 'lastname', firstname: 'name'})).toEqual([]);
        expect(mappingConflicts(split, {lastname: 'name'})).toEqual(['name']);
    });

    it('offers a row the fields no other row picked, nor those named like another field of the source', () => {
        const targets = [...target, {name: 'lastname', label: 'Last name', options: null}];
        const names = (fields: TargetField[]) => fields.map(candidate => candidate.name);
        // firstname is the name of another field of the source, surname is picked by the lastname row
        expect(names(availableTargets(source[2], source, {lastname: 'surname'}, targets))).toEqual(['email']);
        // the row keeps its own pick, and may land under its own name; the firstname row may still land under firstname
        expect(names(availableTargets(source[1], source, {lastname: 'surname'}, targets))).toEqual(['surname', 'email', 'lastname']);
        expect(names(availableTargets(source[0], source, {}, targets))).toEqual(['firstname', 'surname', 'email']);
        // a field never held alongside this one does not take its name away: the renamed submissions of a stopped run
        const split = [{...field('firstname', 'Firstname'), alongside: ['lastname']}, {...field('name'), alongside: ['lastname']}, {...field('lastname', 'Lastname'), alongside: ['firstname', 'name']}];
        const withName = [...targets, {name: 'name', label: 'Name', options: null}];
        expect(names(availableTargets(split[0], split, {}, withName))).toEqual(['firstname', 'surname', 'email', 'name']);
        expect(names(availableTargets(split[2], split, {}, withName))).toEqual(['surname', 'email', 'lastname']);
        // the name row proposed under its own name does not take name away from the firstname row
        expect(names(availableTargets(split[0], split, {name: 'name', lastname: 'lastname'}, withName))).toEqual(['firstname', 'surname', 'email', 'name']);
    });

    it('lists the values of a field that no option of its target carries', () => {
        const colour = field('colour', 'Colour');
        colour.values = ['red', 'blue', 'teal'];
        expect(unmatchedValues(colour, {name: 'colour', label: 'Colour', options: ['red', 'blue']})).toEqual(['teal']);
        expect(unmatchedValues(colour, {name: 'colour', label: 'Colour', options: null})).toEqual([]);
        expect(unmatchedValues(colour, undefined)).toEqual([]);
        expect(unmatchedValues(field('name'), {name: 'name', label: 'Name', options: ['a']})).toEqual([]);
    });

    it('holds the import until the attachment names a form and lands no two fields together', () => {
        const report = {forms: [{sourceName: 'newsletter', outcome: 'resultsOnly', fields: source, submissions: {toImport: 3}}]} as unknown as ImportReport;
        expect(attachProblem({choice: 'attach', form: '', mapping: {}}, source)).toEqual('noForm');
        expect(attachProblem({choice: 'attach', form: 'f1', mapping: {lastname: 'firstname'}}, source)).toEqual('conflict');
        expect(attachProblem({choice: 'attach', form: 'f1', mapping: {lastname: 'surname'}}, source)).toBeNull();
        expect(choicesReady({}, report)).toBe(true);
        expect(choicesReady({newsletter: 'create'}, report)).toBe(true);
        expect(choicesReady({newsletter: {choice: 'attach', form: '', mapping: {}}}, report)).toBe(false);
        expect(choicesReady({newsletter: {choice: 'attach', form: 'f1', mapping: {}}}, report)).toBe(true);
    });

    it('sends the names that change alone', () => {
        expect(requestChoices({
            'contact-us': 'create',
            newsletter: {choice: 'attach', form: 'f1', mapping: {firstname: 'firstname', lastname: 'surname', x: ''}}
        })).toEqual({'contact-us': 'create', newsletter: {choice: 'attach', form: 'f1', mapping: {lastname: 'surname'}}});
    });

    it('reads the forms of the site by title, and the fields of a form without its fieldsets and buttons', () => {
        expect(parseSiteForms({jcr: {nodesByQuery: {nodes: [
            {uuid: 'b', name: 'survey', path: '/sites/s/contents/survey', displayName: 'Survey'},
            {uuid: 'a', name: 'contact', path: '/sites/s/contents/contact', displayName: 'Contact'},
            {uuid: 'c', name: 'contact-2', path: '/sites/s/contents/forms/contact', displayName: 'Contact'}
        ]}}})).toEqual([
            {uuid: 'a', name: 'contact', path: '/sites/s/contents/contact', title: 'Contact'},
            {uuid: 'c', name: 'contact-2', path: '/sites/s/contents/forms/contact', title: 'Contact'},
            {uuid: 'b', name: 'survey', path: '/sites/s/contents/survey', title: 'Survey'}
        ]);
        expect(parseTargetFields({jcr: {nodeById: {fields: {nodes: [{descendants: {nodes: [
            {name: 'identity', displayName: 'Identity', nonSubmittable: true},
            {name: 'firstname', displayName: 'First name', nonSubmittable: false},
            {name: 'submit', displayName: 'Send', nonSubmittable: true},
            {name: 'email', nonSubmittable: false, options: null},
            {name: 'colour', displayName: 'Colour', nonSubmittable: false, options: {values: ['{"value":"red","label":"Red"}', 'not json', '{"label":"no value"}']}}
        ]}}]}}}})).toEqual([
            {name: 'firstname', label: 'First name', options: null},
            {name: 'email', label: 'email', options: null},
            {name: 'colour', label: 'Colour', options: ['red']}
        ]);
        expect(parseTargetFields(undefined)).toEqual([]);
        // the default language of the site, where the options are read; null when the site does not say
        expect(parseSiteDefaultLanguage({jcr: {site: {defaultLanguage: {value: 'fr'}}}})).toEqual('fr');
        expect(parseSiteDefaultLanguage({jcr: {site: {defaultLanguage: null}}})).toBeNull();
        expect(parseSiteDefaultLanguage(undefined)).toBeNull();
    });

    it('kinds a choice, the results alone when none was made', () => {
        expect(kindOf(undefined)).toEqual('resultsOnly');
        expect(kindOf('create')).toEqual('create');
        expect(kindOf({choice: 'attach', form: 'f1', mapping: {}})).toEqual('attach');
        expect(fieldTitle(field('x', 'Title'), 'en')).toEqual('Title');
        expect(fieldTitle(field('x'), 'en')).toEqual('');
    });
});

describe('noteText', () => {
    const t = (key: string, options?: Record<string, unknown>): string => {
        const values = Object.entries(options ?? {}).map(([name, value]) => `${name}=${String(value)}`);
        return `${key}(${values.join(',')})`;
    };

    it('words every parameter that names a type, a Forms type or a setting, never the raw name', () => {
        const text = noteText({code: 'field.rule', level: 'action', params: {rule: 'fcnt:regexValidation', type: 'fmdb:textarea'}}, t);
        expect(text).toContain('rule=formResults.import.terms.rules.regexValidation(defaultValue=regex)');
        expect(text).toContain('type=formResults.import.types.textarea(defaultValue=textarea)');
        expect(noteText({code: 'field.setting', level: 'action', params: {setting: 'minLength', type: 'fmdb:inputHidden'}}, t))
            .toContain('setting=formResults.import.terms.settings.minLength(defaultValue=min length)');
    });

    it('draws words from an unknown name, without its namespace and kind', () => {
        expect(wordsOf('fcnt:geolocationPrefill')).toEqual('geolocation');
        expect(wordsOf('matrixCheckBoxes')).toEqual('matrix check boxes');
        expect(wordsOf('fcnt:passwordDefinition')).toEqual('password');
    });
});

describe('profileMappings', () => {
    const prefilled = (property: string, after: string) => ({code: 'field.profilePrefilled', level: 'info' as const, params: {property, after}});

    it('gathers the mappings of a form, written, unchecked or failed, one per field', () => {
        const captcha = {note: {code: 'form.captchaOn', level: 'info' as const, params: {}}, fields: []};
        const redirect = {note: {code: 'form.redirect', level: 'action' as const, params: {target: '/home'}}, fields: []};
        const info = [
            {note: prefilled('firstName', 'editable'), fields: ['First name']},
            {note: {code: 'field.profileMapped', level: 'info' as const, params: {property: 'company'}}, fields: ['Company']},
            {note: prefilled('email', 'editable'), fields: ['Email']},
            captcha
        ];
        const action = [
            {note: {code: 'field.prefillUnknownProperty', level: 'action' as const, params: {property: 'sfdc__lead'}}, fields: ['Enquiry']},
            {note: {code: 'field.profileUnchecked', level: 'action' as const, params: {property: 'email'}}, fields: ['Email']},
            redirect
        ];

        const profile = profileMappings(info, action);

        expect(profile.mappings).toEqual([
            {field: 'First name', property: 'firstName', status: 'mapped', prefilled: true, after: 'editable', reason: undefined},
            {field: 'Company', property: 'company', status: 'mapped', prefilled: false, after: undefined, reason: undefined},
            {field: 'Email', property: 'email', status: 'unchecked', prefilled: true, after: 'editable', reason: undefined},
            {field: 'Enquiry', property: 'sfdc__lead', status: 'failed', prefilled: false, after: undefined, reason: 'field.prefillUnknownProperty'}
        ]);
        // the failed and unchecked mappings leave the notes to review for the mapping section, which flags them
        expect(profile.toReview.map(line => line.note.code)).toEqual(['form.redirect']);
        expect(profile.others).toEqual([captcha]);
    });
});

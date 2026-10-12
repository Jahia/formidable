import {describe, expect, it} from 'vitest';
import {
    attachProblem,
    availableTargets,
    choicesReady,
    fieldTitle,
    formTitle,
    groupedFieldNotes,
    type ImportReport,
    type ImportReportField,
    type ImportReportForm,
    kindOf,
    mappingConflicts,
    mappingOf,
    parseSiteForms,
    parseTargetFields,
    proposeMapping,
    refuseFile,
    requestChoices,
    type TargetField,
    unmatchedValues
} from './import.utils';

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

describe('groupedFieldNotes', () => {
    it('gives one line per distinct note, with the fields it concerns', () => {
        const prefill = 'prefill not carried over: set it by hand';
        expect(groupedFieldNotes([
            {name: 'first-name', type: 'fmdb:inputText', notes: [prefill]},
            {name: 'last-name', type: 'fmdb:inputText', notes: [prefill, 'pattern not carried over']},
            {name: 'email', type: 'fmdb:inputEmail', notes: []}
        ])).toEqual([`${prefill} — first-name, last-name`, 'pattern not carried over — last-name']);
        expect(groupedFieldNotes([])).toEqual([]);
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
        // a chain or a swap lands on the name of another field of the source, even mapped away: refused too
        expect(mappingConflicts(source, {lastname: 'firstname', firstname: 'name'})).toEqual(['firstname']);
        expect(mappingConflicts(source, {lastname: 'firstname', firstname: 'lastname'}).sort((a, b) => a.localeCompare(b))).toEqual(['firstname', 'lastname']);
    });

    it('offers a row the fields no other row picked, nor those named like another field of the source', () => {
        const targets = [...target, {name: 'lastname', label: 'Last name', options: null}];
        const names = (fields: TargetField[]) => fields.map(candidate => candidate.name);
        // firstname is the name of another field of the source, surname is picked by the lastname row
        expect(names(availableTargets(source[2], source, {lastname: 'surname'}, targets))).toEqual(['email']);
        // the row keeps its own pick, and may land under its own name; the firstname row may still land under firstname
        expect(names(availableTargets(source[1], source, {lastname: 'surname'}, targets))).toEqual(['surname', 'email', 'lastname']);
        expect(names(availableTargets(source[0], source, {}, targets))).toEqual(['firstname', 'surname', 'email']);
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
        const report = {forms: [{sourceName: 'newsletter', fields: source}]} as unknown as ImportReport;
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
    });

    it('kinds a choice, the results alone when none was made', () => {
        expect(kindOf(undefined)).toEqual('resultsOnly');
        expect(kindOf('create')).toEqual('create');
        expect(kindOf({choice: 'attach', form: 'f1', mapping: {}})).toEqual('attach');
        expect(fieldTitle(field('x', 'Title'), 'en')).toEqual('Title');
        expect(fieldTitle(field('x'), 'en')).toEqual('');
    });
});

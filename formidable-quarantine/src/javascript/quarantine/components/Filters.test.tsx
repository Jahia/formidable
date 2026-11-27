// @vitest-environment jsdom
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it, vi} from 'vitest';
import {FormFilter} from './Filters';
import en from '../../../main/resources/javascript/locales/en.json';
import fr from '../../../main/resources/javascript/locales/fr.json';

// a t that shows the options it gets, so a test reads what the label is built from
vi.mock('react-i18next', () => ({
    useTranslation: () => ({t: (key: string, options?: Record<string, unknown>) => (options ? `${key} ${JSON.stringify(options)}` : key)})
}));

const FORMS = [{formId: 'f', formTitle: 'Contact', count: 1}, {formId: 'g', formTitle: 'Claims', count: 2}];

const chip = (selected: string | null) => renderToStaticMarkup(
    <FormFilter forms={FORMS} selected={selected} isOpen={false} onOpen={vi.fn()} onClose={vi.fn()} onSelect={vi.fn()}/>
);

describe('FormFilter', () => {
    it('names the chip by the filter alone while no form is chosen', () => {
        expect(chip(null)).toMatch(/data-sel-role="quarantine-form-filter"[^>]*>.*quarantine\.filter\.form<\/span>/s);
    });

    it('names the chip by the filter and the title of the chosen form, through the key of each language', () => {
        expect(chip('g')).toContain('quarantine.filter.formChip {&quot;name&quot;:&quot;quarantine.filter.form&quot;,&quot;value&quot;:&quot;Claims&quot;}');
        // a form that left the list keeps its identifier
        expect(chip('gone')).toContain('&quot;value&quot;:&quot;gone&quot;');
    });

    it('takes its punctuation from the language', () => {
        expect(en.quarantine.filter.formChip).toBe('{{name}}: {{value}}');
        expect(fr.quarantine.filter.formChip).toBe('{{name}} : {{value}}');
    });
});

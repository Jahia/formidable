import {describe, expect, it} from 'vitest';
import {
    buildAnalytics,
    buildTimeline,
    isPie,
    pageOfReferer,
    parseAnalyticsFields,
    parseAnalyticsSubmission,
    periodStart,
    toDateValue,
    type AnalyticsFields,
    type AnalyticsSubmission
} from './analytics.utils';

const fieldNode = (name: string, flags: Record<string, unknown> = {}) =>
    ({name, displayName: `${name} label`, isSubmittable: true, ...flags});

const formData = (nodes: object[]) => ({jcr: {nodeById: {fields: {nodes: [{descendants: {nodes}}]}}}}) as Parameters<typeof parseAnalyticsFields>[0];

const option = (value: string, label: string) => JSON.stringify({value, label, selected: false});

const submission = (values: Record<string, string[]>, overrides: Partial<AnalyticsSubmission> = {}): AnalyticsSubmission => ({
    created: '2026-11-10T10:00:00.000Z',
    locale: 'en',
    referer: 'https://example.org/en/sites/acme/home/contact.html',
    values: new Map(Object.entries(values)),
    fileFields: new Set(),
    ...overrides
});

const NOW = new Date(2026, 10, 16, 12, 0, 0);
const context = {siteKey: 'acme', contextPath: '', start: null, now: NOW};

describe('parseAnalyticsFields', () => {
    it('keeps the fields that submit a value or a file, in the form\'s order, with their kind', () => {
        const {fields, labels} = parseAnalyticsFields(formData([
            fieldNode('step', {isSubmittable: false}),
            fieldNode('channel', {isChoice: true, options: {values: [option('email', 'By email'), option('phone', 'By phone')]}}),
            fieldNode('consent', {isBoolean: true}),
            fieldNode('rating', {isNumber: true}),
            fieldNode('receipt', {isSubmittable: false, isFile: true}),
            fieldNode('submit', {nonSubmittable: true}),
            fieldNode('secret', {isSensitive: true})
        ]));

        expect(fields.map(field => [field.name, field.kind])).toEqual([
            ['channel', 'choice'], ['consent', 'boolean'], ['rating', 'number'], ['receipt', 'file']
        ]);
        expect(fields[0].options).toEqual([{value: 'email', label: 'By email'}, {value: 'phone', label: 'By phone'}]);
        // The label of an element that is not reported stays known, for a value stored under its name.
        expect(labels.get('step')).toEqual('step label');
    });

    it('reads no option from a value that is not the manual JSON format', () => {
        const {fields} = parseAnalyticsFields(formData([fieldNode('channel', {isChoice: true, options: {values: ['plain', '{"label":"No value"}']}})]));
        expect(fields[0].options).toEqual([]);
    });

    it('answers no field without a form', () => {
        expect(parseAnalyticsFields(undefined).fields).toEqual([]);
    });
});

describe('parseAnalyticsSubmission', () => {
    it('keeps the filled values of the form\'s own properties and the fields that hold a file', () => {
        const parsed = parseAnalyticsSubmission({
            created: {value: '2026-11-10T10:00:00.000Z'},
            locale: {value: 'fr'},
            data: {nodes: [{properties: [
                {name: 'jcr:primaryType', value: 'fmdb:submissionData'},
                {name: 'email', value: 'a@example.org'},
                {name: 'sessions', values: ['cabin', 'crew']},
                {name: 'comment', value: ''}
            ]}]},
            files: {nodes: [{children: {nodes: [{name: 'receipt'}]}}]}
        });

        expect(Array.from(parsed.values.entries())).toEqual([['email', ['a@example.org']], ['sessions', ['cabin', 'crew']]]);
        expect(Array.from(parsed.fileFields)).toEqual(['receipt']);
        expect(parsed.locale).toEqual('fr');
        expect(parsed.referer).toBeNull();
    });
});

describe('pageOfReferer', () => {
    it('counts one page whatever the language, the query, the site prefix or the context path', () => {
        const page = '/home/contact';
        expect(pageOfReferer('https://example.org/en/sites/acme/home/contact.html?utm=x#form', 'en', {siteKey: 'acme'})).toEqual(page);
        expect(pageOfReferer('https://example.org/sites/acme/home/contact.html', 'fr', {siteKey: 'acme'})).toEqual(page);
        expect(pageOfReferer('https://www.acme.example/fr/home/contact.html', 'fr', {siteKey: 'acme'})).toEqual(page);
        expect(pageOfReferer('https://example.org/dx/cms/render/live/en/sites/acme/home/contact.html', 'en', {siteKey: 'acme', contextPath: '/dx'})).toEqual(page);
    });

    it('keeps a first segment that only looks like a language', () => {
        expect(pageOfReferer('https://www.acme.example/it/support.html', 'en', {siteKey: 'acme'})).toEqual('/it/support');
    });

    it('answers the root for the home of a domain, and null without a Referer', () => {
        expect(pageOfReferer('https://www.acme.example/', 'en', {siteKey: 'acme'})).toEqual('/');
        expect(pageOfReferer(null, 'en')).toBeNull();
    });
});

describe('periodStart and toDateValue', () => {
    it('starts a period so that it holds its number of days, today included', () => {
        expect(toDateValue(periodStart('7d', NOW)!)).toEqual('2026-11-10');
        expect(toDateValue(periodStart('30d', NOW)!)).toEqual('2026-10-18');
        expect(periodStart('all', NOW)).toBeNull();
    });
});

describe('buildTimeline', () => {
    it('counts per day and keeps the days without a submission', () => {
        const timeline = buildTimeline(
            [new Date(2026, 10, 14, 9), new Date(2026, 10, 14, 23), new Date(2026, 10, 16, 8)],
            new Date(2026, 10, 13),
            NOW
        );
        expect(timeline.unit).toEqual('day');
        expect(timeline.buckets.map(bucket => bucket.count)).toEqual([0, 2, 0, 1]);
    });

    it('counts per month over a long span', () => {
        const timeline = buildTimeline([new Date(2026, 0, 31), new Date(2026, 10, 1)], new Date(2025, 10, 17), NOW);
        expect(timeline.unit).toEqual('month');
        expect(timeline.buckets).toHaveLength(13);
        expect(timeline.buckets[2].count).toEqual(1);
        expect(timeline.buckets[12].count).toEqual(1);
    });
});

describe('buildAnalytics', () => {
    const form: AnalyticsFields = {
        fields: [
            {name: 'email', label: 'Email', kind: 'other', options: []},
            {name: 'channel', label: 'Channel', kind: 'choice', options: [{value: 'email', label: 'By email'}, {value: 'phone', label: 'By phone'}, {value: 'post', label: 'By post'}]},
            {name: 'sessions', label: 'Sessions', kind: 'choice', options: [{value: 'cabin', label: 'Cabin'}, {value: 'crew', label: 'Crew'}]},
            {name: 'consent', label: 'Consent', kind: 'boolean', options: []},
            {name: 'rating', label: 'Rating', kind: 'number', options: []},
            {name: 'receipt', label: 'Receipt', kind: 'file', options: []}
        ],
        labels: new Map(),
        logic: []
    };
    const submissions = [
        submission({email: ['a@example.org'], channel: ['email'], sessions: ['cabin', 'crew'], consent: ['true'], rating: ['4']}),
        submission({email: ['b@example.org'], channel: ['email'], sessions: ['cabin'], consent: ['false'], rating: ['5']}, {
            referer: 'https://example.org/fr/sites/acme/home/contact.html?member=1', locale: 'fr', fileFields: new Set(['receipt'])
        }),
        submission({email: ['c@example.org'], channel: ['fax'], removed: ['x']}, {referer: 'https://example.org/en/sites/acme/home/partner.html'}),
        submission({channel: ['phone']}, {referer: null, locale: null})
    ];
    const analytics = buildAnalytics(submissions, form, context);
    const distribution = (name: string) => analytics.distributions.find(item => item.name === name)!;

    it('counts the submissions per page and per language, the unknown ones apart', () => {
        expect(analytics.total).toEqual(4);
        expect(analytics.pages).toEqual([
            {key: '/home/contact', count: 2, share: 0.5},
            {key: '/home/partner', count: 1, share: 0.25},
            {key: null, count: 1, share: 0.25}
        ]);
        expect(analytics.languages.map(entry => [entry.key, entry.count])).toEqual([['en', 2], ['fr', 1], [null, 1]]);
    });

    it('lists the fields the least filled first, a field only the submissions know included', () => {
        expect(analytics.completion.map(item => [item.name, item.empty])).toEqual([
            ['consent', 3], ['receipt', 3], ['removed', 3], ['sessions', 2], ['rating', 2], ['email', 1], ['channel', 0]
        ]);
        expect(analytics.completion[0].emptyShare).toEqual(0.75);
    });

    it('breaks a choice down on the submissions that answered, the unchosen options and the removed answers shown', () => {
        expect(distribution('channel')).toMatchObject({answered: 4, multiple: false});
        expect(distribution('channel').entries).toEqual([
            {value: 'email', label: 'By email', count: 2, share: 0.5},
            {value: 'phone', label: 'By phone', count: 1, share: 0.25},
            {value: 'post', label: 'By post', count: 0, share: 0},
            {value: 'fax', label: 'fax', count: 1, share: 0.25}
        ]);
        expect(isPie(distribution('channel'))).toBe(true);
    });

    it('marks a choice with several answers per submission, which is no pie', () => {
        expect(distribution('sessions')).toMatchObject({answered: 2, multiple: true});
        expect(distribution('sessions').entries.map(entry => entry.share)).toEqual([1, 0.5]);
        expect(isPie(distribution('sessions'))).toBe(false);
    });

    it('counts a yes/no on all the submissions and averages a number', () => {
        expect(distribution('consent').entries.map(entry => [entry.value, entry.count])).toEqual([['true', 1], ['false', 3]]);
        expect(distribution('rating').average).toEqual(4.5);
        expect(distribution('rating').entries.map(entry => entry.value)).toEqual(['4', '5']);
        expect(analytics.distributions.some(item => item.name === 'email')).toBe(false);
    });

    it('gives an amount its average and no breakdown', () => {
        const amounts = buildAnalytics([submission({rating: ['1200']}), submission({rating: ['300.5']})], form, context);
        expect(amounts.distributions.find(item => item.name === 'rating')).toMatchObject({entries: [], average: 750.25, answered: 2});
    });

    it('spans the timeline from the oldest submission and reads the newest', () => {
        expect(analytics.lastCreated).toEqual('2026-11-10T10:00:00.000Z');
        expect(analytics.timeline.buckets).toHaveLength(7);
        expect(analytics.dailyAverage).toBeCloseTo(4 / 7);
    });

    it('answers an empty report without a submission', () => {
        const empty = buildAnalytics([], form, {...context, start: periodStart('7d', NOW)});
        expect(empty.total).toEqual(0);
        expect(empty.lastCreated).toBeNull();
        expect(empty.timeline.buckets).toHaveLength(7);
        expect(empty.completion.every(item => item.emptyShare === 0)).toBe(true);
    });
});

describe('buildAnalytics with conditional display', () => {
    const rule = (entries: Record<string, unknown>) => JSON.stringify(entries);
    const element = (name: string, path: string, rules: string[] = []) => ({name, path: `/form/fields/${path}`, fieldKey: {value: `key-${name}`}, logics: {values: rules}});
    const form = parseAnalyticsFields({jcr: {nodeById: {fields: {nodes: [{
        descendants: {nodes: [fieldNode('claimType', {isChoice: true}), fieldNode('estimate'), fieldNode('phone'), fieldNode('member'), fieldNode('vip')]},
        logicElements: {nodes: [
            element('claimType', 'claimType'),
            element('damage', 'damage', [rule({sourceFieldName: 'renamed', sourceFieldKey: 'key-claimType', operator: 'in', values: ['damaged']})]),
            element('estimate', 'damage/estimate'),
            element('phone', 'phone', [rule({sourceFieldName: 'claimType', operator: 'notIn', values: ['lost']})]),
            element('member', 'member', [rule({sourceType: 'urlParam', param: 'member', operator: 'equals', value: '1'})]),
            element('vip', 'vip', [rule({sourceType: 'cookie', cookie: 'vip', operator: 'exists'})])
        ]}
    }]}}}} as Parameters<typeof parseAnalyticsFields>[0]);
    const analytics = buildAnalytics([
        submission({claimType: ['damaged'], estimate: ['1200']}, {referer: 'https://example.org/claim.html?member=1'}),
        submission({claimType: ['damaged']}, {referer: 'https://example.org/claim.html'}),
        submission({claimType: ['lost'], vip: ['yes']}, {referer: null})
    ], form, context);
    const field = (name: string) => analytics.completion.find(item => item.name === name)!;

    it('counts a field of a hidden fieldset only in the submissions that showed it', () => {
        // Two claims are "damaged": the fieldset showed twice, the estimate was left empty once.
        expect(field('estimate')).toMatchObject({shown: 2, empty: 1, emptyShare: 0.5, conditional: true});
        expect(field('claimType')).toMatchObject({shown: 3, empty: 0, conditional: false});
    });

    it('reads a rule on a field by its operator', () => {
        expect(field('phone')).toMatchObject({shown: 2, empty: 2, emptyShare: 1});
    });

    it('reads a rule on a URL parameter from the page of the submission, and leaves out a page it does not know', () => {
        expect(field('member')).toMatchObject({shown: 1, empty: 1, conditional: true});
    });

    it('leaves out the submissions a cookie rule cannot be judged on, but for the ones that filled the field', () => {
        expect(field('vip')).toMatchObject({shown: 1, filled: 1, empty: 0, emptyShare: 0});
    });
});

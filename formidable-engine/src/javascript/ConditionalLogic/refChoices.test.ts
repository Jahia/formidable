import {afterEach, describe, expect, it, vi} from 'vitest';
import {acquireRefChoices, fetchRefChoices, forgetRefChoices, refChoiceOptions, UNAVAILABLE} from './refChoices';
import {getLogicProvider, readProfileProperties} from './providers';

const profile = getLogicProvider('jexperience:profile')!;
const path = '/sites/s/contents/forms/f/fields/size';

const answer = (available: boolean, properties: {name: string; label: string}[] = []) =>
	({data: {formidableJExperienceProfileProperties: {available, properties}}});

describe('the jExperience profile property list', () => {
	afterEach(() => forgetRefChoices());

	it('is asked for the edited field and read as dropdown entries', async () => {
		const post = vi.fn().mockResolvedValue(answer(true, [{name: 'country', label: 'Country (country)'}, {name: 'nbOfVisits', label: 'Visits (nbOfVisits)'}]));

		const load = await fetchRefChoices(profile.refChoices!, path, post);

		expect(post).toHaveBeenCalledWith(profile.refChoices!.query, {path});
		expect(load).toEqual({available: true, choices: [
			{value: 'country', label: 'Country (country)'},
			{value: 'nbOfVisits', label: 'Visits (nbOfVisits)'}
		]});
	});

	it('is unavailable when jCustomer cannot be asked, when the module is missing, or when nothing answers', async () => {
		expect(await fetchRefChoices(profile.refChoices!, path, vi.fn().mockResolvedValue(answer(false)))).toEqual(UNAVAILABLE);
		// formidable-jexperience-engine not installed: the field fails validation
		const missing = vi.fn().mockResolvedValue({errors: [{message: "Validation error: Field 'formidableJExperienceProfileProperties' is undefined"}]});
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		expect(await fetchRefChoices(profile.refChoices!, path, missing)).toEqual(UNAVAILABLE);
		expect(readProfileProperties(null)).toEqual(UNAVAILABLE);
	});

	it('is loaded once for every rule row of the field, and asked again after a failure', async () => {
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);
		const load = vi.fn()
			.mockRejectedValueOnce(new Error('network'))
			.mockResolvedValue({available: true, choices: [{value: 'country', label: 'Country'}]});

		expect(await acquireRefChoices(profile, path, load)).toEqual(UNAVAILABLE);
		const first = acquireRefChoices(profile, path, load);
		const second = acquireRefChoices(profile, path, load);

		expect(await first).toEqual(await second);
		expect(load).toHaveBeenCalledTimes(2);
	});

	it('is never asked for a provider that lists nothing', async () => {
		const load = vi.fn();

		expect(await acquireRefChoices(getLogicProvider('cookie')!, path, load)).toEqual(UNAVAILABLE);
		expect(load).not.toHaveBeenCalled();
	});
});

describe('refChoiceOptions', () => {
	const choices = [{value: 'country', label: 'Country (country)'}, {value: 'address.city', label: 'City'}];

	it('offers only what the provider can resolve', () => {
		expect(refChoiceOptions(choices, '', profile.isValidRef)).toEqual([{value: 'country', label: 'Country (country)'}]);
	});

	it('keeps a stored reference the list lacks, so opening the rule never rewrites it', () => {
		expect(refChoiceOptions(choices, 'legacyScore', profile.isValidRef).map(choice => choice.value)).toEqual(['legacyScore', 'country']);
		expect(refChoiceOptions(choices, 'country', profile.isValidRef)).toHaveLength(1);
	});
});

// @vitest-environment jsdom
import {afterEach, describe, expect, it} from 'vitest';
import {applyConditionalLogicVisibility} from './conditionalLogic';

/** A rule shown on the target as long as its source is not empty, sourced on the field `password` (node `pw-1`). */
const RULE = JSON.stringify([{logicId: 'l1', sourceFieldName: 'password', sourceNodeId: 'pw-1', operator: 'isNotEmpty'}]);

/** A form holding a filled source field and the target its rule drives; `sourceAttributes` mark the source's wrapper. */
const formWith = (sourceAttributes: string, rule = RULE): HTMLFormElement => {
	document.body.innerHTML = `<form>
		<div data-fmdb-node-name="password" data-fmdb-node-id="pw-1" ${sourceAttributes}><input type="text" name="password" value="S3cret!pw"/></div>
		<div id="target" data-fmdb-node-name="welcome" data-fmdb-node-id="t-1" data-fmdb-logics='${rule}'><input name="welcome"/></div>
	</form>`;
	return document.querySelector('form')!;
};

const target = () => document.querySelector<HTMLElement>('#target')!;

describe('applyConditionalLogicVisibility on a rule sourced on a sensitive field', () => {
	afterEach(() => {
		document.body.replaceChildren();
	});

	it('shows the target of a rule satisfied by a field that is not sensitive', () => {
		applyConditionalLogicVisibility(formWith(''));

		expect(target().dataset.fmdbLogicHidden).toBe('false');
	});

	it('hides the target, as the server does with a broken source, however the rule finds the field', () => {
		// by the stored id
		applyConditionalLogicVisibility(formWith('data-fmdb-sensitive="true"'));
		expect(target().dataset.fmdbLogicHidden).toBe('true');
		expect(target().querySelector('input')!.disabled).toBe(true);

		// by the name, the server render having left the rule unbound
		const unbound = JSON.stringify([{logicId: 'l1', sourceFieldName: 'password', operator: 'isNotEmpty'}]);
		applyConditionalLogicVisibility(formWith('data-fmdb-sensitive="true"', unbound));
		expect(target().dataset.fmdbLogicHidden).toBe('true');
	});
});

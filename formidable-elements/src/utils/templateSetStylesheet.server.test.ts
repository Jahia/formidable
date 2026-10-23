import {describe, expect, it} from 'vitest';
import {type SiteLike, TEMPLATE_SET_STYLESHEET, templateSetStylesheetUrl} from './templateSetStylesheet.server';

/** A site whose template set is the module `id`, with the given files in its bundle. */
const siteWith = (id: string, files: string[]): SiteLike => ({
	getTemplatePackage: () => ({
		getId: () => id,
		getBundle: () => ({getEntry: (path: string) => (files.includes(path) ? {} : null)})
	})
});

describe('templateSetStylesheetUrl', () => {
	it('names the bundled stylesheet of a template set that ships one, under the context path', () => {
		expect(templateSetStylesheetUrl(siteWith('my-templateset', [TEMPLATE_SET_STYLESHEET]), '')).toBe('/modules/my-templateset/dist/assets/style.css');
		expect(templateSetStylesheetUrl(siteWith('my-templateset', [TEMPLATE_SET_STYLESHEET]), '/jahia')).toBe('/jahia/modules/my-templateset/dist/assets/style.css');
	});

	it('adds nothing for a template set without the file, such as a JSP one', () => {
		expect(templateSetStylesheetUrl(siteWith('jsp-templateset', ['css/site.css']), '')).toBeUndefined();
	});

	it('adds nothing without a site, without a template set, or without a bundle', () => {
		expect(templateSetStylesheetUrl(undefined, '')).toBeUndefined();
		expect(templateSetStylesheetUrl(null, '')).toBeUndefined();
		expect(templateSetStylesheetUrl({getTemplatePackage: () => null}, '')).toBeUndefined();
		expect(templateSetStylesheetUrl({getTemplatePackage: () => ({getId: () => 'x', getBundle: () => null})}, '')).toBeUndefined();
	});

	it('adds nothing when the host refuses the lookup', () => {
		const refusing: SiteLike = {
			getTemplatePackage: () => {
				throw new Error('host access refused');
			}
		};
		expect(templateSetStylesheetUrl(refusing, '')).toBeUndefined();
	});
});

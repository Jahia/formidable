import {addNode, publishAndWaitJobEnding} from '@jahia/cypress';
import {type FormSite, TEMPLATE_SETS} from '../support/fixtures';
import {contentPathOf} from '../support/constants';

/** One of the playground sites: a form site with the title the site switcher shows. */
export interface PlaygroundSite extends FormSite {
	title: string;
}

const SITE_CONFIG = {serverName: 'localhost', locale: 'en', languages: 'en,fr'};

/**
 * The two playground sites, the same forms on each, told apart by the site alone (no prefix on the
 * forms, no Custom CSS property on any of them: the look is the template set's, as docs/styling/
 * says it should be):
 *   - Plain  the JSP test template set, which ships no stylesheet: the modules' own look, the markup
 *            and the class hooks as the styling contract describes them
 *   - CSS    the TSX test template set, whose Layout ships the complete stylesheet written against that
 *            contract (src/design/registration-yellow-theme.css): a business look on top of the same
 *            markup, the case the authoring UI (Page Builder zones, boxes) must stay readable in
 * Each template set names its page template and its area its own way; the sites carry those names.
 */
export const PLAYGROUND_SITES: PlaygroundSite[] = [
	{
		key: 'FormidablePlaygroundPlain',
		title: 'Formidable Playground Plain',
		config: {...SITE_CONFIG, templateSet: TEMPLATE_SETS.jsp.templateSet},
		templateName: TEMPLATE_SETS.jsp.templateName,
		areaName: TEMPLATE_SETS.jsp.areaName
	},
	{
		key: 'FormidablePlaygroundCss',
		title: 'Formidable Playground CSS',
		config: {...SITE_CONFIG, templateSet: TEMPLATE_SETS.tsx.templateSet},
		templateName: TEMPLATE_SETS.tsx.templateName,
		areaName: TEMPLATE_SETS.tsx.areaName
	}
];

/** The Plain site: the one the results-only leftovers (deleted, unpublished, emptied forms) are made on. */
export const PLAIN_SITE: PlaygroundSite = PLAYGROUND_SITES[0];

/**
 * Where the playground puts its forms on a site: a folder of their own, so the contents root stays what
 * the specs expect and the set is found at a glance in jContent.
 */
export const playgroundFormsPathOf = (siteKey: string): string => `${contentPathOf(siteKey)}/forms/playground`;

/**
 * Creates the forms folder of a site and the playground folder in it, published while still empty so that
 * every form finds its parent in live. Creations are idempotent: a script run on a site that has them keeps them.
 */
export const ensurePlaygroundFolders = (site: PlaygroundSite): void => {
	const formsFolder = `${contentPathOf(site.key)}/forms`;
	addNode({parentPathOrId: contentPathOf(site.key), name: 'forms', primaryNodeType: 'jnt:contentFolder', properties: []});
	addNode({parentPathOrId: formsFolder, name: 'playground', primaryNodeType: 'jnt:contentFolder', properties: []});
	publishAndWaitJobEnding(formsFolder, ['en', 'fr']);
};

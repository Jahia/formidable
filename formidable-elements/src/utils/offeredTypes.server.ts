import {server} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";

// An engine service of the types a site offers under a list, reached over OSGi; offeredUnder answers a java.util.List.
interface OfferedTypesLike {
	offeredUnder(list: JCRNodeWrapper): {isEmpty(): boolean};
}

/**
 * Whether the site offers a type to add under this list: what the chooser behind the create button would list, as
 * the engine service named reads it from the modules enabled on the site. The engine ships no concrete type of a
 * field action or of a check, so a site where no module providing one is enabled has none, and the button would open
 * on nothing (#404). A question the engine cannot answer counts as yes: the button then shows, and the log says why.
 */
export const siteOffersAType = (serviceName: string, what: string, list: JCRNodeWrapper): boolean => {
	try {
		const service = server.osgi.getService(serviceName) as OfferedTypesLike | null;
		if (!service) {
			console.warn(`[Formidable] The engine service ${serviceName} is not available: the create button shows regardless`);
			return true;
		}
		return !service.offeredUnder(list).isEmpty();
	} catch (error) {
		console.error(`[Formidable] Could not list the ${what} types offered under ${list.getPath()}`, error);
		return true;
	}
};

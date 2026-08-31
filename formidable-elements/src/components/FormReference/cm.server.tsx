import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

/**
 * Inspection view for jContent: delegates to the referenced form's own cm view, whatever
 * view the reference is configured to use on the page — the drawer inspects the form's
 * content, it does not simulate the page.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:formReference",
		name: "cm",
		displayName: "jContent internal view"
	},
	({'j:node': node}) => {
		if (!node) {
			return null;
		}

		return <Render node={node} view="cm"/>;
	}
);

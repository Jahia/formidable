import gql from 'graphql-tag';

const GET_LIVE_OWNERSHIP = gql`
	query getLiveOwnership($path: String!) {
		jcr(workspace: LIVE) {
			nodeByPath(path: $path) {
				mixinTypes {
					name
				}
				liveProperties: property(name: "j:liveProperties") {
					values
				}
			}
		}
	}
`;

type LiveOwnershipResponse = {
	data?: {
		jcr?: {
			nodeByPath?: {
				mixinTypes?: Array<{name: string}>;
				liveProperties?: {values?: string[]} | null;
			} | null;
		};
	};
};

const expectNotLiveOwned = (path: string) => {
	cy.apollo({query: GET_LIVE_OWNERSHIP, variables: {path}}).then((response: LiveOwnershipResponse) => {
		const node = response.data?.jcr?.nodeByPath;
		// cy.apollo swallows GraphQL errors: a wrong path would come back as an absent
		// node, so every inspected path has to resolve or the guard is vacuous.
		expect(Boolean(node), `${path} exists in live`).to.equal(true);
		expect(node?.mixinTypes?.map(mixin => mixin.name), `live-owned marker on ${path}`)
			.not.to.include('jmix:liveProperties');
		expect(node?.liveProperties, `live-owned properties on ${path}`).to.equal(null);
	});
};

/**
 * Asserts that no property of the live node — nor of its translation subnodes — is
 * live-owned. Jahia records every property written directly in live on a published
 * node as user-generated content (jmix:liveProperties / j:liveProperties) and the
 * publication then skips it for good: a system rewrite that leaves this marker behind
 * freezes the node in live — its later publications never reach the site (#281).
 *
 * The marker lands on the node that carries the property: an i18n write marks the
 * j:translation_* subnode, not its parent. Those subnodes are hidden from the GraphQL
 * children and mapped back to their parent by queries, so they are read by path. The
 * caller names the languages the node is translated in (every fixture titles its
 * nodes in English at least): each path must resolve, a missing one fails the guard
 * rather than silently inspecting nothing.
 */
export const expectNoLiveOwnedProperty = (path: string, translatedLanguages: string[] = ['en']) => {
	expectNotLiveOwned(path);
	translatedLanguages.forEach(language => expectNotLiveOwned(`${path}/j:translation_${language}`));
};

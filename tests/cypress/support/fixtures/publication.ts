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

/**
 * Asserts that no property of the live node is live-owned. Jahia records every
 * property written directly in live on a published node as user-generated content
 * (jmix:liveProperties / j:liveProperties) and the publication then skips it for
 * good: a system rewrite that leaves this marker behind freezes the node in live —
 * its later publications never reach the site (#281).
 */
export const expectNoLiveOwnedProperty = (path: string) => {
	cy.apollo({query: GET_LIVE_OWNERSHIP, variables: {path}}).then((response: LiveOwnershipResponse) => {
		const node = response.data?.jcr?.nodeByPath;
		expect(Boolean(node), `${path} exists in live`).to.equal(true);
		expect(node?.mixinTypes?.map(mixin => mixin.name), `live-owned marker on ${path}`)
			.not.to.include('jmix:liveProperties');
		expect(node?.liveProperties, `live-owned properties on ${path}`).to.equal(null);
	});
};

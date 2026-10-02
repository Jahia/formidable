import {gql} from '@apollo/client';

/**
 * The identity every JCR node selection carries: uuid and workspace key the editor's Apollo cache
 * (its dataIdFromObject needs both), name and path are what the code reads. The source is exported
 * too, for a document posted outside that client (ConditionalLogic/sources.ts).
 */
export const JCR_NODE_IDENTITY_SOURCE = `
    fragment JcrNodeIdentity on GenericJCRNode {
        uuid
        workspace
        name
        path
    }
`;

export const JCR_NODE_IDENTITY = gql`${JCR_NODE_IDENTITY_SOURCE}`;

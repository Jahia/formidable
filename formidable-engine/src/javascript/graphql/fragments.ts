import {gql} from '@apollo/client';

export const JCR_NODE_IDENTITY = gql`
    fragment JcrNodeIdentity on GenericJCRNode {
        uuid
        workspace
        name
        path
    }
`;


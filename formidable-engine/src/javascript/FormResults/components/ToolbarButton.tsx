import React from 'react';
import {Button} from '@jahia/moonstone';
import type {DisplayActionsProps} from '@jahia/ui-extender';

/** The props ui-extender hands a rendered action: the registered action's fields on top of these. */
type RenderProps = React.ComponentProps<DisplayActionsProps['render']>;

/** What an action registered on the Results page toolbar without a component of its own declares. */
interface ToolbarButtonProps {
    buttonLabel?: string;
    buttonIcon?: React.ReactElement;
    isDisabled?: boolean;
    dataSelRole?: string;
}

/**
 * The renderer of a toolbar action that declares a label, an icon and an onClick rather than a component:
 * a ghost button like the page's own. An action that owns a dialog registers a component instead.
 */
export const ToolbarButton = (props: RenderProps) => {
    const {buttonLabel, buttonIcon, isDisabled, dataSelRole, onClick} = props as RenderProps & ToolbarButtonProps;
    return (
        <Button
            variant="ghost"
            icon={buttonIcon}
            label={buttonLabel}
            isDisabled={isDisabled}
            data-sel-role={dataSelRole}
            onClick={event => onClick?.(props, event)}
        />
    );
};

import React from 'react';
import {Check, Chip, Clock, Close, Warning} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {isState, isVerdict, KNOWN_REASONS, STATE_COLORS, VERDICT_COLORS} from '../quarantine.utils';

/** The state of a held submission: always its text, the colour only adds to it. */
export const StateChip = ({state}: {state: string}) => {
    const {t} = useTranslation('formidable-quarantine');
    if (!isState(state)) {
        return <span>{state}</span>;
    }

    return (
        <span data-sel-role="quarantine-state" data-state={state}>
            <Chip label={t(`quarantine.state.${state}`)} color={STATE_COLORS[state]}/>
        </span>
    );
};

const VERDICT_ICONS = {
    PASS: <Check aria-hidden="true"/>,
    FAIL: <Close aria-hidden="true"/>,
    INCONCLUSIVE: <Warning aria-hidden="true"/>,
    PENDING: <Clock aria-hidden="true"/>,
    FAILED: <Warning aria-hidden="true"/>
};

/**
 * The verdict of a check for a file: a text and an icon. FAILED, a check that could not answer, reads "Check error"
 * in the warning colour, so that it does not look like a FAIL.
 */
export const VerdictChip = ({verdict}: {verdict: string}) => {
    const {t} = useTranslation('formidable-quarantine');
    if (!isVerdict(verdict)) {
        return <span>{verdict}</span>;
    }

    return (
        <span data-sel-role="quarantine-verdict" data-verdict={verdict}>
            <Chip label={t(`quarantine.verdict.${verdict}`)} color={VERDICT_COLORS[verdict]} icon={VERDICT_ICONS[verdict]}/>
        </span>
    );
};

/** A reason of the run: a phrase when the screen knows the code, then the code itself. */
export const Reason = ({code}: {code: string | null | undefined}) => {
    const {t} = useTranslation('formidable-quarantine');
    if (!code) {
        return null;
    }

    return (
        <span>
            {KNOWN_REASONS.has(code) && <>{t(`quarantine.reason.${code}`)} </>}
            <code>{code}</code>
        </span>
    );
};

import React, {useCallback, useEffect, useRef, useState} from 'react';

export type Announce = (message: string, assertive?: boolean) => void;

/**
 * The two live regions of the screen, polite and assertive. They are rendered from the first render, empty, at the
 * root of the screen, which no state change unmounts. A message is cleared, then set on the next frame, so that the
 * same sentence twice is read twice.
 */
export function useAnnouncer(): {polite: string; assertive: string; announce: Announce} {
    const [polite, setPolite] = useState('');
    const [assertive, setAssertive] = useState('');
    // one pending frame per region: a message of one region never cancels a message of the other
    const framesRef = useRef<{polite: number | null; assertive: number | null}>({polite: null, assertive: null});

    useEffect(() => () => {
        Object.values(framesRef.current).forEach(frame => {
            if (frame !== null) {
                cancelAnimationFrame(frame);
            }
        });
    }, []);

    const announce = useCallback<Announce>((message, isAssertive = false) => {
        const region = isAssertive ? 'assertive' : 'polite';
        const set = isAssertive ? setAssertive : setPolite;
        set('');
        const pending = framesRef.current[region];
        if (pending !== null) {
            cancelAnimationFrame(pending);
        }

        framesRef.current[region] = requestAnimationFrame(() => {
            framesRef.current[region] = null;
            set(message);
        });
    }, []);

    return {polite, assertive, announce};
}

export const LiveRegions = ({polite, assertive}: {polite: string; assertive: string}) => (
    <>
        <div role="status" className="fmdbq-sr-only" data-sel-role="quarantine-status">{polite}</div>
        <div role="alert" className="fmdbq-sr-only" data-sel-role="quarantine-alert">{assertive}</div>
    </>
);

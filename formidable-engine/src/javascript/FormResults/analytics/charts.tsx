import React from 'react';
import {Typography} from '@jahia/moonstone';
import type {Timeline} from './analytics.utils';

/**
 * The marks of the analytics, drawn here because Moonstone has no chart. Every figure a mark carries is also
 * written beside it (a legend, a row), so no reading depends on telling two colours apart; the colours of the
 * pie are a fixed order checked for colour-blind separation, never cycled past its six slices.
 */
export const PIE_COLORS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300'];

const MARK_COLOR = 'var(--color-accent)';
const TRACK_COLOR = 'var(--color-gray_light40)';
const MUTED = 'var(--color-gray_dark)';

export const formatCount = (count: number): string => count.toLocaleString();

export const formatShare = (share: number): string =>
    share.toLocaleString(undefined, {style: 'percent', maximumFractionDigits: share > 0 && share < 0.1 ? 1 : 0});

export interface BarItem {
    key: string;
    label: string;
    count: number;
    /** 0 to 1: the length of the bar and the percentage written after the count. */
    share: number;
    /** A remark on what the figures are counted on, written under the name. */
    note?: string;
    color?: string;
}

/** One row per item, the bars on one baseline: a name, a bar, the count and its share. */
export const BarList = ({items, label}: {items: BarItem[]; label: string}) => (
    <ul aria-label={label} style={{listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '10px'}}>
        {items.map(item => (
            <li key={item.key} style={{display: 'flex', flexDirection: 'column', gap: '4px', minWidth: 0}}>
                <div style={{display: 'flex', justifyContent: 'space-between', gap: '12px', minWidth: 0}}>
                    <span style={{display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0}}>
                        {item.color && <span aria-hidden style={{flex: '0 0 auto', width: '10px', height: '10px', borderRadius: '2px', backgroundColor: item.color}}/>}
                        <span style={{minWidth: 0}}>
                            <Typography variant="body" style={{overflowWrap: 'anywhere'}}>{item.label}</Typography>
                            {item.note && <Typography variant="caption" style={{color: MUTED}}>{item.note}</Typography>}
                        </span>
                    </span>
                    <Typography variant="body" style={{flex: '0 0 auto', color: MUTED}}>
                        {`${formatCount(item.count)} · ${formatShare(item.share)}`}
                    </Typography>
                </div>
                <div aria-hidden style={{height: '8px', borderRadius: '4px', backgroundColor: TRACK_COLOR}}>
                    <div style={{
                        width: `${Math.min(100, item.share * 100)}%`,
                        minWidth: item.count > 0 ? '2px' : 0,
                        height: '100%',
                        borderRadius: '4px',
                        backgroundColor: item.color ?? MARK_COLOR
                    }}/>
                </div>
            </li>
        ))}
    </ul>
);

const PIE_SIZE = 132;
const PIE_RADIUS = 46;
const PIE_STROKE = 28;
const PIE_CIRCUMFERENCE = 2 * Math.PI * PIE_RADIUS;
/** The gap between two slices, in the colour of the card: what separates them, not a border. */
const PIE_GAP = 2;

/** A pie of the items with a count, in their order; the list beside it names every slice with its figures. */
export const PieChart = ({items, label}: {items: BarItem[]; label: string}) => {
    const slices = items.filter(item => item.count > 0);
    const total = slices.reduce((sum, item) => sum + item.count, 0);
    let offset = 0;
    return (
        <svg aria-label={label} width={PIE_SIZE} height={PIE_SIZE} viewBox={`0 0 ${PIE_SIZE} ${PIE_SIZE}`} style={{flex: '0 0 auto'}}>
            <title>{label}</title>
            <g transform={`rotate(-90 ${PIE_SIZE / 2} ${PIE_SIZE / 2})`}>
                {slices.map(item => {
                    const length = (item.count / total) * PIE_CIRCUMFERENCE;
                    const gap = slices.length > 1 ? PIE_GAP : 0;
                    const circle = (
                        <circle
                            key={item.key}
                            cx={PIE_SIZE / 2}
                            cy={PIE_SIZE / 2}
                            r={PIE_RADIUS}
                            fill="none"
                            stroke={item.color}
                            strokeWidth={PIE_STROKE}
                            strokeDasharray={`${Math.max(0, length - gap)} ${PIE_CIRCUMFERENCE}`}
                            strokeDashoffset={-offset}
                        >
                            <title>{`${item.label}: ${formatCount(item.count)} (${formatShare(item.share)})`}</title>
                        </circle>
                    );
                    offset += length;
                    return circle;
                })}
            </g>
        </svg>
    );
};

const bucketLabel = (timeline: Timeline, index: number): string => {
    const date = timeline.buckets[index].start;
    return timeline.unit === 'day'
        ? date.toLocaleDateString(undefined, {day: 'numeric', month: 'short'})
        : date.toLocaleDateString(undefined, {month: 'short', year: 'numeric'});
};

/** One column per day or month, on one baseline; the first, middle and last dates name the axis. */
export const TimelineChart = ({timeline, label}: {timeline: Timeline; label: string}) => {
    const max = Math.max(1, ...timeline.buckets.map(bucket => bucket.count));
    const last = timeline.buckets.length - 1;
    const ticks = Array.from(new Set([0, Math.floor(last / 2), last]));
    return (
        <figure aria-label={label} style={{margin: 0}}>
            <div style={{display: 'flex', alignItems: 'flex-end', gap: '2px', height: '120px', borderBottom: `1px solid ${TRACK_COLOR}`}}>
                {timeline.buckets.map((bucket, index) => (
                    <div
                        key={bucket.start.getTime()}
                        title={`${bucketLabel(timeline, index)}: ${formatCount(bucket.count)}`}
                        style={{flex: '1 1 0', minWidth: 0, height: '100%', display: 'flex', alignItems: 'flex-end', justifyContent: 'center'}}
                    >
                        <div style={{
                            width: '100%',
                            maxWidth: '24px',
                            height: `${(bucket.count / max) * 100}%`,
                            minHeight: bucket.count > 0 ? '2px' : 0,
                            borderRadius: '4px 4px 0 0',
                            backgroundColor: MARK_COLOR
                        }}/>
                    </div>
                ))}
            </div>
            <div style={{display: 'flex', justifyContent: 'space-between', paddingTop: '4px'}}>
                {ticks.map(index => (
                    <Typography key={index} variant="caption" style={{color: MUTED}}>{bucketLabel(timeline, index)}</Typography>
                ))}
            </div>
        </figure>
    );
};

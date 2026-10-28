/**
 * React, mocked as a small renderer for the tests of a hook that moves through a form: `render()` walks the
 * hook's calls in order (state and refs kept per slot, an effect run once its dependencies changed, after the
 * cleanup of its previous run), and a setter marks the tree dirty, which `flush()` settles by rendering again.
 * A hook waiting on an asynchronous validation needs that much: what moves meanwhile goes through the setters
 * and a re-render, which is where the hook synchronises the refs a move reads.
 *
 * A test mocks React with it, the factory importing this module so that the test and the mock share one state:
 *
 *     vi.mock('react', async () => (await import('~/utils/testSupport/reactRenderer')).reactModule);
 */
const react = {
	states: [] as unknown[],
	refs: [] as Array<{current: unknown}>,
	effects: [] as Array<{deps: unknown[] | undefined; cleanup: void | (() => void)}>,
	memos: [] as Array<{deps: unknown[] | undefined; value: unknown}>,
	cursor: {state: 0, ref: 0, effect: 0, memo: 0},
	queued: [] as Array<() => void>,
	dirty: false,
	/** Every value the hook's first state was set to, in order: the step or the screen it moved to. */
	moves: [] as unknown[],
};

const changed = (deps: unknown[] | undefined, previous: unknown[] | undefined): boolean => {
	if (deps === undefined || previous === undefined) return true;
	return deps.length !== previous.length || deps.some((dep, i) => !Object.is(dep, previous[i]));
};

// Plain names: these stand in for React's hooks and call none; reactModule below gives them React's names.
const state = <T,>(initial: T | (() => T)) => {
	const slot = react.cursor.state++;
	if (!(slot in react.states)) react.states[slot] = typeof initial === 'function' ? (initial as () => T)() : initial;
	const set = (next: T | ((previous: T) => T)) => {
		const previous = react.states[slot] as T;
		const value = typeof next === 'function' ? (next as (previous: T) => T)(previous) : next;
		if (Object.is(value, previous)) return;
		react.states[slot] = value;
		react.dirty = true;
		if (slot === 0) react.moves.push(value);
	};
	return [react.states[slot] as T, set] as const;
};

const ref = <T,>(initial: T) => {
	const slot = react.cursor.ref++;
	react.refs[slot] ??= {current: initial};
	return react.refs[slot] as {current: T};
};

const effect = (run: () => void | (() => void), deps?: unknown[]) => {
	const slot = react.cursor.effect++;
	const previous = react.effects[slot];
	if (previous && !changed(deps, previous.deps)) return;
	react.queued.push(() => {
		previous?.cleanup?.();
		react.effects[slot] = {deps, cleanup: run()};
	});
};

const callback = <T,>(fn: T, deps?: unknown[]) => {
	const slot = react.cursor.memo++;
	const previous = react.memos[slot];
	if (previous && !changed(deps, previous.deps)) return previous.value as T;
	react.memos[slot] = {deps, value: fn};
	return fn;
};

export const reactModule = {useState: state, useRef: ref, useEffect: effect, useCallback: callback};

/**
 * Mounts a hook on a fresh renderer. `latest()` is its last render, `flush()` renders until no setter fired,
 * `moves` every value its first state was set to. The arguments the hook is called with must keep their
 * identity between renders, as React's refs and state do, or every effect depending on them runs again.
 */
export function renderHook<T>(hook: () => T) {
	Object.assign(react, {states: [], refs: [], effects: [], memos: [], queued: [], dirty: false, moves: []});
	let latest!: T;
	const render = () => {
		react.cursor = {state: 0, ref: 0, effect: 0, memo: 0};
		react.dirty = false;
		latest = hook();
		react.queued.splice(0).forEach(run => run());
	};
	const flush = () => {
		for (let round = 0; round < 10; round++) {
			render();
			if (!react.dirty) return;
		}
		throw new Error('the hook keeps setting state');
	};
	flush();
	return {latest: () => latest, flush, moves: react.moves};
}

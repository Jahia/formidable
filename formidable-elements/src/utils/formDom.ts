/**
 * The reads of a form that no contributed field can take over.
 *
 * `HTMLFormElement` is `[LegacyOverrideBuiltIns]`: a control whose name or id matches a property is
 * exposed as an **own** property of the form, and an own property shadows the whole prototype chain
 * — `Element.prototype` included, so `form.getAttribute` can itself be an `<input>`. A field's name
 * is the contributor's system name, so `id`, `action` and `reset` are names an author can type, and
 * nothing stops one typing `getAttribute`. Every read here therefore goes through the prototype
 * rather than through the form object, and every form access outside this module is a bug.
 */

/** An attribute of the form, never the control that shadows the property of the same name. */
export const formAttribute = (form: HTMLFormElement | null | undefined, name: string): string | null =>
	form ? Element.prototype.getAttribute.call(form, name) : null;

/** Fires an event on the form; a listener that throws is reported to the window, not to the caller. */
export const dispatchOnForm = (form: HTMLFormElement, event: Event): boolean =>
	EventTarget.prototype.dispatchEvent.call(form, event);

/** Empties the form's controls, whatever a control named `reset` shadows. */
export const resetForm = (form: HTMLFormElement): void => {
	HTMLFormElement.prototype.reset.call(form);
};

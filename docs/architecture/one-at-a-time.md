# One-at-a-time

A form whose **Form experience** is `One-at-a-time` shows one field per screen. A fieldset is one
screen, with all its fields. In a form with steps, a step shows its screens one after the other, and
the next step starts after the last screen of the step. A `Classic` form does not change.

## The setting

`formExperience` on `fmdbmix:multiStep` is a choice list: `classic`, the default, and `oneAtATime`.
A form created before the property has no value, and the island reads that as `classic`. The form
moves between screens with the Previous and Next buttons of a multi-step form, and their labels
(`previousBtnLabel`, `nextBtnLabel` on `fmdbmix:buttons`). They keep the size of the step buttons.

In the editor, the setting sits in Buttons, in the Step navigation fieldset, next to the labels of the
Previous and Next buttons it moves with. It applies to a form with steps and to a form without.

The Page Builder shows the form flat, as it does for steps.

## What a screen is

A screen is made of the **direct children** of a step, or of the field list in a form without
steps. Those are the element wrappers that `LogicAwareRender` renders (`[data-fmdb-node-id]`),
whose closest parent wrapper is the step's own wrapper, or none.

- A fieldset is one screen. A fieldset cannot hold another fieldset, so a screen is never nested.
- Content (`fmdbmix:formContent`, such as rich text) is shown with the element that follows it. The
  content that ends a step is shown with the element before it. The wrapper of content carries
  `data-fmdb-content="true"`, because only the server can read the mixin.
- A wrapper that shows nothing is not a screen: a wrapper hidden by the logic
  (`data-fmdb-logic-hidden="true"`), a wrapper hidden by a prefill (`data-fmdb-prefilled="hidden"`),
  a hidden field, and a fieldset whose fields all show nothing. A hidden field is read from the node
  type that its wrapper carries (`data-fmdb-node-type="fmdb:inputHidden"`). A field of any other type
  is a screen, whatever its controls are made of: a custom widget that posts through a hidden input
  is one.
- A step without a screen is skipped, wherever the form arrives at it: Next, Previous, the start of the
  form and a reset.

`utils/screens.ts` reads the screens from the DOM. `hooks/useScreens.ts` moves through them, on top of
`useMultiStep`, which keeps the steps.

## How a screen is hidden

The island marks the wrappers of the current step with `data-fmdb-screen`, and the wrappers of the
screen on display with `data-fmdb-screen-active`. `design/oneAtATime.css` hides the others.

The hook never writes `style.display` on a wrapper. The conditional logic writes that property
(`setWrapperVisibility`) on the same wrappers, and two writers of one property undo each other.
A field that the logic shows again stays hidden until its screen is on display.

Until the island has marked the screens, which it states with `data-fmdb-screens-ready` on the form,
the stylesheet hides every element. Without that rule, the visitor sees the whole form before it
folds into its first screen. The rule fails closed: when the island never runs, after a script error
or a blocked bundle, the form shows its buttons and no field. This is a deliberate choice, made to
keep the form from showing all its fields before it folds into its first screen.

## Moving

- **Next** and the **Enter** key validate the screen, then show the next one. The validation
  is the validation of a step, on the element of the screen: its constraints (`validateInputs`), then
  its field actions (`settleFieldActions`). A refusal keeps the visitor on the screen.
- **Previous** shows the previous screen. It is not shown on the first screen. At the first screen of a step, it shows the **last** screen
  of the previous step.
- After a move, the focus goes to the first control of the new screen.
- On the last screen, Next becomes Submit, and the captcha is shown. The submission validates the whole
  form, as in a Classic form. Off the last screen the captcha widget is removed, and an answer the
  visitor gave it counts no more: Submit waits for the new widget.
- At submission, a field action's refusal, or the first control that fails its constraints, brings
  back the step, then the screen, that holds the control. Logic can show again a field on a screen
  the visitor has passed, and the submission reaches it this way.
- A field action's refusal does not disable Next: the step holds screens the visitor cannot see, and
  Next settles the field actions of its own screen.
- After an accepted submission, and after a reset, the form starts again at its first screen. After a
  reset, the focus goes to its first control.
- In a form without steps, the Reset button, when the author shows it, sits next to Submit on the last
  screen. A form with steps shows no Reset button, in One-at-a-time as in Classic.

The screens are read again on every `input` and `change` event and on `FORM_LOGIC_INVALIDATE_EVENT`,
after the conditional logic has run. An answer can therefore open or close the screens that follow
it. A visibility change with no event is read too: a `MutationObserver` watches the
`data-fmdb-logic-hidden` and `data-fmdb-prefilled` attributes, which a provider's watcher and a prefill
write. The hook writes neither, so the observer does not loop. When the screen on display becomes
hidden, the visitor moves to the next screen that is shown. A read that changes no screen, as most
keystrokes do, writes nothing to the DOM.

### Enter

Enter does what Next does, with four exceptions:

- A field that handles Enter itself, and calls `preventDefault()`, keeps it. Its listeners run before
  the form's: a listener on the field, and a React handler in an island of its own, whose root is below
  the form.
- In a textarea, Enter writes a new line, and Ctrl+Enter or Cmd+Enter moves on.
- On a button or a link, Enter keeps its native action.
- On the last screen, Enter clicks Submit when Submit is enabled.

Enter never submits the form from another screen: the browser's implicit submission would validate
every screen at once.

The text inputs of the screen get `enterkeyhint="next"`, or `"send"` on the last screen, so the
action key of a phone keyboard states what it does.

## A pick moves on: the contract of a field type

A field type whose answer is one pick (a radio group, a card, a rating, a scale) puts
`data-fmdb-advance-on-change="true"` on the element that holds its controls. On a screen that shows
that field alone:

- a pick moves to the next screen after 300 ms, so the visitor sees the pick. A move of the visitor's own
  in that pause, Next, Enter or Previous, cancels it, and it does not run from a screen the visitor or the
  logic left meanwhile;
- Next stays, for a visitor who comes back to the screen and keeps the answer.

A screen that shows a fieldset does not move on by itself, whatever its fields declare. On the last
screen, a pick moves on only when its answer opened a screen after it. The hook decides at the end of
the pause, because the logic runs on the `change` event, after the `click`. A click that unchecks a
checkbox withdraws the answer, and the visitor stays.

**The arrow keys do not move on.** In a radio group, the arrow keys move the selection, and each
press fires `click` and `change`. A click counts as a pick only right after a pointer press or the
Space key, and only on an `<input>` inside the element: a widget made of other elements, such as
buttons, does not move on by itself. Enter moves on, as on every screen. A click on the answer that is already selected also
moves on, so a visitor who went back confirms the same answer with one click.

**The visitor is told before.** WCAG 3.2.2 (On Input) asks that a change of context caused by a choice
be announced before it happens. The island renders one hidden sentence, "Choosing an answer moves to
the next question". Its id is in the `aria-describedby` of the element of the screen on display
when a pick there moves on, and in no other: an element in a fieldset, or on the last screen, does not
point at it.

A pick moves on through the same path as Next: the logic has run, the field actions are settled, and a
refusal keeps the visitor on the screen.

A text field never moves on by itself: it moves on with Next or Enter.

A custom field type uses the same attribute. See
[How to extend views](../extension/how-to-extend-views-and-elements-from-third-party-module.md#one-at-a-time),
and `fmdbsample:quickConfirm` in the tsx samples module, a checkbox that moves on once checked.

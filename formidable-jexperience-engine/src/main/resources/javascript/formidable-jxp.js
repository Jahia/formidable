/*
 * Formidable, jExperience integration: the client half.
 *
 * Two jobs, both driven by the JSON block the render filter writes before each form:
 *
 * 1. Sends the `form` event of an accepted submission through jExperience's tracker, after the
 *    server answered 200 and with the values the server accepted, never the DOM fields. The form
 *    island announces an accepted submission with a bubbling `formidable:submitted` DOM event; the
 *    server put the accepted values in the `jexperience` block of its answer.
 * 2. Prefills the mapped fields from the visitor's profile. Before the tracker starts, the union of
 *    every block's `prefill` properties is pushed once into `digitalDataOverrides`, so that the one
 *    context request the tracker makes anyway returns them; once the context is loaded AND the form
 *    island has taken the form over (`formidable:ready`), the values are written into the fields.
 *
 * Design and gates: docs/architecture/jexperience-integration.md ("Rendering and prefill",
 * "Submitting", "The send condition").
 *
 * One instance per page whatever the number of forms: every form declares the same static asset and
 * core keeps one of them in the head. The guard below is the belt to that braces — a page built
 * another way, or a second copy loaded by hand, still gets one instance.
 */
(function () {
  "use strict";

  if (window.formidableJxp) {
    return;
  }
  const api = {};
  window.formidableJxp = api;

  const BLOCK_SELECTOR = 'script[type="application/json"][data-formidable-jxp]';
  const READY_EVENT = "formidable:ready";
  const SUBMITTED_EVENT = "formidable:submitted";

  /** The configuration block the render filter wrote for the form, or null. */
  api.configOf = (formId) => {
    const block = document.querySelector(BLOCK_SELECTOR + '[data-formidable-jxp="' + formId + '"]');
    return block ? parse(block, formId) : null;
  };

  /** Every configuration block of the page, parsed; a broken one is reported and skipped. */
  api.configs = () =>
    Array.from(document.querySelectorAll(BLOCK_SELECTOR))
      .map((block) => parse(block, block.getAttribute("data-formidable-jxp")))
      .filter(Boolean);

  function parse(block, formId) {
    try {
      return JSON.parse(block.textContent);
    } catch (e) {
      // the render filter wrote the block, so a broken one is a bug worth seeing
      console.warn(
        "[Formidable] the jExperience configuration of form " + formId + " is not valid JSON",
        e,
      );
      return null;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The gates: every reason to talk to the tracker or not, in one place — the one part of
  // Formidable that depends on the tracker's API.

  /**
   * `wem` is jExperience's tracker; a consent manager that blocked it leaves none. `wemLoaded` is set
   * once the tracker ran its callbacks — also in its fallback mode, where no context was loaded, so
   * the loaded context must carry a profile. `activateWem` false is the visitor's own "disable
   * tracking". Whoever decides whether the tracker starts at all — a consent tool, jExperience's
   * bot filter — decides for this script too: no tracker, nothing sent, nothing prefilled.
   */
  api.trackerReady = () => {
    const wem = window.wem;
    const init = (window.digitalData && window.digitalData.wemInitConfig) || {};
    const context =
      wem && typeof wem.getLoadedContext === "function" ? wem.getLoadedContext() : null;
    return (
      Boolean(wem) &&
      window.wemLoaded === true &&
      Boolean(context && context.profileId) &&
      init.activateWem !== false
    );
  };

  /**
   * Sending needs, on top, that the form is tracked, and `getFormNamesToWatch()` is the whole answer:
   * the tracker fills it from the `formEventCondition`s of the loaded context, and a form the author
   * mapped has one — the rule this integration writes at publication. Goals and segments are rules
   * too, so one list covers both and the page has nothing to declare about it.
   * `disableTrackedConditionsListeners` is the integrator's page-level "no automatic form tracking",
   * honoured for sending and never set here; prefill reads, it does not track, so it ignores it.
   */
  api.shouldCollect = (formId) => {
    const wem = window.wem;
    const init = (window.digitalData && window.digitalData.wemInitConfig) || {};
    return (
      api.trackerReady() &&
      !init.disableTrackedConditionsListeners &&
      typeof wem.getFormNamesToWatch === "function" &&
      wem.getFormNamesToWatch().indexOf(formId) > -1
    );
  };

  // ---------------------------------------------------------------------------------------------
  // Sending

  /**
   * The `form` event: the tracker's own builder, the form named for dashboards, the accepted values
   * as fields.
   */
  api.buildEvent = (config, fields) => {
    const event = window.wem.buildFormEvent(config.formId);
    event.target.properties = { name: config.name, path: config.path };
    event.flattenedProperties = { fields: fields || {} };
    return event;
  };

  document.addEventListener(SUBMITTED_EVENT, (e) => {
    const detail = e.detail || {};
    const block = (detail.response || {}).jexperience;
    if (!block || block.formId !== detail.formId) {
      return; // the server did not describe this submission for jExperience
    }
    const config = api.configOf(detail.formId);
    if (!config || !api.shouldCollect(detail.formId)) {
      return;
    }
    // No success callback: nothing of the page depends on the event having landed.
    window.wem.collectEvent(api.buildEvent(config, block.fields), undefined, (xhr) => {
      console.warn(
        "[Formidable] jExperience did not collect the form event: " +
          (xhr && xhr.status) +
          " " +
          (xhr && xhr.statusText),
      );
    });
  });

  // ---------------------------------------------------------------------------------------------
  // Prefill, part one: ask the tracker for the properties, once for the whole page.
  //
  // jExperience creates `window.digitalDataOverrides` in the head and hands the array to the tracker
  // at `wem.init()`; the tracker merges its entries at DOMContentLoaded, concatenating arrays. This
  // script is deferred, so it runs after the whole document is parsed and before DOMContentLoaded:
  // every block is in front of it and the array is still to be read — one push covers every form.

  /** The profile properties the page's forms prefill from, each once. */
  api.prefillProperties = () => {
    const names = [];
    api.configs().forEach((config) => {
      Object.keys(config.prefill || {}).forEach((field) => {
        const property = config.prefill[field] && config.prefill[field].property;
        if (property && names.indexOf(property) === -1) {
          names.push(property);
        }
      });
    });
    return names;
  };

  const required = api.prefillProperties();
  if (required.length > 0) {
    if (Array.isArray(window.digitalDataOverrides)) {
      window.digitalDataOverrides.push({ wemInitConfig: { requiredProfileProperties: required } });
    } else {
      // the block is written for a tracked site only, so the array should be there: said once, or a page
      // that never prefills would have nothing to show for it
      console.warn(
        "[Formidable] jExperience's digitalDataOverrides is not on the page: the profile properties " +
          required.join(", ") +
          " cannot be requested and nothing will be prefilled",
      );
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Prefill, part two: write the values, once per form, when both the tracker and the island are ready.

  /** Dispatched on a hidden control after it is written: an island whose state that control mirrors takes the value from here. */
  const PREFILL_EVENT = "formidable:prefill";

  /** The form the block describes: named by its UUID since phase 3, and taken over by the island once `noValidate` is set. */
  api.formOf = (formId) => document.querySelector('form[name="' + formId + '"]');

  api.islandReady = (form) => Boolean(form && form.noValidate);

  /**
   * The controls the visitor touched, marked by the browser's own `input` and `change` events — trusted
   * ones, never the events a script dispatches, this one included. Comparing the live state with the
   * default state would not say it: a single select has its first option selected by the browser
   * itself, a colour input is born "#000000" over an empty attribute, and a field the visitor typed in
   * then cleared reads as untouched again.
   */
  const touched = new WeakSet();
  const watch = (form) => {
    if (!form || form.dataset.fmdbWatched) {
      return;
    }
    form.dataset.fmdbWatched = "true";
    const mark = (e) => {
      if (e.isTrusted && e.target) {
        touched.add(e.target);
      }
    };
    form.addEventListener("input", mark, true);
    form.addEventListener("change", mark, true);
  };
  api.configs().forEach((config) => watch(api.formOf(config.formId)));

  /**
   * Fills the mapped fields of the form from the loaded context, one field at a time, and once. A control
   * the visitor touched is left alone. A default value the author gave the field gives way: the profile is
   * the fresher word about this visitor. A field the profile has no value for is left as it is, default
   * included — the prefill never blanks. Nothing happens without a tracker, a profile and a hydrated form;
   * a form is marked done once something was written into it, so the second of the two signals that open
   * the gates does not write again.
   */
  api.prefill = (formId) => {
    const form = api.formOf(formId);
    const config = api.configOf(formId);
    const pairs = (config && config.prefill) || {};
    if (!form || form.dataset.fmdbPrefilled || Object.keys(pairs).length === 0) {
      return false;
    }
    if (!api.islandReady(form) || !api.trackerReady()) {
      return false;
    }
    watch(form);
    const values = window.wem.getLoadedContext().profileProperties || {};
    const written = Object.keys(pairs).filter((field) => fill(form, field, values[pairs[field].property]));
    if (written.length > 0) {
      form.dataset.fmdbPrefilled = "true";
    }
    return true;
  };

  /**
   * Writes one profile value into the controls named `field`, by their shape; true when something was
   * written. `input` then `change` are dispatched on what changed, so the conditional logic, the input
   * mask and any validation see the value as if the visitor had typed it.
   */
  function fill(form, field, value) {
    const controls = Array.prototype.slice.call(form.elements).filter((el) => el.name === field);
    if (controls.length === 0) {
      return false;
    }
    const first = controls[0];
    const type = (first.type || "").toLowerCase();
    if (type === "checkbox" || type === "radio") {
      return fillChoices(controls, value);
    }
    if (first.tagName === "SELECT") {
      return fillSelect(first, value);
    }
    if (type === "file" || type === "submit" || type === "button" || type === "reset") {
      return false;
    }
    return fillText(first, value);
  }

  /**
   * One writable value out of what the context holds: a multi-valued property is an array, an empty one
   * says nothing, and an object is not a value — `String([])` or `String({})` would put a word in the
   * visitor's field.
   */
  function scalar(value) {
    if (Array.isArray(value)) {
      const found = value.find((v) => scalar(v) !== null);
      return found === undefined ? null : scalar(found);
    }
    if (value === undefined || value === null || value === "" || typeof value === "object") {
      return null;
    }
    return value;
  }

  /** The values a choice control matches against: every scalar of an array, or the one value, as strings. */
  function list(value) {
    const values = Array.isArray(value) ? value : [value];
    return values.map(scalar).filter((v) => v !== null).map(String);
  }

  const pad = (n) => (n < 10 ? "0" : "") + n;

  /**
   * The calendar day of an ISO date-time in the visitor's own zone — jCustomer stores instants and a date
   * input takes a day, and the UTC day of an instant is one day off for half the planet. A bare day is
   * kept as it is; a text that is no date at all is not written.
   */
  function localDate(value) {
    const text = String(value);
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
      return text;
    }
    const date = new Date(text);
    return isNaN(date.getTime())
      ? null
      : date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
  }

  /** The same, to the minute, for a datetime-local input. */
  function localDateTime(value) {
    const text = String(value);
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(text)) {
      return text;
    }
    const date = new Date(text);
    return isNaN(date.getTime())
      ? null
      : localDate(text) + "T" + pad(date.getHours()) + ":" + pad(date.getMinutes());
  }

  /**
   * text, email, tel, url, number, colour, date, datetime-local, textarea, hidden: one string. A hidden
   * control may be the mirror of an island's state (the range slider is built that way): it is written
   * like any other, then told, and the island takes the value over — or lets its state reset it.
   */
  function fillText(control, value) {
    if (touched.has(control)) {
      return false;
    }
    const one = scalar(value);
    let text = one === null ? null : String(one);
    if (text !== null && control.type === "date") {
      text = localDate(text);
    } else if (text !== null && control.type === "datetime-local") {
      text = localDateTime(text);
    }
    if (text === null) {
      return false;
    }
    setValue(control, text);
    changed(control);
    if (control.type === "hidden") {
      control.dispatchEvent(new CustomEvent(PREFILL_EVENT, { bubbles: true, detail: { value: text } }));
    }
    return true;
  }

  /**
   * A checkbox group, a single checkbox (a boolean or its own value), radios: check what the value names,
   * and only that. A profile naming nothing the options carry — no value at all, or values the field does
   * not offer — writes nothing, so an option the author checked by default stays checked: the prefill never
   * blanks a choice. A lone checkbox is a boolean too: an explicit false unchecks it, an absent value leaves it.
   */
  function fillChoices(controls, value) {
    if (controls.some((c) => touched.has(c))) {
      return false;
    }
    const wanted = list(value);
    const single = controls.length === 1 && controls[0].type === "checkbox";
    const asBoolean = single && (value === true || value === false || value === "true" || value === "false");
    const matching = controls.filter((c) => wanted.indexOf(c.value) > -1);
    if (matching.length === 0 && !asBoolean) {
      return false;
    }
    let written = false;
    controls.forEach((control) => {
      const check = asBoolean ? value === true || value === "true" : matching.indexOf(control) > -1;
      if (control.checked !== check) {
        control.checked = check;
        changed(control);
        written = true;
      }
    });
    return written;
  }

  /** A select, single or multiple: select the options whose value the profile names; no match, nothing. */
  function fillSelect(select, value) {
    const options = Array.prototype.slice.call(select.options);
    if (touched.has(select)) {
      return false;
    }
    const wanted = list(value);
    const matching = options.filter((o) => wanted.indexOf(o.value) > -1);
    if (matching.length === 0) {
      return false;
    }
    if (select.multiple) {
      options.forEach((o) => {
        o.selected = matching.indexOf(o) > -1;
      });
    } else {
      setValue(select, matching[0].value);
    }
    changed(select);
    return true;
  }

  /**
   * Through the prototype's setter, not the element's: React instruments the element's own `value`
   * setter to remember the last value it saw, and a value written through it looks unchanged to a
   * hydrated island; the prototype's setter writes the DOM and leaves the tracker to notice.
   */
  function setValue(control, text) {
    const proto = Object.getPrototypeOf(control);
    const descriptor = Object.getOwnPropertyDescriptor(proto, "value");
    if (descriptor && typeof descriptor.set === "function") {
      descriptor.set.call(control, text);
    } else {
      control.value = text;
    }
  }

  function changed(control) {
    control.dispatchEvent(new Event("input", { bubbles: true }));
    control.dispatchEvent(new Event("change", { bubbles: true }));
  }

  /** Tries every form of the page; each one fills once, when its two gates are open. */
  const attemptAll = () => api.configs().forEach((config) => api.prefill(config.formId));

  // The island signals its readiness; the tracker does not fire a DOM event, so its own callback
  // registration is used, which runs the callback at once when the context is already loaded. The
  // callbacks run in ascending priority and the tracker sets wemLoaded in its own at 99: ours comes
  // after, or the gate above would still read the flag as unset.
  document.addEventListener(READY_EVENT, (e) => {
    const formId = e.detail && e.detail.formId;
    if (formId) {
      api.prefill(formId);
    }
  });
  const onTrackerReady = (callback) => {
    if (window.wemLoaded === true) {
      callback();
    } else if (window.wem && typeof window.wem._registerCallback === "function") {
      window.wem._registerCallback(callback, "Formidable prefill", 100);
    } else {
      // a consent tool or a bot filter that kept the tracker off the page decides for this script too;
      // said once, so that a page that never prefills can be read
      console.warn(
        "[Formidable] no jExperience tracker on the page when the form script ran: the mapped fields are not prefilled",
      );
    }
  };
  if (required.length > 0) {
    onTrackerReady(attemptAll);
    attemptAll();
  }
})();

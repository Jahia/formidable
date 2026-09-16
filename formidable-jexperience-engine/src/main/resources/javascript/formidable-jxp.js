/*
 * Formidable, jExperience integration: the client half.
 *
 * Sends the `form` event of an accepted submission through jExperience's tracker, after the
 * server answered 200 and with the values the server accepted, never the DOM fields. The form
 * island announces an accepted submission with a bubbling `formidable:submitted` DOM event; the
 * server put the accepted values in the `jexperience` block of its answer; the render filter put
 * the form's configuration in a JSON block next to the form. Design and gates:
 * docs/architecture/jexperience-integration.md ("Submitting", "The send condition").
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

  /** The configuration block the render filter wrote for the form, or null. */
  api.configOf = (formId) => {
    const block = document.querySelector(
      'script[type="application/json"][data-formidable-jxp="' + formId + '"]',
    );
    if (!block) {
      return null;
    }
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
  };

  /**
   * Every reason to send or not, in one place: the one function of Formidable that depends on the
   * tracker's API. `wem` is jExperience's tracker; a consent manager that blocked it leaves none.
   * `wemLoaded` is set once the tracker ran its callbacks — also in its fallback mode, where no
   * context was loaded, so the loaded context must carry a profile. `activateWem` false is the
   * visitor's own "disable tracking"; `disableTrackedConditionsListeners` is the integrator's
   * page-level "no automatic form tracking", honoured and never set here.
   *
   * Then the form must be tracked, and `getFormNamesToWatch()` is the whole answer: the tracker
   * fills it from the `formEventCondition`s of the loaded context, and a form the author mapped has
   * one — the rule this integration writes at publication. Goals and segments are rules too, so one
   * list covers both and the page has nothing to declare about it.
   */
  api.shouldCollect = (formId) => {
    const wem = window.wem;
    const init = (window.digitalData && window.digitalData.wemInitConfig) || {};
    const context =
      wem && typeof wem.getLoadedContext === "function" ? wem.getLoadedContext() : null;
    return (
      Boolean(wem) &&
      window.wemLoaded === true &&
      Boolean(context && context.profileId) &&
      init.activateWem !== false &&
      !init.disableTrackedConditionsListeners &&
      typeof wem.getFormNamesToWatch === "function" &&
      wem.getFormNamesToWatch().indexOf(formId) > -1
    );
  };

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

  document.addEventListener("formidable:submitted", (e) => {
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
})();

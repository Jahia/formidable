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
 * One instance per page whatever the number of forms: the render filter writes the script tag
 * next to every form, the first one wins.
 */
(function () {
  'use strict';

  if (window.formidableJxp) {
    return;
  }
  var api = {};
  window.formidableJxp = api;

  /** The configuration block the render filter wrote for the form, or null. */
  api.configOf = function (formId) {
    var block = document.querySelector('script[type="application/json"][data-formidable-jxp="' + formId + '"]');
    if (!block) {
      return null;
    }
    try {
      return JSON.parse(block.textContent);
    } catch (e) {
      return null;
    }
  };

  /**
   * Every reason to send or not, in one place: the one function of Formidable that depends on
   * the tracker's API. `wem` is jExperience's tracker; a consent manager that blocked it leaves
   * none. `wemLoaded` is set once the tracker ran its callbacks — also in its fallback mode, where
   * no context was loaded, so the loaded context must carry a profile. `activateWem` false is the
   * visitor's own "disable tracking"; `disableTrackedConditionsListeners` is the integrator's
   * page-level "no automatic form tracking", honoured and never set here. Then the form must be
   * tracked: a field mapped by the author, or a goal, segment or rule referencing the form in the
   * context (`getFormNamesToWatch`, filled by the tracker at context load).
   */
  api.shouldCollect = function (formId, config) {
    var wem = window.wem;
    var init = (window.digitalData && window.digitalData.wemInitConfig) || {};
    var context = wem && typeof wem.getLoadedContext === 'function' ? wem.getLoadedContext() : null;
    return !!wem
      && window.wemLoaded === true
      && !!(context && context.profileId)
      && init.activateWem !== false
      && !init.disableTrackedConditionsListeners
      && (config.mappings.length > 0
        || (typeof wem.getFormNamesToWatch === 'function' && wem.getFormNamesToWatch().indexOf(formId) > -1));
  };

  /** The `form` event: the tracker's own builder, the form named for dashboards, the accepted values as fields. */
  api.buildEvent = function (config, fields) {
    var event = window.wem.buildFormEvent(config.formId);
    event.target.properties = {name: config.name, path: config.path};
    event.flattenedProperties = {fields: fields || {}};
    return event;
  };

  document.addEventListener('formidable:submitted', function (e) {
    var detail = e.detail || {};
    var response = detail.response || {};
    var block = response.jexperience;
    if (!block || block.formId !== detail.formId) {
      return; // the server did not describe this submission for jExperience
    }
    var config = api.configOf(detail.formId);
    if (!config || !api.shouldCollect(detail.formId, config)) {
      return;
    }
    window.wem.collectEvent(api.buildEvent(config, block.fields), function () {
      /* collected */
    }, function (xhr) {
      console.warn('[Formidable] jExperience did not collect the form event: ' + (xhr && xhr.status) + ' ' + (xhr && xhr.statusText));
    });
  });
})();

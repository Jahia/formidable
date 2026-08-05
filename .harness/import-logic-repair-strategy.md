# Formidable Conditional Logic Import Repair Strategy

## Goal

Repair stale or invalid `sourceFieldId` values in `logics` properties after operations that duplicate content, without delay and without relying on Drools.

Example stored logic:

```json
{
  "sourceFieldId": "42f4b274-1b7d-44f0-9fb4-805e4c8ad048",
  "sourceFieldName": "role",
  "sourceFieldType": "fmdb:select",
  "operator": "notIn",
  "values": ["marketing", "sales"]
}
```

Problem after duplication:

- JCR UUIDs can be regenerated
- node names are preserved
- `sourceFieldId` can become stale or point outside the intended copied subtree

## Recommended strategy

Use:

- a Java `DefaultEventListener`
- filtered on duplication-related operation types
- calling an idempotent repair service
- no delayed execution

This is preferable to a Drools rule because the problem is technical reference repair, not business logic.

## Why no delay is needed

In Jahia, listeners are consumed after the write/save cycle for the triggering operation.

That means the listener runs when:

- the duplicated `fmdb:form` subtree already exists
- child fields are already present
- the repository state is already materialized for the current operation

So the repair service can traverse `form/fields/...` immediately.

## High-level flow

1. an operation duplicates a form subtree
2. Jahia saves with the corresponding operation type
3. the custom listener receives `NODE_ADDED` events
4. it filters `fmdb:form`
5. it calls a repair service
6. the repair service:
   - walks the form tree
   - builds `fieldName + fieldType -> new UUID`
   - repairs rules that can be mapped inside the duplicated subtree
   - drops rules that cannot be resolved safely
   - saves only if something changed

## Mixed-case rule

Use one general rule for duplication:

- a logic rule is valid only if its source can be resolved inside the duplicated subtree
- if it can be resolved uniquely, repair `sourceFieldId`
- if it cannot be resolved, remove that rule
- if no rules remain on the target field, clear `logics`

This single rule covers all cases:

- target copied alone -> all rules are dropped
- target copied with all its sources -> all rules are repaired
- target copied with only some of its sources -> partial repair, unresolved rules dropped

Important constraint:

- do not fall back to an existing field already present in the destination form
- do not guess across forms
- logic dependencies must stay local to the duplicated subtree

## Idempotency contract

The repair service must be safe to run multiple times.

Expected behavior:

- if all `sourceFieldId` values are already correct: no write, no save
- if some values are stale: only those values are rewritten
- if some rules cannot be resolved safely: only those rules are removed
- invalid JSON entries are ignored and preserved as-is

This matters because import workflows can be retried or events can be replayed.

## Listener sample

Important:

- `javax.jcr.observation.Event` does not expose `getSession()`
- in a `DefaultEventListener`, Jahia exposes the originating session through `JCREventIterator`
- for actual reads/writes, use a fresh session opened through `JCRTemplate`
- prefer `event.getPath()` over `event.getIdentifier()` for `NODE_ADDED`

The standard Jahia pattern is:

1. read context from `((JCREventIterator) eventIterator).getSession()`
2. get the user from that session
3. reopen a clean system session with `JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(...)`
4. resolve nodes again by path inside that fresh session

```java
package org.jahia.modules.formidable.engine.listeners;

import org.jahia.modules.formidable.engine.rules.FormLogicRepairService;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCREventIterator;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.Set;

@Component(service = DefaultEventListener.class, immediate = true)
public class FormImportRepairListener extends DefaultEventListener {

    private static final Logger logger = LoggerFactory.getLogger(FormImportRepairListener.class);

    private FormLogicRepairService formLogicRepairService;

    public FormImportRepairListener() {
        setOperationTypes(Set.of(
                JCRObservationManager.IMPORT,
                JCRObservationManager.WORKSPACE_COPY
        ));
    }

    @Reference
    public void setFormLogicRepairService(FormLogicRepairService formLogicRepairService) {
        this.formLogicRepairService = formLogicRepairService;
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[] {"fmdb:form"};
    }

    @Override
    public void onEvent(EventIterator events) {
        try {
            JCRSessionWrapper eventSession = ((JCREventIterator) events).getSession();
            JahiaUser user = eventSession.getUser();

            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(user, workspace, null, new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    while (events.hasNext()) {
                        Event event = events.nextEvent();

                        try {
                            if (event.getType() != Event.NODE_ADDED) {
                                continue;
                            }

                            JCRNodeWrapper node = session.getNode(event.getPath());
                            if (!node.isNodeType("fmdb:form")) {
                                continue;
                            }

                            formLogicRepairService.repairLogics(node);
                        } catch (RepositoryException e) {
                            logger.debug("Skipping import repair event: {}", e.getMessage());
                        }
                    }

                    return null;
                }
            });
        } catch (RepositoryException e) {
            logger.error("Failed to process form import repair listener", e);
        }
    }
}
```

## Service contract sample

```java
package org.jahia.modules.formidable.engine.rules;

import org.jahia.services.content.JCRNodeWrapper;

public interface FormLogicRepairService {
    void repairLogics(JCRNodeWrapper formNode);
}
```

## Idempotent service sample

```java
package org.jahia.modules.formidable.engine.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = FormLogicRepairService.class, immediate = true)
public class FormLogicRepairServiceImpl implements FormLogicRepairService {

    @Override
    public void repairLogics(JCRNodeWrapper formNode) {
        try {
            if (!formNode.hasNode("fields")) {
                return;
            }

            JCRNodeWrapper fieldsNode = formNode.getNode("fields");

            Map<String, String> fieldNameToId = new HashMap<>();
            Map<String, String> fieldKeyToId = new HashMap<>();
            collectFields(fieldsNode, fieldNameToId, fieldKeyToId);

            int repaired = repairLogicsRecursive(fieldsNode, fieldNameToId, fieldKeyToId);

            if (repaired > 0) {
                formNode.getSession().save();
            }
        } catch (RepositoryException e) {
            // log
        }
    }

    private void collectFields(
            JCRNodeWrapper node,
            Map<String, String> nameToId,
            Map<String, String> keyToId
    ) throws RepositoryException {
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();

            if (child.isNodeType("fmdbmix:formElement") || child.isNodeType("fmdbmix:formStep")) {
                String name = child.getName();
                String id = child.getIdentifier();
                String type = child.getPrimaryNodeTypeName();

                nameToId.put(name, id);
                keyToId.put(name + "|" + type, id);
            }

            collectFields(child, nameToId, keyToId);
        }
    }

    private int repairLogicsRecursive(
            JCRNodeWrapper node,
            Map<String, String> nameToId,
            Map<String, String> keyToId
    ) throws RepositoryException {
        int repaired = 0;

        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();

            if (child.isNodeType("fmdbmix:formLogicElement") && child.hasProperty("logics")) {
                if (repairLogicsProperty(child, nameToId, keyToId)) {
                    repaired++;
                }
            }

            repaired += repairLogicsRecursive(child, nameToId, keyToId);
        }

        return repaired;
    }

    private boolean repairLogicsProperty(
            JCRNodeWrapper node,
            Map<String, String> nameToId,
            Map<String, String> keyToId
    ) throws RepositoryException {
        Value[] values = node.getProperty("logics").getValues();
        List<String> repairedValues = new ArrayList<>();
        boolean changed = false;

        for (Value value : values) {
            try {
                String raw = value.getString();
                JSONObject obj = new JSONObject(raw);

                String sourceName = obj.optString("sourceFieldName", "");
                String sourceType = obj.optString("sourceFieldType", "");
                String currentId = obj.optString("sourceFieldId", "");

                if (sourceName.isEmpty()) {
                    repairedValues.add(raw);
                    continue;
                }

                String correctId = keyToId.getOrDefault(
                        sourceName + "|" + sourceType,
                        nameToId.get(sourceName)
                );

                if (correctId != null && !correctId.equals(currentId)) {
                    obj.put("sourceFieldId", correctId);
                    repairedValues.add(obj.toString());
                    changed = true;
                } else if (correctId != null) {
                    repairedValues.add(raw);
                } else {
                    changed = true;
                }
            } catch (Exception e) {
                repairedValues.add(value.getString());
            }
        }

        if (changed) {
            if (repairedValues.isEmpty()) {
                node.setProperty("logics", new String[0]);
            } else {
                node.setProperty("logics", repairedValues.toArray(new String[0]));
            }
        }

        return changed;
    }
}
```

## Registration notes

The listener must be registered as a Jahia JCR event listener, not just exposed as an OSGi service.

Typical registration path:

- implement `DefaultEventListener`
- register/unregister it through `TemplatePackageRegistry.handleJCREventListener(listener, register)`

The exact registration wiring depends on how the module already registers Jahia listeners.

## Session handling notes

Do not use `event.getSession()` in the listener sample. That method does not exist on JCR events.

The correct Jahia pattern is:

- `((JCREventIterator) eventIterator).getSession()` to read event context
- `JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(...)` for the actual work

This avoids:

- relying on the event carrier as a live mutable session
- permission inconsistencies
- session lifecycle issues during listener execution

If user context is irrelevant for the repair, `doExecuteWithSystemSession(...)` can also work. But for consistency with Jahia core listeners, `doExecuteWithSystemSessionAsUser(...)` is the safer default.

## Path vs identifier

For `NODE_ADDED`, prefer:

```java
session.getNode(event.getPath())
```

over:

```java
session.getNodeByIdentifier(event.getIdentifier())
```

Reasons:

- `event.getIdentifier()` can be unavailable depending on the underlying JCR provider behavior
- Jahia itself often resolves added nodes by path in core listeners
- the path is the most stable lookup for a freshly added imported node during listener execution

Using `event.getIdentifier()` is not wrong in all cases, but it is less robust for this scenario.

## Event volume

An import emits `NODE_ADDED` for the form and for all its descendants.

That means a naive listener can receive events for:

- the `fmdb:form`
- `fields`
- fieldsets or steps
- every child field

To reduce useless work:

- keep `getEventTypes()` limited to `Event.NODE_ADDED`
- set `getNodeTypes()` to `new String[] {"fmdb:form"}`

This does not eliminate all internal filtering cost, but it keeps the listener intent explicit and reduces noise before `onEvent()` logic runs.

## About `sourceFieldName`

If you can guarantee that `sourceFieldId` is repaired immediately and stays correct after duplication, then runtime resolution can stay ID-only.

In that model:

- `sourceFieldId` is the only runtime identifier
- `sourceFieldName` is only an import-repair helper
- `sourceFieldName` is used by the repair service to map old imported rules to new field UUIDs

That is a valid simplification.

## Consequence of removing `sourceFieldName` from runtime

This is safe only if your system guarantees:

- duplicated forms are repaired before any logic evaluation depends on them
- all stored `sourceFieldId` values are correct after repair

If that guarantee holds, `sourceFieldName` does not need to participate in runtime evaluation.

## Recommended data model

Two reasonable options:

### Option A: keep both in storage, ID-only at runtime

Store:

```json
{
  "sourceFieldId": "...",
  "sourceFieldName": "role",
  "sourceFieldType": "fmdb:select",
  "operator": "notIn",
  "values": ["marketing", "sales"]
}
```

Use:

- `sourceFieldId` at runtime
- `sourceFieldName` only for import repair

This is the most pragmatic option.

### Option B: persist only `sourceFieldId`

This is only viable if you have another way to reconstruct the field mapping during import repair.

For duplication repair, that usually means:

- custom import metadata
- path-based mapping
- explicit reference properties instead of JSON payload

Without that, the service cannot repair stale UUIDs safely.

## Recommendation

Use Option A:

- keep `sourceFieldName` in the stored JSON
- use `sourceFieldId` only at runtime
- repair IDs with a Java listener on duplication-related operations + idempotent service
- do not use delay
- do not use Drools for this repair

This keeps runtime simple and keeps import repair deterministic.
This keeps runtime simple and keeps duplication repair deterministic.

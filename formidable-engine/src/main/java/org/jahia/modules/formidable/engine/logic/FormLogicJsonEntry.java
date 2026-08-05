package org.jahia.modules.formidable.engine.logic;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Value;
import java.util.UUID;

final class FormLogicJsonEntry {

    private static final Logger log = LoggerFactory.getLogger(FormLogicJsonEntry.class);
    private static final String LOGIC_ID = "logicId";
    private static final String SOURCE_NODE_ID = "sourceNodeId";
    private static final String SOURCE_FIELD_NAME = "sourceFieldName";
    private static final String SOURCE_FIELD_KEY = "sourceFieldKey";
    private static final String SOURCE_TYPE = "sourceType";
    private static final String VARIABLE = "variable";
    private static final String SOURCE_TYPE_JS_VARIABLE = "jsVariable";

    private final JSONObject json;
    private final String logicId;
    private final String sourceFieldName;
    private final boolean jsVariable;
    private String sourceNodeId;
    private String sourceFieldKey;
    private boolean updated;

    private FormLogicJsonEntry(
            JSONObject json,
            String logicId,
            String sourceNodeId,
            String sourceFieldName,
            String sourceFieldKey,
            boolean jsVariable,
            boolean updated
    ) {
        this.json = json;
        this.logicId = logicId;
        this.sourceNodeId = sourceNodeId;
        this.sourceFieldName = sourceFieldName;
        this.sourceFieldKey = sourceFieldKey;
        this.jsVariable = jsVariable;
        this.updated = updated;
    }

    static FormLogicJsonEntry parse(Value value, String targetPath) {
        try {
            String rawJson = value.getString();
            if (rawJson == null || rawJson.isBlank()) {
                return null;
            }

            JSONObject json = new JSONObject(rawJson);
            boolean jsVariable = SOURCE_TYPE_JS_VARIABLE.equals(json.optString(SOURCE_TYPE, ""));
            String sourceFieldName = json.optString(SOURCE_FIELD_NAME, "");
            if (jsVariable) {
                if (json.optString(VARIABLE, "").isBlank()) {
                    log.debug("[FormLogicSync] Skipping jsVariable rule without variable on '{}'", targetPath);
                    return null;
                }
            } else if (sourceFieldName.isEmpty()) {
                log.debug("[FormLogicSync] Skipping rule without sourceFieldName on '{}'", targetPath);
                return null;
            }

            String logicId = json.optString(LOGIC_ID, "");
            String sourceNodeId = json.optString(SOURCE_NODE_ID, "");
            String sourceFieldKey = json.optString(SOURCE_FIELD_KEY, "");
            boolean updated = false;

            if (logicId.isEmpty()) {
                logicId = generateLogicId();
                json.put(LOGIC_ID, logicId);
                updated = true;
            }

            return new FormLogicJsonEntry(json, logicId, sourceNodeId, sourceFieldName, sourceFieldKey, jsVariable, updated);
        } catch (Exception e) {
            log.debug("[FormLogicSync] Skipping invalid logics entry on '{}': {}", targetPath, e.getMessage());
            return null;
        }
    }

    boolean isJsVariable() {
        return jsVariable;
    }

    String logicId() {
        return logicId;
    }

    String sourceNodeId() {
        return sourceNodeId;
    }

    String sourceFieldName() {
        return sourceFieldName;
    }

    String sourceFieldKey() {
        return sourceFieldKey;
    }

    boolean isUpdated() {
        return updated;
    }

    void updateSourceNodeId(String resolvedUuid) {
        if (resolvedUuid.equals(sourceNodeId)) {
            return;
        }

        json.put(SOURCE_NODE_ID, resolvedUuid);
        sourceNodeId = resolvedUuid;
        updated = true;
    }

    void updateSourceFieldKey(String resolvedKey) {
        if (resolvedKey.equals(sourceFieldKey)) {
            return;
        }

        json.put(SOURCE_FIELD_KEY, resolvedKey);
        sourceFieldKey = resolvedKey;
        updated = true;
    }

    String toJsonString() {
        return json.toString();
    }

    private static String generateLogicId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

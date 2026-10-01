package org.jahia.modules.formidable.engine.config.common;

import org.jahia.services.modulemanager.spi.Config;
import org.jahia.services.modulemanager.spi.ConfigService;
import org.jahia.services.modulemanager.util.PropertiesValues;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Jahia's configuration service, in memory: a configuration per {@code factory PID + id}, empty until stored;
 * what was stored, by that key, and what was deleted, by PID.
 */
public final class FakeConfigService {

    private final String factoryPid;
    public final Map<String, Map<String, String>> stored = new HashMap<>();
    public final List<String> deleted = new java.util.ArrayList<>();
    public final ConfigService service = mock(ConfigService.class);

    public FakeConfigService(String factoryPid) throws Exception {
        this.factoryPid = factoryPid;
        when(service.getConfig(any(), any())).thenAnswer(call -> config(call.getArgument(0) + "-" + call.getArgument(1)));
        when(service.getConfig(any(String.class))).thenAnswer(call -> config(call.getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> {
            Config config = call.getArgument(0);
            stored.put(config.getIdentifier(), new java.util.LinkedHashMap<>(pending.remove(config.getIdentifier())));
            return null;
        }).when(service).storeConfig(any());
        org.mockito.Mockito.doAnswer(call -> {
            deleted.add(((Config) call.getArgument(0)).getIdentifier());
            return null;
        }).when(service).deleteConfig(any());
    }

    private final Map<String, Map<String, String>> pending = new HashMap<>();

    private Config config(String key) {
        Config config = mock(Config.class);
        when(config.getIdentifier()).thenReturn(key);
        when(config.getRawProperties()).thenReturn(stored.getOrDefault(key, Map.of()));
        PropertiesValues values = mock(PropertiesValues.class);
        org.mockito.Mockito.doAnswer(call -> {
            pending.computeIfAbsent(key, k -> new java.util.LinkedHashMap<>()).put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).setProperty(any(), any());
        when(config.getValues()).thenReturn(values);
        return config;
    }

    public Map<String, String> entry(String id) {
        return stored.get(factoryPid + "-" + id);
    }
}

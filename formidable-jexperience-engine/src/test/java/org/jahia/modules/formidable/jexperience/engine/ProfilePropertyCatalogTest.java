package org.jahia.modules.formidable.jexperience.engine;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PropertyType;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilePropertyCatalogTest {

    private static PropertyType type(String id, String name, String valueType) {
        Metadata metadata = new Metadata(id);
        metadata.setName(name);
        PropertyType type = new PropertyType(metadata);
        type.setValueTypeId(valueType);
        return type;
    }

    private static ContextServerService serviceAnswering(PropertyType... types) throws IOException {
        ContextServerService service = mock(ContextServerService.class);
        when(service.isAvailable("site")).thenReturn(true);
        when(service.executeGetRequest(eq("site"), eq(ProfilePropertyCatalog.PROPERTY_TYPES_PATH), isNull(), isNull(), eq(PropertyType[].class)))
                .thenReturn(types);
        return service;
    }

    @Test
    void listsFilteredPropertiesSortedByLabel() throws Exception {
        // Verifies the end-to-end shape of a fetch: filter applied, labels sorted case-insensitively.
        PropertyType hidden = type("h", "Hidden", "string");
        hidden.getMetadata().setHidden(true);
        ContextServerService service = serviceAnswering(type("zip", "zip code", "string"), hidden, type("age", "Age", "integer"));
        ProfilePropertyCatalog catalog = new ProfilePropertyCatalog(service, () -> Instant.EPOCH);
        List<ProfilePropertyDescriptor> properties = catalog.profileProperties("site");
        assertEquals(List.of("Age (age)", "zip code (zip)"), properties.stream().map(ProfilePropertyDescriptor::label).toList());
    }

    @Test
    void cachesForFiveMinutesThenRefreshes() throws Exception {
        // Verifies that a second call within the time to live does not reach jCustomer, and a later one does.
        ContextServerService service = serviceAnswering(type("a", "A", "string"));
        AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        ProfilePropertyCatalog catalog = new ProfilePropertyCatalog(service, now::get);
        catalog.profileProperties("site");
        now.set(Instant.EPOCH.plus(ProfilePropertyCatalog.TIME_TO_LIVE).minusSeconds(1));
        catalog.profileProperties("site");
        verify(service, times(1)).executeGetRequest(any(), any(), any(), any(), any());
        now.set(Instant.EPOCH.plus(ProfilePropertyCatalog.TIME_TO_LIVE).plusSeconds(1));
        catalog.profileProperties("site");
        verify(service, times(2)).executeGetRequest(any(), any(), any(), any(), any());
    }

    @Test
    void anExpiredListIsNotServedWhenTheRefreshFails() throws Exception {
        // Verifies the single-duration rule: past the minute, a failed read reports the schema
        // unavailable instead of serving a list that may no longer be true — and forgets it, so a
        // later success starts a fresh minute.
        ContextServerService service = serviceAnswering(type("a", "A", "string"));
        AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        ProfilePropertyCatalog catalog = new ProfilePropertyCatalog(service, now::get);
        catalog.profileProperties("site");
        when(service.executeGetRequest(any(), any(), any(), any(), any())).thenThrow(new IOException("connection refused"));
        now.set(Instant.EPOCH.plus(ProfilePropertyCatalog.TIME_TO_LIVE).plusSeconds(1));
        assertThrows(ProfilePropertiesUnavailableException.class, () -> catalog.profileProperties("site"));
        // within the minute, the list is still served without a call
        now.set(Instant.EPOCH.plus(ProfilePropertyCatalog.TIME_TO_LIVE).minusSeconds(1));
        assertThrows(ProfilePropertiesUnavailableException.class, () -> catalog.profileProperties("site"));
    }

    @Test
    void reportsUnavailabilityWithoutAPreviousList() throws Exception {
        // Verifies the three ways the schema can be unavailable on a first read: no module, site not
        // connected, failed call.
        ProfilePropertyCatalog noModule = new ProfilePropertyCatalog(null, () -> Instant.EPOCH);
        assertThrows(ProfilePropertiesUnavailableException.class, () -> noModule.profileProperties("site"));

        ContextServerService offline = mock(ContextServerService.class);
        when(offline.isAvailable("site")).thenReturn(false);
        assertThrows(ProfilePropertiesUnavailableException.class, () -> new ProfilePropertyCatalog(offline, () -> Instant.EPOCH).profileProperties("site"));

        ContextServerService failing = mock(ContextServerService.class);
        when(failing.isAvailable("site")).thenReturn(true);
        when(failing.executeGetRequest(any(), any(), any(), any(), any())).thenThrow(new IOException("timeout"));
        assertThrows(ProfilePropertiesUnavailableException.class, () -> new ProfilePropertyCatalog(failing, () -> Instant.EPOCH).profileProperties("site"));
    }
}

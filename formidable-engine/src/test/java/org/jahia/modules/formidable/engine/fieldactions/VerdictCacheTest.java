package org.jahia.modules.formidable.engine.fieldactions;

import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictCacheTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final VerdictCache cache = new VerdictCache(now::get);
    private final Duration ttl = Duration.ofSeconds(300);

    @Test
    void aVerdictIsKeptPerActionAndValueUntilItsTtlPasses() {
        // Verifies the nominal cache: the same action and value answer from the cache, another action or another
        // value do not, and the entry is gone once the TTL has passed — read against a clock the test moves.
        cache.put("a1", "ada@example.com", FieldActionResult.reject("undeliverable"), ttl);

        assertEquals(FieldActionResult.Verdict.REJECT, cache.get("a1", "ada@example.com", ttl).orElseThrow().verdict());
        assertTrue(cache.get("a2", "ada@example.com", ttl).isEmpty());
        assertTrue(cache.get("a1", "bob@example.com", ttl).isEmpty());

        now.addAndGet(ttl.toMillis());
        assertTrue(cache.get("a1", "ada@example.com", ttl).isEmpty());
    }

    @Test
    void theValueIsKeyedTrimmedSoTheBrowserAndThePipelineMeet() {
        // Verifies the normalisation: the browser may send the value with the blanks the pipeline later strips.
        // both must land on the one entry, or the submit-time re-check would pay the provider again.
        cache.put("a1", " ada@example.com ", FieldActionResult.accept(), ttl);

        assertTrue(cache.get("a1", "ada@example.com", ttl).isPresent());
    }

    @Test
    void anUnavailableAnswerAndAZeroTtlAreNeverCached() {
        // Verifies the two things the cache refuses: an UNAVAILABLE answer is a moment's truth, not the value's,
        // and a TTL of zero is the administrator switching the cache off — a put then stores nothing either.
        cache.put("a1", "x", FieldActionResult.unavailable("provider 503"), ttl);
        cache.put("a2", "x", FieldActionResult.accept(), Duration.ZERO);

        assertTrue(cache.get("a1", "x", ttl).isEmpty());
        assertTrue(cache.get("a2", "x", ttl).isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void theCacheStaysBoundedByDroppingExpiredEntriesFirst() {
        // Verifies the bound: past the maximum, the expired entries go and the cache keeps working; when nothing
        // has expired it starts over rather than grow — a check paid again is the cheaper failure.
        for (int i = 0; i < VerdictCache.MAX_ENTRIES; i++) {
            cache.put("a", "v" + i, FieldActionResult.accept(), ttl);
        }
        now.addAndGet(ttl.toMillis());
        cache.put("a", "fresh", FieldActionResult.accept(), ttl);
        assertEquals(1, cache.size());

        for (int i = 0; i < VerdictCache.MAX_ENTRIES; i++) {
            cache.put("b", "v" + i, FieldActionResult.accept(), ttl);
        }
        assertTrue(cache.size() <= VerdictCache.MAX_ENTRIES);
        assertTrue(cache.get("b", "v" + (VerdictCache.MAX_ENTRIES - 1), ttl).isPresent());
    }
}

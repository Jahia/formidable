package org.jahia.test.modules.formidable.quarantine.hold;

import org.jahia.modules.formidable.engine.api.SubmissionHold;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The other holds registered on the platform, the reference hold left out. The next hold is the one the engine
 * would bind without the reference hold: the highest service ranking, then the lowest service id, as OSGi orders
 * services.
 */
final class OtherHolds {

    static final String SERVICE_RANKING = "service.ranking";
    static final String SERVICE_ID = "service.id";

    private record Registered(SubmissionHold hold, int ranking, long serviceId) {
    }

    private static final Comparator<Registered> ORDER = Comparator
            .comparingInt(Registered::ranking).reversed()
            .thenComparingLong(Registered::serviceId);

    private final List<Registered> holds = new CopyOnWriteArrayList<>();

    void add(SubmissionHold hold, Map<String, ?> properties) {
        holds.add(new Registered(hold, ranking(properties.get(SERVICE_RANKING)), serviceId(properties.get(SERVICE_ID))));
    }

    void remove(SubmissionHold hold) {
        holds.removeIf(registered -> registered.hold() == hold);
    }

    /** The hold a form that the reference hold does not own goes to, or empty when no other hold is registered. */
    Optional<SubmissionHold> next() {
        return holds.stream().min(ORDER).map(Registered::hold);
    }

    /** OSGi reads a ranking that is not an Integer as 0. */
    private static int ranking(Object value) {
        return value instanceof Integer ranking ? ranking : 0;
    }

    private static long serviceId(Object value) {
        return value instanceof Long id ? id : Long.MAX_VALUE;
    }
}

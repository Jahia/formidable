package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.SubmissionHold;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The other holds registered on the platform, the sample hold left out. A form that the sample hold does not claim
 * goes to the one the engine would bind among the holds that rank below it: the highest service ranking, then the
 * lowest service id, as OSGi orders services. The pattern of the hold of {@code formidable-quarantine}, copied: a
 * module of its own copies it, the private packages of that module are not shared.
 */
final class LowerHolds {

    static final String SERVICE_RANKING = "service.ranking";
    static final String SERVICE_ID = "service.id";

    private record Registered(SubmissionHold hold, int ranking, long serviceId) {
    }

    private static final Comparator<Registered> ORDER = Comparator
            .comparingInt(Registered::ranking).reversed()
            .thenComparingLong(Registered::serviceId);

    private final List<Registered> holds = new CopyOnWriteArrayList<>();

    void add(SubmissionHold hold, Map<String, ?> properties) {
        holds.add(registered(hold, properties));
    }

    private static Registered registered(SubmissionHold hold, Map<String, ?> properties) {
        return new Registered(hold, ranking(properties.get(SERVICE_RANKING)), serviceId(properties.get(SERVICE_ID)));
    }

    /** The properties of a bound hold changed, such as its ranking: its place in the order follows, in one step. */
    void update(SubmissionHold hold, Map<String, ?> properties) {
        Registered updated = registered(hold, properties);
        holds.replaceAll(registered -> registered.hold() == hold ? updated : registered);
    }

    void remove(SubmissionHold hold) {
        holds.removeIf(registered -> registered.hold() == hold);
    }

    /**
     * @param ranking the ranking of the hold that hands the form on
     * @return the next hold below that ranking, or empty when there is none
     */
    Optional<SubmissionHold> nextBelow(int ranking) {
        return holds.stream().filter(registered -> registered.ranking() < ranking).min(ORDER).map(Registered::hold);
    }

    /** OSGi reads a ranking that is not an Integer as 0. */
    private static int ranking(Object value) {
        return value instanceof Integer ranking ? ranking : 0;
    }

    private static long serviceId(Object value) {
        return value instanceof Long id ? id : Long.MAX_VALUE;
    }
}

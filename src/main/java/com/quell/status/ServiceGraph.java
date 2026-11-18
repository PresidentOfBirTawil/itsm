package com.quell.status;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.quell.domain.ITService;

/**
 * The services and which ones depend on which, as a directed graph:
 * an edge from A to B means "A needs B". Read-only; built from the services
 * whenever it is needed.
 */
public final class ServiceGraph {

    private final List<ITService> services;

    public ServiceGraph(Collection<ITService> services) {
        this.services = services.stream().sorted(Comparator.comparing(ITService::getName)).toList();
    }

    public List<ITService> services() {
        return services;
    }

    /** Everything a service needs, directly or through other services. */
    public Set<ITService> upstream(ITService service) {
        Set<ITService> found = new LinkedHashSet<>();
        Deque<ITService> toVisit = new ArrayDeque<>(service.getDependsOn());
        while (!toVisit.isEmpty()) {
            ITService next = toVisit.pop();
            if (found.add(next)) {
                toVisit.addAll(next.getDependsOn());
            }
        }
        return found;
    }

    /** Every service that needs this one, directly or through other services. */
    public Set<ITService> downstream(ITService service) {
        Set<ITService> found = new LinkedHashSet<>();
        for (ITService candidate : services) {
            if (!candidate.equals(service) && upstream(candidate).contains(service)) {
                found.add(candidate);
            }
        }
        return found;
    }

    /**
     * Whether letting {@code service} depend on {@code dependencies} would
     * create a loop (A needs B needs A). Returns the service that would close
     * the loop, so the message can name it.
     */
    public static Optional<ITService> loopIfDependsOn(ITService service, Collection<ITService> dependencies) {
        for (ITService dependency : dependencies) {
            if (dependency.equals(service)) {
                return Optional.of(dependency);
            }
            Set<ITService> seen = new LinkedHashSet<>();
            Deque<ITService> toVisit = new ArrayDeque<>(dependency.getDependsOn());
            while (!toVisit.isEmpty()) {
                ITService next = toVisit.pop();
                if (next.equals(service)) {
                    return Optional.of(dependency);
                }
                if (seen.add(next)) {
                    toVisit.addAll(next.getDependsOn());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The layer of each service for drawing: 0 for services that need nothing
     * (infrastructure, drawn at the bottom), otherwise one more than the
     * highest layer among what it needs. So every arrow points downwards.
     */
    public Map<ITService, Integer> layers() {
        Map<ITService, Integer> layers = new HashMap<>();
        for (ITService service : services) {
            layer(service, layers, new LinkedHashSet<>());
        }
        return layers;
    }

    private int layer(ITService service, Map<ITService, Integer> layers, Set<ITService> path) {
        Integer known = layers.get(service);
        if (known != null) {
            return known;
        }
        if (!path.add(service)) {
            return 0;   // a loop: cannot happen through the forms, but never recurse for ever
        }
        int layer = 0;
        for (ITService dependency : service.getDependsOn()) {
            layer = Math.max(layer, layer(dependency, layers, path) + 1);
        }
        path.remove(service);
        layers.put(service, layer);
        return layer;
    }
}

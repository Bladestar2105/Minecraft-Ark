package de.ark.panel;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.LinkedHashMap;
import java.util.Map;

final class PendingResults {
    private record Job(long created, CompletableFuture<String> future) { }
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final LongSupplier clock;
    PendingResults(LongSupplier clock) { this.clock = clock; }
    private void expire() {
        jobs.values().removeIf(job -> {
            if (clock.getAsLong() - job.created() < 10_000_000_000L) return false;
            job.future().cancel(false);
            return true;
        });
    }
    boolean full() { expire(); return jobs.size() >= 100; }
    UUID add(CompletableFuture<String> future) {
        expire();
        if (jobs.size() >= 100) return null;
        UUID id = UUID.randomUUID();
        jobs.put(id, new Job(clock.getAsLong(), future));
        return id;
    }
    String get(UUID id) {
        expire();
        Job job = jobs.get(id);
        return job == null ? null : job.future().getNow("{\"available\":true,\"ready\":false,\"id\":\"" + id + "\"}");
    }
    void clear() { jobs.values().forEach(job -> job.future().cancel(false)); jobs.clear(); }
}

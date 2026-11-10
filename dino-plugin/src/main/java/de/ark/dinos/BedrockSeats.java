package de.ark.dinos;

import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.java.ServerUpdateEntityPassengersEvent;

/** Optional per-viewer seats; Geyser's native carrier offsets ignore the custom model's back. */
final class BedrockSeats implements AutoCloseable {
    private Seats seats;

    BedrockSeats(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                seats = new GeyserSeats();
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Bedrock dinosaur seats unavailable: " + error.getMessage());
            }
        }
    }

    void put(UUID carrier, float seatY) {
        if (!Float.isFinite(seatY)) throw new IllegalArgumentException("Non-finite dinosaur seat");
        if (seats != null) seats.put(carrier, seatY);
    }

    void remove(UUID carrier) {
        if (seats != null) seats.remove(carrier);
    }

    @Override public void close() {
        if (seats != null) {
            seats.close();
            seats = null;
        }
    }

    private interface Seats {
        void put(UUID carrier, float seatY);
        void remove(UUID carrier);
        void close();
    }

    // Keep optional API types out of the facade so Java-only servers can load it.
    private static final class GeyserSeats implements Seats, EventRegistrar {
        private final GeyserApi api = GeyserApi.api();
        private final Map<UUID, Float> carriers = new ConcurrentHashMap<>();
        // Weak keys let disconnected viewers disappear without retaining their whole session.
        private final Map<GeyserEntity, UUID> riders = Collections.synchronizedMap(new WeakHashMap<>());

        GeyserSeats() {
            api.eventBus().subscribe(this, ServerUpdateEntityPassengersEvent.Mount.class, event -> {
                UUID carrier = event.vehicle().uuid();
                Float seatY = carrier == null ? null : carriers.get(carrier);
                if (seatY != null) update(event.addedPassenger(), carrier, seatY);
                else reset(event.addedPassenger());
            });
            api.eventBus().subscribe(this, ServerUpdateEntityPassengersEvent.Dismount.class, event -> reset(event.removedPassenger()));
        }

        @Override public void put(UUID carrier, float seatY) {
            carriers.put(carrier, seatY);
            updatePassengers(carrier, seatY);
        }

        @Override public void remove(UUID carrier) {
            synchronized (riders) {
                carriers.remove(carrier);
                var iterator = riders.entrySet().iterator();
                while (iterator.hasNext()) {
                    var rider = iterator.next();
                    if (carrier.equals(rider.getValue())) {
                        rider.getKey().override(GeyserEntityDataTypes.SEAT_OFFSET, null);
                        iterator.remove();
                    }
                }
            }
        }

        private void updatePassengers(UUID carrier, float seatY) {
            for (var connection : api.onlineConnections()) {
                var vehicle = connection.entities().byUuid(carrier);
                if (vehicle != null) vehicle.passengers().forEach(passenger -> update(passenger, carrier, seatY));
            }
        }

        private void update(GeyserEntity passenger, UUID carrier, float seatY) {
            if (passenger instanceof GeyserPlayerEntity) {
                synchronized (riders) {
                    if (!Float.valueOf(seatY).equals(carriers.get(carrier))) return;
                    riders.put(passenger, carrier);
                    passenger.override(GeyserEntityDataTypes.SEAT_OFFSET, Vector3f.from(0, seatY, 0));
                }
            }
        }

        private void reset(GeyserEntity passenger) {
            synchronized (riders) {
                if (riders.remove(passenger) != null) passenger.override(GeyserEntityDataTypes.SEAT_OFFSET, null);
            }
        }

        @Override public void close() {
            api.eventBus().unregisterAll(this);
            for (UUID carrier : carriers.keySet()) remove(carrier);
        }
    }
}

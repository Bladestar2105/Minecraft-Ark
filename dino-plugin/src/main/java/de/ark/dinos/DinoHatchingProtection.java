package de.ark.dinos;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.entity.EntityTypes;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.HashSet;
import java.util.function.Predicate;
import org.bukkit.World;
import org.bukkit.entity.Ravager;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/** One synchronous WorldGuard deny-spawn decision for the Ravager currently hatching. */
final class DinoHatchingProtection {
    static final String METADATA = "arkdinos-owned-egg-hatch";

    static void mark(Plugin plugin, Ravager baby) {
        if (!baby.getWorld().getName().equals("ark")) return;
        Predicate<Object> permits = regions -> {
            try { return permits(baby.getWorld(), regions); }
            catch (LinkageError | RuntimeException unavailable) { return false; }
        };
        baby.setMetadata(METADATA, new FixedMetadataValue(plugin, permits));
    }

    private static boolean permits(World world, Object applicable) {
        if (!(applicable instanceof ApplicableRegionSet regions) || regions.isVirtual()) return false;
        var manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null) return false;
        var beginner = manager.getRegion("adventure_beginner");
        var others = new HashSet<>(regions.getRegions());
        if (beginner == null || !others.remove(beginner)) return false;
        var restriction = beginner.getFlag(Flags.DENY_SPAWN);
        if (restriction == null || !restriction.contains(EntityTypes.RAVAGER)) return false;
        if (beginner.getParent() != null) others.add(beginner.getParent());
        var remaining = new RegionResultSet(others, manager.getRegion(ProtectedRegion.GLOBAL_REGION));
        var denied = remaining.queryValue(null, Flags.DENY_SPAWN);
        return denied == null || !denied.contains(EntityTypes.RAVAGER);
    }
}

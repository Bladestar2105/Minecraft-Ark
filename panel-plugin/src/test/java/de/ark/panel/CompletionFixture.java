package de.ark.panel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompletionFixture extends JavaPlugin {
    private int executions;
    @Override public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("arkbridgeprobe").executes(context -> { executions++; return 1; })
                    .then(Commands.argument("value", StringArgumentType.word()).suggests((context, builder) -> {
                        var future = new CompletableFuture<Suggestions>();
                        getServer().getScheduler().runTaskLater(this, () -> future.complete(builder.suggest("deferred").build()), 3L);
                        return future;
                    }).executes(context -> { executions++; return 1; })).build());
            event.registrar().register(Commands.literal("arkbridgeexecutions").executes(context -> {
                context.getSource().getSender().sendPlainMessage("EXECUTIONS " + executions);
                return 1;
            }).build());
        });
    }
}

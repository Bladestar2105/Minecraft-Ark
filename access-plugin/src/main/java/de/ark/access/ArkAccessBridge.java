package de.ark.access;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArkAccessBridge extends JavaPlugin implements Listener {
    private static final Component UNAVAILABLE = Component.text("Die Anmeldung ist voruebergehend gesperrt. Bitte spaeter erneut versuchen.");
    private static final Component LINK_DENIED = Component.text("Die Java-Verknuepfung ist fuer dieses Serverkonto nicht bestaetigt. Bitte zuerst mit Java und dem bisherigen Serverpasswort anmelden. Bei einer abweichenden Identitaet die Serververwaltung kontaktieren.");
    private final SecureRandom random = new SecureRandom();
    private volatile boolean ready;
    private volatile boolean shutdownScheduled;
    private AccessRules rules;
    private Object authApi;
    private Method isRegistered;
    private Method forceRegister;
    private Method forceLogin;
    private Method isAuthenticated;
    private Method playerInfo;
    private Method infoName;
    private Method infoUuid;
    private FloodgateVerifier floodgate;
    private PremiumIdentities premiumIdentities;
    private PremiumVerifier premium;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        try {
            saveDefaultConfig();
            Plugin auth = getServer().getPluginManager().getPlugin("AuthMe");
            if (auth == null || !auth.isEnabled()) throw new IllegalStateException("AuthMe must be enabled");
            Plugin packets = getServer().getPluginManager().getPlugin("packetevents");
            if (packets == null || !packets.isEnabled()) throw new IllegalStateException("PacketEvents must be enabled");
            validateAuthConfiguration(auth);
            Class<?> apiType = auth.getClass().getClassLoader().loadClass("fr.xephi.authme.api.v3.AuthMeApi");
            authApi = apiType.getMethod("getInstance").invoke(null);
            isRegistered = apiType.getMethod("isRegistered", String.class);
            forceRegister = apiType.getMethod("forceRegister", Player.class, String.class, boolean.class);
            forceLogin = apiType.getMethod("forceLogin", Player.class);
            isAuthenticated = apiType.getMethod("isAuthenticated", Player.class);
            playerInfo = apiType.getMethod("getPlayerInfo", String.class);
            Class<?> infoType = auth.getClass().getClassLoader().loadClass("fr.xephi.authme.api.v3.AuthMePlayer");
            infoName = infoType.getMethod("getName");
            infoUuid = infoType.getMethod("getUuid");
            Map<String, UUID> legacy = new LinkedHashMap<>();
            var section = getConfig().getConfigurationSection("legacy-identities");
            if (section == null || section.getKeys(false).isEmpty()) throw new IllegalStateException("Legacy identities must be configured");
            for (String name : section.getKeys(false)) {
                legacy.put(name, UUID.fromString(section.getString(name, "")));
                if (!registered(name)) throw new IllegalStateException("Every legacy identity must be pre-registered in AuthMe");
            }
            rules = new AccessRules(legacy, getConfig().getString("bedrock-prefix", "."));
            premiumIdentities = new PremiumIdentities(getDataFolder().toPath().resolve("premium-identities.yml"), legacy);
            for (var identity : premiumIdentities.all().values()) {
                if (!registered(identity.name())) throw new IllegalStateException("Every premium identity must remain registered in AuthMe");
            }
            Plugin bedrock = getServer().getPluginManager().getPlugin("floodgate");
            if (bedrock != null && bedrock.isEnabled()) floodgate = new FloodgateVerifier(premiumIdentities::linkedBackend);
            premium = new PremiumVerifier(this, auth, premiumIdentities, this::registeredUnchecked,
                (name, address) -> floodgate != null && floodgate.verifiedHandshake(name, address),
                failure -> failClosed("Premium authentication dependency failed", failure));
            registerAuthEvents(auth);
            ready = true;
            getLogger().info("READY: premium=true; password protection active; legacy-identities=" + legacy.size() + "; floodgate=" + (floodgate != null));
        } catch (Exception | LinkageError failure) {
            failClosed("Access guard initialization failed", failure);
        }
    }

    private void validateAuthConfiguration(Plugin auth) {
        var config = YamlConfiguration.loadConfiguration(new File(auth.getDataFolder(), "config.yml"));
        require(config, "settings.enablePremium", false);
        require(config, "settings.sessions.enabled", false);
        require(config, "settings.registration.enabled", true);
        require(config, "settings.registration.force", true);
        require(config, "settings.preventOtherCase", true);
        require(config, "settings.registration.dialog.preJoin.enable", false);
        require(config, "settings.registration.dialog.postJoin.enable", false);
        require(config, "settings.restrictions.ForceSingleSession", true);
        require(config, "settings.restrictions.ProtectInventoryBeforeLogIn", true);
        require(config, "settings.restrictions.allowChat", false);
        require(config, "Security.SQLProblem.stopServer", true);
        require(config, "Hooks.bungeecord", false);
        if (!config.getStringList("settings.unrestrictions.UnrestrictedName").isEmpty()) {
            throw new IllegalStateException("AuthMe must not have unrestricted account names");
        }
        Set<String> safeCommands = Set.of("/login", "/log", "/l", "/register", "/reg", "/email", "/captcha", "/2fa", "/totp");
        if (!safeCommands.containsAll(config.getStringList("settings.restrictions.allowCommands"))) {
            throw new IllegalStateException("Only authentication commands may be allowed before login");
        }
    }

    private static void require(YamlConfiguration config, String key, boolean expected) {
        if (!config.isBoolean(key) || config.getBoolean(key) != expected) {
            throw new IllegalStateException("Unsafe AuthMe setting: " + key);
        }
    }

    private boolean registered(String name) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(isRegistered.invoke(authApi, name));
    }

    private boolean registeredUnchecked(String name) {
        try { return registered(name); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Authentication account lookup failed", failure); }
    }

    private record StoredIdentity(String name, UUID uuid) {}

    private StoredIdentity storedIdentity(String name) throws ReflectiveOperationException {
        var info = (Optional<?>) playerInfo.invoke(authApi, name);
        if (info.isEmpty()) return null;
        Object value = info.get();
        String canonical = (String) infoName.invoke(value);
        var uuid = (Optional<?>) infoUuid.invoke(value);
        return new StoredIdentity(canonical, uuid.isPresent() ? (UUID) uuid.get() : null);
    }

    @SuppressWarnings("unchecked")
    private void registerAuthEvents(Plugin auth) throws Exception {
        for (String type : new String[]{"LoginEvent", "RegisterEvent"}) {
            Class<?> event = auth.getClass().getClassLoader().loadClass("fr.xephi.authme.events." + type);
            Method playerGetter = event.getMethod("getPlayer");
            getServer().getPluginManager().registerEvent((Class<? extends Event>) event, this, EventPriority.MONITOR,
                (listener, fired) -> {
                    try {
                        Player player = (Player) playerGetter.invoke(fired);
                        getServer().getScheduler().runTask(this, () -> confirmAuthenticated(player, 0));
                    } catch (Exception failure) { failClosed("Authentication event integration failed", failure); }
                }, this);
        }
    }

    private void confirmAuthenticated(Player player, int attempts) {
        if (!ready || !player.isOnline() || getServer().getPlayer(player.getUniqueId()) != player) return;
        var session = premium.proof(player.getName(), player.getConnection().getAddress());
        PremiumIdentities.Identity identity;
        boolean registrationConfirmed;
        if (session != null && session.joinedPlayer == player && player.getUniqueId().equals(session.backendUuid)) {
            identity = new PremiumIdentities.Identity(player.getName(), session.mojangUuid, session.backendUuid);
            registrationConfirmed = session.registeredAtStart || session.registrationRequested;
        } else {
            var proof = floodgate == null ? null : floodgate.verified(player.getName(), player.getUniqueId(),
                player.getConnection().getAddress(), player.getAddress());
            if (proof == null || !proof.linked || proof.admission == null || proof.admission.mojangUuid() == null) return;
            identity = new PremiumIdentities.Identity(player.getName(), proof.admission.mojangUuid(), proof.admission.backendUuid());
            registrationConfirmed = proof.admission.registeredAtStart() || proof.registrationRequested;
        }
        try {
            if (!Boolean.TRUE.equals(isAuthenticated.invoke(authApi, player))) {
                if (attempts < 60) getServer().getScheduler().runTaskLater(this, () -> confirmAuthenticated(player, attempts + 1), 5L);
                return;
            }
            if (!registrationConfirmed) {
                player.kick(UNAVAILABLE);
                return;
            }
            premiumIdentities.confirm(identity.name(), identity.mojangUuid(), identity.backendUuid());
        } catch (Exception failure) {
            player.kick(UNAVAILABLE);
            failClosed("Premium identity could not be safely persisted", failure);
        }
    }

    @SuppressWarnings("removal") // Paper 26.3 exposes the login UUID override through its mutable profile API.
    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!ready) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
            return;
        }
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            if (rules.isLegacy(event.getName()) && !registered(event.getName())) {
                throw new IllegalStateException("A reserved legacy account is no longer registered");
            }
            var bedrockProof = floodgate == null ? null : floodgate.verified(event.getName(), event.getUniqueId(),
                event.getConnection().getAddress(), event.getConnection().getClientAddress());
            boolean bedrock = bedrockProof != null;
            if (!bedrock && floodgate != null && floodgate.verifiedHandshake(event.getName(), event.getConnection().getAddress())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
                return;
            }
            var proof = premium.proof(event.getName(), event.getConnection().getAddress());
            if (!bedrock && premiumIdentities.known(event.getName()) != null && proof == null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Dieses Konto benoetigt eine verifizierte Minecraft-Anmeldung."));
                return;
            }
            var admission = rules.admit(event.getName(), event.getUniqueId(), bedrock);
            if (!admission.allowed()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(admission.reason()));
                return;
            }
            UUID backend = admission.uuid();
            if (bedrock) {
                boolean nowRegistered = registered(event.getName());
                if (bedrockProof.linked) {
                    Boolean localConfirmed = floodgate.confirmedLocalLink(bedrockProof);
                    if (localConfirmed == null) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
                        return;
                    }
                    bedrockProof.localConfirmed = localConfirmed;
                    StoredIdentity stored = nowRegistered ? storedIdentity(event.getName()) : null;
                    var identity = nowRegistered && (stored == null || !event.getName().equals(stored.name())) ? null
                        : premiumIdentities.linked(event.getName(), bedrockProof.uuid, nowRegistered, stored == null ? null : stored.uuid(), bedrockProof.localConfirmed);
                    if (identity == null) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, LINK_DENIED);
                        return;
                    }
                    backend = identity.backendUuid();
                    // Floodgate b141 uses its preconstructed profile: a newly confirmed binding needs a fresh handshake.
                    if (!backend.equals(bedrockProof.floodgateUuid)) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Die Kontoverknuepfung wurde aktualisiert. Bitte erneut verbinden."));
                        return;
                    }
                    bedrockProof.admission = new FloodgateProofs.Admission(backend, identity.mojangUuid(),
                        bedrockProof.localConfirmed || premiumIdentities.known(event.getName()) != null, nowRegistered);
                } else {
                    bedrockProof.admission = new FloodgateProofs.Admission(backend, null, nowRegistered, nowRegistered);
                }
            }
            if (!bedrock && proof != null) {
                boolean nowRegistered = registered(event.getName());
                if (PremiumRules.action(proof.known != null, proof.registeredAtStart, nowRegistered) == PremiumRules.Action.REJECT) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
                    return;
                }
                StoredIdentity stored = nowRegistered ? storedIdentity(event.getName()) : null;
                if (nowRegistered && (stored == null || !event.getName().equals(stored.name()))) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
                    return;
                }
                UUID existingBackend = premiumIdentities.backendFor(proof.mojangUuid);
                backend = proof.known != null ? proof.known.backendUuid()
                    : proof.registeredAtStart ? stored.uuid() == null
                        ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + stored.name()).getBytes(StandardCharsets.UTF_8)) : stored.uuid()
                    : existingBackend == null ? proof.mojangUuid : existingBackend;
                if (proof.known != null && stored.uuid() != null && !stored.uuid().equals(backend)) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
                    return;
                }
                if (existingBackend != null && !existingBackend.equals(backend)) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
                    return;
                }
                proof.backendUuid = backend;
            }
            if (!backend.equals(event.getUniqueId())) {
                var profile = event.getPlayerProfile();
                profile.setId(backend);
                event.setPlayerProfile(profile);
            }
        } catch (Exception | LinkageError failure) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
            failClosed("Access verification failed", failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var session = premium == null ? null : premium.joined(player);
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline() || getServer().getPlayer(player.getUniqueId()) != player) return;
            if (!ready) {
                player.kick(UNAVAILABLE);
                return;
            }
            try {
                var bedrockProof = floodgate == null ? null : floodgate.verified(player.getName(), player.getUniqueId(),
                    player.getConnection().getAddress(), player.getAddress());
                boolean bedrock = bedrockProof != null;
                if (!bedrock && floodgate != null && floodgate.verifiedHandshake(player.getName(), player.getConnection().getAddress())) {
                    player.kick(UNAVAILABLE);
                    return;
                }
                if (!bedrock && (session == null || premium.proof(player.getName(), player.getConnection().getAddress()) != session)) return;
                if (bedrock && bedrockProof.admission == null) { player.kick(UNAVAILABLE); return; }
                boolean nowRegistered = registered(player.getName());
                if (bedrock && bedrockProof.linked) {
                    StoredIdentity stored = nowRegistered ? storedIdentity(player.getName()) : null;
                    var identity = nowRegistered && (stored == null || !player.getName().equals(stored.name())) ? null
                        : premiumIdentities.linked(player.getName(), bedrockProof.uuid, nowRegistered, stored == null ? null : stored.uuid(), bedrockProof.localConfirmed);
                    if (identity == null || !identity.backendUuid().equals(bedrockProof.admission.backendUuid())
                        || !Objects.equals(identity.mojangUuid(), bedrockProof.admission.mojangUuid())) { player.kick(LINK_DENIED); return; }
                }
                if (Boolean.TRUE.equals(isAuthenticated.invoke(authApi, player))) return;
                PremiumRules.Action action = bedrock ? bedrockProof.admission.action(nowRegistered)
                    : PremiumRules.action(session.known != null, session.registeredAtStart, nowRegistered);
                if (action == PremiumRules.Action.REJECT) { player.kick(UNAVAILABLE); return; }
                if (action == PremiumRules.Action.PASSWORD) return;
                if (action == PremiumRules.Action.LOGIN) {
                    forceLogin.invoke(authApi, player);
                } else {
                    byte[] secret = new byte[18];
                    random.nextBytes(secret);
                    if (session != null && !bedrock) session.registrationRequested = true;
                    if (bedrock) bedrockProof.registrationRequested = true;
                    forceRegister.invoke(authApi, player, Base64.getUrlEncoder().withoutPadding().encodeToString(secret), true);
                }
            } catch (Exception | LinkageError failure) {
                player.kick(UNAVAILABLE);
                failClosed("Verified player authentication failed", failure);
            }
        });
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if (!getServer().isStopping() && (name.equalsIgnoreCase("AuthMe") || name.equalsIgnoreCase("packetevents")
            || name.equalsIgnoreCase("floodgate"))) failClosed("Authentication dependency was disabled", null);
    }

    private void failClosed(String reason, Throwable failure) {
        ready = false;
        String detail = failure instanceof IllegalStateException ? failure.getMessage()
            : failure == null ? "" : failure.getClass().getSimpleName();
        getLogger().severe(reason + (detail.isEmpty() ? "" : " (" + detail + ")") + "; all logins denied, stopping server.");
        if (!shutdownScheduled) {
            shutdownScheduled = true;
            getServer().getScheduler().runTask(this, () -> {
                getServer().getOnlinePlayers().forEach(player -> player.kick(UNAVAILABLE));
                getServer().shutdown();
            });
        }
    }

    @Override
    public void onDisable() {
        ready = false;
        if (premium != null) premium.close();
        if (floodgate != null) floodgate.close();
        if (!getServer().isStopping() && !getServer().getOnlineMode()) getServer().shutdown();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("status")) return false;
        sender.sendMessage(ready ? "ARKACCESS READY" : "ARKACCESS BLOCKED");
        return true;
    }
}

import java.lang.classfile.*;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.*;
import static java.lang.constant.ConstantDescs.*;

/** Rebuilds one optional hook at WorldGuard 7.0.21's regional DENY_SPAWN check. */
public final class WorldGuardOwnedHatchFix {
    private static final String INPUT_SHA = "070223c535629f3f5d15f002388c1e3ae615a5f4ba051e7ccfb5d716540547a9";
    private static final ClassDesc LISTENER = ClassDesc.of("com.sk89q.worldguard.bukkit.listener.WorldGuardEntityListener");
    private static final ClassDesc EVENT = ClassDesc.of("org.bukkit.event.entity.CreatureSpawnEvent");
    private static final ClassDesc REASON = EVENT.nested("SpawnReason");
    private static final ClassDesc CANCELLABLE = ClassDesc.of("org.bukkit.event.Cancellable");
    private static final ClassDesc REGIONS = ClassDesc.of("com.sk89q.worldguard.protection.ApplicableRegionSet");
    private static final String HOOK = "arkDenyOwnedEggSpawn";
    private static final MethodTypeDesc HOOK_TYPE = MethodTypeDesc.of(CD_boolean, CD_boolean, CANCELLABLE, REGIONS);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected input.jar output.jar");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (!sha(Files.readAllBytes(input)).equals(INPUT_SHA)) throw new IllegalArgumentException("Unexpected WorldGuard input");
        String listener = LISTENER.packageName().replace('.', '/') + "/WorldGuardEntityListener.class";
        try (var original = new ZipFile(input.toFile())) {
            byte[] replacement;
            try (var stream = original.getInputStream(original.getEntry(listener))) { replacement = patch(stream.readAllBytes()); }
            try (var archive = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
                archive.setLevel(9);
                archive.setComment(original.getComment());
                for (var entries = original.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement();
                    byte[] bytes;
                    if (listener.equals(entry.getName())) bytes = replacement;
                    else try (var stream = original.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                    var copy = new ZipEntry(entry);
                    if (copy.getMethod() == ZipEntry.DEFLATED) copy.setCompressedSize(-1);
                    var crc = new CRC32();
                    crc.update(bytes);
                    copy.setCrc(crc.getValue()); copy.setSize(bytes.length);
                    archive.putNextEntry(copy); archive.write(bytes); archive.closeEntry();
                }
                Path sources = Path.of(System.getProperty("jdk.launcher.sourcefile")).toAbsolutePath().getParent();
                for (String name : java.util.List.of("README.md", "LICENSE-WorldGuard.txt", "LICENSE-GPL-3.0.txt")) {
                    var notice = new ZipEntry("META-INF/ark-owned-egg-hatch/" + name);
                    notice.setTime(0);
                    archive.putNextEntry(notice); Files.copy(sources.resolve(name), archive); archive.closeEntry();
                }
            }
        }
        System.out.println(sha(Files.readAllBytes(output)) + "  " + output);
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] patch(byte[] bytes) {
        var cf = ClassFile.of();
        int[] matches = {0};
        var transformed = cf.transformClass(cf.parse(bytes), new ClassTransform() {
            public void accept(ClassBuilder out, ClassElement element) {
                if (!(element instanceof MethodModel method) || !method.methodName().equalsString("handleCreatureSpawn")) {
                    out.with(element); return;
                }
                out.transformMethod(method, MethodTransform.transformingCode(new CodeTransform() {
                    private boolean regionDeny;
                    public void accept(CodeBuilder code, CodeElement instruction) {
                        code.with(instruction);
                        if (instruction instanceof FieldInstruction field && field.name().equalsString("DENY_SPAWN")
                                && field.owner().asInternalName().equals("com/sk89q/worldguard/protection/flags/Flags")) regionDeny = true;
                        if (regionDeny && instruction instanceof InvokeInstruction call && call.name().equalsString("contains")
                                && call.owner().asInternalName().equals("java/util/Set")) {
                            // Keep the native answer and pass its event and already resolved region set.
                            code.aload(0).aload(7).invokestatic(LISTENER, HOOK, HOOK_TYPE);
                            regionDeny = false; matches[0]++;
                        }
                    }
                }));
            }
            public void atEnd(ClassBuilder out) {
                out.withMethodBody(HOOK, HOOK_TYPE, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, WorldGuardOwnedHatchFix::decision);
            }
        });
        if (matches[0] != 1) throw new IllegalStateException("Expected one native regional deny-spawn membership check");
        return transformed;
    }

    // Only ArkDinos can supply the entity's transient Predicate. An older or absent plugin leaves the native answer intact.
    private static void decision(CodeBuilder c) {
        var original = c.newLabel();
        var living = ClassDesc.of("org.bukkit.entity.LivingEntity");
        var list = ClassDesc.of("java.util.List");
        var value = ClassDesc.of("org.bukkit.metadata.MetadataValue");
        var plugin = ClassDesc.of("org.bukkit.plugin.Plugin");
        var plugins = ClassDesc.of("org.bukkit.plugin.PluginManager");
        var predicate = ClassDesc.of("java.util.function.Predicate");
        c.iload(0).ifeq(original).aload(1).instanceOf(EVENT).ifeq(original);
        c.aload(1).checkcast(EVENT).astore(3);
        c.aload(3).invokevirtual(EVENT, "getSpawnReason", MethodTypeDesc.of(REASON))
                .getstatic(REASON, "EGG", REASON).if_acmpne(original);
        c.aload(3).invokevirtual(EVENT, "getEntity", MethodTypeDesc.of(living))
                .ldc("arkdinos-owned-egg-hatch").invokeinterface(living, "getMetadata", MethodTypeDesc.of(list, CD_String)).astore(4);
        c.aload(4).invokeinterface(list, "size", MethodTypeDesc.of(CD_int)).iconst_1().if_icmpne(original);
        c.aload(4).iconst_0().invokeinterface(list, "get", MethodTypeDesc.of(CD_Object, CD_int)).checkcast(value).astore(5);
        c.aload(5).invokeinterface(value, "getOwningPlugin", MethodTypeDesc.of(plugin)).astore(7);
        c.aload(7).ifnull(original).aload(7).invokestatic(ClassDesc.of("org.bukkit.Bukkit"), "getPluginManager", MethodTypeDesc.of(plugins))
                .ldc("ArkDinos").invokeinterface(plugins, "getPlugin", MethodTypeDesc.of(plugin, CD_String)).if_acmpne(original);
        c.aload(5).invokeinterface(value, "value", MethodTypeDesc.of(CD_Object)).astore(6);
        c.aload(6).instanceOf(predicate).ifeq(original);
        c.aload(6).checkcast(predicate).aload(2).invokeinterface(predicate, "test", MethodTypeDesc.of(CD_boolean, CD_Object)).ifeq(original);
        c.iconst_0().ireturn().labelBinding(original).iload(0).ireturn();
    }
}

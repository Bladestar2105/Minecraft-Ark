import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

import static java.lang.constant.ConstantDescs.*;

/** Apply the reviewed entity-property receiver guard to the pinned GeyserUtils jar; Java25, no dependencies. */
public final class GeyserUtilsEntityPropertyValidationFix {
    private static final String INPUT_SHA = "8ed7b7dbece8341277652d4ee01706913e725cdf19b9664025bbb870299ec12a";
    private static final String TARGET = "me/zimzaza4/geyserutils/geyser/GeyserUtils.class";
    private static final String HELPER = "ark$handleEntityProperty";
    private static final String OTHER_PACKETS = "ark$handleOtherCustomPacket";
    private static final ClassDesc UTILS = ClassDesc.of("me.zimzaza4.geyserutils.geyser.GeyserUtils");
    private static final ClassDesc PAYLOAD = ClassDesc.of("me.zimzaza4.geyserutils.common.packet.CustomPayloadPacket");
    private static final ClassDesc UPDATE = ClassDesc.of("me.zimzaza4.geyserutils.common.packet.EntityPropertyPacket");
    private static final ClassDesc SESSION = ClassDesc.of("org.geysermc.geyser.session.GeyserSession");
    private static final ClassDesc CACHE = ClassDesc.of("org.geysermc.geyser.session.cache.EntityCache");
    private static final ClassDesc ENTITY = ClassDesc.of("org.geysermc.geyser.entity.type.Entity");
    private static final ClassDesc DEFINITION = ClassDesc.of("org.geysermc.geyser.entity.BedrockEntityDefinition");
    private static final ClassDesc PROPERTIES = ClassDesc.of("org.geysermc.geyser.entity.properties.GeyserEntityProperties");
    private static final ClassDesc MANAGER = ClassDesc.of("org.geysermc.geyser.entity.properties.GeyserEntityPropertyManager");
    private static final ClassDesc PROPERTY = ClassDesc.of("org.geysermc.geyser.entity.properties.type.PropertyType");
    private static final ClassDesc BOOL_PROPERTY = ClassDesc.of("org.geysermc.geyser.entity.properties.type.BooleanProperty");
    private static final ClassDesc INT_PROPERTY = ClassDesc.of("org.geysermc.geyser.entity.properties.type.IntProperty");
    private static final ClassDesc IDENTIFIER = ClassDesc.of("org.geysermc.geyser.api.util.Identifier");
    private static final ClassDesc LIST = ClassDesc.of("java.util.List");
    private static final ClassDesc INVALID_IDENTIFIER = ClassDesc.of("java.lang.IllegalArgumentException");
    private static final MethodTypeDesc RECEIVER_TYPE = MethodTypeDesc.of(CD_void, SESSION, PAYLOAD);
    private static final MethodTypeDesc HELPER_TYPE = MethodTypeDesc.of(CD_void, SESSION, UPDATE);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java GeyserUtilsEntityPropertyValidationFix.java input.jar output.jar");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Output must be separate from input");
        String actual = sha256(Files.readAllBytes(input));
        if (!actual.equals(INPUT_SHA)) throw new IllegalArgumentException("Unexpected input SHA-256: " + actual);

        try (var source = new ZipFile(input.toFile())) {
            var entries = Collections.list(source.entries());
            if (entries.stream().anyMatch(e -> e.getName().matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")))
                throw new IllegalArgumentException("Signed input is not supported");
            if (entries.stream().filter(e -> e.getName().equals(TARGET)).count() != 1)
                throw new IllegalStateException("Missing unique GeyserUtils class");
            byte[] patched;
            try (var stream = source.getInputStream(source.getEntry(TARGET))) {
                patched = patchReceiver(stream.readAllBytes());
            }

            // CREATE_NEW protects existing output; remove only a newly created incomplete artifact.
            var outputStream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW);
            boolean complete = false;
            try {
                try (var target = new ZipOutputStream(outputStream)) {
                    target.setLevel(9);
                    if (source.getComment() != null) target.setComment(source.getComment());
                    for (var entry : entries) {
                        byte[] bytes;
                        if (entry.getName().equals(TARGET)) bytes = patched;
                        else try (var stream = source.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                        var copy = new ZipEntry(entry);
                        if (copy.getMethod() == ZipEntry.DEFLATED) copy.setCompressedSize(-1);
                        copy.setSize(bytes.length);
                        var crc = new CRC32(); crc.update(bytes); copy.setCrc(crc.getValue());
                        target.putNextEntry(copy); target.write(bytes); target.closeEntry();
                    }
                }
                complete = true;
            } finally {
                if (!complete) Files.deleteIfExists(output);
            }
        }
        System.out.println(sha256(Files.readAllBytes(output)) + "  " + output);
    }

    private static byte[] patchReceiver(byte[] bytes) {
        var classFile = ClassFile.of();
        var model = classFile.parse(bytes);
        var handlers = model.methods().stream().filter(m -> m.methodName().equalsString("handleCustomPacket")
                && m.methodType().equalsString(RECEIVER_TYPE.descriptorString())).toList();
        if (handlers.size() != 1 || (handlers.getFirst().flags().flagsMask() & ClassFile.ACC_STATIC) != 0)
            throw new IllegalStateException("Missing unique instance packet receiver");
        if (model.methods().stream().anyMatch(m -> m.methodName().equalsString(HELPER)
                || m.methodName().equalsString(OTHER_PACKETS)))
            throw new IllegalStateException("Guard helper already exists");
        int[] changed = {0};
        byte[] result = classFile.transformClass(model, new ClassTransform() {
            @Override public void accept(ClassBuilder builder, ClassElement element) {
                if (element == handlers.getFirst()) {
                    // Copy the old body and its stack maps verbatim. Rewriting its many unrelated
                    // branches would require Geyser's external class hierarchy during this build.
                    builder.withMethod(OTHER_PACKETS, RECEIVER_TYPE, handlers.getFirst().flags().flagsMask(),
                            mb -> handlers.getFirst().forEach(mb::with));
                    changed[0]++;
                } else builder.with(element);
            }
            @Override public void atEnd(ClassBuilder builder) {
                builder.withMethodBody("handleCustomPacket", RECEIVER_TYPE, handlers.getFirst().flags().flagsMask(), cb -> {
                    var otherPacket = cb.newLabel();
                    cb.aload(2).instanceOf(UPDATE).ifeq(otherPacket)
                            .aload(1).aload(2).checkcast(UPDATE)
                            .invokestatic(UTILS, HELPER, HELPER_TYPE)
                            .return_().labelBinding(otherPacket)
                            .aload(0).aload(1).aload(2).invokespecial(UTILS, OTHER_PACKETS, RECEIVER_TYPE).return_();
                });
                builder.withMethodBody(HELPER, HELPER_TYPE, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                        GeyserUtilsEntityPropertyValidationFix::guardedUpdate);
            }
        });
        if (changed[0] != 1) throw new IllegalStateException("Expected exactly one changed receiver body");
        return result;
    }

    /** Symbolic bytecode for the accompanying source patch; only accepted updates mutate/flush state. */
    private static void guardedUpdate(CodeBuilder cb) {
        // Locals: 0 session, 1 packet, 2 entity, 3 manager, 4 declarations,
        //         5 canonical identifier, 6 index, 7 declared property, 8 value.
        var done = cb.newLabel();
        var parseStart = cb.newLabel();
        var parseEnd = cb.newLabel();
        var parseFailed = cb.newLabel();
        var parsed = cb.newLabel();
        var integerValue = cb.newLabel();
        var accepted = cb.newLabel();

        cb.aload(0).invokevirtual(SESSION, "getEntityCache", MethodTypeDesc.of(CACHE))
                .aload(1).invokevirtual(UPDATE, "getEntityId", MethodTypeDesc.of(CD_int))
                .invokevirtual(CACHE, "getEntityByJavaId", MethodTypeDesc.of(ENTITY, CD_int))
                .astore(2).aload(2).ifnull(done)
                .aload(1).invokevirtual(UPDATE, "getIdentifier", MethodTypeDesc.of(CD_String)).ifnull(done)
                .aload(1).invokevirtual(UPDATE, "getValue", MethodTypeDesc.of(CD_Object))
                .astore(8).aload(8).ifnull(done)
                .aload(2).invokevirtual(ENTITY, "getPropertyManager", MethodTypeDesc.of(MANAGER))
                .astore(3).aload(3).ifnull(done)
                .aload(2).invokevirtual(ENTITY, "bedrockDefinition", MethodTypeDesc.of(DEFINITION))
                .invokevirtual(DEFINITION, "registeredProperties", MethodTypeDesc.of(PROPERTIES))
                .astore(4).aload(4).ifnull(done)
                .aload(4).invokevirtual(PROPERTIES, "isEmpty", MethodTypeDesc.of(CD_boolean)).ifne(done);

        // Preserve the original Identifier.of normalization (foo -> minecraft:foo).
        cb.labelBinding(parseStart)
                .aload(1).invokevirtual(UPDATE, "getIdentifier", MethodTypeDesc.of(CD_String))
                .invokestatic(IDENTIFIER, "of", MethodTypeDesc.of(IDENTIFIER, CD_String), true)
                .invokevirtual(CD_Object, "toString", MethodTypeDesc.of(CD_String))
                .astore(5).labelBinding(parseEnd).goto_(parsed)
                .labelBinding(parseFailed).pop().return_()
                .labelBinding(parsed)
                .exceptionCatch(parseStart, parseEnd, parseFailed, INVALID_IDENTIFIER);

        cb.aload(4).aload(5).invokevirtual(PROPERTIES, "getPropertyIndex", MethodTypeDesc.of(CD_int, CD_String))
                .istore(6).iload(6).iflt(done)
                .aload(4).invokevirtual(PROPERTIES, "getProperties", MethodTypeDesc.of(LIST))
                .iload(6).invokeinterface(LIST, "get", MethodTypeDesc.of(CD_Object, CD_int))
                .checkcast(PROPERTY).astore(7)
                .aload(8).instanceOf(CD_Boolean).ifeq(integerValue)
                .aload(7).instanceOf(BOOL_PROPERTY).ifeq(done)
                .aload(3).aload(7).aload(8)
                .invokevirtual(MANAGER, "addProperty", MethodTypeDesc.of(CD_void, PROPERTY, CD_Object))
                .goto_(accepted)
                .labelBinding(integerValue)
                .aload(8).instanceOf(CD_Integer).ifeq(done)
                .aload(7).instanceOf(INT_PROPERTY).ifeq(done)
                .aload(3).aload(7).aload(8)
                .invokevirtual(MANAGER, "addProperty", MethodTypeDesc.of(CD_void, PROPERTY, CD_Object))
                .labelBinding(accepted)
                .aload(2).invokevirtual(ENTITY, "updateBedrockEntityProperties", MethodTypeDesc.of(CD_void))
                .labelBinding(done).return_();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

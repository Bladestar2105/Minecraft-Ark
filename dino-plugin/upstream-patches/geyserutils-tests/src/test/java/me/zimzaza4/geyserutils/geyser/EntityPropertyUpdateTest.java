package me.zimzaza4.geyserutils.geyser;

import me.zimzaza4.geyserutils.common.packet.CustomPayloadPacket;
import me.zimzaza4.geyserutils.common.packet.CustomEntityDataPacket;
import me.zimzaza4.geyserutils.common.packet.EntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.data.entity.IntEntityProperty;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.properties.GeyserEntityProperties;
import org.geysermc.geyser.entity.properties.GeyserEntityPropertyManager;
import org.geysermc.geyser.entity.properties.type.BooleanProperty;
import org.geysermc.geyser.entity.properties.type.IntProperty;
import org.geysermc.geyser.entity.properties.type.PropertyType;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.impl.IdentifierImpl;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Exercises the actual receiver and real property manager without a server or network. */
class EntityPropertyUpdateTest {
    private static final int ENTITY_ID = 42;
    private final IntProperty animation = new IntProperty(IdentifierImpl.parse("modelengine:anim0"), 100, 0, 0);
    private final BooleanProperty tint = new BooleanProperty(IdentifierImpl.parse("modelengine:has_tint"), false);
    private final IntProperty defaultNamespace = new IntProperty(IdentifierImpl.parse("minecraft:custom_flag"), 100, 0, 0);
    private final List<IntEntityProperty> sent = new ArrayList<>();
    private GeyserSession session;
    private Entity entity;
    private BedrockEntityDefinition definition;
    private GeyserEntityProperties registered;
    private GeyserEntityPropertyManager manager;
    private GeyserUtils extension;
    private Method receive;
    private MockedStatic<Identifier> identifiers;

    @BeforeEach
    void setUp() throws Exception {
        // Identifier.of normally resolves the running platform's provider.
        // Only that factory is substituted; identifiers and property state remain real.
        identifiers = mockStatic(Identifier.class);
        identifiers.when(() -> Identifier.of(anyString()))
                .thenAnswer(call -> IdentifierImpl.parse(call.getArgument(0)));
        registered = definitions(animation, tint, defaultNamespace);
        manager = new GeyserEntityPropertyManager(registered);
        manager.applyIntProperties(new ArrayList<>()); // initial defaults precede the tested update
        session = mock(GeyserSession.class);
        EntityCache cache = mock(EntityCache.class);
        entity = mock(Entity.class);
        definition = mock(BedrockEntityDefinition.class);
        when(session.getEntityCache()).thenReturn(cache);
        when(cache.getEntityByJavaId(ENTITY_ID)).thenReturn(entity);
        when(entity.getPropertyManager()).thenReturn(manager);
        when(entity.bedrockDefinition()).thenReturn(definition);
        when(definition.registeredProperties()).thenReturn(registered);
        doAnswer(call -> {
            manager.applyIntProperties(sent);
            return null;
        }).when(entity).updateBedrockEntityProperties();
        extension = new GeyserUtils();
        receive = GeyserUtils.class.getDeclaredMethod("handleCustomPacket", GeyserSession.class, CustomPayloadPacket.class);
        receive.setAccessible(true);
    }

    @AfterEach
    void closeFactory() {
        if (identifiers != null) identifiers.close();
    }

    @Test
    void undeclaredBonePropertyDoesNotCreateAnUpdate() throws Exception {
        receive("modelengine:bone0", 0);
        assertEquals(List.of(), sent, "an undeclared property must not reach the send boundary");
        assertFalse(manager.hasProperties());
    }

    @Test
    void booleanCannotUpdateAnIntegerProperty() throws Exception {
        receive("modelengine:anim0", true);
        assertEquals(List.of(), sent);
        assertFalse(manager.hasProperties());
    }

    @Test
    void integerCannotUpdateABooleanProperty() throws Exception {
        receive("modelengine:has_tint", 7);
        assertEquals(List.of(), sent);
        assertFalse(manager.hasProperties());
    }

    @Test
    void registeredIntegerKeepsItsDeclaredIndex() throws Exception {
        receive("modelengine:anim0", 7);
        assertEquals(List.of(new IntEntityProperty(0, 7)), sent);
    }

    @Test
    void registeredBooleanKeepsItsDeclaredIndex() throws Exception {
        receive("modelengine:has_tint", true);
        assertEquals(List.of(new IntEntityProperty(1, 1)), sent);
    }

    @Test
    void unqualifiedIdentifierKeepsTheDefaultNamespace() throws Exception {
        receive("custom_flag", 7);
        assertEquals(List.of(new IntEntityProperty(2, 7)), sent);
    }

    @Test
    void malformedIdentifierPreservesOtherPendingChanges() throws Exception {
        manager.addProperty(animation, 9);
        receive("invalid identifier!", 0);
        assertEquals(List.of(), sent);
        List<IntEntityProperty> pending = new ArrayList<>();
        manager.applyIntProperties(pending);
        assertEquals(List.of(new IntEntityProperty(0, 9)), pending);
    }

    @Test
    void rejectedUpdatePreservesOtherPendingChanges() throws Exception {
        manager.addProperty(animation, 9);
        receive("modelengine:bone0", 0);
        assertEquals(List.of(), sent, "rejection must not flush another update");
        List<IntEntityProperty> pending = new ArrayList<>();
        manager.applyIntProperties(pending);
        assertEquals(List.of(new IntEntityProperty(0, 9)), pending);
    }

    @Test
    void absentDefinitionsAreIgnored() throws Exception {
        when(definition.registeredProperties()).thenReturn(null);
        receive("modelengine:bone0", 0);
        assertEquals(List.of(), sent);
    }

    @Test
    void emptyDefinitionsAreIgnored() throws Exception {
        when(definition.registeredProperties()).thenReturn(new GeyserEntityProperties());
        receive("modelengine:bone0", 0);
        assertEquals(List.of(), sent);
    }

    @Test
    void nullIdentifierAndNullValueAreIgnored() throws Exception {
        receive(null, 0);
        receive("modelengine:anim0", null);
        assertEquals(List.of(), sent);
        assertFalse(manager.hasProperties());
    }

    @Test
    void otherPacketTypesKeepTheirOriginalHandler() throws Exception {
        receive.invoke(extension, session,
                new CustomEntityDataPacket(ENTITY_ID, 2.0f, null, null, null, null));
        verify(entity).setBoundingBoxHeight(2.0f);
        verify(entity).updateBedrockMetadata();
        assertEquals(List.of(), sent);
    }

    private void receive(String name, Object value) throws Exception {
        try {
            receive.invoke(extension, session, new EntityPropertyPacket<>(ENTITY_ID, name, value));
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    private static GeyserEntityProperties definitions(PropertyType<?, ?>... declared) throws Exception {
        // Bootstrap fixture: the ordinary builder requires the running Geyser registry lifecycle.
        // Derive collection classes from the fields to support both standalone and shaded Spigot jars.
        GeyserEntityProperties result = new GeyserEntityProperties();
        Field valuesField = GeyserEntityProperties.class.getDeclaredField("properties");
        valuesField.setAccessible(true);
        List<PropertyType<?, ?>> values = (List<PropertyType<?, ?>>) valuesField.getType().getDeclaredConstructor().newInstance();
        values.addAll(List.of(declared));
        valuesField.set(result, values);
        Field indicesField = GeyserEntityProperties.class.getDeclaredField("propertyIndices");
        indicesField.setAccessible(true);
        Class<?> mapType = Class.forName(indicesField.getType().getName().replace("Object2IntMap", "Object2IntOpenHashMap"));
        Map<String, Integer> indices = (Map<String, Integer>) mapType.getDeclaredConstructor().newInstance();
        for (int i = 0; i < declared.length; i++) indices.put(declared[i].identifier().toString(), i);
        indicesField.set(result, indices);
        return result;
    }
}

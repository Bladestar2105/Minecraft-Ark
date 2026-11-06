package de.ark.panel;

public final class OptionalRuntimeProvider implements RuntimeSettingsTest.StableRuntimeApi {
    public RuntimeSettingsTest.StableRuntimeApi config() { return this; }
    public String title() { return "Public loaded value"; }
    public MissingOptionalDependency optionalApi() { return null; }
}

final class MissingOptionalDependency { }

package com.qualflare.junit5;

/** Set by the build; the fallback is what a source checkout reports. */
final class Version {
    static final String VALUE = readVersion();
    private Version() {}

    private static String readVersion() {
        Package p = Version.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null ? "0.0.0-dev" : v;
    }
}

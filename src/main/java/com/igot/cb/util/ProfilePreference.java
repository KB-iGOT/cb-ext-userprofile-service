package com.igot.cb.util;

public enum ProfilePreference {

    PUBLIC("public", 0),
    PRIVATE_NO_ONE("private no one", 1),
    PRIVATE_CONNECTIONS("private connections", 10);

    private final String name;
    private final int value;

    ProfilePreference(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public static ProfilePreference fromValue(int value) {
        for (ProfilePreference pref : values()) {
            if (pref.getValue() == value) {
                return pref;
            }
        }
        return null; // or throw exception if strict handling needed
    }
}

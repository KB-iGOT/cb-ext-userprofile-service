package com.igot.cb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfilePreferenceTest {

    @Test
    void testPublicEnumValues() {
        assertEquals("public", ProfilePreference.PUBLIC.getName());
        assertEquals(0, ProfilePreference.PUBLIC.getValue());
    }

    @Test
    void testPrivateNoOneEnumValues() {
        assertEquals("private no one", ProfilePreference.PRIVATE_NO_ONE.getName());
        assertEquals(1, ProfilePreference.PRIVATE_NO_ONE.getValue());
    }

    @Test
    void testPrivateConnectionsEnumValues() {
        assertEquals("private connections", ProfilePreference.PRIVATE_CONNECTIONS.getName());
        assertEquals(10, ProfilePreference.PRIVATE_CONNECTIONS.getValue());
    }

    @Test
    void testFromValueWithPublic() {
        ProfilePreference result = ProfilePreference.fromValue(0);
        assertNotNull(result);
        assertEquals(ProfilePreference.PUBLIC, result);
    }

    @Test
    void testFromValueWithPrivateNoOne() {
        ProfilePreference result = ProfilePreference.fromValue(1);
        assertNotNull(result);
        assertEquals(ProfilePreference.PRIVATE_NO_ONE, result);
    }

    @Test
    void testFromValueWithPrivateConnections() {
        ProfilePreference result = ProfilePreference.fromValue(10);
        assertNotNull(result);
        assertEquals(ProfilePreference.PRIVATE_CONNECTIONS, result);
    }

    @Test
    void testFromValueWithInvalidValue() {
        ProfilePreference result = ProfilePreference.fromValue(999);
        assertNull(result);
    }

    @Test
    void testFromValueWithNegativeValue() {
        ProfilePreference result = ProfilePreference.fromValue(-1);
        assertNull(result);
    }

    @Test
    void testEnumValues() {
        ProfilePreference[] values = ProfilePreference.values();
        assertEquals(3, values.length);
        assertEquals(ProfilePreference.PUBLIC, values[0]);
        assertEquals(ProfilePreference.PRIVATE_NO_ONE, values[1]);
        assertEquals(ProfilePreference.PRIVATE_CONNECTIONS, values[2]);
    }

    @Test
    void testEnumValueOf() {
        assertEquals(ProfilePreference.PUBLIC, ProfilePreference.valueOf("PUBLIC"));
        assertEquals(ProfilePreference.PRIVATE_NO_ONE, ProfilePreference.valueOf("PRIVATE_NO_ONE"));
        assertEquals(ProfilePreference.PRIVATE_CONNECTIONS, ProfilePreference.valueOf("PRIVATE_CONNECTIONS"));
    }

    @Test
    void testEnumValueOfWithInvalidName() {
        assertThrows(IllegalArgumentException.class, () -> {
            ProfilePreference.valueOf("INVALID_NAME");
        });
    }

    @Test
    void testEnumToString() {
        assertEquals("PUBLIC", ProfilePreference.PUBLIC.toString());
        assertEquals("PRIVATE_NO_ONE", ProfilePreference.PRIVATE_NO_ONE.toString());
        assertEquals("PRIVATE_CONNECTIONS", ProfilePreference.PRIVATE_CONNECTIONS.toString());
    }

    @Test
    void testEnumEquality() {
        ProfilePreference pref1 = ProfilePreference.PUBLIC;
        ProfilePreference pref2 = ProfilePreference.PUBLIC;
        ProfilePreference pref3 = ProfilePreference.PRIVATE_NO_ONE;

        assertEquals(pref1, pref2);
        assertNotEquals(pref1, pref3);
        assertSame(pref1, pref2);
    }

    @Test
    void testEnumComparison() {
        assertTrue(ProfilePreference.PUBLIC.ordinal() < ProfilePreference.PRIVATE_NO_ONE.ordinal());
        assertTrue(ProfilePreference.PRIVATE_NO_ONE.ordinal() < ProfilePreference.PRIVATE_CONNECTIONS.ordinal());
    }

    @Test
    void testGetNameReturnsCorrectValue() {
        assertNotNull(ProfilePreference.PUBLIC.getName());
        assertNotNull(ProfilePreference.PRIVATE_NO_ONE.getName());
        assertNotNull(ProfilePreference.PRIVATE_CONNECTIONS.getName());
    }

    @Test
    void testGetValueReturnsCorrectValue() {
        assertTrue(ProfilePreference.PUBLIC.getValue() >= 0);
        assertTrue(ProfilePreference.PRIVATE_NO_ONE.getValue() >= 0);
        assertTrue(ProfilePreference.PRIVATE_CONNECTIONS.getValue() >= 0);
    }

    @Test
    void testUniqueValues() {
        int publicValue = ProfilePreference.PUBLIC.getValue();
        int privateNoOneValue = ProfilePreference.PRIVATE_NO_ONE.getValue();
        int privateConnectionsValue = ProfilePreference.PRIVATE_CONNECTIONS.getValue();

        assertNotEquals(publicValue, privateNoOneValue);
        assertNotEquals(publicValue, privateConnectionsValue);
        assertNotEquals(privateNoOneValue, privateConnectionsValue);
    }

    @Test
    void testFromValueConsistency() {
        for (ProfilePreference pref : ProfilePreference.values()) {
            assertEquals(pref, ProfilePreference.fromValue(pref.getValue()));
        }
    }

    @Test
    void testEnumNotNull() {
        assertNotNull(ProfilePreference.PUBLIC);
        assertNotNull(ProfilePreference.PRIVATE_NO_ONE);
        assertNotNull(ProfilePreference.PRIVATE_CONNECTIONS);
    }
}

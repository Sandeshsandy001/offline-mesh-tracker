package com.offlinetracker.protocol

import org.junit.Assert.*
import org.junit.Test

class DeviceIdGeneratorTest {

    @Test
    fun testGenerateFromSimpleName() {
        assertEquals("DEV_MOM", DeviceIdGenerator.generateFromDisplayName("Mom"))
        assertEquals("DEV_DAD", DeviceIdGenerator.generateFromDisplayName("Dad"))
        assertEquals("DEV_SISTER", DeviceIdGenerator.generateFromDisplayName("Sister"))
    }

    @Test
    fun testGenerateFromCompoundOrSpecialNames() {
        assertEquals("DEV_MY_PHONE", DeviceIdGenerator.generateFromDisplayName("My Phone"))
        assertEquals("DEV_UNCLE_BOB", DeviceIdGenerator.generateFromDisplayName("Uncle Bob!"))
        assertEquals("DEV_JOHN_DOE", DeviceIdGenerator.generateFromDisplayName("John-Doe"))
    }

    @Test
    fun testGenerateAvoidsCollisions() {
        val existing = listOf("DEV_MOM", "DEV_DAD", "DEV_01")
        val newMom = DeviceIdGenerator.generateFromDisplayName("Mom", existing)
        assertEquals("DEV_MOM_2", newMom)

        val existingWith2 = listOf("DEV_MOM", "DEV_MOM_2")
        val thirdMom = DeviceIdGenerator.generateFromDisplayName("Mom", existingWith2)
        assertEquals("DEV_MOM_3", thirdMom)
    }

    @Test
    fun testBlankNameFallback() {
        assertEquals("DEV_MEMBER", DeviceIdGenerator.generateFromDisplayName(null))
        assertEquals("DEV_MEMBER", DeviceIdGenerator.generateFromDisplayName(""))
        assertEquals("DEV_MEMBER", DeviceIdGenerator.generateFromDisplayName("   "))
        assertEquals("DEV_MEMBER_2", DeviceIdGenerator.generateFromDisplayName("", listOf("DEV_MEMBER")))
    }

    @Test
    fun testIsConflict() {
        val existing = listOf("DEV_01", "DEV_MOM", "DEV_DAD")
        assertTrue(DeviceIdGenerator.isConflict("dev_mom", existing))
        assertTrue(DeviceIdGenerator.isConflict("DEV_01", existing))
        assertFalse(DeviceIdGenerator.isConflict("DEV_SIS", existing))
        assertFalse(DeviceIdGenerator.isConflict(null, existing))
    }

    @Test
    fun testResolveConflict() {
        val existing = listOf("DEV_01", "DEV_02")
        assertEquals("DEV_01_2", DeviceIdGenerator.resolveConflict("DEV_01", existing))
        assertEquals("DEV_NEW", DeviceIdGenerator.resolveConflict("DEV_NEW", existing))
    }
}

package com.offlinetracker.protocol

import org.junit.Assert.*
import org.junit.Test

class MeshProtocolParserTest {

    @Test
    fun testBuildRequestFormat() {
        val payload = MeshProtocolParser.buildRequest("FAM_01", "DEV_01", "DEV_02", "SECRET_KEY")
        assertEquals("FAM_01|TYPE:REQ|FROM:DEV_01|TO:DEV_02|AUTH:SECRET_KEY", payload)
    }

    @Test
    fun testBuildResponseFormat() {
        val payload = MeshProtocolParser.buildResponse(
            familyId = "FAM_01",
            from = "DEV_02",
            to = "DEV_01",
            lat = 27.678412,
            lon = 84.431290,
            acc = 6.4f,
            bat = 84,
            ts = 1725201300000L
        )
        val expected = "FAM_01|TYPE:RESP|FROM:DEV_02|TO:DEV_01|LAT:27.678412|LON:84.431290|ACC:6|BAT:84|TS:1725201300000"
        assertEquals(expected, payload)
    }

    @Test
    fun testParseValidRequest() {
        val raw = "FAM_01|TYPE:REQ|FROM:DEV_01|TO:DEV_02|AUTH:SECRET_KEY"
        val parsed = MeshProtocolParser.parse(raw)

        assertNotNull(parsed)
        assertTrue(parsed is MeshMessage.Request)

        val request = parsed as MeshMessage.Request
        assertEquals("FAM_01", request.familyId)
        assertEquals("DEV_01", request.fromDeviceId)
        assertEquals("DEV_02", request.toDeviceId)
        assertEquals("SECRET_KEY", request.authKey)
    }

    @Test
    fun testParseValidResponse() {
        val raw = "FAM_01|TYPE:RESP|FROM:DEV_02|TO:DEV_01|LAT:27.678412|LON:84.431290|ACC:6|BAT:84|TS:1725201300000"
        val parsed = MeshProtocolParser.parse(raw)

        assertNotNull(parsed)
        assertTrue(parsed is MeshMessage.Response)

        val response = parsed as MeshMessage.Response
        assertEquals("FAM_01", response.familyId)
        assertEquals("DEV_02", response.fromDeviceId)
        assertEquals("DEV_01", response.toDeviceId)
        assertEquals(27.678412, response.latitude, 0.000001)
        assertEquals(84.431290, response.longitude, 0.000001)
        assertEquals(6f, response.accuracy, 0.01f)
        assertEquals(84, response.battery)
        assertEquals(1725201300000L, response.timestamp)
    }

    @Test
    fun testParseInvalidOrCorruptData() {
        assertNull(MeshProtocolParser.parse(null))
        assertNull(MeshProtocolParser.parse(""))
        assertNull(MeshProtocolParser.parse("FAM_01|TYPE:REQ"))
        assertNull(MeshProtocolParser.parse("FAM_01|TYPE:UNKNOWN|FROM:D1|TO:D2"))
        assertNull(MeshProtocolParser.parse("FAM_01|TYPE:RESP|FROM:D1|TO:D2|LAT:invalid|LON:84.4"))
    }
}

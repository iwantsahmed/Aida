package com.rayluxeo.aida.domain.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BusinessCardParserTest {

    @Test
    fun `parse business card extracts name company email phone website and address`() {
        val ocrText = """
            Abu Sayed Ahmed
            Founder & CEO
            RayLuxeo Technologies Ltd.
            
            Phone: +8801712345678
            Email: abu@rayluxeo.com
            Website: www.rayluxeo.com
            Address: House 12, Road 5, Block D, Gulshan, Dhaka, Bangladesh
        """.trimIndent()

        val parsed = BusinessCardParser.parse(ocrText)

        assertThat(parsed.name).isEqualTo("Abu Sayed Ahmed")
        assertThat(parsed.company).isEqualTo("RayLuxeo Technologies Ltd.")
        assertThat(parsed.email).isEqualTo("abu@rayluxeo.com")
        assertThat(parsed.phone).contains("8801712345678")
        assertThat(parsed.website).contains("rayluxeo.com")
        assertThat(parsed.address).contains("Gulshan")
    }
}

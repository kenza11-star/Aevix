package app.absentmotion.library

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PresetSourceTest {
    @Test fun pastedXmlAndCodeBlocksRetainTheScene() {
        val xml = "<scene width=\"720\" height=\"1280\"><text id=\"1\">Hello</text></scene>"
        assertEquals(xml, (PresetSource.parse("\uFEFF  $xml  ") as PresetSource.Xml).text)
        assertEquals(xml, (PresetSource.parse("```xml\n$xml\n```") as PresetSource.Xml).text)
    }

    @Test fun directDownloadKeepsEncodedPathsAndQueryParameters() {
        val source = PresetSource.parse("https://example.com/my%20preset.xml?download=1") as PresetSource.Download
        assertEquals("https://example.com/my%20preset.xml?download=1", source.url.toASCIIString())
    }

    @Test fun oldAlightLinksAreUpgradedToHttps() {
        val source = PresetSource.parse("http://alight.link/ExampleCode") as PresetSource.Download
        assertEquals("https://alight.link/ExampleCode", source.url.toString())
    }

    @Test fun invalidAndUnsafeInputsNeverBecomeDownloads() {
        for ((input, problem) in listOf("" to PresetSourceProblem.Empty, "not xml" to PresetSourceProblem.Invalid,
            "file:///data/user/0/private.xml" to PresetSourceProblem.Insecure, "http://example.com/a.xml" to PresetSourceProblem.Insecure,
            "https://user:password@example.com/a.xml" to PresetSourceProblem.Insecure)) {
            try {
                PresetSource.parse(input)
                fail("Accepted unsafe or invalid input")
            } catch (expected: PresetSourceException) {
                assertEquals(problem, expected.problem)
            }
        }
    }

    @Test fun oversizedPastedXmlIsRejectedBeforeParsing() {
        try {
            PresetSource.parse("<scene>" + "x".repeat(16 * 1024 * 1024) + "</scene>")
            fail("Accepted oversized XML")
        } catch (expected: PresetSourceException) {
            assertEquals(PresetSourceProblem.TooLarge, expected.problem)
        }
    }
}

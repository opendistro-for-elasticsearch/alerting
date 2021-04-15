package com.amazon.opendistroforelasticsearch.alerting.util

import org.elasticsearch.test.ESTestCase

class SupportedApiSettingsExtensionsTests : ESTestCase() {
    private var expectedResponse = hashMapOf<String, Any>()
    private var mappedResponse = hashMapOf<String, Any>()
    private var supportedJsonPayload = hashMapOf<String, Set<String>>()

    fun `test redactFieldsFromResponse with non-empty supportedJsonPayload`() {
        // GIVEN
        mappedResponse = hashMapOf(
            ("pathRoot1" to hashMapOf(
                ("pathRoot1_subPath1" to 11),
                ("pathRoot1_subPath2" to hashMapOf(
                    ("pathRoot1_subPath2_subPath1" to 121),
                    ("pathRoot1_subPath2_subPath2" to hashMapOf(
                        ("pathRoot1_subPath2_subPath2_subPath1" to 1221)
                    ))
                ))
            )),
            ("pathRoot2" to hashMapOf(
                ("pathRoot2_subPath1" to 21),
                ("pathRoot2_subPath2" to setOf(221, 222, "223string"))
            )),
            ("pathRoot3" to 3))

        supportedJsonPayload = hashMapOf(
            ("pathRoot1" to setOf(
                "pathRoot1_subPath1",
                "pathRoot1_subPath2.pathRoot1_subPath2_subPath2.pathRoot1_subPath2_subPath2_subPath1"
            )),
            ("pathRoot2" to setOf(
                "pathRoot2_subPath2"
            )))

        expectedResponse = hashMapOf(
            ("pathRoot1" to hashMapOf(
                ("pathRoot1_subPath1" to 11),
                ("pathRoot1_subPath2" to hashMapOf(
                    ("pathRoot1_subPath2_subPath2" to hashMapOf(
                        ("pathRoot1_subPath2_subPath2_subPath1" to 1221)
                    ))
                ))
            )),
            ("pathRoot2" to hashMapOf(
                ("pathRoot2_subPath2" to setOf(221, 222, "223string"))
            )))

        // WHEN
        val result = redactFieldsFromResponse(mappedResponse, supportedJsonPayload)

        // THEN
        assertEquals(expectedResponse, result)
    }

    fun `test redactFieldsFromResponse with empty supportedJsonPayload`() {
        // GIVEN
        mappedResponse = hashMapOf(
            ("pathRoot1" to hashMapOf(
                ("pathRoot1_subPath1" to 11),
                ("pathRoot1_subPath2" to hashMapOf(
                    ("pathRoot1_subPath2_subPath1" to 121),
                    ("pathRoot1_subPath2_subPath2" to hashMapOf(
                        ("pathRoot1_subPath2_subPath2_subPath1" to 1221)
                    ))
                ))
            )),
            ("pathRoot2" to hashMapOf(
                ("pathRoot2_subPath1" to 21),
                ("pathRoot2_subPath2" to setOf(221, 222, "223string"))
            )),
            ("pathRoot3" to 3))

        expectedResponse = hashMapOf(
            ("pathRoot1" to hashMapOf(
                ("pathRoot1_subPath1" to 11),
                ("pathRoot1_subPath2" to hashMapOf(
                    ("pathRoot1_subPath2_subPath1" to 121),
                    ("pathRoot1_subPath2_subPath2" to hashMapOf(
                        ("pathRoot1_subPath2_subPath2_subPath1" to 1221)
                    ))
                ))
            )),
            ("pathRoot2" to hashMapOf(
                ("pathRoot2_subPath1" to 21),
                ("pathRoot2_subPath2" to setOf(221, 222, "223string"))
            )),
            ("pathRoot3" to 3))

        // WHEN
        val result = redactFieldsFromResponse(mappedResponse, supportedJsonPayload)

        // THEN
        assertEquals(expectedResponse, result)
    }
}
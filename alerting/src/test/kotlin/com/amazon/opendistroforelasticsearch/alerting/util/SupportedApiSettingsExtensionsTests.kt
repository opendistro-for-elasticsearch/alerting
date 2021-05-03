package com.amazon.opendistroforelasticsearch.alerting.util

import org.elasticsearch.test.ESTestCase

class SupportedApiSettingsExtensionsTests : ESTestCase() {
    private var expectedResponse = hashMapOf<String, Any>()
    private var mappedResponse = hashMapOf<String, Any>()
    private var supportedJsonPayload = hashMapOf<String, ArrayList<String>>()

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
            ("pathRoot3" to hashMapOf(
                ("pathRoot3_subPath1" to 31),
                ("pathRoot3_subPath2" to setOf(321, 322, "323string"))
            )))

        supportedJsonPayload = hashMapOf(
            ("pathRoot1" to arrayListOf(
                "pathRoot1_subPath1",
                "pathRoot1_subPath2.pathRoot1_subPath2_subPath2.pathRoot1_subPath2_subPath2_subPath1"
            )),
            ("pathRoot2" to arrayListOf(
                "pathRoot2_subPath2"
            )),
            ("pathRoot3" to arrayListOf()))

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
            )),
            ("pathRoot3" to hashMapOf(
                ("pathRoot3_subPath1" to 31),
                ("pathRoot3_subPath2" to setOf(321, 322, "323string"))
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
package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.ALERTING_BASE_URI
import com.amazon.opendistroforelasticsearch.alerting.ALWAYS_RUN
import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.DRYRUN_MONITOR
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.makeRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.randomAction
import com.amazon.opendistroforelasticsearch.alerting.randomAlert
import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import com.amazon.opendistroforelasticsearch.alerting.randomTemplateScript
import com.amazon.opendistroforelasticsearch.alerting.randomTrigger
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import com.amazon.opendistroforelasticsearch.commons.rest.SecureRestClientBuilder
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.junit.annotations.TestLogging
import org.junit.After
import org.junit.Before

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class SecureMonitorRestApiIT : AlertingRestTestCase() {

    val user = "userOne"
    var userClient: RestClient? = null

    @Before
    fun create() {
        if (!securityEnabled()) return

        if (userClient == null) {
            createUser(user, user, arrayOf())
            userClient = SecureRestClientBuilder(clusterHosts.toTypedArray(), isHttps(), user, user).setSocketTimeout(60000).build()
        }
    }

    @After
    fun cleanup() {
        if (!securityEnabled()) return

        userClient?.close()
        deleteUser(user)
    }

    // Create Monitor related security tests

    fun `test create monitor with an user with alerting role`() {
        if (!securityEnabled()) return

        createUserWithTestData(user, "hr_data", "hr_role", "HR")
        createUserRolesMapping("alerting_full_access", arrayOf(user))
        try {
            // randomMonitor has a dummy user, api ignores the User passed as part of monitor, it picks user info from the logged-in user.
            val monitor = randomMonitor().copy(
                    inputs = listOf(SearchInput(
                            indices = listOf("hr_data"), query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))))
            val createResponse = userClient?.makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
            assertEquals("Create monitor failed", RestStatus.CREATED, createResponse?.restStatus())

            // val newMonitor = getMonitor(userClient as RestClient, monitor.id)
            val responseBody = createResponse?.asMap()
            val monitorMap = responseBody!!["monitor"] as HashMap<String, Any>
            val userMap = monitorMap["user"] as HashMap<String, Any>
            assertEquals("User is not present", user, userMap["name"])

            val brolesArray = userMap["backend_roles"] as ArrayList<String>
            assertTrue(brolesArray.contains("HR"))

            val rolesArray = userMap["roles"] as ArrayList<String>
            assertTrue(rolesArray.contains("hr_role"))
            assertTrue(rolesArray.contains("alerting_full_access"))
        } finally {
            deleteRoleMapping("hr_role")
            deleteRole("hr_role")
            deleteRoleMapping("alerting_full_access")
        }
    }

    fun `test create monitor with an user without alerting role`() {
        if (!securityEnabled()) return

        createUserWithTestData(user, "hr_data", "hr_role", "HR")
        try {
            val monitor = randomMonitor().copy(
                    inputs = listOf(SearchInput(
                            indices = listOf("hr_data"), query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))))
            userClient?.makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
            fail("Expected 403 Method FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        } finally {
            deleteRoleMapping("hr_role")
            deleteRole("hr_role")
        }
    }

    fun `test create monitor with an user without index read role`() {
        if (!securityEnabled()) return

        createUserWithTestData(user, "hr_data", "hr_role", "HR")
        createUserRolesMapping("alerting_full_access", arrayOf(user))
        try {
            val monitor = randomMonitor().copy(
                    inputs = listOf(SearchInput(
                            indices = listOf("not_hr_data"), query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))))
            val createResponse = userClient?.makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
            assertEquals("Create monitor failed", RestStatus.CREATED, createResponse?.restStatus())
            fail("Expected 403 Method FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        } finally {
            deleteRoleMapping("hr_role")
            deleteRole("hr_role")
            deleteRoleMapping("alerting_full_access")
        }
    }

    fun `test create monitor with disable filter by`() {
        disableFilterBy()
        val monitor = randomMonitor()
        val createResponse = client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
        assertEquals("Create monitor failed", RestStatus.CREATED, createResponse.restStatus())
    }

    fun `test create monitor with enable filter by`() {
        enableFilterBy()
        val monitor = randomMonitor()

        if (securityEnabled()) {
            // when security is enabled. No errors, must succeed.
            val createResponse = client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
            assertEquals("Create monitor failed", RestStatus.CREATED, createResponse.restStatus())
        } else {
            // when security is disable. Must return Forbidden.
            try {
                client().makeRequest("POST", ALERTING_BASE_URI, emptyMap(), monitor.toHttpEntity())
                fail("Expected 403 FORBIDDEN response")
            } catch (e: ResponseException) {
                assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
            }
        }
    }

    fun getDocs(response: Response?): Any? {
        val hits = createParser(XContentType.JSON.xContent(),
                response?.entity?.content).map()["hits"]!! as Map<String, Map<String, Any>>
        return hits["total"]?.get("value")
    }

    // Query Monitors related security tests
    fun `test update monitor with disable filter by`() {
        disableFilterBy()
        val monitor = randomMonitor(enabled = true)

        val createdMonitor = createMonitor(monitor = monitor)

        assertNotNull("The monitor was not created", createdMonitor)
        assertTrue("The monitor was not enabled", createdMonitor.enabled)

        val monitorV2 = createdMonitor.copy(enabled = false, enabledTime = null)
        val updatedMonitor = updateMonitor(monitor = monitorV2)

        assertFalse("The monitor was not disabled", updatedMonitor.enabled)
    }

    fun `test update monitor with enable filter by`() {
        enableFilterBy()
        if (!isHttps()) {
            // if security is disabled and filter by is enabled, we can't create monitor
            // refer: `test create monitor with enable filter by`
            return
        }
        val monitor = randomMonitor(enabled = true)

        val createdMonitor = createMonitor(monitor = monitor)

        assertNotNull("The monitor was not created", createdMonitor)
        assertTrue("The monitor was not enabled", createdMonitor.enabled)

        val monitorV2 = createdMonitor.copy(enabled = false, enabledTime = null)
        val updatedMonitor = updateMonitor(monitor = monitorV2)

        assertFalse("The monitor was not disabled", updatedMonitor.enabled)
    }

    fun `test delete monitor with disable filter by`() {
        disableFilterBy()
        val monitor = randomMonitor(enabled = true)

        val createdMonitor = createMonitor(monitor = monitor)

        assertNotNull("The monitor was not created", createdMonitor)
        assertTrue("The monitor was not enabled", createdMonitor.enabled)

        deleteMonitor(monitor = createdMonitor)

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", createdMonitor.id)).toString()
        // search as "admin" - must get 0 docs
        val adminSearchResponse = client().makeRequest("POST",
            "$ALERTING_BASE_URI/_search",
            emptyMap(),
            NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, adminSearchResponse.restStatus())

        val adminHits = createParser(XContentType.JSON.xContent(),
            adminSearchResponse.entity.content).map()["hits"]!! as Map<String, Map<String, Any>>
        val adminDocsFound = adminHits["total"]?.get("value")
        assertEquals("Monitor found during search", 0, adminDocsFound)
    }

    fun `test delete monitor with enable filter by`() {
        enableFilterBy()
        if (!isHttps()) {
            // if security is disabled and filter by is enabled, we can't create monitor
            // refer: `test create monitor with enable filter by`
            return
        }
        val monitor = randomMonitor(enabled = true)

        val createdMonitor = createMonitor(monitor = monitor)

        assertNotNull("The monitor was not created", createdMonitor)
        assertTrue("The monitor was not enabled", createdMonitor.enabled)

        deleteMonitor(monitor = createdMonitor)

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", createdMonitor.id)).toString()
        // search as "admin" - must get 0 docs
        val adminSearchResponse = client().makeRequest("POST",
            "$ALERTING_BASE_URI/_search",
            emptyMap(),
            NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, adminSearchResponse.restStatus())

        val adminHits = createParser(XContentType.JSON.xContent(),
            adminSearchResponse.entity.content).map()["hits"]!! as Map<String, Map<String, Any>>
        val adminDocsFound = adminHits["total"]?.get("value")
        assertEquals("Monitor found during search", 0, adminDocsFound)
    }

    fun `test query monitors with disable filter by`() {
        if (!securityEnabled()) return

        disableFilterBy()

        // creates monitor as "admin" user.
        val monitor = createRandomMonitor(true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()

        // search as "admin" - must get 1 docs
        val adminSearchResponse = client().makeRequest("POST",
                "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, adminSearchResponse.restStatus())
        assertEquals("Monitor not found during search", 1, getDocs(adminSearchResponse))

        // search as userOne without alerting roles - must return 403 Forbidden
        try {
            userClient?.makeRequest("POST", "$ALERTING_BASE_URI/_search",
                    emptyMap(),
                    NStringEntity(search, ContentType.APPLICATION_JSON))
            fail("Expected 403 FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        }

        // add alerting roles and search as userOne - must return 1 docs
        createUserRolesMapping("alerting_full_access", arrayOf(user))
        try {
            val userOneSearchResponse = userClient?.makeRequest("POST",
                    "$ALERTING_BASE_URI/_search",
                    emptyMap(),
                    NStringEntity(search, ContentType.APPLICATION_JSON))
            assertEquals("Search monitor failed", RestStatus.OK, userOneSearchResponse?.restStatus())
            assertEquals("Monitor not found during search", 1, getDocs(userOneSearchResponse))
        } finally {
            deleteRoleMapping("alerting_full_access")
        }
    }

    fun `test query monitors with enable filter by`() {
        if (!securityEnabled()) return

        enableFilterBy()

        // creates monitor as "admin" user.
        val monitor = createRandomMonitor(true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()

        // search as "admin" - must get 1 docs
        val adminSearchResponse = client().makeRequest("POST",
                "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, adminSearchResponse.restStatus())
        assertEquals("Monitor not found during search", 1, getDocs(adminSearchResponse))

        // search as userOne without alerting roles - must return 403 Forbidden
        try {
            userClient?.makeRequest("POST", "$ALERTING_BASE_URI/_search",
                    emptyMap(),
                    NStringEntity(search, ContentType.APPLICATION_JSON))
            fail("Expected 403 FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        }

        // add alerting roles and search as userOne - must return 0 docs
        createUserRolesMapping("alerting_full_access", arrayOf(user))
        try {
            val userOneSearchResponse = userClient?.makeRequest("POST",
                    "$ALERTING_BASE_URI/_search",
                    emptyMap(),
                    NStringEntity(search, ContentType.APPLICATION_JSON))
            assertEquals("Search monitor failed", RestStatus.OK, userOneSearchResponse?.restStatus())
            assertEquals("Monitor not found during search", 0, getDocs(userOneSearchResponse))
        } finally {
            deleteRoleMapping("alerting_full_access")
        }
    }

    fun `test query all alerts in all states with disabled filter by`() {
        if (!securityEnabled()) return

        disableFilterBy()
        putAlertMappings()
        val monitor = createRandomMonitor(refresh = true)
        createAlert(randomAlert(monitor).copy(state = Alert.State.ACKNOWLEDGED))
        createAlert(randomAlert(monitor).copy(state = Alert.State.COMPLETED))
        createAlert(randomAlert(monitor).copy(state = Alert.State.ERROR))
        createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE))
        randomAlert(monitor).copy(id = "foobar")

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"

        // search as "admin" - must get 4 docs
        val adminResponseMap = getAlerts(client(), inputMap).asMap()
        assertEquals(4, adminResponseMap["totalAlerts"])

        // search as userOne without alerting roles - must return 403 Forbidden
        try {
            getAlerts(userClient as RestClient, inputMap).asMap()
            fail("Expected 403 FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        }

        // add alerting roles and search as userOne - must return 0 docs
        createUserRolesMapping("alerting_full_access", arrayOf(user))
        try {
            val responseMap = getAlerts(userClient as RestClient, inputMap).asMap()
            assertEquals(4, responseMap["totalAlerts"])
        } finally {
            deleteRoleMapping("alerting_full_access")
        }
    }

    fun `test query all alerts in all states with filter by`() {
        // if security is disabled and filter by is enabled, we can't create monitor
        // refer: `test create monitor with enable filter by`
        if (!securityEnabled()) return

        enableFilterBy()
        putAlertMappings()
        val monitor = createRandomMonitor(refresh = true)
        createAlert(randomAlert(monitor).copy(state = Alert.State.ACKNOWLEDGED))
        createAlert(randomAlert(monitor).copy(state = Alert.State.COMPLETED))
        createAlert(randomAlert(monitor).copy(state = Alert.State.ERROR))
        createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE))
        randomAlert(monitor).copy(id = "foobar")

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"

        // search as "admin" - must get 4 docs
        val adminResponseMap = getAlerts(client(), inputMap).asMap()
        assertEquals(4, adminResponseMap["totalAlerts"])

        // search as userOne without alerting roles - must return 403 Forbidden
        try {
            getAlerts(userClient as RestClient, inputMap).asMap()
            fail("Expected 403 FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        }

        // add alerting roles and search as userOne - must return 0 docs
        createUserRolesMapping("alerting_full_access", arrayOf(user))
        try {
            val responseMap = getAlerts(userClient as RestClient, inputMap).asMap()
            assertEquals(0, responseMap["totalAlerts"])
        } finally {
            deleteRoleMapping("alerting_full_access")
        }
    }

    // Execute Monitor related security tests

    fun `test execute monitor with elevate permissions`() {
        if (!securityEnabled()) return

        val action = randomAction(template = randomTemplateScript("Hello {{ctx.monitor.name}}"), destinationId = createDestination().id)
        val inputs = listOf(
                SearchInput(
                        indices = kotlin.collections.listOf("not_hr_data"),
                        query = SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
                )
        )
        val monitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN, actions = listOf(action))), inputs = inputs)

        // Make sure the elevating the permissions fails execute.
        val adminUser = User("admin", listOf("admin"), listOf("all_access"), listOf())
        var modifiedMonitor = monitor.copy(user = adminUser)
        createUserRolesMapping("alerting_full_access", arrayOf(user))

        try {
            val response = executeMonitor(userClient as RestClient, modifiedMonitor, params = DRYRUN_MONITOR)
            val output = entityAsMap(response)
            val inputResults = output.stringMap("input_results")
            assertTrue("Missing monitor error message", (inputResults?.get("error") as String).isNotEmpty())
            assertTrue((inputResults.get("error") as String).contains("no permissions for [indices:data/read/search]"))
        } finally {
            deleteRoleMapping("alerting_full_access")
        }
    }
}

/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */
package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.core.model.Input
import com.amazon.opendistroforelasticsearch.alerting.core.model.IntervalSchedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.Schedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.rest.ESRestTestCase
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val ANOMALY_DETECTOR_INDEX = ".opendistro-anomaly-detectors"
const val ANOMALY_RESULT_INDEX = ".opendistro-anomaly-results*"

fun anomalyDetectorIndexMapping(): String {
    return """
        "properties": {
    "schema_version": {
      "type": "integer"
    },
    "name": {
      "type": "text",
      "fields": {
        "keyword": {
          "type": "keyword",
          "ignore_above": 256
        }
      }
    },
    "description": {
      "type": "text"
    },
    "time_field": {
      "type": "keyword"
    },
    "indices": {
      "type": "text",
      "fields": {
        "keyword": {
          "type": "keyword",
          "ignore_above": 256
        }
      }
    },
    "filter_query": {
      "type": "object",
      "enabled": false
    },
    "feature_attributes": {
      "type": "nested",
      "properties": {
        "feature_id": {
          "type": "keyword",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "feature_name": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "feature_enabled": {
          "type": "boolean"
        },
        "aggregation_query": {
          "type": "object",
          "enabled": false
        }
      }
    },
    "detection_interval": {
      "properties": {
        "period": {
          "properties": {
            "interval": {
              "type": "integer"
            },
            "unit": {
              "type": "keyword"
            }
          }
        }
      }
    },
    "window_delay": {
      "properties": {
        "period": {
          "properties": {
            "interval": {
              "type": "integer"
            },
            "unit": {
              "type": "keyword"
            }
          }
        }
      }
    },
    "shingle_size": {
      "type": "integer"
    },
    "last_update_time": {
      "type": "date",
      "format": "strict_date_time||epoch_millis"
    },
    "ui_metadata": {
      "type": "object",
      "enabled": false
    },
    "user": {
      "type": "nested",
      "properties": {
        "name": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "backend_roles": {
          "type" : "text",
          "fields" : {
            "keyword" : {
              "type" : "keyword"
            }
          }
        },
        "roles": {
          "type" : "text",
          "fields" : {
            "keyword" : {
              "type" : "keyword"
            }
          }
        },
        "custom_attribute_names": {
          "type" : "text",
          "fields" : {
            "keyword" : {
              "type" : "keyword"
            }
          }
        }
      }
    },
    "category_field": {
      "type": "keyword"
    }
  }
        """
}

fun anomalyResultIndexMapping(): String {
    return """
            "properties": {
              "detector_id": {
                "type": "keyword"
              },
              "is_anomaly": {
                "type": "boolean"
              },
              "anomaly_score": {
                "type": "double"
              },
              "anomaly_grade": {
                "type": "double"
              },
              "confidence": {
                "type": "double"
              },
              "feature_data": {
                "type": "nested",
                "properties": {
                  "feature_id": {
                    "type": "keyword"
                  },
                  "data": {
                    "type": "double"
                  }
                }
              },
              "data_start_time": {
                "type": "date",
                "format": "strict_date_time||epoch_millis"
              },
              "data_end_time": {
                "type": "date",
                "format": "strict_date_time||epoch_millis"
              },
              "execution_start_time": {
                "type": "date",
                "format": "strict_date_time||epoch_millis"
              },
              "execution_end_time": {
                "type": "date",
                "format": "strict_date_time||epoch_millis"
              },
              "error": {
                "type": "text"
              },
              "user": {
                "type": "nested",
                "properties": {
                  "name": {
                    "type": "text",
                    "fields": {
                      "keyword": {
                        "type": "keyword",
                        "ignore_above": 256
                      }
                    }
                  },
                  "backend_roles": {
                    "type": "text",
                    "fields": {
                      "keyword": {
                        "type": "keyword"
                      }
                    }
                  },
                  "roles": {
                    "type": "text",
                    "fields": {
                      "keyword": {
                        "type": "keyword"
                      }
                    }
                  },
                  "custom_attribute_names": {
                    "type": "text",
                    "fields": {
                      "keyword": {
                        "type": "keyword"
                      }
                    }
                  }
                }
              },
              "entity": {
                "type": "nested",
                "properties": {
                  "name": {
                    "type": "keyword"
                  },
                  "value": {
                    "type": "keyword"
                  }
                }
              },
              "schema_version": {
                "type": "integer"
              }
            }
        """
}

fun randomAnomalyDetector(): String {
    return """{
    "name" : "${ESTestCase.randomAlphaOfLength(10)}",
    "description" : "${ESTestCase.randomAlphaOfLength(10)}",
    "time_field" : "timestamp",
    "indices" : [
      "${ESTestCase.randomAlphaOfLength(5)}"
    ],
    "filter_query" : {
      "match_all" : {
        "boost" : 1.0
      }
    },
    "detection_interval" : {
      "period" : {
        "interval" : 1,
        "unit" : "Minutes"
      }
    },
    "window_delay" : {
      "period" : {
        "interval" : 1,
        "unit" : "Minutes"
      }
    },
    "shingle_size" : 8,
    "feature_attributes" : [
      {
        "feature_name" : "F1",
        "feature_enabled" : true,
        "aggregation_query" : {
          "f_1" : {
            "sum" : {
              "field" : "value"
            }
          }
        }
      }
    ]
  }
        """.trimIndent()
}

fun randomAnomalyDetectorWithUser(backendRole: String): String {
    return """{
    "name" : "${ESTestCase.randomAlphaOfLength(5)}",
    "description" : "${ESTestCase.randomAlphaOfLength(10)}",
    "time_field" : "timestamp",
    "indices" : [
      "${ESTestCase.randomAlphaOfLength(5)}"
    ],
    "filter_query" : {
      "match_all" : {
        "boost" : 1.0
      }
    },
    "detection_interval" : {
      "period" : {
        "interval" : 1,
        "unit" : "Minutes"
      }
    },
    "window_delay" : {
      "period" : {
        "interval" : 1,
        "unit" : "Minutes"
      }
    },
    "shingle_size" : 8,
    "feature_attributes" : [
      {
        "feature_name" : "F1",
        "feature_enabled" : true,
        "aggregation_query" : {
          "f_1" : {
            "sum" : {
              "field" : "value"
            }
          }
        }
      }
    ],
    "user" : {
      "name" : "${ESTestCase.randomAlphaOfLength(5)}",
      "backend_roles" : [ "$backendRole" ],
      "roles" : [
        "${ESTestCase.randomAlphaOfLength(5)}"
      ],
      "custom_attribute_names" : [ ]
    }
  }
        """.trimIndent()
}

fun randomAnomalyResult(
    detectorId: String = ESTestCase.randomAlphaOfLength(10),
    dataStartTime: Long = ZonedDateTime.now().minus(2, ChronoUnit.MINUTES).toInstant().toEpochMilli(),
    dataEndTime: Long = ZonedDateTime.now().toInstant().toEpochMilli(),
    featureId: String = ESTestCase.randomAlphaOfLength(5),
    featureName: String = ESTestCase.randomAlphaOfLength(5),
    featureData: Double = ESTestCase.randomDouble(),
    executionStartTime: Long = ZonedDateTime.now().minus(10, ChronoUnit.SECONDS).toInstant().toEpochMilli(),
    executionEndTime: Long = ZonedDateTime.now().toInstant().toEpochMilli(),
    anomalyScore: Double = ESTestCase.randomDouble(),
    anomalyGrade: Double = ESTestCase.randomDouble(),
    confidence: Double = ESTestCase.randomDouble(),
    user: User = randomUser()
): String {
    return """{
          "detector_id" : "$detectorId",
          "data_start_time" : $dataStartTime,
          "data_end_time" : $dataEndTime,
          "feature_data" : [
            {
              "feature_id" : "$featureId",
              "feature_name" : "$featureName",
              "data" : $featureData
            }
          ],
          "execution_start_time" : $executionStartTime,
          "execution_end_time" : $executionEndTime,
          "anomaly_score" : $anomalyScore,
          "anomaly_grade" : $anomalyGrade,
          "confidence" : $confidence,
          "user" : {
            "name" : "${user.name}",
            "backend_roles" : [
              ${user.backendRoles.joinToString { "\"${it}\"" }}
            ],
            "roles" : [
              ${user.roles.joinToString { "\"${it}\"" }}
            ],
            "custom_attribute_names" : [
              ${user.customAttNames.joinToString { "\"${it}\"" }}
            ]
          }
        }
        """.trimIndent()
}

fun randomAnomalyResultWithoutUser(
    detectorId: String = ESTestCase.randomAlphaOfLength(10),
    dataStartTime: Long = ZonedDateTime.now().minus(2, ChronoUnit.MINUTES).toInstant().toEpochMilli(),
    dataEndTime: Long = ZonedDateTime.now().toInstant().toEpochMilli(),
    featureId: String = ESTestCase.randomAlphaOfLength(5),
    featureName: String = ESTestCase.randomAlphaOfLength(5),
    featureData: Double = ESTestCase.randomDouble(),
    executionStartTime: Long = ZonedDateTime.now().minus(10, ChronoUnit.SECONDS).toInstant().toEpochMilli(),
    executionEndTime: Long = ZonedDateTime.now().toInstant().toEpochMilli(),
    anomalyScore: Double = ESTestCase.randomDouble(),
    anomalyGrade: Double = ESTestCase.randomDouble(),
    confidence: Double = ESTestCase.randomDouble()
): String {
    return """{
          "detector_id" : "$detectorId",
          "data_start_time" : $dataStartTime,
          "data_end_time" : $dataEndTime,
          "feature_data" : [
            {
              "feature_id" : "$featureId",
              "feature_name" : "$featureName",
              "data" : $featureData
            }
          ],
          "execution_start_time" : $executionStartTime,
          "execution_end_time" : $executionEndTime,
          "anomaly_score" : $anomalyScore,
          "anomaly_grade" : $anomalyGrade,
          "confidence" : $confidence
        }
        """.trimIndent()
}

fun maxAnomalyGradeSearchInput(
    adResultIndex: String = ".opendistro-anomaly-results-history",
    detectorId: String = ESTestCase.randomAlphaOfLength(10),
    size: Int = 1
): SearchInput {
    val rangeQuery = QueryBuilders.rangeQuery("execution_end_time")
            .gt("{{period_end}}||-10d")
            .lte("{{period_end}}")
            .format("epoch_millis")
    val termQuery = QueryBuilders.termQuery("detector_id", detectorId)

    var boolQueryBuilder = BoolQueryBuilder()
    boolQueryBuilder.filter(rangeQuery).filter(termQuery)

    val aggregationBuilder = AggregationBuilders.max("max_anomaly_grade").field("anomaly_grade")
    val searchSourceBuilder = SearchSourceBuilder().query(boolQueryBuilder).aggregation(aggregationBuilder).size(size)
    return SearchInput(indices = listOf(adResultIndex), query = searchSourceBuilder)
}

fun adMonitorTrigger(): Trigger {
    val triggerScript = """
            return ctx.results[0].aggregations.max_anomaly_grade.value != null && 
                   ctx.results[0].aggregations.max_anomaly_grade.value > 0.7
        """.trimIndent()
    return randomTrigger(condition = Script(triggerScript))
}

fun adSearchInput(detectorId: String): SearchInput {
    return maxAnomalyGradeSearchInput(adResultIndex = ANOMALY_RESULT_INDEX, detectorId = detectorId, size = 10)
}

fun randomADMonitor(
    name: String = ESRestTestCase.randomAlphaOfLength(10),
    user: User? = randomUser(),
    inputs: List<Input> = listOf(adSearchInput("test_detector_id")),
    schedule: Schedule = IntervalSchedule(interval = 5, unit = ChronoUnit.MINUTES),
    enabled: Boolean = ESTestCase.randomBoolean(),
    triggers: List<Trigger> = (1..ESTestCase.randomInt(10)).map { randomTrigger() },
    enabledTime: Instant? = if (enabled) Instant.now().truncatedTo(ChronoUnit.MILLIS) else null,
    lastUpdateTime: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    withMetadata: Boolean = false
): Monitor {
    return Monitor(name = name, enabled = enabled, inputs = inputs, schedule = schedule, triggers = triggers,
            enabledTime = enabledTime, lastUpdateTime = lastUpdateTime,
            user = user, uiMetadata = if (withMetadata) mapOf("foo" to "bar") else mapOf())
}

fun randomADUser(backendRole: String = ESRestTestCase.randomAlphaOfLength(10)): User {
    return User(ESRestTestCase.randomAlphaOfLength(10), listOf(backendRole),
            listOf(ESRestTestCase.randomAlphaOfLength(10), "all_access"), listOf("test_attr=test"))
}

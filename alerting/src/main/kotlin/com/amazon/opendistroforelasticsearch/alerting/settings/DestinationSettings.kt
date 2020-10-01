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

package com.amazon.opendistroforelasticsearch.alerting.settings

import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.common.settings.SecureSetting
import org.elasticsearch.common.settings.SecureString
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.settings.Settings
import java.util.function.Function

/**
 * Settings specific to Destinations. This class is separated from the general AlertingSettings since some Destination
 * types require SecureSettings and need additional logic for retrieving and loading them.
 */
class DestinationSettings {

    companion object {

        const val DESTINATION_SETTING_PREFIX = "opendistro.alerting.destination."
        const val EMAIL_DESTINATION_SETTING_PREFIX = DESTINATION_SETTING_PREFIX + "email."
        val ALLOW_LIST_ALL = DestinationType.values().toList().map { it.value }
        val ALLOW_LIST_NONE = emptyList<String>()

        val ALLOW_LIST: Setting<List<String>> = Setting.listSetting(
            DESTINATION_SETTING_PREFIX + "allow_list",
            ALLOW_LIST_ALL,
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        )

        val EMAIL_USERNAME: Setting.AffixSetting<SecureString> = Setting.affixKeySetting(
            EMAIL_DESTINATION_SETTING_PREFIX,
            "username",
            // Needed to coerce lambda to Function type for some reason to avoid argument mismatch compile error
            Function { key: String -> SecureSetting.secureString(key, null) }
        )

        val EMAIL_PASSWORD: Setting.AffixSetting<SecureString> = Setting.affixKeySetting(
            EMAIL_DESTINATION_SETTING_PREFIX,
            "password",
            // Needed to coerce lambda to Function type for some reason to avoid argument mismatch compile error
            Function { key: String -> SecureSetting.secureString(key, null) }
        )

        fun loadDestinationSettings(settings: Settings): Map<String, SecureDestinationSettings> {
            // Only loading Email Destination settings for now since those are the only secure settings needed.
            // If this logic needs to be expanded to support other Destinations, different groups can be retrieved similar
            // to emailAccountNames based on the setting namespace and SecureDestinationSettings should be expanded to support
            // these new settings.
            val emailAccountNames: Set<String> = settings.getGroups(EMAIL_DESTINATION_SETTING_PREFIX).keys
            val emailAccounts: MutableMap<String, SecureDestinationSettings> = mutableMapOf()
            for (emailAccountName in emailAccountNames) {
                // Only adding the settings if they exist
                getSecureDestinationSettings(settings, emailAccountName)?.let {
                    emailAccounts[emailAccountName] = it
                }
            }

            return emailAccounts
        }

        private fun getSecureDestinationSettings(settings: Settings, emailAccountName: String): SecureDestinationSettings? {
            // Using 'use' to emulate Java's try-with-resources on multiple closeable resources.
            // Values are cloned so that we maintain a SecureString, the original SecureStrings will be closed after
            // they have left the scope of this function.
            return getEmailSettingValue(settings, emailAccountName, EMAIL_USERNAME)?.use { emailUsername ->
                getEmailSettingValue(settings, emailAccountName, EMAIL_PASSWORD)?.use { emailPassword ->
                    SecureDestinationSettings(emailUsername = emailUsername.clone(), emailPassword = emailPassword.clone())
                }
            }
        }

        private fun <T> getEmailSettingValue(settings: Settings, emailAccountName: String, emailSetting: Setting.AffixSetting<T>): T? {
            val concreteSetting = emailSetting.getConcreteSettingForNamespace(emailAccountName)
            return concreteSetting.get(settings)
        }

        data class SecureDestinationSettings(val emailUsername: SecureString, val emailPassword: SecureString)
    }
}

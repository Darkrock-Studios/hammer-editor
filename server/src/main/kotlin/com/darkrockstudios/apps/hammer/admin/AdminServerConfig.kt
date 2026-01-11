package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.email.MailgunConfig
import com.darkrockstudios.apps.hammer.email.PostmarkConfig
import com.darkrockstudios.apps.hammer.email.SendGridConfig
import com.darkrockstudios.apps.hammer.email.SmtpConfig
import com.darkrockstudios.apps.hammer.patreon.PatreonConfig
import kotlinx.serialization.json.Json

data class ServerConfigKey<T>(
	val key: String,
	val default: T,
	val parse: (String) -> T,
	val serialize: (T) -> String = { it.toString() }
) {
	companion object {
		fun string(key: String, default: String) = ServerConfigKey(key, default, { it }, { it })
		fun int(key: String, default: Int) = ServerConfigKey(key, default, { it.toInt() }, { it.toString() })
		fun boolean(key: String, default: Boolean) =
			ServerConfigKey(key, default, { it.toBoolean() }, { it.toString() })
	}
}

object AdminServerConfig {
	val WHITELIST_ENABLED = ServerConfigKey.boolean("whitelist_enabled", true)
	val SERVER_MESSAGE = ServerConfigKey.string("server_message", "Welcome to this instance of Hammer! Happy syncing!")
	val ABOUT_SERVER = ServerConfigKey.string("about_server", "")
	val CONTACT_EMAIL = ServerConfigKey.string("contact_email", "")
	val DEFAULT_LOCALE = ServerConfigKey.string("default_locale", "en")
	val PATREON_CONFIG = ServerConfigKey(
		key = "patreon_config",
		default = PatreonConfig(),
		parse = { Json.decodeFromString<PatreonConfig>(it) },
		serialize = { Json.encodeToString(PatreonConfig.serializer(), it) }
	)
	val SMTP_CONFIG = ServerConfigKey(
		key = "smtp_config",
		default = SmtpConfig(),
		parse = { Json.decodeFromString<SmtpConfig>(it) },
		serialize = { Json.encodeToString(SmtpConfig.serializer(), it) }
	)
	val SENDGRID_CONFIG = ServerConfigKey(
		key = "sendgrid_config",
		default = SendGridConfig(),
		parse = { Json.decodeFromString<SendGridConfig>(it) },
		serialize = { Json.encodeToString(SendGridConfig.serializer(), it) }
	)
	val POSTMARK_CONFIG = ServerConfigKey(
		key = "postmark_config",
		default = PostmarkConfig(),
		parse = { Json.decodeFromString<PostmarkConfig>(it) },
		serialize = { Json.encodeToString(PostmarkConfig.serializer(), it) }
	)
	val MAILGUN_CONFIG = ServerConfigKey(
		key = "mailgun_config",
		default = MailgunConfig(),
		parse = { Json.decodeFromString<MailgunConfig>(it) },
		serialize = { Json.encodeToString(MailgunConfig.serializer(), it) }
	)
}

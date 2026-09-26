import java.io.File

/**
 * Where tests find built plugins: HAMMER_PLUGINS points at a hammer-plugins checkout, with
 * hammer-plugin-sdk and hammer-plugin-development checked out beside it.
 */
object PluginRepos {
	val official: File get() = File(System.getenv("HAMMER_PLUGINS")).absoluteFile
	val sdk: File get() = official.resolveSibling("hammer-plugin-sdk")
	val development: File get() = official.resolveSibling("hammer-plugin-development")
}

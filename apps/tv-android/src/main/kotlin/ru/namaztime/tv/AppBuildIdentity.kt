package ru.namaztime.tv

data class AppBuildIdentity(
    val versionName: String,
    val versionCode: Int,
    val variant: String,
    val commit: String,
    val dirty: Boolean,
) {
    init {
        require(versionName.matches(VERSION_NAME_PATTERN)) { "invalid application version name" }
        require(versionCode > 0) { "application version code must be positive" }
        require(variant in BUILD_VARIANTS) { "unknown application build variant" }
        require(commit.matches(COMMIT_PATTERN)) { "invalid application build commit" }
        require(telemetryVersion.length <= MAX_TELEMETRY_VERSION_LENGTH) {
            "application build identity is too long"
        }
    }

    val shortCommit: String
        get() = commit.take(SHORT_COMMIT_LENGTH)

    val versionLabel: String
        get() = "$versionName ($versionCode)"

    val telemetryVersion: String
        get() = buildString {
            append(versionName)
            append("+g")
            append(shortCommit)
            if (dirty) append(".dirty")
        }

    private companion object {
        val VERSION_NAME_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$")
        val COMMIT_PATTERN = Regex("^[0-9a-f]{40}$")
        val BUILD_VARIANTS = setOf("debug", "release", "pilot")
        const val SHORT_COMMIT_LENGTH = 12
        const val MAX_TELEMETRY_VERSION_LENGTH = 64
    }
}

fun currentAppBuildIdentity(): AppBuildIdentity = AppBuildIdentity(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    variant = BuildConfig.BUILD_VARIANT,
    commit = BuildConfig.BUILD_COMMIT,
    dirty = BuildConfig.BUILD_DIRTY,
)

import java.io.File
import java.util.Properties

/**
 * Shared VERSION + Play upload signing (env / optional keystore.properties).
 *
 * Env (CI secrets or local export):
 *   ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
 * Optional: ANDROID_KEYSTORE_TYPE, CI_VERSION_NAME
 *
 * Form factor (set on the module before apply): extra["strawFormFactor"] = 0 (phone) | 1 (wear)
 * versionCode = (major*1_000_000 + minor*10_000 + patch*100 + preview) * 2 + formFactor
 * so Phone and Wear can share applicationId xyz.wastebase.strawnfc with unique Play versionCodes.
 */

val props = Properties()
val propsFile = rootProject.file("keystore.properties")
if (propsFile.isFile) {
    propsFile.inputStream().use { props.load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String {
    props.getProperty(propertyKey)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return System.getenv(envKey)?.trim().orEmpty()
}

fun parseBaseVersionCode(versionName: String): Int {
    val core = versionName.trim().substringBefore("-")
    val parts = core.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val preview = parts.getOrNull(3)?.toIntOrNull() ?: 0
    return major * 1_000_000 + minor * 10_000 + patch * 100 + preview
}

val fileVersion = rootProject.file("VERSION").readText().trim()
val versionName =
    System.getenv("CI_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: fileVersion
val formFactor =
    if (extra.has("strawFormFactor")) {
        (extra["strawFormFactor"] as Number).toInt()
    } else {
        0
    }
val versionCode = parseBaseVersionCode(versionName) * 2 + formFactor

val storeFilePath = signingValue("storeFile", "ANDROID_KEYSTORE_FILE")
val storePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val keyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
val keyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
val storeType = signingValue("storeType", "ANDROID_KEYSTORE_TYPE").ifEmpty { "jks" }

val storeFileObj: File? =
    if (storeFilePath.isEmpty()) {
        null
    } else {
        val candidate = File(storeFilePath)
        if (candidate.isAbsolute) candidate else rootProject.file(storeFilePath)
    }

val hasReleaseSigning =
    storeFileObj != null &&
        storeFileObj.isFile &&
        storePassword.isNotEmpty() &&
        keyAlias.isNotEmpty() &&
        keyPassword.isNotEmpty()

extra["strawVersionName"] = versionName
extra["strawVersionCode"] = versionCode
extra["strawFormFactor"] = formFactor
extra["strawSigningStoreFile"] = storeFileObj
extra["strawSigningStorePassword"] = storePassword
extra["strawSigningKeyAlias"] = keyAlias
extra["strawSigningKeyPassword"] = keyPassword
extra["strawSigningStoreType"] = storeType
extra["strawHasReleaseSigning"] = hasReleaseSigning

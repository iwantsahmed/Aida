package com.rayluxeo.aida.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Where the platform records the assistant. Written together, but not always both. */
private const val SETTING_ASSISTANT = "assistant"
private const val SETTING_VOICE_INTERACTION_SERVICE = "voice_interaction_service"

/** The AOSP assistant screen, reached by component because no public action names it. */
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val ASSIST_SETTINGS_CLASS = "com.android.settings.Settings\$ManageAssistActivity"

private const val SAMSUNG = "samsung"
private const val SAMSUNG_LABEL = "Samsung"

/**
 * One UI splits what Abu thinks of as one thing: the app that answers the assistant, and the
 * button that summons it. Both are named, in the order he would do them, and where a menu may
 * have moved between One UI versions the step says to search for it rather than asserting a path
 * that might not be there.
 */
private val SAMSUNG_STEPS_ENGLISH = listOf(
    "Open Settings > Apps > Choose default apps > Digital assistant app, and pick Aida there.",
    "One UI keeps the assistant app and the side button apart, so picking Aida above does not " +
        "hand her the press-and-hold gesture on its own.",
    "For the gesture, look under Settings > Advanced features for the side button. If One UI has " +
        "moved it, search Settings for \"side button\".",
    "Come back and test afterwards: Aida can hold the role while the side button still opens " +
        "something else.",
)

private val SAMSUNG_STEPS_BENGALI = listOf(
    "Settings > Apps > Choose default apps > Digital assistant app-এ গিয়ে Aida বেছে নিন।",
    "One UI-তে অ্যাসিস্ট্যান্ট অ্যাপ আর সাইড বাটন আলাদা সেটিং — উপরে আইদাকে বেছে নিলেই সাইড বাটন " +
        "আইদার কাছে যায় না।",
    "সাইড বাটনের জন্য Settings > Advanced features দেখুন। One UI-তে জায়গা বদলে থাকলে Settings-এ " +
        "\"side button\" লিখে খুঁজুন।",
    "বদলানোর পর ফিরে এসে পরীক্ষা করে নিন — রোল আইদার কাছে থাকলেও সাইড বাটন অন্য কিছু খুলতে পারে।",
)

/**
 * Asks the platform who the assistant is, and points Abu at the screen where he can change it.
 *
 * Nothing here grants anything. `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` is not a
 * supported way to ask for this role — it throws on some builds — so the only honest control is an
 * intent to a system screen, and [settingsIntent] returns null rather than name a screen this
 * phone does not have.
 *
 * Every answer is read fresh. The assistant can be changed from outside Mira while she is running,
 * and a remembered yes would be a lie the moment Abu does it.
 */
class AssistantRoleControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) : AssistantRoleController {

    override suspend fun status(): AssistantStatus = withContext(dispatchers.io) {
        // Two questions, because they can disagree and only one of them decides what Abu
        // experiences. isRoleHeld reports the role framework's record; isActiveService reports
        // which voice interaction service the platform has actually bound. Seen on a device with
        // Mira bound and answering the gesture while the role record still said no — believing
        // isRoleHeld alone put "Aida is not the assistant" directly above "Mira answers the
        // gesture today" on the same screen. Whichever says yes, the gesture reaches her.
        val roleAnswer = assistantRoleHeld()
        val serviceAnswer = miraServiceIsActive()
        val held = roleAnswer == true || serviceAnswer
        // A platform that refuses the settings read has still answered the role question, and a
        // yes there names Mira as certainly as the setting would have.
        val mira = AssistantComponent(context.packageName, className = null)
        val holder = assistantComponent() ?: mira.takeIf { held }
        val settingsScreen = settingsIntent()
        AssistantStatus(
            state = assistantRoleStateOf(
                roleHeld = if (held) true else roleAnswer,
                hasSettingsScreen = settingsScreen != null,
            ),
            holder = holder?.packageName,
            holderLabel = holder?.let(::holderLabelOf),
            oem = samsungGuidance(
                manufacturer = Build.MANUFACTURER,
                language = guidanceLanguage(settings.current().languageMode),
            )?.copy(settingsIntent = settingsScreen),
        )
    }

    /**
     * The first screen that both exists and can actually be started, so a screen that is only
     * present but locked down is not offered as a way forward.
     */
    override fun settingsIntent(): Intent? = candidateSettingsIntents().firstOrNull(::launchable)

    private fun candidateSettingsIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Intent.ACTION_MAIN).setComponent(
            ComponentName(SETTINGS_PACKAGE, ASSIST_SETTINGS_CLASS),
        ),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /**
     * True, false, or null when the question could not be asked at all — a build with no
     * [RoleManager], or one that does not know this role and answers with an exception. Null is
     * not a no: it is the difference between offering Abu a screen and telling him there is none.
     */
    /**
     * Whether Mira's own [MiraVoiceInteractionService] is the one the platform has bound.
     *
     * This is the fact that decides whether the assist gesture reaches her, and it is asked of the
     * platform rather than inferred from a settings string.
     */
    private fun miraServiceIsActive(): Boolean = runCatching {
        VoiceInteractionService.isActiveService(
            context,
            ComponentName(context, MiraVoiceInteractionService::class.java),
        )
    }.getOrDefault(false)

    private fun assistantRoleHeld(): Boolean? {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        return runCatching { roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrNull()
    }

    /**
     * Who Android says answers the gesture now.
     *
     * Both keys are read because they are written together but not always both, and either can be
     * refused outright — `Settings.Secure` became readable-by-annotation in API 31, so a target
     * this high can be handed a `SecurityException` in place of a value. A refusal is an absence,
     * never a name to guess at.
     */
    private fun assistantComponent(): AssistantComponent? =
        parseAssistantComponent(secureSetting(SETTING_ASSISTANT))
            ?: parseAssistantComponent(secureSetting(SETTING_VOICE_INTERACTION_SERVICE))

    private fun secureSetting(key: String): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()

    /**
     * The name Abu would recognise, asked of the package manager rather than derived from the
     * component. Null when the holder is not visible to Mira or has no label worth showing — the
     * package itself is already carried separately.
     */
    private fun holderLabelOf(component: AssistantComponent): String? {
        val packageManager = context.packageManager
        val serviceLabel = component.className?.let { className ->
            runCatching {
                serviceInfo(ComponentName(component.packageName, className)).loadLabel(packageManager)
            }.getOrNull()
        }
        val applicationLabel = runCatching {
            applicationInfo(component.packageName).loadLabel(packageManager)
        }.getOrNull()
        return preferredHolderLabel(
            serviceLabel = serviceLabel?.toString(),
            applicationLabel = applicationLabel?.toString(),
            packageName = component.packageName,
        )
    }

    /**
     * Resolution alone is not enough: the AOSP assistant screen is reached by component, and a
     * component that exists but is not exported would throw on the way out of Mira rather than
     * open. Disabled components are already excluded by the query itself.
     */
    private fun launchable(intent: Intent): Boolean {
        val activity = runCatching { resolveActivity(intent) }.getOrNull()?.activityInfo
            ?: return false
        return activity.exported
    }

    private fun resolveActivity(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    private fun serviceInfo(component: ComponentName) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, 0)
        }

    private fun applicationInfo(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
}

/**
 * A flattened `package/class` out of `Settings.Secure`, parsed without `ComponentName` so the
 * parsing can be tested off a device.
 */
internal data class AssistantComponent(val packageName: String, val className: String?)

/**
 * Follows `ComponentName.unflattenFromString`: a leading dot on the class is relative to the
 * package, anything else is taken as written. A value with no separator is treated as a bare
 * package, which some builds store.
 */
internal fun parseAssistantComponent(flattened: String?): AssistantComponent? {
    val value = flattened?.trim().orEmpty()
    if (value.isEmpty()) return null
    val separator = value.indexOf('/')
    if (separator < 0) return AssistantComponent(value, className = null)
    val packageName = value.substring(0, separator).trim()
    if (packageName.isEmpty()) return null
    val className = value.substring(separator + 1).trim()
    return AssistantComponent(
        packageName = packageName,
        className = when {
            className.isEmpty() -> null
            className.startsWith('.') -> packageName + className
            else -> className
        },
    )
}

/**
 * A label that merely repeats the package is not a name Abu would recognise, and showing it twice
 * would dress a raw identifier up as an answer.
 */
internal fun preferredHolderLabel(
    serviceLabel: String?,
    applicationLabel: String?,
    packageName: String,
): String? = listOfNotNull(serviceLabel, applicationLabel)
    .map(String::trim)
    .firstOrNull { it.isNotEmpty() && !it.equals(packageName, ignoreCase = true) }

/**
 * [AssistantRoleState.UNSUPPORTED] covers both silences: a platform that would not answer whether
 * Mira holds the role, and one that answered no but offers nowhere to change it.
 */
internal fun assistantRoleStateOf(
    roleHeld: Boolean?,
    hasSettingsScreen: Boolean,
): AssistantRoleState = when {
    roleHeld == true -> AssistantRoleState.HELD
    roleHeld == null -> AssistantRoleState.UNSUPPORTED
    hasSettingsScreen -> AssistantRoleState.AVAILABLE
    else -> AssistantRoleState.UNSUPPORTED
}

/**
 * A settings screen is not an utterance, so `AUTO` has no script to read and falls back to English
 * the way Mira's other static screens do.
 */
internal fun guidanceLanguage(mode: LanguageMode): SpokenLanguage = when (mode) {
    LanguageMode.BANGLA -> SpokenLanguage.BENGALI
    LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
    LanguageMode.AUTO -> SpokenLanguage.ENGLISH
}

/**
 * Null for every other manufacturer: guessing at a menu on a phone Abu does not own would be
 * worse than saying nothing at all.
 */
internal fun samsungGuidance(
    manufacturer: String,
    language: SpokenLanguage,
): OemAssistantGuidance? {
    if (!manufacturer.trim().equals(SAMSUNG, ignoreCase = true)) return null
    return OemAssistantGuidance(
        manufacturer = SAMSUNG_LABEL,
        steps = when (language) {
            SpokenLanguage.ENGLISH -> SAMSUNG_STEPS_ENGLISH
            SpokenLanguage.BENGALI -> SAMSUNG_STEPS_BENGALI
        },
    )
}

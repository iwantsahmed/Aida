package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.ui.tasks.openPlaceSettings

/**
 * Where Abu tells Mira what "the office" means.
 *
 * Place reminders have worked for a while — the storage, the proximity alert, the notification
 * and the refusals were all built — but there was no way to name a spot, so the whole feature was
 * unreachable. This is that way in, and it is deliberately the only one: Mira has no geocoder and
 * no map, because looking an address up means sending Abu's places to somebody's server. So he
 * stands somewhere and saves it, and nothing about where he is leaves the phone.
 *
 * The screen is optional in the strongest sense. Everything else in Mira works untouched if it is
 * never opened and the location permission is never granted; nothing here blocks, nags, or is
 * presented as setup she needs.
 */
@Composable
fun PlacesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PlacesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MiraTheme.spacing
    val wording = placesWording(state.language)

    val locationRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Read back from the platform rather than taken from the dialog's own result, because the
        // dialog may never have been shown: Android stops offering it after a second refusal, and
        // the callback then returns denials instantly. Falling through to the refusal is what
        // turns that dead end into the settings screen that can still settle it.
        if (context.canReadLocation()) {
            viewModel.confirmSave()
        } else {
            viewModel.reportRefusal(PlaceWatchRefusal.PERMISSION_MISSING)
        }
    }

    val takeAFix: () -> Unit = {
        if (context.canReadLocation()) {
            viewModel.confirmSave()
        } else {
            locationRequest.launch(LOCATION_PERMISSIONS)
        }
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = wording.title,
                subtitle = wording.subtitle,
                onBack = onBack,
                backContentDescription = wording.back,
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MiraListSkeleton()
            }
            return@MiraScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Above the empty state as well as above the list: a refused fix on Abu's first ever
            // visit is exactly when he most needs to be told why nothing was saved.
            if (state.hasNotice) {
                Spacer(Modifier.height(spacing.md))
                PlacesNotice(
                    state = state,
                    wording = wording,
                    onOpenSettings = { refusal -> context.openPlaceSettings(refusal) },
                    // A real retry, not a re-render: the name Abu typed survived the refusal, so
                    // this takes the fix again exactly as the dialog's own confirm would.
                    onRetry = takeAFix,
                    onDismiss = viewModel::dismissNotice,
                )
            }

            if (state.isEmpty) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MiraEmptyState(icon = MiraIcons.Place, message = wording.emptyMessage)
                    // The button is drawn here rather than through MiraEmptyState's own action
                    // slot, because that slot carries no `enabled`. Left lit while a fix was
                    // being taken it read "Taking a fix" and then did nothing when pressed; the
                    // saved-list button below has always been gated, and one screen's two states
                    // cannot disagree about whether its only control is live.
                    MiraButton(
                        text = if (state.saving) wording.takingAFix else wording.saveHere,
                        onClick = viewModel::beginSaving,
                        enabled = !state.saving,
                    )
                }
            } else {
                SavedPlaceList(
                    places = state.places,
                    wording = wording,
                    saving = state.saving,
                    onSaveHere = viewModel::beginSaving,
                    onRename = viewModel::beginRenaming,
                    onForget = viewModel::beginForgetting,
                )
            }
        }
    }

    when (val prompt = state.prompt) {
        PlacePrompt.Naming -> MiraDialog(
            title = wording.nameTitle,
            onDismissRequest = viewModel::dismissPrompt,
            confirmText = wording.save,
            onConfirm = takeAFix,
            message = wording.nameMessage,
            dismissText = wording.cancel,
        ) {
            PlaceNameField(
                value = state.draftLabel,
                onValueChange = viewModel::onDraftChange,
                wording = wording,
            )
        }

        is PlacePrompt.Renaming -> MiraDialog(
            title = wording.renameTitle,
            onDismissRequest = viewModel::dismissPrompt,
            confirmText = wording.rename,
            onConfirm = viewModel::confirmRename,
            message = wording.renameMessage,
            dismissText = wording.cancel,
        ) {
            PlaceNameField(
                value = state.draftLabel,
                onValueChange = viewModel::onDraftChange,
                wording = wording,
            )
        }

        is PlacePrompt.Forgetting -> MiraDialog(
            title = wording.forgetTitle(prompt.label),
            onDismissRequest = viewModel::dismissPrompt,
            confirmText = wording.forget,
            onConfirm = viewModel::confirmForget,
            message = wording.forgetMessage(prompt.label),
            dismissText = wording.keep,
            destructive = true,
        )

        null -> Unit
    }
}

/**
 * The places Mira knows, and the one control that adds another.
 *
 * A row is its own rename target and carries its own forget button, so neither action is hidden
 * behind a long press — there is no third place for a menu to live on a list this short.
 */
@Composable
private fun SavedPlaceList(
    places: List<SavedPlace>,
    wording: PlacesWording,
    saving: Boolean,
    onSaveHere: () -> Unit,
    onRename: (SavedPlace) -> Unit,
    onForget: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(spacing.md))
        Text(
            text = wording.intro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.sectionGap))
        MiraSectionHeader(text = wording.section)
        places.forEachIndexed { index, place ->
            if (index > 0) MiraDivider()
            MiraListRow(
                title = place.label,
                // The radius and the coordinate, so Abu can tell at a glance that a fix taken
                // indoors landed somewhere he recognises rather than three streets away.
                supporting = wording.placeDetail(place),
                onClick = { onRename(place) },
                leading = {
                    // Muted, not champagne. Five saved places would have meant five accents
                    // arguing with the one button that actually adds a sixth.
                    Icon(
                        imageVector = MiraIcons.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = {
                    // Also muted. The glyph is a bin and says so; colouring every row's bin red
                    // makes a list of Abu's own places look like a list of faults. What is
                    // destructive here is the confirmation, and that dialog is danger-coloured.
                    MiraIconButton(
                        icon = MiraIcons.DeleteOutline,
                        contentDescription = wording.forgetContentDescription(place.label),
                        onClick = { onForget(place) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        Spacer(Modifier.height(spacing.sectionGap))
        MiraButton(
            text = if (saving) wording.takingAFix else wording.saveHere,
            onClick = onSaveHere,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        )
        Spacer(Modifier.height(spacing.xxl))
    }
}

/**
 * One line above the content when something did not happen.
 *
 * A refusal outranks a problem because it is the more recent thing to have gone wrong and the
 * only one of the two that can carry a way out. The action is drawn only where the refusal has a
 * remedy, so the notice can never show a button with nothing behind it — and which remedy it is
 * decides what the button does, because "no fix yet" is answered by asking again rather than by a
 * settings screen that has nothing on it to change.
 */
@Composable
private fun PlacesNotice(
    state: PlacesUiState,
    wording: PlacesWording,
    onOpenSettings: (PlaceWatchRefusal) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refusal = state.refusal
    val problem = state.problem
    when {
        refusal != null -> {
            val remedy = refusal.remedy()
            val act: (() -> Unit)? = when (remedy) {
                PlaceRefusalRemedy.SETTINGS -> ({ onOpenSettings(refusal) })
                PlaceRefusalRemedy.RETRY -> onRetry
                null -> null
            }
            MiraInlineNotice(
                text = wording.refusal(refusal),
                modifier = modifier,
                tone = MiraNoticeTone.DANGER,
                dismissContentDescription = wording.dismiss,
                onDismiss = onDismiss,
                actionText = wording.refusalAction(refusal),
                onAction = act,
            )
        }

        problem != null -> MiraInlineNotice(
            text = wording.problem(problem),
            modifier = modifier,
            tone = MiraNoticeTone.DANGER,
            dismissContentDescription = wording.dismiss,
            onDismiss = onDismiss,
        )
    }
}

/** The field both dialogs put the name in, so the two cannot drift apart. */
@Composable
private fun PlaceNameField(
    value: String,
    onValueChange: (String) -> Unit,
    wording: PlacesWording,
    modifier: Modifier = Modifier,
) {
    MiraTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = wording.nameLabel,
    )
}

/**
 * Whether Mira may ask the platform where Abu is at all.
 *
 * Deliberately "any" rather than [com.rayluxeo.aida.ui.onboarding.hasPermissions]'s "all": Android
 * shows precise and approximate as one choice, and granting only the approximate one still gives
 * a fix. How good that fix is, `CurrentPlaceFinder` decides. Requiring both would leave an
 * approximate grant looping back to a dialog Android will not show again — a button that silently
 * does nothing, which is the one defect this screen must not have.
 */
private fun Context.canReadLocation(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Both are asked for together, because Android presents them as the single precise-or-approximate
 * choice and asking for one alone hides the other half of it.
 *
 * `ACCESS_BACKGROUND_LOCATION` is deliberately absent. Taking one fix while Abu is looking at this
 * screen needs nothing more, and all-the-time access is what a reminder *waiting* at a place
 * needs — asked for there, where it is used, rather than here.
 */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

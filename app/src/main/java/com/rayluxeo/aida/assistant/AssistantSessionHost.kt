package com.rayluxeo.aida.assistant

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The three owners Compose needs and a voice interaction session does not have.
 *
 * A `ComposeView` only works under a host that provides `ViewTreeLifecycleOwner`,
 * `ViewTreeViewModelStoreOwner` and `ViewTreeSavedStateRegistryOwner`: the first decides when the
 * recomposer runs, the second scopes anything remembered across configuration changes, and the
 * third is what `rememberSaveable` writes into. An Activity supplies all three.
 * `VoiceInteractionSession` supplies none, and a `ComposeView` added without them either throws
 * on attach or composes nothing at all — an overlay that is a blank rectangle.
 *
 * This is that host, driven by hand from the session's own callbacks. The lifecycle is the
 * session's: CREATED once the window exists, RESUMED while the panel is on screen, back to
 * CREATED when it is hidden, DESTROYED when the session ends. The recomposer follows it, so a
 * hidden overlay stops drawing frames instead of animating over an app Abu is using.
 */
class AssistantSessionHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    /**
     * Restores from nothing on purpose. A session is opened by a gesture and closed when the turn
     * is over; there is no saved instance state to come back to, and the registry still has to be
     * restored before the lifecycle leaves INITIALIZED or the composition cannot register its own
     * saveable state provider.
     */
    fun onCreate() {
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /**
     * Must be called on the root view **before** the `ComposeView` is attached: the view tree is
     * searched upwards at attach time, and an owner set afterwards arrives too late.
     */
    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun onShow() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onHide() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /**
     * The guard is not defensive noise: the framework may destroy a session that was created and
     * never shown, and `LifecycleRegistry` has no event down from INITIALIZED — moving there from
     * a host that never reached CREATED throws.
     */
    fun onDestroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStore.clear()
    }
}

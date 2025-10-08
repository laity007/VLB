package org.videolan.vlc.gui.beauty

import android.view.View
import android.view.ViewStub
import androidx.appcompat.widget.ViewStubCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.DisplayManager
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * Lightweight controller that wires the beauty rendering pipeline with the UI
 * controls exposed on the player overlay.
 */
class VideoBeautyDelegate(
    private val activity: VideoPlayerActivity,
    private val layout: BeautyVideoLayout?,
    private val beautyStub: ViewStubCompat?
) {

    private val manager: BeautyManager? = layout?.let { BeautyManager(it) }

    private var panelView: View? = null
    private var switchMaterial: SwitchMaterial? = null
    private var smoothingSlider: Slider? = null
    private var faceSlider: Slider? = null
    private var eyeSlider: Slider? = null
    private var noseSlider: Slider? = null
    private var mouthSlider: Slider? = null
    private var filterIntensitySlider: Slider? = null
    private var filterGroup: ChipGroup? = null

    private var panelVisible = false

    fun onResume() {
        manager?.onResume()
    }

    fun onPause() {
        manager?.onPause()
    }

    fun onDestroy() {
        manager?.destroy()
    }

    fun detachFromPlayer() {
        manager?.detachFromPlayer()
    }

    fun attachToPlayer(mediaPlayer: MediaPlayer, displayManager: DisplayManager) {
        manager?.attachToPlayer(mediaPlayer, displayManager)
    }

    fun togglePanel() {
        ensurePanel()?.let { panel ->
            panelVisible = !panelVisible
            panel.visibility = if (panelVisible) View.VISIBLE else View.GONE
        }
    }

    fun hidePanel() {
        panelVisible = false
        panelView?.visibility = View.GONE
    }

    fun onOverlayVisibilityChanged(visible: Boolean) {
        if (!visible) hidePanel()
    }

    private fun ensurePanel(): View? {
        if (panelView == null) {
            panelView = when (val stub = beautyStub) {
                is ViewStub -> stub.inflate()
                is ViewStubCompat -> stub.inflate()
                else -> null
            }
            setupPanel(panelView)
        }
        return panelView
    }

    private fun setupPanel(panel: View?) {
        if (panel == null || manager == null) return
        switchMaterial = panel.findViewById(R.id.beauty_switch)
        smoothingSlider = panel.findViewById(R.id.beauty_slider_smooth)
        faceSlider = panel.findViewById(R.id.beauty_slider_face)
        eyeSlider = panel.findViewById(R.id.beauty_slider_eye)
        noseSlider = panel.findViewById(R.id.beauty_slider_nose)
        mouthSlider = panel.findViewById(R.id.beauty_slider_mouth)
        filterIntensitySlider = panel.findViewById(R.id.beauty_slider_filter_intensity)
        filterGroup = panel.findViewById(R.id.beauty_filter_group)

        val currentConfig = manager.currentConfig()

        switchMaterial?.apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                manager.setBeautyEnabled(isChecked)
            }
        }

        fun Slider.bind(initial: Float, onChange: (Float) -> Unit) {
            valueFrom = 0f
            valueTo = 1f
            stepSize = 0.01f
            value = initial
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) onChange(value)
            }
        }

        smoothingSlider?.bind(currentConfig.skinSmoothing) { value ->
            manager.updateConfig { it.copy(skinSmoothing = value) }
        }
        faceSlider?.bind(currentConfig.faceSlimming) { value ->
            manager.updateConfig { it.copy(faceSlimming = value) }
        }
        eyeSlider?.bind(currentConfig.eyeEnlarge) { value ->
            manager.updateConfig { it.copy(eyeEnlarge = value) }
        }
        noseSlider?.bind(currentConfig.noseRefine) { value ->
            manager.updateConfig { it.copy(noseRefine = value) }
        }
        mouthSlider?.bind(currentConfig.mouthAdjust) { value ->
            manager.updateConfig { it.copy(mouthAdjust = value) }
        }
        filterIntensitySlider?.bind(currentConfig.filterIntensity) { value ->
            manager.updateConfig { it.copy(filterIntensity = value) }
        }

        filterGroup?.apply {
            isSingleSelection = true
            val group = this
            val currentId = manager.currentFilter().id
            removeAllViews()
            BeautyFilterPreset.presets().forEach { preset ->
                val chip = Chip(activity).apply {
                    text = activity.getString(preset.titleRes)
                    isCheckable = true
                    id = View.generateViewId()
                    isChecked = preset.id == currentId
                    setOnClickListener {
                        group.check(this.id)
                        manager.selectFilter(preset)
                    }
                }
                addView(chip)
                if (preset.id == currentId) check(chip.id)
            }
        }
    }
}

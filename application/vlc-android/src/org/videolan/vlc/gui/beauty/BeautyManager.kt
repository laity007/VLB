package org.videolan.vlc.gui.beauty

import android.view.Surface
import android.view.SurfaceView
import android.view.View
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.DisplayManager

/**
 * Coordinates VLC's decoded frames with the OpenGL renderer. It is responsible
 * for swapping VLC's default surfaces with the GL texture once it is ready,
 * managing lifecycle events and dispatching configuration changes to the
 * renderer.
 */
class BeautyManager(
    private val layout: BeautyVideoLayout
) {

    private val renderer = BeautyRenderer()
    private var renderSurface: Surface? = null
    private var mediaPlayer: MediaPlayer? = null
    private var displayManager: DisplayManager? = null
    private var subtitlesSurface: Surface? = null

    private var config: BeautyConfig = BeautyConfig()
    private var filterPreset: BeautyFilterPreset = BeautyFilterPreset.NONE

    private var pendingAttach = false

    init {
        layout.setRenderer(renderer)
        renderer.setOnFrameRequested { layout.requestRender() }
        renderer.setOnSurfaceTextureReady { surfaceTexture ->
            renderSurface?.release()
            renderSurface = Surface(surfaceTexture)
            bindIfPossible()
        }
        renderer.setOnSizeChanged { _, _ ->
            // Placeholder for dynamic adjustments if needed later.
        }
    }

    fun onResume() {
        layout.onRendererResume()
    }

    fun onPause() {
        layout.onRendererPause()
    }

    fun attachToPlayer(mediaPlayer: MediaPlayer, displayManager: DisplayManager) {
        this.mediaPlayer = mediaPlayer
        this.displayManager = displayManager
        pendingAttach = true
        bindIfPossible()
    }

    fun detachFromPlayer() {
        pendingAttach = false
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer = null
        displayManager = null
        renderSurface?.release()
        renderSurface = null
        subtitlesSurface = null
        layout.resetVideoSurfaceVisibility()
    }

    fun destroy() {
        detachFromPlayer()
        layout.queueEvent { renderer.release() }
    }

    fun setBeautyEnabled(enabled: Boolean) {
        layout.queueEvent { renderer.setBeautyEnabled(enabled) }
        layout.requestRender()
    }

    fun updateConfig(block: (BeautyConfig) -> BeautyConfig) {
        config = block(config)
        layout.queueEvent { renderer.updateConfig(config) }
        selectFilter(filterPreset)
        layout.requestRender()
    }

    fun selectFilter(preset: BeautyFilterPreset) {
        filterPreset = preset
        config = config.copy(filterId = preset.id)
        layout.queueEvent {
            renderer.setFilter(preset.colorMatrix, preset.strength)
            renderer.updateConfig(config)
        }
        layout.requestRender()
    }

    fun currentConfig(): BeautyConfig = config

    fun currentFilter(): BeautyFilterPreset = filterPreset

    private fun bindIfPossible() {
        if (!pendingAttach) return
        val surface = renderSurface ?: return
        val player = mediaPlayer ?: return
        val manager = displayManager ?: return

        val vout = player.vlcVout
        vout.detachViews()
        vout.setVideoSurface(surface, null)
        val subtitleView = layout.getSubtitleSurfaceView()
        subtitlesSurface = subtitleView?.holder?.surface
        val subtitleHolder = subtitleView?.holder
        if (subtitlesSurface != null && subtitleHolder != null) {
            vout.setSubtitlesSurface(subtitlesSurface, subtitleHolder)
        }
        vout.attachViews(layout, manager, true, false)
        hideOriginalVideoSurface()
        pendingAttach = false
    }

    private fun hideOriginalVideoSurface() {
        layout.getVideoSurfaceView()?.let { view ->
            view.visibility = View.INVISIBLE
            if (view is SurfaceView) {
                view.holder.setFixedSize(view.width, view.height)
            }
        }
    }
}

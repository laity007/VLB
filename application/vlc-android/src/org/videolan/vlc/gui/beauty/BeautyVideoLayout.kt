package org.videolan.vlc.gui.beauty

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View
import org.videolan.libvlc.util.VLCVideoLayout
import org.videolan.vlc.R

/**
 * Wrapper around [VLCVideoLayout] that adds an OpenGL pipeline on top of the
 * classic VLC surfaces so that we can intercept decoded frames and post process
 * them before displaying them to the user.
 */
class BeautyVideoLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : VLCVideoLayout(context, attrs, defStyleAttr) {

    private val glSurfaceView: GLSurfaceView = GLSurfaceView(context)

    init {
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.preserveEGLContextOnPause = true
        glSurfaceView.setZOrderMediaOverlay(true)
        glSurfaceView.setBackgroundColor(0x00000000)
        glSurfaceView.isClickable = false
        glSurfaceView.isFocusable = false
        addView(glSurfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setRenderer(renderer: BeautyRenderer) {
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    fun queueEvent(action: () -> Unit) {
        glSurfaceView.queueEvent(action)
    }

    fun requestRender() {
        glSurfaceView.requestRender()
    }

    fun onRendererResume() {
        glSurfaceView.onResume()
    }

    fun onRendererPause() {
        glSurfaceView.onPause()
    }

    fun resetVideoSurfaceVisibility() {
        findViewById<SurfaceView?>(R.id.surface_video)?.visibility = View.VISIBLE
    }

    fun getVideoSurfaceView(): SurfaceView? = findViewById(R.id.surface_video)

    fun getSubtitleSurfaceView(): SurfaceView? = findViewById(R.id.surface_subtitles)
}

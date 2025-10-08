package org.videolan.vlc.gui.beauty

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.annotation.GuardedBy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * OpenGL renderer responsible for drawing VLC's decoded frames on top of our
 * post-processing pipeline. The shader is intentionally simple – it blends a
 * light weight smoothing pass with a couple of warping functions and an
 * optional color matrix.
 */
class BeautyRenderer : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer

    private val texMatrix = FloatArray(16)

    private var program = 0
    private var positionHandle = 0
    private var textureHandle = 0
    private var texMatrixHandle = 0
    private var smoothingHandle = 0
    private var faceHandle = 0
    private var eyeHandle = 0
    private var noseHandle = 0
    private var mouthHandle = 0
    private var enabledHandle = 0
    private var resolutionHandle = 0
    private var colorMatrixHandle = 0
    private var filterIntensityHandle = 0

    private var surfaceTexture: SurfaceTexture? = null
    private var textureId = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var beautyConfig = BeautyConfig()
    private var beautyEnabled = true
    private val currentFilterMatrix = FloatArray(BeautyFilterPreset.MATRIX_LENGTH)
    private var currentFilterBaseStrength = 0f
    private var currentFilterStrength = 0f

    private val frameAvailable = AtomicBoolean(false)

    private val callbacksLock = Any()
    @GuardedBy("callbacksLock")
    private var surfaceReadyCallback: ((SurfaceTexture) -> Unit)? = null
    @GuardedBy("callbacksLock")
    private var sizeChangedCallback: ((Int, Int) -> Unit)? = null
    @GuardedBy("callbacksLock")
    private var frameCallback: (() -> Unit)? = null

    init {
        val vertexData = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f,
        )
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
        vertexBuffer.position(0)

        val textureData = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
        )
        textureBuffer = ByteBuffer.allocateDirect(textureData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureData)
        textureBuffer.position(0)

        updateConfig(beautyConfig)
        setFilter(BeautyFilterPreset.NONE.colorMatrix, 0f)
    }

    fun setOnSurfaceTextureReady(listener: (SurfaceTexture) -> Unit) {
        synchronized(callbacksLock) {
            surfaceReadyCallback = listener
            surfaceTexture?.let(listener)
        }
    }

    fun setOnSizeChanged(listener: (Int, Int) -> Unit) {
        synchronized(callbacksLock) {
            sizeChangedCallback = listener
            if (viewportWidth > 0 && viewportHeight > 0) {
                listener(viewportWidth, viewportHeight)
            }
        }
    }

    fun setOnFrameRequested(listener: () -> Unit) {
        synchronized(callbacksLock) {
            frameCallback = listener
        }
    }

    fun setBeautyEnabled(enabled: Boolean) {
        beautyEnabled = enabled
    }

    fun updateConfig(config: BeautyConfig) {
        beautyConfig = config
        updateFilterIntensity()
    }

    fun setFilter(matrix: FloatArray, baseIntensity: Float) {
        require(matrix.size == BeautyFilterPreset.MATRIX_LENGTH)
        System.arraycopy(matrix, 0, currentFilterMatrix, 0, matrix.size)
        currentFilterBaseStrength = max(0f, baseIntensity)
        updateFilterIntensity()
    }

    private fun updateFilterIntensity() {
        currentFilterStrength = beautyConfig.filterIntensity * currentFilterBaseStrength
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        textureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        smoothingHandle = GLES20.glGetUniformLocation(program, "uSmooth")
        faceHandle = GLES20.glGetUniformLocation(program, "uFaceSlim")
        eyeHandle = GLES20.glGetUniformLocation(program, "uEye")
        noseHandle = GLES20.glGetUniformLocation(program, "uNose")
        mouthHandle = GLES20.glGetUniformLocation(program, "uMouth")
        enabledHandle = GLES20.glGetUniformLocation(program, "uBeautyEnabled")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")
        colorMatrixHandle = GLES20.glGetUniformLocation(program, "uColorMatrix")
        filterIntensityHandle = GLES20.glGetUniformLocation(program, "uFilterIntensity")

        textureId = generateExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@BeautyRenderer)
        }
        synchronized(callbacksLock) {
            surfaceReadyCallback?.invoke(surfaceTexture!!)
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        synchronized(callbacksLock) {
            sizeChangedCallback?.invoke(width, height)
        }
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        if (surfaceTexture == null) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }
        if (frameAvailable.compareAndSet(true, false)) {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(texMatrix)
        }

        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        GLES20.glEnableVertexAttribArray(textureHandle)

        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glUniform1f(smoothingHandle, beautyConfig.skinSmoothing)
        GLES20.glUniform1f(faceHandle, beautyConfig.faceSlimming)
        GLES20.glUniform1f(eyeHandle, beautyConfig.eyeEnlarge)
        GLES20.glUniform1f(noseHandle, beautyConfig.noseRefine)
        GLES20.glUniform1f(mouthHandle, beautyConfig.mouthAdjust)
        GLES20.glUniform1f(enabledHandle, if (beautyEnabled) 1f else 0f)
        GLES20.glUniform2f(resolutionHandle, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES20.glUniformMatrix3fv(colorMatrixHandle, 1, false, currentFilterMatrix, 0)
        GLES20.glUniform1f(filterIntensityHandle, currentFilterStrength)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        frameAvailable.set(true)
        synchronized(callbacksLock) {
            frameCallback?.invoke()
        }
    }

    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = null
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun generateExternalTexture(): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return texture[0]
    }

    companion object {
        private const val FLOAT_SIZE_BYTES = 4

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vec4 tex = uTexMatrix * aTextureCoord;
                vTextureCoord = tex.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform float uSmooth;
            uniform float uFaceSlim;
            uniform float uEye;
            uniform float uNose;
            uniform float uMouth;
            uniform float uBeautyEnabled;
            uniform vec2 uResolution;
            uniform mat3 uColorMatrix;
            uniform float uFilterIntensity;

            vec2 warpFace(vec2 coord, vec2 center, float radius, float strength) {
                vec2 dir = coord - center;
                float dist = length(dir);
                if (dist < radius) {
                    float ratio = (radius - dist) / radius;
                    float scale = 1.0 - strength * ratio * 0.35;
                    dir *= scale;
                    return center + dir;
                }
                return coord;
            }

            vec2 warpEye(vec2 coord, vec2 center, float radius, float strength) {
                vec2 dir = coord - center;
                float dist = length(dir);
                if (dist < radius) {
                    float percent = (radius - dist) / radius;
                    float scale = 1.0 - strength * percent * 0.45;
                    dir *= scale;
                    return center + dir;
                }
                return coord;
            }

            vec2 refineFeature(vec2 coord, vec2 center, float radius, float strength, vec2 direction) {
                vec2 dir = coord - center;
                float dist = length(dir);
                if (dist < radius) {
                    float percent = (radius - dist) / radius;
                    return coord - direction * strength * percent * 0.1;
                }
                return coord;
            }

            vec4 sampleSmooth(vec2 coord, vec2 texel, float strength) {
                vec4 sum = vec4(0.0);
                sum += texture2D(sTexture, coord + texel * vec2(-2.0, -2.0)) * 0.05;
                sum += texture2D(sTexture, coord + texel * vec2(-1.0, -1.0)) * 0.09;
                sum += texture2D(sTexture, coord + texel * vec2(0.0, -1.0)) * 0.12;
                sum += texture2D(sTexture, coord + texel * vec2(1.0, -1.0)) * 0.09;
                sum += texture2D(sTexture, coord + texel * vec2(2.0, -2.0)) * 0.05;
                sum += texture2D(sTexture, coord + texel * vec2(-2.0, 0.0)) * 0.09;
                sum += texture2D(sTexture, coord) * 0.2;
                sum += texture2D(sTexture, coord + texel * vec2(2.0, 0.0)) * 0.09;
                sum += texture2D(sTexture, coord + texel * vec2(-2.0, 2.0)) * 0.05;
                sum += texture2D(sTexture, coord + texel * vec2(-1.0, 1.0)) * 0.09;
                sum += texture2D(sTexture, coord + texel * vec2(0.0, 1.0)) * 0.12;
                sum += texture2D(sTexture, coord + texel * vec2(1.0, 1.0)) * 0.09;
                sum += texture2D(sTexture, coord + texel * vec2(2.0, 2.0)) * 0.05;
                vec4 original = texture2D(sTexture, coord);
                return mix(original, sum, clamp(strength, 0.0, 1.0));
            }

            void main() {
                vec2 texel = 1.0 / uResolution;
                vec2 coord = vTextureCoord;
                if (uBeautyEnabled > 0.5) {
                    coord = warpFace(coord, vec2(0.5, 0.65), 0.45, uFaceSlim);
                    coord = warpEye(coord, vec2(0.35, 0.45), 0.18, uEye);
                    coord = warpEye(coord, vec2(0.65, 0.45), 0.18, uEye);
                    coord = refineFeature(coord, vec2(0.5, 0.55), 0.25, uNose, vec2(0.0, 1.0));
                    coord = refineFeature(coord, vec2(0.5, 0.72), 0.25, uMouth, vec2(0.0, -1.0));
                }
                vec4 color = sampleSmooth(coord, texel, uBeautyEnabled * uSmooth);
                if (uFilterIntensity > 0.0) {
                    vec3 graded = uColorMatrix * color.rgb;
                    color.rgb = mix(color.rgb, graded, clamp(uFilterIntensity, 0.0, 1.0));
                }
                gl_FragColor = color;
            }
        """

        private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Could not link program: $error")
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return program
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader: $error")
            }
            return shader
        }
    }
}

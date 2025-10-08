package org.videolan.vlc.gui.beauty

import androidx.annotation.StringRes
import org.videolan.vlc.R

/**
 * Defines the runtime configurable beauty parameters that can be
 * controlled from the UI. All values are expressed between 0f and 1f.
 */
data class BeautyConfig(
    val skinSmoothing: Float = 0.35f,
    val faceSlimming: Float = 0.2f,
    val eyeEnlarge: Float = 0.18f,
    val noseRefine: Float = 0.1f,
    val mouthAdjust: Float = 0.05f,
    val filterIntensity: Float = 0.6f,
    val filterId: String = BeautyFilterPreset.NONE.id,
)

/**
 * Color grading presets that can be stacked on top of the beauty shader.
 */
data class BeautyFilterPreset(
    val id: String,
    @StringRes val titleRes: Int,
    val colorMatrix: FloatArray,
    val strength: Float = 1.0f
) {
    companion object {
        const val MATRIX_LENGTH = 9
        val NONE = BeautyFilterPreset(
            id = "none",
            titleRes = R.string.beauty_filter_none,
            colorMatrix = floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            ),
            strength = 0f
        )

        fun presets(): List<BeautyFilterPreset> = listOf(
            NONE,
            BeautyFilterPreset(
                id = "warm_glow",
                titleRes = R.string.beauty_filter_warm,
                colorMatrix = floatArrayOf(
                    1.12f, -0.05f, 0.03f,
                    0.02f, 1.05f, 0.01f,
                    -0.04f, 0f, 1.03f
                ),
                strength = 0.85f
            ),
            BeautyFilterPreset(
                id = "film",
                titleRes = R.string.beauty_filter_film,
                colorMatrix = floatArrayOf(
                    1.05f, 0.02f, -0.08f,
                    0.02f, 0.95f, -0.02f,
                    0.06f, 0.04f, 0.92f
                ),
                strength = 0.9f
            ),
            BeautyFilterPreset(
                id = "retro",
                titleRes = R.string.beauty_filter_retro,
                colorMatrix = floatArrayOf(
                    0.85f, 0.1f, 0.05f,
                    0.04f, 0.9f, 0.02f,
                    0.04f, 0.08f, 0.86f
                ),
                strength = 0.75f
            )
        )
    }
}

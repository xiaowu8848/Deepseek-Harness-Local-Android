package app.dsh.mobile

import android.graphics.Bitmap

/**
 * 背景图特效用的小工具（v1.5.x）。
 *
 * 只为「设置页背景图」服务：模糊 + 不透明度。
 */
object ImageFx {

    /** 不透明度换算：0~100 → 0~255。 */
    fun alphaOf(percent: Int): Int =
        ((percent.coerceIn(0, 100)) * 255 / 100)

    /**
     * 粗略高斯模糊（降采样近似）。
     *
     * ## 为什么不用 RenderScript / RenderEffect
     *  · RenderScript 已废弃，且需要额外支持库；
     *  · RenderEffect.createBlurEffect 需要 API 31，本项目 minSdk 26。
     *
     * ## 做法
     * 反复「缩小 → 放大」：缩小阶段用双线性采样把细节平均掉，放大时再插值回
     * 原尺寸，视觉上就是一张均匀模糊的图。次数越多越糊，代价只有几次缩放，
     * 远低于逐像素卷积 —— 背景图常常是整屏尺寸，逐像素卷积会明显卡顿。
     *
     * @param strength 0 = 原图；1~24 越大越模糊
     */
    fun blur(src: Bitmap, strength: Int): Bitmap {
        if (strength <= 0) return src
        // 每档大约缩小 1/3，strength 越大缩小得越狠（最小不低于 1/24）
        val factor = (strength / 2 + 2).coerceAtLeast(2)
        val w = (src.width / factor).coerceAtLeast(1)
        val h = (src.height / factor).coerceAtLeast(1)
        var small = Bitmap.createScaledBitmap(src, w, h, true)
        // 再用一次缩放做「软过渡」，避免小图放大后出现明显块状
        small = Bitmap.createScaledBitmap(small, (w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }
}

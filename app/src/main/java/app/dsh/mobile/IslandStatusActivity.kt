package app.dsh.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * 收起态状态栏设置（v1.5.x）：状态栏收起成小圆点时显示什么。
 *
 * 三项：
 *  - 图片：自定义图标；不设则用默认的「深色圆 + 中心小亮核」；
 *  - 形状：圆形裁切 / 保留方形原图；
 *  - 尺寸：圆点边长 26~72dp。
 *
 * 改完立刻写偏好并刷新预览；回到主界面时 [MainActivity] 的
 * `applyIslandDotImage` 会按新值重绘，不需要重启。
 */
class IslandStatusActivity : ThemedActivity() {

    /** 属于设置体系：启用「设置页背景图」。 */
    override val usesSettingsBackground: Boolean get() = true

    private var shape = CustomVisualPref.DotShape.CIRCLE
    private lateinit var imgPreview: ImageView
    private lateinit var previewDefault: View
    private lateinit var txtHint: TextView
    private lateinit var txtSize: TextView
    private lateinit var seekSize: SeekBar

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationPrefs.applyToSettingsPage(this)
        setContentView(R.layout.activity_island_status)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        imgPreview = findViewById(R.id.imgDotPreview)
        previewDefault = findViewById(R.id.previewDefault)
        txtHint = findViewById(R.id.txtDotHint)
        txtSize = findViewById(R.id.valDotSize)
        seekSize = findViewById(R.id.seekDotSize)

        shape = CustomVisualPref.islandDotShape(this)

        findViewById<LinearLayout>(R.id.rowShapeCircle).setOnClickListener {
            setShape(CustomVisualPref.DotShape.CIRCLE)
        }
        findViewById<LinearLayout>(R.id.rowShapeSquare).setOnClickListener {
            setShape(CustomVisualPref.DotShape.SQUARE)
        }
        findViewById<View>(R.id.btnPickDot).setOnClickListener { pickImage() }
        findViewById<View>(R.id.btnRemoveDot).setOnClickListener {
            CustomVisualPref.clearIslandDot(this)
            refreshPreview()
        }

        // SeekBar 用 0..(max-min) 的偏移量表示尺寸，避免把 dp 值直接当 progress
        seekSize.max = CustomVisualPref.DOT_SIZE_MAX_DP - CustomVisualPref.DOT_SIZE_MIN_DP
        seekSize.progress = CustomVisualPref.islandDotSizeDp(this) - CustomVisualPref.DOT_SIZE_MIN_DP
        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                CustomVisualPref.setIslandDotSizeDp(
                    this@IslandStatusActivity,
                    CustomVisualPref.DOT_SIZE_MIN_DP + progress,
                )
                refreshPreview()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        // 可能刚在外观页改过形状/尺寸，回来重新对齐
        shape = CustomVisualPref.islandDotShape(this)
        refreshPreview()
    }

    private fun setShape(next: CustomVisualPref.DotShape) {
        shape = next
        CustomVisualPref.setIslandDotShape(this, next)
        refreshPreview()
    }

    /**
     * 刷新预览：形状与尺寸都体现在预览上 —— 用户改完马上看到效果，
     * 不用先返回主界面把状态栏收起来才知道对不对。
     */
    private fun refreshPreview() {
        val sizeDp = CustomVisualPref.islandDotSizeDp(this)
        txtSize.text = getString(R.string.setting_dot_size_value, sizeDp)
        seekSize.progress = sizeDp - CustomVisualPref.DOT_SIZE_MIN_DP

        // 单选标记：当前形状那行显示勾，另一行隐藏
        val circleMark = findViewById<ImageView>(R.id.chkShapeCircle)
        val squareMark = findViewById<ImageView>(R.id.chkShapeSquare)
        circleMark.visibility =
            if (shape == CustomVisualPref.DotShape.CIRCLE) View.VISIBLE else View.INVISIBLE
        squareMark.visibility =
            if (shape == CustomVisualPref.DotShape.SQUARE) View.VISIBLE else View.INVISIBLE

        // 预览尺寸：按 dp 换算，最大不超过预览框（96dp）
        // 预览按实际 dp 显示（上限 96dp，即预览框尺寸），让"大小"看得见
        val px = dp(sizeDp.coerceAtMost(96))
        imgPreview.layoutParams = imgPreview.layoutParams.apply {
            width = px
            height = px
        }
        imgPreview.requestLayout()

        val file = CustomVisualPref.islandDotFile(this)
        if (file == null) {
            imgPreview.visibility = View.GONE
            previewDefault.visibility = View.VISIBLE
            txtHint.setText(R.string.setting_image_none)
            return
        }
        val bmp = runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
        if (bmp == null) {
            imgPreview.visibility = View.GONE
            previewDefault.visibility = View.VISIBLE
            txtHint.setText(R.string.setting_image_none)
            return
        }
        val shown = if (shape == CustomVisualPref.DotShape.CIRCLE) circular(bmp) ?: bmp else bmp
        imgPreview.setImageDrawable(BitmapDrawable(resources, shown))
        imgPreview.visibility = View.VISIBLE
        previewDefault.visibility = View.GONE
        txtHint.setText(R.string.setting_image_set)
    }

    /** 圆形裁切（与主界面 applyIslandDotImage 同一算法，保证预览即所得）。 */
    private fun circular(src: Bitmap): Bitmap? = runCatching {
        val size = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(
            src, (src.width - size) / 2, (src.height - size) / 2, size, size,
        )
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)
        if (square !== src) square.recycle()
        out
    }.getOrNull()

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        runCatching { startActivityForResult(intent, REQ_PICK) }.onFailure {
            Toast.makeText(this, R.string.setting_image_none, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK) return
        val uri = data?.data ?: return
        if (!CustomVisualPref.importIslandDot(this, uri)) {
            Toast.makeText(this, R.string.setting_image_none, Toast.LENGTH_SHORT).show()
            return
        }
        refreshPreview()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_PICK = 4311
    }
}

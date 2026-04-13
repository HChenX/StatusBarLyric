/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/Block-Network/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as
 * published by Block-Network contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/Block-Network/StatusBarLyric/blob/main/LICENSE>.
 */

package statusbar.lyric.hook.module

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColor
import androidx.core.graphics.toColorInt
import androidx.core.util.Consumer
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.EzXHelper.moduleRes
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createAfterHook
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.finders.ConstructorFinder.`-Static`.constructorFinder
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP.config
import statusbar.lyric.hook.BaseHook
import statusbar.lyric.hook.module.xiaomi.XiaomiHooks
import statusbar.lyric.tools.BitmapTool.base64ToBitmap
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.LyricViewTools
import statusbar.lyric.tools.LyricViewTools.cancelAnimation
import statusbar.lyric.tools.LyricViewTools.hideView
import statusbar.lyric.tools.LyricViewTools.randomAnima
import statusbar.lyric.tools.LyricViewTools.showView
import statusbar.lyric.tools.Tools.callMethod
import statusbar.lyric.tools.Tools.existField
import statusbar.lyric.tools.Tools.getObjectField
import statusbar.lyric.tools.Tools.getObjectFieldIfExist
import statusbar.lyric.tools.Tools.goMainThread
import statusbar.lyric.tools.Tools.ifNotNull
import statusbar.lyric.tools.Tools.isLandscape
import statusbar.lyric.tools.Tools.isNotNull
import statusbar.lyric.tools.Tools.isNull
import statusbar.lyric.tools.Tools.observableChange
import statusbar.lyric.tools.Tools.shell
import statusbar.lyric.view.LyricSwitchView
import statusbar.lyric.view.TitleDialog
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class SystemUILyric : BaseHook() {
    private val context: Context by lazy { AndroidAppHelper.currentApplication() }

    private var lastLyric: String = ""
    private var lastColor: Int by observableChange(Color.WHITE) { oldValue, newValue ->
        if (oldValue == newValue) return@observableChange
        goMainThread {
            if (containers.isNotEmpty()) {
                containers.forEach { c ->
                    if (config.lyricColor.isEmpty()) {
                        c.lyricView.setTextColor(newValue)
                    }
                    if (config.iconColor.isEmpty()) {
                        c.iconView.setColorFilter(newValue, PorterDuff.Mode.SRC_IN)
                    }
                }
            }
        }
        "Changing Color: ${newValue.toColor()}".log()
    }
    private var title: String by observableChange("") { _, newValue ->
        if (!config.titleShowWithSameLyric && lastLyric == newValue) return@observableChange
        goMainThread {
            titleDialog.apply {
                if (newValue.isEmpty()) {
                    hideTitle()
                } else {
                    showTitle(newValue.trim())
                }
            }
        }
        "Changing Title: $newValue".log()
    }
    private var lastBase64Icon: String by observableChange("") { _, newValue ->
        goMainThread {
            base64ToBitmap(newValue).isNotNull { bmp ->
                if (containers.isNotEmpty()) {
                    containers.forEach { it.iconView.setImageBitmap(bmp) }
                }
            }
        }
        "Changing Icon".log()
    }
    private var canLoad: Boolean = true
    private var isScreenLocked: Boolean = false
    private var statusBarShowing: Boolean = true
    private var iconSwitch: Boolean = config.iconSwitch

    @Volatile
    var isMusicPlaying: Boolean = false

    @Volatile
    var isHiding: Boolean = false
    private var isRandomAnima: Boolean = false
    private var autoHideController: Any? = null
    private val isReady: Boolean get() = containers.isNotEmpty()

    private var theoreticalWidth: Int = 0
    private lateinit var point: Point

    private val displayMetrics: DisplayMetrics by lazy { context.resources.displayMetrics }
    private val displayWidth: Int by lazy { displayMetrics.widthPixels }
    private val displayHeight: Int by lazy { displayMetrics.heightPixels }

    // 独立控制每个状态栏歌词容器
    private data class LyricContainer(
        val clock: TextView,
        val target: ViewGroup,
        val iconView: ImageView,
        val lyricView: LyricSwitchView,
        val layout: LinearLayout
    )

    private val containers = mutableListOf<LyricContainer>()

    // 判断传入的 View 是否为任意容器的时钟视图
    private fun isClockView(view: View?): Boolean {
        if (view == null || containers.isEmpty()) return false
        return containers.any { it.clock == view }
    }

    // 创建歌词容器
    private fun createContainer(clock: TextView, target: ViewGroup): LyricContainer {
        val localIconView = ImageView(context).apply { visibility = View.GONE }
        val localLyricView = object : LyricSwitchView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
            }
        }.apply {
            setTypeface(clock.typeface ?: Typeface.DEFAULT)
            setSingleLine(true)
            setMaxLines(1)
        }
        val localLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(localIconView)
            addView(localLyricView)
            visibility = View.GONE
        }
        return LyricContainer(clock, target, localIconView, localLyricView, localLayout)
    }

    // 添加歌词容器
    private fun addContainerIfAbsent(clock: TextView, target: ViewGroup) {
        if (containers.any { it.target === target }) return
        val container = createContainer(clock, target)
        containers.add(container)
        goMainThread {
            runCatching { (container.layout.parent as? ViewGroup)?.removeView(container.layout) }
            container.target.addView(container.layout, 0)
        }
    }

    // 标题对话框
    private val titleDialog by lazy {
        TitleDialog(context)
    }

    ////////////////////////////// Hook //////////////////////////////////////
    private var defaultDisplay: Any? = null
    private var centralSurfacesImpl: Any? = null
    private var statusBatteryContainer: View? = null
    var notificationIconArea: View? = null
    var superIslandWidth: Int by observableChange(0) { oldValue, newValue ->
        if (oldValue == newValue) return@observableChange
        "superIslandWidth changed: $oldValue -> $newValue".log()
        goMainThread {
            if (containers.isNotEmpty()) {
                containers.forEach { c ->
                    val lyric = lastLyric
                    if (lyric.isEmpty()) return@forEach
                    val lyricWidth = getLyricWidth(c, lyric)
                    c.lyricView.width = lyricWidth
                    val i = theoreticalWidth - lyricWidth
                    if (i > 0 && lyricWidth > 0) {
                        if (config.dynamicLyricSpeed) {
                            val proportion = i / lyricWidth
                            val speed = (10 * proportion + 0.7f).coerceIn(0.3f, 5.0f)
                            c.lyricView.setScrollSpeed(speed)
                            "Dynamic mode (update) - Proportion: $proportion, Speed: $speed".log()
                        } else {
                            c.lyricView.setScrollSpeed(config.lyricSpeed.toFloat())
                        }
                    } else {
                        c.lyricView.setScrollSpeed(config.lyricSpeed.toFloat())
                    }
                    c.lyricView.post { c.lyricView.resumeScroll() }
                    c.lyricView.requestLayout()
                }
            }
        }
    }

    override fun init() {
        "Initializing Hook".log()
        Application::class.java.methodFinder().filterByName("attach").single().createHook {
            after {
                hook()
                if (!canLoad) return@after
                registerSuperLyric(it.args[0] as Context)
            }
        }
    }

    private fun hook() {
        "Hooking clock method".log()
        val miuiClock = loadClassOrNull("com.android.systemui.statusbar.views.MiuiClock")
        loadClassOrNull("com.android.systemui.statusbar.views.MiuiNotificationHeaderClock")
        miuiClock?.constructorFinder()?.filterByParamCount(3)?.filterByParamTypes { it[0] == Context::class.java }?.single()
            ?.createAfterHook {
                val miuiClock = it.thisObject as TextView
                val miuiClockName = miuiClock.resources.getResourceEntryName(miuiClock.id)
                if (miuiClockName == "clock" || miuiClockName == "pad_clock") {
                    miuiClock.post {
                        "Running MiuiClock".log()
                        if ((miuiClock.parent is LinearLayout).not()) {
                            "Parent is not LinearLayout, cannot load lyric view.".log()
                            return@post
                        }
                        val parent = (miuiClock.parent as LinearLayout).apply { gravity = Gravity.CENTER_VERTICAL }
                        addContainerIfAbsent(miuiClock, parent)
                        if (canLoad) {
                            lyricHookInit()
                            canLoad = false
                        }
                    }
                }
            }
    }

    private fun lyricHookInit() {
        "lyricHook init".log()

        View::class.java.methodFinder().filterByName("setVisibility").single().createHook {
            before {
                val view = it.thisObject as View
                if (statusBatteryContainer.isNotNull()) {
                    if (statusBatteryContainer != view) return@before
                    if (!isMusicPlaying) return@before

                    val visibility = it.args[0] == View.VISIBLE
                    if (visibility) {
                        updateLyricState()
                    } else {
                        updateLyricState(showLyric = false)
                    }
                } else {
                    val idName = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
                    if (idName.isNotNull() && idName == "system_icons") {
                        statusBatteryContainer = view
                    }
                }
            }
        }

        // 限制可见性更改
        if (config.limitVisibilityChange) {
            moduleRes.getString(R.string.limit_visibility_change).log()
            View::class.java.methodFinder().filterByName("setVisibility").single().createHook {
                before {
                    if (isMusicPlaying && !isHiding) {
                        if (it.args[0] == View.VISIBLE) {
                            val view = it.thisObject as View
                            if (isReady && config.hideTime && isClockView(view)) {
                                it.args[0] = View.GONE
                            }
                        }
                    }
                }
            }
        }

        // 状态栏图标颜色更改
        loadClassOrNull("com.android.systemui.statusbar.phone.DarkIconDispatcherImpl").isNotNull {
            it.methodFinder().filterByName("applyDarkIntensity").filterNonAbstract().single().createHook {
                after {
                    if (!isMusicPlaying) return@after

                    val mIconTint = it.thisObject.objectHelper().getObjectOrNullAs<Int>("mIconTint")
                    lastColor = mIconTint ?: Color.WHITE
                }
            }
        }

        // 触摸监听
        loadClassOrNull("com.android.systemui.statusbar.phone.PhoneStatusBarView").isNotNull {
            it.methodFinder().filterByName("onTouchEvent").single().createHook {
                before {
                    if (!isReady) return@before

                    val motionEvent = it.args[0] as MotionEvent
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            point = Point(motionEvent.rawX.toInt(), motionEvent.rawY.toInt())
                        }

                        MotionEvent.ACTION_MOVE -> {}

                        MotionEvent.ACTION_UP -> {
                            val isMove = abs(point.y - motionEvent.rawY.toInt()) > 50 || abs(point.x - motionEvent.rawX.toInt()) > 50
                            val isLongChick = motionEvent.eventTime - motionEvent.downTime > 500
                            when (isMove) {
                                true -> {
                                    if (config.slideStatusBarCutSongs) {
                                        if (isMusicPlaying) {
                                            if (isHiding) return@before

                                            if (abs(point.y - motionEvent.rawY.toInt()) <= config.slideStatusBarCutSongsYRadius) {
                                                val i = point.x - motionEvent.rawX.toInt()
                                                if (abs(i) > config.slideStatusBarCutSongsXRadius) {
                                                    moduleRes.getString(R.string.slide_status_bar_cut_songs).log()
                                                    if (i > 0) {
                                                        shell("input keyevent 87", false)
                                                    } else {
                                                        shell("input keyevent 88", false)
                                                    }
                                                    it.result = true
                                                }
                                            }
                                        }
                                    }
                                }

                                false -> {
                                    when (isLongChick) {
                                        true -> {
                                            if (config.longClickStatusBarStop) {
                                                if (isHiding) return@before

                                                moduleRes.getString(R.string.long_click_status_bar_stop).log()
                                                shell("input keyevent 85", false)
                                                it.result = true
                                            }
                                        }

                                        false -> {
                                            if (config.clickStatusBarToHideLyric) {
                                                if (!isMusicPlaying) return@before

                                                moduleRes.getString(R.string.click_status_bar_to_hide_lyric).log()
                                                val x = motionEvent.x.toInt()
                                                val y = motionEvent.y.toInt()

                                                if (isHiding) {
                                                    it.result = true
                                                    updateLyricState()
                                                    autoHideStatusBarInFullScreenModeIfNeed()
                                                } else {
                                                    var hit = false
                                                    if (containers.isNotEmpty()) {
                                                        containers.forEach { c ->
                                                            val left = c.layout.left
                                                            val top = c.layout.top
                                                            val right = c.layout.right
                                                            val bottom = c.layout.bottom
                                                            if (x in left..right && y in top..bottom) {
                                                                hit = true
                                                                return@forEach
                                                            }
                                                        }
                                                    }

                                                    if (hit) {
                                                        it.result = true
                                                        updateLyricState(showLyric = false)
                                                        autoHideStatusBarInFullScreenModeIfNeed()
                                                    }
                                                    "Change to hide LyricView: $isHiding".log()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 屏幕状态
        loadClassOrNull("com.android.systemui.statusbar.phone.CentralSurfacesImpl").isNotNull {
            it.constructorFinder().singleOrNull().ifNotNull {
                it.createHook {
                    after {
                        centralSurfacesImpl = it.thisObject
                        autoHideController = it.thisObject.getObjectField("mAutoHideController")
                        val mStatusBarModeRepository = it.thisObject.getObjectFieldIfExist("mStatusBarModeRepository")
                        defaultDisplay = mStatusBarModeRepository?.getObjectFieldIfExist("defaultDisplay")
                    }
                }
            }
        }

        // 屏幕方向
        loadClassOrNull("com.android.systemui.SystemUIApplication").isNotNull { clazz ->
            clazz.methodFinder().filterByName("onConfigurationChanged").single().createHook {
                after {
                    "onConfigurationChanged".log()
                    val newConfig = it.args[0] as Configuration

                    if (
                        newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                        newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
                    ) {
                        if (!isReady) return@after
                        updateLyricState()
                    }
                }
            }
        }

        // Xiaomi 相关 Hook
        XiaomiHooks.init(this)

        // 更新配置
        updateConfig()
    }

    // 适合考虑状态的更新
    fun updateLyricState(showLyric: Boolean = true, delay: Int = 0) {
        if (
            isInFullScreenMode() &&
            (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                    context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        ) {
            if (statusBarShowing && showLyric && canShowLyric()) {
                showLyric(lastLyric, delay)
                "StatusBar state is showing".log()
            } else {
                hideLyric()
                if (!statusBarShowing) "StatusBar state is hiding".log()
            }
        } else {
            if (showLyric && canShowLyric()) {
                showLyric(lastLyric, delay)
            } else {
                hideLyric()
            }
        }
    }

    private fun canShowLyric(): Boolean {
        return isMusicPlaying
    }

    private fun isInFullScreenMode(): Boolean {
        var isInFullScreenMode = false

        if (centralSurfacesImpl.existField("mIsFullscreen")) {
            isInFullScreenMode = centralSurfacesImpl?.getObjectField("mIsFullscreen") as Boolean
            statusBarShowing = centralSurfacesImpl?.getObjectField("mTransientShown") as Boolean
        } else if (defaultDisplay.existField("isInFullscreenMode")) {
            val isInFullscreenMode = defaultDisplay?.getObjectField("isInFullscreenMode")
            isInFullScreenMode = isInFullscreenMode?.getObjectField($$$"$$delegate_0")?.callMethod("getValue") as Boolean

            val isTransientShown = defaultDisplay?.getObjectField("isTransientShown")
            statusBarShowing = isTransientShown?.getObjectField($$$"$$delegate_0")?.callMethod("getValue") as Boolean
        }

        return isInFullScreenMode
    }

    private fun autoHideStatusBarInFullScreenModeIfNeed() {
        if (autoHideController == null) return
        if (!isInFullScreenMode()) return

        autoHideController!!.callMethod("touchAutoHide")
    }

    private var playingApp: String = ""
    private var updateConfig: UpdateConfig = UpdateConfig()
    private var screenLockReceiver: ScreenLockReceiver = ScreenLockReceiver()
    private val timeoutRestore: Int = 0
    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == timeoutRestore && config.timeoutRestore) {
                lastLyric = ""
                playingApp = ""
                updateLyricState(showLyric = false)
                "Timeout restore".log()
            }
        }
    }
    private var lastRunnable: Runnable? = null
    private val showTitleConsumer: Consumer<SuperLyricData> = object : Consumer<SuperLyricData> {
        override fun accept(value: SuperLyricData) {
            if (!isMusicPlaying) return
            this@SystemUILyric.title = value.title!!
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerSuperLyric(context: Context) {
        SuperLyricHelper.registerReceiver(object : ISuperLyricReceiver.Stub() {
            override fun onStop(publisher: String?, data: SuperLyricData?) {
                if (data.isNull() || !isReady) return
                if (playingApp.isNotEmpty() && playingApp != publisher) return

                isMusicPlaying = false
                lastLyric = ""
                playingApp = ""
                updateLyricState(showLyric = false)
                if (lastRunnable.isNotNull()) handler.removeCallbacks(lastRunnable!!)
                if (handler.hasMessages(timeoutRestore)) handler.removeMessages(timeoutRestore)
            }

            override fun onLyric(publisher: String?, data: SuperLyricData?) {
                if (data == null) return
                if (!isReady) return

                isMusicPlaying = true
                playingApp = publisher!!

                if (config.titleSwitch && data.hasTitle()) {
                    if (title != data.title) {
                        if (lastRunnable.isNotNull()) handler.removeCallbacks(lastRunnable!!)
                        lastRunnable = Runnable { showTitleConsumer.accept(data) }
                        if (lastRunnable.isNotNull()) handler.postDelayed(lastRunnable!!, 800)
                        ("Title: " + data.title).log()
                    }
                }

                if (!data.hasLyric()) return
                lastLyric = data.lyric!!.text
                "Lyric: $lastLyric".log()

                if (data.hasBase64Icon()) {
                    changeIcon(data.base64Icon!!)
                }
                updateLyricState(delay = data.lyric!!.delay.toInt())

                if (handler.hasMessages(timeoutRestore)) {
                    handler.removeMessages(timeoutRestore)
                    handler.sendEmptyMessageDelayed(timeoutRestore, 10000L)
                } else handler.sendEmptyMessageDelayed(timeoutRestore, 10000L)
            }
        })

        context.registerReceiver(updateConfig, IntentFilter("updateConfig"), Context.RECEIVER_EXPORTED)

        if (config.hideLyricWhenLockScreen) {
            val screenLockFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(screenLockReceiver, screenLockFilter, Context.RECEIVER_EXPORTED)
        }

        "Register SuperLyric".log()
    }

    // 显示歌词
    private fun showLyric(lyric: String, delay: Int = 0) {
        if (!isReady || !isMusicPlaying || lyric.isEmpty() || isScreenLocked) return

        "Showing LyricView".log()
        goMainThread {
            isHiding = false
            if (containers.isNotEmpty()) {
                containers.forEach { c ->
                    if (config.lyricColor.isEmpty()) {
                        c.lyricView.setTextColor(c.clock.currentTextColor)
                    } else {
                        c.lyricView.setTextColor(config.lyricColor.toColorInt())
                    }
                    if (config.iconColor.isEmpty()) {
                        c.iconView.setColorFilter(c.clock.currentTextColor, PorterDuff.Mode.SRC_IN)
                    }
                    c.lyricView.apply {
                        val lyricWidth = getLyricWidth(c, lyric)
                        width = lyricWidth
                        val i = theoreticalWidth - lyricWidth
                        "Lyric width: $lyricWidth, Theoretical width: $theoreticalWidth, i: $i".log()
                        if (i > 0 && lyricWidth > 0) {
                            if (delay > 0) {
                                val durationInSeconds = delay / 1000f
                                if (durationInSeconds > 0) {
                                    val speed = 0.3f + (i.toFloat() / lyricWidth) * (5f / durationInSeconds)
                                    val boundedSpeed = speed.coerceIn(0.3f, 5.0f)
                                    setScrollSpeed(boundedSpeed)
                                    "Delay mode - Duration: ${durationInSeconds}, Speed: $boundedSpeed".log()
                                }
                            } else if (config.dynamicLyricSpeed) {
                                val proportion = i / lyricWidth
                                val speed = (10 * proportion + 0.7f).coerceIn(0.3f, 5.0f)
                                setScrollSpeed(speed)
                                "Dynamic mode - Proportion: $proportion, Speed: $speed".log()
                            }
                        } else {
                            setScrollSpeed(config.lyricSpeed.toFloat())
                        }
                        if (isRandomAnima) {
                            val animation = randomAnima
                            val interpolator = config.lyricInterpolator
                            val duration = config.animationDuration
                            inAnimation = LyricViewTools.switchViewInAnima(animation, interpolator, duration)
                            outAnimation = LyricViewTools.switchViewOutAnima(animation, duration)
                        }
                        setText(lyric)
                    }
                    c.layout.cancelAnimation()
                    c.layout.showView()
                    if (config.hideTime) {
                        c.clock.hideView()
                    }
                }
            }
            XiaomiHooks.getNotificationBigTime()?.visibility = View.GONE
        }
    }

    // 更改图标
    private fun changeIcon(base64Icon: String) {
        if (!iconSwitch || !isMusicPlaying) return

        // 针对椒盐音乐（com.salt.music），优先使用预设图标而不是 SuperLyric 提供的 base64
        lastBase64Icon = if (playingApp == "com.salt.music") {
            config.changeAllIcons.ifEmpty { config.getDefaultIcon(playingApp) }
        } else {
            config.changeAllIcons.ifEmpty { base64Icon.ifEmpty { config.getDefaultIcon(playingApp) } }
        }
    }

    // 隐藏歌词
    private fun hideLyric() {
        if (!isReady || isHiding) return
        isHiding = true

        "Hiding LyricView".log()
        goMainThread {
            if (containers.isNotEmpty()) {
                containers.forEach { c ->
                    c.clock.showView()
                    c.lyricView.setText("")
                    c.layout.hideView(false)
                }
            }
            if (config.titleSwitch) titleDialog.hideTitle()
            notificationIconArea?.showView()
            XiaomiHooks.getNotificationBigTime()?.visibility = View.VISIBLE
        }
    }

    private fun updateConfig() {
        "Updating Config".log()
        config.update()
        goMainThread {
            runCatching {
                val base = XiaomiHooks.getLastIslandWidth()
                superIslandWidth = if (base > 0) base + config.islandOffset else 0
            }
            if (containers.isNotEmpty()) {
                containers.forEach { c ->
                    c.lyricView.apply {
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_SHIFT,
                            if (config.lyricSize == 0) c.clock.textSize else config.lyricSize.toFloat()
                        )
                        setMargins(
                            config.lyricStartMargins,
                            config.lyricTopMargins,
                            config.lyricEndMargins,
                            config.lyricBottomMargins
                        )
                        if (config.lyricColor.isEmpty()) {
                            setTextColor(c.clock.currentTextColor)
                        } else {
                            setTextColor(config.lyricColor.toColorInt())
                        }
                        setLetterSpacings(config.lyricLetterSpacing / 100f)
                        setStrokeWidth(config.lyricStrokeWidth / 100f)
                        if (!config.dynamicLyricSpeed) setScrollSpeed(config.lyricSpeed.toFloat())
                        if (config.lyricBackgroundColor.isNotEmpty()) {
                            val solidColor = config.lyricBackgroundColor.trim().split(",").first().trim().toColorInt()
                            if (config.lyricBackgroundRadius != 0) {
                                setBackgroundColor(Color.TRANSPARENT)
                                background = GradientDrawable().apply {
                                    cornerRadius = config.lyricBackgroundRadius.toFloat()
                                    setColor(solidColor)
                                }
                            } else {
                                setBackgroundColor(solidColor)
                            }
                        }
                        val animation = config.lyricAnimation
                        isRandomAnima = animation == 11
                        if (!isRandomAnima) {
                            val interpolator = config.lyricInterpolator
                            val duration = config.animationDuration
                            inAnimation = LyricViewTools.switchViewInAnima(animation, interpolator, duration)
                            outAnimation = LyricViewTools.switchViewOutAnima(animation, duration)
                        }
                        runCatching {
                            val file = File("${context.filesDir.path}/font")
                            if (file.exists() && file.canRead()) {
                                setTypeface(Typeface.createFromFile(file))
                            }
                        }
                    }
                    val lyric = lastLyric
                    if (lyric.isNotEmpty()) {
                        val lyricWidth = getLyricWidth(c, lyric)
                        c.lyricView.width = lyricWidth
                        val i = theoreticalWidth - lyricWidth
                        if (i > 0 && lyricWidth > 0) {
                            if (config.dynamicLyricSpeed) {
                                val proportion = i / lyricWidth
                                val speed = (10 * proportion + 0.7f).coerceIn(0.3f, 5.0f)
                                c.lyricView.setScrollSpeed(speed)
                            } else {
                                c.lyricView.setScrollSpeed(config.lyricSpeed.toFloat())
                            }
                        } else {
                            c.lyricView.setScrollSpeed(config.lyricSpeed.toFloat())
                        }
                        c.lyricView.post { c.lyricView.resumeScroll() }
                        c.lyricView.requestLayout()
                    }
                    if (config.iconSwitch) {
                        c.iconView.apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.MATCH_PARENT
                            ).apply {
                                setMargins(
                                    config.iconStartMargins,
                                    config.iconTopMargins,
                                    0,
                                    config.iconBottomMargins
                                )
                                if (config.iconSize == 0) {
                                    width = c.clock.height / 2
                                    height = c.clock.height / 2
                                } else {
                                    width = config.iconSize
                                    height = config.iconSize
                                }
                            }
                            if (config.iconColor.isEmpty()) {
                                setColorFilter(c.clock.currentTextColor, PorterDuff.Mode.SRC_IN)
                            } else {
                                setColorFilter(config.iconColor.toColorInt(), PorterDuff.Mode.SRC_IN)
                            }
                            if (config.iconBgColor.isEmpty()) {
                                setBackgroundColor(Color.TRANSPARENT)
                            } else {
                                setBackgroundColor(config.iconBgColor.toColorInt())
                            }
                            showView()
                        }
                    } else {
                        c.iconView.hideView()
                    }
                }
            }
        }
    }

    // 获取歌词宽度
    private fun getLyricWidth(container: LyricContainer, lyric: String): Int {
        "Getting Lyric Width (container)".log()
        val textView = TextView(context).apply {
            setTextSize(
                TypedValue.COMPLEX_UNIT_SHIFT,
                if (config.lyricSize == 0) container.clock.textSize else config.lyricSize.toFloat()
            )
            setTypeface(container.clock.typeface ?: Typeface.DEFAULT)
            letterSpacing = config.lyricLetterSpacing / 100f
            paint.strokeWidth = config.lyricStrokeWidth / 100f
        }
        val textWidth = textView.paint.measureText(lyric).toInt()
        theoreticalWidth = textWidth
        return if (config.lyricWidth == 0) {
            val available = container.target.width - config.lyricStartMargins - config.lyricEndMargins - superIslandWidth
            val safeAvailable = kotlin.math.max(0, available)
            min(textWidth, safeAvailable)
        } else {
            if (config.fixedLyricWidth) {
                scaleWidth()
            } else {
                min(textWidth, scaleWidth())
            }
        }
    }

    private fun scaleWidth(): Int {
        "Scale Width".log()
        return (config.lyricWidth / 100f * if (context.isLandscape()) displayHeight else displayWidth).toInt()
    }

    inner class UpdateConfig : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("type")) {
                "normal" -> {
                    if (!isReady) return
                    updateConfig()
                }

                "change_font" -> {}
                "reset_font" -> {}
            }
        }
    }

    // 屏幕锁定状态监听
    inner class ScreenLockReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isScreenLocked = intent.action == Intent.ACTION_SCREEN_OFF
            "isScreenLocked: $isScreenLocked".log()
            if (isScreenLocked) {
                updateLyricState(showLyric = false)
            } else {
                if (isMusicPlaying && lastLyric.isNotEmpty()) {
                    updateLyricState()
                }
            }
        }
    }
}

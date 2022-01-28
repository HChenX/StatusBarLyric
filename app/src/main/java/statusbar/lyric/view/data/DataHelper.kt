@file:Suppress("DEPRECATION")

package statusbar.lyric.view.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import com.microsoft.appcenter.analytics.Analytics
import statusbar.lyric.BuildConfig
import statusbar.lyric.R
import statusbar.lyric.activity.SettingsActivity
import statusbar.lyric.config.IconConfig
import statusbar.lyric.utils.ActivityOwnSP
import statusbar.lyric.utils.ActivityUtils
import statusbar.lyric.utils.ShellUtils
import statusbar.lyric.utils.Utils
import statusbar.lyric.view.miuiview.MIUIDialog


enum class DataItem {
    Main, Menu, Custom, Author, CustomIcon
}

@SuppressLint("StaticFieldLeak")
object DataHelper {

    lateinit var currentActivity: SettingsActivity

    fun getItems(thisItems: DataItem = DataItem.Main): ArrayList<Item> = when (thisItems) {
        DataItem.Menu -> loadMenuItems()
        DataItem.Custom -> loadCustomItems()
        DataItem.Author -> loadAboutItems()
        DataItem.CustomIcon -> loadCustomIconItems()
        else -> loadItems()
    }

    fun getTitle(dataItem: DataItem): String = when (dataItem) {
        DataItem.Custom -> currentActivity.getString(R.string.Custom)
        DataItem.Author -> currentActivity.getString(R.string.About)
        DataItem.CustomIcon -> currentActivity.getString(R.string.IconSettings)
        else -> currentActivity.getString(R.string.AppName)
    }

    private fun loadAboutItems(): ArrayList<Item> {
        val itemList = arrayListOf<Item>()
        itemList.apply {
            add(Item(Text(resId = R.string.Developer, isTitle = true)))
            add(
                Item(
                    Text(null, onClickListener = {
                        ActivityUtils.openUrl(
                            currentActivity,
                            "https://github.com/577fkj"
                        )
                    }),
                    author = Author(
                        "577fkj",
                        tipsId = R.string.AboutTips1,
                        head = currentActivity.getDrawable(R.drawable.header_577fkj)!!
                    )
                )
            )
            add(
                Item(
                    Text(null, onClickListener = {
                        ActivityUtils.openUrl(
                            currentActivity,
                            "https://github.com/xiaowine"
                        )
                    }),
                    author = Author(
                        "xiaowine",
                        tipsId = R.string.AboutTips2,
                        head = currentActivity.getDrawable(R.drawable.header_xiaowine)!!
                    )
                )
            )
            add(Item(Text(resId = R.string.ThkListTips, isTitle = true), line = true))
            add(
                Item(
                    Text(
                        resId = R.string.ThkList,
                        showArrow = true,
                        onClickListener = {
                            ActivityUtils.openUrl(
                                currentActivity,
                                "https://github.com/577fkj/StatusBarLyric#%E6%84%9F%E8%B0%A2%E5%90%8D%E5%8D%95%E4%B8%8D%E5%88%86%E5%85%88%E5%90%8E"
                            )
                        })
                )
            )
            add(Item(Text(resId = R.string.SponsoredList, showArrow = true, onClickListener = {
                ActivityUtils.openUrl(
                    currentActivity,
                    "https://github.com/577fkj/StatusBarLyric/blob/Dev/doc/SPONSOR.md"
                )
            })))
            add(Item(Text(resId = R.string.Other, isTitle = true), line = true))
            add(
                Item(
                    Text(
                        resId = R.string.PrivacyPolicy,
                        showArrow = true,
                        onClickListener = {
                            ActivityUtils.openUrl(
                                currentActivity,
                                "https://github.com/577fkj/StatusBarLyric/blob/main/EUAL.md"
                            )
                        })
                )
            )
            add(
                Item(
                    Text(
                        resId = R.string.Source,
                        showArrow = true,
                        onClickListener = {
                            ActivityUtils.openUrl(
                                currentActivity,
                                "https://github.com/577fkj/StatusBarLyric"
                            )
                        })
                )
            )
            add(
                Item(
                    Text(
                        resId = R.string.Donate,
                        showArrow = true,
                        onClickListener = {
                            ActivityUtils.openUrl(
                                currentActivity,
                                "https://fkj2005.gitee.io/merger/"
                            )
                        })
                )
            )
        }
        return itemList
    }

    private fun loadMenuItems(): ArrayList<Item> {
        val itemList = arrayListOf<Item>()
        itemList.apply {
//            隐藏桌面图标
            add(
                Item(
                    Text(resId = R.string.HideDeskIcon),
                    Switch("hLauncherIcon", onCheckedChangeListener = { _, newValue -> // 隐藏桌面图标
                        val packageManager: PackageManager = currentActivity.packageManager
                        val mode: Int = if (newValue) {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        }
                        packageManager.setComponentEnabledSetting(
                            ComponentName(currentActivity, "${BuildConfig.APPLICATION_ID}.launcher"),
                            mode,
                            PackageManager.DONT_KILL_APP
                        )
                    })
                )
            )
//            重置模块
            add(Item(Text(resId = R.string.ResetModule, showArrow = true, onClickListener = {
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.ResetModuleDialog)
                    setMessage(R.string.ResetModuleDialogTips)
                    setButton(R.string.Ok) {
                        ActivityUtils.cleanConfig(
                            currentActivity
                        )
                        dismiss()
                    }
                    setCancelButton(R.string.Cancel) { dismiss() }
                    show()
                }
            })))
//            重启系统界面
            add(Item(Text(resId = R.string.ReStartSystemUI, onClickListener = { // 重启SystemUI
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.RestartUI)
                    setMessage(R.string.RestartUITips)
                    setButton(R.string.Ok) {
                        ShellUtils.voidShell("pkill -f com.android.systemui", true)
                        Analytics.trackEvent("重启SystemUI")
                        dismiss()
                    }
                    setCancelButton(R.string.Cancel) {
                        dismiss()
                    }
                    show()
                }
            }), null))
//            分割线
            add(Item(Text("Module Version", isTitle = true), null, line = true))
//            模块版本
            add(Item(Text("${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})-${BuildConfig.BUILD_TYPE}"), null))
        }
        return itemList
    }

    private fun loadCustomIconItems(): ArrayList<Item> {
        val itemList = arrayListOf<Item>()
        itemList.apply {
            val iconConfig = IconConfig(Utils.getSP(currentActivity, "Icon_Config"))
            for (icon in arrayOf("Netease", "KuGou", "QQMusic", "Myplayer", "MiGu", "Default")) {
                val drawable=  BitmapDrawable(Utils.stringToBitmap(iconConfig.getIcon(icon)))
                drawable.setTint(currentActivity.resources.getColor(android.R.color.background_dark))
                add(
                    Item(
                        Text(null, onClickListener = {
                            MIUIDialog(currentActivity).apply {
                                setTitle(icon)
                                setMessage(R.string.MakeIconTitle)
                                setEditText(iconConfig.getIcon(icon).toString(), "")
                                setButton(R.string.Ok) {
                                    ActivityOwnSP.ownSPConfig.setLyricPosition(getEditText().toInt())
                                    iconConfig.setIcon(icon, getEditText())
                                    dismiss()
                                }
                                setCancelButton(R.string.Cancel) { dismiss() }
                                show()
                            }
                        }),
                        author = Author(
                            icon,
                            head = drawable
                        )
                    )
                )
            }

        }
        return itemList
    }

    @SuppressLint("SetTextI18n")
    private fun loadCustomItems(): ArrayList<Item> {
        val itemList = arrayListOf<Item>()
        itemList.apply {
//            歌词大小
            if (!ActivityOwnSP.ownSPConfig.getUseSystemReverseColor()) {
                add(Item(Text(resId = R.string.LyricColor, showArrow = true, onClickListener = {
                    MIUIDialog(currentActivity).apply {
                        setTitle(R.string.LyricColor)
                        setMessage(R.string.LyricColorTips)
                        setEditText(ActivityOwnSP.ownSPConfig.getLyricColor(), "#FFFFFF")
                        setButton(R.string.Ok) {
                            if (getEditText() == "") {
                                ActivityOwnSP.ownSPConfig.setLyricColor("")
                            } else {
                                try {
                                    Color.parseColor(getEditText())
                                    ActivityOwnSP.ownSPConfig.setLyricColor(getEditText())
                                } catch (e: Throwable) {
                                    ActivityUtils.showToastOnLooper(
                                        currentActivity,
                                        currentActivity.getString(R.string.LyricColorError)
                                    )
                                    ActivityOwnSP.ownSPConfig.setLyricColor("")
                                }
                            }
                            dismiss()
                        }
                        setCancelButton(R.string.Cancel) { dismiss() }
                        show()
                    }
                })))
            }
            add(
                Item(Text(
                    "${currentActivity.getString(R.string.LyricSize)} (${
                        if (ActivityOwnSP.ownSPConfig.getLyricSize() != 0) ActivityOwnSP.ownSPConfig.getLyricSize() else currentActivity.getString(
                            R.string.Adaptive
                        )
                    })"
                ), seekBar = SeekBar(0, 100, ActivityOwnSP.ownSPConfig.getLyricSize()) { pos, text ->
                    ActivityOwnSP.ownSPConfig.setLyricSize(pos)
                    if (pos == 0) {
                        text.text = "${currentActivity.getString(R.string.LyricSize)} (${
                            currentActivity.getString(R.string.Adaptive)
                        })"
                    } else {
                        text.text = "${currentActivity.getString(R.string.LyricSize)} (${pos})"
                    }
                })
            )
//            歌词宽度
            add(
                Item(
                    Text(
                        "${currentActivity.getString(R.string.LyricWidth)} (${
                            if (ActivityOwnSP.ownSPConfig.getLyricWidth() != -1) ActivityOwnSP.ownSPConfig.getLyricWidth() else currentActivity.getString(
                                R.string.Adaptive
                            )
                        }"
                    ), seekBar = SeekBar(-1, 100, ActivityOwnSP.ownSPConfig.getLyricWidth()) { pos, text ->
                        ActivityOwnSP.ownSPConfig.setLyricWidth(pos)
                        if (pos == -1) {
                            text.text =
                                "${currentActivity.getString(R.string.LyricWidth)} (${currentActivity.getString(R.string.Adaptive)})"
                        } else {
                            text.text = "${currentActivity.getString(R.string.LyricWidth)} (${pos})"
                        }
                    })
            )
            // 歌词最大自适应宽度
            if (ActivityOwnSP.ownSPConfig.getLyricWidth() == -1) {
                add(
                    Item(Text(
                        "${currentActivity.getString(R.string.LyricAutoMaxWidth)} (${
                            if (ActivityOwnSP.ownSPConfig.getLyricMaxWidth() != -1) ActivityOwnSP.ownSPConfig.getLyricMaxWidth() else currentActivity.getString(
                                R.string.Adaptive
                            )
                        })"
                    ), seekBar = SeekBar(-1, 100, ActivityOwnSP.ownSPConfig.getLyricMaxWidth()) { pos, text ->
                        ActivityOwnSP.ownSPConfig.setLyricMaxWidth(pos)
                        if (pos == -1) {
                            text.text = "${currentActivity.getString(R.string.LyricAutoMaxWidth)} (${
                                currentActivity.getString(R.string.Adaptive)
                            })"
                        } else {
                            text.text = "${currentActivity.getString(R.string.LyricAutoMaxWidth)} (${pos})"
                        }
                    })
                )
            }
            // 歌词左右位置
            add(
                Item(Text(
                    "${currentActivity.getString(R.string.LyricPos)} (${
                        if (ActivityOwnSP.ownSPConfig.getLyricPosition() != 0) ActivityOwnSP.ownSPConfig.getLyricPosition() else currentActivity.getString(
                            R.string.Adaptive
                        )
                    })", onClickListener = {
                        MIUIDialog(currentActivity).apply {
                            setTitle(R.string.LyricPos)
                            setMessage(R.string.LyricPosTips)
                            setEditText(ActivityOwnSP.ownSPConfig.getLyricPosition().toString(), "0")
                            setButton(R.string.Ok) {
                                ActivityOwnSP.ownSPConfig.setLyricPosition(getEditText().toInt())
                                dismiss()
                            }
                            setCancelButton(R.string.Cancel) { dismiss() }
                            show()
                        }
                    }
                ), seekBar = SeekBar(-900, 900, ActivityOwnSP.ownSPConfig.getLyricPosition()) { pos, text ->
                    ActivityOwnSP.ownSPConfig.setLyricPosition(pos)
                    if (pos == 0) {
                        text.text = "${currentActivity.getString(R.string.LyricPos)} (${
                            currentActivity.getString(R.string.Adaptive)
                        })"
                    } else {
                        text.text = "${currentActivity.getString(R.string.LyricPos)} (${pos})"
                    }
                })
            )
//          歌词高度
            add(Item(Text(
                "${currentActivity.getString(R.string.LyricHigh)} (${
                    if (ActivityOwnSP.ownSPConfig.getLyricHigh() != 0) ActivityOwnSP.ownSPConfig.getLyricHigh() else currentActivity.getString(
                        R.string.Adaptive
                    )
                })", onClickListener = {
                    MIUIDialog(currentActivity).apply {
                        setTitle(R.string.LyricHigh)
                        setMessage(R.string.LyricHighTips)
                        setEditText(ActivityOwnSP.ownSPConfig.getLyricHigh().toString(), "0")
                        setButton(R.string.Ok) {
                            ActivityOwnSP.ownSPConfig.setLyricHigh(getEditText().toInt())
                            dismiss()
                        }
                        setCancelButton(R.string.Cancel) { dismiss() }
                        show()
                    }
                }
            ), seekBar = SeekBar(-100, 100, ActivityOwnSP.ownSPConfig.getLyricHigh()) { pos, text ->
                ActivityOwnSP.ownSPConfig.setLyricHigh(pos)
                if (pos == 0) {
                    text.text = "${currentActivity.getString(R.string.LyricHigh)} (${
                        currentActivity.getString(R.string.Adaptive)
                    })"
                } else {
                    text.text = "${currentActivity.getString(R.string.LyricHigh)} (${pos})"
                }
            })
            )

//            歌词颜色
            add(Item(Text(resId = R.string.LyricColor, onClickListener = {
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.LyricColor)
                    setMessage(R.string.LyricColorTips)
                    setEditText(ActivityOwnSP.ownSPConfig.getLyricColor(), "#C0C0C0")
                    setButton(R.string.Ok) {
                        ActivityOwnSP.ownSPConfig.setLyricColor(getEditText())
                        dismiss()
                    }
                    setCancelButton(R.string.Cancel) { dismiss() }
                    show()
                }
            })))

            //            歌词动效
            val anim = arrayListOf(
                currentActivity.getString(R.string.Off),
                currentActivity.getString(R.string.top),
                currentActivity.getString(R.string.lower),
                currentActivity.getString(R.string.left),
                currentActivity.getString(R.string.right),
                currentActivity.getString(R.string.random)
            )
            val dict: HashMap<String, String> = hashMapOf()
            dict["off"] = currentActivity.getString(R.string.Off)
            dict["top"] = currentActivity.getString(R.string.top)
            dict["lower"] = currentActivity.getString(R.string.lower)
            dict["left"] = currentActivity.getString(R.string.left)
            dict["right"] = currentActivity.getString(R.string.right)
            dict["random"] = currentActivity.getString(R.string.random)
            dict[currentActivity.getString(R.string.Off)] = "off"
            dict[currentActivity.getString(R.string.top)] = "top"
            dict[currentActivity.getString(R.string.lower)] = "lower"
            dict[currentActivity.getString(R.string.left)] = "left"
            dict[currentActivity.getString(R.string.right)] = "right"
            dict[currentActivity.getString(R.string.random)] = "random"
            dict[""] = "off"
            add(
                Item(
                    Text(resId = R.string.LyricsAnimation),
                    spinner = Spinner(
                        anim,
                        select = dict[ActivityOwnSP.ownSPConfig.getAnim()]!!,
                        context = currentActivity,
                        callBacks = {
                            ActivityOwnSP.ownSPConfig.setAnim(dict[it]!!)
                        })
                )
            )

//            隐藏时间
            add(Item(Text(resId = R.string.HideTime), Switch("HideTime", true)))
//            点击切换时间和歌词
            add(Item(Text(resId = R.string.ClickLyric), Switch("LSwitch")))
//            伪时间
            add(Item(Text(resId = R.string.pseudoTime), Switch("PseudoTime")))
//            伪时间格式
            add(Item(Text(resId = R.string.pseudoTimeStyle, showArrow = true, onClickListener = {
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.pseudoTimeStyleTips)
                    setEditText(
                        ActivityOwnSP.ownSPConfig.getPseudoTimeStyle(),
                        ""
                    )
                    setButton(R.string.Ok) {
                        ActivityOwnSP.ownSPConfig.setPseudoTimeStyle(getEditText())
                        dismiss()
                    }
                    setCancelButton(R.string.Cancel) { dismiss() }
                    show()
                }
            })))
//            魅族歌词滚动样式
            add(Item(Text(resId = R.string.MeizuStyle), Switch("LStyle", true)))
//            仅滚动一次
            if (!ActivityOwnSP.ownSPConfig.getLyricStyle()) add(
                Item(
                    Text(resId = R.string.lShowOnce),
                    Switch("LShowOnce", true)
                )
            )
//            歌词速度
            if (ActivityOwnSP.ownSPConfig.getLyricStyle()) {
                add(Item(Text(resId = R.string.LyricSpeed, showArrow = true, onClickListener = {
                    MIUIDialog(currentActivity).apply {
                        setTitle(R.string.LyricSpeed)
                        setEditText(ActivityOwnSP.ownSPConfig.getLyricSpeed().toString(), "1.0")
                        setButton(R.string.Ok) {
                            if (getEditText() == "") {
                                ActivityOwnSP.ownSPConfig.setLyricSpeed(1f)
                            } else {
                                ActivityOwnSP.ownSPConfig.setLyricSpeed(getEditText().toFloat())
                            }
                            dismiss()
                        }
                        setCancelButton(R.string.Cancel) { dismiss() }
                        show()
                    }
                })))
            }
//            图标分割线
            add(Item(Text(resId = R.string.IconSettings, isTitle = true), line = true))
//            图标大小
            add(
                Item(Text(
                    "${currentActivity.getString(R.string.IconSize)} (${
                        if (ActivityOwnSP.ownSPConfig.getIconSize() != 0) ActivityOwnSP.ownSPConfig.getIconSize() else currentActivity.getString(
                            R.string.Adaptive
                        )
                    })", onClickListener = {
                        MIUIDialog(currentActivity).apply {
                            setTitle(R.string.IconSize)
                            setMessage(R.string.LyricHighTips)
                            setEditText(ActivityOwnSP.ownSPConfig.getIconSize().toString(), "0")
                            setButton(R.string.Ok) {
                                ActivityOwnSP.ownSPConfig.setIconSize(getEditText().toInt())
                                dismiss()
                            }
                            setCancelButton(R.string.Cancel) { dismiss() }
                            show()
                        }
                    }

                ), seekBar = SeekBar(0, 100, ActivityOwnSP.ownSPConfig.getIconSize()) { pos, text ->
                    ActivityOwnSP.ownSPConfig.setIconSize(pos)
                    if (pos == 0) {
                        text.text = "${currentActivity.getString(R.string.IconSize)} (${
                            currentActivity.getString(R.string.Adaptive)
                        })"
                    } else {
                        text.text = "${currentActivity.getString(R.string.IconSize)} (${pos})"
                    }
                })
            )
//            歌词上下位置
            add(Item(Text(
                "${currentActivity.getString(R.string.IconHigh)} (${ActivityOwnSP.ownSPConfig.getIconHigh()})",
                onClickListener = {
                    MIUIDialog(currentActivity).apply {
                        setTitle(R.string.IconHigh)
                        setMessage(R.string.LyricSizeTips)
                        setEditText(ActivityOwnSP.ownSPConfig.getIconHigh().toString(), "7")
                        setButton(R.string.Ok) {
                            ActivityOwnSP.ownSPConfig.setIconHigh(getEditText().toInt())
                            dismiss()
                        }
                        setCancelButton(R.string.Cancel) { dismiss() }
                        show()
                    }
                }
            ), seekBar = SeekBar(-100, 100, ActivityOwnSP.ownSPConfig.getIconHigh()) { pos, text ->
                ActivityOwnSP.ownSPConfig.setIconHigh(pos)
                text.text = "${currentActivity.getString(R.string.IconHigh)} (${pos})"
            })
            )
//            歌词自动反色
            add(Item(Text(resId = R.string.IconAutoColors), Switch("IAutoColor", true)))
//            图标设置
            add(
                Item(
                    Text(
                        resId = R.string.IconSettings,
                        onClickListener = { currentActivity.showFragment(DataItem.CustomIcon) })
                )
            )
            add(Item(Text(null)))
        }
        return itemList
    }

    private fun loadItems(): ArrayList<Item> {
        val itemList = arrayListOf<Item>()
        itemList.apply {
//            使用说明
            add(Item(Text(resId = R.string.UseInfo, showArrow = true, onClickListener = { // 使用说明
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.VerExplanation)
                    setMessage(
                        String.format(
                            " %s [%s] %s",
                            currentActivity.getString(R.string.CurrentVer),
                            BuildConfig.VERSION_NAME,
                            currentActivity.getString(R.string.VerExp)
                        )
                    )
                    setButton(R.string.Done) {
                        dismiss()
                    }
                    show()
                }
            })))
//            模块注意事项
            add(Item(Text(resId = R.string.WarnExplanation, showArrow = true, onClickListener = { // 模块注意事项
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.WarnExplanation)
                    setMessage(
                        String.format(
                            " %s [%s] %s",
                            currentActivity.getString(R.string.CurrentVer),
                            BuildConfig.VERSION_NAME,
                            currentActivity.getString(R.string.WarnExp)
                        )
                    )
                    setButton(R.string.Done) {
                        dismiss()
                    }
                    show()
                }
            })))
//             基础设置分割线
            add(Item(Text(resId = R.string.BaseSetting, isTitle = true), line = true))
//             总开关
            add(Item(Text(resId = R.string.AllSwitch), Switch("LService")))
//             图标
            add(Item(Text(resId = R.string.LyricIcon), Switch("I", true)))
//             个性化
            add(
                Item(
                    Text(
                        resId = R.string.Custom,
                        showArrow = true,
                        onClickListener = { currentActivity.showFragment(DataItem.Custom) })
                )
            )
//             高级设置分割线
            add(Item(Text(resId = R.string.AdvancedSettings, isTitle = true), line = true))
//            防烧屏
            add(Item(Text(resId = R.string.AbScreen), Switch("AntiBurn")))
//              使用系统反色
            add(Item(Text(resId = R.string.UseSystemReverseColor), Switch("UseSystemReverseColor", true)))
//             暂停歌词自动关闭歌词
            add(Item(Text(resId = R.string.SongPauseCloseLyrics), Switch("LAutoOff")))
//             仅锁屏显示
            add(Item(Text(resId = R.string.UnlockShow), Switch("LockScreenOff")))
//             隐藏通知图标
            add(Item(Text(resId = R.string.AutoHideNotiIcon), Switch("HNoticeIcon")))
//             隐藏实时网速
            add(Item(Text(resId = R.string.HideNetWork), Switch("HNetSpeed")))
//            隐藏运营商名称
            add(Item(Text(resId = R.string.AutoHideCarrierName), Switch("HCuk")))
//             其他分割线
            add(Item(Text(resId = R.string.Other, isTitle = true), line = true))
//            自定义Hook
            add(Item(Text(resId = R.string.CustomHook, showArrow = true, onClickListener = {
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.HookSetTips)
                    setEditText(
                        ActivityOwnSP.ownSPConfig.getHook(),
                        currentActivity.getString(R.string.InputCustomHook)
                    )
                    setButton(R.string.Ok) {
                        ActivityOwnSP.ownSPConfig.setHook(getEditText())
                        ActivityUtils.showToastOnLooper(
                            currentActivity,
                            String.format(
                                "%s %s%s",
                                currentActivity.getString(R.string.HookSetTips),
                                if (ActivityOwnSP.ownSPConfig.getHook() == "") currentActivity.getString(R.string.Default) else ActivityOwnSP.ownSPConfig.getHook(),
                                currentActivity.getString(R.string.RestartSystemUI)
                            )
                        )
                        dismiss()
                    }
                    setCancelButton(R.string.Cancel) { dismiss() }
                    show()
                }
            })))
//            Debug模式
            add(Item(Text(resId = R.string.DebugMode), Switch("Debug")))
//            测试
            add(Item(Text(resId = R.string.Test, showArrow = true, onClickListener = {
                MIUIDialog(currentActivity).apply {
                    setTitle(R.string.Test)
                    setMessage(R.string.TestDialogTips)
                    setButton(R.string.Start) {
                        ActivityUtils.showToastOnLooper(currentActivity, "尝试唤醒界面")
                        currentActivity.sendBroadcast(
                            Intent().apply {
                                action = "Lyric_Server"
                                putExtra("Lyric_Type", "test")
                            }
                        )
                        dismiss()
                    }
                    setCancelButton(R.string.Back) { dismiss() }
                    show()
                }
            })))
//            关于分割线
            add(Item(Text(resId = R.string.About, isTitle = true), line = true))
//            检查更新
            add(
                Item(
                    Text(
                        "${currentActivity.getString(R.string.CheckUpdate)} (${BuildConfig.VERSION_NAME})",
                        onClickListener = {
                            ActivityUtils.showToastOnLooper(
                                currentActivity,
                                currentActivity.getString(R.string.StartCheckUpdate)
                            )
                            ActivityUtils.checkUpdate(currentActivity)
                        })
                )
            )
//            关于模块
            add(
                Item(
                    Text(
                        resId = R.string.AboutModule,
                        onClickListener = { currentActivity.showFragment(DataItem.Author) })
                )
            )
            add(Item(Text(null)))
        }
        return itemList
    }
}
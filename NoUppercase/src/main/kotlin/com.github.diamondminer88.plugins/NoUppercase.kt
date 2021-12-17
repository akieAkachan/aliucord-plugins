package com.github.diamondminer88.plugins

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.after
import com.aliucord.utils.DimenUtils
import com.aliucord.views.Button
import com.aliucord.widgets.BottomSheet
import com.discord.databinding.*
import com.discord.widgets.roles.RoleIconView
import com.discord.widgets.settings.WidgetSettings
import com.discord.widgets.user.presence.ViewHolderUserRichPresence
import com.lytefast.flexinput.R

private const val TEXT_SIZE_KEY = "textSizeMultiplier"
private const val DEFAULT_MULTIPLIER = 1f

@Suppress("unused")
@AliucordPlugin(requiresRestart = true)
class NoUppercase : Plugin() {
    init {
        settingsTab = SettingsTab(
            NoUppercaseSettings::class.java,
            SettingsTab.Type.BOTTOM_SHEET
        ).withArgs(settings)
    }

    private fun configureTextView(textView: TextView, multiplier: Float) {
        textView.isAllCaps = false
        textView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            textView.textSize * multiplier
        )
    }

    override fun start(ctx: Context) {
        // !!!!!!!!!! Before you yell at me for the following patches, these already work = no reason to change them !!!!!!!!!!
        // TODO: Patch TextView directly
        // *although my futile attempts did not work*

        // guild member list role headers
        patcher.after<WidgetChannelMembersListItemHeaderBinding>(
            LinearLayout::class.java,
            RoleIconView::class.java,
            TextView::class.java
        ) {
            configureTextView(this.c, settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER))
        }

        // channel list categories
        patcher.after<WidgetChannelsListItemCategoryBinding>(
            LinearLayout::class.java,
            ImageView::class.java,
            ImageView::class.java,
            TextView::class.java
        ) {
            configureTextView(this.d, settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER))
        }

        // user profile
        patcher.patch(
            WidgetUserSheetBinding::class.java.declaredConstructors[0], Hook {
                val thisObj = it.thisObject as WidgetUserSheetBinding
                val multiplier = settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER)
                configureTextView(thisObj.e, multiplier) // about me
                configureTextView(thisObj.m, multiplier) // connections
                configureTextView(thisObj.z, multiplier) // note
                configureTextView(thisObj.p, multiplier) // developer mode
            }
        )

        // user profile rich presence
        val richPresenceClass = ViewHolderUserRichPresence::class.java
        val fRichPresenceHeader = richPresenceClass.getDeclaredField("richPresenceHeader").apply { isAccessible = true }
        patcher.patch(richPresenceClass.declaredConstructors[0], Hook {
            val multiplier = settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER)
            configureTextView(fRichPresenceHeader.get(it.thisObject) as TextView, multiplier)
        })

        // friends list online/offline headers
        patcher.after<WidgetFriendsListAdapterItemHeaderBinding>(
            FrameLayout::class.java,
            TextView::class.java
        ) {
            configureTextView(this.b, settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER))
        }
        // friends list pending headers
        patcher.after<WidgetFriendsListExpandableHeaderBinding>(
            FrameLayout::class.java,
            TextView::class.java,
            TextView::class.java
        ) {
            configureTextView(this.c, settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER))
        }

        // user settings
        patcher.after<WidgetSettings>("onViewBound", View::class.java) {
            val multiplier = settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER)
            val view =
                ((it.args[0] as CoordinatorLayout).getChildAt(1) as NestedScrollView)
                    .getChildAt(0) as LinearLayoutCompat

            val stringIds = listOf(
                "user_settings_header",
                "nitro_header",
                "app_settings_header",
                "developer_options_header",
                "app_info_header"
            )
            stringIds
                .map { id -> Utils.getResId(id, "id") }
                .forEachIndexed { index, id ->
                    val child = view.findViewById<TextView>(id)
                    if (child == null) {
                        logger.error("Failed to find TextView ${stringIds[index]}", null)
                        return@forEachIndexed
                    }
                    configureTextView(child, multiplier)
                }
        }

        // invite cards
        patcher.patch(WidgetChatListAdapterItemInviteBinding::class.java.declaredConstructors[0], Hook {
            val thisObj = it.thisObject as WidgetChatListAdapterItemInviteBinding
            configureTextView(thisObj.c, settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER))
        })
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}

@SuppressLint("SetTextI18n")
class NoUppercaseSettings(private val settings: SettingsAPI) : BottomSheet() {
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        val ctx = view.context

        val multiplier = settings.getFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER)

        val currentSize = TextView(ctx, null, 0, R.i.UiKit_TextView).apply {
            text = "${multiplier}x"
            width = DimenUtils.dpToPx(43)
        }

        val slider = SeekBar(ctx, null, 0, R.i.UiKit_SeekBar).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            max = 200
            progress = (multiplier * 100).toInt()
            setPadding(DimenUtils.dpToPx(12), 0, DimenUtils.dpToPx(12), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    currentSize.text = "${progress.div(100f)}x"
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) =
                    settings.setFloat(TEXT_SIZE_KEY, progress.div(100f))
            })
        }

        val resetButton = Button(ctx).apply {
            text = "Reset"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(DimenUtils.dpToPx(12), 0, DimenUtils.dpToPx(12), 0)
            }
            setOnClickListener {
                currentSize.text = "1.0x"
                slider.progress = 100
                settings.setFloat(TEXT_SIZE_KEY, DEFAULT_MULTIPLIER)
            }
        }

        addView(TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Label).apply {
            text = "Header size (multiplier)"
        })

        addView(LinearLayout(ctx, null, 0, R.i.UiKit_Settings_Item).apply {
            addView(currentSize)
            addView(slider)
        })

        addView(resetButton)

        addView(TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Label).apply {
            text = "Changes will fully apply after reloading Discord"
            textSize = DimenUtils.dpToPx(4).toFloat()
        })
    }
}

// doesn't work
//        patcher.patch(TextView::class.java.getDeclaredMethod("setAllCaps", Boolean::class.javaPrimitiveType), PreHook {
//            logger.info("here")
//            it.args[0] = false
//        })

// doesn't work
//        patcher.patch(
//            TextView::class.java.getDeclaredMethod(
//                "setTransformationMethod",
//                TransformationMethod::class.java
//            ), Hook {
//                logger.info((it.thisObject as TextView).text.toString())
//                if (it.args[0] is AllCapsTransformationMethod) it.args[0] = null
//            })

//        val textAppearanceAttributesClass =
//            Class.forName("android.widget.TextView\$TextAppearanceAttributes")

// doesn't work
//        patcher.patch(
//            TextView::class.java.getDeclaredMethod(
//                "readTextAppearance",
//                Context::class.java,
//                TypedArray::class.java,
//                textAppearanceAttributesClass,
//                Boolean::class.javaPrimitiveType
//            ), Hook {
////                val thisObj = it.thisObject as TextView
//                ReflectUtils.setField(TextView::class.java, it.thisObject, "mAllCaps", false)
//            }
//        )

// doesn't work
//        patcher.patch(textAppearanceAttributesClass.constructors[0], Hook {
//            ReflectUtils.setField(textAppearanceAttributesClass, it.thisObject, "mAllCaps", false)
//        })

// can't find the private method
//        patcher.patch(
//            TextView::class.java.declaredMethods.first { it.name == "readTextAppearance" },
//            Hook {
////            ReflectUtils.setField(textAppearanceAttributesClass, it.thisObject, "mAllCaps", false)
//                logger.info("here")
//
//            })

// readTextAppearance isn't showing
//        TextView::class.java.declaredMethods.forEach {
//            if (it.toString().contains("readTextAppearance"))
//                logger.info(it.toString())
//        }

// fails as well don't remember why
//        patcher.patch(
//            TextView::class.java.getDeclaredMethod(
//                "applyTextAppearance",
//                textAppearanceAttributesClass,
//            ), PreHook {
//                ReflectUtils.setField(textAppearanceAttributesClass, it.args[0], "mAllCaps", false)
//            }
//        )

//        patcher.patch(
//            TextView::class.java.getDeclaredConstructor(
//                Context::class.java,
//                AttributeSet::class.java,
//                Int::class.javaPrimitiveType
//            ), Hook {
//                val attr = it.args[1] as AttributeSet
//                logger.info(attr.styleAttribute.toString())
//            })

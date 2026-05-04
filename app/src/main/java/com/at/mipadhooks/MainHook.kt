package com.at.mipadhooks

import android.app.AndroidAppHelper
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    private val fnOnMap: Map<Int, Pair<Int, Int>> = mapOf(
        KeyEvent.KEYCODE_ESCAPE to Pair(KeyEvent.KEYCODE_GRAVE, 41),
        KeyEvent.KEYCODE_1 to Pair(KeyEvent.KEYCODE_F1, 59),
        KeyEvent.KEYCODE_2 to Pair(KeyEvent.KEYCODE_F2, 60),
        KeyEvent.KEYCODE_3 to Pair(KeyEvent.KEYCODE_F3, 61),
        KeyEvent.KEYCODE_4 to Pair(KeyEvent.KEYCODE_F4, 62),
        KeyEvent.KEYCODE_5 to Pair(KeyEvent.KEYCODE_F5, 63),
        KeyEvent.KEYCODE_6 to Pair(KeyEvent.KEYCODE_F6, 64),
        KeyEvent.KEYCODE_7 to Pair(KeyEvent.KEYCODE_F7, 65),
        KeyEvent.KEYCODE_8 to Pair(KeyEvent.KEYCODE_F8, 66),
        KeyEvent.KEYCODE_9 to Pair(KeyEvent.KEYCODE_F9, 67),
        KeyEvent.KEYCODE_0 to Pair(KeyEvent.KEYCODE_F10, 68),
        KeyEvent.KEYCODE_MINUS to Pair(KeyEvent.KEYCODE_F11, 87),
        KeyEvent.KEYCODE_EQUALS to Pair(KeyEvent.KEYCODE_F12, 88),
        KeyEvent.KEYCODE_DEL to Pair(KeyEvent.KEYCODE_FORWARD_DEL, 111),
        KeyEvent.KEYCODE_DPAD_UP to Pair(KeyEvent.KEYCODE_PAGE_UP, 104),
        KeyEvent.KEYCODE_DPAD_DOWN to Pair(KeyEvent.KEYCODE_PAGE_DOWN, 109),
        KeyEvent.KEYCODE_DPAD_LEFT to Pair(KeyEvent.KEYCODE_MOVE_HOME, 102),
        KeyEvent.KEYCODE_DPAD_RIGHT to Pair(KeyEvent.KEYCODE_MOVE_END, 107),
    )
    private val fnTrigger: Int = KeyEvent.KEYCODE_ALT_RIGHT
    private var fnOn: Boolean = false
    private var lastDownKey: Int = -1

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") {
            return
        }

        findClass("com.android.server.input.config.InputCommonConfig", lpparam.classLoader)
            .declaredMethods
            .first { it.name == "setPadMode" }
            .also { method ->
                hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[0] = false
                        }
                    },
                )
            }

        findClass("com.android.server.SystemServerImpl", lpparam.classLoader)
            .declaredMethods
            .first { it.name == "addMagicPointerManagerService" }
            .also { method ->
                hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = null
                        }
                    },
                )
            }

        findClass("com.android.server.policy.PhoneWindowManager", lpparam.classLoader)
            .declaredMethods
            .first { it.name == "interceptKeyBeforeDispatching" }
            .also { method ->
                hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val event = param.args[1] as KeyEvent
                            val code = event.keyCode
                            val action = event.action

                            if (action == KeyEvent.ACTION_DOWN) {
                                lastDownKey = code
                            }

                            if (code == fnTrigger) {
                                param.result = -1L
                                handleToggleFnKey(event)
                                return
                            }

                            if (fnOn && code in fnOnMap.keys) {
                                param.result = -1L
                                val (key, scan) = fnOnMap.getOrDefault(event.keyCode, null)
                                    ?: return
                                handleFnKey(key, scan, event, lpparam)
                                return
                            }

                            if (event.isCtrlPressed && code == KeyEvent.KEYCODE_SPACE) {
                                param.result = -1L
                                handleCtrlShortcut(event)
                                return
                            }
                        }
                    },
                )
            }

        XposedBridge.log("Hooked ${lpparam.packageName} success.")
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                AndroidAppHelper.currentApplication(),
                message,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun handleToggleFnKey(event: KeyEvent) {
        if (lastDownKey == KeyEvent.KEYCODE_ALT_RIGHT && event.action == KeyEvent.ACTION_UP) {
            fnOn = !fnOn
            if (fnOn) {
                toast("Fn key ON")
            } else {
                toast("Fn key OFF")
            }
        }
    }

    private fun handleFnKey(
        key: Int,
        scan: Int,
        event: KeyEvent,
        lpparam: XC_LoadPackage.LoadPackageParam,
    ) {
        val newKeyEvent = KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            key,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            scan,
            event.flags,
            event.source,
        )

        val inputManagerClass = findClass(
            "android.hardware.input.InputManager",
            lpparam.classLoader,
        )
        val inputManager = callStaticMethod(
            inputManagerClass,
            "getInstance",
        )
        callMethod(inputManager, "injectInputEvent", newKeyEvent, 0)
    }

    private fun handleCtrlShortcut(event: KeyEvent) {
        if (lastDownKey == KeyEvent.KEYCODE_SPACE && event.action == KeyEvent.ACTION_UP) {
            val contentResolver = AndroidAppHelper.currentApplication().contentResolver
            val enabledInputMethods = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS,
            ).split(":")
            val defaultInputMethod = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            )
            val nextInputMethodIndex = enabledInputMethods.indexOf(defaultInputMethod) + 1
            val nextInputMethod = enabledInputMethods.getOrNull(nextInputMethodIndex)
                ?: enabledInputMethods[0]
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                nextInputMethod,
            )
            toast("Input method to: $nextInputMethod")
        }
    }
}

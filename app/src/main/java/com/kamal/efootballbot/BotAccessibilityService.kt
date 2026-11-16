package com.kamal.efootballbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.cos
import kotlin.math.sin

class BotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BotAccessibilityService? = null
    }

    private var currentJoyStroke: GestureDescription.StrokeDescription? = null
    private var lastJoyX = 320f
    private var lastJoyY = 780f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun steerJoystick(centerX: Float, centerY: Float, angleRad: Double, power: Float = 1.0f) {
        val radius = 170f * power.coerceIn(0.45f, 1.0f)
        val targetX = centerX + (cos(angleRad) * radius).toFloat()
        val targetY = centerY + (sin(angleRad) * radius).toFloat()

        val path = Path()
        if (currentJoyStroke == null) {
            path.moveTo(centerX, centerY)
            path.lineTo(targetX, targetY)
            currentJoyStroke = GestureDescription.StrokeDescription(path, 0, 140, true)
        } else {
            path.moveTo(lastJoyX, lastJoyY)
            path.lineTo(targetX, targetY)
            currentJoyStroke = currentJoyStroke!!.continueStroke(path, 0, 140, true)
        }

        lastJoyX = targetX
        lastJoyY = targetY

        val gesture = GestureDescription.Builder()
            .addStroke(currentJoyStroke!!)
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun releaseJoystick() {
        currentJoyStroke?.let {
            val path = Path().apply {
                moveTo(lastJoyX, lastJoyY)
                lineTo(lastJoyX, lastJoyY)
            }
            val endStroke = it.continueStroke(path, 0, 20, false)
            dispatchGesture(GestureDescription.Builder().addStroke(endStroke).build(), null, null)
            currentJoyStroke = null
        }
    }

    fun triggerAction(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 115) {
        val actionPath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val actionStroke = GestureDescription.StrokeDescription(actionPath, 0, durationMs, false)
        val builder = GestureDescription.Builder().addStroke(actionStroke)

        currentJoyStroke?.let {
            val holdPath = Path().apply {
                moveTo(lastJoyX, lastJoyY)
                lineTo(lastJoyX, lastJoyY)
            }
            currentJoyStroke = it.continueStroke(holdPath, 0, durationMs, true)
            builder.addStroke(currentJoyStroke!!)
        }

        dispatchGesture(builder.build(), null, null)
    }

    // المهارات الاحترافية الجاهزة
    fun shortPass(x: Float, y: Float) = triggerAction(x, y, x, y, 110)
    fun throughBall(x: Float, y: Float) = triggerAction(x, y, x, y, 130)
    fun finesseShot(x: Float, y: Float) = triggerAction(x, y, x, y + 145f, 130)
    fun stunningShot(x: Float, y: Float) = triggerAction(x, y, x - 155f, y, 145)
    fun loftedCross(x: Float, y: Float) = triggerAction(x, y, x, y - 145f, 130)
    fun sprintTap(x: Float, y: Float) = triggerAction(x, y, x, y, 60)
    fun gkRush(throughX: Float, throughY: Float) = triggerAction(throughX, throughY, throughX, throughY + 145f, 120)
}
package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.Locale

class FapCounterModule : Module {

    private var ctx: Context? = null
    private var running = false

    override fun key(): String = "fap"
    override fun name(): String = ctx?.getString(R.string.fap_counter_module_name) ?: "Fap Counter"
    override fun emoji(): String = "🍆"
    override fun description(): String = ctx?.getString(R.string.fap_counter_module_description) ?: "Self-monitoring counter & streak tracker"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 100

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "fap_interval", 60000) } ?: 60000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
        checkDayRollover()
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        checkDayRollover()
    }

    fun increment() {
        val c = ctx ?: return
        val today = Prefs.getInt(c, "fap_today", 0) + 1
        Prefs.setInt(c, "fap_today", today)

        val monthly = Prefs.getInt(c, "fap_monthly", 0) + 1
        Prefs.setInt(c, "fap_monthly", monthly)

        val allTime = Prefs.getInt(c, "fap_all_time", 0) + 1
        Prefs.setInt(c, "fap_all_time", allTime)

        Prefs.setInt(c, "fap_streak", 0)
        Prefs.setInt(c, "fap_last_day", getDayOfYear())
    }

    private fun checkDayRollover() {
        val c = ctx ?: return
        val currentDay = getDayOfYear()
        val lastDay = Prefs.getInt(c, "fap_last_check_day", -1)

        if (lastDay == -1) {
            Prefs.setInt(c, "fap_last_check_day", currentDay)
            return
        }

        if (currentDay != lastDay) {
            val todayCount = Prefs.getInt(c, "fap_today", 0)
            if (todayCount == 0) {
                val streak = Prefs.getInt(c, "fap_streak", 0)
                Prefs.setInt(c, "fap_streak", streak + 1)
            }
            Prefs.setInt(c, "fap_yesterday", todayCount)
            Prefs.setInt(c, "fap_today", 0)

            val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
            val lastMonth = Prefs.getInt(c, "fap_last_month", -1)
            if (lastMonth != -1 && currentMonth != lastMonth) {
                Prefs.setInt(c, "fap_prev_monthly", Prefs.getInt(c, "fap_monthly", 0))
                Prefs.setInt(c, "fap_monthly", 0)
            }
            Prefs.setInt(c, "fap_last_month", currentMonth)
            Prefs.setInt(c, "fap_last_check_day", currentDay)
        }
    }

    fun getTodayCount(): Int = ctx?.let { Prefs.getInt(it, "fap_today", 0) } ?: 0

    private fun getDayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    override fun compact(): String {
        val c = ctx ?: return ""
        val today = Prefs.getInt(c, "fap_today", 0)
        val streak = Prefs.getInt(c, "fap_streak", 0)
        val showStreak = Prefs.getBool(c, "fap_show_streak", true)
        return if (streak > 0 && showStreak) c.getString(R.string.fap_counter_module_compact_streak, today, streak) else c.getString(R.string.fap_counter_module_compact_today, today)
    }

    override fun detail(): String {
        val c = ctx ?: return ""
        val today = Prefs.getInt(c, "fap_today", 0)
        val streak = Prefs.getInt(c, "fap_streak", 0)
        val yesterday = Prefs.getInt(c, "fap_yesterday", 0)
        val monthly = Prefs.getInt(c, "fap_monthly", 0)
        val allTime = Prefs.getInt(c, "fap_all_time", 0)

        val showStreak = Prefs.getBool(c, "fap_show_streak", true)
        val showYesterday = Prefs.getBool(c, "fap_show_yesterday", true)
        val showAllTime = Prefs.getBool(c, "fap_show_all_time", true)

        val sb = StringBuilder()
        sb.append(c.getString(R.string.fap_counter_module_today, today))
        if (streak > 0 && showStreak) sb.append(c.getString(R.string.fap_counter_module_streak, streak))
        if (showYesterday && yesterday >= 0) sb.append(c.getString(R.string.fap_counter_module_yesterday, yesterday))
        sb.append(c.getString(R.string.fap_counter_module_monthly, monthly))
        if (showAllTime) sb.append(c.getString(R.string.fap_counter_module_total, allTime))
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        val c = ctx ?: return d

        d["fap.today"] = Prefs.getInt(c, "fap_today", 0).toString()
        d["fap.yesterday"] = Prefs.getInt(c, "fap_yesterday", 0).toString()
        val streak = Prefs.getInt(c, "fap_streak", 0)
        d["fap.streak"] = if (streak > 0) c.getString(R.string.fap_counter_module_streak_days, streak) else "0"
        d["fap.monthly"] = Prefs.getInt(c, "fap_monthly", 0).toString()
        d["fap.all_time"] = Prefs.getInt(c, "fap_all_time", 0).toString()
        return d
    }

    override fun checkAlerts(ctx: Context) {
        val dailyLimit = Prefs.getInt(ctx, "fap_daily_limit", 0)
        if (dailyLimit <= 0) return

        val today = Prefs.getInt(ctx, "fap_today", 0)
        val alertFired = Prefs.getBool(ctx, "fap_limit_fired", false)

        if (today >= dailyLimit && !alertFired) {
            fireAlert(ctx, 2010, ctx.getString(R.string.fap_counter_module_daily_limit_reached_title), ctx.getString(R.string.fap_counter_module_daily_limit_reached_content, dailyLimit))
            vibrate(ctx, longArrayOf(0, 200, 100, 200)) // Feedback pulse
            Prefs.setBool(ctx, "fap_limit_fired", true)
        }

        if (today == 0 && alertFired) {
            Prefs.setBool(ctx, "fap_limit_fired", false)
        }
    }

    private fun fireAlert(c: Context, id: Int, title: String, body: String) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, NotificationCompat.Builder(c, "ebox_alerts")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).build())
        } catch (ignored: Exception) {
        }
    }
}

package com.hotpodata.twistris

import android.content.Context
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.Tracker
import com.hotpodata.common.interfaces.IAnalyticsProvider

/**
 * Created by jdrotos on 11/18/15.
 */
object AnalyticsMaster : IAnalyticsProvider {

    //CATEGORIES
    val CATEGORY_ACTION = "Action"
    val CATEGORY_EVENT = "Event"

    //ACTIONS
    val ACTION_PAUSE = "Pause"
    val ACTION_RESUME = "Resume"
    val ACTION_START_OVER = "StartOver"
    val ACTION_HELP = "Help"
    val ACTION_OPEN_DRAWER = "OpenDrawer"
    val ACTION_SIGN_IN = "SignInClicked"
    val ACTION_SIGN_OUT = "SignOutClicked"
    val ACTION_ACHIEVEMENTS = "AchievementsClicked"
    val ACTION_LEADERBOARD = "LeaderBoardClicked"

    val ACTION_FIRE = "Fire"
    val ACTION_ROT_LEFT = "RotLeft"
    val ACTION_ROT_RIGHT = "RotRight"
    val ACTION_UP = "Up"
    val ACTION_DOWN = "Down"
    val ACTION_DRAG = "Drag"

    val EVENT_LEVEL_COMPLETE = "LevelComplete"
    val EVENT_ROWS_CLEARED = "RowsCleared"
    val EVENT_TWIST = "Twist"
    val EVENT_GAME_OVER = "GameOver"

    //Labels
    val LABEL_LEVEL = "Level"
    val LABEL_ROWS_CLEARED = "Rows_Cleared"
    val LABEL_TWIST_COUNT = "Total_Twists"

    private var tracker: Tracker? = null
    public override fun getTracker(context: Context): Tracker {
        val t = tracker ?:
                GoogleAnalytics.getInstance(context).newTracker(R.xml.global_tracker).apply {
                    enableExceptionReporting(true)
                    enableAdvertisingIdCollection(true)
                    enableAutoActivityTracking(true)
                }
        tracker = t
        return t
    }
}
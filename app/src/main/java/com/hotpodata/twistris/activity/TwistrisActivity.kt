package com.hotpodata.twistris.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.games.Games
import com.hotpodata.blockelganger.utils.ScreenPositionUtils
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import com.hotpodata.common.utils.HashUtils
import com.hotpodata.twistris.AnalyticsMaster
import com.hotpodata.twistris.BuildConfig
import com.hotpodata.twistris.R
import com.hotpodata.twistris.adapter.TwistrisSideBarAdapter
import com.hotpodata.twistris.data.TwistrisGame
import com.hotpodata.twistris.fragment.DialogHelpFragment
import com.hotpodata.twistris.interfaces.IGameController
import com.hotpodata.twistris.interfaces.IGooglePlayGameServicesProvider
import com.hotpodata.twistris.utils.BaseGameUtils
import com.hotpodata.twistris.utils.GridOfColorsBlockDrawer
import com.hotpodata.twistris.view.SizeAwareFrameLayout
import kotlinx.android.synthetic.main.activity_twistris.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by jdrotos on 12/20/15.
 */
class TwistrisActivity : AppCompatActivity(), IGameController, DialogHelpFragment.IHelpDialogListener, IGooglePlayGameServicesProvider, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    val STATE_GAME = "STATE_GAME"

    val REQUEST_LEADERBOARD = 1
    val REQUEST_ACHIEVEMENTS = 2

    val STORAGE_KEY_LAUNCH_COUNT = "STORAGE_KEY_LAUNCH_COUNT"
    val STORAGE_KEY_AUTO_SIGN_IN = "STORAGE_KEY_AUTO_SIGN_IN"

    val FTAG_HELP = "FTAG_HELP"

    val MAX_TICK_LENGTH_MS = 3000
    val MIN_TICK_LENGTH_MS = 300

    //This is the board we add the upcoming piece to...
    var boardUpcoming = Grid(4, 2)

    //This is the game state, if we kill the game and come back, this should reflect where we are
    var game = TwistrisGame()

    //Launch stats
    var launchCount: Int
        set(launches: Int) {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            with(sharedPref.edit()) {
                putInt(STORAGE_KEY_LAUNCH_COUNT, launches);
                commit()
            }
        }
        get() {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            return sharedPref.getInt(STORAGE_KEY_LAUNCH_COUNT, 0)
        }

    //Sign in stuff
    val RC_SIGN_IN = 9001
    var resolvingConnectionFailure = false
    var autoStartSignInFlow: Boolean
        set(signInOnStart: Boolean) {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            with(sharedPref.edit()) {
                putBoolean(STORAGE_KEY_AUTO_SIGN_IN, signInOnStart);
                commit()
            }
        }
        get() {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            return sharedPref.getBoolean(STORAGE_KEY_AUTO_SIGN_IN, false)
        }
    var signInClicked = false;
    var _googleApiClient: GoogleApiClient? = null
    val googleApiClient: GoogleApiClient
        get() {
            if (_googleApiClient == null) {
                _googleApiClient = GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(Games.API)
                        .addScope(Games.SCOPE_GAMES)
                        .build();
            }
            return _googleApiClient!!
        }

    //Game state
    var subTicker: Subscription? = null
    var tickInterpolator = DecelerateInterpolator()
    var actionAnimator: Animator? = null
    var paused: Boolean = true
        set(pause: Boolean) {
            var changed = pause != field
            if (changed) {
                if (pause) {
                    if (actionAnimator?.isRunning ?: false) {
                        actionAnimator?.pause()
                    }
                    unsubscribeFromTicker()
                    pause_btn.setImageResource(R.drawable.ic_play_arrow_24dp)
                } else {
                    if (actionAnimator?.isPaused ?: false) {
                        actionAnimator?.resume()
                    } else {
                        subscribeToTicker()
                    }
                    pause_btn.setImageResource(R.drawable.ic_pause_24dp)
                }
                field = pause
                bindStoppedContainer()
            }
        }

    //Drawer stuff
    var sideBarAdapter: TwistrisSideBarAdapter? = null
    var drawerToggle: ActionBarDrawerToggle? = null

    var touchStartY = 0f
    var blockHeight = 1f

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_twistris)

        //Set up the actionbar
        setSupportActionBar(toolbar);
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setShowHideAnimationEnabled(true)

        //Set up the drawer
        setUpLeftDrawer()
        drawerToggle = object : ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_closed) {
            override fun onDrawerOpened(drawerView: View?) {
                pauseGame()
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                try {
                    AnalyticsMaster?.getTracker(this@TwistrisActivity)?.let {
                        it.send(HitBuilders.EventBuilder()
                                .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                                .setAction(AnalyticsMaster.ACTION_OPEN_DRAWER)
                                .build());
                    }
                } catch(ex: Exception) {
                    Timber.e(ex, "Error recording analytics event.")
                }
            }

            override fun onDrawerClosed(drawerView: View?) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }
        drawer_layout.setDrawerListener(drawerToggle)
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);


        up_btn.setOnClickListener {
            if (allowGameActions() && game.actionMoveActiveUp()) {
                bindHorizGridView()
                try {
                    AnalyticsMaster?.getTracker(this)?.let {
                        it.send(HitBuilders.EventBuilder()
                                .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                                .setAction(AnalyticsMaster.ACTION_UP)
                                .build());
                    }
                } catch(ex: Exception) {
                    Timber.e(ex, "Error recording analytics event.")
                }
            }
        }
        down_btn.setOnClickListener {
            if (allowGameActions() && game.actionMoveActiveDown()) {
                bindHorizGridView()
                try {
                    AnalyticsMaster?.getTracker(this)?.let {
                        it.send(HitBuilders.EventBuilder()
                                .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                                .setAction(AnalyticsMaster.ACTION_DOWN)
                                .build());
                    }
                } catch(ex: Exception) {
                    Timber.e(ex, "Error recording analytics event.")
                }
            }
        }
        rotate_left_btn.setOnClickListener {
            if (allowGameActions() && game.actionRotateActiveLeft()) {
                bindHorizGridView()
                try {
                    AnalyticsMaster?.getTracker(this)?.let {
                        it.send(HitBuilders.EventBuilder()
                                .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                                .setAction(AnalyticsMaster.ACTION_ROT_LEFT)
                                .build());
                    }
                } catch(ex: Exception) {
                    Timber.e(ex, "Error recording analytics event.")
                }
            }
        }
        rotate_right_btn.setOnClickListener {
            if (allowGameActions() && game.actionRotateActiveRight()) {
                bindHorizGridView()
                try {
                    AnalyticsMaster?.getTracker(this)?.let {
                        it.send(HitBuilders.EventBuilder()
                                .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                                .setAction(AnalyticsMaster.ACTION_ROT_RIGHT)
                                .build());
                    }
                } catch(ex: Exception) {
                    Timber.e(ex, "Error recording analytics event.")
                }
            }
        }
        fire_btn.setOnClickListener {
            if (allowGameActions()) {
                var animGame = TwistrisGame(game)
                performActionFire(animGame)

            }
        }
        pause_btn.setOnClickListener {
            if (paused) {
                resumeGame()
            } else {
                pauseGame()
            }
        }
        stopped_start_over_btn.setOnClickListener {
            try {
                AnalyticsMaster?.getTracker(this)?.let {
                    it.send(HitBuilders.EventBuilder()
                            .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                            .setAction(AnalyticsMaster.ACTION_START_OVER)
                            .build());
                }
            } catch(ex: Exception) {
                Timber.e(ex, "Error recording analytics event.")
            }
            resetGame()
            resumeGame()
        }
        stopped_continue_btn.setOnClickListener {
            resumeGame()
        }
        stopped_leader_board_btn.setOnClickListener {
            showLeaderBoard()
        }
        stopped_sign_in_button.setOnClickListener {
            login()
        }


        if (savedInstanceState == null || !savedInstanceState.containsKey(STATE_GAME)) {
            showHelp()
            launchCount++
        } else {
            TwistrisGame.Serializer.gameFromBundle(savedInstanceState.getBundle(STATE_GAME))?.let {
                game = it
                Timber.d("Game from SAVED INSTANCE STATE:" + TwistrisGame.Serializer.gameToJson(game).toString())
            }
        }

        gridbinderview_horizontal.grid = game.boardHoriz
        gridbinderview_horizontal.blockDrawer = GridOfColorsBlockDrawer
        gridbinderview_vertical.blockDrawer = GridOfColorsBlockDrawer
        gridbinderview_upcomingpiece.blockDrawer = GridOfColorsBlockDrawer

        bindGameInfo()
        bindHorizGridView()
        bindVertGridView()

        //Set up the touch listener for the top grid
        gridbinderview_horizontal.setOnTouchListener {
            view, motionEvent ->
            if (allowGameActions()) {
                var gridView = view as GridBinderView
                var motionEventConsumed = motionEvent.actionMasked != MotionEvent.ACTION_DOWN
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = motionEvent.y
                        blockHeight = gridView.getSubGridPosition(Grid(1, 1), 0, 0).height()
                        motionEventConsumed = gridView.getSubGridPosition(game.activePiece, game.activeXOffset, game.activeYOffset).contains(motionEvent.x, motionEvent.y)
                        if (motionEventConsumed) {
                            try {
                                AnalyticsMaster?.getTracker(this)?.let {
                                    it.send(HitBuilders.EventBuilder()
                                            .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                                            .setAction(AnalyticsMaster.ACTION_DRAG)
                                            .build());
                                }
                            } catch(ex: Exception) {
                                Timber.e(ex, "Error recording analytics event.")
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        var dif = touchStartY - motionEvent.y
                        var blocks = Math.abs(dif.toInt() / blockHeight.toInt())
                        if (Math.abs(dif) > blockHeight) {
                            for (i in 0..blocks - 1) {
                                if (dif > 0) {
                                    game.actionMoveActiveUp()
                                } else {
                                    game.actionMoveActiveDown()
                                }
                            }
                            bindHorizGridView()
                            touchStartY = motionEvent.y + (dif % blockHeight)
                        }
                    }
                }
                motionEventConsumed
            } else {
                false
            }
        }



        game_container.sizeChangeListener = object : SizeAwareFrameLayout.ISizeChangeListener {
            override fun onSizeChange(w: Int, h: Int, oldw: Int, oldh: Int) {
                if (w > 0 && h > 0) {
                    //how much height we have above the button bar for our horizontal board
                    val availableHoriBoardHeight = h - btn_outer_container.height

                    val vertBoardHtoWRatio = game.VERT_H / game.VERT_W.toFloat()
                    var vertBoardWidth = w - 2 * resources.getDimensionPixelSize(R.dimen.btn_container_width)
                    var vertBoardHeight = vertBoardHtoWRatio * vertBoardWidth
                    if (vertBoardWidth > availableHoriBoardHeight) {
                        vertBoardWidth = availableHoriBoardHeight
                        vertBoardHeight = vertBoardHtoWRatio * vertBoardWidth
                    }

                    val horizBoardWtoHRatio = game.HORI_W / game.HORI_H.toFloat()
                    val horiBoardHeight = vertBoardWidth
                    val horiBoardWidth = horizBoardWtoHRatio * horiBoardHeight

                    gridbinderview_vertical.layoutParams?.let {
                        it.height = vertBoardHeight.toInt()
                        it.width = vertBoardWidth
                        gridbinderview_vertical.layoutParams = it
                    }

                    horiz_container.layoutParams?.let {
                        it.height = horiBoardHeight
                        it.width = horiBoardWidth.toInt()
                        horiz_container.layoutParams = it
                    }

                    arrow.translationY = (horiBoardHeight - resources.getDimensionPixelSize(R.dimen.arrow_size)) / 2f
                }
            }
        }

        //Load ads
        bindAdView()
    }


    override fun onStart() {
        super.onStart()
        if (autoStartSignInFlow) {
            googleApiClient.connect();
        }
    }

    override fun onPause() {
        super.onPause()
        if (!game.gameIsOver) {
            pauseGame()
        }
    }

    override fun onStop() {
        super.onStop()
        googleApiClient.disconnect()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putBundle(STATE_GAME, TwistrisGame.Serializer.gameToBundle(game))
        super.onSaveInstanceState(outState)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == RC_SIGN_IN) {
            signInClicked = false;
            resolvingConnectionFailure = false;
            if (resultCode == RESULT_OK) {
                googleApiClient.connect();
            } else {
                BaseGameUtils.showActivityResultError(this,
                        requestCode, resultCode, R.string.signin_error);
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle?.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(left_drawer) ?: false ) {
            drawer_layout.closeDrawers()
        } else {
            super.onBackPressed()
        }

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (drawerToggle?.onOptionsItemSelected(item) ?: false) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun setUpLeftDrawer() {
        if (sideBarAdapter == null) {
            sideBarAdapter = TwistrisSideBarAdapter(this, this, this, AnalyticsMaster)
            sideBarAdapter?.setAccentColor(android.support.v4.content.ContextCompat.getColor(this@TwistrisActivity, R.color.colorPrimary))
            left_drawer.adapter = sideBarAdapter
            left_drawer.layoutManager = LinearLayoutManager(this)
        }
    }

    fun subscribeToTicker() {
        unsubscribeFromTicker()
        val interval = (MAX_TICK_LENGTH_MS - (MAX_TICK_LENGTH_MS - MIN_TICK_LENGTH_MS) * tickInterpolator.getInterpolation(game.currentLevel / game.MAX_LEVEL.toFloat())).toLong()
        subTicker = Observable.interval(interval, TimeUnit.MILLISECONDS)
                .filter({ l -> allowGameActions() })//So we don't do anything
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    gameTick()
                }
    }

    fun unsubscribeFromTicker() {
        subTicker?.let { if (!it.isUnsubscribed) it.unsubscribe() }
    }

    fun bindGameInfo() {
        levelTv.text = getString(R.string.level_template, game.currentLevel)
        scoreTv.text = getString(R.string.score_template, game.currentScore)
        linesTv.text = getString(R.string.lines_template, game.currentRowsDestroyed)
        boardUpcoming.clear()
        GridHelper.addGrid(boardUpcoming, game.upcomingPiece, 0, 0)
        gridbinderview_upcomingpiece.grid = boardUpcoming
    }

    fun bindStoppedContainer() {
        if (game.gameIsOver || paused) {
            if (game.gameIsOver) {
                stopped_msg_tv.text = getString(R.string.game_over)
                stopped_continue_btn.visibility = View.GONE
            } else if (paused) {
                stopped_msg_tv.text = getString(R.string.paused)
                stopped_continue_btn.visibility = View.VISIBLE
            }
            if (googleApiClient.isConnected) {
                stopped_signed_in_container.visibility = View.VISIBLE
                stopped_sign_in_container.visibility = View.GONE
            } else {
                stopped_signed_in_container.visibility = View.GONE
                stopped_sign_in_container.visibility = View.VISIBLE
            }
            stopped_container.visibility = View.VISIBLE
        } else {
            stopped_container.visibility = View.INVISIBLE
        }
    }

    fun bindHorizGridView() {
        var grid = GridHelper.copyGrid(game.boardHoriz)
        GridHelper.addGrid(grid, game.activePiece, game.activeXOffset, game.activeYOffset)
        gridbinderview_horizontal.grid = grid
    }

    fun bindVertGridView() {
        var grid = GridHelper.copyGrid(game.boardVert)
        gridbinderview_vertical.grid = grid
    }


    fun gameTick() {
        Timber.d("tick")
        if (game.gameIsOver) {
            Timber.d("tick - gameIsOver")
            if (googleApiClient.isConnected()) {
                Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_alltimehighscores_id), game.currentScore.toLong());
            }
            unsubscribeFromTicker()
            bindStoppedContainer()
            try {
                AnalyticsMaster?.getTracker(this)?.let {
                    it.send(HitBuilders.EventBuilder()
                            .setCategory(AnalyticsMaster.CATEGORY_EVENT)
                            .setAction(AnalyticsMaster.EVENT_GAME_OVER)
                            .setLabel(AnalyticsMaster.LABEL_LEVEL)
                            .setValue(game.currentLevel.toLong())
                            .build());
                }
            } catch(ex: Exception) {
                Timber.e(ex, "Error recording analytics event.")
            }
        } else if (game.gameNeedsTwist()) {
            Timber.d("tick - gameNeedsTwist")
            var animGame = TwistrisGame(game)
            game.actionTwistBoard()
            game.actionNextPiece()
            performActionTwist(animGame)
        } else if (game.gameNeedsNextPiece()) {
            Timber.d("tick - gameNeedsNextPiece")
            game.actionNextPiece()
            bindHorizGridView()
        } else if (!paused) {
            Timber.d("tick - actionMoveActiveLeft")
            if (game.actionMoveActiveLeft()) {
                bindHorizGridView()
            }
        }
        if (subTicker?.isUnsubscribed ?: true) {
            subscribeToTicker()
        }
        bindGameInfo()
    }

    fun performActionFire(animGame: TwistrisGame) {
        unsubscribeFromTicker()
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_FIRE)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }

        game.actionMoveActiveAllTheWayLeft()
        var anim = genFlyLeftAnim(animGame)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                actionAnimator = null
                gameTick()
            }
        })
        actionAnimator = anim
        anim.start()
    }

    fun performActionTwist(animGame: TwistrisGame) {
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_EVENT)
                        .setAction(AnalyticsMaster.EVENT_TWIST)
                        .setLabel(AnalyticsMaster.LABEL_TWIST_COUNT)
                        .setValue(game.twistCount.toLong())
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(genTwistAnimation(), genHorizDropAnimation(animGame), genHorizFlyInFromRightAnimation(animGame, animGame.currentLevel != game.currentLevel))
        if (game.twistCount <= 1) {
            animatorSet.setDuration(3000)
        }
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                bindHorizGridView()
                bindVertGridView()
                actionAnimator = null

                //We show our achievements after things are back to normal
                if (game.currentLevel == 2 && animGame.currentLevel < 2) {
                    achieve(R.string.achievement_plot_twist);
                }
                if (game.currentLevel >= 3 && animGame.currentLevel < 3) {
                    achieve(R.string.achievement_twisted_like_fishing_line);
                }
                if (game.currentLevel >= 5 && animGame.currentLevel < 5) {
                    achieve(R.string.achievement_twisted_not_stirred);
                }
                if (game.currentLevel >= 7 && animGame.currentLevel < 7) {
                    achieve(R.string.achievement_double_helix);
                }
                if (animGame.currentRowsDestroyed == animGame.currentRowsDestroyed + 4) {
                    achieve(R.string.achievement_four_line_so_divine);
                } else if (animGame.currentRowsDestroyed >= animGame.currentRowsDestroyed + 3) {
                    achieve(R.string.achievement_three_line_ninja_time);
                }

                if (game.currentLevel > animGame.currentLevel) {
                    try {
                        AnalyticsMaster?.getTracker(this@TwistrisActivity)?.let {
                            it.send(HitBuilders.EventBuilder()
                                    .setCategory(AnalyticsMaster.CATEGORY_EVENT)
                                    .setAction(AnalyticsMaster.EVENT_LEVEL_COMPLETE)
                                    .setLabel(AnalyticsMaster.LABEL_LEVEL)
                                    .setValue(game.currentLevel.toLong())
                                    .build());
                        }
                    } catch(ex: Exception) {
                        Timber.e(ex, "Error recording analytics event.")
                    }
                }

                if (game.currentRowsDestroyed > animGame.currentRowsDestroyed) {
                    try {
                        AnalyticsMaster?.getTracker(this@TwistrisActivity)?.let {
                            it.send(HitBuilders.EventBuilder()
                                    .setCategory(AnalyticsMaster.CATEGORY_EVENT)
                                    .setAction(AnalyticsMaster.EVENT_ROWS_CLEARED)
                                    .setLabel(AnalyticsMaster.LABEL_ROWS_CLEARED)
                                    .setValue((game.currentRowsDestroyed - animGame.currentRowsDestroyed).toLong())
                                    .build());
                        }
                    } catch(ex: Exception) {
                        Timber.e(ex, "Error recording analytics event.")
                    }
                }


            }
        })
        actionAnimator = animatorSet
        animatorSet.start()
    }

    fun genFlyLeftAnim(animGame: TwistrisGame): Animator {
        var startX = animGame.activeXOffset
        var endX = startX
        //Sink
        while (GridHelper.gridInBounds(animGame.boardHoriz, animGame.activePiece, endX - 1, animGame.activeYOffset) && !GridHelper.gridsCollide(animGame.boardHoriz, animGame.activePiece, endX - 1, animGame.activeYOffset)) {
            endX--
        }
        var anim = genPieceMoveAnim(gridbinderview_horizontal, animGame.activePiece, startX, animGame.activeYOffset, endX, animGame.activeYOffset)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                gridbinderview_horizontal.grid = animGame.boardHoriz//We let the animator draw the piece
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                bindHorizGridView()
                bindVertGridView()
                actionAnimator = null
            }
        })
        return anim
    }

    fun allowGameActions(): Boolean {
        return !paused && !(actionAnimator?.isRunning ?: false)
    }

    fun genHorizDropAnimation(animGame: TwistrisGame): Animator {
        var animators = ArrayList<Animator>()

        //The working board is twice as tall as the display board, so pieces can be added over the bounds
        var workingBoard = Grid(animGame.boardVert.width, animGame.boardVert.height * 2)
        GridHelper.addGrid(workingBoard, GridHelper.copyGrid(animGame.boardVert), 0, animGame.boardVert.height);
        Timber.d("1 workingBoard:" + workingBoard.getPrintString(" - ", " * "))

        var duration = 250L

        //This builds up the drop animators
        for (item in animGame.horizPieces.reversed()) {
            var coords = item.second
            if (coords != null) {
                var rotated = item.first.rotate(false)
                var startX = animGame.boardHoriz.height - coords.second - rotated.width
                var startY = coords.first + animGame.boardVert.height
                var endY = 0
                while (GridHelper.gridInBounds(workingBoard, rotated, startX, endY + 1) && !GridHelper.gridsCollide(workingBoard, rotated, startX, endY + 1)) {
                    endY++
                }

                GridHelper.addGrid(workingBoard, rotated, startX, endY)
                var pieceDoneBoard = GridHelper.copyGridPortion(workingBoard, 0, animGame.boardVert.height, animGame.boardVert.width, workingBoard.height)

                var pieceAnim = genPieceMoveAnim(gridbinderview_vertical, rotated, startX, startY - animGame.boardVert.height, startX, endY - animGame.boardVert.height)
                pieceAnim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        done()
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                        done()
                    }

                    fun done() {
                        gridbinderview_vertical.grid = pieceDoneBoard
                    }
                })
                pieceAnim.setDuration(duration)
                animators.add(pieceAnim)
                duration += 100
            }
        }
        var dropAnimators = AnimatorSet()
        dropAnimators.playTogether(animators)

        Timber.d("2 workingBoard:" + workingBoard.getPrintString(" - ", " * "))


        //Now we need to build up row removal animators
        var rowRemovalAnimators = AnimatorSet()
        var shifts = ArrayList<Int>()
        var shiftVal = 0
        for (i in workingBoard.height - 1 downTo 0) {
            if (workingBoard.rowFull(i)) {
                shifts.add(0, -1)
                shiftVal++
            } else {
                shifts.add(0, shiftVal)
            }
        }

        Timber.d("shiftVal:" + shiftVal + " shifts:" + listToStr(shifts));
        var binderView = gridbinderview_vertical
        if (shiftVal > 0 && binderView != null) {
            //In this case we have rows to remove

            //Build up the row slide out animations
            var rowRemoveAnims = ArrayList<Animator>()
            var rowRemovedBoard = GridHelper.copyGridPortion(workingBoard, 0, animGame.boardVert.height, animGame.boardVert.width, workingBoard.height)
            for (i in shifts.indices) {
                if (shifts[i] == -1) {
                    var vertBoardYCoordRowRemove = i - animGame.boardVert.height
                    var rowGrid = GridHelper.copyGridPortion(workingBoard, 0, i, workingBoard.width, i + 1)
                    var rowPos = binderView.getSubGridPosition(rowGrid, 0, vertBoardYCoordRowRemove)

                    //Set up the view
                    var startX = binderView.left.toFloat()
                    var startY = rowPos.top + binderView.top
                    var endX = startX + binderView.width.toFloat()

                    //Set up the view
                    var animView = GridBinderView(this)
                    animView.grid = rowGrid//GridHelper.copyGridPortion(workingBoard, 0, i, workingBoard.width, i + 1)
                    animView.translationX = startX
                    animView.translationY = startY
                    animView.blockDrawer = GridOfColorsBlockDrawer

                    //Row removed board

                    for (i in 0..animGame.boardVert.width - 1) {
                        rowRemovedBoard.remove(i, vertBoardYCoordRowRemove)
                    }


                    var animTransX = ObjectAnimator.ofFloat(animView, "translationX", startX, endX)
                    animTransX.interpolator = AccelerateInterpolator()
                    animTransX.setDuration(450)
                    animTransX.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator?) {
                            game_container.addView(animView, rowPos.width().toInt(), rowPos.height().toInt())
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            done()
                        }

                        override fun onAnimationCancel(animation: Animator?) {
                            done()
                        }

                        fun done() {
                            game_container.removeView(animView)
                        }
                    })
                    rowRemoveAnims.add(animTransX)
                }
            }
            var rowRemSet = AnimatorSet()
            rowRemSet.playTogether(rowRemoveAnims)
            rowRemSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    binderView.grid = rowRemovedBoard
                }
            })
            rowRemovalAnimators.playSequentially(rowRemSet)


            //Shift working board for realz
            for (i in shifts.size - 1 downTo 0) {
                if (shifts[i] > 0) {
                    for (j in 0..workingBoard.width - 1) {
                        workingBoard.put(j, i + shifts[i], workingBoard.at(j, i))
                    }
                }
            }
        }


        //The arrow animates in and out based on the height of pieces in the vertical board
        var arrowAlphaOut: ObjectAnimator? = null
        var emptyRows = 0
        for (i in animGame.boardVert.height..workingBoard.height - 1) {
            if (workingBoard.rowEmpty(i)) {
                emptyRows++
            } else {
                break;
            }
        }
        Timber.d("emptyRows:" + emptyRows + " horizHeight:" + game.HORI_H)
        if (emptyRows <= game.HORI_H && arrow.visibility == View.VISIBLE) {
            Timber.d("emptyRows:" + emptyRows + " HIDING ARROW!")
            arrowAlphaOut = ObjectAnimator.ofFloat(arrow, "alpha", 1f, 0f)
            arrowAlphaOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    arrow.visibility = View.INVISIBLE
                }
            })
        } else if (emptyRows > game.HORI_H && arrow.visibility != View.VISIBLE) {
            Timber.d("emptyRows:" + emptyRows + " SHOWING ARROW!")
            arrowAlphaOut = ObjectAnimator.ofFloat(arrow, "alpha", 0f, 1f)
            arrowAlphaOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    arrow.alpha = 0f
                    arrow.visibility = View.VISIBLE
                }
            })
        }


        Timber.d("3 workingBoard:" + workingBoard.getPrintString(" - ", " * "))
        var animatorSet = AnimatorSet()
        if (arrowAlphaOut == null) {
            animatorSet.playSequentially(dropAnimators, rowRemovalAnimators)
        } else {
            var dropAndArrowAlpha = AnimatorSet()
            dropAndArrowAlpha.playTogether(arrowAlphaOut, dropAnimators)
            animatorSet.playSequentially(dropAndArrowAlpha, rowRemovalAnimators)
        }
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                horiz_container.alpha = 0f//Maybe we could find a better place for this?
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                bindVertGridView()
                if (game.gameIsOver) {
                    gameTick()
                }
            }
        })
        return animatorSet
    }

    fun genHorizFlyInFromRightAnimation(animGame: TwistrisGame, includeLevelBlurb: Boolean): Animator {
        var animSet = AnimatorSet()

        var horiBinder = horiz_container
        var animTransX = ObjectAnimator.ofFloat(horiBinder, "translationX", horiBinder.width.toFloat(), 0f)
        animTransX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                bindHorizGridView()
                horiBinder.scaleX = 1f
                horiBinder.scaleY = 1f
                horiBinder.translationY = 0f
                horiBinder.translationX = horiBinder.width.toFloat()
                horiBinder.rotation = 0f
                horiz_container.alpha = 1f//Maybe we could find a better place for this?
            }
        })
        animTransX.interpolator = DecelerateInterpolator()
        var arrowRotation = ObjectAnimator.ofFloat(arrow, "rotation", 270f, 0f)

        Timber.d("LevelMessage animGame.currentLevel:" + animGame.currentLevel + " game.currentLevel:" + game.currentLevel)
        if (includeLevelBlurb) {
            var lvlTxtAnimAlpha = ObjectAnimator.ofFloat(level_up_container, "alpha", 1f, 0f)
            lvlTxtAnimAlpha.interpolator = AccelerateInterpolator()
            var lvlTxtAnimScaleX = ObjectAnimator.ofFloat(level_up_container, "scaleX", 1f, 0.4f)
            var lvlTxtAnimScaleY = ObjectAnimator.ofFloat(level_up_container, "scaleY", 1f, 0.4f)

            val lvlTxtAnim = AnimatorSet()
            lvlTxtAnim.playTogether(lvlTxtAnimAlpha, lvlTxtAnimScaleX, lvlTxtAnimScaleY)
            lvlTxtAnim.setDuration(if (game.currentLevel == 2) 2500 else 1000)
            lvlTxtAnim.interpolator = AccelerateInterpolator()
            animSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    level_up_tv.text = getString(R.string.level_template, game.currentLevel)
                    level_up_blurb_tv.text = getString(R.string.level_up_blurb_template, game.currentLevel)
                    level_up_container.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator?) {
                    done()
                }

                override fun onAnimationCancel(animation: Animator?) {
                    done()
                }

                fun done() {
                    level_up_container.visibility = View.INVISIBLE
                    level_up_container.alpha = 1f
                    level_up_container.scaleX = 1f
                    level_up_container.scaleY = 1f
                }
            })

            animSet.playSequentially(arrowRotation, animTransX, lvlTxtAnim)
        } else {
            animSet.playTogether(arrowRotation, animTransX)
        }

        return animSet
    }

    fun genTwistAnimation(): Animator {
        var animSet = AnimatorSet()
        var vertBinder = gridbinderview_vertical
        var horiBinder = horiz_container
        if (vertBinder != null && horiBinder != null) {
            var vertGlobal = ScreenPositionUtils.getGlobalScreenPosition(vertBinder)
            var vertLocal = ScreenPositionUtils.translateGlobalPositionToLocalPosition(vertGlobal, game_container)
            var horiGlobal = ScreenPositionUtils.getGlobalScreenPosition(horiBinder)
            var horiLocal = ScreenPositionUtils.translateGlobalPositionToLocalPosition(horiGlobal, game_container)

            var scaleRatio = vertBinder.width / horiBinder.height.toFloat()
            var horiCenterY = horiLocal.top + horiLocal.height() / 2f
            var targetHorizScale = scaleRatio
            var targetHorizYOffset = vertLocal.top + (horiLocal.width() * scaleRatio) / 2f - horiCenterY

            var animScaleY = ObjectAnimator.ofFloat(horiBinder, "scaleY", 1f, 0.5f, targetHorizScale)
            var animScaleX = ObjectAnimator.ofFloat(horiBinder, "scaleX", 1f, 0.5f, targetHorizScale)
            var animTransY = ObjectAnimator.ofFloat(horiBinder, "translationY", 0f, targetHorizYOffset)
            var animRotation = ObjectAnimator.ofFloat(horiBinder, "rotation", 0f, 90f)

            var arrowRotation = ObjectAnimator.ofFloat(arrow, "rotation", 0f, 270f)

            animSet.playTogether(animScaleY, animScaleX, animTransY, animRotation, arrowRotation)
            animSet.interpolator = AccelerateDecelerateInterpolator()
        }
        return animSet
    }

    fun genPieceMoveAnim(binderView: GridBinderView, p: Grid, startOffsetX: Int, startOffsetY: Int, endOffsetX: Int, endOffsetY: Int): Animator {
        var startPos = binderView.getSubGridPosition(p, startOffsetX, startOffsetY)
        var endPos = binderView.getSubGridPosition(p, endOffsetX, endOffsetY)

        var binderViewRect = ScreenPositionUtils.getGlobalScreenPosition(binderView)
        var binderViewOuterRect = ScreenPositionUtils.translateGlobalPositionToLocalPosition(binderViewRect, game_container)

        Timber.d("genPieceMoveAnim binderViewRect:" + binderViewRect)
        Timber.d("genPieceMoveAnim binderViewOuterRect:" + binderViewOuterRect)

        //Set up the view
        var startX = startPos.left + binderViewOuterRect.left
        var startY = startPos.top + binderViewOuterRect.top
        var endX = endPos.left + binderViewOuterRect.left
        var endY = endPos.top + binderViewOuterRect.top

        Timber.d("genPieceMoveAnim startX:" + startX + " startY:" + startY + " endX:" + endX + " endY:" + endY)

        //Set up the view
        var animView = GridBinderView(this)
        animView.grid = GridHelper.copyGrid(p)
        animView.translationX = startX
        animView.translationY = startY
        animView.blockDrawer = GridOfColorsBlockDrawer


        var animTransX = ObjectAnimator.ofFloat(animView, "translationX", startX, endX)
        var animTransY = ObjectAnimator.ofFloat(animView, "translationY", startY, endY)
        var animSet = AnimatorSet()
        animSet.playTogether(animTransX, animTransY)
        animSet.interpolator = AccelerateInterpolator()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                game_container.addView(animView, startPos.width().toInt(), startPos.height().toInt())
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                game_container.removeView(animView)
            }
        })
        return animSet
    }

    fun listToStr(list: List<Any?>): String {
        var builder = StringBuilder()
        builder.append("[")
        for (i in list) {
            if (builder.length > 1) {
                builder.append(",")
            }
            builder.append(i.toString())
        }
        builder.append("]")
        return builder.toString()
    }


    /**
     * IGameController
     */
    override fun pauseGame() {
        paused = true
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_PAUSE)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    override fun resumeGame() {
        paused = false
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_RESUME)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    override fun resetGame() {
        if (isLoggedIn() && !game.gameIsOver && game.currentScore > 0) {
            Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_alltimehighscores_id), game.currentScore.toLong())
        }

        game = TwistrisGame()
        actionAnimator?.cancel()
        actionAnimator = null
        boardUpcoming = Grid(4, 2)
        bindStoppedContainer()
        bindHorizGridView()
        bindVertGridView()
        arrow.rotation = 0f
        arrow.alpha = 1f
        arrow.visibility = View.VISIBLE
    }

    override fun showHelp() {
        var startFrag = supportFragmentManager.findFragmentByTag(FTAG_HELP) as DialogHelpFragment?
        if (startFrag == null) {
            startFrag = DialogHelpFragment()
        }
        if (!startFrag.isAdded) {
            startFrag.show(supportFragmentManager, FTAG_HELP)
        }
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_HELP)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    /**
     * IGooglePlayGameServicesProvider
     */

    override fun isLoggedIn(): Boolean {
        return googleApiClient.isConnected
    }

    override fun login() {
        pauseGame()
        signInClicked = true
        autoStartSignInFlow = true
        googleApiClient.connect();
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_SIGN_IN)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    override fun logout() {
        signInClicked = false
        autoStartSignInFlow = false
        if (isLoggedIn()) {
            Games.signOut(googleApiClient)
            googleApiClient.disconnect()
            sideBarAdapter?.rebuildRowSet()
        }
        bindStoppedContainer()
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_SIGN_OUT)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    override fun showLeaderBoard() {
        if (googleApiClient.isConnected) {
            startActivityForResult(Games.Leaderboards.getLeaderboardIntent(googleApiClient,
                    getString(R.string.leaderboard_alltimehighscores_id)), REQUEST_LEADERBOARD);
        } else {
            Toast.makeText(this, R.string.you_must_be_signed_in, Toast.LENGTH_SHORT).show()
        }
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_LEADERBOARD)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    override fun showAchievements() {
        if (isLoggedIn()) {
            startActivityForResult(Games.Achievements.getAchievementsIntent(googleApiClient),
                    REQUEST_ACHIEVEMENTS);
        } else {
            Toast.makeText(this, R.string.you_must_be_signed_in, Toast.LENGTH_SHORT).show()
        }
        try {
            AnalyticsMaster?.getTracker(this)?.let {
                it.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_ACHIEVEMENTS)
                        .build());
            }
        } catch(ex: Exception) {
            Timber.e(ex, "Error recording analytics event.")
        }
    }

    /**
     * SIGN IN STUFF
     */

    override fun onConnected(connectionHint: Bundle?) {
        Timber.d("SignIn - onConnected")
        if (game.gameIsOver) {
            Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_alltimehighscores_id), game.currentScore.toLong())
        }
        bindStoppedContainer()
        sideBarAdapter?.rebuildRowSet()
    }

    override fun onConnectionSuspended(p0: Int) {
        Timber.d("SignIn - onConnectionSuspended")
        googleApiClient.connect()
    }

    override fun onConnectionFailed(result: ConnectionResult?) {
        Timber.d("SignIn - onConnectionFailed")
        if (resolvingConnectionFailure) {
            // already resolving
            return
        }

        // if the sign-in button was clicked or if auto sign-in is enabled,
        // launch the sign-in flow
        if (signInClicked || autoStartSignInFlow) {
            autoStartSignInFlow = false
            signInClicked = false
            resolvingConnectionFailure = true

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."
            if (!BaseGameUtils.resolveConnectionFailure(this,
                    googleApiClient, result,
                    RC_SIGN_IN, getString(R.string.sign_in_failed))) {
                resolvingConnectionFailure = false
            }
        }
    }

    /**
     * IHelpDialogListener
     */
    override fun onHelpDialogDismissed() {
        Timber.d("onHelpDialogDismissed")
        if (!drawer_layout.isDrawerOpen(left_drawer)) {
            Timber.d("onHelpDialogDismissed drawer wasnt open... so we should resume..")
            resumeGame()
        }
    }


    /*
    AD STUFF
     */

    private fun bindAdView() {
        var adRequest = with(AdRequest.Builder()) {
            if (BuildConfig.IS_DEBUG_BUILD) {
                addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                var andId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                var hash = HashUtils.md5(andId).toUpperCase()
                Timber.d("Adding test device. hash:" + hash)
                addTestDevice(hash)
            }
            build()
        }
        ad_view.loadAd(adRequest)
    }

    /**
     * Achievement stuff
     */

    private fun achieve(resId: Int) {
        achieve(getString(resId))
    }

    private fun achieve(id: String) {
        Timber.d("ACHIEVEMENT:" + id)
        if (isLoggedIn()) {
            Games.Achievements.unlock(googleApiClient, id);
        } else if (BuildConfig.IS_DEBUG_BUILD) {
            Toast.makeText(this, R.string.achievements_logged_out_message, Toast.LENGTH_SHORT).show()
        }
    }
}

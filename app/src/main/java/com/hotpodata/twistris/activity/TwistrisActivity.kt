package com.hotpodata.twistris.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.expedia.bookings.utils.ScreenPositionUtils
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import com.hotpodata.twistris.R
import com.hotpodata.twistris.data.TwistrisGame
import com.hotpodata.twistris.fragment.DialogGameOverFragment
import com.hotpodata.twistris.fragment.DialogPauseFragment
import com.hotpodata.twistris.fragment.DialogStartFragment
import com.hotpodata.twistris.interfaces.IGameController
import com.hotpodata.twistris.utils.GridOfColorsBlockDrawer
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
class TwistrisActivity : AppCompatActivity(), IGameController {

    val FTAG_PAUSE = "FTAG_PAUSE"
    val FTAG_START = "FTAG_START"
    val FTAG_GAME_OVER = "FTAG_GAME_OVER"

    val MAX_TICK_LENGTH_MS = 3000
    val MIN_TICK_LENGTH_MS = 300

    //This is the board we add the upcoming piece to...
    var boardUpcoming = Grid(4, 2)

    //This is the game state, if we kill the game and come back, this should reflect where we are
    var game = TwistrisGame()


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

                    var pauseFrag = DialogPauseFragment();
                    pauseFrag.show(supportFragmentManager, FTAG_PAUSE);
                } else {
                    if (actionAnimator?.isPaused ?: false) {
                        actionAnimator?.resume()
                    } else {
                        subscribeToTicker()
                    }
                    pause_btn.setImageResource(R.drawable.ic_pause_24dp)
                }
                field = pause
            }
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_twistris)
        setSupportActionBar(toolbar);

        up_btn.setOnClickListener {
            if (allowGameActions() && game.actionMoveActiveUp()) {
                bindHorizGridView()
            }
        }
        down_btn.setOnClickListener {
            if (allowGameActions() && game.actionMoveActiveDown()) {
                bindHorizGridView()

            }
        }
        rotate_left_btn.setOnClickListener {
            if (allowGameActions() && game.actionRotateActiveLeft()) {
                bindHorizGridView()
            }
        }
        rotate_right_btn.setOnClickListener {
            if (allowGameActions() && game.actionRotateActiveRight()) {
                bindHorizGridView()
            }
        }
        fire_btn.setOnClickListener {
            if (allowGameActions()) {
                var animGame = TwistrisGame(game)
                performActionFire(animGame)
            }
        }
        pause_btn.setOnClickListener {
            paused = !paused
        }

        gridbinderview_horizontal.grid = game.boardHoriz
        gridbinderview_horizontal.blockDrawer = GridOfColorsBlockDrawer
        gridbinderview_vertical.blockDrawer = GridOfColorsBlockDrawer
        gridbinderview_upcomingpiece.blockDrawer = GridOfColorsBlockDrawer

        bindHorizGridView()
        bindVertGridView()

        if (savedInstanceState == null) {
            var startFrag = DialogStartFragment()
            startFrag.show(supportFragmentManager, FTAG_START)
        }
    }

    override fun onResume() {
        super.onResume()
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
            unsubscribeFromTicker()
            var frag = supportFragmentManager.findFragmentByTag(FTAG_GAME_OVER) as? DialogGameOverFragment
            if (frag == null) {
                frag = DialogGameOverFragment()
            }
            if (!frag.isAdded) {
                frag.show(supportFragmentManager, FTAG_GAME_OVER)
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
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(genTwistAnimation(), genHorizDropAnimation(animGame), genHorizFlyInFromRightAnimation(animGame, animGame.currentLevel != game.currentLevel))
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
                            outer_container.addView(animView, rowPos.width().toInt(), rowPos.height().toInt())
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            done()
                        }

                        override fun onAnimationCancel(animation: Animator?) {
                            done()
                        }

                        fun done() {
                            outer_container.removeView(animView)
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


        Timber.d("3 workingBoard:" + workingBoard.getPrintString(" - ", " * "))

        var animatorSet = AnimatorSet()
        animatorSet.playSequentially(dropAnimators, rowRemovalAnimators)
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
            animSet.playSequentially(animTransX, lvlTxtAnim)
        } else {
            animSet.play(animTransX)
        }

        return animSet
    }

    fun genTwistAnimation(): Animator {
        var animSet = AnimatorSet()
        var vertBinder = gridbinderview_vertical
        var horiBinder = horiz_container
        if (vertBinder != null && horiBinder != null) {
            var scaleRatio = vertBinder.width / horiBinder.height.toFloat()
            var horiCenterY = horiBinder.top + horiBinder.height / 2f
            var targetHorizScale = scaleRatio
            var targetHorizYOffset = (horiBinder.width * scaleRatio) / 2f - horiCenterY

            var animScaleY = ObjectAnimator.ofFloat(horiBinder, "scaleY", 1f, 0.5f, targetHorizScale)
            var animScaleX = ObjectAnimator.ofFloat(horiBinder, "scaleX", 1f, 0.5f, targetHorizScale)
            var animTransY = ObjectAnimator.ofFloat(horiBinder, "translationY", 0f, targetHorizYOffset)
            var animRotation = ObjectAnimator.ofFloat(horiBinder, "rotation", 0f, 90f)

            animSet.playTogether(animScaleY, animScaleX, animTransY, animRotation)
            animSet.interpolator = AccelerateDecelerateInterpolator()
        }
        return animSet
    }

    fun genPieceMoveAnim(binderView: GridBinderView, p: Grid, startOffsetX: Int, startOffsetY: Int, endOffsetX: Int, endOffsetY: Int): Animator {
        var startPos = binderView.getSubGridPosition(p, startOffsetX, startOffsetY)
        var endPos = binderView.getSubGridPosition(p, endOffsetX, endOffsetY)

        var binderViewRect = ScreenPositionUtils.getGlobalScreenPosition(binderView)
        var binderViewOuterRect = ScreenPositionUtils.translateGlobalPositionToLocalPosition(binderViewRect, outer_container)

        //Set up the view
        var startX = startPos.left + binderViewOuterRect.left
        var startY = startPos.top + binderViewOuterRect.top
        var endX = endPos.left + binderViewOuterRect.left
        var endY = endPos.top + binderViewOuterRect.top

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
                outer_container.addView(animView, startPos.width().toInt(), startPos.height().toInt())
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                outer_container.removeView(animView)
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
    }

    override fun resumeGame() {
        paused = false
    }

    override fun resetGame() {
        game = TwistrisGame()
        actionAnimator?.cancel()
        actionAnimator = null
        boardUpcoming = Grid(4, 2)
        bindHorizGridView()
        bindVertGridView()

        //TODO: Show real ad
        Toast.makeText(this, "Showing ad!", Toast.LENGTH_SHORT).show()
    }
}
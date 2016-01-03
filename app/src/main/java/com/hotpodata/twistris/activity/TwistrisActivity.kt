package com.hotpodata.twistris.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import com.hotpodata.twistris.R
import com.hotpodata.twistris.fragment.DialogGameOverFragment
import com.hotpodata.twistris.fragment.DialogPauseFragment
import com.hotpodata.twistris.fragment.DialogStartFragment
import com.hotpodata.twistris.utils.TetrisFactory
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
    val MAX_LEVEL = 10

    var boardVert = Grid(8, 16)
    var boardHoriz = Grid(12, 8)
    var boardUpcoming = Grid(4, 2)
    var activePiece: Grid = TetrisFactory.j().rotate(false)
    var upcomingPiece: Grid = TetrisFactory.randomPiece()
    var activeXOffset = boardHoriz.width - activePiece.width
    var activeYOffset = (boardHoriz.height / 2f).toInt()

    var subTicker: Subscription? = null
    var tickInterpolator = DecelerateInterpolator()

    //These are the current horizontal pieces
    var horizPieceCoords = HashMap<Grid, Pair<Int, Int>>()
    var horizPieces = ArrayList<Grid>()

    //Game state
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
    var currentRowsDestroyed: Int = 0
        set(lines: Int) {
            field = lines
            linesTv.text = getString(R.string.lines_template, lines)
        }
    var currentScore = 0
        set(score: Int) {
            field = score
            scoreTv.text = getString(R.string.score_template, score)
        }

    var currentLevel = 1
        set(lev: Int) {
            field = lev
            levelTv.text = getString(R.string.level_template, lev)
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_twistris)
        setSupportActionBar(toolbar);

        up_btn.setOnClickListener {
            if (allowGameActions()) {
                if (GridHelper.gridInBounds(boardHoriz, activePiece, activeXOffset, activeYOffset - 1)) {
                    activeYOffset--
                    updateHorizGridView()
                }
            }
        }
        down_btn.setOnClickListener {
            if (allowGameActions()) {
                if (GridHelper.gridInBounds(boardHoriz, activePiece, activeXOffset, activeYOffset + 1)) {
                    activeYOffset++
                    updateHorizGridView()
                }
            }
        }
        rotate_left_btn.setOnClickListener {
            if (allowGameActions()) {
                var rot = activePiece.rotate(true)
                while (activeYOffset + rot.height > boardHoriz.height) {
                    activeYOffset--
                }
                activePiece = rot
                updateHorizGridView()
            }
        }
        rotate_right_btn.setOnClickListener {
            if (allowGameActions()) {
                var rot = activePiece.rotate(false)
                while (activeYOffset + rot.height > boardHoriz.height) {
                    activeYOffset--
                }
                activePiece = rot
                updateHorizGridView()
            }
        }
        fire_btn.setOnClickListener {
            if (allowGameActions()) {
                performActionFire()
            }
        }
        pause_btn.setOnClickListener {
            paused = !paused
        }

        gridbinderview_horizontal.grid = boardHoriz
        gridbinderview_horizontal.blockDrawer = GridOfColorsBlockDrawer
        gridbinderview_vertical.blockDrawer = GridOfColorsBlockDrawer
        gridbinderview_upcomingpiece.blockDrawer = GridOfColorsBlockDrawer

        updateHorizGridView()
        updateVertGridView()

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
        val interval = (MAX_TICK_LENGTH_MS - (MAX_TICK_LENGTH_MS - MIN_TICK_LENGTH_MS) * tickInterpolator.getInterpolation(currentLevel / MAX_LEVEL.toFloat())).toLong()
        subTicker = Observable.interval(interval, TimeUnit.MILLISECONDS)
                .filter({ l -> allowGameActions() })//So we don't do anything
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (!GridHelper.gridInBounds(boardHoriz, activePiece, activeXOffset - 1, activeYOffset) || GridHelper.gridsCollide(boardHoriz, activePiece, activeXOffset - 1, activeYOffset)) {
                        pieceHitLeft()
                    } else {
                        activeXOffset--
                        updateHorizGridView()
                    }
                }
    }

    fun unsubscribeFromTicker() {
        subTicker?.let { if (!it.isUnsubscribed) it.unsubscribe() }
    }

    fun updateHorizGridView() {
        var grid = GridHelper.copyGrid(boardHoriz)
        GridHelper.addGrid(grid, activePiece, activeXOffset, activeYOffset)
        gridbinderview_horizontal.grid = grid
    }

    fun updateVertGridView() {
        var grid = GridHelper.copyGrid(boardVert)
        gridbinderview_vertical.grid = grid
    }

    fun nextPiece() {
        var newPiece = TetrisFactory.randomPiece()
        if (newPiece.height > newPiece.width) {
            newPiece = newPiece.rotate(false)
        }

        var piece = upcomingPiece
        activeXOffset = boardHoriz.width - piece.width
        activeYOffset = (boardHoriz.height / 2f).toInt()

        upcomingPiece = newPiece
        boardUpcoming.clear()
        GridHelper.addGrid(boardUpcoming, upcomingPiece, 0, 0)
        gridbinderview_upcomingpiece.grid = boardUpcoming

        activePiece = piece
        updateHorizGridView()
        subscribeToTicker()
    }

    fun pieceHitLeft() {
        unsubscribeFromTicker()
        recordActivePiece()
        if (horizPieces.size % currentLevel == 0) {
            performActionAnim()
        } else {
            nextPiece()
        }
    }

    fun performActionFire() {
        unsubscribeFromTicker()
        var anim = genFlyLeftAnim()
        actionAnimator = anim
        anim.start()
    }

    fun performActionAnim() {
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(genTwistAnimation(), genHorizDropAnimation(), genHorizFlyInFromRightAnimation())
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                actionAnimator = null
            }
        })
        actionAnimator = animatorSet
        animatorSet.start()
    }

    fun recordActivePiece() {
        horizPieces.add(activePiece)
        horizPieceCoords.put(activePiece, Pair(activeXOffset, activeYOffset))
        GridHelper.addGrid(boardHoriz, activePiece, activeXOffset, activeYOffset)
        updateHorizGridView()
    }

    fun genFlyLeftAnim(): Animator {
        var startX = activeXOffset
        var endX = startX
        //Sink
        while (GridHelper.gridInBounds(boardHoriz, activePiece, endX - 1, activeYOffset) && !GridHelper.gridsCollide(boardHoriz, activePiece, endX - 1, activeYOffset)) {
            endX--
        }
        var anim = genPieceMoveAnim(gridbinderview_horizontal, activePiece, startX, activeYOffset, endX, activeYOffset)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                gridbinderview_horizontal.grid = boardHoriz//We let the animator draw the piece
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                currentScore += (10 * activeXOffset - endX)//10 points per space flown
                activeXOffset = endX
                pieceHitLeft()
            }
        })
        return anim
    }

    fun allowGameActions(): Boolean {
        return !paused && !(actionAnimator?.isRunning ?: false)
    }

    fun genHorizDropAnimation(): Animator {
        var animators = ArrayList<Animator>()

        //The working board is twice as tall as the display board, so pieces can be added over the bounds
        var workingBoard = Grid(boardVert.width, boardVert.height * 2)
        GridHelper.addGrid(workingBoard, GridHelper.copyGrid(boardVert), 0, boardVert.height);
        Timber.d("1 workingBoard:" + workingBoard.getPrintString(" - ", " * "))

        var duration = 250L

        //This builds up the drop animators
        for (piece in horizPieces.reversed()) {
            var coords = horizPieceCoords[piece]
            if (coords != null) {
                var rotated = piece.rotate(false)
                var startX = boardHoriz.height - coords.second - rotated.width
                var startY = coords.first + boardVert.height
                var endY = 0
                while (GridHelper.gridInBounds(workingBoard, rotated, startX, endY + 1) && !GridHelper.gridsCollide(workingBoard, rotated, startX, endY + 1)) {
                    endY++
                }

                GridHelper.addGrid(workingBoard, rotated, startX, endY)
                var pieceDoneBoard = GridHelper.copyGridPortion(workingBoard, 0, boardVert.height, boardVert.width, workingBoard.height)

                var pieceAnim = genPieceMoveAnim(gridbinderview_vertical, rotated, startX, startY - boardVert.height, startX, endY - boardVert.height)
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
            var rowRemovedBoard = GridHelper.copyGridPortion(workingBoard, 0, boardVert.height, boardVert.width, workingBoard.height)
            for (i in shifts.indices) {
                if (shifts[i] == -1) {
                    var vertBoardYCoordRowRemove = i - boardVert.height
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

                    for (i in 0..boardVert.width - 1) {
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
                gridbinderview_horizontal.alpha = 0f//Maybe we could find a better place for this?
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            fun done() {
                currentScore += (50 * shiftVal * shiftVal)
                currentRowsDestroyed += shiftVal
                var level = currentRowsDestroyed / 10 + 1
                currentLevel = level

                if(!workingBoard.rowEmpty(boardVert.height - 1)){
                    unsubscribeFromTicker()
                    var frag = DialogGameOverFragment()
                    frag.show(supportFragmentManager,FTAG_GAME_OVER)
                }
                boardVert = GridHelper.copyGridPortion(workingBoard, 0, boardVert.height, boardVert.width, workingBoard.height)
                updateVertGridView()


            }

        })
        return animatorSet
    }

    fun genHorizFlyInFromRightAnimation(): Animator {
        var horiBinder = gridbinderview_horizontal
        var animTransX = ObjectAnimator.ofFloat(horiBinder, "translationX", horiBinder.width.toFloat(), 0f)
        animTransX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                horizPieces.clear()
                horizPieceCoords.clear()
                boardHoriz.clear()
                updateHorizGridView()
                updateVertGridView()
                nextPiece()

                horiBinder.scaleX = 1f
                horiBinder.scaleY = 1f
                horiBinder.translationY = 0f
                horiBinder.translationX = horiBinder.width.toFloat()
                horiBinder.rotation = 0f
                gridbinderview_horizontal.alpha = 1f//Maybe we could find a better place for this?
            }

        })
        animTransX.interpolator = DecelerateInterpolator()
        return animTransX
    }

    fun genTwistAnimation(): Animator {
        var animSet = AnimatorSet()
        var vertBinder = gridbinderview_vertical
        var horiBinder = gridbinderview_horizontal
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

        //Set up the view
        var startX = startPos.left + binderView.left
        var startY = startPos.top + binderView.top
        var endX = endPos.left + binderView.left
        var endY = endPos.top + binderView.top

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
        actionAnimator?.cancel()
        actionAnimator = null

        boardVert = Grid(8, 16)
        boardHoriz = Grid(12, 8)
        boardUpcoming = Grid(4, 2)
        activePiece = TetrisFactory.j().rotate(false)
        upcomingPiece = TetrisFactory.randomPiece()
        activeXOffset = boardHoriz.width - activePiece.width
        activeYOffset = (boardHoriz.height / 2f).toInt()

        horizPieceCoords.clear()
        horizPieces.clear()

        currentRowsDestroyed = 0
        currentScore = 0
        currentLevel = 1

        updateHorizGridView()
        updateVertGridView()
    }
}
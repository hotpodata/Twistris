package com.hotpodata.twistris.data

import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.twistris.utils.TetrisFactory
import timber.log.Timber
import java.util.*

/**
 * Created by jdrotos on 1/3/16.
 */
class TwistrisGame() {

    val VERT_W = 8
    val VERT_H = 16
    val HORI_W = 12
    val HORI_H = 8

    val MAX_LEVEL = 10
    val LEVEL_STEP = 2

    var currentRowsDestroyed = 0
    var currentScore = 0
    var currentLevel = 1
    var gameIsOver = false

    var boardVert: Grid
    var boardHoriz: Grid

    var activePiece: Grid
        get() = horizPieces[horizPieces.size - 1].first
        set(piece: Grid) {
            horizPieces[horizPieces.size - 1] = Pair(piece, horizPieces[horizPieces.size - 1].second)
        }

    var activeXOffset: Int
        get() = horizPieces[horizPieces.size - 1].second.first
        set(offset: Int) {
            horizPieces[horizPieces.size - 1] = Pair(horizPieces[horizPieces.size - 1].first, Pair(offset, horizPieces[horizPieces.size - 1].second.second))
        }

    var activeYOffset: Int
        get() = horizPieces[horizPieces.size - 1].second.second
        set(offset: Int) {
            horizPieces[horizPieces.size - 1] = Pair(horizPieces[horizPieces.size - 1].first, Pair(horizPieces[horizPieces.size - 1].second.first, offset))
        }

    var upcomingPiece: Grid = TetrisFactory.randomPiece()
    var horizPieces = ArrayList<Pair<Grid, Pair<Int, Int>>>()

    init {
        boardVert = Grid(VERT_W, VERT_H)
        boardHoriz = Grid(HORI_W, HORI_H)
        actionNextPiece()
    }

    constructor(source: TwistrisGame) : this() {
        currentRowsDestroyed = source.currentRowsDestroyed
        currentScore = source.currentScore
        currentLevel = source.currentLevel

        boardVert = GridHelper.copyGrid(source.boardVert)
        boardHoriz = GridHelper.copyGrid(source.boardHoriz)

        upcomingPiece = GridHelper.copyGrid(source.upcomingPiece)

        horizPieces.clear()
        for (item in source.horizPieces) {
            horizPieces.add(Pair(GridHelper.copyGrid(item.first), Pair(item.second.first, item.second.second)))
        }
    }


    fun peekMoveActiveUp(): Boolean {
        return GridHelper.gridInBounds(boardHoriz, activePiece, activeXOffset, activeYOffset - 1) && !GridHelper.gridsCollide(boardHoriz, activePiece, activeXOffset, activeYOffset - 1)
    }

    fun actionMoveActiveUp(): Boolean {
        if (peekMoveActiveUp()) {
            activeYOffset--
            return true
        }
        return false
    }

    fun peekMoveActiveDown(): Boolean {
        return GridHelper.gridInBounds(boardHoriz, activePiece, activeXOffset, activeYOffset + 1) && !GridHelper.gridsCollide(boardHoriz, activePiece, activeXOffset, activeYOffset + 1)
    }

    fun actionMoveActiveDown(): Boolean {
        if (peekMoveActiveDown()) {
            activeYOffset++
            return true
        }
        return false
    }

    fun peekMoveActiveLeft(): Boolean {
        return GridHelper.gridInBounds(boardHoriz, activePiece, activeXOffset - 1, activeYOffset) && !GridHelper.gridsCollide(boardHoriz, activePiece, activeXOffset - 1, activeYOffset);
    }

    fun actionMoveActiveLeft(): Boolean {
        if (peekMoveActiveLeft()) {
            activeXOffset--
            return true
        }
        return false
    }

    fun actionMoveActiveAllTheWayLeft(): Boolean {
        var ret = false
        while (actionMoveActiveLeft()) {
            currentScore += 10
            ret = true
        }
        return ret
    }

    fun actionRotate(left: Boolean): Boolean {
        var rot = activePiece.rotate(left)
        var xOff = activeXOffset
        var yOff = activeYOffset

        //Now we make sure the coordinates are ok with the board (since rotations next to the edge are ok)
        while (xOff + rot.width > boardHoriz.width) {
            xOff--
        }
        while (yOff + rot.height > boardHoriz.height) {
            yOff--
        }

        //If we've got no collisions we're in great shape
        if (!GridHelper.gridsCollide(boardHoriz, rot, xOff, yOff)) {
            activeXOffset = xOff
            activeYOffset = yOff
            activePiece = rot
            return true
        } else {
            return false
        }
    }

    fun actionRotateActiveLeft(): Boolean {
        return actionRotate(true)
    }

    fun actionRotateActiveRight(): Boolean {
        return actionRotate(false)
    }

    fun actionTwistBoard() {
        //The working board is twice as tall as the display board, so pieces can be added over the bounds
        var workingBoard = Grid(boardVert.width, boardVert.height * 2)
        GridHelper.addGrid(workingBoard, GridHelper.copyGrid(boardVert), 0, boardVert.height);
        Timber.d("1 workingBoard:" + workingBoard.getPrintString(" - ", " * "))

        //Move all of the pieces down
        for (pieceAndCoords in horizPieces.reversed()) {
            var coords = pieceAndCoords.second
            if (coords != null) {
                var rotated = pieceAndCoords.first.rotate(false)
                var startX = boardHoriz.height - coords.second - rotated.width
                var endY = 0
                while (GridHelper.gridInBounds(workingBoard, rotated, startX, endY + 1) && !GridHelper.gridsCollide(workingBoard, rotated, startX, endY + 1)) {
                    endY++
                }
                GridHelper.addGrid(workingBoard, rotated, startX, endY)
            }
        }

        //Figure out the shift values
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

        //Shift working board for realz
        if (shiftVal > 0) {
            for (i in shifts.size - 1 downTo 0) {
                if (shifts[i] > 0) {
                    for (j in 0..workingBoard.width - 1) {
                        workingBoard.put(j, i + shifts[i], workingBoard.at(j, i))
                    }
                }
            }
        }

        horizPieces.clear()
        boardHoriz.clear()

        currentScore += (50 * shiftVal * shiftVal)
        currentRowsDestroyed += shiftVal
        currentLevel = currentRowsDestroyed / LEVEL_STEP + 1
        gameIsOver = !workingBoard.rowEmpty(boardVert.height - 1)

        boardVert = GridHelper.copyGridPortion(workingBoard, 0, boardVert.height, boardVert.width, workingBoard.height)
    }

    private fun randomWidePiece(): Grid {
        var newPiece = TetrisFactory.randomPiece()
        if (newPiece.height > newPiece.width) {
            newPiece = newPiece.rotate(false)
        }
        return newPiece
    }

    fun actionNextPiece() {
        //Commit the active piece to the board
        if (horizPieces.size > 0) {
            GridHelper.addGrid(boardHoriz, activePiece, activeXOffset, activeYOffset)
        }
        if (upcomingPiece == null) {
            upcomingPiece = randomWidePiece()
        }
        var piece = upcomingPiece
        var x = boardHoriz.width - piece.width
        var y = (boardHoriz.height / 2f - piece.height / 2f).toInt()
        horizPieces.add(Pair(piece, Pair(x, y)))
        upcomingPiece = randomWidePiece()
    }

    fun gameNeedsTwist(): Boolean {
        return !peekMoveActiveLeft() && horizPieces.size == currentLevel
    }

    fun gameNeedsNextPiece(): Boolean {
        return horizPieces.size == 0 || !peekMoveActiveLeft()
    }
}
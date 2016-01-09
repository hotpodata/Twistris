package com.hotpodata.twistris.utils

import android.content.Context
import android.graphics.Color
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.twistris.R
import java.util.*

/**
 * Created by jdrotos on 12/19/15.
 */
object TetrisFactory {
    var boxColor = Color.GRAY
    var jColor = Color.BLUE
    var lColor = Color.LTGRAY
    var barColor = Color.CYAN
    var sColor = Color.GREEN
    var zColor = Color.RED
    var tColor = Color.MAGENTA

    val random = Random()
    var allPieces: Array<Grid> = arrayOf(box(), bar(), j(), l(), z(), s(), t())

    fun setupResColors(ctx: Context){
        boxColor = ctx.resources.getColor(R.color.material_amber)
        jColor = ctx.resources.getColor(R.color.material_green)
        lColor = ctx.resources.getColor(R.color.material_deep_orange)
        barColor = ctx.resources.getColor(R.color.material_deep_purple)
        sColor = ctx.resources.getColor(R.color.material_blue_grey)
        zColor = ctx.resources.getColor(R.color.material_red)
        tColor = ctx.resources.getColor(R.color.material_purple)

        allPieces = arrayOf(box(), bar(), j(), l(), z(), s(), t())
    }

    fun randomPiece(): Grid {
        return GridHelper.copyGrid(allPieces.get(random.nextInt(allPieces.size)))
    }

    fun box(): Grid {
        var grid = Grid(2, 2)
        grid.put(0, 0, boxColor)
        grid.put(0, 1, boxColor)
        grid.put(1, 0, boxColor)
        grid.put(1, 1, boxColor)
        return grid
    }

    fun j(): Grid {
        var grid = Grid(2, 3)
        grid.put(1, 0, jColor)
        grid.put(1, 1, jColor)
        grid.put(1, 2, jColor)
        grid.put(0, 2, jColor)
        return grid
    }

    fun l(): Grid {
        var grid = Grid(2, 3)
        grid.put(0, 0, lColor)
        grid.put(0, 1, lColor)
        grid.put(0, 2, lColor)
        grid.put(1, 2, lColor)
        return grid
    }

    fun bar(): Grid {
        var grid = Grid(1, 4)
        grid.put(0, 0, barColor)
        grid.put(0, 1, barColor)
        grid.put(0, 2, barColor)
        grid.put(0, 3, barColor)
        return grid
    }

    fun s(): Grid {
        var grid = Grid(3, 2)
        grid.put(0, 1, sColor)
        grid.put(1, 0, sColor)
        grid.put(1, 1, sColor)
        grid.put(2, 0, sColor)
        return grid
    }

    fun z(): Grid {
        var grid = Grid(3, 2)
        grid.put(0, 0, zColor)
        grid.put(1, 0, zColor)
        grid.put(1, 1, zColor)
        grid.put(2, 1, zColor)
        return grid
    }

    fun t(): Grid {
        var grid = Grid(3, 2)
        grid.put(0, 0, tColor)
        grid.put(1, 0, tColor)
        grid.put(2, 0, tColor)
        grid.put(1, 1, tColor)
        return grid
    }



}
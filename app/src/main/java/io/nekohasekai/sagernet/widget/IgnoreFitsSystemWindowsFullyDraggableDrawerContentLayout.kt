/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <sekai@neko.services>                    *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

/******************************************************************************
 * *
 * Copyright (C) 2021 by nekohasekai <sekai></sekai>@neko.services>                    *
 * Copyright (C) 2021 by Max Lv <max.c.lv></max.c.lv>@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android></contact-shadowsocks-android>@mygod.be>  *
 * *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                       *
 * *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 * *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http:></http:>//www.gnu.org/licenses/>.       *
 * *
 */
package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import com.drakeet.drawer.FullDraggableContainer
import android.view.WindowInsets

class IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout : FullDraggableContainer {
    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context,
        attrs,
        defStyleAttr) {
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        return insets
    }
}

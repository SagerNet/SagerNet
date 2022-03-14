/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.ui

import android.os.Bundle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

class SwitchActivity : ThemedActivity(R.layout.layout_empty),
    ConfigurationFragment.SelectCallback {

    override val type = Type.Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.beginTransaction().replace(
            R.id.fragment_holder, ConfigurationFragment(true, null, R.string.action_switch)
        ).commitAllowingStateLoss()
    }

    override fun returnProfile(profileId: Long) {
        runOnDefaultDispatcher {
            val lastProxy = DataStore.selectedProxy
            DataStore.selectedProxy = profileId
            ProfileManager.postUpdate(lastProxy)
            ProfileManager.postUpdate(profileId)
            onMainDispatcher {
                SagerNet.reloadService()
            }
        }
        finish()
    }
}
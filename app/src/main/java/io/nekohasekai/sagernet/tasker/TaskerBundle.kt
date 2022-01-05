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

package io.nekohasekai.sagernet.tasker

import android.content.Intent
import android.os.Bundle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.app
import com.twofortyfouram.locale.api.Intent as ApiIntent

class TaskerBundle(val bundle: Bundle) {

    companion object {
        fun fromIntent(intent: Intent) =
            TaskerBundle(intent.getBundleExtra(ApiIntent.EXTRA_BUNDLE) ?: Bundle())

        const val KEY_ACTION = "action"
        const val ACTION_START = 0
        const val ACTION_STOP = 1

        const val KEY_PROFILE_ID = "profile"
    }

    var action: Int
        get() = bundle.getInt(KEY_ACTION, ACTION_START)
        set(value) {
            bundle.putInt(KEY_ACTION, value)
        }

    var profileId: Long
        get() = bundle.getLong(KEY_PROFILE_ID, -1L)
        set(value) {
            bundle.putLong(KEY_PROFILE_ID, value)
        }


    fun toIntent(): Intent {
        var blurb = ""
        when (action) {
            ACTION_START -> {
                if (profileId > 0) {
                    val entity = ProfileManager.getProfile(profileId)
                    if (entity != null) {
                        blurb = app.getString(
                            R.string.tasker_blurb_start_profile, entity.displayName()
                        )
                    }
                }
                if (blurb.isBlank()) {
                    blurb = app.getString(R.string.tasker_action_start_service)
                }
            }
            ACTION_STOP -> {
                blurb = app.getString(R.string.tasker_action_stop_service)
            }
        }
        return Intent().apply {
            putExtra(ApiIntent.EXTRA_BUNDLE, bundle)
            putExtra(ApiIntent.EXTRA_STRING_BLURB, blurb)
        }
    }

}
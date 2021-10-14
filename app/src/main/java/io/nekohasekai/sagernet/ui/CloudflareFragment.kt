/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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
import android.view.View
import androidx.appcompat.app.AlertDialog
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.databinding.LayoutCloudflareBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressBinding
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.Cloudflare
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

class CloudflareFragment : NamedFragment(R.layout.layout_cloudflare) {

    override fun name() = "CloudFlare"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutCloudflareBinding.bind(view)
        binding.warpGenerate.setOnClickListener {
            runBlocking {
                generateWarpConfiguration()
            }
        }
    }

    suspend fun generateWarpConfiguration() {
        val activity = requireActivity() as MainActivity
        val binding = LayoutProgressBinding.inflate(layoutInflater)
        var job: Job? = null
        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                job?.cancel()
            }
            .show()
        job = runOnDefaultDispatcher {
            try {
                val bean = Cloudflare.makeWireGuardConfiguration()
                if (isActive) {
                    val groupId = DataStore.selectedGroupForImport()
                    if (DataStore.selectedGroup != groupId) {
                        DataStore.selectedGroup = groupId
                    }
                    onMainDispatcher {
                        activity.displayFragmentWithId(R.id.nav_configuration)
                    }
                    delay(1000L)
                    onMainDispatcher {
                        dialog.dismiss()
                    }
                    ProfileManager.createProfile(groupId, bean)
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    if (isActive) {
                        dialog.dismiss()
                        activity.snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    }

}
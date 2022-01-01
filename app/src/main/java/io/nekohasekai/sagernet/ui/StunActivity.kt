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
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutStunBinding
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.widget.ListHolderListener
import io.noties.markwon.Markwon
import libcore.Libcore

class StunActivity : ThemedActivity() {

    private lateinit var binding: LayoutStunBinding
    private val markwon by lazy { Markwon.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutStunBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.stun_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }
        binding.stunTest.setOnClickListener {
            doTest()
        }

        doTest()
    }

    fun doTest() {
        binding.waitLayout.isVisible = true
        binding.resultLayout.isVisible = false
        runOnDefaultDispatcher {
            val result = try {
                Libcore.stunTest("", DataStore.socksPort)
            } catch (e: Exception) {
                onMainDispatcher {
                    AlertDialog.Builder(this@StunActivity)
                        .setTitle(R.string.error_title)
                        .setMessage(e.readableMessage)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            finish()
                        }
                        .setOnCancelListener {
                            finish()
                        }
                        .runCatching { show() }

                }
                return@runOnDefaultDispatcher
            }
            onMainDispatcher {
                binding.waitLayout.isVisible = false
                binding.resultLayout.isVisible = true
                when (result.natMapping) {
                    Libcore.StunEndpointIndependentNoNAT -> {
                        markwon.setMarkdown(
                            binding.natMappingBehaviour, getString(
                                R.string.nat_mapping_endpoint_independent,
                                getString(R.string.endpoint_independent_no_nat)
                            )
                        )
                    }
                    Libcore.StunEndpointIndependent -> {
                        markwon.setMarkdown(
                            binding.natMappingBehaviour, getString(
                                R.string.nat_mapping_endpoint_independent,
                                getString(R.string.endpoint_independent)
                            )
                        )
                    }
                    Libcore.StunAddressDependent -> {
                        markwon.setMarkdown(
                            binding.natMappingBehaviour, getString(
                                R.string.nat_mapping_address_dependent_and_address_and_port_dependent,
                                getString(R.string.address_dependent)
                            )
                        )
                    }
                    Libcore.StunAddressAndPortDependent -> {
                        markwon.setMarkdown(
                            binding.natMappingBehaviour, getString(
                                R.string.nat_mapping_address_dependent_and_address_and_port_dependent,
                                getString(R.string.address_and_port_dependent)
                            )
                        )
                    }
                }
                when (result.natFiltering) {
                    Libcore.StunEndpointIndependent -> {
                        markwon.setMarkdown(
                            binding.natFilteringBehaviour,
                            getString(R.string.nat_filtering_endpoint_independent)
                        )
                    }
                    Libcore.StunAddressDependent -> {
                        markwon.setMarkdown(
                            binding.natFilteringBehaviour,
                            getString(R.string.nat_filtering_address_dependent)
                        )
                    }
                    Libcore.StunAddressAndPortDependent -> {
                        markwon.setMarkdown(
                            binding.natFilteringBehaviour,
                            getString(R.string.nat_filtering_address_and_port_dependent)
                        )
                    }
                }
            }
        }
    }

}
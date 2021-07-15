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

package io.nekohasekai.sagernet.ui

import android.os.Bundle
import com.mikepenz.aboutlibraries.LibsBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.ListHolderListener

class LicenseActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.layout_license)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.oss_licenses)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }
        ListHolderListener.setup(this)

        val libs =
            LibsBuilder().withExcludedLibraries( // Can't parse ${project.artifactId} in pom.xml
                "cn_hutool__hutool_core", "cn_hutool__hutool_json", "cn_hutool__hutool_crypto", "cn_hutool__hutool_cache"
            ).withAboutIconShown(false).withFields(R.string::class.java.fields).supportFragment()

        supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, libs)
            .commitAllowingStateLoss()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
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

import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.isGone
import com.google.android.material.appbar.MaterialToolbar
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.*
import rikka.material.app.MaterialActivity


class ScannerActivity : MaterialActivity(), BarcodeCallback {

    lateinit var toolbar: MaterialToolbar
    lateinit var capture: CaptureManager
    lateinit var barcodeScanner: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 25) getSystemService<ShortcutManager>()!!.reportShortcutUsed("scan")
        setContentView(R.layout.layout_scanner)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_navigation_close)

        barcodeScanner = findViewById(R.id.barcode_scanner)
        barcodeScanner.statusView.isGone = true
        barcodeScanner.viewFinder.isGone = true
        barcodeScanner.barcodeView.setDecoderFactory {
            MixedDecoder(QRCodeReader())
        }

        capture = CaptureManager(this, barcodeScanner)
        barcodeScanner.decodeSingle(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.scanner_menu, menu)
        return true
    }

    val importCodeFile = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        runOnDefaultDispatcher {
            try {
                it.forEachTry { uri ->
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,
                            uri)) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    val intArray = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(intArray,
                        0,
                        bitmap.width,
                        0,
                        0, bitmap.width, bitmap.height)

                    val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
                    val qrReader = QRCodeReader()
                    try {
                        val result = try {
                            qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)),
                                mapOf(
                                    DecodeHintType.TRY_HARDER to true
                                ))
                        } catch (e: NotFoundException) {
                            qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())),
                                mapOf(
                                    DecodeHintType.TRY_HARDER to true
                                ))
                        }

                        val results = parseProxies(result.text ?: "")

                        if (results.isNotEmpty()) {
                            onMainDispatcher {
                                finish()
                            }
                            val currentGroupId = DataStore.selectedGroup
                            for (profile in results) {
                                ProfileManager.createProfile(currentGroupId, profile)
                            }
                        } else {
                            Toast.makeText(app, R.string.action_import_err, Toast.LENGTH_SHORT)
                                .show()
                        }
                    } catch (e: Throwable) {
                        Logs.w(e)
                        onMainDispatcher {
                            Toast.makeText(app, R.string.action_import_err, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)

                onMainDispatcher {
                    Toast.makeText(app, e.readableMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        importCodeFile.launch("image/*")
        return true
    }

    /**
     * See also: https://stackoverflow.com/a/31350642/2245107
     */
    override fun shouldUpRecreateTask(targetIntent: Intent?) =
        super.shouldUpRecreateTask(targetIntent) || isTaskRoot

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return barcodeScanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun barcodeResult(result: BarcodeResult) {
        finish()
        val text = result.result.text
        runOnDefaultDispatcher {
            val results = parseProxies(text)
            if (results.isNotEmpty()) {
                val currentGroupId = DataStore.selectedGroup

                for (profile in results) {
                    ProfileManager.createProfile(currentGroupId, profile)
                }
            } else {
                Toast.makeText(app, R.string.action_import_err, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

}

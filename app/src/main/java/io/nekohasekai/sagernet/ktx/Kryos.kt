package io.nekohasekai.sagernet.ktx

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import java.io.InputStream
import java.io.OutputStream


fun InputStream.byteBuffer() = ByteBufferInput(this)
fun OutputStream.byteBuffer() = ByteBufferOutput(this)
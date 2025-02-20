/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.video.internal.encoder

import android.media.MediaCodec
import android.os.Build
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(ParameterizedRobolectricTestRunner::class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
class BufferCopiedEncodedDataTest(
    private val sourceOffset: Int,
    private val sourceSize: Int
) {
    companion object {
        private const val SOURCE_CAPACITY = 1024
        // The index value is used to generate the content, 127 is chosen because it is the largest
        // prime number less than 128 (bytes allow -128 to 127).
        private val SOURCE_CONTENT = ByteArray(SOURCE_CAPACITY) { i -> (i % 127).toByte() }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "offset={0}, size={1}")
        fun createTestSet() = listOf(
            arrayOf(0, 1024),
            arrayOf(0, 0),
            arrayOf(1024, 0),
            arrayOf(1023, 1),
            arrayOf(1, 1023),
            arrayOf(512, 512),
            arrayOf(0, 29),
            arrayOf(947, 73),
        )
    }

    private val sourceTimeUs = 123456L
    private val sourceFlags = 0
    private lateinit var fakeEncodedData: EncodedData
    private lateinit var copiedEncodedData: EncodedData

    @Before
    fun setup() {
        val byteBuffer = ByteBuffer.allocate(SOURCE_CAPACITY)
        byteBuffer.put(SOURCE_CONTENT)
        val bufferInfo = MediaCodec.BufferInfo()
        bufferInfo.set(sourceOffset, sourceSize, sourceTimeUs, sourceFlags)

        fakeEncodedData = FakeEncodedData(byteBuffer, bufferInfo)
        copiedEncodedData = BufferCopiedEncodedData(fakeEncodedData)
    }

    @Test
    fun getBufferInfo_haveSameContentExceptOffsetIsZero() {
        with(copiedEncodedData.bufferInfo) {
            assertThat(offset).isEqualTo(0)
            assertThat(size).isEqualTo(sourceSize)
            assertThat(presentationTimeUs).isEqualTo(sourceTimeUs)
            assertThat(flags).isEqualTo(sourceFlags)
        }
    }

    @Test
    fun getByteBuffer_haveSameContent() {
        val sourceByteBuffer = fakeEncodedData.byteBuffer
        val copiedByteBuffer = copiedEncodedData.byteBuffer
        assertThat(copiedByteBuffer).isEqualTo(sourceByteBuffer)
    }

    @Test
    fun getByteBuffer_capacityEqualsToBufferInfoSize() {
        val bufferInfo = copiedEncodedData.bufferInfo
        val byteBuffer = copiedEncodedData.byteBuffer
        assertThat(byteBuffer.capacity()).isEqualTo(bufferInfo.size)
    }

    @Test
    fun getPresentationTimeUs_returnCorrectResult() {
        assertThat(copiedEncodedData.presentationTimeUs).isEqualTo(sourceTimeUs)
    }

    @Test
    fun getSize_returnCorrectResult() {
        assertThat(copiedEncodedData.size()).isEqualTo(fakeEncodedData.size())
    }

    @Test
    fun getIsKeyFrame_returnCorrectResult() {
        assertThat(copiedEncodedData.isKeyFrame).isEqualTo(fakeEncodedData.isKeyFrame)
    }
}
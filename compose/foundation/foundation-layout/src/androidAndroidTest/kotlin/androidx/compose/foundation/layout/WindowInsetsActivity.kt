/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.compose.foundation.layout

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import java.util.concurrent.CountDownLatch

class WindowInsetsActivity : ComponentActivity() {
    val createdLatch = CountDownLatch(1)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        createdLatch.countDown()
    }
}

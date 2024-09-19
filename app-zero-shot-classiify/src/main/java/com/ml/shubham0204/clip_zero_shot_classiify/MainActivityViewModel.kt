/*
 * Copyright (C) 2024 Shubham Panchal
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

package com.ml.shubham0204.clip_zero_shot_classiify

import android.clip.cpp.CLIPAndroid
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel : ViewModel() {

    val selectedImageState = mutableStateOf<Bitmap?>(null)
    val descriptionState = mutableStateOf("")
    val isLoadingModelState = mutableStateOf(true)
    val isInferenceRunning = mutableStateOf(false)
    val isShowingModelInfoDialogState = mutableStateOf(false)
    val classConfListState = mutableStateOf<List<Pair<String, Float>>?>(null)
    private val clipAndroid = CLIPAndroid()
    var visionHyperParameters: CLIPAndroid.CLIPVisionHyperParameters? = null
    var textHyperParameters: CLIPAndroid.CLIPTextHyperParameters? = null

    private val MODEL_PATH = "/data/local/tmp/clip_model_fp16.gguf"
    private val NUM_THREADS = 4
    private val VERBOSITY = 1

    init {
        CoroutineScope(Dispatchers.IO).launch {
            mainScope { isLoadingModelState.value = true }
            clipAndroid.load(MODEL_PATH, VERBOSITY)
            visionHyperParameters = clipAndroid.visionHyperParameters
            textHyperParameters = clipAndroid.textHyperParameters
            mainScope { isLoadingModelState.value = false }
        }
    }

    fun classify() {
        if (selectedImageState.value != null && descriptionState.value.isNotEmpty()) {
            CoroutineScope(Dispatchers.Default).launch {
                mainScope { isInferenceRunning.value = true }

                val classes =
                    descriptionState.value
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { it.lowercase() }
                        .toTypedArray()

                val imageBuffer = bitmapToByteBuffer(selectedImageState.value!!)
                val scoresAndIndices =
                    clipAndroid
                        .zeroShotClassify(
                            imageBuffer,
                            selectedImageState.value!!.width,
                            selectedImageState.value!!.height,
                            NUM_THREADS,
                            classes
                        )
                        .toList()
                val scores = scoresAndIndices.take(classes.size)
                val indices = scoresAndIndices.takeLast(classes.size)
                mainScope {
                    Log.d("MainActivityViewModel", "Scores: $scores")
                    classConfListState.value =
                        indices.map { it.toInt() }.map { index -> classes[index] }.zip(scores)
                    isInferenceRunning.value = false
                }
            }
        }
    }

    fun showModelInfo() {
        isShowingModelInfoDialogState.value = true
    }

    fun reset() {
        selectedImageState.value = null
        descriptionState.value = ""
        classConfListState.value = null
        isInferenceRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        clipAndroid.close()
    }

    private suspend fun mainScope(action: () -> Unit) {
        withContext(Dispatchers.Main) { action() }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val imageBuffer = ByteBuffer.allocateDirect(width * height * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                imageBuffer.put((pixel shr 16 and 0xFF).toByte())
                imageBuffer.put((pixel shr 8 and 0xFF).toByte())
                imageBuffer.put((pixel and 0xFF).toByte())
            }
        }
        return imageBuffer
    }
}

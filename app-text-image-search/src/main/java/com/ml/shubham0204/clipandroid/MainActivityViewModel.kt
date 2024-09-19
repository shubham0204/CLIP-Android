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

package com.ml.shubham0204.clipandroid

import android.clip.cpp.CLIPAndroid
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory.decodeStream
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.mutableStateOf
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import com.ml.shubham0204.clipandroid.data.ImagesDB
import com.ml.shubham0204.clipandroid.data.VectorSearchResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class MainActivityViewModel : ViewModel() {

    val selectedImagesUriListState = mutableStateOf<List<Uri>>(emptyList())
    val queryTextState = mutableStateOf("")
    val isLoadingModelState = mutableStateOf(true)
    val isInsertingImagesState = mutableStateOf(false)
    val insertedImagesCountState = mutableStateOf(0)
    val isShowingModelInfoDialogState = mutableStateOf(false)
    val isInferenceRunningState = mutableStateOf(false)

    val isShowingResultsState = mutableStateOf(false)
    val vectorSearchResultsState = mutableStateOf<VectorSearchResults?>(null)

    private val clipAndroid = CLIPAndroid()
    var visionHyperParameters: CLIPAndroid.CLIPVisionHyperParameters? = null
    var textHyperParameters: CLIPAndroid.CLIPTextHyperParameters? = null

    private val MODEL_PATH = "/data/local/tmp/clip_model_fp16.gguf"
    private val NUM_THREADS = 4
    private val VERBOSITY = 1
    private val embeddingDim = 512
    private val threshold = 0.8

    private val imagesDB = ImagesDB()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            mainScope { isLoadingModelState.value = true }
            clipAndroid.load(MODEL_PATH, VERBOSITY)
            visionHyperParameters = clipAndroid.visionHyperParameters
            textHyperParameters = clipAndroid.textHyperParameters
            mainScope { isLoadingModelState.value = false }
        }
    }

    fun processQuery() {
        CoroutineScope(Dispatchers.IO).launch {
            mainScope { isInferenceRunningState.value = true }
            val textEmbedding =
                clipAndroid.encodeText(
                    queryTextState.value.lowercase(),
                    NUM_THREADS,
                    embeddingDim,
                    true
                )
            val vectorSearchResults = imagesDB.nearestNeighbors(textEmbedding)
            mainScope {
                vectorSearchResultsState.value = vectorSearchResults
                selectedImagesUriListState.value =
                    vectorSearchResults.imageEntities
                        .filterIndexed { index, imageEntity ->
                            vectorSearchResults.scores[index] <= threshold
                        }
                        .map { Uri.parse(it.uri) }
                isInferenceRunningState.value = false
                isShowingResultsState.value = true
            }
        }
    }

    fun removeAllImages() {
        CoroutineScope(Dispatchers.IO).launch {
            imagesDB.removeAll()
            mainScope { selectedImagesUriListState.value = emptyList() }
        }
    }

    fun addImagesToDB(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            mainScope { isInsertingImagesState.value = true }
            flow {
                    selectedImagesUriListState.value.forEach { uri ->
                        val bitmap = getFixedBitmap(context, uri)
                        val resizedBitmap =
                            Bitmap.createScaledBitmap(
                                bitmap,
                                visionHyperParameters?.imageSize ?: 224,
                                visionHyperParameters?.imageSize ?: 224,
                                true
                            )
                        val imageBuffer = bitmapToByteBuffer(resizedBitmap)
                        emit(
                            Pair(
                                imageBuffer,
                                Pair(uri, Size(resizedBitmap.width, resizedBitmap.height))
                            )
                        )
                    }
                }
                .buffer()
                .collect { (imageBuffer, uriAndSize) ->
                    val imageEmbedding =
                        clipAndroid.encodeImageNoResize(
                            imageBuffer,
                            uriAndSize.second.width,
                            uriAndSize.second.height,
                            NUM_THREADS,
                            embeddingDim,
                            true
                        )
                    imagesDB.add(uriAndSize.first.toString(), imageEmbedding)
                    mainScope { insertedImagesCountState.value += 1 }
                }
            mainScope {
                isInsertingImagesState.value = false
                insertedImagesCountState.value = 0
            }
        }
    }

    fun loadImages() {
        CoroutineScope(Dispatchers.IO).launch {
            val images = imagesDB.getAll()
            mainScope { selectedImagesUriListState.value = images.map { Uri.parse(it.uri) } }
        }
    }

    fun showModelInfo() {
        isShowingModelInfoDialogState.value = true
    }

    fun closeResults() {
        isShowingResultsState.value = false
        loadImages()
        queryTextState.value = ""
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

    private fun getFixedBitmap(context: Context, imageFileUri: Uri): Bitmap {
        var imageBitmap = decodeStream(context.contentResolver.openInputStream(imageFileUri))
        val exifInterface = ExifInterface(context.contentResolver.openInputStream(imageFileUri)!!)
        imageBitmap =
            when (
                exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(imageBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(imageBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(imageBitmap, 270f)
                else -> imageBitmap
            }
        return imageBitmap
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }
}

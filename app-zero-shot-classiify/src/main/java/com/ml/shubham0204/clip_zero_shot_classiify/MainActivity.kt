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

import android.graphics.Bitmap
import android.graphics.BitmapFactory.decodeStream
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ml.shubham0204.clip_zero_shot_classiify.ui.components.AppProgressDialog
import com.ml.shubham0204.clip_zero_shot_classiify.ui.components.hideProgressDialog
import com.ml.shubham0204.clip_zero_shot_classiify.ui.components.setProgressDialogText
import com.ml.shubham0204.clip_zero_shot_classiify.ui.components.showProgressDialog
import com.ml.shubham0204.clip_zero_shot_classiify.ui.theme.CLIPAndroidTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = viewModel<MainActivityViewModel>()

            CLIPAndroidTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Zero Shot Labelling") },
                            actions = {
                                Row {
                                    IconButton(onClick = { viewModel.showModelInfo() }) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Model Info"
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        SelectImagePanel(viewModel)
                        EnterDescriptionPanel(viewModel)
                    }
                    LoadModelProgressDialog(viewModel)
                    RunningInferenceProgressDialog(viewModel)
                    ModelInfoDialog(viewModel)
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.SelectImagePanel(viewModel: MainActivityViewModel) {
        var selectedImage by remember { viewModel.selectedImageState }
        val pickMediaLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) {
                if (it != null) {
                    val bitmap = getFixedBitmap(it)
                    selectedImage = bitmap
                }
            }
        Column(
            modifier = Modifier.fillMaxSize().background(Color.LightGray).weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedImage == null) {
                Button(
                    modifier = Modifier.padding(vertical = 40.dp),
                    onClick = {
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Select an image")
                    Text(text = "Select an image")
                }
            } else {
                Image(
                    bitmap = selectedImage!!.asImageBitmap(),
                    contentDescription = "Selected image",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun ColumnScope.EnterDescriptionPanel(viewModel: MainActivityViewModel) {
        var description by remember { viewModel.descriptionState }
        val classConfList by remember { viewModel.classConfListState }
        Column(modifier = Modifier.fillMaxSize().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (classConfList == null) {
                    TextField(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        label = { Text(text = "Enter class names (comma separated)") },
                        value = description,
                        onValueChange = { description = it }
                    )
                    Button(enabled = description.isNotEmpty(), onClick = { viewModel.classify() }) {
                        Text(text = "Classify")
                    }
                } else {
                    classConfList?.forEach {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                text = it.first,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                text = "%.2f".format(it.second * 100) + " %",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Button(onClick = { viewModel.reset() }) { Text(text = "Try again") }
                }
            }
        }
    }

    @Composable
    private fun LoadModelProgressDialog(viewModel: MainActivityViewModel) {
        val isLoadingModel by remember { viewModel.isLoadingModelState }
        if (isLoadingModel) {
            showProgressDialog()
            setProgressDialogText("Loading model...")
        } else {
            hideProgressDialog()
        }
        AppProgressDialog()
    }

    @Composable
    private fun RunningInferenceProgressDialog(viewModel: MainActivityViewModel) {
        val isInferenceRunning by remember { viewModel.isInferenceRunning }
        if (isInferenceRunning) {
            showProgressDialog()
            setProgressDialogText("Running inference...")
        } else {
            hideProgressDialog()
        }
        AppProgressDialog()
    }

    @Composable
    private fun ModelInfoDialog(viewModel: MainActivityViewModel) {
        var showDialog by remember { viewModel.isShowingModelInfoDialogState }
        if (
            showDialog &&
                viewModel.visionHyperParameters != null &&
                viewModel.textHyperParameters != null
        ) {
            Dialog(onDismissRequest = { showDialog = false }) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(8.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Model Info",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        IconButton(onClick = { showDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Model Info Dialog"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Vision Hyper-parameters",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "imageSize = ${viewModel.visionHyperParameters?.imageSize}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "hiddenSize = ${viewModel.visionHyperParameters?.hiddenSize}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "patchSize = ${viewModel.visionHyperParameters?.patchSize}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "projectionDim = ${viewModel.visionHyperParameters?.projectionDim}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "num layers = ${viewModel.visionHyperParameters?.nLayer}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text =
                            "num intermediate = ${viewModel.visionHyperParameters?.nIntermediate}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "num heads = ${viewModel.visionHyperParameters?.nHead}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Text Hyper-parameters", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "num positions = ${viewModel.textHyperParameters?.numPositions}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "hiddenSize = ${viewModel.textHyperParameters?.hiddenSize}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "num vocab = ${viewModel.textHyperParameters?.nVocab}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "projectionDim = ${viewModel.textHyperParameters?.projectionDim}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "num layers = ${viewModel.textHyperParameters?.nLayer}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "num intermediate = ${viewModel.textHyperParameters?.nIntermediate}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "num heads = ${viewModel.textHyperParameters?.nHead}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    private fun getFixedBitmap(imageFileUri: Uri): Bitmap {
        var imageBitmap = decodeStream(contentResolver.openInputStream(imageFileUri))
        val exifInterface = ExifInterface(contentResolver.openInputStream(imageFileUri)!!)
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

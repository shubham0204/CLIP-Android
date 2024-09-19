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

package com.ml.shubham0204.clipandroid.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class ImageEntity(
    @Id var id: Long = 0,
    var uri: String = "",
    @HnswIndex(dimensions = 512, distanceType = VectorDistanceType.DOT_PRODUCT)
    var embedding: FloatArray = floatArrayOf()
)

data class VectorSearchResults(
    val imageEntities: List<ImageEntity>,
    val scores: List<Double>,
    val numVectorsSearched: Int,
    val timeTakenMillis: Long
)

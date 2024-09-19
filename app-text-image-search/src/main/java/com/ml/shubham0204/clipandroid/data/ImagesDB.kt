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

import io.objectbox.Box
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImagesDB {

    private val imagesBox: Box<ImageEntity> = ObjectBoxStore.store.boxFor(ImageEntity::class.java)

    suspend fun add(uri: String, embedding: FloatArray) =
        withContext(Dispatchers.IO) { imagesBox.put(ImageEntity(uri = uri, embedding = embedding)) }

    suspend fun getAll(): List<ImageEntity> = withContext(Dispatchers.IO) { imagesBox.all }

    suspend fun removeAll() = withContext(Dispatchers.IO) { imagesBox.removeAll() }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) { imagesBox.remove(id) }

    suspend fun nearestNeighbors(embedding: FloatArray): VectorSearchResults =
        withContext(Dispatchers.IO) {
            val query =
                imagesBox.query(ImageEntity_.embedding.nearestNeighbors(embedding, 50)).build()
            val (results, time) = measureTimedValue { query.findWithScores() }
            val scores = results.map { it.score }
            val entities = results.map { it.get() }
            VectorSearchResults(
                imageEntities = entities,
                scores = scores,
                numVectorsSearched = results.size,
                timeTakenMillis = time.toLong(DurationUnit.MILLISECONDS)
            )
        }
}

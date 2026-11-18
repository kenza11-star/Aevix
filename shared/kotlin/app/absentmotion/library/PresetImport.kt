package app.absentmotion.library

import app.absentmotion.R
import app.absentmotion.engine.EngineThread
import app.absentmotion.i18n.txt
import app.absentmotion.model.Scene
import app.absentmotion.model.storedMediaUris
import app.absentmotion.platform.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

data class PresetImportResult(val presets: List<SavedPreset>, val reports: List<ImportReport>)

class PresetImport(
    private val projects: ProjectStore,
    private val presets: PresetStore,
    private val importer: Importer,
    private val render: suspend (Scene, Int, File) -> Boolean,
) {
    suspend fun import(open: () -> InputStream?): PresetImportResult {
        val reports = ArrayList<ImportReport>()
        val saved = ArrayList<SavedPreset>()
        var complete = false
        try {
            val result = withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                reports += importer.import {
                    open()?.let { input -> object : FilterInputStream(input) {
                        override fun read(): Int { job.ensureActive(); return super.read() }
                        override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                            job.ensureActive()
                            return super.read(bytes, offset, count)
                        }
                    } }
                }
                for (report in reports) {
                    job.ensureActive()
                    val scene = projects.load(report.id) ?: throw IOException("The imported scene could not be read")
                    if (scene.layers.isEmpty()) throw ImportFailed(txt(R.string.preset_import_empty_scene))
                    val preset = presets.addComposition(scene, render)
                    saved += preset
                    if (preset.scene.node.storedMediaUris().any(projects::inMediaFolder)) {
                        throw IOException("Preset media could not be copied")
                    }
                }
                PresetImportResult(saved.toList(), reports.mapIndexed { index, report -> report.copy(id = saved[index].id) })
            }
            complete = true
            return result
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (!complete) saved.forEach { presets.delete(it.id) }
                reports.forEach { projects.delete(it.id) }
            }
        }
    }

    companion object {
        fun forApp(context: Context, projects: ProjectStore, engine: EngineThread): PresetImport =
            PresetImport(projects, PresetStore(context), Importer.forApp(context, projects)) { scene, time, file ->
                val side = maxOf(scene.width, scene.height, 1)
                engine.stillPng(scene.toXml(), time, (256L * scene.width / side).toInt().coerceAtLeast(1),
                    (256L * scene.height / side).toInt().coerceAtLeast(1), file)
            }
    }
}

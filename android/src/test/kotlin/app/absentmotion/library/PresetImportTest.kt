package app.absentmotion.library

import app.absentmotion.model.Scene
import app.absentmotion.model.mediaUris
import app.absentmotion.compat.EffectMigration
import app.absentmotion.effects.EffectCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PresetImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val xml = """<scene title="Imported motion" width="720" height="1280" fps="24" totalTime="2000" am="com.alightcreative.motion/5">
        <shape id="1" label="First" startTime="0" endTime="1999" s=".rect"><transform><location><kf t="0" v="10,20,0"/><kf t="1" v="100,200,0"/></location></transform></shape>
        <text id="2" label="Second" startTime="500" endTime="1999" size="32">Hello</text>
        </scene>"""

    private fun service(projects: ProjectStore, presets: PresetStore, render: suspend (Scene, Int, File) -> Boolean = { _, _, file ->
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        true
    }) = PresetImport(projects, presets, Importer(projects), render)

    @Test fun completeCompositionAndKeyframesAreSavedAndStagingProjectsAreRemoved() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        val result = service(projects, presets).import { ByteArrayInputStream(xml.toByteArray()) }
        val saved = presets.load(result.presets.single().id)!!
        assertTrue(saved.composition)
        assertEquals(listOf("First", "Second"), saved.scene.layers.map { it.label })
        assertEquals(24, saved.scene.fps)
        assertEquals(2000, saved.scene.totalTime)
        assertEquals("100,200,0", saved.scene.layers[0].node.child("transform")!!.child("location")!!.children[1]["v"])
        assertTrue(saved.thumbnail!!.isFile)
        assertTrue(projects.list().isEmpty())
        presets.rename(saved.id, "Renamed")
        assertEquals("Renamed", presets.load(saved.id)!!.title)
    }

    @Test fun rendererFailureDoesNotPublishAPresetOrLeaveAProject() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        try {
            service(projects, presets) { _, _, _ -> false }.import { ByteArrayInputStream(xml.toByteArray()) }
            fail("A failed render was published")
        } catch (_: IOException) {
            assertTrue(presets.list().isEmpty())
            assertTrue(projects.list().isEmpty())
        }
    }

    @Test fun cancellationRollsBackFiles() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val folder = tmp.newFolder()
        val presets = PresetStore(folder)
        try {
            service(projects, presets) { _, _, _ -> throw CancellationException("cancel") }.import { ByteArrayInputStream(xml.toByteArray()) }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) {
            assertTrue(presets.list().isEmpty())
            assertTrue(projects.list().isEmpty())
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun packagedMediaBelongsToThePresetAfterTemporaryProjectsAreDeleted() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        val scene = """<scene title="Media" width="100" height="100" fps="30" totalTime="999"><media uri="amproj:a.png" filename="a.png"/><shape id="1" startTime="0" endTime="999" fillType="media" fillImage="amproj:a.png"/></scene>"""
        val manifest = """{"format":"absentmotion-package","version":1,"media":[{"uri":"amproj:a.png","entry":"media/a.png"}]}"""
        val bytes = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            for ((name, data) in listOf("project.xml" to scene, "manifest.json" to manifest, "media/a.png" to "image bytes")) {
                zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry()
            }
        } }.toByteArray()
        val result = service(projects, presets).import { ByteArrayInputStream(bytes) }
        val saved = presets.load(result.presets.single().id)!!
        val uris = saved.scene.node.mediaUris().toList()
        assertTrue(uris.isNotEmpty())
        assertFalse(uris.any(projects::inMediaFolder))
        val media = File(URI(uris.first()))
        assertEquals("image bytes", media.readText())
        presets.delete(saved.id)
        assertFalse(media.exists())
        assertTrue(projects.list().isEmpty())
    }

    @Test fun emptySceneCannotBecomeAnInvisiblePreset() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        try {
            service(projects, presets).import { ByteArrayInputStream("<scene width=\"100\" height=\"100\"/>".toByteArray()) }
            fail("Saved an empty preset")
        } catch (_: ImportFailed) {
            assertTrue(presets.list().isEmpty())
            assertTrue(projects.list().isEmpty())
        }
    }

    @Test fun previewUsesAVisibleLayerTimeInsteadOfAnEmptyGap() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        val scene = """<scene width="100" height="100" fps="30" totalTime="8000"><shape id="1" hidden="true" startTime="0" endTime="7999"/><shape id="2" startTime="6000" endTime="7999"/></scene>"""
        var renderedAt = -1
        service(projects, presets) { _, time, file -> renderedAt = time; file.writeText("preview"); true }
            .import { ByteArrayInputStream(scene.toByteArray()) }
        assertEquals(6999, renderedAt)
    }

    @Test fun alightEffectsAreConvertedBeforeTheRendererReceivesTheScene() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        val pack = File("../shared/assets/effects")
        val catalog = EffectCatalog.parse { pack.resolve(it).inputStream() }
        val migration = EffectMigration.parse(pack.resolve("foreign.json").readText(), catalog)
        val input = """<scene title="Alight" width="100" height="100" fps="30" totalTime="999" am="com.alightcreative.motion/5"><shape id="1" startTime="0" endTime="999"><effect id="com.alightcreative.effects.mirror"><property name="type" type="int" value="1"/></effect></shape></scene>"""
        var renderedId: String? = null
        val importer = PresetImport(projects, presets, Importer(projects, migration = migration, canRun = { catalog.find(it) != null })) { scene, _, file ->
            renderedId = scene.layers.single().node.child("effect")?.get("id")
            file.writeText("preview")
            true
        }
        val result = importer.import { ByteArrayInputStream(input.toByteArray()) }
        assertEquals("absent.mirror", renderedId)
        assertEquals("absent.mirror", result.presets.single().scene.layers.single().node.child("effect")!!["id"])
    }

    @Test fun unknownEffectsAreKeptWithACompatibilityReport() = runBlocking {
        val projects = ProjectStore(tmp.newFolder())
        val presets = PresetStore(tmp.newFolder())
        val input = """<scene width="100" height="100" fps="30" totalTime="999"><shape id="1" startTime="0" endTime="999"><effect id="com.alightcreative.effects.unknownfutureeffect"/></shape></scene>"""
        val importer = PresetImport(projects, presets, Importer(projects, canRun = { false })) { _, _, file -> file.writeText("preview"); true }
        val result = importer.import { ByteArrayInputStream(input.toByteArray()) }
        assertEquals("com.alightcreative.effects.unknownfutureeffect", result.presets.single().scene.layers.single().node.child("effect")!!["id"])
        assertTrue(result.reports.single().sections.isNotEmpty())
    }
}

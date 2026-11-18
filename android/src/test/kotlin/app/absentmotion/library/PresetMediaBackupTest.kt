package app.absentmotion.library

import app.absentmotion.model.Scene
import app.absentmotion.model.mediaUris
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

class PresetMediaBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private suspend fun imported(root: File): SavedPreset {
        val picture = tmp.newFile().apply { writeText("preset image") }
        val scene = Scene.parse("""<scene title="Motion" width="100" height="100" fps="30" totalTime="999"><shape id="1" startTime="0" endTime="999" fillType="media" fillImage="${picture.toURI()}"/></scene>""")
        return PresetStore(File(root, "presets")).addComposition(scene) { _, _, file -> file.writeText("preview"); true }
    }

    private fun backup(root: File): ByteArray = ByteArrayOutputStream().also { Backup.write(root, it, true) }.toByteArray()

    @Test fun importedPresetMediaIsReadableAndPrivatePresetXmlIsNot() = runBlocking {
        val root = tmp.newFolder()
        val preset = imported(root)
        val access = MediaAccess.forPhone(root)
        assertTrue(preset.scene.node.mediaUris().all(access::allows))
        assertFalse(access.allows(File(root, "presets/${preset.id}.xml").toURI().toString()))
        assertFalse(access.allows(File(root, "presets/${preset.id}.media").toURI().toString()))
    }

    @Test fun backupRestoresPreviewAndMediaAtTheNewLocation() = runBlocking {
        val root = tmp.newFolder()
        val preset = imported(root)
        val restoredRoot = tmp.newFolder()
        Backup.restore(ProjectStore(restoredRoot), restoredRoot, ByteArrayInputStream(backup(root)))
        val restored = PresetStore(File(restoredRoot, "presets")).load(preset.id)!!
        assertEquals("preview", restored.thumbnail!!.readText())
        val uri = restored.scene.node.mediaUris().single()
        assertTrue(File(URI(uri)).canonicalPath.startsWith(restoredRoot.canonicalPath + File.separator))
        assertEquals("preset image", File(URI(uri)).readText())
    }

    @Test fun collisionRestoresANewPresetAndKeepsTheExistingMedia() = runBlocking {
        val root = tmp.newFolder()
        val preset = imported(root)
        val restoredRoot = tmp.newFolder()
        root.copyRecursively(restoredRoot, overwrite = true)
        val existingXml = File(restoredRoot, "presets/${preset.id}.xml")
        existingXml.writeText(existingXml.readText().replace(root.toURI().toString(), restoredRoot.toURI().toString()))
        val store = PresetStore(File(restoredRoot, "presets"))
        store.rename(preset.id, "Keep existing")
        val existingMedia = File(restoredRoot, "presets/${preset.id}.media").listFiles()!!.first().apply { writeText("existing image") }
        Backup.restore(ProjectStore(restoredRoot), restoredRoot, ByteArrayInputStream(backup(root)))
        assertEquals("existing image", existingMedia.readText())
        assertEquals(setOf("Keep existing", "Motion"), store.list().map { it.title }.toSet())
        val restored = store.list().single { it.title == "Motion" }
        assertTrue(restored.id != preset.id)
        val uri = restored.scene.node.mediaUris().single()
        assertEquals("preset image", File(URI(uri)).readText())
        assertTrue(File(URI(uri)).canonicalPath.contains(restored.id + ".media"))
    }

    @Test fun backupWithoutMediaOmitsThePresetAssets() = runBlocking {
        val root = tmp.newFolder()
        val preset = imported(root)
        val names = Backup.files(root, false).map { it.first }
        assertTrue("presets/${preset.id}.xml" in names)
        assertTrue("presets/${preset.id}.png" in names)
        assertFalse(names.any { it.contains("${preset.id}.media/") })
    }

    @Test fun storageCleanupKeepsSavedMediaAndRemovesAbandonedImportFiles() = runBlocking {
        val root = tmp.newFolder()
        val preset = imported(root)
        val live = File(URI(preset.scene.node.mediaUris().single()))
        val abandoned = File(root, "presets/abandoned.media/photo.png").apply { parentFile!!.mkdirs(); writeText("unused") }
        val thumbnail = File(root, "presets/abandoned.png").apply { writeText("unused") }
        val old = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000
        root.walkBottomUp().forEach { it.setLastModified(old) }
        sweepStorage(root, emptyList())
        assertTrue(live.isFile)
        assertFalse(abandoned.exists())
        assertFalse(thumbnail.exists())
    }
}

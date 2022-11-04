package net.pillowmc.pillow.gradle

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import net.minecraftforge.artifactural.api.repository.Repository
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder
import net.minecraftforge.artifactural.base.repository.SimpleRepository
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter
import net.minecraftforge.gradle.common.util.Artifact
import net.minecraftforge.gradle.common.util.BaseRepo
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader
import net.minecraftforge.gradle.common.util.Utils
import org.gradle.api.Project
import org.w3c.dom.Node
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class TweakMixinRepo(cache: File, private val project: Project) : BaseRepo(cache, project.logger) {
    private val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(
            ArtifactIdentifier::class.java
        ).provide(this)
    )

    companion object {
        private var INSTANCE: TweakMixinRepo? = null
        val xPath: XPath = XPathFactory.newInstance().newXPath()
        private fun getInstance(project: Project): TweakMixinRepo {
            if(INSTANCE==null){
                INSTANCE= TweakMixinRepo(Utils.getCache(project, "mxn_repo"), project)
            }
            return INSTANCE!!
        }

        fun attach(project: Project) {
            val instance = getInstance(project)
            GradleRepositoryAdapter.add(project.repositories, "MXN_REPO", instance.cacheRoot, instance.repo)
        }
    }
    override fun findFile(artifact: ArtifactIdentifier): File? {
        if(!(artifact.version.endsWith("_mxn")&&artifact.name.equals("sponge-mixin")))return null
        val original = Artifact.from(artifact).withVersion(artifact.version.substring(0, artifact.version.length-4))
        if(artifact.extension.equals("pom"))
            return tweakPom(original)
        else if(artifact.extension.equals("jar"))
            return tweakJar(original)
        return null
    }
    private fun tweakJar(artifact: Artifact): File? {
        val origin = download(artifact) ?: return null
        val output=Utils.getCache(project, "mxn_repo", artifact.path)
        if(!output.exists()&&!output.isFile){
            if(output.isDirectory)throw FileAlreadyExistsException(output)
        }else{
            return output
        }
        val jarFile = JarOutputStream(output.outputStream())
        val oJarFile = JarFile(origin)
        for (entry in oJarFile.entries()) {
            jarFile.putNextEntry(entry)
            val ips=oJarFile.getInputStream(entry)
            if(entry.name.contains("cpw.mods.modlauncher")){
                val bytes = ips.readAllBytes()
                jarFile.write(bytes, 0, bytes.size-6)
            }else jarFile.write(ips.readAllBytes())
        }
        jarFile.close()
        oJarFile.close()
        return output
    }

    private fun tweakPom(artifact: Artifact): File? {
        val output=Utils.getCache(project, "mxn_repo", artifact.path)
        val origin = download(artifact) ?: return null
        if(!output.isFile){
            if(output.isDirectory)throw FileAlreadyExistsException(output)
            createFile(output)
        }else {
            return output
        }
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.parse(origin)
        val version = xPath.compile("/*[local-name()=\"project\"]/*[local-name()=\"version\"]").evaluate(doc, XPathConstants.NODE) as Node
        version.textContent = version.textContent + "_mxn"
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(output))
        return output
    }

    private fun download(artifact: Artifact): File? {
//        val cfg = project.configurations.create("i2m_conf_"+artifact.descriptor.replace(":", "_"))
//        cfg.dependencies.add(project.dependencies.create(artifact.descriptor))
//        cfg.resolutionStrategy {
//            it.cacheChangingModulesFor(5, TimeUnit.MINUTES)
//            it.cacheDynamicVersionsFor(5, TimeUnit.MINUTES)
//        }
//        val item = cfg.first()
//        project.configurations.remove(cfg)
//        return item
        return MavenArtifactDownloader.manual(project, artifact.toString(), false)
    }
    private fun createFile(file: File) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }
}

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
import net.pillowmc.remapper.NameOnlyRemapper
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.nio.charset.Charset
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class Intermediary2MCPRepo(cache: File, private val project: Project) : BaseRepo(cache, project.logger) {
    private val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(
            ArtifactIdentifier::class.java
        ).provide(this)
    )
    private var mappings: Map<String, String> = loadMappings()

    companion object {
        val FULL_CLASS = Regex("net\\.minecraft(\\.class_\\d+)+")
        val CLASS = Regex("class_\\d+(\\.class_\\d+)*")
        val METHOD = Regex("method_\\d+")
        val FIELD = Regex("field_\\d+")
        val xPath: XPath = XPathFactory.newInstance().newXPath()
        private var INSTANCE: Intermediary2MCPRepo? = null
        private fun getInstance(project: Project): Intermediary2MCPRepo {
            if(INSTANCE==null){
                INSTANCE= Intermediary2MCPRepo(Utils.getCache(project, "i2m_repo"), project)
            }
            return INSTANCE!!
        }

        fun attach(project: Project) {
            val instance = getInstance(project)
            GradleRepositoryAdapter.add(project.repositories, "I2M_REPO", instance.cacheRoot, instance.repo)
        }
    }
    private fun loadMappings(): Map<String, String>{
        val csv=CSVParser.parse(project.layout.buildDirectory.dir("genIntermediary2MCP").map { s -> s.file("mappings.csv") }.get().asFile, Charset.defaultCharset(), CSVFormat.DEFAULT.withSkipHeaderRecord())
        val res=HashMap<String, String>()
        csv.forEach{
            res[it[0]] = it[1]
        }
        return res
    }

    override fun findFile(artifact: ArtifactIdentifier): File? {
        if(!artifact.version.endsWith("_i2m"))return null

        val original = Artifact.from(artifact).withVersion(artifact.version.substring(0, artifact.version.length-4))
        if(artifact.extension.equals("pom")){
            return deobfPom(original)
        }else if(artifact.extension.equals("jar")){
            if(artifact.classifier!=null&&artifact.classifier.equals("sources")) {
                return deobfSrc(original)
            }
            return deobfBin(original)
        }
        return null
    }

    private fun deobfSrc(artifact: Artifact): File? {
        val output=Utils.getCache(project, "i2m_repo", artifact.path)
        if(!output.isFile){
            if(output.isDirectory)throw FileAlreadyExistsException(output)
        }else{
            return output
        }
        val jarFile = JarOutputStream(output.outputStream())
        val origin = download(artifact) ?: return null
        val oJarFile = JarFile(origin)
        for (entry in oJarFile.entries()) {
            jarFile.putNextEntry(entry)
            if(!entry.isDirectory){
                val ips=oJarFile.getInputStream(entry)
                val str=FIELD.replace(
                    METHOD.replace(
                        CLASS.replace(
                            FULL_CLASS.replace(ips.readAllBytes().decodeToString()) {
                                (mappings["net/minecraft/" + it.value.substring(14).replace(".", "$")]?:it.value).replace("/", ".").replace("$", ".")
                            }
                        ) {
                            (mappings["net/minecraft/" + it.value.replace(".", "$")]?:it.value).replace(Regex(".+/"), "").replace("$", ".")
                        }
                    ) {
                        mappings[it.value]?: run {
                            project.logger.warn(it.value)
                            it.value
                        }
                    }
                ) {
                    mappings[it.value]?: run {
                        project.logger.warn(it.value)
                        it.value
                    }
                }
                jarFile.write(str.toByteArray())
            }
        }
        jarFile.close()
        oJarFile.close()
        return output
    }

    private fun deobfBin(artifact: Artifact): File? {
        val output=Utils.getCache(project, "i2m_repo", artifact.path)
        val origin = download(artifact) ?: return null
        if(!output.isFile){
            if(output.isDirectory)throw FileAlreadyExistsException(output)
        }else{
            return output
        }
        val jarFile = JarOutputStream(output.outputStream())
        val oJarFile = JarFile(origin)
        for (entry in oJarFile.entries()) {
            jarFile.putNextEntry(entry)
            if(!entry.isDirectory){
                val ips=oJarFile.getInputStream(entry)
                if(entry.name.endsWith(".class")) {
                    val cw = ClassWriter(0)
                    ClassReader(ips).accept(
                        ClassRemapper(
                            cw, NameOnlyRemapper(
                                mappings.filter { it.key.startsWith("net") },
                                mappings.filter { it.key.startsWith("method") },
                                mappings.filter { it.key.startsWith("field") })
                        ), 0
                    )
                    jarFile.write(cw.toByteArray())
                }else {
                    jarFile.write(ips.readAllBytes())
                }
            }
        }
        jarFile.close()
        oJarFile.close()
        return output
//        return download(artifact)
    }

    private fun deobfPom(artifact: Artifact): File? {
        val output=Utils.getCache(project, "i2m_repo", artifact.path)
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
            version.textContent = version.textContent + "_i2m"
        val dependencies = xPath.compile("/*[local-name()=\"project\"]/*[local-name()=\"dependencies\"]/*[local-name()=\"dependency\"]/*[local-name()=\"version\"]").evaluate(doc, XPathConstants.NODESET) as NodeList
        val l = dependencies.length
        for (i in IntRange(0, l-1)){
            val item=dependencies.item(i)
            item.textContent=item.textContent+"_i2m"
        }
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

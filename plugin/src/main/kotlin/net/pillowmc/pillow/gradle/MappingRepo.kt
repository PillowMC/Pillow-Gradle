package net.pillowmc.pillow.gradle

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import net.minecraftforge.artifactural.api.repository.Repository
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder
import net.minecraftforge.artifactural.base.repository.SimpleRepository
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter
import net.minecraftforge.gradle.common.util.Artifact
import net.minecraftforge.gradle.common.util.BaseRepo
import net.minecraftforge.gradle.common.util.Utils
import org.gradle.api.Project
import java.io.File
import java.util.jar.JarOutputStream
import java.util.jar.JarEntry

class MappingRepo(cache: File, private val project: Project) : BaseRepo(cache, project.logger) {
    private val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(
            ArtifactIdentifier::class.java
        ).provide(this)
    )

    companion object {
        private var INSTANCE: MappingRepo? = null
        private fun getInstance(project: Project): MappingRepo {
            if(INSTANCE==null){
                INSTANCE= MappingRepo(Utils.getCache(project, "map_repo"), project)
            }
            return INSTANCE!!
        }

        fun attach(project: Project) {
            val instance = getInstance(project)
            GradleRepositoryAdapter.add(project.repositories, "MAP_REPO", instance.cacheRoot, instance.repo)
        }
    }
    override fun findFile(artifact: ArtifactIdentifier): File? {
        if(!(artifact.name=="intermediary2mcp"&&artifact.group=="net.pillowmc"&&artifact.version=="current"))return null
        val artifact2 = Artifact.from(artifact)
        if(artifact.extension.equals("pom"))
            return genPom(artifact2)
        else if(artifact.extension.equals("jar"))
            return genJar(artifact2)
        return null
    }
    private fun genJar(artifact: Artifact): File? {
        val mappings = project.layout.buildDirectory.dir("genIntermediary2MCP").map { s -> s.file("mappings.tiny") }.get().asFile.readBytes()
        val output=Utils.getCache(project, "map_repo", artifact.path)
        if(!output.exists()&&!output.isFile){
            if(output.isDirectory)throw FileAlreadyExistsException(output)
        }
        val jarFile = JarOutputStream(output.outputStream())
        jarFile.putNextEntry(JarEntry("mappings/"))
        jarFile.putNextEntry(JarEntry("mappings/mappings.tiny"))
        jarFile.write(mappings)
        jarFile.close()
        return output
    }

    private fun genPom(artifact: Artifact): File? {
        val output=Utils.getCache(project, "map_repo", artifact.path)
        if(!output.isFile){
            if(output.isDirectory)throw FileAlreadyExistsException(output)
            createFile(output)
        }else {
            return output
        }
        output.writeText(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>net.pillowmc</groupId>
                    <artifactId>intermediary2mcp</artifactId>
                    <version>current</version>
                </project>
            """.trimIndent()
        )
        return output
    }
    private fun createFile(file: File) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }
}

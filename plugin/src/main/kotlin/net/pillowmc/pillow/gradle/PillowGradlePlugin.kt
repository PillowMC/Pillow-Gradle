package net.pillowmc.pillow.gradle

import net.minecraftforge.gradle.common.tasks.DownloadMavenArtifact
import net.minecraftforge.gradle.common.tasks.ExtractMCPData
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.gradle.userdev.UserDevExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class PillowGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val conf=project.configurations.create("quiltMod")
        project.configurations.getByName("compileOnly").extendsFrom(conf)
        val downloadIntermediaryTask=project.tasks.register("downloadIntermediaryTask", DownloadMavenArtifact::class.java)
        val genIntermediary2MCP=project.tasks.register("genIntermediary2MCP", GenIntermediary2MCP::class.java)
        val extractSrg=project.tasks.getByName("extractSrg") as ExtractMCPData
        val createSrgToMcp=project.tasks.getByName("createSrgToMcp") as GenerateSRG
        genIntermediary2MCP.configure {
            it.getObf2Intermediary().set(downloadIntermediaryTask.flatMap(DownloadMavenArtifact::getOutput))
            it.getObf2Srg().set(extractSrg.output)
            it.getSrg2Mcp().set(createSrgToMcp.output)
            it.getIntermediary2Mcp().set(project.layout.buildDirectory
                .dir(it.name).map { s -> s.file("mappings.csv") })
            it.getIntermediary2McpTiny().set(project.layout.buildDirectory
            .dir(it.name).map { s -> s.file("mappings.tiny") })
        }
        genIntermediary2MCP.get().dependsOn("downloadIntermediaryTask", "extractSrg", "createSrgToMcp")
            .mustRunAfter(project.tasks.getByName("createSrgToMcp"))
        Intermediary2MCPRepo.attach(project)
        TweakMixinRepo.attach(project)
        MappingRepo.attach(project)
        project.afterEvaluate {
            val mcVersion=project.extensions.extraProperties["MC_VERSION"] as String?
            downloadIntermediaryTask.configure{
                it.setArtifact("net.fabricmc:intermediary:$mcVersion:v2")
            }
            val mods = StringBuilder()
            conf.resolve().forEach {
                mods.append(it.path.toString()+File.pathSeparator)
            }
            project.extensions.getByType(UserDevExtension::class.java).runs.forEach {
                it.property("loader.addMods", mods.toString())
            }
        }
    }
}

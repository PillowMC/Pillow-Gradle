package net.pillowmc.pillow.gradle

import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.FileOutputStream

abstract class GenIntermediary2MCP: DefaultTask() {
    @InputFile
    abstract fun getObf2Intermediary(): RegularFileProperty
    @InputFile
    abstract fun getObf2Srg(): RegularFileProperty
    @InputFile
    abstract fun getSrg2Mcp(): RegularFileProperty
    @OutputFile
    abstract fun getIntermediary2Mcp(): RegularFileProperty
    @OutputFile
    abstract fun getIntermediary2McpTiny(): RegularFileProperty
    @TaskAction
    fun run(){
        val os=IMappingFile.load(getObf2Srg().asFile.get())
        val oi=IMappingFile.load(project.zipTree(getObf2Intermediary()).find { it.name.endsWith("mappings.tiny") })
        val sm=IMappingFile.load(getSrg2Mcp().asFile.get())
        val output = getIntermediary2Mcp().asFile.get()
        val fow = FileOutputStream(output).writer()
        val out = oi.reverse().chain(os).chain(sm)
        out.classes.forEach{ itClass ->
            fow.write(itClass.original+","+itClass.mapped+"\n")
            itClass.methods.forEach { fow.write(it.original+","+it.mapped+"\n") }
            itClass.fields.forEach { fow.write(it.original+","+it.mapped+"\n") }
        }
        out.write(getIntermediary2McpTiny().asFile.get().toPath(), IMappingFile.Format.TINY, false)
    }
}

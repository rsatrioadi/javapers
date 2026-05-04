package nl.tue.win.javapers

import nl.tue.win.lpg.encoder.CyJsonCodec
import org.json.JSONObject
import java.io.File

object ParityMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: ParityMain <graph-a.json> <graph-b.json>")
            return
        }
        val graphA = CyJsonCodec.decodeGraph(JSONObject(File(args[0]).readText()))
        val graphB = CyJsonCodec.decodeGraph(JSONObject(File(args[1]).readText()))
        println(ParityReport.compare(graphA, graphB, File(args[0]).name, File(args[1]).name))
    }
}

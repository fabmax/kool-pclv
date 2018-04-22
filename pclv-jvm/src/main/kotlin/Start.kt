import de.fabmax.kool.util.Log
import de.fabmax.pclv.pointTree.OcTree
import de.fabmax.pclv.pointTree.Point
import de.fabmax.pclv.pointImport.loadPointTree
import de.fabmax.pclv.pointTree.OnDiskOcTree
import de.fabmax.pclv.srv.PclvServer
import org.apache.commons.cli.*
import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
    try {
        var httpPort = 8080
        var dataPath: String? = null
        var visualizeClients = false
        DefaultParser().parse(buildOptions(), args).options.forEach {
            when (it.opt) {
                "h" -> printHelp()
                "p" -> httpPort = parseInt(it.value)
                "d" -> dataPath = it.value
                "v" -> visualizeClients = true
            }
        }

        PclvServer(loadData(dataPath), httpPort, visualizeClients)

    } catch (e: ParseException) {
        Log.e(PclvServer.TAG) { "Failed parsing command line arguments: $e" }
        printHelp()
    }
}

private fun loadData(dataPath: String?): OcTree<Point> {
    val f = File(dataPath ?: "")

    when {
        !f.exists() -> throw FileNotFoundException("Point cloud data not found: $f")
        f.isDirectory -> {
            val meta = File(f, "meta.pb")
            if (!meta.exists()) {
                throw FileNotFoundException("Specified directory does not contain a valid Octree $f")
            }
            return OnDiskOcTree(f)
        }
        f.name.endsWith(".ply", true) -> return loadPointTree(f.path, 5000)
    }
    throw IllegalArgumentException("Invalid point cloud file: $f")
}

private fun parseInt(str: String): Int {
    try {
        return str.toInt()
    } catch (e: NumberFormatException) {
        throw ParseException("Invalid number: $str")
    }
}

private fun buildOptions(): Options {
    val options = Options()

    options.addOption(Option.builder("p")
            .longOpt("port")
            .hasArg()
            .desc("Port used for web viewer http interface")
            .build())

    options.addOption(Option.builder("d")
            .longOpt("point-data")
            .hasArg()
            .required()
            .desc("Path to point cloud data (PLY file or directory with Cartographer octree)")
            .build())

    options.addOption(Option.builder("h")
            .longOpt("help")
            .desc("Displays this help message")
            .build())

    options.addOption(Option.builder("v")
            .longOpt("visualize-clients")
            .desc("Shows a debug view with client position and transmitted content")
            .build())


    return options
}

private fun printHelp() {
    HelpFormatter().printHelp("java -cp pclv.jar StartKt [Options]", buildOptions())
}

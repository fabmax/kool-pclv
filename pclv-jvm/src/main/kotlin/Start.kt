import de.fabmax.kool.createContext
import de.fabmax.kool.util.Log
import de.fabmax.pclv.pointImport.loadPointTree
import de.fabmax.pclv.pointTree.OcTree
import de.fabmax.pclv.pointTree.OnDiskOcTree
import de.fabmax.pclv.pointTree.Point
import de.fabmax.pclv.srv.PclvServer
import de.fabmax.pclv.viewer.JvmViewer
import org.apache.commons.cli.*
import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
    try {
        var httpPort = 8080
        var dataPath: String? = null
        var localVisualizer = false
        DefaultParser().parse(buildOptions(), args).options.forEach {
            when (it.opt) {
                "h" -> printHelp()
                "p" -> httpPort = parseInt(it.value)
                "d" -> dataPath = it.value
                "v" -> localVisualizer = true
            }
        }

        val srv = PclvServer(loadData(dataPath), httpPort, localVisualizer)

        if (localVisualizer) {
            // launch local visualizer
            val ctx = createContext()
            JvmViewer(ctx, srv)
            ctx.run()

            // when ctx.run() returns window was closed -> shutdown server and exit
            srv.close()
        }

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
            .longOpt("local-visualizer")
            .desc("Open a local visualizer window (faster than WebGL)")
            .build())


    return options
}

private fun printHelp() {
    HelpFormatter().printHelp("java -cp pclv.jar StartKt [Options]", buildOptions())
}

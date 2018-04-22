package de.fabmax.pclv.viewer

import de.fabmax.kool.KoolContext
import de.fabmax.kool.util.serialization.MeshData
import de.fabmax.pclv.PointViewerScene
import de.fabmax.pclv.messaging.VisibleNodes
import de.fabmax.pclv.srv.PclvServer
import kotlinx.coroutines.experimental.launch

class JvmViewer(ctx: KoolContext, srv: PclvServer) {

    private val pointViewerScene = PointViewerScene(ctx) { camRequest ->
        launch {
            val (visibleNodes, nodes) = srv.handleCamRequest(camRequest)
            setVisibleNodes(visibleNodes)
            setPoints(nodes)
        }
    }

    private fun setVisibleNodes(visibleNodes: VisibleNodes) {
        pointViewerScene.setVisibleNodes(visibleNodes)
    }

    private fun setPoints(nodes: List<MeshData>) {
        nodes.forEach {
            pointViewerScene.addPoints(it)
        }
    }

}
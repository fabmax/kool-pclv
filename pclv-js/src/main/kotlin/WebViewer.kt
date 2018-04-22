import de.fabmax.kool.createContext
import de.fabmax.pclv.PointViewerScene
import kotlin.browser.window

/**
 * Application entry point, called from index.html
 */
fun main() {
    val wsClient = WebSocketClient("ws://${window.location.hostname}:8887")

    val ctx = createContext()
    val viewer = PointViewerScene(ctx) { camRequest ->
        wsClient.send(camRequest.asMessage())
    }

    wsClient.installHandler("meshData", { meshData!! }) { viewer.addPoints(it) }
    wsClient.installHandler("visibleNodes", { visibleNodes!! }) { viewer.setVisibleNodes(it) }

    ctx.run()
}

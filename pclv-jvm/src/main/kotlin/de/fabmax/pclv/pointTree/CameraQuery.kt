package de.fabmax.pclv.pointTree

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.PerspectiveCamera

class CameraQuery : PerspectiveCamera() {

    var viewportWidth = 1
    var viewportHeight = 1

    // density = numberOfPoints / screen pixel
    // smaller number means less points transmitted to client
    var maxDensity = 0.3f

    init {
        clipNear = 1f
        clipFar = 1000f
    }

    fun setup(pos: Vec3f, lookAt: Vec3f, viewportWidth: Int, viewportHeight: Int, fovy: Float) {
        this.fovy = fovy
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.position.set(pos)
        this.lookAt.set(lookAt)

        aspectRatio = viewportWidth.toFloat() / viewportHeight
        updateViewMatrix()
        updateProjectionMatrix()

        proj.mul(view, mvp)
        mvp.invert(invMvp)
    }
}
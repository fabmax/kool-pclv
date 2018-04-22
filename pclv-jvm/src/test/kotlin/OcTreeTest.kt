import de.fabmax.kool.math.Random
import de.fabmax.kool.math.Vec3f
import de.fabmax.pclv.pointTree.InMemoryOcTree
import org.junit.Test

class OcTreeTest {
    @Test
    fun testMerge() {
        val n = 100
        val tree = InMemoryOcTree(randomPoints(n), (n * 0.9).toInt())

        assert(!tree.root.isLeaf) { "tree root must not be a leaf" }
        var sum = 0
        for (i in 0..7) {
            val c = tree.root[i]!!
            assert(c.isLeaf) { "tree child node $i is not a leaf" }
            sum += c.numPoints.toInt()
        }
        assert(sum == n) { "tree must contain $n points (has $sum instead)"}

        tree.root.nodeIsze = n
        assert(tree.root.isLeaf) { "merged tree root must be a leaf" }
        assert(tree.root.numPoints.toInt() == n) { "tree must contain $n points (has ${tree.root.numPoints} instead)" }
    }

    @Test
    fun testSplit() {
        val n = 100
        val tree = InMemoryOcTree(randomPoints(n), n)

        assert(tree.root.isLeaf) { "tree root must be a leaf" }
        assert(tree.root.numPoints.toInt() == n) { "tree must contain $n points (has ${tree.root.numPoints} instead)" }
        tree.root.nodeIsze = (n * 0.9).toInt()

        assert(!tree.root.isLeaf) { "split tree root must not be a leaf" }
        var sum = 0
        for (i in 0..7) {
            val c = tree.root[i]!!
            assert(c.isLeaf) { "tree child node $i is not a leaf" }
            sum += c.numPoints.toInt()
        }
        assert(sum == n) { "tree must contain $n points (has $sum instead)"}
    }

    private fun randomPoints(n: Int): List<Vec3f> {
        val r = Random(0)
        val list = mutableListOf<Vec3f>()

        for (i in 1..n) {
            list += Vec3f(r.randomF(), r.randomF(), r.randomF())
        }

        return list
    }
}

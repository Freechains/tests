import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.freechains.common.*
import org.freechains.host.*
import org.freechains.cli.*
import org.freechains.bootstrap.Bootstrap
import org.freechains.bootstrap.Store
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import kotlin.concurrent.thread

const val PATH = "/tmp/freechains/tests/bootstrap/"

fun port (i: Int) : String {
    return "--port=${PORT_8330+i}"
}
fun host (i: Int) : String {
    return "--host=localhost:${PORT_8330+i}"
}
fun peer (i: Int) : String {
    return "localhost:${PORT_8330+i}"
}

@TestMethodOrder(Alphanumeric::class)
class Tests_Bootstrap {

    companion object {
        @BeforeAll
        @JvmStatic
        internal fun reset () {
            assert_(File(PATH).deleteRecursively())
        }
    }

    @BeforeEach
    fun stop () {
        //main_host(arrayOf("start", (PORT_8330+0).toString()))
    }

    @Test
    fun t00 () {
        thread { main_host(arrayOf("start", PATH+"/0", port(0))) }
        thread { main_host(arrayOf("start", PATH+"/1", port(1))) }
        thread { main_host(arrayOf("start", PATH+"/2", port(2))) }
        Thread.sleep(200)

        main_cli_assert(arrayOf("chains", "join", "#boot", host(0)))
        main_cli_assert(arrayOf("chains", "join", "#boot", host(2)))

        // post to 8330 -- send to --> 8332
        val data = Store (
            mutableListOf(peer(1)),
            mutableListOf(Pair("#chat",null))
        )
        @OptIn(UnstableDefault::class)
        val json= Json(JsonConfiguration(prettyPrint=true)).stringify(Store.serializer(), data)
        main_cli_assert(arrayOf("chain", "#boot", "post", "inline", json))
        main_cli_assert(arrayOf("peer", peer(2), "send", "#boot"))

        var ok = false
        val boot = Bootstrap(PATH+"/x.json","#boot")
        boot.cbs.add {
            ok = it.peers.contains(peer(1)) && it.chains.contains(Pair("#chat",null))
        }

        Thread.sleep(1000)
        assert(ok)
    }
}
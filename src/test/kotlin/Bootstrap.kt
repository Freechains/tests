import org.freechains.common.*
import org.freechains.host.*
import org.freechains.cli.*
import org.freechains.bootstrap.*
import org.freechains.bootstrap.Chain
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
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
        thread { main_host(arrayOf("start", PATH+"/0", port(0))) }      // peer with bootstrap
        thread { main_host(arrayOf("start", PATH+"/1", port(1))) }      // peer with all content
        thread { main_host(arrayOf("start", PATH+"/2", port(2))) }      // myself
        Thread.sleep(200)

        val key = main_cli_assert(arrayOf("crypto", "shared", "my-password", host(2)))

        main_cli_assert(arrayOf("chains", "join", "#boot", host(0)))
        main_cli_assert(arrayOf("chains", "join", "#chat", host(1)))
        main_cli_assert(arrayOf("chains", "join", "\$family", key, host(1)))
        main_cli_assert(arrayOf("chains", "join", "#boot", host(2)))

        main_cli_assert(arrayOf("chain", "#chat",    "post", "inline", "[#chat] Hello World!",    host(1)))
        main_cli_assert(arrayOf("chain", "\$family", "post", "inline", "[\$family] Hello World!", host(1)))

        // post to 8330 -- send to --> 8332
        val data = Store (
            mutableListOf(peer(1)),
            mutableListOf(Pair("#chat",null))
        )
        @OptIn(UnstableDefault::class)
        val json= Json(JsonConfiguration(prettyPrint=true)).stringify(Store.serializer(), data)
        main_cli_assert(arrayOf("chain", "#boot", "post", "inline", json, host(0)))
        main_cli_assert(arrayOf("peer", peer(2), "send", "#boot", host(0)))

        var ok = false
        val boot = Chain(PATH,"#boot", peer(2))
        boot.cbs.add {
            ok = it.peers.contains(peer(1)) && it.chains.contains(Pair("#chat",null))
        }

        Thread.sleep(1000)
        assert(ok)
        main_cli_assert(arrayOf("chain", "#chat", "heads", "all", host(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "#chat", "get", "payload", it, host(2)))
            assert_(pay == "[#chat] Hello World!")
        }

        boot.write { it.chains.add(Pair("\$family", key)) }
        Thread.sleep(1000)
        main_cli_assert(arrayOf("chain", "\$family", "heads", "all", host(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "\$family", "get", "payload", it, host(2)))
            assert_(pay == "[\$family] Hello World!")
        }
    }

    @Test
    fun t01 () {

    }
}
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
var KEY: String? = null

fun path (i: Int) : String {
    return "$PATH/$i"
}

fun port (i: Int) : String {
    return "--port=${PORT_8330+i}"
}
fun myself (i: Int) : String {
    return "--host=localhost:${PORT_8330+i}"
}
fun pair (i: Int) : String {
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
        //main_host(arrayOf("start", (PORT_8330+1).toString()))
        //main_host(arrayOf("start", (PORT_8330+2).toString()))
    }

    @Test
    fun t00 () {
        thread { main_host(arrayOf("start", path(0), port(0))) }      // peer with bootstrap
        thread { main_host(arrayOf("start", path(1), port(1))) }      // peer with all content
        thread { main_host(arrayOf("start", path(2), port(2))) }      // myself
        Thread.sleep(200)

        KEY = main_cli_assert(arrayOf("crypto", "shared", "my-password", myself(2)))

        main_cli_assert(arrayOf("chains", "join", "\$bootstrap.xxx", KEY!!, myself(0)))
        main_cli_assert(arrayOf("chains", "join", "#chat", myself(1)))
        main_cli_assert(arrayOf("chains", "join", "\$family", KEY!!, myself(1)))
        main_cli_assert(arrayOf("chains", "join", "\$bootstrap.xxx", KEY!!, myself(2)))

        main_cli_assert(arrayOf("chain", "#chat",    "post", "inline", "[#chat] Hello World!",    myself(1)))
        main_cli_assert(arrayOf("chain", "\$family", "post", "inline", "[\$family] Hello World!", myself(1)))

        // post to 8330 -- send to --> 8332
        val data = Store (
            mutableListOf(pair(1)),
            mutableListOf(Pair("#chat",null))
        )
        @OptIn(UnstableDefault::class)
        val json= Json(JsonConfiguration(prettyPrint=true)).stringify(Store.serializer(), data)
        main_cli_assert(arrayOf("chain", "\$bootstrap.xxx", "post", "inline", json, myself(0)))
        main_cli_assert(arrayOf("peer", pair(2), "send", "\$bootstrap.xxx", myself(0)))

        var ok = false
        val boot = Chain(path(0),"\$bootstrap.xxx", pair(2))
        boot.cbs.add {
            ok = it.peers.contains(pair(1)) && it.chains.contains(Pair("#chat",null))
        }

        Thread.sleep(500)
        assert(ok)
        main_cli_assert(arrayOf("chain", "#chat", "heads", "all", myself(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "#chat", "get", "payload", it, myself(2)))
            assert_(pay == "[#chat] Hello World!")
        }

        boot.write { it.chains.add(Pair("\$family", KEY)) }
        Thread.sleep(500)
        main_cli_assert(arrayOf("chain", "\$family", "heads", "all", myself(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "\$family", "get", "payload", it, myself(2)))
            assert_(pay == "[\$family] Hello World!")
        }
    }

    @Test
    fun t01 () {
        thread { main_host(arrayOf("start", path(3), port(3))) }
        Thread.sleep(500)
        val boot = Bootstrap(path(3), pair(3))
        boot.boot(pair(2), "\$bootstrap.xxx", KEY!!)
        Thread.sleep(1000)
        main_cli_assert(arrayOf(myself(3), "chain", "#chat", "heads", "all")).let {
            val pay = main_cli_assert(arrayOf(myself(3), "chain", "#chat", "get", "payload", it))
            assert_(pay == "[#chat] Hello World!")
        }
        main_cli_assert(arrayOf(myself(3), "chain", "\$family", "heads", "all")).let {
            val pay = main_cli_assert(arrayOf(myself(3), "chain", "\$family", "get", "payload", it))
            assert_(pay == "[\$family] Hello World!")
        }
    }
}
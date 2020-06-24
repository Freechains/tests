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
    fun s00 () {

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
        main_cli_assert(arrayOf(myself(0), "chain", "\$bootstrap.xxx", "post", "inline", "peers add ${pair(1)}"))
        main_cli_assert(arrayOf(myself(0), "chain", "\$bootstrap.xxx", "post", "inline", "chains add #chat"))
        main_cli_assert(arrayOf("peer", pair(2), "send", "\$bootstrap.xxx", myself(0)))

        val boot = Chain("\$bootstrap.xxx", PORT_8330+2)
        Thread.sleep(500)
        assert (
            boot.peers.contains(pair(1)) &&
            main_cli_assert(arrayOf(port(2), "chains", "list")).listSplit().contains("#chat")
        )

        main_cli_assert(arrayOf("chain", "#chat", "heads", "all", myself(2))).let {
            val pay = main_cli_assert(arrayOf("chain", "#chat", "get", "payload", it, myself(2)))
            assert_(pay == "[#chat] Hello World!")
        }

        main_cli_assert(arrayOf(port(2), "chain", "\$bootstrap.xxx", "post", "inline", "chains add \$family $KEY"))
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
        thread {
            main_cli_assert(arrayOf(port(3), "chains", "join", "\$bootstrap.xxx", KEY!!))
            main_bootstrap(arrayOf(port(3), "remote", pair(2), "\$bootstrap.xxx"))
        }

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
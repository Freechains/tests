import org.freechains.cli.main_cli_assert
import org.freechains.common.PORT_8330
import org.freechains.common.assert_
import org.freechains.common.listSplit
import org.freechains.host.main_host
import org.freechains.store.Store
import org.freechains.sync.main_sync
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread


private const val PATH = "/tmp/freechains/tests/sync/"
private const val PVT0 = "6F99999751DE615705B9B1A987D8422D75D16F5D55AF43520765FA8C5329F7053CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322"
private const val PUB0 = "3CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322"
private const val SIG0 = "--sign=$PVT0"

private fun path (i: Int) : String {
    return "$PATH/$i"
}

private fun port (i: Int) : String {
    return "--port=${PORT_8330+i}"
}
private fun myself (i: Int) : String {
    return "--host=localhost:${PORT_8330+i}"
}
private fun pair (i: Int) : String {
    return "localhost:${PORT_8330+i}"
}

@TestMethodOrder(Alphanumeric::class)
class Tests_Sync {

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
        thread { main_host(arrayOf("start", path(10), port(10))) }
        Thread.sleep(200)

        main_cli_assert(arrayOf(myself(10), "chains", "join", "#store"))
        val s = Store("#store", PORT_8330+10)

        var ok = false
        fun cb (v1: String, v2: String, v3: String) {
            ok = true
            assert_(v1=="v1" && v2=="v2" && v3=="v3")
        }
        s.cbs.add(::cb)

        s.store("v1","v2","v3")
        Thread.sleep(200)
        assert_(ok)
        assert(s.data["v1"]!!["v2"]!! == "v3")
        s.cbs.remove(::cb)

        s.store("v1","v2","REM")
        assert_(!s.data["v1"]!!.containsKey("v2"))

        s.cb_chains()
        s.store("chains","#xxx","ADD")
        Thread.sleep(100)
        assert_(main_cli_assert(arrayOf(myself(10), "chains", "list")).listSplit().contains("#xxx"))
    }

    @Test
    fun s01 () {
        thread { main_host(arrayOf("start", path(11), port(11))) }
        Thread.sleep(500)

        main_cli_assert(arrayOf(myself(10), "chains", "join", "@$PUB0"))
        main_cli_assert(arrayOf(myself(11), "chains", "join", "@$PUB0"))

        thread { main_sync(arrayOf(port(10), "@$PUB0")) }
        main_cli_assert(arrayOf(myself(10), SIG0, "chain", "@$PUB0", "post", "inline", "chains \"@$PUB0\" ADD"))
        Thread.sleep(500)

        main_cli_assert(arrayOf(myself(10), "peer", pair(11), "send", "@$PUB0"))
        Thread.sleep(500)
        thread { main_sync(arrayOf(port(11), "@$PUB0")) }

        main_cli_assert(arrayOf(myself(10), SIG0, "chain", "@$PUB0", "post", "inline", "peers ${pair(11)} ADD"))
        main_cli_assert(arrayOf(myself(10), SIG0, "chain", "@$PUB0", "post", "inline", "chains #chat ADD"))

        Thread.sleep(500) // #chat ADD has to propagate to @11

        main_cli_assert(arrayOf(myself(10), "chain", "#chat", "post", "inline", "[#chat] Hello World!"))

        // TODO 15s (!!!)
        Thread.sleep(15000) // "Hello World! has to propagate to @11

        main_cli_assert(arrayOf(myself(11), "chain", "#chat", "heads", "all")).let {
            println(it)
            val pay = main_cli_assert(arrayOf(myself(11), "chain", "#chat", "get", "payload", it))
            assert_(pay == "[#chat] Hello World!")
        }
    }
}